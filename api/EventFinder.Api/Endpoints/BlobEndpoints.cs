using System.Security.Claims;
using EventFinder.Api.Common;
using EventFinder.Api.Data;
using EventFinder.Api.Dtos;
using EventFinder.Api.Services;
using Microsoft.EntityFrameworkCore;

namespace EventFinder.Api.Endpoints;

/// <summary>
/// Blob-storage endpoints backed by Azure Blob Storage.
///
/// The Android app uploads the event photo here instead of talking to a
/// cloud storage service directly. The API owns the storage credentials so
/// the account key never reaches the handset, and the phone only ever
/// handles the resulting public URL.
/// </summary>
public static class BlobEndpoints
{
    /// <summary>Maximum accepted upload size: 8 MB.</summary>
    private const long MaxUploadBytes = 8L * 1024 * 1024;

    public static RouteGroupBuilder MapBlobEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/blobs").WithTags("Blobs");

        group.MapPost("/upload", UploadAsync).RequireAuthorization();
        // Catch-all: blob keys are hierarchical ("event_images/<userId>/<file>.png"),
        // so a single-segment route would never match one. Clients URL-encode the
        // key on the way in, hence the explicit unescape in DeleteAsync.
        group.MapDelete("/{*key}", DeleteAsync).RequireAuthorization();
        group.MapGet("/status", Status);

        return group;
    }

    private static IResult Status(IBlobStorageService storage) =>
        Results.Json(ApiResponse<object>.Ok(new
        {
            configured = storage.IsConfigured,
            provider = "azure-blob-storage",
        }));

    private static async Task<IResult> UploadAsync(
        BlobUploadRequest request,
        ClaimsPrincipal principal,
        AppDbContext db,
        IBlobStorageService storage,
        CancellationToken ct)
    {
        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        if (!storage.IsConfigured)
        {
            return Results.Json(
                ApiResponse<object>.Fail("Blob storage is not configured on this server."),
                statusCode: StatusCodes.Status503ServiceUnavailable);
        }

        byte[] bytes;
        try
        {
            bytes = Convert.FromBase64String(request.ContentBase64);
        }
        catch (FormatException)
        {
            return Results.Json(
                ApiResponse<object>.Fail("ContentBase64 is not valid base64."),
                statusCode: StatusCodes.Status400BadRequest);
        }

        if (bytes.Length == 0)
        {
            return Results.Json(
                ApiResponse<object>.Fail("The uploaded file is empty."),
                statusCode: StatusCodes.Status400BadRequest);
        }

        if (bytes.Length > MaxUploadBytes)
        {
            return Results.Json(
                ApiResponse<object>.Fail("The file is larger than the 8 MB limit."),
                statusCode: StatusCodes.Status413PayloadTooLarge);
        }

        var extension = Path.GetExtension(request.FileName);
        if (string.IsNullOrWhiteSpace(extension) || extension.Length > 10)
        {
            extension = ".jpg";
        }

        // Namespace every object by user so one account can never overwrite
        // another account's photos, and sanitise the extension.
        var safeExtension = extension.ToLowerInvariant();
        if (safeExtension is not (".jpg" or ".jpeg" or ".png" or ".webp" or ".gif"))
        {
            safeExtension = ".jpg";
        }

        var key = $"event_images/{user.Id}/{Guid.NewGuid():N}{safeExtension}";
        var contentType = string.IsNullOrWhiteSpace(request.ContentType)
            ? ContentTypeFor(safeExtension)
            : request.ContentType.Trim();

        var url = await storage.UploadAsync(key, bytes, contentType, ct);
        if (url is null)
        {
            return Results.Json(
                ApiResponse<object>.Fail("The blob upload failed."),
                statusCode: StatusCodes.Status502BadGateway);
        }

        return Results.Json(ApiResponse<BlobUploadResponse>.Ok(
            new BlobUploadResponse(url, key, bytes.Length),
            "Uploaded."));
    }

    private static async Task<IResult> DeleteAsync(
        string? key,
        ClaimsPrincipal principal,
        AppDbContext db,
        IBlobStorageService storage,
        CancellationToken ct)
    {
        var user = await db.Users.CurrentUserAsync(principal, ct);
        if (user is null)
            return Results.Json(ApiResponse<object>.Fail("Unauthorized."), statusCode: 401);

        if (!storage.IsConfigured)
        {
            return Results.Json(
                ApiResponse<object>.Fail("Blob storage is not configured on this server."),
                statusCode: StatusCodes.Status503ServiceUnavailable);
        }

        // The key reaches us either with its slashes intact or still
        // percent-encoded, depending on how the client built the URL. Normalise
        // both before the ownership check, otherwise a correctly-owned key would
        // be rejected as foreign.
        var blobKey = UnescapeKey(key);
        if (blobKey is null)
        {
            return Results.Json(
                ApiResponse<object>.Fail("A blob key is required."),
                statusCode: StatusCodes.Status400BadRequest);
        }

        // Objects live under "event_images/<userId>/..." - only the owner may
        // delete their own photos.
        var prefix = $"event_images/{user.Id}/";
        if (!blobKey.StartsWith(prefix, StringComparison.Ordinal))
        {
            return Results.Json(
                ApiResponse<object>.Fail("You can only delete your own uploads."),
                statusCode: StatusCodes.Status403Forbidden);
        }

        var deleted = await storage.DeleteAsync(blobKey, ct);
        return deleted
            ? Results.Json(ApiResponse<object>.Ok(new { }, "Deleted."))
            : Results.Json(
                ApiResponse<object>.Fail("The blob could not be deleted."),
                statusCode: StatusCodes.Status502BadGateway);
    }

    /// <summary>
    /// Decodes a routed blob key. Returns <c>null</c> when there is no key or
    /// when it contains an escape sequence we would rather reject than guess at.
    /// </summary>
    private static string? UnescapeKey(string? key)
    {
        if (string.IsNullOrWhiteSpace(key))
        {
            return null;
        }

        var trimmed = key.Trim().TrimStart('/');
        if (trimmed.Length == 0)
        {
            return null;
        }

        try
        {
            trimmed = Uri.UnescapeDataString(trimmed);
        }
        catch (UriFormatException)
        {
            return null;
        }

        // Collapse a leading slash left behind by a decoded %2F.
        trimmed = trimmed.TrimStart('/');
        return trimmed.Length == 0 ? null : trimmed;
    }

    private static string ContentTypeFor(string extension) => extension switch
    {
        ".png" => "image/png",
        ".webp" => "image/webp",
        ".gif" => "image/gif",
        ".jpeg" => "image/jpeg",
        _ => "image/jpeg",
    };
}
