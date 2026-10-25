package com.example.keymapper.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(context: Context) {
    private val _flow = MutableStateFlow(AppSettings())
    val flow: StateFlow<AppSettings> = _flow

    suspend fun save(settings: AppSettings) {
        _flow.value = settings
    }
}
