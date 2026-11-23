package com.afam.pura.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CaptureResult
import android.location.Location
import android.media.Image
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Riduzione del rumore multi-scatto, senza AI: si allineano più fotogrammi identici e se ne fa
 * la media. Il rumore (casuale) si riduce di √N, i dettagli (fissi) restano. I pixel che
 * cambiano troppo tra un fotogramma e l'altro (soggetti in movimento) vengono presi solo dal
 * primo fotogramma, così non compaiono "fantasmi".
 */
class MultiFrameMerger(
    private val w: Int,
    private val h: Int,
    iso: Int,
) {
    private val cw = w / 2
    private val ch = h / 2
    private val refY = ByteArray(w * h)
    private val refU = ByteArray(cw * ch)
    private val refV = ByteArray(cw * ch)
    // Allocati solo dal secondo fotogramma: lo scatto singolo usa poca memoria.
    private val sumY by lazy { ShortArray(w * h) }
    private val sumU by lazy { ShortArray(cw * ch) }
    private val sumV by lazy { ShortArray(cw * ch) }
    private val frameY by lazy { ByteArray(w * h) }
    private val frameU by lazy { ByteArray(cw * ch) }
    private val frameV by lazy { ByteArray(cw * ch) }

    private val sw = w / SCALE
    private val sh = h / SCALE
    private var refSmall: IntArray? = null
    private var patch: Box? = null

    private class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Soglie oltre le quali un pixel è considerato "in movimento". Si ricalcolano per ogni
     * fotogramma dal rumore misurato (3σ), così le zone ferme vengono mediate quasi sempre.
     */
    private var tY = (12 + iso / 70).coerceAtMost(60)
    private var tC = (10 + iso / 100).coerceAtMost(50)

    var count = 0
        private set

    /** Aggiunge un fotogramma YUV_420_888 (l'immagine si può chiudere subito dopo). */
    fun add(image: Image) {
        if (image.width != w || image.height != h) return
        addFrame { y, u, v -> readPlanes(image, y, u, v) }
    }

    /** Ultimo spostamento stimato (per i test). */
    var lastShift: Pair<Int, Int> = 0 to 0
        private set

    /** [fill] riempie i piani Y, U, V compatti del fotogramma. */
    fun addFrame(fill: (ByteArray, ByteArray, ByteArray) -> Unit) {
        if (count == 0) {
            fill(refY, refU, refV)
            count = 1
            return
        }
        if (count == 1) {
            for (i in refY.indices) sumY[i] = (refY[i].toInt() and 0xFF).toShort()
            for (i in refU.indices) {
                sumU[i] = (refU[i].toInt() and 0xFF).toShort()
                sumV[i] = (refV[i].toInt() and 0xFF).toShort()
            }
            refSmall = downscale(refY)
            patch = detailPatch()
        }
        fill(frameY, frameU, frameV)
        val (dx, dy) = align()
        lastShift = dx to dy
        estimateNoise(dx, dy)
        accumulate(dx, dy)
        count++
    }

    /** Immagine finale in NV21, pronta per la compressione JPEG. */
    fun toNv21(): ByteArray {
        val out = ByteArray(w * h + 2 * cw * ch)
        if (count <= 1) {
            System.arraycopy(refY, 0, out, 0, w * h)
            var o = w * h
            for (i in 0 until cw * ch) {
                out[o++] = refV[i]
                out[o++] = refU[i]
            }
            return out
        }
        val n = count
        val half = n / 2
        for (i in 0 until w * h) out[i] = ((sumY[i] + half) / n).toByte()
        var o = w * h
        for (i in 0 until cw * ch) {
            out[o++] = ((sumV[i] + half) / n).toByte()
            out[o++] = ((sumU[i] + half) / n).toByte()
        }
        return out
    }

    fun toJpeg(quality: Int): ByteArray {
        val out = ByteArrayOutputStream(w * h / 3)
        YuvImage(toNv21(), ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), quality, out)
        return out.toByteArray()
    }

    // ---- Lettura dei piani ----

    private fun readPlanes(image: Image, y: ByteArray, u: ByteArray, v: ByteArray) {
        val planes = image.planes
        val yp = planes[0]
        val yb = yp.buffer
        for (row in 0 until h) {
            yb.position(row * yp.rowStride)
            yb.get(y, row * w, w)
        }
        readChroma(planes[1], u)
        readChroma(planes[2], v)
    }

    private fun readChroma(plane: Image.Plane, dst: ByteArray) {
        val buf = plane.buffer
        val ps = plane.pixelStride
        val rs = plane.rowStride
        if (ps == 1) {
            for (row in 0 until ch) {
                buf.position(row * rs)
                buf.get(dst, row * cw, cw)
            }
            return
        }
        val len = (cw - 1) * ps + 1
        val tmp = ByteArray(len)
        for (row in 0 until ch) {
            buf.position(row * rs)
            buf.get(tmp, 0, min(len, buf.remaining()))
            var s = 0
            val base = row * cw
            for (x in 0 until cw) {
                dst[base + x] = tmp[s]
                s += ps
            }
        }
    }

    // ---- Allineamento (spostamento globale, da grossolano a fine) ----

    private fun downscale(src: ByteArray): IntArray {
        val out = IntArray(sw * sh)
        for (sy in 0 until sh) {
            for (sx in 0 until sw) {
                var acc = 0
                // Campiona 4x4 punti dentro il blocco 8x8: abbastanza per allineare.
                var yy = sy * SCALE
                while (yy < sy * SCALE + SCALE) {
                    val row = yy * w + sx * SCALE
                    acc += (src[row].toInt() and 0xFF) + (src[row + 2].toInt() and 0xFF) +
                        (src[row + 4].toInt() and 0xFF) + (src[row + 6].toInt() and 0xFF)
                    yy += 2
                }
                out[sy * sw + sx] = acc
            }
        }
        return out
    }

    /** La zona 256×256 con più dettagli (bordi): lì l'allineamento fine è affidabile. */
    private fun detailPatch(): Box {
        val size = min(256, min(w, h) / 4)
        var best = Box(w / 2 - size / 2, h / 2 - size / 2, w / 2 + size / 2, h / 2 + size / 2)
        var bestScore = -1L
        for (gy in 1..3) for (gx in 1..3) {
            val cx = w * gx / 4
            val cy = h * gy / 4
            val r = Box(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            var score = 0L
            var y = r.top + 1
            while (y < r.bottom - 1) {
                var x = r.left + 1
                while (x < r.right - 1) {
                    val c = refY[y * w + x].toInt() and 0xFF
                    score += abs(c - (refY[y * w + x + 1].toInt() and 0xFF)) + abs(c - (refY[(y + 1) * w + x].toInt() and 0xFF))
                    x += 4
                }
                y += 4
            }
            if (score > bestScore) {
                bestScore = score
                best = r
            }
        }
        return best
    }

    private fun align(): Pair<Int, Int> {
        val ref = refSmall ?: return 0 to 0
        val cur = downscale(frameY)
        // 1) Ricerca grossolana a 1/8 di risoluzione (±6 → ±48 pixel reali).
        val r = 6
        var bestX = 0
        var bestY = 0
        var best = Long.MAX_VALUE
        for (dy in -r..r) for (dx in -r..r) {
            var sad = 0L
            var y = r
            while (y < sh - r) {
                val ro = y * sw
                val co = (y + dy) * sw + dx
                var x = r
                while (x < sw - r) {
                    sad += abs(ref[ro + x] - cur[co + x])
                    x++
                }
                y++
            }
            if (sad < best) {
                best = sad
                bestX = dx
                bestY = dy
            }
        }
        // 2) Rifinitura a piena risoluzione sulla zona più dettagliata.
        val p = patch ?: return bestX * SCALE to bestY * SCALE
        val cx = bestX * SCALE
        val cy = bestY * SCALE
        val f = SCALE
        var fx = cx
        var fy = cy
        best = Long.MAX_VALUE
        for (dy in cy - f..cy + f) for (dx in cx - f..cx + f) {
            if (p.left + dx < 0 || p.right + dx >= w || p.top + dy < 0 || p.bottom + dy >= h) continue
            var sad = 0L
            var y = p.top
            while (y < p.bottom) {
                val ro = y * w
                val co = (y + dy) * w + dx
                var x = p.left
                while (x < p.right) {
                    sad += abs((refY[ro + x].toInt() and 0xFF) - (frameY[co + x].toInt() and 0xFF))
                    x += 2
                }
                y += 2
            }
            if (sad < best) {
                best = sad
                fx = dx
                fy = dy
            }
        }
        return fx to fy
    }

    /** Stima del rumore (deviazione mediana assoluta) tra fotogramma allineato e riferimento. */
    private fun estimateNoise(dx: Int, dy: Int) {
        val histY = IntArray(256)
        val histC = IntArray(256)
        val step = 5
        var y = 8
        while (y < h - 8) {
            val fy = y + dy
            if (fy in 0 until h) {
                var x = 8
                while (x < w - 8) {
                    val fx = x + dx
                    if (fx in 0 until w) {
                        histY[abs((frameY[fy * w + fx].toInt() and 0xFF) - (refY[y * w + x].toInt() and 0xFF))]++
                    }
                    x += step
                }
            }
            y += step
        }
        val cdx = dx shr 1
        val cdy = dy shr 1
        y = 4
        while (y < ch - 4) {
            val fy = y + cdy
            if (fy in 0 until ch) {
                var x = 4
                while (x < cw - 4) {
                    val fx = x + cdx
                    if (fx in 0 until cw) {
                        val j = fy * cw + fx
                        val i = y * cw + x
                        histC[abs((frameU[j].toInt() and 0xFF) - (refU[i].toInt() and 0xFF))]++
                        histC[abs((frameV[j].toInt() and 0xFF) - (refV[i].toInt() and 0xFF))]++
                    }
                    x += step
                }
            }
            y += step
        }
        // σ ≈ 1,4826 · mediana(|d|); soglia = 3σ (99,7% dei pixel fermi viene mediato).
        tY = (3 * 1.4826 * median(histY)).roundToInt().coerceIn(8, 80)
        tC = (3 * 1.4826 * median(histC)).roundToInt().coerceIn(6, 60)
    }

    private fun median(hist: IntArray): Double {
        val total = hist.sum()
        if (total == 0) return 4.0
        var acc = 0
        for (i in hist.indices) {
            acc += hist[i]
            if (acc * 2 >= total) return max(1.0, i.toDouble())
        }
        return 4.0
    }

    // ---- Media con esclusione dei pixel in movimento ----

    private fun accumulate(dx: Int, dy: Int) {
        val threads = POOL_SIZE
        val tasks = (0 until threads).map { t ->
            Callable {
                val y0 = h * t / threads
                val y1 = h * (t + 1) / threads
                for (y in y0 until y1) {
                    val fy = y + dy
                    val ro = y * w
                    if (fy < 0 || fy >= h) {
                        for (x in 0 until w) sumY[ro + x] = (sumY[ro + x] + (refY[ro + x].toInt() and 0xFF)).toShort()
                        continue
                    }
                    val fo = fy * w + dx
                    val xs = max(0, -dx)
                    val xe = min(w, w - dx)
                    for (x in 0 until w) {
                        val i = ro + x
                        val r = refY[i].toInt() and 0xFF
                        val v = if (x in xs until xe) {
                            val fv = frameY[fo + x].toInt() and 0xFF
                            if (abs(fv - r) <= tY) fv else r
                        } else r
                        sumY[i] = (sumY[i] + v).toShort()
                    }
                }
                // Crominanza (risoluzione dimezzata).
                val cdx = dx shr 1
                val cdy = dy shr 1
                val c0 = ch * t / threads
                val c1 = ch * (t + 1) / threads
                for (y in c0 until c1) {
                    val fy = y + cdy
                    val ro = y * cw
                    for (x in 0 until cw) {
                        val i = ro + x
                        val ru = refU[i].toInt() and 0xFF
                        val rv = refV[i].toInt() and 0xFF
                        val fx = x + cdx
                        var u = ru
                        var v = rv
                        if (fy in 0 until ch && fx in 0 until cw) {
                            val j = fy * cw + fx
                            val fu = frameU[j].toInt() and 0xFF
                            val fv = frameV[j].toInt() and 0xFF
                            val fyv = frameY[min(h - 1, (y * 2) + dy).coerceAtLeast(0) * w + (x * 2 + dx).coerceIn(0, w - 1)].toInt() and 0xFF
                            val ryv = refY[(y * 2) * w + x * 2].toInt() and 0xFF
                            // Si usa il fotogramma solo se anche la luminanza coincide (niente fantasmi colorati).
                            if (abs(fu - ru) <= tC && abs(fv - rv) <= tC && abs(fyv - ryv) <= tY) {
                                u = fu
                                v = fv
                            }
                        }
                        sumU[i] = (sumU[i] + u).toShort()
                        sumV[i] = (sumV[i] + v).toShort()
                    }
                }
            }
        }
        pool.invokeAll(tasks).forEach { it.get() }
    }

    companion object {
        private const val SCALE = 8
        const val MAX_FRAMES = 8
        private val POOL_SIZE = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        private val pool = Executors.newFixedThreadPool(POOL_SIZE) { r ->
            Thread(r, "pura-merge").apply { priority = Thread.NORM_PRIORITY + 1 }
        }

        /** Quanti fotogrammi unire in base alla sensibilità: più buio, più fotogrammi. */
        fun framesFor(iso: Int): Int = when {
            iso <= 200 -> 4
            iso <= 800 -> 6
            else -> MAX_FRAMES
        }

        /** Dati EXIF per il JPEG creato da Pura (quello dell'ISP li scrive da sé). */
        fun writeExif(
            jpeg: ByteArray,
            cacheDir: File,
            result: CaptureResult?,
            spec: CameraSpec,
            exifOrientation: Int,
            location: Location?,
            frames: Int,
        ): ByteArray {
            var tmp: File? = null
            return try {
                // Attenzione: Java vuole un prefisso di almeno 3 caratteri.
                tmp = File.createTempFile("pura_mf", ".jpg", cacheDir)
                tmp.writeBytes(jpeg)
                val exif = ExifInterface(tmp)
                val now = Date()
                val stamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(now)
                val offset = SimpleDateFormat("XXX", Locale.US).format(now)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
                exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
                exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, if (frames > 1) "Pura (multi-scatto ×$frames)" else "Pura")
                result?.get(CaptureResult.SENSOR_SENSITIVITY)?.let {
                    exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString())
                }
                result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let {
                    exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, (it / 1e9).toString())
                }
                (result?.get(CaptureResult.LENS_APERTURE) ?: spec.aperture)?.let {
                    exif.setAttribute(ExifInterface.TAG_F_NUMBER, it.toString())
                }
                val focal = result?.get(CaptureResult.LENS_FOCAL_LENGTH) ?: spec.focalLength
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "${(focal * 100).roundToInt()}/100")
                exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, spec.equivalentFocal.roundToInt().toString())
                exif.setAttribute(ExifInterface.TAG_FLASH, "0")
                location?.let { exif.setGpsInfo(it) }
                exif.saveAttributes()
                tmp.readBytes()
            } catch (t: Throwable) {
                // Senza EXIF la foto si salva comunque.
                jpeg
            } finally {
                tmp?.delete()
            }
        }
    }
}
