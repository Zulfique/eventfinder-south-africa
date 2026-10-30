using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace EventFinder.Api.Data;

[Index(nameof(Email), IsUnique = true)]
public class User
{
    [Key]
    public Guid Id { get; set; }

    [Required, MaxLength(254)]
    public string Email { get; set; } = string.Empty;

    [Required, MaxLength(120)]
    public string FullName { get; set; } = string.Empty;

    /// <summary>PBKDF2 hash in the form <c>salt:hash</c> (Base64 parts).</summary>
    [Required]
    public string PasswordHash { get; set; } = string.Empty;

    [MaxLength(5)]
    public string PreferredLanguage { get; set; } = "en";

    /// <summary>Set when the account was created via a verified Google ID token.</summary>
    [MaxLength(64)]
    public string? GoogleSubject { get; set; }

    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }

    /// <summary>
    /// Incremented whenever the password is reset (or otherwise changed in a
    /// way that must invalidate outstanding JWTs). Tokens carrying an older
    /// version are rejected.
    /// </summary>
    public int TokenVersion { get; set; }

    [NotMapped]
    public bool HasGoogleAccount => !string.IsNullOrEmpty(GoogleSubject);
}

public class EventEntity
{
    [Key]
    public Guid Id { get; set; }

    [Required, MaxLength(160)]
    public string Title { get; set; } = string.Empty;

    [Required, MaxLength(4000)]
    public string Description { get; set; } = string.Empty;

    [Required, MaxLength(160)]
    public string VenueName { get; set; } = string.Empty;

    [MaxLength(240)]
    public string Address { get; set; } = string.Empty;

    [MaxLength(120)]
    public string City { get; set; } = string.Empty;

    public double Latitude { get; set; }
    public double Longitude { get; set; }

    public DateTime StartUtc { get; set; }
    public DateTime EndUtc { get; set; }

    [MaxLength(80)]
    public string Category { get; set; } = string.Empty;

    [MaxLength(1024)]
    public string? ImageUrl { get; set; }

    public Guid OrganizerId { get; set; }
    public User? Organizer { get; set; }

    [MaxLength(120)]
    public string OrganizerName { get; set; } = string.Empty;

    public bool IsPublic { get; set; } = true;
    public int AttendeeCount { get; set; }

    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }

    public List<Rsvp> Rsvps { get; set; } = new();
}

[Index(nameof(EventId), nameof(UserId), IsUnique = true)]
public class Rsvp
{
    [Key]
    public Guid Id { get; set; }

    public Guid EventId { get; set; }
    public EventEntity? Event { get; set; }

    public Guid UserId { get; set; }
    public User? User { get; set; }

    /// <summary>"attending" | "maybe" | "declined"</summary>
    [Required, MaxLength(20)]
    public string Status { get; set; } = "attending";

    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

/// <summary>
/// One review per user per event. <c>EventId</c> is the app-side event key as
/// a string: Guid-based API events and imported (RSS/ICS) event ids both land
/// here, so every event in the app can carry reviews.
/// </summary>
[Index(nameof(EventId), nameof(CreatedAt))]
[Index(nameof(EventId), nameof(UserId), IsUnique = true)]
public class EventReview
{
    [Key]
    public Guid Id { get; set; }

    [Required, MaxLength(64)]
    public string EventId { get; set; } = string.Empty;

    public Guid UserId { get; set; }

    public User? User { get; set; }

    /// <summary>1–5 stars.</summary>
    public int Rating { get; set; }

    [MaxLength(2000)]
    public string? Comment { get; set; }

    /// <summary>
    /// True when the reviewer had an "attending" RSVP for this event at the
    /// time of writing (Guid events only — imported events have no backend
    /// RSVP record).
    /// </summary>
    public bool IsVerifiedAttendee { get; set; }

    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

public class PasswordResetToken
{
    [Key]
    public Guid Id { get; set; }

    public Guid UserId { get; set; }

    /// <summary>SHA-256 of the raw token that was emailed to the user.</summary>
    [Required]
    public string TokenHash { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }
    public DateTime? UsedAt { get; set; }
    public DateTime CreatedAt { get; set; }
}

public class EmailChangeToken
{
    [Key]
    public Guid Id { get; set; }

    public Guid UserId { get; set; }

    [Required, MaxLength(254)]
    public string NewEmail { get; set; } = string.Empty;

    [Required]
    public string TokenHash { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }
    public DateTime? UsedAt { get; set; }
    public DateTime CreatedAt { get; set; }
}
