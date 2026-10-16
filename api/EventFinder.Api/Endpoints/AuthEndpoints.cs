using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using EventFinder.Api.Common;
using EventFinder.Api.Data;
using EventFinder.Api.Dtos;
using EventFinder.Api.Services;
using Microsoft.EntityFrameworkCore;

namespace EventFinder.Api.Endpoints;

public static class AuthEndpoints
{
    public static RouteGroupBuilder MapAuthEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/auth").WithTags("Auth").RequireRateLimiting("auth");

        group.MapPost("/register", RegisterAsync);
        group.MapPost("/login", LoginAsync);
        group.MapPost("/google", GoogleSignInAsync);
        group.MapPost("/forgot-password", ForgotPasswordAsync);
        group.MapPost("/reset-password", ResetPasswordAsync);
        group.MapPost("/change-password", ChangePasswordAsync).RequireAuthorization();
        group.MapPut("/me", UpdateProfileAsync).RequireAuthorization();
        group.MapPost("/request-email-change", RequestEmailChangeAsync).RequireAuthorization();
        group.MapPost("/verify-email-change", VerifyEmailChangeAsync);
        group.MapDelete("/me", DeleteAccountAsync).RequireAuthorization();
        group.MapGet("/me", MeAsync).RequireAuthorization();

        return group;
    }

    private static async Task<IResult> RegisterAsync(
        RegisterRequest request, AppDbContext db, TokenService tokens, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<AuthResponse>.Fail("Validation failed.", errors), statusCode: 400);

        var email = request.Email!.Trim().ToLowerInvariant();
        if (await db.Users.AnyAsync(u => u.Email == email, ct))
        {
            return Results.Json(
                ApiResponse<AuthResponse>.Fail("An account with this email already exists.",
                    new Dictionary<string, string[]> { ["email"] = ["Email is already registered."] }),
                statusCode: 409);
        }

        var now = DateTime.UtcNow;
        var user = new User
        {
            Id = Guid.NewGuid(), // server-generated identifier
            Email = email,
            FullName = request.FullName!.Trim(),
            PasswordHash = PasswordHasher.Hash(request.Password!),
            PreferredLanguage = string.IsNullOrWhiteSpace(request.Language) ? "en" : request.Language!,
            CreatedAt = now,
            UpdatedAt = now,
        };
        db.Users.Add(user);
        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException dbEx)
        {
            // Check if the underlying database error is specifically the unique-email
            // constraint violation. In SQLite, this typically produces a message containing
            // "UNIQUE constraint failed" or similar. Other DbUpdateExceptions (e.g. foreign
            // key violations, database lock failures) should be handled as server errors.
            var isUniqueEmailConstraint = dbEx.Message?.Contains("UNIQUE constraint failed") == true ||
                                        dbEx.InnerException?.Message?.Contains("UNIQUE constraint failed") == true;

            if (isUniqueEmailConstraint)
            {
                return Results.Json(
                    ApiResponse<AuthResponse>.Fail("An account with this email already exists.",
                        new Dictionary<string, string[]> { ["email"] = ["Email is already registered."] }),
                    statusCode: 409);
            }

            // Return 500 for unrelated database failures rather than misreporting as duplicate email
            return Results.Json(
                ApiResponse<AuthResponse>.Fail("A database error occurred. Please try again later.",
                    new Dictionary<string, string[]> { ["error"] = ["DatabaseError"] }),
                statusCode: 500);
        }

        var (token, expires) = tokens.CreateToken(user);
        return Results.Json(ApiResponse<AuthResponse>.Ok(
            new AuthResponse(user.ToDto(), token, expires), "Account created."));
    }

    private static async Task<IResult> LoginAsync(
        LoginRequest request, AppDbContext db, TokenService tokens, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

        var email = request.Email!.Trim().ToLowerInvariant();
        var user = await db.Users.FirstOrDefaultAsync(u => u.Email == email, ct);
        if (user is null || !PasswordHasher.Verify(request.Password!, user.PasswordHash))
        {
            return Results.Json(ApiResponse<object>.Fail("Invalid email or password."), statusCode: 401);
        }

        var (token, expires) = tokens.CreateToken(user);
        return Results.Json(ApiResponse<AuthResponse>.Ok(
            new AuthResponse(user.ToDto(), token, expires), "Signed in."));
    }

    private static async Task<IResult> GoogleSignInAsync(
        GoogleSignInRequest request, AppDbContext db, TokenService tokens, GoogleTokenVerifier verifier, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<AuthResponse>.Fail("Validation failed.", errors), statusCode: 400);

        var info = await verifier.VerifyAsync(request.IdToken!, ct);
        if (info is null)
        {
            return Results.Json(
                ApiResponse<AuthResponse>.Fail("Google sign-in failed.",
                    new Dictionary<string, string[]> { ["idToken"] = ["The Google ID token is invalid or expired."] }),
                statusCode: 401);
        }

        var email = info.Email.Trim().ToLowerInvariant();
        var user = await db.Users.FirstOrDefaultAsync(
            u => u.GoogleSubject == info.Sub || u.Email == email, ct);

        var now = DateTime.UtcNow;
        if (user is null)
        {
            user = new User
            {
                Id = Guid.NewGuid(),
                Email = email,
                FullName = string.IsNullOrWhiteSpace(info.Name) ? email : info.Name,
                PasswordHash = PasswordHasher.Hash(Guid.NewGuid().ToString("N")),
                PreferredLanguage = "en",
                GoogleSubject = info.Sub,
                CreatedAt = now,
                UpdatedAt = now,
            };
            db.Users.Add(user);
        }
        else
        {
            user.GoogleSubject = info.Sub;
            user.UpdatedAt = now;
            if (!string.IsNullOrWhiteSpace(info.Name)) user.FullName = info.Name;
        }
        await db.SaveChangesAsync(ct);

        var (token, expires) = tokens.CreateToken(user);
        return Results.Json(ApiResponse<AuthResponse>.Ok(
            new AuthResponse(user.ToDto(), token, expires), "Signed in with Google."));
    }

    private static async Task<IResult> ForgotPasswordAsync(
        ForgotPasswordRequest request, AppDbContext db, IEmailService email, ILoggerFactory lf, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

        var normalized = request.Email!.Trim().ToLowerInvariant();
        var user = await db.Users.FirstOrDefaultAsync(u => u.Email == normalized, ct);

        // Always answer success so the endpoint cannot be used to enumerate accounts.
        if (user is not null)
        {
            // Invalidate any earlier unused reset tokens for this account so only
            // the most recently issued one remains usable.
            var prior = db.PasswordResetTokens.Where(t => t.UserId == user.Id && t.UsedAt == null);
            db.PasswordResetTokens.RemoveRange(prior);

            var rawToken = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
            var reset = new PasswordResetToken
            {
                Id = Guid.NewGuid(),
                UserId = user.Id,
                TokenHash = HashToken(rawToken),
                ExpiresAt = DateTime.UtcNow.AddMinutes(60),
                CreatedAt = DateTime.UtcNow,
            };
            db.PasswordResetTokens.Add(reset);
            await db.SaveChangesAsync(ct);

            try
            {
                await email.SendPasswordResetEmailAsync(user.Email, rawToken, ct);
            }
            catch (Exception ex) when (
                ex is InvalidOperationException ||
                ex is HttpRequestException ||
                (ex is TaskCanceledException && !ct.IsCancellationRequested))
            {
                lf.CreateLogger("AuthEndpoints").LogError(ex, "Failed to send reset email to {Email}", user.Email);
                // Remove the token we just stored so a failed delivery does not
                // leave a usable reset token behind. Return the same success
                // response as for an unknown account to avoid enumeration.
                db.PasswordResetTokens.Remove(reset);
                await db.SaveChangesAsync(ct);
            }
        }

        return Results.Json(ApiResponse<object>.Ok(
            new { }, "If an account exists for that email, a reset token has been sent."));
    }

    private static async Task<IResult> ResetPasswordAsync(
        ResetPasswordRequest request, AppDbContext db, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

        var normalized = request.Email!.Trim().ToLowerInvariant();
        var user = await db.Users.FirstOrDefaultAsync(u => u.Email == normalized, ct);
        var tokenHash = HashToken(request.Token!);

        var reset = user is null
            ? null
            : await db.PasswordResetTokens.FirstOrDefaultAsync(
                t => t.UserId == user.Id && t.TokenHash == tokenHash && t.UsedAt == null && t.ExpiresAt > DateTime.UtcNow,
                ct);

        if (reset is null || user is null)
        {
            return Results.Json(
                ApiResponse<object>.Fail("This reset token is invalid or has expired.",
                    new Dictionary<string, string[]> { ["token"] = ["Invalid or expired token."] }),
                statusCode: 400);
        }

        user.PasswordHash = PasswordHasher.Hash(request.NewPassword!);
        user.UpdatedAt = DateTime.UtcNow;
        user.TokenVersion++;
        reset.UsedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);

        return Results.Json(ApiResponse<object>.Ok(new { }, "Password updated. You can now sign in."));
    }

    private static async Task<IResult> ChangePasswordAsync(
        ChangePasswordRequest request, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        if (!PasswordHasher.Verify(request.CurrentPassword!, user.PasswordHash))
        {
            return Results.Json(
                ApiResponse<object>.Fail("Current password is incorrect.",
                    new Dictionary<string, string[]> { ["currentPassword"] = ["Incorrect password."] }),
                statusCode: 400);
        }

        user.PasswordHash = PasswordHasher.Hash(request.NewPassword!);
        user.UpdatedAt = DateTime.UtcNow;
        user.TokenVersion++;

        // Invalidate any outstanding reset tokens for this account.
        var outstanding = db.PasswordResetTokens.Where(t => t.UserId == user.Id && t.UsedAt == null);
        db.PasswordResetTokens.RemoveRange(outstanding);

        await db.SaveChangesAsync(ct);

        return Results.Json(ApiResponse<object>.Ok(new { }, "Password changed."));
    }

    private static async Task<IResult> UpdateProfileAsync(
        UpdateProfileRequest request, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var fullName = request.FullName!.Trim();
        var email = request.Email!.Trim().ToLowerInvariant();

        if (email != user.Email)
        {
            return Results.Json(
                ApiResponse<object>.Fail("Email changes require verification.",
                    new Dictionary<string, string[]> { ["email"] = ["Use the email-change verification flow."] }),
                statusCode: 400);
        }

        user.FullName = fullName;
        user.UpdatedAt = DateTime.UtcNow;

        await db.Events
            .Where(e => e.OrganizerId == user.Id)
            .ExecuteUpdateAsync(s => s.SetProperty(e => e.OrganizerName, user.FullName), ct);

        await db.SaveChangesAsync(ct);
        return Results.Json(ApiResponse<UserDto>.Ok(user.ToDto(), "Profile updated."));
    }

    private static async Task<IResult> DeleteAccountAsync(
        ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        // Decrement attendee counts for events this user RSVP'd to as "attending".
        var userRsvps = await db.Rsvps.Where(r => r.UserId == user.Id).ToListAsync(ct);
        foreach (var r in userRsvps)
        {
            if (r.Status == "attending")
            {
                var ev = await db.Events.FirstOrDefaultAsync(e => e.Id == r.EventId, ct);
                if (ev is not null) ev.AttendeeCount = Math.Max(0, ev.AttendeeCount - 1);
            }
        }
        db.Rsvps.RemoveRange(userRsvps);

        // Remove owned events (their RSVPs cascade).
        var owned = await db.Events.Where(e => e.OrganizerId == user.Id).ToListAsync(ct);
        db.Events.RemoveRange(owned);

        // Remove outstanding reset tokens.
        var tokens = await db.PasswordResetTokens.Where(t => t.UserId == user.Id).ToListAsync(ct);
        db.PasswordResetTokens.RemoveRange(tokens);

        db.Users.Remove(user);
        await db.SaveChangesAsync(ct);

        return Results.Json(ApiResponse<object>.Ok(new { }, "Account deleted."));
    }

    private static async Task<IResult> RequestEmailChangeAsync(
        RequestEmailChangeRequest request, ClaimsPrincipal principal, AppDbContext db, IEmailService email, ILoggerFactory lf, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var newEmail = request.NewEmail!.Trim().ToLowerInvariant();
        if (newEmail == user.Email)
            return Results.Json(ApiResponse<object>.Ok(new { }, "Email already verified."));

        if (await db.Users.AnyAsync(u => u.Email == newEmail, ct))
        {
            return Results.Json(
                ApiResponse<object>.Fail("An account with this email already exists.",
                    new Dictionary<string, string[]> { ["newEmail"] = ["Email is already registered."] }),
                statusCode: 409);
        }

        db.EmailChangeTokens.RemoveRange(db.EmailChangeTokens.Where(t => t.UserId == user.Id && t.UsedAt == null));

        var rawToken = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
        var token = new EmailChangeToken
        {
            Id = Guid.NewGuid(),
            UserId = user.Id,
            NewEmail = newEmail,
            TokenHash = HashToken(rawToken),
            ExpiresAt = DateTime.UtcNow.AddMinutes(60),
            CreatedAt = DateTime.UtcNow,
        };
        db.EmailChangeTokens.Add(token);
        await db.SaveChangesAsync(ct);

        try
        {
            await email.SendEmailChangeVerificationAsync(newEmail, rawToken, ct);
        }
        catch (InvalidOperationException ex)
        {
            lf.CreateLogger("AuthEndpoints").LogError(ex, "Failed to send email-change verification to {Email}", newEmail);
            db.EmailChangeTokens.Remove(token);
            await db.SaveChangesAsync(ct);
            return Results.Json(
                ApiResponse<object>.Fail("Could not send the verification email. Try again shortly."),
                statusCode: 502);
        }

        return Results.Json(ApiResponse<object>.Ok(new { }, "Verification email sent."));
    }

    private static async Task<IResult> VerifyEmailChangeAsync(
        VerifyEmailChangeRequest request, AppDbContext db, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

        var tokenHash = HashToken(request.Token!);
        var token = await db.EmailChangeTokens.FirstOrDefaultAsync(
            t => t.TokenHash == tokenHash && t.UsedAt == null && t.ExpiresAt > DateTime.UtcNow, ct);
        if (token is null)
        {
            return Results.Json(
                ApiResponse<object>.Fail("This verification token is invalid or has expired.",
                    new Dictionary<string, string[]> { ["token"] = ["Invalid or expired token."] }),
                statusCode: 400);
        }

        var user = await db.Users.FirstOrDefaultAsync(u => u.Id == token.UserId, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("User not found."), statusCode: 404);

        if (await db.Users.AnyAsync(u => u.Email == token.NewEmail && u.Id != user.Id, ct))
        {
            return Results.Json(
                ApiResponse<object>.Fail("An account with this email already exists."),
                statusCode: 409);
        }

        user.Email = token.NewEmail;
        user.UpdatedAt = DateTime.UtcNow;
        token.UsedAt = DateTime.UtcNow;
        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException dbEx)
        {
            // Check if the underlying database error is specifically the unique-email
            // constraint violation. Other DbUpdateExceptions (e.g. foreign key violations,
            // database lock failures) should be handled as server errors, not misreported
            // as duplicate email conflicts.
            var isUniqueEmailConstraint = dbEx.Message?.Contains("UNIQUE constraint failed") == true ||
                                        dbEx.InnerException?.Message?.Contains("UNIQUE constraint failed") == true;

            if (isUniqueEmailConstraint)
            {
                return Results.Json(
                    ApiResponse<object>.Fail("An account with this email already exists."),
                    statusCode: 409);
            }

            // Return 500 for unrelated database failures
            return Results.Json(
                ApiResponse<object>.Fail("A database error occurred. Please try again later."),
                statusCode: 500);
        }

        return Results.Json(ApiResponse<object>.Ok(new { }, "Email verified and updated."));
    }

    private static async Task<IResult> MeAsync(
        ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);
        return Results.Json(ApiResponse<UserDto>.Ok(user.ToDto()));
    }

    public static string HashToken(string rawToken)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(rawToken));
        return Convert.ToHexString(bytes);
    }
}

internal static class AuthQueryExtensions
{
    public static Task<User?> CurrentUserAsync(this DbSet<User> users, ClaimsPrincipal principal, CancellationToken ct)
    {
        var sub = principal.FindFirstValue(ClaimTypes.NameIdentifier)
                  ?? principal.FindFirstValue("sub");
        return Guid.TryParse(sub, out var id)
            ? users.FirstOrDefaultAsync(u => u.Id == id, ct)
            : Task.FromResult<User?>(null);
    }

    public static UserDto ToDto(this User user) =>
        new(user.Id, user.Email, user.FullName, user.PreferredLanguage, user.CreatedAt, user.HasGoogleAccount);
}
