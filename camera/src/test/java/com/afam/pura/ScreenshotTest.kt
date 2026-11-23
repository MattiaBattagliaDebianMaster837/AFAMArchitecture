package com.afam.pura

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Shader
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.afam.pura.camera.CamStatus
import com.afam.pura.camera.LensStop
import com.afam.pura.camera.LiveInfo
import com.afam.pura.settings.Aspect
import com.afam.pura.settings.CaptureMode
import com.afam.pura.settings.FlashMode
import com.afam.pura.settings.GridType
import com.afam.pura.settings.Manual
import com.afam.pura.settings.PhotoFormat
import com.afam.pura.settings.Resolution
import com.afam.pura.settings.Settings
import com.afam.pura.settings.WhiteBalance
import com.afam.pura.ui.CameraActions
import com.afam.pura.ui.CameraLayout
import com.afam.pura.ui.CameraUi
import com.afam.pura.ui.UiState
import com.afam.pura.ui.theme.PuraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Disegna lo schermo della fotocamera con un'anteprima finta e salva gli screenshot in
 * build/outputs/roborazzi: impaginazione e comandi si controllano senza telefono.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h911dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val stops = listOf(
        LensStop("0.6", "0", 0.6f, 0.6f),
        LensStop("1", "0", 1f, 1f),
        LensStop("2", "0", 2f, 2f),
        LensStop("10", "0", 10f, 10f),
    )

    private val baseState = UiState(
        settings = Settings(grid = GridType.THIRDS),
        status = CamStatus(cameraId = "0", previewSize = Size(1440, 1080), ready = true, stillSize = Size(4080, 3072)),
        live = LiveInfo(iso = 320, exposureNs = 8_333_333L, focus = 0.4f),
        camera = CameraUi(),
        stops = stops,
        zoom = 1f,
        thumb = scene(200, 200, 7).asImageBitmap(),
        cameraInfo = listOf(
            "Camera 0 · posteriore · 24 mm · 12.5 / 50 MP · LEVEL 3 · zoom 0.6–20× · logica (2, 3), manuale, RAW",
            "Camera 1 · frontale · 22 mm · 8.0 MP · FULL · zoom 1.0–8× · manuale",
        ),
    )

    private fun render(state: UiState, name: String, before: () -> Unit = {}) {
        val preview = scene(1080, 1440, 3).asImageBitmap()
        compose.setContent {
            PuraTheme {
                CameraLayout(state = state, actions = NoActions, message = null, shutterBlink = 0) { g ->
                    Image(
                        preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.offset(y = g.top).size(g.width, g.height).clip(RoundedCornerShape(g.corner)),
                    )
                }
            }
        }
        before()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/camera_$name.png")
    }

    @Test
    fun photo() = render(
        baseState.copy(
            settings = baseState.settings.copy(flash = FlashMode.AUTO),
            status = baseState.status.copy(focusPoint = PointF(0.6f, 0.4f)),
        ),
        "01_photo",
    )

    @Test
    fun photoSquareRaw() = render(
        baseState.copy(
            settings = baseState.settings.copy(aspect = Aspect.R1_1, format = PhotoFormat.RAW_JPEG, resolution = Resolution.MAXIMUM),
            status = baseState.status.copy(raw = true, maxRes = true),
        ),
        "02_square_raw",
    )

    @Test
    fun pro() = render(
        baseState.copy(
            settings = baseState.settings.copy(
                mode = CaptureMode.PRO,
                level = true,
                histogram = true,
                manual = Manual(iso = 100, exposureNs = null, focus = 2f, wb = WhiteBalance.KELVIN, kelvin = 5200, ev = -2),
            ),
            histogram = IntArray(64) { i -> (200 * kotlin.math.exp(-((i - 26) * (i - 26)) / 120.0)).toInt() + 4 },
        ),
        "03_pro",
    ) { compose.onNodeWithText("ISO").performClick() }

    @Test
    fun video() = render(
        baseState.copy(
            settings = baseState.settings.copy(mode = CaptureMode.VIDEO),
            status = baseState.status.copy(previewSize = Size(1920, 1080), recording = true, videoSize = Size(3840, 2160), videoFps = 30),
        ),
        "04_video_recording",
    )

    @Test
    fun settings() = render(baseState, "05_settings") {
        compose.onNodeWithContentDescription("Impostazioni").performClick()
    }

    @Test
    fun landscapeHold() = render(baseState.copy(orientation = 90), "06_landscape_icons")

    private fun scene(w: Int, h: Int, seed: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sky = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.6f, 0xFF6FA8DC.toInt(), 0xFFF6D7A7.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h * 0.6f, sky)
        val ground = Paint().apply {
            shader = LinearGradient(0f, h * 0.6f, 0f, h.toFloat(), 0xFF3B5D2E.toInt(), 0xFF1E2B17.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), ground)
        val r = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(6) {
            p.color = 0xFF000000.toInt() or (r.nextInt(0x404040, 0x8F8F8F))
            val x = r.nextFloat() * w
            c.drawCircle(x, h * 0.6f, w * (0.1f + r.nextFloat() * 0.2f), p)
        }
        p.color = 0xFFFFF4D6.toInt()
        c.drawCircle(w * 0.75f, h * 0.18f, w * 0.07f, p)
        return bmp
    }
}

private object NoActions : CameraActions {
    override fun shutter() = Unit
    override fun setMode(mode: CaptureMode) = Unit
    override fun toggleFront() = Unit
    override fun cycleFlash() = Unit
    override fun selectLens(stop: LensStop) = Unit
    override fun setZoom(z: Float) = Unit
    override fun setEv(ev: Int) = Unit
    override fun focusAt(x: Float, y: Float, lock: Boolean) = Unit
    override fun clearFocus() = Unit
    override fun openGallery() = Unit
    override fun togglePause() = Unit
    override fun update(transform: (Settings) -> Settings) = Unit
    override fun updateManual(transform: (Manual) -> Manual) = Unit
    override fun requestLocation() = Unit
    override fun intentAccept() = Unit
    override fun intentRetake() = Unit
    override fun close() = Unit
}
