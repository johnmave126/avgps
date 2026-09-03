package moe.youmu.avgps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class BarometerManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isSupported: Boolean = pressureSensor != null

    private var isListening = false
    private var listener: SensorEventListener? = null

    fun startListening(onPressureChanged: (Float) -> Unit) {
        if (!isSupported || isListening) return
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                    onPressureChanged(event.values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = sensorEventListener
        sensorManager?.registerListener(sensorEventListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        isListening = false
    }
}
