package com.example.keymapper.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.input.CaptureView

class OverlayManager(
    private val ctx: Context,
    private val displays: DisplayHelper,
    private val host: HudView.Host,
    private val refreshHz: () -> Int,
    private val onDelta: (Float, Float) -> Unit,
    private val onButton: (Int, Boolean) -> Unit,
    private val onKey: (Int, Boolean) -> Unit
) {
    private var wm: WindowManager? = null
    private var wctx: Context? = null
    private var bubble: BubbleView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: ConfigPanelView? = null

    var capture: CaptureView? = null
        private set
    var hud: HudView? = null
        private set

    @Volatile
    var display: Display? = null
        private set

    private val base = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    private val hudFlags = base or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    private val hudEditFlags = base or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    private val captureFlags = base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    fun show() {
        hide()
        if (!android.provider.Settings.canDrawOverlays(ctx)) return
        try {
            val d = displays.target()
            display = d
            val wc = displays.windowContext(d)
            wctx = wc
            val manager = wc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm = manager
            val mode = displays.pickMode(d, refreshHz())

            fun params(flags: Int): WindowManager.LayoutParams {
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    flags,
                    PixelFormat.TRANSLUCENT
                )
                lp.alpha = 0.8f
                if (mode != null) {
                    lp.preferredDisplayModeId = mode.modeId
                    lp.preferredRefreshRate = mode.refreshRate
                }
                return lp
            }

            val h = HudView(wc, host)
            val c = CaptureView(wc, onDelta, onButton, onKey)
            manager.addView(h, params(hudFlags))
            hud = h
            manager.addView(c, params(captureFlags))
            capture = c
            c.start()

            val dens = host.uiDensity()
            val info = DisplayUtils.info(wc, d)
            val b = BubbleView(
                wc, dens,
                onTap = { host.setEditing(!(hud?.editing ?: false)) },
                onDrag = { dx, dy -> moveBubble(dx, dy) }
            )
            val blp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            blp.gravity = Gravity.TOP or Gravity.START
            blp.x = info.width - (70 * dens).toInt()
            blp.y = (20 * dens).toInt()
            manager.addView(b, blp)
            bubble = b
            bubbleLp = blp
        } catch (e: Exception) {
            hide()
        }
    }

    private fun moveBubble(dx: Int, dy: Int) {
        val lp = bubbleLp ?: return
        val v = bubble ?: return
        lp.x += dx
        lp.y += dy
        try { wm?.updateViewLayout(v, lp) } catch (e: Exception) {}
    }

    fun setEditing(on: Boolean) {
        val h = hud ?: return
        try {
            val lp = h.layoutParams as? WindowManager.LayoutParams ?: return
            lp.flags = if (on) hudEditFlags else hudFlags
            wm?.updateViewLayout(h, lp)
        } catch (e: Exception) { return }
        h.editing = on
        if (!on) { h.armed = false; closeConfig() }
        bubble?.active = on
        h.postInvalidate()
        if (on) capture?.stop() else capture?.start()
    }

    fun openConfig(id: Int) {
        val wc = wctx ?: return
        val manager = wm ?: return
        val d = display ?: return
        closeConfig()
        try {
            val dens = host.uiDensity()
            val screen = DisplayUtils.info(wc, d)
            val b = host.settings().buttons.firstOrNull { it.id == id }
            val onTop = b != null && b.y > 0.5f
            val p = ConfigPanelView(wc, host, id, dens) { saved -> closeConfig(); if (saved) host.persist() }
            val lp = WindowManager.LayoutParams(
                minOf(screen.width, (560 * dens).toInt()),
                (screen.height * 0.46f).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL or (if (onTop) Gravity.TOP else Gravity.BOTTOM)
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            manager.addView(p, lp)
            p.requestFocus()
            panel = p
        } catch (e: Exception) { panel = null }
    }

    private fun closeConfig() {
        val p = panel ?: return
        try { wm?.removeView(p) } catch (e: Exception) {}
        panel = null
    }

    fun setCapture(on: Boolean) { if (on) capture?.start() else capture?.stop() }
    fun refresh() { hud?.postInvalidate() }

    fun hide() {
        capture?.stop()
        closeConfig()
        val m = wm
        val views = listOf<android.view.View?>(hud, capture, bubble)
        for (v in views) { if (v != null) { try { m?.removeView(v) } catch (e: Exception) {} } }
        hud = null; capture = null; bubble = null; bubbleLp = null; wm = null; wctx = null
    }
}
