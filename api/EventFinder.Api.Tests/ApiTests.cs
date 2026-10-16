using System.Data.Common;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using EventFinder.Api.Data;
using EventFinder.Api.Services;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace EventFinder.Api.Tests;

/// <summary>
/// Boots the real API with an isolated in-memory SQLite database per test
/// class, so tests exercise the actual endpoints, validation and EF Core
/// code path without touching the development database file.
/// </summary>
public class NoOpEmailService : IEmailService
{
    public Task SendPasswordResetEmailAsync(string toEmail, string rawToken, CancellationToken ct = default) =>
        Task.CompletedTask;

    public Task SendEmailChangeVerificationAsync(string toEmail, string rawToken, CancellationToken ct = default) =>
        Task.CompletedTask;
}

public class ApiFactory : WebApplicationFactory<Program>
{
    private readonly SqliteConnection _connection = new("DataSource=:memory:");

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        _connection.Open();

        builder.UseSetting("Jwt:Secret", "test-secret-that-is-long-enough-for-hmac-sha256-0123456789");
        builder.UseSetting("Jwt:Issuer", "eventfinder-api");
        builder.UseSetting("Jwt:Audience", "eventfinder-app");
        builder.UseSetting("Google:ServerClientId", "test-audience.apps.googleusercontent.com");
        builder.UseSetting("Brevo:ApiKey", ""); // email service logs instead of sending

        builder.ConfigureServices(services =>
        {
            services.RemoveAll(typeof(DbContextOptions<AppDbContext>));
            services.RemoveAll(typeof(DbContextOptions));
    services.RemoveAll(typeof(AppDbContext));
    services.AddDbContext<AppDbContext>(o => o.UseSqlite(_connection));

    services.RemoveAll(typeof(IEmailService));
    services.AddSingleton<IEmailService>(new NoOpEmailService());
        });
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
        if (disposing) _connection.Dispose();
    }
}

public class Envelope
{
    public bool Success { get; set; }
    public string? Message { get; set; }
    public JsonElement Data { get; set; }
    public Dictionary<string, string[]>? Errors { get; set; }
}

public static class TestHttpClientExtensions
{
    public static async Task<Envelope> ReadEnvelopeAsync(this HttpResponseMessage response)
    {
        var json = await response.Content.ReadAsStringAsync();
        try
        {
            return JsonSerializer.Deserialize<Envelope>(json, new JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true,
            }) ?? throw new InvalidOperationException($"Unparseable response ({response.StatusCode}): {json}");
        }
        catch (JsonException ex)
        {
            throw new InvalidOperationException($"Bad body from {response.StatusCode}: {json}", ex);
        }
    }

    public static async Task AuthenticateAsync(this HttpClient client, ApiFactory factory, string email = "tester@example.com")
    {
        using var scope = factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        if (!db.Users.Any(u => u.Email == email))
        {
            db.Users.Add(new User
            {
                Id = Guid.NewGuid(),
                Email = email,
                FullName = "Tester",
                PasswordHash = EventFinder.Api.Services.PasswordHasher.Hash("Password1!"),
                PreferredLanguage = "en",
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow,
            });
            await db.SaveChangesAsync();
        }

        var response = await client.PostAsJsonAsync("/api/auth/login", new { email, password = "Password1!" });
        var envelope = await response.ReadEnvelopeAsync();
        var token = envelope.Data.GetProperty("token").GetString();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
    }
}

public class HealthAndEnvelopeTests : IClassFixture<ApiFactory>
{
    private readonly ApiFactory _factory;

    public HealthAndEnvelopeTests(ApiFactory factory) => _factory = factory;

