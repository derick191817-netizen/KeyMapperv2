package com.example.keymapper.data

enum class Kind { NORMAL, TOGGLE, MACRO, AIM }

data class HudButton(
    val id: Int,
    val key: Int,
    val x: Float,
    val y: Float,
    val size: Float = 0.08f,
    val opacity: Float = 0.8f,
    val kind: Kind = Kind.NORMAL,
    val macro: List<Int> = emptyList()
)

data class AppSettings(
    val areaMode: Int = 0,
    val areaRatio: Float = 16f / 9f,
    val areaX: Float = 0f,
    val areaY: Float = 0f,
    val areaW: Float = 1f,
    val areaH: Float = 1f,
    val uiScale: Float = 1f,
    val globalSens: Float = 1f,
    val joyR: Float = 0.12f,
    val lookR: Float = 0.25f,
    val joyX: Float = 0.15f,
    val joyY: Float = 0.8f,
    val lookX: Float = 0.85f,
    val lookY: Float = 0.5f,
    val buttons: List<HudButton> = emptyList(),
    val hudAlpha: Float = 0.8f,
    val showHud: Boolean = true,
    val perfMode: Int = 0,
    val refreshHz: Int = 60,
    val editKey: Int = -1,
    val pauseKey: Int = -2,
    val haptics: Boolean = true
)

fun keyLabel(key: Int): String = when (key) {
    -1 -> "M1"
    -2 -> "M2"
    else -> key.toString()
}
