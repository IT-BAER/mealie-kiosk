package com.baer.mealiekiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltDetectorTest {
    private val d = TiltDetector()

    /** One sample at the given tilt in degrees, taken at the given time in milliseconds. */
    private fun at(deg: Double, ms: Long): Posture? {
        val rad = Math.toRadians(deg)
        return d.sample((9.81 * sin(rad)).toFloat(), 0f, (-9.81 * cos(rad)).toFloat(), ms * 1_000_000L)
    }

    @Test fun firstSampleReportsThePostureAtOnce() {
        assertEquals(Posture.STOWED, at(0.0, 0))
    }

    @Test fun firstSampleWhileDeployedReportsDeployed() {
        assertEquals(Posture.DEPLOYED, at(95.0, 0))
    }

    @Test fun aChangeNeedsTheAngleHeldForFourHundredMilliseconds() {
        at(0.0, 0)
        assertNull(at(95.0, 100))
        assertNull(at(95.0, 499))
        assertEquals(Posture.DEPLOYED, at(95.0, 500))
    }

    @Test fun theSamePostureIsReportedOnlyOnce() {
        at(0.0, 0)
        at(95.0, 100)
        assertEquals(Posture.DEPLOYED, at(95.0, 500))
        assertNull(at(95.0, 1500))
    }

    @Test fun leavingTheAngleRestartsTheHold() {
        at(0.0, 0)
        at(95.0, 100)
        at(0.0, 300)
        assertNull(at(95.0, 600))
        assertNull(at(95.0, 900))
        assertEquals(Posture.DEPLOYED, at(95.0, 1000))
    }

    @Test fun theHysteresisBandHoldsTheCurrentPosture() {
        at(95.0, 0)
        assertNull(at(45.0, 1000))
        assertNull(at(45.0, 2000))
        at(35.0, 3000)
        assertEquals(Posture.STOWED, at(35.0, 3400))
        assertNull(at(45.0, 4000))
        assertNull(at(45.0, 5000))
    }

    @Test fun aSampleWithoutGravityNeitherCountsNorResets() {
        at(0.0, 0)
        at(95.0, 100)
        assertNull(d.sample(0f, 0f, 0f, 300_000_000L))
        assertEquals(Posture.DEPLOYED, at(95.0, 500))
    }
}
