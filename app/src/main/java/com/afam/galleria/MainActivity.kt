package com.afam.galleria

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.theme.GalleryTheme

/** Come è stata aperta l'app. */
sealed interface LaunchRequest {
    data object Main : LaunchRequest
    data class View(val uri: Uri, val type: String?) : LaunchRequest
    data class Pick(val type: String?, val multiple: Boolean) : LaunchRequest
}

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val request = parse(intent)
        if (request is LaunchRequest.View && savedInstanceState == null) {
            vm.openExternal(request.uri, request.type)
        }
        setContent {
            GalleryTheme {
                GalleryRoot(
                    vm = vm,
                    request = request,
                    onPicked = { returnPicked(it) },
                    onFinish = { finish() },
                )
            }
        }
    }

    private fun parse(intent: Intent?): LaunchRequest {
        if (intent == null) return LaunchRequest.Main
        return when (intent.action) {
            Intent.ACTION_VIEW,
            MediaStore.ACTION_REVIEW,
            MediaStore.ACTION_REVIEW_SECURE,
            "com.android.camera.action.REVIEW" -> {
                val uri = intent.data
                if (uri != null) LaunchRequest.View(uri, intent.type) else LaunchRequest.Main
            }
            Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT -> LaunchRequest.Pick(
                type = intent.type,
                multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
            )
            else -> LaunchRequest.Main
        }
    }

    private fun returnPicked(items: List<MediaItem>) {
        if (items.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val data = Intent()
        data.data = items[0].uri
        val clip = ClipData.newRawUri(null, items[0].uri)
        items.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
        data.clipData = clip
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, data)
        finish()
    }
}
