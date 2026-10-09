package com.yashraj.glidr.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class Haptic { Click, Thud, Tick, Confirm, Reject }

/** Haptic vocabulary: click (taps, keys), thud (right click), tick (detents, latches), confirm, reject. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** [level] is the user's intensity setting: 0 off .. 3 strong. */
    fun play(h: Haptic, level: Int) {
        val v = vibrator ?: return
        if (level <= 0 || !v.hasVibrator()) return
        val scale = level / 3f
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val c = VibrationEffect.startComposition()
            val prim = when (h) {
                Haptic.Click -> VibrationEffect.Composition.PRIMITIVE_CLICK
                Haptic.Thud -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_THUD else VibrationEffect.Composition.PRIMITIVE_CLICK
                Haptic.Tick -> VibrationEffect.Composition.PRIMITIVE_TICK
                Haptic.Confirm -> VibrationEffect.Composition.PRIMITIVE_CLICK
                Haptic.Reject -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_THUD else VibrationEffect.Composition.PRIMITIVE_CLICK
            }
            val supported = v.areAllPrimitivesSupported(prim)
            if (supported) {
                c.addPrimitive(prim, scale)
                if (h == Haptic.Confirm || h == Haptic.Reject) c.addPrimitive(prim, scale, 60)
                c.compose()
            } else {
                predefined(h)
            }
        } else {
            predefined(h)
        }
        v.vibrate(effect)
    }

    private fun predefined(h: Haptic): VibrationEffect = VibrationEffect.createPredefined(
        when (h) {
            Haptic.Click, Haptic.Confirm -> VibrationEffect.EFFECT_CLICK
            Haptic.Thud, Haptic.Reject -> VibrationEffect.EFFECT_HEAVY_CLICK
            Haptic.Tick -> VibrationEffect.EFFECT_TICK
        },
    )
}
