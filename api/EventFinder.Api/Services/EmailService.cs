using System.Text.Json;
using System.Text.Json.Serialization;

namespace EventFinder.Api.Services;

/// <summary>
/// Sends transactional email through the Brevo (formerly Sendinblue) SMTP API.
/// Brevo's free plan allows 300 emails/day with no credit card. When no API key
/// is configured the service logs the message instead — convenient for local
/// development and for the automated test suite.
/// </summary>
public interface IEmailService
{
    Task SendPasswordResetEmailAsync(string toEmail, string rawToken, CancellationToken ct = default);
}

public class EmailService(HttpClient http, IConfiguration configuration, ILogger<EmailService> logger)
    : IEmailService
{
    public async Task SendPasswordResetEmailAsync(string toEmail, string rawToken, CancellationToken ct = default)
    {
        var apiKey = configuration["Brevo:ApiKey"];
        var fromEmail = configuration["Brevo:FromEmail"] ?? "no-reply@eventfinder.app";
        var fromName = configuration["Brevo:FromName"] ?? "EventFinder";
        var appUrl = configuration["App:WebUrl"] ?? "https://eventfinder.app";

        var body = $"""
            You requested a password reset for EventFinder.

            Reset token: {rawToken}

            Enter this token in the app together with your new password.
            The token expires in 60 minutes and can only be used once.

            If you did not request this, you can ignore this email.
            """;

        if (string.IsNullOrWhiteSpace(apiKey))
        {
            logger.LogWarning(
                "BREVO_API_KEY not configured — password reset for {Email} not sent. Token: {Token}",
                toEmail, rawToken);
            return;
        }

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

    private sealed record BrevoActor(string Email, string Name);

    private sealed record BrevoMail(
        BrevoActor Sender,
        List<BrevoActor> To,
        string Subject,
        [property: JsonPropertyName("textContent")] string TextContent);
}
