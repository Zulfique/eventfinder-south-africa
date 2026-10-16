using System.Text;
using EventFinder.Api.Data;
using EventFinder.Api.Endpoints;
using EventFinder.Api.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
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

app.UseAuthentication();
app.UseAuthorization();

// API health-check endpoint (used by uptime monitors and Render deploy checks).
app.MapGet("/health", () => Results.Json(new
{
    status = "Healthy",
    service = "EventFinder.Api",
    utc = DateTime.UtcNow,
})).WithTags("Health");

app.MapAuthEndpoints();
app.MapEventEndpoints();

app.Run();

/// <summary>Exposed for the integration test suite (WebApplicationFactory).</summary>
public partial class Program;
