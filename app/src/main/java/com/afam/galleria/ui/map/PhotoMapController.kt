package com.afam.galleria.ui.map

import android.content.Context
import android.graphics.RectF
import android.location.Geocoder
import android.util.Log
import android.view.Gravity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.afam.galleria.data.GeoPoint
import com.afam.galleria.data.Library
import com.afam.galleria.ui.components.Thumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.concat
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.heatmapDensity
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.rgba
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SOURCE = "photos"
private const val HEAT_SOURCE = "photos-heat"
private const val MARKERS = "photo-markers"
private const val HEAT = "photo-heat"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val MAX_IMAGES = 160

/**
 * Gestisce la mappa MapLibre: stile chiaro/scuro, punti delle foto raggruppati sulla GPU,
 * miniature come segnaposto (caricate solo quando servono) e mappa di calore.
 * Vive per tutta la sessione, così tornare sulla scheda Mappa è istantaneo.
 */
@Stable
class PhotoMapController(private val context: Context, accent: Int) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val markers = MarkerFactory(context, accent)
    private val added = LinkedHashMap<String, Boolean>(64, 0.75f, true)

    val mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var styleUrl: String? = null
    private var json: String = EMPTY
    private var appliedJson: String? = null
    private var dataJob: Job? = null
    private var visibleJob: Job? = null
    private var placeJob: Job? = null
    private var fitted = false
    private var pendingFocus: GeoPoint? = null

    private var library: Library = Library.EMPTY
    private var points: Map<Long, GeoPoint> = emptyMap()

    /** Elementi visibili nell'area inquadrata, dal più recente. */
    var visibleIds by mutableStateOf<List<Long>>(emptyList())
        private set

    /** Nome del luogo al centro della mappa (se disponibile). */
    var place by mutableStateOf<String?>(null)
        private set

    var styleLoaded by mutableStateOf(false)
        private set

    /** true se le tessere non sono raggiungibili (senza connessione). */
    var offline by mutableStateOf(false)
        private set

    /** Margine superiore dell'attribuzione OpenStreetMap (sotto i controlli). */
    var attributionTop: Int = 0
        set(value) {
            field = value
            map?.uiSettings?.setAttributionMargins(attributionLeft, value, 0, 0)
        }
    private val attributionLeft = (16 * context.resources.displayMetrics.density).roundToInt()

    var mapPadding = IntArray(4)

    var onOpen: ((ids: List<Long>, startId: Long) -> Unit)? = null

    private var started = false
    private var resumed = false
    private var destroyed = false

    init {
        MapLibre.getInstance(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .logoEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .attributionEnabled(true)
        mapView = MapView(context, options)
        mapView.onCreate(null)
        mapView.addOnStyleImageMissingListener { id -> onImageMissing(id) }
        mapView.addOnDidFailLoadingMapListener {
            if (!styleLoaded) loadFallbackStyle()
        }
        mapView.getMapAsync { m ->
            map = m
            m.setMinZoomPreference(1.0)
            m.setMaxZoomPreference(19.0)
            m.uiSettings.setAttributionGravity(Gravity.TOP or Gravity.START)
            m.uiSettings.setAttributionMargins(attributionLeft, attributionTop, 0, 0)
            m.addOnCameraIdleListener { onCameraIdle() }
            m.addOnMapClickListener { latLng -> onClick(latLng) }
            styleUrl?.let { loadStyle(it) }
        }
    }

    // ---- Ciclo di vita ------------------------------------------------------------------

    fun start() {
        if (destroyed || started) return
        started = true
        mapView.onStart()
    }

    fun resume() {
        if (destroyed || resumed) return
        start()
        resumed = true
        mapView.onResume()
        // Se prima mancava la connessione, riprova a caricare la mappa vera.
        if (offline) styleUrl?.let { loadStyle(it) }
    }

    fun pause() {
        if (!resumed) return
        resumed = false
        mapView.onPause()
    }

    fun stop() {
        pause()
        if (!started) return
        started = false
        mapView.onStop()
    }

    fun destroy() {
        if (destroyed) return
        stop()
        destroyed = true
        scope.cancel()
        mapView.onDestroy()
    }

    fun lowMemory() {
        if (!destroyed) mapView.onLowMemory()
    }

    // ---- Stile --------------------------------------------------------------------------

    fun setDark(dark: Boolean) {
        val url = if (dark) STYLE_DARK else STYLE_LIGHT
        if (url == styleUrl) return
        styleUrl = url
        if (map != null) loadStyle(url)
    }

    private fun loadStyle(url: String) {
        val m = map ?: return
        style = null
        styleLoaded = false
        added.clear()
        appliedJson = null
        m.setStyle(Style.Builder().fromUri(url)) { s ->
            if (styleUrl != url) return@setStyle
            style = s
            offline = false
            addLayers(s)
            applyData()
            styleLoaded = true
            if (!fitted) fitAll(animated = false)
        }
    }

    /** Stile minimo senza rete: le foto restano visibili su uno sfondo neutro. */
    private fun loadFallbackStyle() {
        val m = map ?: return
        offline = true
        val dark = styleUrl == STYLE_DARK
        val bg = if (dark) "#1C1C1E" else "#E5E5EA"
        val json = "{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"bg\",\"type\":\"background\",\"paint\":{\"background-color\":\"$bg\"}}]}"
        added.clear()
        appliedJson = null
        m.setStyle(Style.Builder().fromJson(json)) { s ->
            style = s
            addLayers(s)
            applyData()
            styleLoaded = true
            if (!fitted) fitAll(animated = false)
        }
    }

    private fun addLayers(s: Style) {
        val clustered = GeoJsonOptions()
            .withCluster(true)
            .withClusterRadius(58)
            .withMaxZoom(18)
            .withClusterMaxZoom(18)
            .withClusterProperty("cover", literal("max"), get("id"))
        s.addSource(GeoJsonSource(SOURCE, EMPTY, clustered))
        s.addSource(GeoJsonSource(HEAT_SOURCE, EMPTY, GeoJsonOptions().withMaxZoom(14)))

        s.addLayer(
            HeatmapLayer(HEAT, HEAT_SOURCE).withProperties(
                PropertyFactory.heatmapWeight(1f),
                PropertyFactory.heatmapRadius(interpolate(linear(), zoom(), stop(0, 10), stop(6, 22), stop(12, 34))),
                PropertyFactory.heatmapIntensity(interpolate(linear(), zoom(), stop(0, 0.5), stop(9, 1.4))),
                PropertyFactory.heatmapOpacity(interpolate(linear(), zoom(), stop(0, 0.75), stop(10, 0.4), stop(14, 0.0))),
                PropertyFactory.heatmapColor(
                    interpolate(
                        linear(), heatmapDensity(),
                        stop(0, rgba(0, 122, 255, 0)),
                        stop(0.15, rgba(0, 122, 255, 0.30)),
                        stop(0.4, rgba(90, 200, 250, 0.55)),
                        stop(0.7, rgba(255, 204, 0, 0.70)),
                        stop(1, rgba(255, 69, 58, 0.80)),
                    )
                ),
            )
        )
        val cover = coalesce(get("cover"), get("id"))
        s.addLayer(
            SymbolLayer(MARKERS, SOURCE).withProperties(
                PropertyFactory.iconImage(
                    concat(
                        literal("m"),
                        Expression.toString(cover),
                        literal("_"),
                        Expression.toString(coalesce(get("point_count"), literal(1))),
                    )
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor("center"),
                PropertyFactory.symbolSortKey(cover),
            )
        )
    }

    // ---- Dati ---------------------------------------------------------------------------

    fun setData(library: Library, points: Map<Long, GeoPoint>) {
        val sameLibrary = this.library.byId === library.byId && this.library.local.hidden == library.local.hidden
        val samePoints = this.points === points
        this.library = library
        this.points = points
        if (sameLibrary && samePoints && appliedJson != null) return
        dataJob?.cancel()
        dataJob = scope.launch {
            val built = withContext(Dispatchers.Default) { buildGeoJson(library, points) }
            json = built
            applyData()
            onCameraIdle()
            if (!fitted && styleLoaded) fitAll(animated = false)
        }
    }

    private fun applyData() {
        val s = style ?: return
        if (appliedJson == json) return
        s.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(json)
        s.getSourceAs<GeoJsonSource>(HEAT_SOURCE)?.setGeoJson(json)
        appliedJson = json
    }

    private fun buildGeoJson(library: Library, points: Map<Long, GeoPoint>): String {
        val sb = StringBuilder(points.size * 96 + 64)
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true
        for ((id, p) in points) {
            val item = library.byId[id] ?: continue
            if (library.isHidden(item)) continue
            if (!first) sb.append(',')
            first = false
            sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                .append(p.lng).append(',').append(p.lat)
                .append("]},\"properties\":{\"id\":").append(id).append("}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun visiblePoints(): List<Pair<Long, GeoPoint>> {
        val lib = library
        return points.entries.mapNotNull { (id, p) ->
            val item = lib.byId[id] ?: return@mapNotNull null
            if (lib.isHidden(item)) null else id to p
        }
    }

    // ---- Segnaposto ---------------------------------------------------------------------

    private fun onImageMissing(id: String) {
        val s = style ?: return
        if (!id.startsWith("m")) return
        val sep = id.indexOf('_')
        if (sep < 0) return
        val cover = id.substring(1, sep).toDoubleOrNull()?.toLong() ?: return
        val count = id.substring(sep + 1).toDoubleOrNull()?.toInt() ?: 1
        val item = library.byId[cover]
        val cached = item?.let { Thumbs.peek(it, Thumbs.SMALL) ?: Thumbs.peekBest(it) }
        // L'immagine va aggiunta subito: intanto un segnaposto, poi la miniatura vera.
        s.addImage(id, markers.create(cached, count))
        remember(s, id)
        if (cached == null && item != null) {
            scope.launch {
                val bitmap = Thumbs.load(context, item, Thumbs.SMALL) ?: return@launch
                val marker = withContext(Dispatchers.Default) { markers.create(bitmap, count) }
                if (style === s && added.containsKey(id)) s.addImage(id, marker)
            }
        }
    }

    private fun remember(s: Style, id: String) {
        added[id] = true
        if (added.size > MAX_IMAGES) {
            val eldest = added.keys.first()
            added.remove(eldest)
            runCatching { s.removeImage(eldest) }
        }
    }

    // ---- Interazione --------------------------------------------------------------------

    private fun onClick(latLng: LatLng): Boolean {
        val m = map ?: return false
        val s = style ?: return false
        val pt = m.projection.toScreenLocation(latLng)
        val r = 6f * context.resources.displayMetrics.density
        val features = m.queryRenderedFeatures(RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r), MARKERS)
        val feature = features.firstOrNull() ?: return false
        val source = s.getSourceAs<GeoJsonSource>(SOURCE) ?: return false
        val geometry = feature.geometry() as? Point ?: return false
        val position = LatLng(geometry.latitude(), geometry.longitude())
        if (feature.hasProperty("point_count")) {
            val expansion = runCatching { source.getClusterExpansionZoom(feature) }.getOrDefault(99)
            val current = m.cameraPosition.zoom
            if (expansion <= 18 && expansion > current + 0.3) {
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(position, expansion + 0.4), 450)
                return true
            }
            val leaves = runCatching { source.getClusterLeaves(feature, 10_000, 0) }.getOrNull()
            val ids = leaves?.features()?.mapNotNull { it.getNumberProperty("id")?.toLong() }.orEmpty()
            if (ids.isNotEmpty()) open(ids, ids.maxOrNull() ?: ids.first())
        } else {
            val id = feature.getNumberProperty("id")?.toLong() ?: return false
            val nearby = visibleIds.ifEmpty { listOf(id) }
            open(if (id in nearby) nearby else listOf(id) + nearby, id)
        }
        return true
    }

    private fun open(ids: List<Long>, start: Long) {
        val lib = library
        val sorted = ids.distinct().sortedByDescending { lib.byId[it]?.dateTaken ?: 0L }
        onOpen?.invoke(sorted, start)
    }

    private fun onCameraIdle() {
        val m = map ?: return
        val bounds = runCatching { m.projection.visibleRegion.latLngBounds }.getOrNull() ?: return
        val north = bounds.latitudeNorth
        val south = bounds.latitudeSouth
        val west = bounds.longitudeWest
        val east = bounds.longitudeEast
        val lib = library
        val pts = points
        visibleJob?.cancel()
        visibleJob = scope.launch {
            val ids = withContext(Dispatchers.Default) {
                val span = east - west
                pts.entries.asSequence()
                    .filter { (id, p) ->
                        val item = lib.byId[id]
                        item != null && !lib.isHidden(item) &&
                            p.lat in south..north && (span >= 360.0 || inLongitude(p.lng, west, east))
                    }
                    .map { it.key }
                    .sortedByDescending { lib.byId[it]?.dateTaken ?: 0L }
                    .toList()
            }
            visibleIds = ids
        }
        val zoom = m.cameraPosition.zoom
        val center = m.cameraPosition.target
        placeJob?.cancel()
        if (center == null || zoom < 3.5) {
            place = null
            return
        }
        placeJob = scope.launch {
            delay(350)
            place = withContext(Dispatchers.IO) { geocode(center.latitude, center.longitude, zoom) }
        }
    }

    private fun inLongitude(lng: Double, west: Double, east: Double): Boolean {
        fun norm(v: Double): Double {
            var x = (v + 180.0) % 360.0
            if (x < 0) x += 360.0
            return x - 180.0
        }
        val w = norm(west)
        val e = norm(east)
        return if (w <= e) lng in w..e else lng >= w || lng <= e
    }

    private val geocodeCache = HashMap<String, String?>()

    @Suppress("DEPRECATION")
    private fun geocode(lat: Double, lng: Double, zoom: Double): String? {
        if (!Geocoder.isPresent()) return null
        val key = String.format(Locale.ROOT, "%.2f,%.2f,%d", lat, lng, zoom.roundToInt() / 3)
        synchronized(geocodeCache) { if (geocodeCache.containsKey(key)) return geocodeCache[key] }
        val result = try {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
            when {
                a == null -> null
                zoom >= 9 -> listOfNotNull(a.locality ?: a.subAdminArea, a.countryName).distinct().joinToString(", ")
                zoom >= 6 -> listOfNotNull(a.adminArea, a.countryName).distinct().joinToString(", ")
                else -> a.countryName
            }?.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
        synchronized(geocodeCache) { geocodeCache[key] = result }
        return result
    }

    // ---- Fotocamera ---------------------------------------------------------------------

    /** Inquadra tutte le foto con posizione. */
    fun fitAll(animated: Boolean) {
        val m = map ?: return
        val focus = pendingFocus
        if (focus != null) {
            pendingFocus = null
            fitted = true
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus.lat, focus.lng), 15.0))
            return
        }
        val pts = visiblePoints()
        if (pts.isEmpty()) return
        if (mapView.width == 0 || mapView.height == 0) {
            mapView.post { fitAll(animated) }
            return
        }
        fitted = true
        val update = if (pts.size == 1 || pts.all { abs(it.second.lat - pts[0].second.lat) < 1e-4 && abs(it.second.lng - pts[0].second.lng) < 1e-4 }) {
            CameraUpdateFactory.newLatLngZoom(LatLng(pts[0].second.lat, pts[0].second.lng), 13.0)
        } else {
            val builder = LatLngBounds.Builder()
            pts.forEach { builder.include(LatLng(it.second.lat, it.second.lng)) }
            val p = mapPadding
            CameraUpdateFactory.newLatLngBounds(builder.build(), p[0], p[1], p[2], p[3])
        }
        try {
            if (animated) m.animateCamera(update, 700) else m.moveCamera(update)
        } catch (e: Exception) {
            Log.w("PhotoMap", "camera update failed", e)
        }
    }

    /** Centra la mappa su un punto (es. "Mostra sulla mappa" dal pannello Info). */
    fun focus(point: GeoPoint) {
        val m = map
        if (m == null || !styleLoaded) {
            pendingFocus = point
            fitted = false
            return
        }
        fitted = true
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lng), 15.0), 800)
    }
}
