package com.example.keymapper.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display

class DisplayHelper(private val ctx: Context) {
    private val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    fun target(): Display = dm.getDisplay(Display.DEFAULT_DISPLAY)
    fun windowContext(display: Display): Context = ctx.createDisplayContext(display)
    fun pickMode(display: Display, refreshHz: Int): Display.Mode? = null
    fun register(listener: DisplayManager.DisplayListener, handler: Handler) = dm.registerDisplayListener(listener, handler)
    fun unregister(listener: DisplayManager.DisplayListener) = dm.unregisterDisplayListener(listener)
    fun info(display: Display): ScreenInfo = DisplayUtils.info(ctx, display)
}
