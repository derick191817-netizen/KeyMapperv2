package com.example.keymapper.injection

class TouchInjector(
    private val injector: ShizukuInjector,
    private val displayId: () -> Int,
    private val extraDelay: Long
) {
    fun shutdown() {}
}
