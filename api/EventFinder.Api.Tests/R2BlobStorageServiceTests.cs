using System;
using System.Collections.Generic;
using EventFinder.Api.Services;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace EventFinder.Api.Tests;

/// <summary>
/// The Cloudflare R2 blob storage must degrade gracefully: without
/// credentials it reports itself as unconfigured and every call is a no-op,
/// so the Android app keeps its local-file fallback.
/// </summary>
public class R2BlobStorageServiceTests
{
    private static R2BlobStorageService Create(Dictionary<string, string?> values)
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new R2BlobStorageService(configuration, NullLogger<R2BlobStorageService>.Instance);
    }

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
            ["R2:AccountId"] = "account",
            ["R2:AccessKeyId"] = "key",
            // SecretAccessKey and BucketName are missing.
        });

        Assert.False(service.IsConfigured);
    }

    [Fact]
    public void A_full_credential_set_configures_the_service()
    {
        using var service = Create(new Dictionary<string, string?>
        {
            ["R2:AccountId"] = "account123",
            ["R2:AccessKeyId"] = "accesskey",
            ["R2:SecretAccessKey"] = "secret",
            ["R2:BucketName"] = "eventfinder",
        });

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
    public void The_default_public_base_url_follows_the_r2_dev_convention()
    {
        using var service = Create(new Dictionary<string, string?>
        {
            ["R2:AccountId"] = "account123",
            ["R2:AccessKeyId"] = "accesskey",
            ["R2:SecretAccessKey"] = "secret",
            ["R2:BucketName"] = "eventfinder",
        });

        var key = service.KeyFromUrl("https://account123.r2.cloudflarestorage.com/eventfinder/event_images/a.jpg");

        Assert.Equal("event_images/a.jpg", key);
    }

    [Fact]
    public void A_custom_public_base_url_is_honoured()
    {
        using var service = Create(new Dictionary<string, string?>
        {
            ["R2:AccountId"] = "account123",
            ["R2:AccessKeyId"] = "accesskey",
            ["R2:SecretAccessKey"] = "secret",
            ["R2:BucketName"] = "eventfinder",
            ["R2:PublicBaseUrl"] = "https://cdn.eventfinder.app/",
        });

        var key = service.KeyFromUrl("https://cdn.eventfinder.app/event_images/a.jpg");

        Assert.Equal("event_images/a.jpg", key);
    }

    [Fact]
    public void Percent_encoded_keys_are_decoded()
    {
        using var service = Create(new Dictionary<string, string?>
        {
            ["R2:AccountId"] = "account123",
            ["R2:AccessKeyId"] = "accesskey",
            ["R2:SecretAccessKey"] = "secret",
            ["R2:BucketName"] = "eventfinder",
        });

        var key = service.KeyFromUrl("https://account123.r2.cloudflarestorage.com/eventfinder/event_images/my%20photo.jpg");

        Assert.Equal("event_images/my photo.jpg", key);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("file:///local/image.jpg")]
    [InlineData("https://example.com/other.jpg")]
    public void Foreign_urls_do_not_map_to_a_bucket_key(string? url)
    {
        using var service = Create(new Dictionary<string, string?>
        {
            ["R2:AccountId"] = "account123",
            ["R2:AccessKeyId"] = "accesskey",
            ["R2:SecretAccessKey"] = "secret",
            ["R2:BucketName"] = "eventfinder",
        });

        Assert.Null(service.KeyFromUrl(url));
    }
}
