using System.Security.Claims;
using EventFinder.Api.Common;
using EventFinder.Api.Data;
using EventFinder.Api.Dtos;
using Microsoft.EntityFrameworkCore;

namespace EventFinder.Api.Endpoints;

public static class EventEndpoints
{
    public static RouteGroupBuilder MapEventEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/events").WithTags("Events");

        group.MapGet("/public", ListPublicAsync);
        group.MapGet("/my-rsvps", MyRsvpsAsync).RequireAuthorization();
        group.MapGet("/{id:guid}", GetAsync);
        group.MapPost("/", CreateAsync).RequireAuthorization();
        group.MapPut("/{id:guid}", UpdateAsync).RequireAuthorization();
        group.MapDelete("/{id:guid}", DeleteAsync).RequireAuthorization();

        group.MapPost("/{id:guid}/rsvp", RsvpAsync).RequireAuthorization();
        group.MapDelete("/{id:guid}/rsvp", CancelRsvpAsync).RequireAuthorization();
        group.MapGet("/{id:guid}/attendees", AttendeesAsync);

        return group;
    }

    private static async Task<IResult> ListPublicAsync(
        AppDbContext db, string? city, double? lat, double? lng, double? radiusKm, CancellationToken ct)
    {
        var query = db.Events.Where(e => e.IsPublic && e.StartUtc >= DateTime.UtcNow);

        if (!string.IsNullOrWhiteSpace(city))
        {
            var needle = city.Trim();
            query = query.Where(e => EF.Functions.Like(e.City, $"%{needle}%"));
        }

        if (lat is not null && lng is not null && radiusKm is > 0)
        {
            // Approximate degree window, then exact Haversine filter in memory
            // (fine at this catalogue size; keeps the SQLite provider happy).
            var latDelta = radiusKm.Value / 111.0;
            var lngDelta = radiusKm.Value / (111.0 * Math.Cos(lat.Value * Math.PI / 180.0));
            var candidates = await query
                .Where(e => e.Latitude >= lat - latDelta && e.Latitude <= lat + latDelta
                            && e.Longitude >= lng - lngDelta && e.Longitude <= lng + lngDelta)
                .OrderBy(e => e.StartUtc)
                .AsNoTracking()
                .ToListAsync(ct);
            var filtered = candidates
                .Where(e => HaversineKm(lat!.Value, lng!.Value, e.Latitude, e.Longitude) <= radiusKm!.Value)
                .ToList();
            return Results.Json(ApiResponse<List<EventDto>>.Ok(filtered.Select(ToDto).ToList()));
        }

        var events = await query.OrderBy(e => e.StartUtc).AsNoTracking().ToListAsync(ct);
        return Results.Json(ApiResponse<List<EventDto>>.Ok(events.Select(ToDto).ToList()));
    }

    private static async Task<IResult> GetAsync(Guid id, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var ev = await db.Events.AsNoTracking().FirstOrDefaultAsync(e => e.Id == id, ct);
        if (ev is null)
            return Results.Json(ApiResponse<object>.Fail("Event not found."), statusCode: 404);

        if (!ev.IsPublic)
        {
            var user = await db.Users.CurrentUserAsync(principal, ct);
            if (user is null)
                return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);
            var isAttendee = await db.Rsvps.AsNoTracking().AnyAsync(r => r.EventId == ev.Id && r.UserId == user.Id, ct);
            if (ev.OrganizerId != user.Id && !isAttendee)
                return Results.Json(ApiResponse<object>.Fail("Forbidden."), statusCode: 403);
        }

        return Results.Json(ApiResponse<EventDto>.Ok(ToDto(ev)));
    }

    private static async Task<IResult> MyRsvpsAsync(
        ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var rsvps = await db.Rsvps.AsNoTracking()
            .Where(r => r.UserId == user.Id)
            .OrderBy(r => r.UpdatedAt)
            .ToListAsync(ct);
        var eventIds = rsvps.Select(r => r.EventId).ToList();
        var events = await db.Events.AsNoTracking()
            .Where(e => eventIds.Contains(e.Id))
            .ToDictionaryAsync(e => e.Id, ct);
        var items = rsvps
            .Where(r => events.TryGetValue(r.EventId, out _))
            .Select(r => (object)new
            {
                Event = ToDto(events[r.EventId]),
                r.Status,
                RsvpUpdatedAt = r.UpdatedAt,
            })
            .ToList();

        return Results.Json(ApiResponse<List<object>>.Ok(items.Cast<object>().ToList()));
    }

    private static async Task<IResult> CreateAsync(
        CreateEventRequest request, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<EventDto>.Fail("Validation failed.", errors), statusCode: 400);

        if (request.EndUtc <= request.StartUtc)
        {
            return Results.Json(
                ApiResponse<EventDto>.Fail("Validation failed.",
                    new Dictionary<string, string[]> { ["endUtc"] = ["End must be after start."] }),
                statusCode: 400);
        }

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var now = DateTime.UtcNow;
        var ev = new EventEntity
        {
            Id = Guid.NewGuid(), // server-generated identifier
            Title = request.Title!.Trim(),
            Description = request.Description!.Trim(),
            VenueName = request.VenueName!.Trim(),
            Address = request.Address?.Trim() ?? string.Empty,
            City = request.City?.Trim() ?? string.Empty,
            Latitude = request.Latitude!.Value,
            Longitude = request.Longitude!.Value,
            StartUtc = request.StartUtc!.Value.ToUniversalTime(),
            EndUtc = request.EndUtc!.Value.ToUniversalTime(),
            Category = request.Category?.Trim() ?? string.Empty,
            ImageUrl = request.ImageUrl,
            OrganizerId = user.Id,
            OrganizerName = user.FullName,
            IsPublic = request.IsPublic,
            AttendeeCount = 0,
            CreatedAt = now, // server-generated timestamp
            UpdatedAt = now,
        };
        db.Events.Add(ev);
        await db.SaveChangesAsync(ct);

        return Results.Json(ApiResponse<EventDto>.Ok(ToDto(ev), "Event created."), statusCode: 201);
    }

    private static async Task<IResult> UpdateAsync(
        Guid id, UpdateEventRequest request, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<EventDto>.Fail("Validation failed.", errors), statusCode: 400);

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var ev = await db.Events.FirstOrDefaultAsync(e => e.Id == id, ct);
        if (ev is null)
            return Results.Json(ApiResponse<object>.Fail("Event not found."), statusCode: 404);
        if (ev.OrganizerId != user.Id)
            return Results.Json(ApiResponse<object>.Fail("Only the organiser can edit this event."), statusCode: 403);

        if (request.EndUtc <= request.StartUtc)
        {
            return Results.Json(
                ApiResponse<EventDto>.Fail("Validation failed.",
                    new Dictionary<string, string[]> { ["endUtc"] = ["End must be after start."] }),
                statusCode: 400);
        }

        ev.Title = request.Title!.Trim();
        ev.Description = request.Description!.Trim();
        ev.VenueName = request.VenueName!.Trim();
        ev.Address = request.Address?.Trim() ?? string.Empty;
        ev.City = request.City?.Trim() ?? string.Empty;
        ev.Latitude = request.Latitude!.Value;
        ev.Longitude = request.Longitude!.Value;
        ev.StartUtc = request.StartUtc!.Value.ToUniversalTime();
        ev.EndUtc = request.EndUtc!.Value.ToUniversalTime();
        ev.Category = request.Category?.Trim() ?? string.Empty;
        ev.ImageUrl = request.ImageUrl;
        ev.IsPublic = request.IsPublic;
        ev.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);

        return Results.Json(ApiResponse<EventDto>.Ok(ToDto(ev), "Event updated."));
    }

    private static async Task<IResult> DeleteAsync(
        Guid id, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var ev = await db.Events.FirstOrDefaultAsync(e => e.Id == id, ct);
        if (ev is null)
            return Results.Json(ApiResponse<object>.Fail("Event not found."), statusCode: 404);
        if (ev.OrganizerId != user.Id)
            return Results.Json(ApiResponse<object>.Fail("Only the organiser can delete this event."), statusCode: 403);

        db.Events.Remove(ev);
        await db.SaveChangesAsync(ct);
        return Results.Json(ApiResponse<object>.Ok(new { }, "Event deleted."));
    }

    private static async Task<IResult> RsvpAsync(
        Guid id, RsvpRequest request, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var errors = RequestValidator.Validate(request);
        if (errors.Count > 0)
            return Results.Json(ApiResponse<AttendeeDto>.Fail("Validation failed.", errors), statusCode: 400);

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var ev = await db.Events.FirstOrDefaultAsync(e => e.Id == id, ct);
        if (ev is null)
            return Results.Json(ApiResponse<object>.Fail("Event not found."), statusCode: 404);

        var status = request.Status!;
        var now = DateTime.UtcNow;

        try
        {
            await using var tx = await db.Database.BeginTransactionAsync(ct);
            try
            {
                var existing = await db.Rsvps.FirstOrDefaultAsync(r => r.EventId == id && r.UserId == user.Id, ct);
                if (existing is null)
                {
                    existing = new Rsvp
                    {
                        Id = Guid.NewGuid(),
                        EventId = id,
                        UserId = user.Id,
                        Status = status,
                        CreatedAt = now,
                        UpdatedAt = now,
                    };
                    db.Rsvps.Add(existing);
                }
                else
                {
                    existing.Status = status;
                    existing.UpdatedAt = now;
                }

                await db.SaveChangesAsync(ct);
                ev.AttendeeCount = await db.Rsvps.CountAsync(r => r.EventId == id && r.Status == "attending", ct);
                await db.SaveChangesAsync(ct);
                await tx.CommitAsync(ct);
            }
            catch
            {
                await tx.RollbackAsync(ct);
                throw;
            }
        }
        catch (DbUpdateException)
        {
            // Concurrent insert on the unique (EventId, UserId) index: reload
            // the winning row, apply this request's status and recompute.
            var existing = await db.Rsvps.FirstOrDefaultAsync(r => r.EventId == id && r.UserId == user.Id, ct);
            if (existing is null)
            {
                return Results.Json(ApiResponse<object>.Fail("RSVP conflict."), statusCode: 409);
            }
            existing.Status = status;
            existing.UpdatedAt = now;
            await db.SaveChangesAsync(ct);
            ev.AttendeeCount = await db.Rsvps.CountAsync(r => r.EventId == id && r.Status == "attending", ct);
            await db.SaveChangesAsync(ct);
        }

        return Results.Json(ApiResponse<AttendeeDto>.Ok(
            new AttendeeDto(user.Id, user.FullName, status, now), "RSVP saved."));
    }

    private static async Task<IResult> CancelRsvpAsync(
        Guid id, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        var ev = await db.Events.FirstOrDefaultAsync(e => e.Id == id, ct);
        if (ev is null)
            return Results.Json(ApiResponse<object>.Fail("Event not found."), statusCode: 404);

        var existing = await db.Rsvps.FirstOrDefaultAsync(r => r.EventId == id && r.UserId == user.Id, ct);
        if (existing is not null)
        {
            await using var tx = await db.Database.BeginTransactionAsync(ct);
            try
            {
                db.Rsvps.Remove(existing);
                await db.SaveChangesAsync(ct);
                ev.AttendeeCount = await db.Rsvps.CountAsync(r => r.EventId == id && r.Status == "attending", ct);
                await db.SaveChangesAsync(ct);
                await tx.CommitAsync(ct);
            }
            catch
            {
                await tx.RollbackAsync(ct);
                throw;
            }
        }

        return Results.Json(ApiResponse<object>.Ok(new { }, "RSVP removed."));
    }

    private static async Task<IResult> AttendeesAsync(Guid id, ClaimsPrincipal principal, AppDbContext db, CancellationToken ct)
    {
        var ev = await db.Events.AsNoTracking().FirstOrDefaultAsync(e => e.Id == id, ct);
        if (ev is null)
            return Results.Json(ApiResponse<object>.Fail("Event not found."), statusCode: 404);

        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        if (!ev.IsPublic && ev.OrganizerId != user.Id)
            return Results.Json(ApiResponse<object>.Fail("Forbidden."), statusCode: 403);

        var rsvps = await db.Rsvps
            .AsNoTracking()
            .Where(r => r.EventId == id && r.Status == "attending")
            .OrderBy(r => r.UpdatedAt)
            .ToListAsync(ct);
        var userIds = rsvps.Select(r => r.UserId).ToList();
        var users = await db.Users.AsNoTracking()
            .Where(u => userIds.Contains(u.Id))
            .ToDictionaryAsync(u => u.Id, ct);
        var attendees = rsvps
            .Where(r => users.TryGetValue(r.UserId, out _))
            .Select(r => new AttendeeDto(users[r.UserId].Id, users[r.UserId].FullName, r.Status, r.UpdatedAt))
            .ToList();

        return Results.Json(ApiResponse<List<AttendeeDto>>.Ok(attendees));
    }

    private static double HaversineKm(double lat1, double lon1, double lat2, double lon2)
    {
        const double R = 6371.0;
        var dLat = (lat2 - lat1) * Math.PI / 180.0;
        var dLon = (lon2 - lon1) * Math.PI / 180.0;
        var a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2)
                + Math.Cos(lat1 * Math.PI / 180.0) * Math.Cos(lat2 * Math.PI / 180.0)
                * Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
        return R * 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));
    }

    internal static EventDto ToDto(EventEntity e) => new(
        e.Id, e.Title, e.Description, e.VenueName, e.Address, e.City,
        e.Latitude, e.Longitude, e.StartUtc, e.EndUtc, e.Category, e.ImageUrl,
        e.OrganizerId, e.OrganizerName, e.IsPublic, e.AttendeeCount,
        e.CreatedAt, e.UpdatedAt);
}
