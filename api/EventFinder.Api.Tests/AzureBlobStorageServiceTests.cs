using EventFinder.Api.Services;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace EventFinder.Api.Tests;

/// <summary>
/// Unit tests for <see cref="AzureBlobStorageService"/> that do not require a
/// live Azure account — they only exercise configuration detection (IsConfigured)
/// and URL-to-key mapping, both of which are pure logic.
/// </summary>
public class AzureBlobStorageServiceTests
{
    private const string FakeConnectionString =
        "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=fakefakefake==;EndpointSuffix=core.windows.net";

    [Fact]
    public void IsConfigured_is_false_when_connection_string_is_missing()
    {
        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["AzureStorage:ContainerName"] = "event-images",
            })
            .Build();

        var service = new AzureBlobStorageService(config, NullLogger<AzureBlobStorageService>.Instance);

        Assert.False(service.IsConfigured);
    }

    [Fact]
    public void IsConfigured_is_false_when_container_name_is_missing()
    {
        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["AzureStorage:ConnectionString"] = FakeConnectionString,
            })
            .Build();

        var service = new AzureBlobStorageService(config, NullLogger<AzureBlobStorageService>.Instance);

        Assert.False(service.IsConfigured);
    }

    [Theory]
    [InlineData("https://cdn.eventfinder.app/event_images/user-id/abc123.jpg", "event_images/user-id/abc123.jpg")]
    [InlineData("https://cdn.eventfinder.app/event_images/user-id/abc123.png", "event_images/user-id/abc123.png")]
    public void KeyFromUrl_extracts_key_from_matching_public_base_url(string url, string expectedKey)
    {
        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["AzureStorage:ConnectionString"] = FakeConnectionString,
                ["AzureStorage:ContainerName"] = "event-images",
                ["AzureStorage:PublicBaseUrl"] = "https://cdn.eventfinder.app",
            })
            .Build();

        var service = new AzureBlobStorageService(config, NullLogger<AzureBlobStorageService>.Instance);

        Assert.Equal(expectedKey, service.KeyFromUrl(url));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("not-a-url")]
    [InlineData("file:///event_images/local.jpg")]
    public void KeyFromUrl_returns_null_for_non_matching_urls(string? url)
    {
        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["AzureStorage:ConnectionString"] = FakeConnectionString,
                ["AzureStorage:ContainerName"] = "event-images",
                ["AzureStorage:PublicBaseUrl"] = "https://cdn.eventfinder.app",
            })
            .Build();

        var service = new AzureBlobStorageService(config, NullLogger<AzureBlobStorageService>.Instance);

        Assert.Null(service.KeyFromUrl(url));
    }

    [Fact]
    public void KeyFromUrl_returns_null_for_unrelated_public_url()
    {
        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["AzureStorage:ConnectionString"] = FakeConnectionString,
                ["AzureStorage:ContainerName"] = "event-images",
                ["AzureStorage:PublicBaseUrl"] = "https://cdn.eventfinder.app",
            })
            .Build();

        var service = new AzureBlobStorageService(config, NullLogger<AzureBlobStorageService>.Instance);

        Assert.Null(service.KeyFromUrl("https://example.com/photo.jpg"));
    }
}
