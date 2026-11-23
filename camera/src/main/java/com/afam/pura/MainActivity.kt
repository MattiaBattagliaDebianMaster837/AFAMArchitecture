package com.afam.pura

import android.Manifest
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.afam.pura.ui.CameraScreen
import com.afam.pura.ui.PermissionScreen
import com.afam.pura.ui.theme.PuraTheme
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {

    protected val vm: CameraViewModel by viewModels()
    private var orientationListener: OrientationEventListener? = null
    private var hasCamera by mutableStateOf(false)
    private var secure = false

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = granted(Manifest.permission.CAMERA)
        vm.onPermissionsChanged()
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        vm.updateSettings { it.copy(geotag = ok) }
        vm.onPermissionsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            // Animazione di rotazione "a scatto": il telefono non ruota l'interfaccia, ruotano le icone.
            rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
        }
        hideSystemBars()
        handleIntent(intent)

        hasCamera = granted(Manifest.permission.CAMERA)
        if (!hasCamera) {
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val current = vm.orientation.value
                // Isteresi: si cambia solo oltre i 60° dal valore attuale, niente sfarfallii.
                var diff = kotlin.math.abs(orientation - current)
                diff = minOf(diff, 360 - diff)
                if (diff < 60) return
                vm.setOrientation(((orientation + 45) / 90 * 90) % 360)
            }
        }

        lifecycleScope.launch {
            vm.savedForIntent.collect { uri -> onVideoForIntent(uri) }
        }

        setContent {
            PuraTheme {
                if (hasCamera) {
                    CameraScreen(
                        vm = vm,
                        onOpenGallery = { openGallery() },
                        onRequestLocation = { requestLocation() },
                        onIntentAccept = { acceptIntentShot() },
                        onClose = if (vm.intentMode) ({ cancelIntent() }) else null,
                    )
                } else {
                    PermissionScreen(
                        onRequest = {
                            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        },
                        onOpenSettings = {
                            startActivity(
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    protected open fun handleIntent(intent: Intent?) {
        val action = intent?.action
        secure = action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE ||
            action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE
        setShowWhenLocked(secure)
        setTurnScreenOn(secure)
        when (action) {
            MediaStore.INTENT_ACTION_VIDEO_CAMERA -> vm.setMode(com.afam.pura.settings.CaptureMode.VIDEO)
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE ->
                if (vm.settings.value.mode == com.afam.pura.settings.CaptureMode.VIDEO) vm.setMode(com.afam.pura.settings.CaptureMode.PHOTO)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        hasCamera = granted(Manifest.permission.CAMERA)
        orientationListener?.enable()
        vm.onResume()
    }

    override fun onPause() {
        orientationListener?.disable()
        vm.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!hasCamera) return super.onKeyDown(keyCode, event)
                if (event.repeatCount > 0 && vm.settings.value.volumeKeys == com.afam.pura.settings.VolumeKeys.SHUTTER) return true
                if (vm.onVolumeKey(keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
            }
            KeyEvent.KEYCODE_CAMERA -> {
                if (event.repeatCount == 0) vm.shutter()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            vm.updateSettings { it.copy(geotag = true) }
            vm.onPermissionsChanged()
        } else {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun openGallery() {
        val uri = vm.lastUri ?: return
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (vm.lastIsVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val launch: () -> Unit = {
            try {
                startActivity(view)
            } catch (e: ActivityNotFoundException) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            }
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (secure && km?.isKeyguardLocked == true) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    launch()
                }
            })
        } else {
            launch()
        }
    }

    // ---- Richieste di altre app (sovrascritte in CaptureActivity) ----

    protected open fun acceptIntentShot() = Unit
    protected open fun cancelIntent() = Unit
    protected open fun onVideoForIntent(uri: Uri) = Unit
}

/** Usata quando un'altra app chiede una foto o un video. */
class CaptureActivity : MainActivity() {

    private var output: Uri? = null

    override fun handleIntent(intent: Intent?) {
        super.handleIntent(intent)
        val video = intent?.action == MediaStore.ACTION_VIDEO_CAPTURE
        vm.configureIntent(capture = true, video = video)
        output = intent?.let {
            if (android.os.Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
        }
    }

    override fun acceptIntentShot() {
        val shot = vm.intentShot.value ?: return
        val target = output
        if (target != null) {
            val ok = runCatching {
                contentResolver.openOutputStream(target, "wt")?.use { it.write(shot.bytes) } != null
            }.getOrDefault(false)
            setResult(if (ok) RESULT_OK else RESULT_CANCELED, Intent().apply {
                data = target
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } else {
            // Senza file di destinazione si restituisce una miniatura, come da standard Android.
            val small = shot.preview?.let { scaleDown(it, 640) }
            setResult(RESULT_OK, Intent("inline-data").putExtra("data", small))
        }
        finish()
    }

    override fun onVideoForIntent(uri: Uri) {
        val target = output
        if (target != null) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    contentResolver.openOutputStream(target, "wt")?.use { input.copyTo(it) }
                }
            }
            setResult(RESULT_OK, Intent().apply {
                data = target
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } else {
            setResult(RESULT_OK, Intent().apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
        finish()
    }

    override fun cancelIntent() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun scaleDown(b: Bitmap, max: Int): Bitmap {
        val scale = max.toFloat() / maxOf(b.width, b.height)
        if (scale >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true)
    }
}
