using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using EventFinder.Api.Services;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace EventFinder.Api.Tests;

/// <summary>
/// The Azure Blob Storage service must degrade gracefully: without credentials it reports
/// itself as unconfigured and every call is a no-op, so the Android app keeps its
/// local-file fallback. The URL helpers must also round-trip every URL the service can
/// produce, and nothing else.
/// </summary>
public class AzureBlobStorageServiceTests
{
    private const string ConnectionString =
        "DefaultEndpointsProtocol=https;AccountName=eventfinder;AccountKey=ZmFrZWFjY291bnRrZXk=;EndpointSuffix=core.windows.net";

    private static AzureBlobStorageService Create(Dictionary<string, string?> values)
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new AzureBlobStorageService(configuration, NullLogger<AzureBlobStorageService>.Instance);
    }

    private static Dictionary<string, string?> FullCredentials() => new()
    {
        ["AzureStorage:ConnectionString"] = ConnectionString,
        ["AzureStorage:ContainerName"] = "eventphotos",
    };

    [Fact]
    public void Missing_credentials_mean_the_service_is_not_configured()
    {
        using var service = Create(new Dictionary<string, string?>());

        Assert.False(service.IsConfigured);
    }

    [Fact]
    public void A_partial_credential_set_is_still_not_configured()
    {
        using var service = Create(new Dictionary<string, string?>
        {
            ["AzureStorage:ConnectionString"] = ConnectionString,
            // ContainerName is missing.
        });

        Assert.False(service.IsConfigured);
    }

    [Fact]
    public void A_full_credential_set_configures_the_service()
    {
        using var service = Create(FullCredentials());

        Assert.True(service.IsConfigured);
    }

    [Fact]
    public async Task Uploading_without_credentials_returns_null_instead_of_throwing()
    {
        using var service = Create(new Dictionary<string, string?>());

        var url = await service.UploadAsync("event_images/x.jpg", new byte[] { 1, 2, 3 }, "image/jpeg");

        Assert.Null(url);
    }

    [Fact]
    public async Task Deleting_without_credentials_returns_false_instead_of_throwing()
    {
        using var service = Create(new Dictionary<string, string?>());

        var deleted = await service.DeleteAsync("event_images/x.jpg");

        Assert.False(deleted);
    }

    [Fact]
    public void The_default_public_base_url_is_derived_from_the_connection_string()
    {
        using var service = Create(FullCredentials());

        var key = service.KeyFromUrl(
            "https://eventfinder.blob.core.windows.net/eventphotos/event_images/a.jpg");

        Assert.Equal("event_images/a.jpg", key);
    }

    [Fact]
    public void An_explicit_blob_endpoint_in_the_connection_string_wins()
    {
        using var service = Create(new Dictionary<string, string?>
        {
            ["AzureStorage:ConnectionString"] =
                "BlobEndpoint=https://myaccount.example.net;AccountName=eventfinder;AccountKey=eA==",
            ["AzureStorage:ContainerName"] = "eventphotos",
        });

        var key = service.KeyFromUrl("https://myaccount.example.net/eventphotos/event_images/a.jpg");

        Assert.Equal("event_images/a.jpg", key);
    }

    [Fact]
    public void A_custom_public_base_url_is_honoured()
    {
        var values = FullCredentials();
        values["AzureStorage:PublicBaseUrl"] = "https://cdn.eventfinder.app/";
        using var service = Create(values);

        var key = service.KeyFromUrl("https://cdn.eventfinder.app/event_images/a.jpg");

        Assert.Equal("event_images/a.jpg", key);
    }

    [Fact]
    public void A_sas_query_string_is_ignored_when_mapping_a_url_to_its_key()
    {
        using var service = Create(FullCredentials());

        var key = service.KeyFromUrl(
            "https://eventfinder.blob.core.windows.net/eventphotos/event_images/a.jpg?sv=2024-01-01&sig=abc%2Bdef");

        Assert.Equal("event_images/a.jpg", key);
    }

    [Fact]
    public void Percent_encoded_keys_are_decoded()
    {
        using var service = Create(FullCredentials());

        var key = service.KeyFromUrl(
            "https://eventfinder.blob.core.windows.net/eventphotos/event_images/my%20photo.jpg");

        Assert.Equal("event_images/my photo.jpg", key);
    }

    [Fact]
    public void A_container_whose_name_shares_a_prefix_does_not_match()
    {
        using var service = Create(FullCredentials());

        // "eventphotos-dev" must not be mistaken for the "eventphotos" container.
        var key = service.KeyFromUrl(
            "https://eventfinder.blob.core.windows.net/eventphotos-dev/event_images/a.jpg");

        Assert.Null(key);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("file:///local/image.jpg")]
    [InlineData("https://example.com/other.jpg")]
    [InlineData("https://eventfinder.blob.core.windows.net/wrongcontainer/event_images/a.jpg")]
    public void Foreign_urls_do_not_map_to_a_container_key(string? url)
    {
        using var service = Create(FullCredentials());

        Assert.Null(service.KeyFromUrl(url));
    }
}
