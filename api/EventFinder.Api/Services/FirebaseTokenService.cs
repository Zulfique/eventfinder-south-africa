using FirebaseAdmin;
using FirebaseAdmin.Auth;
using Google.Apis.Auth.OAuth2;
using Microsoft.Extensions.Configuration;

namespace EventFinder.Api.Services;

public class FirebaseTokenService
{
    private readonly FirebaseAuth? _auth;

    public FirebaseTokenService(IConfiguration configuration)
    {
        // Initialize Firebase Admin SDK if not already initialized
        if (FirebaseApp.DefaultInstance == null)
        {
            // Try to load service account credentials from configuration
            var serviceAccountJson = configuration["Firebase:ServiceAccountJson"];
            if (!string.IsNullOrWhiteSpace(serviceAccountJson))
            {
                try
                {
                    FirebaseApp.Create(new AppOptions
                    {
                        Credential = GoogleCredential.FromJson(serviceAccountJson)
                    });
                }
                catch
                {
                    // Firebase unavailable — custom tokens will not work
                }
            }
            else
            {
                try
                {
                    FirebaseApp.Create();
                }
                catch
                {
                    // Firebase unavailable — custom tokens will not work
                }
            }
        }
        _auth = FirebaseApp.DefaultInstance != null ? FirebaseAuth.DefaultInstance : null;
    }

    /// <summary>
    /// Creates a Firebase custom token for an EventFinder authenticated user.
    /// The custom token's UID is set to the EventFinder user ID (as string) so
    /// Firestore security rules can verify request.auth.uid == userId for
    /// owner-scoped document access.
    /// </summary>
    public string? CreateCustomToken(Guid userId)
    {
        if (_auth == null)
            return null;

        var token = _auth.CreateCustomTokenAsync(userId.ToString()).GetAwaiter().GetResult();
        return token;
    }

    public bool IsAvailable => _auth != null;
}
