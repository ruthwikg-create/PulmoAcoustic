package com.ruthwik.pulmoacoustic.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class DeviceSensors(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile private var instantMotion = 0.0
    @Volatile private var sessionPeak = 0.0
    @Volatile private var sessionEnergy = 0.0
    @Volatile private var majorMovement = false

    @Volatile private var pitchDeg = 0.0
    @Volatile private var rollDeg = 0.0
    @Volatile private var yawDeg = 0.0
    private var lastGyroTimestampNs = 0L

    fun hasAccelerometer() = accel != null
    fun hasGyroscope() = gyro != null

    fun start() {
        accel?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    fun beginMeasurementSession() {
        lastGyroTimestampNs = 0L
        instantMotion = 0.0
        sessionPeak = 0.0
        sessionEnergy = 0.0
        majorMovement = false
    }

    fun getMeasurementMotionScore(): Double {
        return (0.7 * sessionPeak + 0.3 * sessionEnergy).coerceIn(0.0, 1.0)
    }

    fun getLiveMotionScore(): Double = instantMotion
    fun hasMajorMovement(): Boolean = majorMovement

    fun pitchDegrees(): Double = pitchDeg
    fun rollDegrees(): Double = rollDeg
    fun yawDegrees(): Double = yawDeg

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()
                val az = event.values[2].toDouble()
                val magnitude = sqrt(ax * ax + ay * ay + az * az)
                val normalized = (abs(magnitude - SensorManager.GRAVITY_EARTH) / 3.0).coerceIn(0.0, 1.0)

                rollDeg = Math.toDegrees(atan2(ay, az))
                pitchDeg = Math.toDegrees(atan2(-ax, sqrt(ay * ay + az * az)))

                instantMotion = 0.82 * instantMotion + 0.18 * normalized
                sessionPeak = maxOf(sessionPeak, normalized)
                sessionEnergy = 0.96 * sessionEnergy + 0.04 * normalized
                if (normalized >= 0.45) majorMovement = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0].toDouble()
                val gy = event.values[1].toDouble()
                val gz = event.values[2].toDouble()
                val magnitude = sqrt(gx * gx + gy * gy + gz * gz)
                val normalized = (magnitude / 1.25).coerceIn(0.0, 1.0)

                instantMotion = 0.82 * instantMotion + 0.18 * normalized
                sessionPeak = maxOf(sessionPeak, normalized)
                sessionEnergy = 0.96 * sessionEnergy + 0.04 * normalized
                if (normalized >= 0.45) majorMovement = true

                if (lastGyroTimestampNs != 0L) {
                    val dt = ((event.timestamp - lastGyroTimestampNs) / 1e9).coerceIn(0.0, 0.1)
                    yawDeg += Math.toDegrees(gz * dt)
                    if (yawDeg > 180.0) yawDeg -= 360.0
                    if (yawDeg < -180.0) yawDeg += 360.0
                }
                lastGyroTimestampNs = event.timestamp
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}