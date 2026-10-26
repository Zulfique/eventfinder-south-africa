using Azure;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using Microsoft.Extensions.Logging;

namespace EventFinder.Api.Services;

/// <summary>
/// Azure Blob Storage backing for the event-photo uploads.
///
/// Replaces the previous Cloudflare R2 implementation: Azure Storage is used
/// instead because the project runs on an Azure for Students subscription,
/// whose credit covers this app's usage. Like R2, the credentials never reach
/// the handset - the API owns the connection string and exposes only a small
/// authenticated upload/delete surface, and the phone stores the resulting
/// public URL.
///
/// The service reports itself as <see cref="IsConfigured"/> only when both the
/// connection string and the container name are present, so a half-configured
/// Render service degrades to its "storage unavailable" answers instead of
/// failing every request.
/// </summary>
public sealed class AzureBlobStorageService : IBlobStorageService, IDisposable
{
    private readonly string? _connectionString;
    private readonly string _containerName;
    private readonly string? _publicBaseUrl;
    private readonly ILogger? _logger;

    /// <summary>
    /// Lazily created container client. A malformed connection string makes the
    /// factory return null rather than throwing, so a bad secret reports "not
    /// configured" instead of turning every request into a 500.
    /// </summary>
    private readonly Lazy<BlobContainerClient?> _client;

    private readonly SemaphoreSlim _containerLock = new(1, 1);
    private bool _containerEnsured;
    private bool _disposed;

    public AzureBlobStorageService(
        IConfiguration configuration,
        ILogger<AzureBlobStorageService>? logger = null)
    {
        _logger = logger;

        var connectionString = configuration["AzureStorage:ConnectionString"]?.Trim();
        _connectionString = string.IsNullOrWhiteSpace(connectionString)
            ? null
            : connectionString;

        var containerName = configuration["AzureStorage:ContainerName"]?.Trim();
        _containerName = string.IsNullOrWhiteSpace(containerName)
            ? string.Empty
            : containerName!;

        var publicBaseUrl = configuration["AzureStorage:PublicBaseUrl"]?.Trim();
        _publicBaseUrl = string.IsNullOrWhiteSpace(publicBaseUrl)
            ? null
            : publicBaseUrl.TrimEnd('/');

        // Only derive a base URL once a container is known, otherwise the
        // derived value would be meaningless and KeyFromUrl would start
        // matching foreign URLs. (_client is not built yet here, so the gate
        // is repeated on the local values rather than going through
        // IsConfigured.)
        if (_publicBaseUrl is null &&
            _connectionString is not null &&
            _containerName.Length > 0)
        {
            _publicBaseUrl = DerivePublicBaseUrl(_connectionString, _containerName);
        }

        _client = new Lazy<BlobContainerClient?>(
            CreateContainerClient,
            LazyThreadSafetyMode.ExecutionAndPublication);
    }

    /// <inheritdoc />
    public bool IsConfigured =>
        _connectionString is { Length: > 0 } &&
        _containerName.Length > 0 &&
        _client.Value is not null;

    /// <inheritdoc />
    public async Task<string?> UploadAsync(
        string key,
        byte[] content,
        string contentType,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(key) || content is null || content.Length == 0)
        {
            return null;
        }

        if (_client.Value is not BlobContainerClient client)
        {
            return null;
        }

        var normalizedKey = NormalizeKey(key);
        if (normalizedKey.Length == 0)
        {
            return null;
        }

        try
        {
            await EnsureContainerAsync(client, cancellationToken);

            // The blob name is the key verbatim, so the public URL is simply
            // {PublicBaseUrl}/{key}.
            var blob = client.GetBlobClient(normalizedKey);

            using var stream = new MemoryStream(content, writable: false);
            await blob.UploadAsync(
                stream,
                new BlobUploadOptions
                {
                    HttpHeaders = new BlobHttpHeaders { ContentType = contentType }
                },
                cancellationToken);

            var baseUrl = _publicBaseUrl ?? blob.Uri.GetLeftPart(UriPartial.Authority);
            return $"{baseUrl.TrimEnd('/')}/{normalizedKey}";
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (RequestFailedException ex)
        {
            AppLoggerWarn(
                $"Azure Blob upload failed for '{key}': {ex.Status} {ex.Message}");
        }
        catch (Exception ex)
        {
            AppLoggerWarn($"Azure Blob upload failed for '{key}': {ex.Message}");
        }

        return null;
    }

    /// <inheritdoc />
    public async Task<bool> DeleteAsync(string key, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(key))
        {
            return false;
        }

        if (_client.Value is not BlobContainerClient client)
        {
            return false;
        }

        try
        {
            var blob = client.GetBlobClient(NormalizeKey(key));
            var response = await blob.DeleteIfExistsAsync(
                DeleteSnapshotsOption.IncludeSnapshots,
                cancellationToken: cancellationToken);

            if (response.Value)
            {
                return true;
            }

            // The container may not exist yet, which means neither does the
            // blob - a delete of an absent blob still succeeds semantically.
            return await ContainerExistsAsync(client, cancellationToken);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (RequestFailedException ex)
        {
            // 404 means "already gone", which is the state we want.
            if (ex.Status == 404)
            {
                return true;
            }

            AppLoggerWarn(
                $"Azure Blob delete failed for '{key}': {ex.Status} {ex.Message}");
            return false;
        }
        catch (Exception ex)
        {
            AppLoggerWarn($"Azure Blob delete failed for '{key}': {ex.Message}");
            return false;
        }
    }

