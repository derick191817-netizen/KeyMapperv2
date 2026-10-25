package com.example.keymapper.mapping

import android.graphics.RectF
import com.example.keymapper.data.AppSettings
import com.example.keymapper.injection.TouchInjector

class MappingEngine(
    private val injector: TouchInjector,
    private val area: () -> RectF,
    private val settings: () -> AppSettings
) {
    var paused: Boolean = false
    fun onDelta(dx: Float, dy: Float) {}
    fun onMouseButton(bit: Int, down: Boolean) {}
    fun onKey(code: Int, down: Boolean) {}
    fun releaseAll() {}
}
