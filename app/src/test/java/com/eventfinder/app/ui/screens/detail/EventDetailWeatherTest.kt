package com.eventfinder.app.ui.screens.detail

import android.content.ContextWrapper
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.repository.NewEventDraft
import com.eventfinder.app.data.repository.WeatherRepository
import com.eventfinder.app.data.repository.WeatherSummary
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the failure cases the previous suite missed: a forecast belonging to a
 * previous event/date must never stay on screen, dates outside the 16-day
 * Open-Meteo window must be marked unavailable without a network call, and an
 * API failure must clear whatever was displayed before.
 *
 * The ViewModel is driven through the injected repository seams, so these run
 * on the JVM without Room or a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailWeatherTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------ fakes

    private class FakeEventRepository : EventRepository {
        var event: Event? = null

        override fun observeAllEvents(): Flow<List<Event>> = flowOf(listOfNotNull(event))
        override fun observeFavoriteEvents(): Flow<List<Event>> = flowOf(emptyList())
        override fun observeFavoriteIds(): Flow<Set<String>> = flowOf(emptySet())
        override fun observeRsvpStatuses(): Flow<Map<String, RsvpStatus>> = flowOf(emptyMap())
        override suspend fun ensureSeeded() = Unit
        override suspend fun toggleFavorite(eventId: String): Boolean = false
        override suspend fun setRsvp(eventId: String, status: RsvpStatus) = Unit
        override suspend fun createEvent(draft: NewEventDraft): Result<String> =
            Result.success("id")
        override suspend fun updateEvent(eventId: String, draft: NewEventDraft): Result<Unit> =
            Result.success(Unit)
        override suspend fun deleteEvent(eventId: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun getEvent(eventId: String): Event? = event
        override suspend fun clearLocalCache(): Int = 0
    }

    /** Returns whatever the next call should produce, in order; records every call. */
    private class FakeWeatherRepository : WeatherRepository {
        val requests = mutableListOf<Triple<Double, Double, Long>>()
        var results: MutableList<Result<WeatherSummary>> = mutableListOf()

        override suspend fun forecastFor(
            eventId: String,
            latitude: Double,
            longitude: Double,
            startDate: Long
        ): Result<WeatherSummary> {
            requests += Triple(latitude, longitude, startDate)
            return results.removeFirstOrNull()
                ?: Result.failure(IllegalStateException("no stubbed result"))
        }
    }

    // ------------------------------------------------------------- utilities

    private fun event(
        startDate: Long,
        lat: Double? = -33.92, lng: Double? = 18.42
    ) = Event(
        id = "event-1",
        title = "Test Event",
        description = "desc",
        category = EventCategory.MUSIC,
        startDate = startDate,
        endDate = startDate,
        venueName = "Venue",
        address = "1 Main Rd",
        latitude = lat,
        longitude = lng,
        imageUrl = null,
        isPublic = true,
        organizerId = "org-1",
        organizerName = "Organiser",
        attendeeCount = 0,
        isFavorite = false,
        isCreatedByUser = false
    )

    private fun summary(temp: Double = 21.0) = WeatherSummary(
        temperatureCelsius = temp,
        weatherCode = 1,
        unit = "°C",
        hourIso = "2026-10-03T12:00"
    )

    private val dayMillis = 24L * 60 * 60 * 1000

    private fun viewModel(
        events: FakeEventRepository,
        weather: FakeWeatherRepository
    ): EventDetailViewModel {
        // ContextWrapper over a null base: the JVM mockable android.jar stubs
        // every framework method, and this test never touches appContext (it is
        // only used by the RSVP notification path).
        val context = ContextWrapper(null)
        return EventDetailViewModel(
            container = AppContainer(context),
            appContext = context,
            eventId = "event-1",
            injectedEventRepository = events,
            injectedWeatherRepository = weather
        )
    }

    // ----------------------------------------------------------------- tests

    @Test
    fun `weather is shown for an event inside the forecast window`() = runTest {
        val events = FakeEventRepository().apply { event = event(System.currentTimeMillis() + 2 * dayMillis) }
        val weather = FakeWeatherRepository().apply { results = mutableListOf(Result.success(summary())) }

        viewModel(events, weather).let { vm ->
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.weather)
            assertFalse(vm.uiState.value.weatherUnavailable)
            assertEquals(1, weather.requests.size)
        }
    }

    @Test
    fun `api failure clears a forecast that was already displayed`() = runTest {
        val start = System.currentTimeMillis() + 2 * dayMillis
        val events = FakeEventRepository().apply { event = event(start) }
        val weather = FakeWeatherRepository().apply {
            results = mutableListOf(Result.success(summary()), Result.failure(RuntimeException("boom")))
        }

        val vm = viewModel(events, weather)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.weather)

        // The venue moves (new coordinates => new cache key) and the new lookup
        // fails: the old event's forecast must not stay on screen.
        events.event = event(start, lat = -26.2, lng = 28.0)
        vm.refresh()
        advanceUntilIdle()

        assertNull("stale forecast must not survive a failed lookup", vm.uiState.value.weather)
        assertTrue(vm.uiState.value.weatherUnavailable)
        assertEquals(2, weather.requests.size)
    }

    @Test
    fun `reloading the same event and date does not refetch`() = runTest {
        val start = System.currentTimeMillis() + 2 * dayMillis
        val events = FakeEventRepository().apply { event = event(start) }
        val weather = FakeWeatherRepository().apply {
            results = mutableListOf(Result.success(summary()))
        }

        val vm = viewModel(events, weather)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.weather)
        assertEquals(1, weather.requests.size)
    }

    @Test
    fun `changing the event date never keeps the old event forecast`() = runTest {
        val oldStart = System.currentTimeMillis() + 2 * dayMillis
        val newStart = System.currentTimeMillis() + 5 * dayMillis
        val events = FakeEventRepository().apply { event = event(oldStart) }
        val weather = FakeWeatherRepository().apply {
            results = mutableListOf(Result.success(summary(18.0)))
        }

        val vm = viewModel(events, weather)
        advanceUntilIdle()
        val first = vm.uiState.value.weather
        assertNotNull(first)

        // Date changes to one the API can no longer answer for.
        events.event = event(newStart)
        weather.results.add(Result.failure(RuntimeException("no forecast for new date")))
        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.uiState.value.weather)
        assertTrue(vm.uiState.value.weatherUnavailable)
        assertEquals(newStart, weather.requests.last().third)
        assertTrue(weather.requests.size >= 2)
    }

    @Test
    fun `an event past the forecast window is unavailable and makes no request`() = runTest {
        val events = FakeEventRepository().apply {
            event = event(System.currentTimeMillis() + 30 * dayMillis)
        }
        val weather = FakeWeatherRepository()

        val vm = viewModel(events, weather)
        advanceUntilIdle()

        assertNull(vm.uiState.value.weather)
        assertTrue(vm.uiState.value.weatherUnavailable)
        assertTrue("no API call for out-of-window dates", weather.requests.isEmpty())
    }

    @Test
    fun `a past event is unavailable and makes no request`() = runTest {
        val events = FakeEventRepository().apply {
            event = event(System.currentTimeMillis() - 3 * dayMillis)
        }
        val weather = FakeWeatherRepository()

        val vm = viewModel(events, weather)
        advanceUntilIdle()

        assertNull(vm.uiState.value.weather)
        assertTrue(vm.uiState.value.weatherUnavailable)
        assertTrue(weather.requests.isEmpty())
    }

    @Test
    fun `an event without coordinates is unavailable without a request`() = runTest {
        val events = FakeEventRepository().apply {
            event = event(System.currentTimeMillis() + 2 * dayMillis, lat = null, lng = null)
        }
        val weather = FakeWeatherRepository()

        val vm = viewModel(events, weather)
        advanceUntilIdle()

        assertNull(vm.uiState.value.weather)
        assertTrue(vm.uiState.value.weatherUnavailable)
        assertTrue(weather.requests.isEmpty())
    }
}
