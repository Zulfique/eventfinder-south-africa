using System.Security.Claims;
using EventFinder.Api.Common;
using EventFinder.Api.Data;
using EventFinder.Api.Dtos;
using Microsoft.EntityFrameworkCore;

namespace EventFinder.Api.Endpoints;

/// <summary>
/// Ratings and reviews for every event in the app. Event ids are strings so
/// imported (RSS/ICS) events can carry reviews alongside Guid API events.
/// One review per user per event, upserted through PUT.
/// </summary>
public static class ReviewEndpoints
{
    public static RouteGroupBuilder MapReviewEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api").WithTags("Reviews");

        group.MapGet("/events/{eventId}/reviews", GetReviewsAsync);
        group.MapPut("/events/{eventId}/reviews", UpsertReviewAsync).RequireAuthorization();
        group.MapDelete("/events/{eventId}/reviews", DeleteReviewAsync).RequireAuthorization();
        group.MapGet("/reviews/summary", SummaryAsync);

        return group;
    }

    private static async Task<IResult> GetReviewsAsync(
        string eventId, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        if (!TryNormalize(eventId, out var key))
            return ApiError.BadRequest("Invalid event id.");

        var list = await BuildListAsync(db, key, principal, ct);
        return Results.Json(ApiResponse<ReviewListDto>.Ok(list));
    }

    private static async Task<IResult> UpsertReviewAsync(
        string eventId, UpsertReviewRequest request, ClaimsPrincipal principal,
        AppDbContext db, CancellationToken ct)
    {
        if (!TryNormalize(eventId, out var key))
            return ApiError.BadRequest("Invalid event id.");

        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<ReviewListDto>.Fail("Validation failed.", errors), statusCode: 400);

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return ApiError.Unauthorized();

        var now = DateTime.UtcNow;
        var existing = await db.EventReviews
            .FirstOrDefaultAsync(r => r.EventId == key && r.UserId == user.Id, ct);

        if (existing is null)
        {
            db.EventReviews.Add(new EventReview
            {
                Id = Guid.NewGuid(),
                EventId = key,
                UserId = user.Id,
                Rating = request.Rating,
                Comment = NormalizeComment(request.Comment),
                IsVerifiedAttendee = await IsVerifiedAttendeeAsync(db, key, user.Id, ct),
                CreatedAt = now,
                UpdatedAt = now,
            });
        }
        else
        {
            existing.Rating = request.Rating;
            existing.Comment = NormalizeComment(request.Comment);
            existing.IsVerifiedAttendee = await IsVerifiedAttendeeAsync(db, key, user.Id, ct);
            existing.UpdatedAt = now;
        }

        await db.SaveChangesAsync(ct);

        var list = await BuildListAsync(db, key, principal, ct);
        return Results.Json(ApiResponse<ReviewListDto>.Ok(list, "Review saved."));
    }

    private static async Task<IResult> DeleteReviewAsync(
        string eventId, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        if (!TryNormalize(eventId, out var key))
            return ApiError.BadRequest("Invalid event id.");

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return ApiError.Unauthorized();

        var existing = await db.EventReviews
            .FirstOrDefaultAsync(r => r.EventId == key && r.UserId == user.Id, ct);
        if (existing is not null)
        {
            db.EventReviews.Remove(existing);
            await db.SaveChangesAsync(ct);
        }

        var list = await BuildListAsync(db, key, principal, ct);
        return Results.Json(ApiResponse<ReviewListDto>.Ok(list, "Review deleted."));
    }

    /// <summary>
    /// Batch rating summaries for event cards:
    /// GET /api/reviews/summary?id=a&id=b (up to 100 ids per call).
    /// </summary>
    private static async Task<IResult> SummaryAsync(
        string[]? id, AppDbContext db, CancellationToken ct)
    {
        var ids = (id ?? Array.Empty<string>())
            .Where(s => !string.IsNullOrWhiteSpace(s))
            .Select(s => s.Trim())
            .Where(s => s.Length <= 64)
            .Distinct()
            .Take(100)
            .ToList();

        if (ids.Count == 0)
            return Results.Json(ApiResponse<List<ReviewSummaryItemDto>>.Ok(new List<ReviewSummaryItemDto>()));

        var grouped = await db.EventReviews.AsNoTracking()
            .Where(r => ids.Contains(r.EventId))
            .GroupBy(r => r.EventId)
            .Select(g => new ReviewSummaryItemDto(
                g.Key,
                Math.Round(g.Average(r => r.Rating), 1),
                g.Count()))
            .ToListAsync(ct);

        return Results.Json(ApiResponse<List<ReviewSummaryItemDto>>.Ok(grouped));
    }

    private static async Task<ReviewListDto> BuildListAsync(
        AppDbContext db, string eventId, ClaimsPrincipal principal, CancellationToken ct)
    {
        var all = await db.EventReviews.AsNoTracking()
            .Include(r => r.User)
            .Where(r => r.EventId == eventId)
            .OrderByDescending(r => r.CreatedAt)
            .Take(50)
            .ToListAsync(ct);

        var count = await db.EventReviews.CountAsync(r => r.EventId == eventId, ct);
        var average = count == 0
            ? 0
            : Math.Round(
                await db.EventReviews
                    .Where(r => r.EventId == eventId)
                    .AverageAsync(r => (double)r.Rating, ct),
                1);

        var currentUserId = GetUserId(principal);
        var items = all.Select(ToDto).ToList();
        var mine = currentUserId is null
            ? null
            : items.FirstOrDefault(i => i.UserId == currentUserId);

        return new ReviewListDto(average, count, items, mine);
    }

    private static ReviewDto ToDto(EventReview r) => new(
        r.Id,
        r.EventId,
        r.UserId,
        r.User?.FullName ?? "Member",
        r.Rating,
        r.Comment,
        r.IsVerifiedAttendee,
        r.CreatedAt,
        r.UpdatedAt);

    private static Guid? GetUserId(ClaimsPrincipal principal)
    {
        var raw = principal.FindFirstValue(ClaimTypes.NameIdentifier)
                  ?? principal.FindFirstValue("sub");
        return Guid.TryParse(raw, out var id) ? id : null;
    }

    private static async Task<bool> IsVerifiedAttendeeAsync(
        AppDbContext db, string eventId, Guid userId, CancellationToken ct)
    {
        // Imported events have no Guid RSVP record, so they can never verify.
        if (!Guid.TryParse(eventId, out var guid))
            return false;

        return await db.Rsvps.AnyAsync(
            r => r.EventId == guid && r.UserId == userId && r.Status == "attending", ct);
    }

    private static string? NormalizeComment(string? comment)
    {
        var trimmed = comment?.Trim();
        return string.IsNullOrEmpty(trimmed) ? null : trimmed;
    }

    private static bool TryNormalize(string? raw, out string key)
    {
        key = (raw ?? string.Empty).Trim();
        return key.Length is > 0 and <= 64;
    }
}
