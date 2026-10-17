using System.Security.Claims;
using System.Text;
using System.Threading.RateLimiting;
using EventFinder.Api.Data;
using EventFinder.Api.Endpoints;
using EventFinder.Api.Services;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

// Never allow a known development JWT secret in production.
const string DevelopmentJwtSecret =
    "DEV-ONLY-Secret-Replace-In-Production-0123456789abcdef";

var configuredJwtSecret = builder.Configuration["Jwt:Secret"];

if (builder.Environment.IsProduction())
{
    if (string.IsNullOrWhiteSpace(configuredJwtSecret) ||
        configuredJwtSecret == DevelopmentJwtSecret ||
        Encoding.UTF8.GetByteCount(configuredJwtSecret) < 32)
    {
        throw new InvalidOperationException(
            "Production requires a strong, unique Jwt:Secret of at least 32 bytes. " +
            "Configure it using an environment variable or secret manager.");
    }
}
else
{
    // Development and ordinary local tests can run without a deployed secret.
    configuredJwtSecret ??= DevelopmentJwtSecret;
}

builder.Services.AddDbContext<AppDbContext>(options =>
{
    var connectionString = builder.Configuration.GetConnectionString("Default")
        ?? "Data Source=data/eventfinder.db";
    options.UseSqlite(connectionString);
});

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        var secret = configuredJwtSecret
            ?? throw new InvalidOperationException("Jwt:Secret is not configured.");
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = builder.Configuration["Jwt:Issuer"] ?? "eventfinder-api",
            ValidateAudience = true,
            ValidAudience = builder.Configuration["Jwt:Audience"] ?? "eventfinder-app",
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret)),
            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromMinutes(1),
            NameClaimType = "sub",
        };
        options.Events = new Microsoft.AspNetCore.Authentication.JwtBearer.JwtBearerEvents
        {
            OnTokenValidated = async context =>
            {
                var sub = context.Principal?.FindFirstValue(System.Security.Claims.ClaimTypes.NameIdentifier)
                          ?? context.Principal?.FindFirstValue("sub");
                var tvClaim = context.Principal?.FindFirstValue("tv");
                if (!Guid.TryParse(sub, out var userId))
                {
                    context.Fail("Invalid subject.");
                    return;
                }
                using var scope = context.HttpContext.RequestServices.CreateScope();
                var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
                var user = await db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.Id == userId);
                if (user is null)
                {
                    context.Fail("User not found.");
                    return;
                }
                if (tvClaim is null || !int.TryParse(tvClaim, out var tokenVersion) || tokenVersion != user.TokenVersion)
                {
                    context.Fail("Token has been revoked.");
                }
            }
        };
    });
builder.Services.AddAuthorization();

builder.Services.Configure<ForwardedHeadersOptions>(o =>
{
    o.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    // Configure trusted proxy addresses for the actual hosting environment.
    // When deployed on Render (or another proxy), add the proxy's IP addresses/nets here
    // so that RateLimiter correctly partitions requests by the real client IP.
    // Example for Render: o.ForwardedAddresses.Add(IPAddress.Parse("10.0.0.1"));
    // Never trust arbitrary forwarded IP headers from untrusted clients.
});

builder.Services.AddRateLimiter(options =>
{
    options.AddPolicy("auth", context =>
    {
        var ip = context.Connection.RemoteIpAddress?.ToString() ?? "unknown";
        return RateLimitPartition.GetFixedWindowLimiter(ip, _ => new FixedWindowRateLimiterOptions
        {
            Window = TimeSpan.FromMinutes(1),
            PermitLimit = 20,
            QueueLimit = 0,
        });
    });
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
});

builder.Services.AddSingleton<TokenService>();
builder.Services.AddSingleton<FirebaseTokenService>();
builder.Services.AddHttpClient<GoogleTokenVerifier>();
builder.Services.AddHttpClient<IEmailService, EmailService>();

// Initialize Firebase Admin SDK for custom token generation.
// The service account JSON can be provided via Firebase:ServiceAccountJson configuration,
// otherwise default credentials are used (e.g., on Google Cloud / Render managed environments).
// In test environments without credentials, Firebase auth features will be unavailable
// but the rest of the API remains functional.
try
{
    var serviceAccountJson = builder.Configuration["Firebase:ServiceAccountJson"];
    if (!string.IsNullOrWhiteSpace(serviceAccountJson))
    {
        FirebaseApp.Create(new AppOptions
        {
            Credential = GoogleCredential.FromJson(serviceAccountJson)
        });
    }
    else
    {
        FirebaseApp.Create();
    }
}
catch
{
    // Firebase unavailable — custom token endpoint will return 503
}

var app = builder.Build();

// Apply pending EF Core migrations automatically on boot (code-first workflow).
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    db.Database.Migrate();
}

var emailConfigured = !string.IsNullOrWhiteSpace(app.Configuration["Brevo:ApiKey"]);
var googleConfigured = !string.IsNullOrWhiteSpace(app.Configuration["Google:ServerClientId"]);
if (!emailConfigured)
{
    if (app.Environment.IsProduction())
        app.Logger.LogWarning("Brevo:ApiKey is not configured — password-reset emails cannot be sent.");
    else
        app.Logger.LogInformation("Brevo:ApiKey is not configured — running in development fallback (no email sent).");
}

app.UseForwardedHeaders();
app.UseAuthentication();
app.UseAuthorization();
app.UseRateLimiter();

// Landing page so the service root is not a 404.
app.MapGet("/", () => Results.Json(new
{
    service = "EventFinder.Api",
    status = "ok",
    health = "/health",
})).WithTags("Health");

// API health-check endpoint (used by uptime monitors and Render deploy checks).
app.MapGet("/health", () => Results.Json(new
{
    status = "Healthy",
    service = "EventFinder.Api",
    utc = DateTime.UtcNow,
    emailConfigured,
    googleConfigured,
})).WithTags("Health");

app.MapAuthEndpoints();
app.MapEventEndpoints();

app.Run();

/// <summary>Exposed for the integration test suite (WebApplicationFactory).</summary>
public partial class Program;
