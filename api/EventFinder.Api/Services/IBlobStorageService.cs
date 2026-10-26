namespace EventFinder.Api.Services;

/// <summary>
/// The blob-storage abstraction the API talks to, currently backed by Azure
/// Blob Storage (see <see cref="AzureBlobStorageService"/>).
///
/// The Android app never talks to a storage vendor directly: it posts the
/// photo bytes to <c>POST /api/blobs/upload</c> and stores the public URL that
/// comes back, so this interface is the whole surface the REST layer needs.
///
/// Every member fails softly. When the storage credentials are missing or the
/// vendor is unreachable, the service reports <see cref="IsConfigured"/> as
/// false and the callers answer 503 instead of throwing, which keeps the app's
/// local-file fallback working offline.
/// </summary>
public interface IBlobStorageService
{
    /// <summary>True when storage credentials are configured and the service can be used.</summary>
    bool IsConfigured { get; }

    /// <summary>
    /// Uploads <paramref name="content"/> under <paramref name="key"/> and returns the
    /// permanent public URL, or <c>null</c> when storage is not configured or the
    /// upload failed.
    /// </summary>
    Task<string?> UploadAsync(string key, byte[] content, string contentType, CancellationToken ct = default);

    /// <summary>Best-effort removal of a previously uploaded object.</summary>
    Task<bool> DeleteAsync(string key, CancellationToken ct = default);

    /// <summary>
    /// Maps a stored public URL back to its object key, or returns <c>null</c>
    /// when the URL does not belong to this container.
    /// </summary>
    string? KeyFromUrl(string? url);
}