    /// <inheritdoc />
    public string? KeyFromUrl(string? url)
    {
        if (string.IsNullOrWhiteSpace(url))
        {
            return null;
        }

        var baseUrl = _publicBaseUrl;
        if (string.IsNullOrWhiteSpace(baseUrl))
        {
            return null;
        }

        baseUrl = baseUrl.TrimEnd('/');

        // Strip a SAS or cache-busting query string before comparing, so a
        // signed download URL still maps back to its own key.
        var path = url;
        var queryStart = path.IndexOf('?', StringComparison.Ordinal);
        if (queryStart >= 0)
        {
            path = path.Substring(0, queryStart);
        }

        var fragmentStart = path.IndexOf('#', StringComparison.Ordinal);
        if (fragmentStart >= 0)
        {
            path = path.Substring(0, fragmentStart);
        }

        // Require an exact segment boundary: "eventphotos-dev/..." must not be
        // mistaken for an object inside the "eventphotos" container.
        if (path.Length <= baseUrl.Length ||
            !path.StartsWith(baseUrl, StringComparison.Ordinal) ||
            path[baseUrl.Length] != '/')
        {
            return null;
        }

        var key = path.Substring(baseUrl.Length).TrimStart('/');
        if (string.IsNullOrWhiteSpace(key))
        {
            return null;
        }

        try
        {
            return Uri.UnescapeDataString(key);
        }
        catch (UriFormatException)
        {
            // A malformed escape sequence is not a key this service produced.
            return null;
        }
    }

    /// <summary>Releases the synchronisation primitive held by this service.</summary>
    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }

        _disposed = true;
        _containerLock.Dispose();
        GC.SuppressFinalize(this);
    }

    /// <summary>
    /// Normalises a key to the canonical form used for both writes and reads.
    /// </summary>
    private static string NormalizeKey(string key)
    {
        var normalized = key.Trim().Replace('\\', '/').TrimStart('/');
        while (normalized.Contains("//"))
        {
            normalized = normalized.Replace("//", "/");
        }

        return normalized;
    }

    private BlobContainerClient? CreateContainerClient()
    {
        if (_connectionString is null || _containerName.Length == 0)
        {
            return null;
        }

        try
        {
            return new BlobContainerClient(_connectionString, _containerName);
        }
        catch (Exception ex)
        {
            AppLoggerWarn($"Azure Blob connection string rejected: {ex.Message}");
            return null;
        }
    }

    /// <summary>
    /// Derives the public base URL from the connection string when the operator
    /// does not set <c>AzureStorage:PublicBaseUrl</c>. The container URI is used rather
    /// than just the host, so a path-prefixed custom endpoint keeps working.
    /// </summary>
    private static string? DerivePublicBaseUrl(string connectionString, string containerName)
    {
        try
        {
            var service = new BlobServiceClient(connectionString);
            return service.GetBlobContainerClient(containerName).Uri.ToString().TrimEnd('/');
        }
        catch
        {
            // A malformed connection string must not crash startup; UploadAsync
            // falls back to the blob's own URI after a real upload has happened.
            return null;
        }
    }

    /// <summary>
    /// Creates the container on first use and makes its blobs readable through a
    /// public URL, which is how event photos are served to the app.
    ///
    /// Failures are logged but not rethrown: when Azure is unreachable or the
    /// account rejects public access, <see cref="UploadAsync"/> still attempts
    /// the upload and the caller keeps its local-file fallback.
    /// </summary>
    private async Task EnsureContainerAsync(
        BlobContainerClient client,
        CancellationToken cancellationToken)
    {
        if (_containerEnsured)
        {
            return;
        }

        await _containerLock.WaitAsync(cancellationToken);
        try
        {
            if (_containerEnsured)
            {
                return;
            }

            await client.CreateIfNotExistsAsync(
                PublicAccessType.Blob,
                cancellationToken: cancellationToken);

            // CreateIfNotExists is a no-op when the container already exists, so
            // re-apply the ACL explicitly - the account-level "Allow Blob public
            // access" switch must be on for this to stick.
            await client.SetAccessPolicyAsync(
                PublicAccessType.Blob,
                cancellationToken: cancellationToken);

            _containerEnsured = true;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (RequestFailedException ex)
        {
            AppLoggerWarn(
                "Could not prepare the Azure Blob container (status " + ex.Status +
                "): " + ex.Message +
                " - check that the storage account has 'Allow Blob public access' enabled.");
        }
        catch (Exception ex)
        {
            AppLoggerWarn("Could not prepare the Azure Blob container: " + ex.Message);
        }
        finally
        {
            _containerLock.Release();
        }
    }

    private async Task<bool> ContainerExistsAsync(
        BlobContainerClient client,
        CancellationToken cancellationToken)
    {
        try
        {
            var response = await client.ExistsAsync(cancellationToken);
            return response.Value;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>Writes a diagnostic to the host log, when a logger was supplied.</summary>
    private void AppLoggerWarn(string message)
    {
        _logger?.LogWarning("{Message}", message);
    }
}