    [Fact]
    public async Task Health_endpoint_returns_healthy()
    {
        var client = _factory.CreateClient();
        var response = await client.GetAsync("/health");

        Assert.Equal(System.Net.HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync();
        Assert.Contains("Healthy", body);
    }

    [Fact]
    public async Task Envelope_shape_is_consistent_across_endpoints()
    {
        var client = _factory.CreateClient();
        var envelope = await (await client.GetAsync("/api/events/public")).ReadEnvelopeAsync();

        Assert.True(envelope.Success);
        Assert.True(envelope.Data.ValueKind == JsonValueKind.Array || envelope.Data.ValueKind == JsonValueKind.Object);
    }

    [Fact]
    public async Task Validation_errors_are_returned_in_the_envelope()
    {
        var client = _factory.CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/register", new { });

        Assert.Equal(System.Net.HttpStatusCode.BadRequest, response.StatusCode);
        var envelope = await response.ReadEnvelopeAsync();
        Assert.False(envelope.Success);
        Assert.NotNull(envelope.Errors);
        Assert.Contains("email", envelope.Errors!.Keys);
        Assert.Contains("password", envelope.Errors!.Keys);
    }
}

public class AuthTests : IClassFixture<ApiFactory>
{
    private readonly ApiFactory _factory;

    public AuthTests(ApiFactory factory) => _factory = factory;

    [Fact]
    public async Task Register_returns_token_and_server_generated_user_id()
    {
        var client = _factory.CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/register", new
        {
            fullName = "Thandi",
            email = $"thandi-{Guid.NewGuid():N}@example.com",
            password = "Password1!",
            language = "en",
        });

        Assert.Equal(System.Net.HttpStatusCode.OK, response.StatusCode);
        var envelope = await response.ReadEnvelopeAsync();
        Assert.True(envelope.Success);
        Assert.False(string.IsNullOrEmpty(envelope.Data.GetProperty("token").GetString()));
        Assert.NotEqual(Guid.Empty, envelope.Data.GetProperty("user").GetProperty("id").GetGuid());
    }

    [Fact]
    public async Task Register_rejects_duplicate_email_with_conflict()
    {
        var client = _factory.CreateClient();
        var email = $"dup-{Guid.NewGuid():N}@example.com";
        var payload = new { fullName = "Dup", email, password = "Password1!", language = "en" };

        Assert.Equal(System.Net.HttpStatusCode.OK, (await client.PostAsJsonAsync("/api/auth/register", payload)).StatusCode);
        var second = await client.PostAsJsonAsync("/api/auth/register", payload);

        Assert.Equal(System.Net.HttpStatusCode.Conflict, second.StatusCode);
        var envelope = await second.ReadEnvelopeAsync();
        Assert.False(envelope.Success);
        Assert.Contains("email", envelope.Errors!.Keys);
    }

    [Fact]
    public async Task Login_with_wrong_password_is_unauthorized()
    {
        var client = _factory.CreateClient();
        var email = $"login-{Guid.NewGuid():N}@example.com";
        await client.PostAsJsonAsync("/api/auth/register", new
        {
            fullName = "Login", email, password = "Password1!", language = "en",
        });

        var badLogin = await client.PostAsJsonAsync("/api/auth/login", new { email, password = "WrongPass1!" });
        Assert.Equal(System.Net.HttpStatusCode.Unauthorized, badLogin.StatusCode);

        var goodLogin = await client.PostAsJsonAsync("/api/auth/login", new { email, password = "Password1!" });
        Assert.Equal(System.Net.HttpStatusCode.OK, goodLogin.StatusCode);
    }

