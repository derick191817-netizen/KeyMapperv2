package com.example.keymapper.mapping

import android.graphics.RectF
import com.example.keymapper.data.AppSettings
import com.example.keymapper.injection.TouchInjector
import kotlin.math.abs

class MappingEngine(
    private val injector: TouchInjector,
    private val area: () -> RectF,
    private val settings: () -> AppSettings
) {
    var paused: Boolean = false
    private var lastX = 0f
    private var lastY = 0f

    fun onDelta(dx: Float, dy: Float) {
        if (paused) return
        val s = settings()
        
        // Ignorar pequenos movimentos (Deadzone)
        if (abs(dx) < s.deadzone && abs(dy) < s.deadzone) return

        // Lógica de Velocidade e Aceleração do Windows
        var speedMult = s.pointerSpeed / 10f
        if (s.enhancePrecision) {
            // Simulação do "Melhorar precisão do ponteiro" (mais rápido = move mais)
            val accel = 1f + (abs(dx) + abs(dy)) / 1000f
            speedMult *= accel.coerceAtMost(3f) // Limite para não ficar incontrolável
        }

        val finalDx = dx * speedMult * s.sensX
        val finalDy = dy * speedMult * s.sensY
        
        lastX += finalDx
        lastY += finalDy
        // Aqui chamaria o injetor de swipe
    }
    fun onMouseButton(bit: Int, down: Boolean) {}
    fun onKey(code: Int, down: Boolean) {}
    fun releaseAll() {}
}
