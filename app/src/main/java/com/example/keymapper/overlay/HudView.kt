package com.example.keymapper.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.keyLabel
import com.example.keymapper.util.Haptics
import kotlin.math.hypot
import kotlin.math.min

class HudView(ctx: Context, private val host: Host) : View(ctx) {
    interface Host {
        fun settings(): AppSettings
        fun update(s: AppSettings)
        fun persist()
        fun setEditing(on: Boolean)
        fun arm()
        fun closeAll()
        fun openConfig(id: Int)
        fun area(): RectF
        fun uiDensity(): Float
    }

    var editing = false
    var armed = false
    private class Grip(val id: Int, val x: Float, val y: Float)
    private var selected = NONE
    private var dragging = false
    private var grabDx = 0f
    private var grabDy = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hits = ArrayList<Pair<RectF, () -> Unit>>()
    private val grips = ArrayList<Grip>()
    private var dp = 1f
    private var ga = 1f
    private var aL = 0f
    private var aT = 0f
    private var aW = 1f
    private var aH = 1f
    private val loc = IntArray(2)

    private companion object {
        const val NONE = -1000
        const val JOY = -10
        const val LOOK = -11
    }

    private fun updateArea() {
        dp = host.uiDensity()
        getLocationOnScreen(loc)
        val a = host.area()
        aL = a.left - loc[0]
        aT = a.top - loc[1]
        aW = a.width().coerceAtLeast(1f)
        aH = a.height().coerceAtLeast(1f)
    }

    private fun px(f: Float) = aL + f * aW
    private fun py(f: Float) = aT + f * aH
    private fun am() = min(aW, aH)

    private fun centerOf(id: Int, s: AppSettings): Pair<Float, Float>? = when (id) {
        JOY -> px(s.joyX) to py(s.joyY)
        LOOK -> px(s.lookX) to py(s.lookY)
        else -> s.buttons.firstOrNull { it.id == id }?.let { px(it.x) to py(it.y) }
    }

    override fun onDraw(c: Canvas) {
        hits.clear()
        grips.clear()
        updateArea()
        val s = host.settings()
        ga = s.hudAlpha
        if (!editing && !s.showHud) return
        val m = am()
        val selBtn = s.buttons.firstOrNull { it.id == selected }
        val macroSel = if (selBtn != null && selBtn.kind == Kind.MACRO) selBtn else null

        if (editing) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * dp
            paint.color = Color.argb(140, 0, 200, 255)
            paint.pathEffect = DashPathEffect(floatArrayOf(10 * dp, 6 * dp), 0f)
            c.drawRect(aL, aT, aL + aW, aT + aH, paint)
            paint.pathEffect = null
        }

        drawArea(c, px(s.lookX), py(s.lookY), s.lookR * m, "OLHAR", selected == LOOK, LOOK)
        drawArea(c, px(s.joyX), py(s.joyY), s.joyR * m, "WASD", selected == JOY, JOY)

        for (b in s.buttons) {
            val cx = px(b.x)
            val cy = py(b.y)
            val r = b.size * m / 2
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((204 * b.opacity * ga).toInt().coerceIn(15, 255), 0, 0, 0)
            c.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * dp
            paint.color = when {
                editing && selected == b.id -> Color.YELLOW
                macroSel != null && macroSel.macro.contains(b.id) -> Color.GREEN
                else -> Color.argb((170 * ga).toInt(), 255, 255, 255)
            }
            c.drawCircle(cx, cy, r, paint)
            val label = keyLabel(b.key) + when (b.kind) {
                Kind.TOGGLE -> "•"
                Kind.MACRO -> "+"
                Kind.AIM -> "◎"
                else -> ""
            }
            text(c, label, cx, cy, (r * 0.55f).coerceAtLeast(10f * dp), Color.WHITE)
            if (editing) {
                drawGrip(c, b.id, cx, cy + r + 14 * dp)
                if (selected == b.id) chip(c, "⚙", cx + r, cy - r) { host.openConfig(b.id) }
            }
        }

