package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class ConfigPanelView(
    ctx: Context,
    private val host: HudView.Host,
    private val id: Int,
    private val dp: Float,
    private val onClose: (Boolean) -> Unit
) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        setBackgroundColor(Color.argb(240, 30, 30, 30))
        isFocusable = true
        isFocusableInTouchMode = true
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        paint.color = Color.WHITE
        paint.textSize = 40f * dp
        c.drawText("Configurando Botão #$id", 40f * dp, 80f * dp, paint)
        paint.textSize = 24f * dp
        c.drawText("Pressione BACK para cancelar", 40f * dp, 140f * dp, paint)
    }
}
