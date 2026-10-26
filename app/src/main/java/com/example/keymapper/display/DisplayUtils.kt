package com.example.keymapper.display

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.RectF
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.example.keymapper.data.AppSettings

data class ScreenInfo(
    val displayId: Int,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val density: Float,
    val refreshRate: Float,
    val external: Boolean
) {
    val ratio: Float get() = if (height > 0) width.toFloat() / height else 16f / 9f
}

object DisplayUtils {
    val DEFAULT = ScreenInfo(0, 1920, 1080, 420, 2.625f, 60f, false)

    @Suppress("DEPRECATION")
    fun info(windowCtx: Context, display: Display): ScreenInfo {
        var w = 0
        var h = 0
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val wm = windowCtx.getSystemService(WindowManager::class.java)
                var b = wm.currentWindowMetrics.bounds
                if (b.width() <= 0 || b.height() <= 0) b = wm.maximumWindowMetrics.bounds
                w = b.width()
                h = b.height()
            } catch (e: Exception) {}
        }
        if (w <= 0 || h <= 0) {
            val p = Point()
            display.getRealSize(p)
            w = p.x
            h = p.y
        }
        val dm = windowCtx.resources.displayMetrics
        val dpi = if (dm.densityDpi > 0) dm.densityDpi else 160
        val density = if (dm.density > 0f) dm.density else dpi / 160f
        return ScreenInfo(display.displayId, w, h, dpi, density, display.refreshRate, display.displayId != Display.DEFAULT_DISPLAY)
    }

    fun gameArea(w: Int, h: Int, s: AppSettings): RectF {
        val fw = w.toFloat()
        val fh = h.toFloat()
        return when (s.areaMode) {
            1 -> {
                val target = s.areaRatio.coerceIn(0.5f, 4f)
                if (fw / fh > target) {
                    val aw = fh * target
                    val l = (fw - aw) / 2f
                    RectF(l, 0f, l + aw, fh)
                } else {
                    val ah = fw / target
                    val t = (fh - ah) / 2f
                    RectF(0f, t, fw, t + ah)
                }
            }
            2 -> {
                val x = s.areaX.coerceIn(0f, 0.95f)
                val y = s.areaY.coerceIn(0f, 0.95f)
                val ww = s.areaW.coerceIn(0.05f, 1f - x)
                val hh = s.areaH.coerceIn(0.05f, 1f - y)
                RectF(fw * x, fh * y, fw * (x + ww), fh * (y + hh))
            }
            else -> RectF(0f, 0f, fw, fh)
        }
    }

    fun uiDensity(info: ScreenInfo, s: AppSettings): Float = (info.density * s.uiScale).coerceIn(1f, 8f)

    fun isDex(ctx: Context, info: ScreenInfo): Boolean =
        info.external || (ctx.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK

    fun applyDexPreset(s: AppSettings): AppSettings = s.copy(
        uiScale = 1.25f,
        sensX = 0.85f, // Corrigido para a nova variável
        sensY = 0.85f, // Corrigido para a nova variável
        joyR = minOf(s.joyR, 0.08f),
        lookR = minOf(s.lookR, 0.16f),
        buttons = s.buttons.map { it.copy(size = minOf(it.size, 0.055f)) }
    )
}