        drawToolbar(c)
        if (editing) drawPanel(c, width.toFloat(), height.toFloat(), macroSel != null)
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        c.drawText(t, x, y - (paint.descent() + paint.ascent()) / 2, paint)
    }

    private fun drawArea(c: Canvas, cx: Float, cy: Float, r: Float, label: String, sel: Boolean, id: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((if (editing) 50 else 22) * 1, 0, 0, 0)
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * dp
        paint.color = if (editing && sel) Color.YELLOW else Color.argb(90, 255, 255, 255)
        c.drawCircle(cx, cy, r, paint)
        text(c, label, cx, cy, 14 * dp, Color.argb(140, 255, 255, 255))
        if (editing) drawGrip(c, id, cx, cy - r)
    }

    private fun drawGrip(c: Canvas, id: Int, gx: Float, gy: Float) {
        paint.style = Paint.Style.FILL
        paint.color = if (selected == id) Color.YELLOW else Color.argb(230, 0, 0, 0)
        c.drawCircle(gx, gy, 13 * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * dp
        paint.color = Color.argb(220, 255, 255, 255)
        c.drawCircle(gx, gy, 13 * dp, paint)
        paint.style = Paint.Style.FILL
        paint.color = if (selected == id) Color.BLACK else Color.WHITE
        for (i in 0..1) for (j in 0..2) {
            c.drawCircle(gx + (i - 0.5f) * 5 * dp, gy + (j - 1) * 5 * dp, 1.5f * dp, paint)
        }
        grips.add(Grip(id, gx, gy))
    }

    private fun chip(c: Canvas, label: String, cx: Float, cy: Float, action: () -> Unit) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 0, 0, 0)
        c.drawCircle(cx, cy, 17 * dp, paint)
        text(c, label, cx, cy, 16 * dp, Color.WHITE)
        hits.add(RectF(cx - 20 * dp, cy - 20 * dp, cx + 20 * dp, cy + 20 * dp) to action)
    }

    private fun drawToolbar(c: Canvas) {
        val cx = 70 * dp
        val cy = 74 * dp
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(204, 0, 0, 0)
        c.drawCircle(cx, cy, 56 * dp, paint)
        text(c, "W", cx, cy - 28 * dp, 16 * dp, Color.WHITE)
        text(c, "A", cx - 28 * dp, cy, 16 * dp, Color.WHITE)
        text(c, "S", cx, cy + 28 * dp, 16 * dp, Color.WHITE)
        text(c, "D", cx + 28 * dp, cy, 16 * dp, Color.WHITE)
        chip(c, "X", 150 * dp, 30 * dp) { host.closeAll() }
        chip(c, "⚙", 195 * dp, 30 * dp) { host.setEditing(!editing) }
        chip(c, "+", 240 * dp, 30 * dp) { if (editing) { armed = true; host.arm() } }
        if (editing && armed) {
            text(c, "Pressione uma tecla ou escolha:", 360 * dp, 30 * dp, 13 * dp, Color.WHITE)
            chip(c, "M1", 150 * dp, 80 * dp) { addButton(-1) }
            chip(c, "M2", 195 * dp, 80 * dp) { addButton(-2) }
        }
    }

    private fun drawPanel(c: Canvas, w: Float, h: Float, macroMode: Boolean) {
        if (selected == NONE) return
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add("Tam -" to { sizeDelta(-0.01f) })
        items.add("Tam +" to { sizeDelta(0.01f) })
        if (selected >= 0) {
            items.add("Op -" to { opDelta(-0.1f) })
            items.add("Op +" to { opDelta(0.1f) })
            items.add("Tipo" to { cycleKind() })
            items.add("Config" to { host.openConfig(selected) })
            items.add("Apagar" to { delete() })
        }
        val bw = 56 * dp
        val bh = 40 * dp
        val gap = 8 * dp
        val total = items.size * bw + (items.size - 1) * gap
        var x = (w - total) / 2
        val y = h - bh - 24 * dp
        for ((label, act) in items) {
            val r = RectF(x, y, x + bw, y + bh)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(225, 30, 30, 30)
            c.drawRoundRect(r, 10 * dp, 10 * dp, paint)
            text(c, label, r.centerX(), r.centerY(), 14 * dp, Color.WHITE)
            hits.add(r to act)
            x += bw + gap
        }
        if (macroMode) {
            text(c, "Macro: toque nos botões que ela aciona (verde)", w / 2, y - 18 * dp, 14 * dp, Color.GREEN)
        }
    }

    private fun mutate(f: (AppSettings) -> AppSettings) { host.update(f(host.settings())); invalidate() }

    fun addButton(key: Int) {
        val id = (host.settings().buttons.maxOfOrNull { it.id } ?: -1) + 1
        mutate { it.copy(buttons = it.buttons + HudButton(id, key, 0.5f, 0.5f)) }
        selected = id
        armed = false
        host.persist()
        Haptics.tick(context)
    }

    private fun sizeDelta(d: Float) {
        mutate { s ->
            when (selected) {
                JOY -> s.copy(joyR = (s.joyR + d).coerceIn(0.03f, 0.3f))
                LOOK -> s.copy(lookR = (s.lookR + d * 2).coerceIn(0.05f, 0.5f))
                else -> s.copy(buttons = s.buttons.map { if (it.id == selected) it.copy(size = (it.size + d).coerceIn(0.03f, 0.3f)) else it })
            }
        }
        host.persist()
        Haptics.tick(context)
    }

    private fun opDelta(d: Float) {
        mutate { s -> s.copy(buttons = s.buttons.map { if (it.id == selected) it.copy(opacity = (it.opacity + d).coerceIn(0.1f, 1f)) else it }) }
        host.persist()
        Haptics.tick(context)
    }

    private fun cycleKind() {
        mutate { s -> s.copy(buttons = s.buttons.map { if (it.id == selected) it.copy(kind = Kind.entries[(it.kind.ordinal + 1) % Kind.entries.size]) else it }) }
        host.persist()
        Haptics.tick(context)
    }

    private fun delete() {
        val id = selected
        mutate { s -> s.copy(buttons = s.buttons.filter { it.id != id }.map { it.copy(macro = it.macro.filter { m -> m != id }) }) }
        selected = NONE
        host.persist()
        Haptics.tick(context)
    }

    private fun toggleMacro(macroId: Int, target: Int) {
        mutate { s -> s.copy(buttons = s.buttons.map { if (it.id == macroId) it.copy(macro = if (it.macro.contains(target)) it.macro - target else it.macro + target) else it }) }
        host.persist()
        Haptics.tick(context)
    }

    private fun pick(x: Float, y: Float): Int {
        val m = am()
        val s = host.settings()
        for (b in s.buttons.asReversed()) {
            if (hypot(x - px(b.x), y - py(b.y)) <= b.size * m / 2) return b.id
        }
        if (hypot(x - px(s.joyX), y - py(s.joyY)) <= s.joyR * m) return JOY
        if (hypot(x - px(s.lookX), y - py(s.lookY)) <= s.lookR * m) return LOOK
        return NONE
    }

    private fun moveSelected(cx: Float, cy: Float) {
        val fx = ((cx - aL) / aW).coerceIn(0f, 1f)
        val fy = ((cy - aT) / aH).coerceIn(0f, 1f)
        mutate { s ->
            when (selected) {
                JOY -> s.copy(joyX = fx, joyY = fy)
                LOOK -> s.copy(lookX = fx, lookY = fy)
                else -> s.copy(buttons = s.buttons.map { if (it.id == selected) it.copy(x = fx, y = fy) else it })
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) return false
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateArea()
                val hit = hits.firstOrNull { it.first.contains(x, y) }
                if (hit != null) { hit.second.invoke(); invalidate(); return true }
                val g = grips.firstOrNull { hypot(x - it.x, y - it.y) <= 20 * dp }
                if (g != null) {
                    selected = g.id
                    dragging = true
                    val c = centerOf(g.id, host.settings())
                    grabDx = (c?.first ?: x) - x
                    grabDy = (c?.second ?: y) - y
                    Haptics.tick(context)
                    invalidate()
                    return true
                }
                val picked = pick(x, y)
                val cur = host.settings().buttons.firstOrNull { it.id == selected }
                if (cur != null && cur.kind == Kind.MACRO && picked >= 0 && picked != cur.id) { toggleMacro(cur.id, picked); return true }
                selected = picked
                dragging = false
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) moveSelected(x + grabDx, y + grabDy)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) { dragging = false; host.persist(); Haptics.tick(context) }
        }
        return true
    }
}
