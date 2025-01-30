package ro.sonicpix.heartbeat.presentation

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log

interface HeartBeatListener {
    fun onHeartRateUpdated(heartRate: Int)
}

class HeartRateSensorListener  (): SensorEventListener  {
    private var heartBeatListener: HeartBeatListener? = null

    fun registerListener(heartBeatListener: HeartBeatListener) {
        this.heartBeatListener = heartBeatListener
    }

    fun unregisterListener() {
        this.heartBeatListener = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val heartRate = event.values[0].toInt()
            heartBeatListener?.onHeartRateUpdated(heartRate)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(debugTag, "Accuracy changed: $accuracy")
    }
}
