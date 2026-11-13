package com.afam.galleria.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import com.afam.galleria.data.MediaItem

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Condivisione, "Apri con", "Imposta come" e appunti. */
object ShareUtil {

    private fun mimeFor(items: List<MediaItem>): String = when {
        items.size == 1 -> items[0].mime
        items.all { !it.isVideo } -> "image/*"
        items.all { it.isVideo } -> "video/*"
        else -> "*/*"
    }

    fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = ArrayList<Uri>(items.map { it.uri })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeFor(items)
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeFor(items)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        val clip = ClipData.newRawUri("", uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(intent, null))
    }

    fun openWith(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(intent, "Apri con"))
    }

    fun setAs(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(item.uri, item.mime)
            putExtra("mimeType", item.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(intent, "Imposta come"))
    }

    fun copy(context: Context, item: MediaItem) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, item.name, item.uri))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copiato", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMap(context: Context, lat: Double, lng: Double, label: String?) {
        val q = Uri.encode("$lat,$lng" + (label?.let { "($it)" } ?: ""))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$q"))
        start(context, intent)
    }

    fun openManageMediaSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:" + context.packageName)
            }
            if (!start(context, intent)) openAppSettings(context)
        } else {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:" + context.packageName)
        })
    }

    fun openDefaultAppsSettings(context: Context) {
        if (!start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
            openAppSettings(context)
        }
    }

    fun openCamera(context: Context) {
        start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Toast.makeText(context, "Nessuna app disponibile", Toast.LENGTH_SHORT).show()
        false
    }
}

/** Sblocco con impronta/volto o PIN, come Face ID per gli album protetti su iOS. */
object Auth {
    fun authenticate(
        context: Context,
        title: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val activity = context.findActivity()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (activity == null || keyguard == null || !keyguard.isDeviceSecure) {
            onSuccess()
            return
        }
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL
                        ) onSuccess() else onFailure()
                    }
                },
            )
        } catch (e: Exception) {
            onSuccess()
        }
    }
}
