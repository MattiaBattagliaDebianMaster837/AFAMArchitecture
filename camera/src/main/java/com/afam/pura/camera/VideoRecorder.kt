package com.afam.pura.camera

import android.content.Context
import android.location.Location
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Size
import android.view.Surface
import com.afam.pura.settings.VideoCodec
import java.io.File

/** Capacità degli encoder video del telefono (lette una volta). */
object VideoCaps {
    private val codecs by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder } }
    private val cache = HashMap<String, Boolean>()

    fun mime(codec: VideoCodec) = when (codec) {
        VideoCodec.HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
        VideoCodec.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
    }

    @Synchronized
    fun supports(codec: VideoCodec, size: Size, fps: Int): Boolean {
        val key = "$codec $size $fps"
        return cache.getOrPut(key) {
            val mime = mime(codec)
            codecs.any { info ->
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } && runCatching {
                    info.getCapabilitiesForType(mime).videoCapabilities
                        ?.areSizeAndRateSupported(size.width, size.height, fps.toDouble()) == true
                }.getOrDefault(false)
            }
        }
    }

    fun maxBitrate(codec: VideoCodec): Int {
        val mime = mime(codec)
        return codecs.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            .maxOfOrNull { runCatching { it.getCapabilitiesForType(mime).videoCapabilities?.bitrateRange?.upper ?: 0 }.getOrDefault(0) }
            ?.takeIf { it > 0 } ?: 100_000_000
    }

    /** Bitrate generoso: niente compressione aggressiva che impasta i dettagli. */
    fun bitrate(codec: VideoCodec, size: Size, fps: Int, high: Boolean): Int {
        val bpp = if (codec == VideoCodec.HEVC) 0.15 else 0.25
        var rate = size.width.toDouble() * size.height * fps * bpp
        if (high) rate *= 1.6
        return rate.toInt().coerceIn(2_000_000, maxBitrate(codec))
    }
}

/** Registrazione con MediaRecorder su una superficie persistente (nessuna riconfigurazione della fotocamera). */
class VideoRecorder(private val context: Context) {

    private var surface: Surface? = null
    private var surfaceKey: String? = null
    private var recorder: MediaRecorder? = null

    data class Params(val size: Size, val fps: Int, val codec: VideoCodec, val highBitrate: Boolean)

    private fun encoder(codec: VideoCodec) = when (codec) {
        VideoCodec.HEVC -> MediaRecorder.VideoEncoder.HEVC
        VideoCodec.H264 -> MediaRecorder.VideoEncoder.H264
    }

    /** Superficie da dare alla sessione della fotocamera, già "preparata" per queste dimensioni. */
    fun surfaceFor(p: Params): Surface {
        val key = "${p.size}@${p.fps}/${p.codec}"
        surface?.let { if (key == surfaceKey) return it }
        surface?.release()
        val s = MediaCodec.createPersistentInputSurface()
        val tmp = File(context.cacheDir, "prime.mp4")
        val r = MediaRecorder(context)
        try {
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setOutputFile(tmp.path)
            r.setVideoEncodingBitRate(VideoCaps.bitrate(p.codec, p.size, p.fps, p.highBitrate))
            r.setVideoFrameRate(p.fps)
            r.setVideoSize(p.size.width, p.size.height)
            r.setVideoEncoder(encoder(p.codec))
            r.setInputSurface(s)
            r.prepare()
        } finally {
            r.release()
            tmp.delete()
        }
        surface = s
        surfaceKey = key
        return s
    }

    fun start(p: Params, fd: ParcelFileDescriptor, orientation: Int, location: Location?, audio: Boolean) {
        val s = surfaceFor(p)
        val r = MediaRecorder(context)
        try {
            if (audio) r.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setOutputFile(fd.fileDescriptor)
            r.setVideoEncodingBitRate(VideoCaps.bitrate(p.codec, p.size, p.fps, p.highBitrate))
            r.setVideoFrameRate(p.fps)
            r.setVideoSize(p.size.width, p.size.height)
            r.setVideoEncoder(encoder(p.codec))
            if (audio) {
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(48_000)
                r.setAudioEncodingBitRate(256_000)
                r.setAudioChannels(2)
            }
            r.setOrientationHint(orientation)
            location?.let { r.setLocation(it.latitude.toFloat(), it.longitude.toFloat()) }
            r.setInputSurface(s)
            r.prepare()
            r.start()
            recorder = r
        } catch (t: Throwable) {
            r.release()
            throw t
        }
    }

    /** @return true se il file è valido. */
    fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        return ok
    }

    fun pause() { runCatching { recorder?.pause() } }
    fun resume() { runCatching { recorder?.resume() } }

    fun release() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        surface?.release()
        surface = null
        surfaceKey = null
    }
}
