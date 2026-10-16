using System.Security.Cryptography;

namespace EventFinder.Api.Services;

/// <summary>
/// PBKDF2 password hashing, format <c>salt:hash</c> (Base64 parts) — same
/// scheme the Android client uses locally for guest accounts.
/// </summary>
public static class PasswordHasher
{
    private const int Iterations = 120_000;
    private const int KeyLengthBytes = 32;
    private const int SaltLengthBytes = 16;

    public static string Hash(string password)
    {
        var salt = RandomNumberGenerator.GetBytes(SaltLengthBytes);
        var hash = Rfc2898DeriveBytes.Pbkdf2(password, salt, Iterations, HashAlgorithmName.SHA256, KeyLengthBytes);
        return $"{Convert.ToBase64String(salt)}:{Convert.ToBase64String(hash)}";
    }

    public static bool Verify(string password, string stored)
    {
        var parts = stored.Split(':');
        if (parts.Length != 2) return false;

        byte[] salt, expected;
        try
        {
            salt = Convert.FromBase64String(parts[0]);
            expected = Convert.FromBase64String(parts[1]);
        }
        catch (FormatException)
        {
            return false;
        }

        var actual = Rfc2898DeriveBytes.Pbkdf2(password, salt, Iterations, HashAlgorithmName.SHA256, expected.Length);
        return CryptographicOperations.FixedTimeEquals(actual, expected);
    }
}
