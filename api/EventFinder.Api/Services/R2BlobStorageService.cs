using System.Net;
using Amazon;
using Amazon.Runtime;
using Amazon.S3;
using Amazon.S3.Model;
using Microsoft.Extensions.Configuration;

namespace EventFinder.Api.Services;

/// <summary>
/// Blob storage backed by <b>Cloudflare R2</b>.
///
/// R2 speaks the S3 protocol, so the AWS SDK talks to it unchanged. The
/// free tier covers 10 GB of storage, 1 million Class A and 10 million
/// Class B operations per month with zero egress fees - no Blaze billing
/// account required.
///
/// R2 is configured with three environment variables:
/// <list type="bullet">
///   <item><description><c>R2__AccountId</c> - the Cloudflare account id.</description></item>
///   <item><description><c>R2__AccessKeyId</c> - the R2 API token access key id.</description></item>
///   <item><description><c>R2__SecretAccessKey</c> - the R2 API token secret.</description></item>
///   <item><description><c>R2__BucketName</c> - the bucket that stores event photos.</description></item>
///   <item><description><c>R2__PublicBaseUrl</c> - optional custom domain / public bucket URL.</description></item>
/// </list>
///
/// When the credentials are missing the service reports itself as
/// unavailable and every call returns <c>null</c> / fails softly, so the
/// Android app keeps its local-file fallback and nothing breaks.
/// </summary>
public interface IBlobStorageService
{
    /// <summary>True when R2 credentials are configured and the service can be used.</summary>
    bool IsConfigured { get; }

    /// <summary>
    /// Uploads [content] to the bucket under [key] and returns the permanent
    /// public URL, or <c>null</c> when R2 is not configured or the upload failed.
    /// </summary>
    Task<string?> UploadAsync(string key, byte[] content, string contentType, CancellationToken ct = default);

    /// <summary>Best-effort removal of a previously uploaded object.</summary>
    Task<bool> DeleteAsync(string key, CancellationToken ct = default);

    /// <summary>
    /// Maps a stored public URL back to its bucket key, or returns <c>null</c>
    /// when the URL does not belong to this bucket.
    /// </summary>
    string? KeyFromUrl(string? url);
}

public class R2BlobStorageService : IBlobStorageService, IDisposable
{
    private readonly IConfiguration _configuration;
    private readonly ILogger<R2BlobStorageService> _logger;
    private readonly Lazy<IAmazonS3?> _client;

    public R2BlobStorageService(IConfiguration configuration, ILogger<R2BlobStorageService> logger)
    {
        _configuration = configuration;
        _logger = logger;
        _client = new Lazy<IAmazonS3?>(CreateClient, LazyThreadSafetyMode.ExecutionAndPublication);
    }

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(AccountId) &&
        !string.IsNullOrWhiteSpace(AccessKeyId) &&
        !string.IsNullOrWhiteSpace(SecretAccessKey) &&
        !string.IsNullOrWhiteSpace(BucketName);

    private string? AccountId => _configuration["R2:AccountId"];
    private string? AccessKeyId => _configuration["R2:AccessKeyId"];
    private string? SecretAccessKey => _configuration["R2:SecretAccessKey"];
    private string? BucketName => _configuration["R2:BucketName"];

    /// <summary>
    /// Public base URL for the bucket. Defaults to the standard R2 dev URL
    /// (<c>https://&lt;account&gt;.r2.cloudflarestorage.com/&lt;bucket&gt;</c>)
    /// and can be overridden with <c>R2__PublicBaseUrl</c> when a custom
    /// domain is configured.
    /// </summary>
    private string PublicBaseUrl
    {
        get
        {
            var configured = _configuration["R2:PublicBaseUrl"];
            if (!string.IsNullOrWhiteSpace(configured))
            {
                return configured.TrimEnd('/');
            }

            return $"https://{AccountId}.r2.cloudflarestorage.com/{BucketName}";
        }
    }

    private IAmazonS3? CreateClient()
    {
        if (!IsConfigured)
        {
            _logger.LogWarning("R2 is not configured - blob uploads fall back to local file storage.");
            return null;
        }

        try
        {
            var credentials = new BasicAWSCredentials(AccessKeyId!, SecretAccessKey!);
            var config = new AmazonS3Config
            {
                ServiceURL = $"https://{AccountId}.r2.cloudflarestorage.com",
                AuthenticationRegion = "auto",
                ForcePathStyle = true,
                UseHttp = false,
            };

            return new AmazonS3Client(credentials, config);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to build the Cloudflare R2 client.");
            return null;
        }
    }

    public async Task<string?> UploadAsync(string key, byte[] content, string contentType, CancellationToken ct = default)
    {
        var client = _client.Value;
        if (client is null || content.Length == 0)
        {
            return null;
        }

        try
        {
            using var stream = new MemoryStream(content, writable: false);
            var request = new PutObjectRequest
            {
                BucketName = BucketName,
                Key = key,
                InputStream = stream,
                ContentType = contentType,
                AutoCloseStream = false,
            };

            await client.PutObjectAsync(request, ct);

            var url = $"{PublicBaseUrl}/{key}";
            _logger.LogInformation("Uploaded blob to Cloudflare R2: {Key}", key);
            return url;
        }
        catch (AmazonS3Exception ex)
        {
            _logger.LogError(ex, "R2 upload failed for {Key} (HTTP {Status}).", key, ex.StatusCode);
            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "R2 upload failed for {Key}.", key);
            return null;
        }
    }

    public async Task<bool> DeleteAsync(string key, CancellationToken ct = default)
    {
        var client = _client.Value;
        if (client is null) return false;

        try
        {
            await client.DeleteObjectAsync(BucketName, key, ct);
            _logger.LogInformation("Deleted blob from Cloudflare R2: {Key}", key);
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "R2 delete skipped for {Key}.", key);
            return false;
        }
    }

    public string? KeyFromUrl(string? url)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;
        if (!url.StartsWith("http", StringComparison.OrdinalIgnoreCase)) return null;

        var baseUrl = PublicBaseUrl;
        if (url.StartsWith(baseUrl, StringComparison.OrdinalIgnoreCase))
        {
            var key = url[baseUrl.Length..].TrimStart('/');
            return string.IsNullOrWhiteSpace(key) ? null : Uri.UnescapeDataString(key);
        }

        // Fall back to the canonical dev URL when a custom domain is in play.
        if (!string.IsNullOrWhiteSpace(AccountId) && !string.IsNullOrWhiteSpace(BucketName))
        {
            var canonical = $"https://{AccountId}.r2.cloudflarestorage.com/{BucketName}/";
            if (url.StartsWith(canonical, StringComparison.OrdinalIgnoreCase))
            {
                return Uri.UnescapeDataString(url[canonical.Length..]);
            }
        }

        return null;
    }

    public void Dispose()
    {
        if (_client.IsValueCreated)
        {
            _client.Value?.Dispose();
        }
        GC.SuppressFinalize(this);
    }
}