    [Fact]
    public async Task Password_reset_flow_sends_token_and_allows_new_password()
    {
        var client = _factory.CreateClient();
        var email = $"reset-{Guid.NewGuid():N}@example.com";
        await client.PostAsJsonAsync("/api/auth/register", new
        {
            fullName = "Reset", email, password = "Password1!", language = "en",
        });

        // 1. Request a reset — always succeeds, even before we read the token.
        var forgot = await client.PostAsJsonAsync("/api/auth/forgot-password", new { email });
        Assert.Equal(System.Net.HttpStatusCode.OK, forgot.StatusCode);

        // 2. Pull the raw token the email service would have sent (dev/test mode logs it).
        using var scope = _factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var userId = await db.Users.Where(u => u.Email == email).Select(u => u.Id).FirstAsync();
        var stored = await db.PasswordResetTokens
            .Where(t => t.UserId == userId && t.UsedAt == null)
            .OrderByDescending(t => t.CreatedAt)
            .FirstAsync();
        Assert.True(stored.ExpiresAt > DateTime.UtcNow);

        // 3. Use a wrong token first — rejected.
        var badReset = await client.PostAsJsonAsync("/api/auth/reset-password", new
        {
            email, token = "not-the-real-token", newPassword = "BrandNew1!",
        });
        Assert.Equal(System.Net.HttpStatusCode.BadRequest, badReset.StatusCode);

        // We cannot reverse the hash, so mint a fresh known-token pair for the happy path.
        var rawToken = Convert.ToBase64String(System.Security.Cryptography.RandomNumberGenerator.GetBytes(32));
        stored.TokenHash = EventFinder.Api.Endpoints.AuthEndpoints.HashToken(rawToken);
        await db.SaveChangesAsync();

        var reset = await client.PostAsJsonAsync("/api/auth/reset-password", new
        {
            email, token = rawToken, newPassword = "BrandNew1!",
        });
        Assert.Equal(System.Net.HttpStatusCode.OK, reset.StatusCode);

        // Token is single-use.
        var reuse = await client.PostAsJsonAsync("/api/auth/reset-password", new
        {
            email, token = rawToken, newPassword = "ThirdPass1!",
        });
        Assert.Equal(System.Net.HttpStatusCode.BadRequest, reuse.StatusCode);

        // New password works; old one does not.
        Assert.Equal(System.Net.HttpStatusCode.Unauthorized,
            (await client.PostAsJsonAsync("/api/auth/login", new { email, password = "Password1!" })).StatusCode);
        Assert.Equal(System.Net.HttpStatusCode.OK,
            (await client.PostAsJsonAsync("/api/auth/login", new { email, password = "BrandNew1!" })).StatusCode);
    }

    [Fact]
    public async Task Google_sign_in_rejects_invalid_token()
    {
        var client = _factory.CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/google", new { idToken = "garbage-token" });

        Assert.Equal(System.Net.HttpStatusCode.Unauthorized, response.StatusCode);
        var envelope = await response.ReadEnvelopeAsync();
        Assert.False(envelope.Success);
    }
}

public class EventTests : IClassFixture<ApiFactory>
{
    private readonly ApiFactory _factory;

    public EventTests(ApiFactory factory) => _factory = factory;

    private static object NewEvent(string title = "Jazz Night", bool isPublic = true) => new
    {
        title,
        description = "An evening of live jazz in the heart of Johannesburg.",
        venueName = "The Orbit",
        address = "81 De Korte Street",
        city = "Johannesburg",
        latitude = -26.2041,
        longitude = 28.0473,
        startUtc = DateTime.UtcNow.AddDays(7),
        endUtc = DateTime.UtcNow.AddDays(7).AddHours(3),
        category = "music",
        imageUrl = (string?)null,
        isPublic,
    };

