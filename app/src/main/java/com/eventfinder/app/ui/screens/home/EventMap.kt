package com.eventfinder.app.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.ui.components.categoryPngIcon
import com.eventfinder.app.utils.AppLogger
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Interactive event map built on the osmdroid (OpenStreetMap) SDK — the
 * showcase map screen: category glyph pins, a blue user dot, and marker taps
 * that *select* an event (the floating card below handles opening it).
 *
 * The camera is centred once after the map has loaded and the first usable
 * location arrives. Subsequent location updates do not move the camera.
 * Call [recenterMapOnUser] to manually recenter at any time.
 */
@Composable
fun EventMap(
    events: List<Event>,
    center: Pair<Double, Double>,
    userLocation: Location?,
    onEventSelect: (String) -> Unit,
    onMapViewCreated: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCenteredOnUser by rememberSaveable { mutableStateOf(false) }
    val currentOnEventSelect by rememberUpdatedState(onEventSelect)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(center.first, center.second))
        }
    }

    // Pin bitmaps are cached per category so every marker of a category
    // shares one decoded bitmap instead of re-drawing it per event.
    val pinCache = remember { mutableMapOf<Int, Bitmap>() }

    // User location marker — showcase-style blue dot with a white ring.
    val userMarker = remember(mapView) {
        Marker(mapView).apply {
            title = context.getString(R.string.your_location)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
    }

    // Resume/pause/detach lifecycle of the map as the composable enters/exits.
    DisposableEffect(mapView) {
        onMapViewCreated(mapView)
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Rebuild event markers only when the event list actually changes.
    val eventSignature = remember(events) {
        events.fold(1) { hash, event ->
            var result = hash * 31 + event.id.hashCode()
            result = result * 31 + event.latitude.hashCode()
            result = result * 31 + event.longitude.hashCode()
            result = result * 31 + event.title.hashCode()
            result = result * 31 + event.venueName.hashCode()
            result
        }
    }

    LaunchedEffect(eventSignature) {
        mapView.overlays.clear()

        events.forEach { event ->
            val latitude = event.latitude
            val longitude = event.longitude

            if (latitude == null || longitude == null) {
                return@forEach
            }

            val iconRes = categoryPngIcon(event.category)
            val pin = pinCache.getOrPut(iconRes) { categoryPinBitmap(context, iconRes) }

            val marker = Marker(mapView).apply {
                position = GeoPoint(latitude, longitude)
                title = event.title
                snippet = event.venueName
                icon = BitmapDrawable(context.resources, pin)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    // Showcase behaviour: tapping a pin selects the event and
                    // shows the floating card; the card opens the detail.
                    currentOnEventSelect(event.id)
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        userLocation?.let { location ->
            userMarker.position = GeoPoint(location.latitude, location.longitude)
            mapView.overlays.add(userMarker)
        }

        mapView.invalidate()
        AppLogger.d("EventMap", "Rendered ${events.size} markers")
    }

    // Update the user marker position when location changes without rebuilding
    // event overlays; centre the camera on first location arrival.
    LaunchedEffect(userLocation?.latitude, userLocation?.longitude) {
        val location = userLocation ?: return@LaunchedEffect

        userMarker.icon = BitmapDrawable(context.resources, userDotBitmap())
        val userAlreadyPresent = mapView.overlays.contains(userMarker)
        userMarker.position = GeoPoint(location.latitude, location.longitude)
        if (!userAlreadyPresent) {
            mapView.overlays.add(userMarker)
        }
        mapView.invalidate()

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
 * Showcase pin: white teardrop with a green edge carrying the category's
 * full-colour glyph — readable on the always-light OpenStreetMap tiles in
 * both app themes.
 */
private fun categoryPinBitmap(context: Context, iconRes: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (44 * density).toInt().coerceAtLeast(88)
    val height = (width * 27 / 20)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val radius = width * 0.40f
    val centerX = width / 2f
    val centerY = radius + width * 0.08f
    val tipY = height - width * 0.06f

    val pin = Path().apply {
        addCircle(centerX, centerY, radius, Path.Direction.CW)
        moveTo(centerX - radius * 0.66f, centerY + radius * 0.55f)
        lineTo(centerX, tipY)
        lineTo(centerX + radius * 0.66f, centerY + radius * 0.55f)
        close()
    }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(width * 0.04f, 0f, width * 0.03f, 0x55000000)
    }
    canvas.drawPath(pin, fill)
    canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0F763E")
        style = Paint.Style.STROKE
        strokeWidth = width * 0.045f
    })

    val glyph = BitmapFactory.decodeResource(context.resources, iconRes)
    if (glyph != null) {
        val target = (radius * 1.25f).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(glyph, target, target, true)
        canvas.drawBitmap(scaled, centerX - scaled.width / 2f, centerY - scaled.height / 2f, null)
        if (scaled != glyph) scaled.recycle()
    }
    return bitmap
}

/** Showcase-style user position: solid blue dot with a white ring. */
private fun userDotBitmap(): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawCircle(
        size / 2f, size / 2f, size * 0.36f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
    )
    canvas.drawCircle(
        size / 2f, size / 2f, size * 0.28f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2F80ED")
            style = Paint.Style.FILL
        }
    )
    return bitmap
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
