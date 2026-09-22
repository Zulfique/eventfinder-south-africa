package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.model.RemoteEvent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class RssEventSourceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun sourceWith(xml: String): RssEventSource {
        server.enqueue(MockResponse().setResponseCode(200).setBody(xml))
        return RssEventSource(
            id = "rss-test",
            displayName = "RSS Test",
            url = server.url("/feed.xml").toString(),
            httpClient = OkHttpClient()
        )
    }

    private fun expectedMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `ignores items that only carry a publication date`() = runTest {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test</title>
                <item>
                  <title>News Item</title>
                  <pubDate>Mon, 15 Jun 2030 10:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val events: List<RemoteEvent> = sourceWith(xml).fetchEvents()
        assertEquals(0, events.size)
    }

    @Test
    fun `parses start_time as an event start`() = runTest {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test</title>
                <item>
                  <title>Start Time Event</title>
                  <start_time>2030-06-15T10:00:00Z</start_time>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val events: List<RemoteEvent> = sourceWith(xml).fetchEvents()
        assertEquals(1, events.size)
        assertEquals("Start Time Event", events[0].title)
        assertEquals(expectedMillis("2030-06-15T10:00:00Z"), events[0].startDate)
    }

    @Test
    fun `parses event_date as an event start`() = runTest {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test</title>
                <item>
                  <title>Event Date Feed</title>
                  <event_date>2030-06-16T14:30:00Z</event_date>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val events: List<RemoteEvent> = sourceWith(xml).fetchEvents()
        assertEquals(1, events.size)
        assertEquals("Event Date Feed", events[0].title)
        assertEquals(expectedMillis("2030-06-16T14:30:00Z"), events[0].startDate)
    }

    @Test
    fun `parses plain start element as an event start`() = runTest {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test</title>
                <item>
                  <title>Plain Start Event</title>
                  <start>2030-06-17T09:00:00Z</start>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val events: List<RemoteEvent> = sourceWith(xml).fetchEvents()
        assertEquals(1, events.size)
        assertEquals("Plain Start Event", events[0].title)
        assertEquals(expectedMillis("2030-06-17T09:00:00Z"), events[0].startDate)
    }

    @Test
    fun `parses dc date as an event start`() = runTest {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test</title>
                <item>
                  <title>DC Date Event</title>
                  <dc:date xmlns:dc="http://purl.org/dc/elements/1.1/">2030-06-18T11:00:00Z</dc:date>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val events: List<RemoteEvent> = sourceWith(xml).fetchEvents()
        assertEquals(1, events.size)
        assertEquals("DC Date Event", events[0].title)
        assertEquals(expectedMillis("2030-06-18T11:00:00Z"), events[0].startDate)
    }

    @Test
    fun `parses when element as an event start`() = runTest {
        val xml = """
            <rss version="2.0">
              <channel>
                <title>Test</title>
                <item>
                  <title>When Event</title>
                  <when>2030-06-19T08:00:00Z</when>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val events: List<RemoteEvent> = sourceWith(xml).fetchEvents()
        assertEquals(1, events.size)
        assertEquals("When Event", events[0].title)
        assertEquals(expectedMillis("2030-06-19T08:00:00Z"), events[0].startDate)
    }
}