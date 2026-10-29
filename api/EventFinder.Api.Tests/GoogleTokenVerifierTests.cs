using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using EventFinder.Api.Services;
using Microsoft.Extensions.Configuration;
using Xunit;

namespace EventFinder.Api.Tests;

/// <summary>
/// Google's tokeninfo endpoint returns most claims as JSON *strings* — including
/// <c>exp</c> — while <c>email_verified</c> has appeared as both a boolean and a
/// string. The verifier must accept both shapes: parsing <c>exp</c> as a number
/// only used to collapse to MinValue, so every otherwise-valid sign-in was
/// rejected as "expired" with HTTP 401.
/// </summary>
public class GoogleTokenVerifierTests
{
    private const string Audience = "690167106186-example.apps.googleusercontent.com";

    [Fact]
    public async Task String_typed_claims_from_tokeninfo_are_accepted()
    {
        var future = DateTimeOffset.UtcNow.AddHours(1).ToUnixTimeSeconds();
        var verifier = Create($$"""
            {
              "sub": "111965100327040587686",
              "email": "johnny@example.com",
              "name": "Johnny",
              "email_verified": "true",
              "aud": "{{Audience}}",
              "exp": "{{future}}"
            }
            """);

        var info = await verifier.VerifyAsync("any-token");

        Assert.NotNull(info);
        Assert.Equal("johnny@example.com", info!.Email);
        Assert.Equal("111965100327040587686", info.Sub);
        Assert.True(info.EmailVerified);
    }

    [Fact]
    public async Task Boolean_email_verified_and_numeric_exp_are_accepted()
    {
        var future = DateTimeOffset.UtcNow.AddHours(1).ToUnixTimeSeconds();
        var verifier = Create($$"""
            {
              "sub": "42",
              "email": "someone@example.com",
              "email_verified": true,
              "aud": "{{Audience}}",
              "exp": {{future}}
            }
            """);

        var info = await verifier.VerifyAsync("any-token");

        Assert.NotNull(info);
        Assert.True(info!.EmailVerified);
    }

    [Fact]
    public async Task An_expired_token_is_rejected()
    {
        var past = DateTimeOffset.UtcNow.AddHours(-1).ToUnixTimeSeconds();
        var verifier = Create($$"""
            {
              "sub": "42",
              "email": "someone@example.com",
              "email_verified": "true",
              "aud": "{{Audience}}",
              "exp": "{{past}}"
            }
            """);

        Assert.Null(await verifier.VerifyAsync("any-token"));
    }

    [Fact]
    public async Task An_audience_mismatch_is_rejected()
    {
        var future = DateTimeOffset.UtcNow.AddHours(1).ToUnixTimeSeconds();
        var verifier = Create($$"""
            {
              "sub": "42",
              "email": "someone@example.com",
              "email_verified": "true",
              "aud": "some-other-client.apps.googleusercontent.com",
              "exp": "{{future}}"
            }
            """);

        Assert.Null(await verifier.VerifyAsync("any-token"));
    }

    [Fact]
    public async Task A_token_without_a_verified_email_is_rejected()
    {
        var future = DateTimeOffset.UtcNow.AddHours(1).ToUnixTimeSeconds();
        var verifier = Create($$"""
            {
              "sub": "42",
              "email": "someone@example.com",
              "email_verified": "false",
              "aud": "{{Audience}}",
              "exp": "{{future}}"
            }
            """);

        Assert.Null(await verifier.VerifyAsync("any-token"));
    }

    [Fact]
    public async Task Missing_configuration_rejects_every_token()
    {
        var verifier = Create("""{"sub":"42","email":"a@b.c"}""", configuredAudience: null);

        Assert.Null(await verifier.VerifyAsync("any-token"));
    }

    [Fact]
    public async Task A_malformed_tokeninfo_body_is_rejected()
    {
        var verifier = Create("not json at all");

        Assert.Null(await verifier.VerifyAsync("any-token"));
    }

    private static GoogleTokenVerifier Create(string responseBody, string? configuredAudience = Audience)
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Google:ServerClientId"] = configuredAudience,
            })
            .Build();

        return new GoogleTokenVerifier(new HttpClient(new StubHandler(responseBody)), configuration);
    }

    private sealed class StubHandler : HttpMessageHandler
    {
        private readonly string _body;

        public StubHandler(string body) => _body = body;

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(_body, Encoding.UTF8, "application/json"),
            });
        }
    }
}
