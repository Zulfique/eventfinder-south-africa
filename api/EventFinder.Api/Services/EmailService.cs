using System.Text.Json;
using System.Text.Json.Serialization;

namespace EventFinder.Api.Services;

/// <summary>
/// Sends transactional email through the Brevo (formerly Sendinblue) SMTP API.
/// Brevo's free plan allows 300 emails/day with no credit card. In Development
/// with no API key configured the service skips delivery (it never logs the raw
/// token). In Production a missing key is a configuration error and the call
/// throws so the caller can surface a controlled failure.
/// </summary>
public interface IEmailService
{
    Task SendPasswordResetEmailAsync(string toEmail, string rawToken, CancellationToken ct = default);
    Task SendEmailChangeVerificationAsync(string toEmail, string rawToken, CancellationToken ct = default);
}

public class EmailService(HttpClient http, IConfiguration configuration, ILogger<EmailService> logger, IHostEnvironment environment)
    : IEmailService
{
    public async Task SendPasswordResetEmailAsync(string toEmail, string rawToken, CancellationToken ct = default)
    {
        var apiKey = configuration["Brevo:ApiKey"];
        var fromEmail = configuration["Brevo:FromEmail"] ?? "no-reply@eventfinder.app";
        var fromName = configuration["Brevo:FromName"] ?? "EventFinder";
        var appUrl = configuration["App:WebUrl"] ?? "https://eventfinder.app";

        if (string.IsNullOrWhiteSpace(apiKey))
        {
            if (environment.IsDevelopment())
            {
                logger.LogWarning("Brevo API key is missing; reset email was NOT sent for {Email}.", toEmail);
            }
            throw new InvalidOperationException("Email delivery is not configured (Brevo:ApiKey missing).");
        }

        var body = $"""
            You requested a password reset for EventFinder.

            Reset token: {rawToken}

            Enter this token in the app together with your new password.
            The token expires in 60 minutes and can only be used once.

            If you did not request this, you can ignore this email.
            """;

        var payload = new BrevoMail(
            Sender: new BrevoActor(Email: fromEmail, Name: fromName),
            To: [new BrevoActor(Email: toEmail, Name: string.Empty)],
            Subject: "Reset your EventFinder password",
            TextContent: body);

        using var request = new HttpRequestMessage(HttpMethod.Post, "https://api.brevo.com/v3/smtp/email")
        {
            Content = JsonContent.Create(payload, options: new JsonSerializerOptions
            {
                DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
            })
        };
        request.Headers.Add("api-key", apiKey);

        using var response = await http.SendAsync(request, ct);
        if (!response.IsSuccessStatusCode)
        {
            var detail = await response.Content.ReadAsStringAsync(ct);
            logger.LogError("Brevo send failed ({Status}): {Detail}", response.StatusCode, detail);
            throw new InvalidOperationException($"Email delivery failed ({(int)response.StatusCode}).");
        }
    }

    public async Task SendEmailChangeVerificationAsync(string toEmail, string rawToken, CancellationToken ct = default)
    {
        var apiKey = configuration["Brevo:ApiKey"];
        var fromEmail = configuration["Brevo:FromEmail"] ?? "no-reply@eventfinder.app";
        var fromName = configuration["Brevo:FromName"] ?? "EventFinder";

        if (string.IsNullOrWhiteSpace(apiKey))
        {
            if (environment.IsDevelopment())
            {
                logger.LogWarning("Brevo API key is missing; email-change verification was NOT sent for {Email}.", toEmail);
            }
            throw new InvalidOperationException("Email delivery is not configured (Brevo:ApiKey missing).");
        }

        var body = $"""
            You requested to change the email address on your EventFinder account.

            Verification token: {rawToken}

            Enter this token in the app to confirm the new address.
            The token expires in 60 minutes and can only be used once.

            If you did not request this, you can ignore this email.
            """;

        var payload = new BrevoMail(
            Sender: new BrevoActor(Email: fromEmail, Name: fromName),
            To: [new BrevoActor(Email: toEmail, Name: string.Empty)],
            Subject: "Verify your new EventFinder email",
            TextContent: body);

        using var request = new HttpRequestMessage(HttpMethod.Post, "https://api.brevo.com/v3/smtp/email")
        {
            Content = JsonContent.Create(payload, options: new JsonSerializerOptions
            {
                DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
            })
        };
        request.Headers.Add("api-key", apiKey);

        using var response = await http.SendAsync(request, ct);
        if (!response.IsSuccessStatusCode)
        {
            var detail = await response.Content.ReadAsStringAsync(ct);
            logger.LogError("Brevo send failed ({Status}): {Detail}", response.StatusCode, detail);
            throw new InvalidOperationException($"Email delivery failed ({(int)response.StatusCode}).");
        }
    }

    private sealed record BrevoActor(string Email, string Name);

    private sealed record BrevoMail(
        BrevoActor Sender,
        List<BrevoActor> To,
        string Subject,
        [property: JsonPropertyName("textContent")] string TextContent);
}
