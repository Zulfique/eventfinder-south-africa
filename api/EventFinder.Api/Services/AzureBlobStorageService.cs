using Azure;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using Azure.Storage.Blobs.Specialized;
using Microsoft.Extensions.Configuration;

namespace EventFinder.Api.Services;

/// <summary>
/// Blob storage backed by <b>Azure Storage Accounts (Azure Blob Storage)</b>.
///
/// Azure's free tier covers 5 GB of LRS blob storage plus 20,000 reads,
/// 10,000 writes and 15 GB of egress per month — ample headroom for this
/// prototype app and, crucially, it needs no billing/Blaze-style account.
///
/// The service is configured with environment variables that map to the
/// <c>AzureStorage</c> configuration section:
/// <list type="bullet">
///   <item><description><c>AzureStorage__ConnectionString</c> - the Azure Storage connection string.</description></item>
///   <item><description><c>AzureStorage__ContainerName</c> - the blob container that stores event photos.</description></item>
///   <item><description><c>AzureStorage__PublicBaseUrl</c> - optional custom domain for the container; defaults to the blob service URL.</description></item>
/// </list>
///
/// When the credentials are missing the service reports itself as
/// unavailable and every call returns <c>null</c> / fails softly, so the
/// Android app keeps its local-file fallback and nothing breaks.
/// </summary>
public interface IBlobStorageService
{
    /// <summary>True when blob credentials are configured and the service can be used.</summary>
    bool IsConfigured { get; }

    /// <summary>
    /// Uploads [content] to the container under [key] and returns the permanent
    /// public URL, or <c>null</c> when blob storage is not configured or the upload failed.
    /// </summary>
    Task<string?> UploadAsync(string key, byte[] content, string contentType, CancellationToken ct = default);

    /// <summary>Best-effort removal of a previously uploaded object.</summary>
    Task<bool> DeleteAsync(string key, CancellationToken ct = default);

    /// <summary>
    /// Maps a stored public URL back to its blob key, or returns <c>null</c>
    /// when the URL does not belong to this container.
    /// </summary>
    string? KeyFromUrl(string? url);
}

public class AzureBlobStorageService : IBlobStorageService, IDisposable
{
    private readonly IConfiguration _configuration;
    private readonly ILogger<AzureBlobStorageService> _logger;
    private readonly Lazy<BlobContainerClient?> _container;

    public AzureBlobStorageService(IConfiguration configuration, ILogger<AzureBlobStorageService> logger)
    {
        _configuration = configuration;
        _logger = logger;
        _container = new Lazy<BlobContainerClient?>(CreateContainerClient, LazyThreadSafetyMode.ExecutionAndPublication);
    }

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(ConnectionString) &&
        !string.IsNullOrWhiteSpace(ContainerName);

    private string? ConnectionString => _configuration["AzureStorage:ConnectionString"];
    private string? ContainerName => _configuration["AzureStorage:ContainerName"];

    /// <summary>
    /// Public base URL for the container. Defaults to the Azure blob service
    /// endpoint and can be overridden with <c>AzureStorage:PublicBaseUrl</c>
    /// when a custom domain or CDN front is configured.
    /// </summary>
    private string PublicBaseUrl
    {
        get
        {
            var configured = _configuration["AzureStorage:PublicBaseUrl"];
            if (!string.IsNullOrWhiteSpace(configured))
            {
                return configured.TrimEnd('/');
            }

            var client = _container.Value;
            return client?.Uri.ToString().TrimEnd('/') ?? string.Empty;
        }
    }

    private BlobContainerClient? CreateContainerClient()
    {
        if (!IsConfigured)
        {
            _logger.LogWarning("Azure Blob Storage is not configured — blob uploads fall back to local file storage.");
            return null;
        }

        try
        {
            var blobServiceClient = new BlobServiceClient(ConnectionString!);
            var client = blobServiceClient.GetBlobContainerClient(ContainerName!);

            // Create the container if it does not already exist so the first
            // upload succeeds without a manual provisioning step.
            client.CreateIfNotExists(PublicAccessType.Blob);

            _logger.LogInformation("Azure Blob Storage container ready: {Container}", ContainerName);
            return client;
        }
        catch (RequestFailedException ex)
        {
            _logger.LogError(ex, "Failed to build the Azure Blob Storage client (HTTP {Status}).", ex.Status);
            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to build the Azure Blob Storage client.");
            return null;
        }
    }

    public async Task<string?> UploadAsync(string key, byte[] content, string contentType, CancellationToken ct = default)
    {
        var container = _container.Value;
        if (container is null || content.Length == 0)
        {
            return null;
        }

        try
        {
            using var stream = new MemoryStream(content, writable: false);
            var blob = container.GetBlobClient(key);

            await blob.UploadAsync(stream, overwrite: true, cancellationToken: ct);
            await blob.SetHttpHeadersAsync(new BlobHttpHeaders { ContentType = contentType }, null, ct);

            var url = $"{PublicBaseUrl}/{key}";
            _logger.LogInformation("Uploaded blob to Azure Blob Storage: {Key}", key);
            return url;
        }
        catch (RequestFailedException ex)
        {
            _logger.LogError(ex, "Azure upload failed for {Key} (HTTP {Status}).", key, ex.Status);
            return null;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Azure upload failed for {Key}.", key);
            return null;
        }
    }

    public async Task<bool> DeleteAsync(string key, CancellationToken ct = default)
    {
        var container = _container.Value;
        if (container is null) return false;

        try
        {
            var blob = container.GetBlobClient(key);
            await blob.DeleteIfExistsAsync(cancellationToken: ct);
            _logger.LogInformation("Deleted blob from Azure Blob Storage: {Key}", key);
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Azure blob delete skipped for {Key}.", key);
            return false;
        }
    }

    public string? KeyFromUrl(string? url)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;
        if (!url.StartsWith("http", StringComparison.OrdinalIgnoreCase)) return null;

        var baseUrl = PublicBaseUrl;
        if (!string.IsNullOrWhiteSpace(baseUrl) && url.StartsWith(baseUrl, StringComparison.OrdinalIgnoreCase))
        {
            var key = url[baseUrl.Length..].TrimStart('/');
            return string.IsNullOrWhiteSpace(key) ? null : Uri.UnescapeDataString(key);
        }

        return null;
    }

    public void Dispose()
    {
        // Nothing to dispose manually — Azure SDK clients are lazy and pooled.
    }
}
