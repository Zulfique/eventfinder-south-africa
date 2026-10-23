using System.ComponentModel.DataAnnotations;

namespace EventFinder.Api.Dtos;

/// <summary>
/// Payload for uploading an event photo to blob storage.
/// </summary>
public record BlobUploadRequest(
    /// <summary>File name including the extension (for example <c>event.jpg</c>).</summary>
    [property: Required]
    [property: StringLength(120, MinimumLength = 1)]
    string FileName,

    /// <summary>Base64-encoded file bytes.</summary>
    [property: Required]
    string ContentBase64,

    /// <summary>Optional MIME type; inferred from the file name when omitted.</summary>
    [property: StringLength(100)]
    string? ContentType = null
);

/// <summary>Result of a successful blob upload.</summary>
public record BlobUploadResponse(
    /// <summary>Public URL of the stored object.</summary>
    string Url,

    /// <summary>Bucket key of the stored object.</summary>
    string Key,

    /// <summary>Size of the stored object in bytes.</summary>
    long SizeBytes
);
