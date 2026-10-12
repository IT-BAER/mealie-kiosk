package com.baer.mealiekiosk

import kotlin.math.acos
import kotlin.math.sqrt

enum class Posture { STOWED, DEPLOYED }

/**
 * Turns accelerometer samples into the tablet's mount posture.
 * Tilt is the angle between the screen normal and straight down: 0 deg is stowed flat with the
 * screen facing the floor, 90 deg is the screen upright.
 */
class TiltDetector(
    private val deployedDeg: Float = 50f,
    private val stowedDeg: Float = 40f,
    private val holdNs: Long = 400_000_000L,
) {
    var posture: Posture? = null
        private set
    private var pending: Posture? = null
    private var pendingSince = 0L

    /** Returns the posture when it changed, else null. */
    fun sample(x: Float, y: Float, z: Float, timestampNs: Long): Posture? {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < 1f) return null
        val tilt = Math.toDegrees(acos((-z / magnitude).coerceIn(-1f, 1f).toDouble())).toFloat()
        val settled = posture
        if (settled == null) {
            posture = if (tilt >= deployedDeg) Posture.DEPLOYED else Posture.STOWED
            return posture
        }
        val candidate = when {
            tilt >= deployedDeg -> Posture.DEPLOYED
            tilt <= stowedDeg -> Posture.STOWED
            else -> settled
        }
        if (candidate == settled) {
            pending = null
            return null
        }
        if (candidate != pending) {
            pending = candidate
            pendingSince = timestampNs
            return null
        }
        if (timestampNs - pendingSince < holdNs) return null
        pending = null
        posture = candidate
        return candidate
    }
}
