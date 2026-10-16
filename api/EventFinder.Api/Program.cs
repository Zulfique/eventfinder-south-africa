using System.Text;
using System.Threading.RateLimiting;
using EventFinder.Api.Data;
using EventFinder.Api.Endpoints;
using EventFinder.Api.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<AppDbContext>(options =>
{
    var connectionString = builder.Configuration.GetConnectionString("Default")
        ?? "Data Source=data/eventfinder.db";
    options.UseSqlite(connectionString);
});

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        var secret = builder.Configuration["Jwt:Secret"]
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
    });
builder.Services.AddAuthorization();

builder.Services.AddRateLimiter(options =>
{
    options.AddFixedWindowLimiter("auth", o =>
    {
        o.Window = TimeSpan.FromMinutes(1);
        o.PermitLimit = 20;
        o.QueueLimit = 0;
        o.QueueProcessingOrder = QueueProcessingOrder.OldestFirst;
    });
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
});

builder.Services.AddSingleton<TokenService>();
builder.Services.AddHttpClient<GoogleTokenVerifier>();
builder.Services.AddHttpClient<IEmailService, EmailService>();

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
