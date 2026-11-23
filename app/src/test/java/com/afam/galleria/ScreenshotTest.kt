package com.afam.galleria

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.theme.GalleryTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Esegue le schermate reali con dati finti e salva gli screenshot in build/outputs/roborazzi,
 * per verificare impaginazione, vetro e assenza di crash senza un dispositivo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var vm: GalleryViewModel

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).grantPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        vm = GalleryViewModel(app)
        val items = fakeItems(160)
        items.forEachIndexed { i, item ->
            val bmp = fakeBitmap(i)
            Thumbs.debugPut(item, Thumbs.SMALL, bmp)
            Thumbs.debugPut(item, Thumbs.GRID, bmp)
            Thumbs.debugPut(item, Thumbs.LARGE, bmp)
        }
        vm.debugSetMedia(items)
    }

    private fun fakeItems(n: Int): List<MediaItem> {
        val now = System.currentTimeMillis()
        return (0 until n).map { i ->
            MediaItem(
                id = (1000 + n - i).toLong(),
                uri = Uri.parse("content://media/external/images/media/${1000 + n - i}"),
                name = "IMG_${1000 + i}.jpg",
                mime = if (i % 11 == 3) "video/mp4" else "image/jpeg",
                isVideo = i % 11 == 3,
                dateTaken = now - i * 5L * 3_600_000L,
                dateModified = (now / 1000) - i,
                width = if (i % 4 == 0) 3000 else 4000,
                height = if (i % 4 == 0) 4000 else 3000,
                orientation = 0,
                size = 2_500_000L + i * 1000L,
                duration = if (i % 11 == 3) 14_000L + i * 1000L else 0L,
                bucketId = if (i % 7 == 0) 2L else 1L,
                bucketName = if (i % 7 == 0) "Screenshots" else "Camera",
                relativePath = if (i % 7 == 0) "Pictures/Screenshots/" else "DCIM/Camera/",
                systemFavorite = i % 13 == 0,
            )
        }
    }

    private fun fakeBitmap(i: Int): Bitmap {
        val r = Random(i * 31 + 7)
        val bmp = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val hue = (i * 37 % 360).toFloat()
        val a = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.95f))
        val b = android.graphics.Color.HSVToColor(floatArrayOf((hue + 60) % 360, 0.75f, 0.6f))
        c.drawRect(0f, 0f, 320f, 320f, Paint().apply {
            shader = LinearGradient(0f, 0f, 320f, 320f, a, b, Shader.TileMode.CLAMP)
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(3) {
            p.color = android.graphics.Color.HSVToColor(120, floatArrayOf(r.nextFloat() * 360, 0.4f, 1f))
            c.drawCircle(r.nextFloat() * 320, r.nextFloat() * 320, 40f + r.nextFloat() * 80, p)
        }
        return bmp
    }

    private fun show(dark: Boolean) {
        compose.setContent {
            GalleryTheme(dark = dark) {
                GalleryRoot(vm = vm, request = LaunchRequest.Main, onPicked = {}, onFinish = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun library_light() {
        show(dark = false)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/01_library_light.png")
    }

    @Test
    fun library_dark() {
        show(dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/02_library_dark.png")
    }

    @Test
    fun library_days_and_menu() {
        show(dark = false)
        compose.onNodeWithText("Giorni").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/03_library_days.png")
        compose.onNodeWithContentDescription("Altro").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/04_action_sheet.png")
    }

    @Test
    fun albums_and_settings() {
        show(dark = false)
        compose.onNodeWithContentDescription("Album").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/05_albums.png")
        compose.onNodeWithContentDescription("Impostazioni").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/06_settings.png")
    }

    @Test
    fun for_you_and_search() {
        show(dark = true)
        compose.onNodeWithContentDescription("Per te").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/07_for_you_dark.png")
        compose.onNodeWithContentDescription("Cerca").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/08_search_dark.png")
    }

    @Test
    fun album_detail() {
        show(dark = false)
        compose.onNodeWithContentDescription("Album").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Camera").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/09_album_detail.png")
    }
}
