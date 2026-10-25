package us.bergnet.oversight.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers

/**
 * Tests have no network, so every Coil request is answered with a generated picture.
 * The URL picks the shape: contains "portrait" -> 3:4, "square" -> 1:1, "tiny" -> 64x64, else 16:9.
 * Each picture has a border and a size label so cropping and scaling are visible in snapshots.
 */
object FakeImages {
    fun install(context: Context) {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components { add(FakeImageInterceptor(context)) }
                // Resolve synchronously so a single mainClock advance shows the loaded image
                .coroutineContext(Dispatchers.Main.immediate)
                .crossfade(false)
                .build()
        )
    }

    private class FakeImageInterceptor(private val context: Context) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val url = chain.request.data.toString()
            val (w, h) = when {
                "portrait" in url -> 600 to 800
                "square" in url -> 600 to 600
                "tiny" in url -> 64 to 64
                else -> 1280 to 720
            }
            return SuccessResult(
                image = BitmapDrawable(context.resources, scene(w, h)).asImage(),
                request = chain.request,
                // MEMORY_CACHE makes Coil skip crossfade; its animation runs on the system clock,
                // which Robolectric freezes, so crossfaded images would stay invisible
                dataSource = DataSource.MEMORY_CACHE
            )
        }
    }

    /** Sky/ground scene with a sun, a frame border, and a "WxH" label. */
    private fun scene(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.65f, 0xFF4A90C2.toInt(), 0xFFB5D8EE.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.65f, p)
        p.shader = LinearGradient(0f, h * 0.65f, 0f, h.toFloat(), 0xFF5B8C3A.toInt(), 0xFF2F4F1F.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.65f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFD54F.toInt()
        c.drawCircle(w * 0.78f, h * 0.25f, minOf(w, h) * 0.1f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = minOf(w, h) * 0.02f
        p.color = 0xFFE53935.toInt()
        c.drawRect(p.strokeWidth / 2, p.strokeWidth / 2, w - p.strokeWidth / 2, h - p.strokeWidth / 2, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = minOf(w, h) * 0.12f
        p.setShadowLayer(4f, 0f, 0f, 0xFF000000.toInt())
        c.drawText("${w}x$h", w * 0.05f, h * 0.92f, p)
        return bmp
    }
}