    [Fact]
    public async Task Create_event_generates_server_side_id_and_timestamps()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory);

        var response = await client.PostAsJsonAsync("/api/events", NewEvent());
        Assert.Equal(System.Net.HttpStatusCode.Created, response.StatusCode);

        var envelope = await response.ReadEnvelopeAsync();
        var id = envelope.Data.GetProperty("id").GetGuid();
        Assert.NotEqual(Guid.Empty, id);

        var createdAt = envelope.Data.GetProperty("createdAt").GetDateTime();
        Assert.True(createdAt <= DateTime.UtcNow.AddMinutes(1));
        Assert.True(envelope.Data.GetProperty("attendeeCount").GetInt32() == 0);
    }

    [Fact]
    public async Task Create_event_validation_rejects_bad_coordinates()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory);

        var response = await client.PostAsJsonAsync("/api/events", new
        {
            title = "Bad coords",
            description = "This event is somewhere far outside South Africa entirely.",
            venueName = "Nowhere",
            latitude = 51.5, // London
            longitude = -0.12,
            startUtc = DateTime.UtcNow.AddDays(1),
            endUtc = DateTime.UtcNow.AddDays(1).AddHours(2),
        });

        Assert.Equal(System.Net.HttpStatusCode.BadRequest, response.StatusCode);
        var envelope = await response.ReadEnvelopeAsync();
        Assert.NotNull(envelope.Errors);
        Assert.Contains("latitude", envelope.Errors!.Keys);
    }

    [Fact]
    public async Task Public_list_shows_only_public_future_events()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, $"pub-{Guid.NewGuid():N}@example.com");

        await client.PostAsJsonAsync("/api/events", NewEvent("Public party", isPublic: true));
        await client.PostAsJsonAsync("/api/events", NewEvent("Private meetup", isPublic: false));

        var envelope = await (await client.GetAsync("/api/events/public")).ReadEnvelopeAsync();
        var titles = envelope.Data.EnumerateArray()
            .Select(e => e.GetProperty("title").GetString())
            .ToList();

        Assert.Contains("Public party", titles);
        Assert.DoesNotContain("Private meetup", titles);
    }

    [Fact]
    public async Task Update_and_delete_are_forbidden_for_non_owners()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, $"owner-{Guid.NewGuid():N}@example.com");
        var created = await (await client.PostAsJsonAsync("/api/events", NewEvent("Owner event"))).ReadEnvelopeAsync();
        var id = created.Data.GetProperty("id").GetGuid();

        // Second user may not edit or delete.
        var other = _factory.CreateClient();
        await other.AuthenticateAsync(_factory, $"intruder-{Guid.NewGuid():N}@example.com");

        var put = await other.PutAsJsonAsync($"/api/events/{id}", NewEvent("Hijacked"));
        Assert.Equal(System.Net.HttpStatusCode.Forbidden, put.StatusCode);

        var delete = await other.DeleteAsync($"/api/events/{id}");
        Assert.Equal(System.Net.HttpStatusCode.Forbidden, delete.StatusCode);

        // The owner can.
        var ownerPut = await client.PutAsJsonAsync($"/api/events/{id}", NewEvent("Owner renamed"));
        Assert.Equal(System.Net.HttpStatusCode.OK, ownerPut.StatusCode);
        var ownerDelete = await client.DeleteAsync($"/api/events/{id}");
        Assert.Equal(System.Net.HttpStatusCode.OK, ownerDelete.StatusCode);
    }

    [Fact]
    public async Task Multi_user_rsvp_updates_attendee_count_and_attendee_list()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, $"organiser-{Guid.NewGuid():N}@example.com");
        var created = await (await client.PostAsJsonAsync("/api/events", NewEvent("RSVP party"))).ReadEnvelopeAsync();
        var id = created.Data.GetProperty("id").GetGuid();

        var alice = _factory.CreateClient();
        await alice.AuthenticateAsync(_factory, $"alice-{Guid.NewGuid():N}@example.com");
        var bob = _factory.CreateClient();
        await bob.AuthenticateAsync(_factory, $"bob-{Guid.NewGuid():N}@example.com");

        Assert.Equal(System.Net.HttpStatusCode.OK,
            (await alice.PostAsJsonAsync($"/api/events/{id}/rsvp", new { status = "attending" })).StatusCode);
        Assert.Equal(System.Net.HttpStatusCode.OK,
            (await bob.PostAsJsonAsync($"/api/events/{id}/rsvp", new { status = "attending" })).StatusCode);

        // Alice changes her mind — count drops.
        Assert.Equal(System.Net.HttpStatusCode.OK,
            (await alice.PostAsJsonAsync($"/api/events/{id}/rsvp", new { status = "declined" })).StatusCode);

        var attendees = await (await client.GetAsync($"/api/events/{id}/attendees")).ReadEnvelopeAsync();
        Assert.Equal(1, attendees.Data.GetArrayLength());

        var detail = await (await client.GetAsync($"/api/events/{id}")).ReadEnvelopeAsync();
        Assert.Equal(1, detail.Data.GetProperty("attendeeCount").GetInt32());

        // Alice can also fully cancel — Bob's RSVP remains.
        Assert.Equal(System.Net.HttpStatusCode.OK,
            (await alice.DeleteAsync($"/api/events/{id}/rsvp")).StatusCode);
        var afterAliceCancel = await (await client.GetAsync($"/api/events/{id}/attendees")).ReadEnvelopeAsync();
        Assert.Equal(1, afterAliceCancel.Data.GetArrayLength());

        Assert.Equal(System.Net.HttpStatusCode.OK,
            (await bob.DeleteAsync($"/api/events/{id}/rsvp")).StatusCode);
        var afterCancel = await (await client.GetAsync($"/api/events/{id}/attendees")).ReadEnvelopeAsync();
        Assert.Equal(0, afterCancel.Data.GetArrayLength());
    }
}
