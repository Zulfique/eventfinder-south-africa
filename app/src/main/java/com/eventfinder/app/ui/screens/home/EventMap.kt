package com.eventfinder.app.ui.screens.home

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.utils.AppLogger
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Interactive event map built on the osmdroid (OpenStreetMap) SDK.
 *
 * The camera is centred once after the map has loaded and the first usable
 * location arrives. Subsequent location updates do not move the camera.
 */
@Composable
fun EventMap(
    events: List<Event>,
    center: Pair<Double, Double>,
    userLocation: Location?,
    onEventClick: (String) -> Unit,
    onMapViewCreated: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCenteredOnUser by rememberSaveable { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(center.first, center.second))
        }
    }

    // Resume/pause lifecycle of the map as the composable enters/exits.
    DisposableEffect(mapView) {
        onMapViewCreated(mapView)
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    // Stable marker state: rebuild overlays only when the event content
    // actually changes, not on unrelated recompositions.
    val eventSignature = remember(events) {
        events.joinToString("|") {
            "${it.id}:${it.latitude}:${it.longitude}:${it.title}:${it.venueName}"
        }
    }

    LaunchedEffect(eventSignature) {
        mapView.overlays.clear()

        events.forEach { event ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(event.latitude, event.longitude)
                title = event.title
                snippet = event.venueName
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    onEventClick(event.id)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
        AppLogger.d("EventMap", "Rendered ${events.size} markers")
    }

    // Centre on the user once after the map loads and the first location arrives.
    LaunchedEffect(userLocation?.latitude, userLocation?.longitude) {
        val location = userLocation ?: return@LaunchedEffect

        if (!hasCenteredOnUser) {
            mapView.controller.animateTo(
                GeoPoint(location.latitude, location.longitude),
                14.0,
                700L
            )
            hasCenteredOnUser = true
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * Centre the map on the user's current location at street-level zoom.
 * Call this from a Locate Me button.
 */
fun recenterMapOnUser(mapView: MapView, userLocation: Location) {
    mapView.controller.animateTo(
        GeoPoint(userLocation.latitude, userLocation.longitude),
        15.0,
        400L
    )
}
