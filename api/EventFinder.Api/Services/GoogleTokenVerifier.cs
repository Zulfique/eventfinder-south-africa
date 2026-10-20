using System.Net.Http;
using System.Text.Json;

namespace EventFinder.Api.Services;

public record GoogleTokenInfo(string Sub, string Email, string Name, bool EmailVerified);

/// <summary>
/// Verifies a Google ID token by exchanging it with Google's tokeninfo
/// endpoint. The expected audience is the OAuth web client ID that the
/// Android Credential Manager flow was configured with.
/// </summary>
public class GoogleTokenVerifier(HttpClient http, IConfiguration configuration)
{
    public async Task<GoogleTokenInfo?> VerifyAsync(string idToken, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(idToken)) return null;

        var expectedAudience = configuration["Google:ServerClientId"];
        if (string.IsNullOrWhiteSpace(expectedAudience))
        {
            return null;
        }

        HttpResponseMessage response;
        try
        {
            response = await http.GetAsync(
                $"https://oauth2.googleapis.com/tokeninfo?id_token={Uri.EscapeDataString(idToken)}", ct);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception)
        {
            return null;
        }

        using (response)
        {
            if (!response.IsSuccessStatusCode) return null;

            string body;
            try
            {
                body = await response.Content.ReadAsStringAsync(ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception)
            {
                return null;
            }

            try
            {
                using var doc = JsonDocument.Parse(body);
                var root = doc.RootElement;

                string Get(string name) =>
                    root.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String
                        ? v.GetString() ?? string.Empty
                        : string.Empty;

                var info = new GoogleTokenInfo(
                    Sub: Get("sub"),
                    Email: Get("email"),
                    Name: Get("name"),
                    EmailVerified: Get("email_verified") is "true" or "True");

                if (string.IsNullOrEmpty(info.Sub) || string.IsNullOrEmpty(info.Email)) return null;
                if (!string.Equals(Get("aud"), expectedAudience, StringComparison.Ordinal)) return null;

                var exp = root.TryGetProperty("exp", out var expEl) && expEl.TryGetInt64(out var expSec)
                    ? DateTimeOffset.FromUnixTimeSeconds(expSec)
                    : DateTimeOffset.MinValue;
                if (exp <= DateTimeOffset.UtcNow) return null;

                if (!info.EmailVerified) return null;
                return info;
            }
            catch (Exception)
            {
                return null;
            }
        }
    }
}