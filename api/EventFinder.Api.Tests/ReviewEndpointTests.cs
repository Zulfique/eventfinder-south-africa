using System.Net;
using System.Net.Http.Json;

namespace EventFinder.Api.Tests;

/// <summary>
/// Reviews/ratings contract: one review per user per event (PUT upserts),
/// validation of the 1–5 range, verified-attendee flagging from RSVP state,
/// deletion, and the batch summary used by event cards. Event ids are plain
/// strings so imported events are first-class citizens.
/// </summary>
public class ReviewEndpointTests : IClassFixture<ApiFactory>
{
    private readonly ApiFactory _factory;

    public ReviewEndpointTests(ApiFactory factory) => _factory = factory;

    private static object NewEvent(string title, bool isPublic = true) => new
    {
        title,
        description = "A test event for review coverage.",
        venueName = "Test Venue",
        address = "1 Test Street",
        city = "Cape Town",
        latitude = -33.92,
        longitude = 18.42,
        startUtc = DateTime.UtcNow.AddDays(3),
        endUtc = DateTime.UtcNow.AddDays(3).AddHours(4),
        category = "Music",
        isPublic = isPublic,
    };

    [Fact]
    public async Task Empty_event_has_zero_rating_and_no_reviews()
    {
        var client = _factory.CreateClient();

        var response = await client.GetAsync("/api/events/imported-no-such-event/reviews");
        var envelope = await response.ReadEnvelopeAsync();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(envelope.Success);
        Assert.Equal(0, envelope.Data.GetProperty("average").GetDouble());
        Assert.Equal(0, envelope.Data.GetProperty("count").GetInt32());
        Assert.Empty(envelope.Data.GetProperty("items").EnumerateArray());
        Assert.True(envelope.Data.GetProperty("mine").ValueKind == System.Text.Json.JsonValueKind.Null);
    }

    [Fact]
    public async Task Anonymous_review_write_is_rejected()
    {
        var client = _factory.CreateClient();

        var response = await client.PutAsJsonAsync(
            "/api/events/some-event/reviews", new { rating = 5, comment = "Great" });

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Writing_updating_and_deleting_a_review_round_trips()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, "reviewer@example.com");

        // First write creates.
        var created = await client.PutAsJsonAsync(
            "/api/events/imported-round-trip/reviews",
            new { rating = 4, comment = "  Solid show  " });
        var createEnvelope = await created.ReadEnvelopeAsync();
        Assert.Equal(HttpStatusCode.OK, created.StatusCode);
        Assert.True(createEnvelope.Success);
        Assert.Equal(4.0, createEnvelope.Data.GetProperty("average").GetDouble());
        Assert.Equal(1, createEnvelope.Data.GetProperty("count").GetInt32());

        var mine = createEnvelope.Data.GetProperty("mine");
        Assert.Equal(4, mine.GetProperty("rating").GetInt32());
        // Comment is trimmed server-side.
        Assert.Equal("Solid show", mine.GetProperty("comment").GetString());
        Assert.False(mine.GetProperty("isVerifiedAttendee").GetBoolean());

        // Second write upserts instead of duplicating.
        var updated = await client.PutAsJsonAsync(
            "/api/events/imported-round-trip/reviews",
            new { rating = 2, comment = "Changed my mind" });
        var updateEnvelope = await updated.ReadEnvelopeAsync();
        Assert.Equal(1, updateEnvelope.Data.GetProperty("count").GetInt32());
        Assert.Equal(2.0, updateEnvelope.Data.GetProperty("average").GetDouble());
        Assert.Equal("Changed my mind", updateEnvelope.Data.GetProperty("mine").GetProperty("comment").GetString());

        // Delete clears it.
        var deleted = await client.DeleteAsync("/api/events/imported-round-trip/reviews");
        var deleteEnvelope = await deleted.ReadEnvelopeAsync();
        Assert.Equal(HttpStatusCode.OK, deleted.StatusCode);
        Assert.Equal(0, deleteEnvelope.Data.GetProperty("count").GetInt32());
        Assert.True(deleteEnvelope.Data.GetProperty("mine").ValueKind == System.Text.Json.JsonValueKind.Null);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(6)]
    [InlineData(-1)]
    public async Task Rating_outside_1_to_5_is_rejected(int rating)
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, "range@example.com");

        var response = await client.PutAsJsonAsync(
            "/api/events/imported-range/reviews", new { rating, comment = "nope" });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        var envelope = await response.ReadEnvelopeAsync();
        Assert.False(envelope.Success);
    }

    [Fact]
    public async Task Rsvpd_attending_user_gets_the_verified_attendee_badge()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, "verified@example.com");

        var created = await client.PostAsJsonAsync("/api/events", NewEvent("Verified Night"));
        var envelope = await created.ReadEnvelopeAsync();
        var eventId = envelope.Data.GetProperty("id").GetString();
        Assert.False(string.IsNullOrEmpty(eventId));

        await client.PostAsJsonAsync($"/api/events/{eventId}/rsvp", new { status = "attending" });

        var reviewed = await client.PutAsJsonAsync(
            $"/api/events/{eventId}/reviews", new { rating = 5, comment = "Went, loved it" });
        var reviewEnvelope = await reviewed.ReadEnvelopeAsync();

        Assert.True(reviewEnvelope.Success);
        Assert.True(reviewEnvelope.Data.GetProperty("mine").GetProperty("isVerifiedAttendee").GetBoolean());
    }

    [Fact]
    public async Task Non_attendee_review_is_not_verified()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, "bystander@example.com");

        var response = await client.PutAsJsonAsync(
            "/api/events/00000000-0000-0000-0000-000000000042/reviews",
            new { rating = 3, comment = "Watched from outside" });
        var envelope = await response.ReadEnvelopeAsync();

        Assert.True(envelope.Success);
        Assert.False(envelope.Data.GetProperty("mine").GetProperty("isVerifiedAttendee").GetBoolean());
    }

    [Fact]
    public async Task Summary_endpoint_batches_by_event_id()
    {
        var client = _factory.CreateClient();
        await client.AuthenticateAsync(_factory, "summarizer@example.com");

        await client.PutAsJsonAsync("/api/events/sum-a/reviews", new { rating = 5 });
        await client.PutAsJsonAsync("/api/events/sum-b/reviews", new { rating = 1 });

        var response = await client.GetAsync("/api/reviews/summary?id=sum-a&id=sum-b&id=never-reviewed");
        var envelope = await response.ReadEnvelopeAsync();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(envelope.Success);

        var items = envelope.Data.EnumerateArray().ToList();
        Assert.Equal(2, items.Count);

        var a = items.Single(i => i.GetProperty("eventId").GetString() == "sum-a");
        var b = items.Single(i => i.GetProperty("eventId").GetString() == "sum-b");
        Assert.Equal(5.0, a.GetProperty("average").GetDouble());
        Assert.Equal(1.0, b.GetProperty("average").GetDouble());
        Assert.Equal(1, a.GetProperty("count").GetInt32());
    }

    [Fact]
    public async Task Summary_with_no_ids_returns_an_empty_list()
    {
        var client = _factory.CreateClient();

        var response = await client.GetAsync("/api/reviews/summary");
        var envelope = await response.ReadEnvelopeAsync();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(envelope.Success);
        Assert.Empty(envelope.Data.EnumerateArray());
    }
}
