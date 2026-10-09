package com.yashraj.glidr.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "glidr_settings")

data class Settings(
    val sensitivity: Float = 1.6f,
    val acceleration: Int = 2,
    val scrollSpeed: Float = 1.2f,
    val naturalScroll: Boolean = true,
    val tapToClick: Boolean = true,
    val leftHanded: Boolean = false,
    val layoutId: String = "us",
    val lightTheme: Boolean = false,
    val accent: Int = 0,
    val haptics: Int = 2, // 0 off, 1 light, 2 medium, 3 strong
    val reduceMotion: Boolean = false,
    val keepAwakeEnabled: Boolean = true,
    val nudgeEvery: Int = 1, // index into [NUDGE_SECONDS]
    val autoStop: Int = 0, // index into [AUTO_STOP_MINUTES], 0 = never
    val pauseWhileTouching: Boolean = true,
    val hapticScroll: Boolean = true,
    val hapticKeys: Boolean = true,
    val autoReconnect: Boolean = true,
    val nudgeRoam: Boolean = true,
    val lastHost: String? = null,
) {
    companion object {
        val NUDGE_SECONDS = intArrayOf(15, 30, 60, 120)
        val AUTO_STOP_MINUTES = intArrayOf(0, 30, 60, 120)
    }
}

class SettingsRepository(private val context: Context) {
    private object K {
        val sens = floatPreferencesKey("sens")
        val accel = intPreferencesKey("accel")
        val scroll = floatPreferencesKey("scroll")
        val natural = booleanPreferencesKey("natural")
        val tap = booleanPreferencesKey("tap")
        val left = booleanPreferencesKey("left")
        val layout = stringPreferencesKey("layout")
        val light = booleanPreferencesKey("light")
        val accent = intPreferencesKey("accent")
        val haptics = intPreferencesKey("haptics")
        val reduce = booleanPreferencesKey("reduce")
        val ka = booleanPreferencesKey("ka")
        val every = intPreferencesKey("every")
        val stop = intPreferencesKey("stop")
        val pauseTouch = booleanPreferencesKey("pause_touch")
        val hapticScroll = booleanPreferencesKey("hap_scroll")
        val hapticKeys = booleanPreferencesKey("hap_keys")
        val autoReconnect = booleanPreferencesKey("auto_reconnect")
        val nudgeRoam = booleanPreferencesKey("nudge_roam")
        val host = stringPreferencesKey("host")
    }

    val flow: Flow<Settings> = context.store.data.map { map(it) }

    private fun map(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            sensitivity = p[K.sens] ?: d.sensitivity,
            acceleration = p[K.accel] ?: d.acceleration,
            scrollSpeed = p[K.scroll] ?: d.scrollSpeed,
            naturalScroll = p[K.natural] ?: d.naturalScroll,
            tapToClick = p[K.tap] ?: d.tapToClick,
            leftHanded = p[K.left] ?: d.leftHanded,
            layoutId = p[K.layout] ?: d.layoutId,
            lightTheme = p[K.light] ?: d.lightTheme,
            accent = p[K.accent] ?: d.accent,
            haptics = p[K.haptics] ?: d.haptics,
            reduceMotion = p[K.reduce] ?: d.reduceMotion,
            keepAwakeEnabled = p[K.ka] ?: d.keepAwakeEnabled,
            nudgeEvery = p[K.every] ?: d.nudgeEvery,
            autoStop = p[K.stop] ?: d.autoStop,
            pauseWhileTouching = p[K.pauseTouch] ?: d.pauseWhileTouching,
            hapticScroll = p[K.hapticScroll] ?: d.hapticScroll,
            hapticKeys = p[K.hapticKeys] ?: d.hapticKeys,
            autoReconnect = p[K.autoReconnect] ?: d.autoReconnect,
            nudgeRoam = p[K.nudgeRoam] ?: d.nudgeRoam,
            lastHost = p[K.host],
        )
    }

    suspend fun update(block: (Settings) -> Settings) {
        context.store.edit { p ->
            val s = block(read(p))
            p[K.sens] = s.sensitivity
            p[K.accel] = s.acceleration
            p[K.scroll] = s.scrollSpeed
            p[K.natural] = s.naturalScroll
            p[K.tap] = s.tapToClick
            p[K.left] = s.leftHanded
            p[K.layout] = s.layoutId
            p[K.light] = s.lightTheme
            p[K.accent] = s.accent
            p[K.haptics] = s.haptics
            p[K.reduce] = s.reduceMotion
            p[K.ka] = s.keepAwakeEnabled
            p[K.every] = s.nudgeEvery
            p[K.stop] = s.autoStop
            p[K.pauseTouch] = s.pauseWhileTouching
            p[K.hapticScroll] = s.hapticScroll
            p[K.hapticKeys] = s.hapticKeys
            p[K.autoReconnect] = s.autoReconnect
            p[K.nudgeRoam] = s.nudgeRoam
            val h = s.lastHost
            if (h == null) p.remove(K.host) else p[K.host] = h
        }
    }

    private fun read(p: Preferences): Settings = map(p)
}
