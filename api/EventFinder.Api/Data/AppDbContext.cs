using Microsoft.EntityFrameworkCore;

namespace EventFinder.Api.Data;

public class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<EventEntity> Events => Set<EventEntity>();
    public DbSet<Rsvp> Rsvps => Set<Rsvp>();
    public DbSet<PasswordResetToken> PasswordResetTokens => Set<PasswordResetToken>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<EventEntity>()
            .HasOne(e => e.Organizer)
            .WithMany()
            .HasForeignKey(e => e.OrganizerId)
            .OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<Rsvp>()
            .HasOne(r => r.Event)
            .WithMany(e => e.Rsvps)
            .HasForeignKey(r => r.EventId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Rsvp>()
            .HasOne(r => r.User)
            .WithMany()
            .HasForeignKey(r => r.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // The Android app sends long string event ids for locally-created events
        // only on the client side; the API always generates its own Guid keys.
        modelBuilder.Entity<EventEntity>()
            .Property(e => e.StartUtc)
            .HasConversion(
                v => v.ToUniversalTime(),
                v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
        modelBuilder.Entity<EventEntity>()
            .Property(e => e.EndUtc)
            .HasConversion(
                v => v.ToUniversalTime(),
                v => DateTime.SpecifyKind(v, DateTimeKind.Utc));
    }
}
