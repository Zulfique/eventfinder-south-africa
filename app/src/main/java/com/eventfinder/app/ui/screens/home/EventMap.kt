package com.eventfinder.app.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 * osmdroid is a fully free, keyless mapping SDK — chosen over Google Maps SDK
 * to satisfy the 100% free requirement, and it is the same SDK family used in
 * many open-source event apps.
 *
 * References:
 *  - osmdroid library: https://github.com/osmdroid/osmdroid
 *  - osmdroid usage guide: https://github.com/osmdroid/osmdroid/wiki/How-to-use-the-osmdroid-library
 */
@Composable
fun EventMap(
    events: List<Event>,
    center: Pair<Double, Double>,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    // Rebuild markers whenever the visible event set changes.
    LaunchedEffect(events, center) {
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

    AndroidView(factory = { mapView }, modifier = modifier)
}