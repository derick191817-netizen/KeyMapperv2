package com.example.keymapper.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

object Haptics {
    @Volatile var enabled = true
    fun tick(ctx: Context) {
        if (!enabled) return
        try {
            val v = ctx.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(12, 90))
        } catch (e: Exception) {}
    }
}
