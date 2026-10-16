using System.ComponentModel.DataAnnotations;

namespace EventFinder.Api.Dtos;

public record RegisterRequest(
    [property: Required, MaxLength(120)] string? FullName,
    [property: Required, EmailAddress, MaxLength(254)] string? Email,
    [property: Required, MinLength(8), MaxLength(128)] string? Password,
    [property: MaxLength(5)] string? Language);

public record LoginRequest(
    [property: Required, EmailAddress, MaxLength(254)] string? Email,
    [property: Required, MaxLength(128)] string? Password);

public record GoogleSignInRequest(
    [property: Required] string? IdToken);

public record ForgotPasswordRequest(
    [property: Required, EmailAddress, MaxLength(254)] string? Email);

public record ResetPasswordRequest(
    [property: Required, EmailAddress, MaxLength(254)] string? Email,
    [property: Required, MinLength(16), MaxLength(256)] string? Token,
    [property: Required, MinLength(8), MaxLength(128)] string? NewPassword);

public record ChangePasswordRequest(
    [property: Required, MaxLength(128)] string? CurrentPassword,
    [property: Required, MinLength(8), MaxLength(128)] string? NewPassword);

public record UpdateProfileRequest(
    [property: Required, MaxLength(120)] string? FullName,
    [property: Required, EmailAddress, MaxLength(254)] string? Email);

public record RequestEmailChangeRequest(
    [property: Required, EmailAddress, MaxLength(254)] string? NewEmail);

public record VerifyEmailChangeRequest(
    [property: Required, MinLength(16), MaxLength(256)] string? Token);

public record CreateEventRequest(
    [property: Required, MaxLength(160)] string? Title,
    [property: Required, MinLength(10), MaxLength(4000)] string? Description,
    [property: Required, MaxLength(160)] string? VenueName,
    [property: MaxLength(240)] string? Address,
    [property: MaxLength(120)] string? City,
    [property: Required, Range(-35.5, -21.5)] double? Latitude,
    [property: Required, Range(16.0, 33.5)] double? Longitude,
    [property: Required] DateTime? StartUtc,
    [property: Required] DateTime? EndUtc,
    [property: MaxLength(80)] string? Category,
    [property: MaxLength(1024)] string? ImageUrl,
    bool IsPublic = true);

public record UpdateEventRequest(
    [property: Required, MaxLength(160)] string? Title,
    [property: Required, MinLength(10), MaxLength(4000)] string? Description,
    [property: Required, MaxLength(160)] string? VenueName,
    [property: MaxLength(240)] string? Address,
    [property: MaxLength(120)] string? City,
    [property: Required, Range(-35.5, -21.5)] double? Latitude,
    [property: Required, Range(16.0, 33.5)] double? Longitude,
    [property: Required] DateTime? StartUtc,
    [property: Required] DateTime? EndUtc,
    [property: MaxLength(80)] string? Category,
    [property: MaxLength(1024)] string? ImageUrl,
    bool IsPublic = true);

public record RsvpRequest(
    [property: Required, RegularExpression("^(attending|maybe|declined)$")] string? Status);

public record UserDto(
    Guid Id,
    string Email,
    string FullName,
    string PreferredLanguage,
    DateTime CreatedAt,
    bool HasGoogleAccount);

public record EventDto(
    Guid Id,
    string Title,
    string Description,
    string VenueName,
    string Address,
    string City,
    double Latitude,
    double Longitude,
    DateTime StartUtc,
    DateTime EndUtc,
    string Category,
    string? ImageUrl,
    Guid OrganizerId,
    string OrganizerName,
    bool IsPublic,
    int AttendeeCount,
    DateTime CreatedAt,
    DateTime UpdatedAt);

public record AuthResponse(UserDto User, string Token, DateTime ExpiresAt);

public record AttendeeDto(Guid UserId, string FullName, string Status, DateTime RsvpUpdatedAt);

public static class RequestValidator
{
    public static Dictionary<string, string[]> Validate(object request)
    {
        var errors = new Dictionary<string, string[]>();
        var context = new ValidationContext(request);
        var results = new List<ValidationResult>();
        Validator.TryValidateObject(request, context, results, validateAllProperties: true);

        foreach (var result in results)
        {
            var members = result.MemberNames.Any() ? result.MemberNames : new[] { "request" };
            foreach (var member in members)
            {
                var key = char.ToLowerInvariant(member[0]) + member[1..];
                if (!errors.TryGetValue(key, out var list))
                {
                    errors[key] = [result.ErrorMessage ?? "Invalid value."];
                }
                else
                {
                    errors[key] = [.. list, result.ErrorMessage ?? "Invalid value."];
                }
            }
        }

        return errors;
    }
}
