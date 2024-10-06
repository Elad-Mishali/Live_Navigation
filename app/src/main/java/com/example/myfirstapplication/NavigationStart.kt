package com.example.myfirstapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.widget.TextView


class NavigationStart : ComponentActivity(), SensorEventListener {

    // Declare sensor-related variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var accDataView: TextView
    private lateinit var gyroDataView: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.navigation_start)

        // Initialize views
        val navigationParameters = findViewById<EditText>(R.id.inputPoints)
        val submitButton = findViewById<Button>(R.id.submit_button)
        accDataView = findViewById(R.id.sensorView)
        gyroDataView = findViewById(R.id.sensorView2)

        // Initialize SensorManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Initialize sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Check if sensors are available
        if (accelerometer == null) {
            accDataView.text = "Accelerometer not available"
        } else {
            accDataView.text = "Accelerometer available"
        }

        if (gyroscope == null) {
            gyroDataView.text = "Gyroscope not available"
        } else {
            gyroDataView.text = "Gyroscope available"
        }

        // Handle what happens when user presses the submit button
        submitButton.setOnClickListener {
            val userInput: String = navigationParameters.text.toString()

            // Split the input string by commas and check for correct format
            val stringCoordinates: List<String> = userInput.split(",")
            if (stringCoordinates.size != 4) {
                navigationParameters.error = "Please enter four comma-separated coordinates"
                return@setOnClickListener
            }

            try {
                // Convert string inputs to doubles
                stringCoordinates[0].toDouble()
                stringCoordinates[1].toDouble()
                stringCoordinates[2].toDouble()
                stringCoordinates[3].toDouble()
            } catch (e: NumberFormatException) {
                // Handle number format exception if the input is not valid
                navigationParameters.error = "Invalid input. Please enter valid numbers."
                return@setOnClickListener
            }

            val intent = Intent(this,LiveNavigation::class.java)
            intent.putExtra("Coordinates", userInput)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        // Register sensor listeners if sensors are available
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()

        // Unregister the sensor listeners to conserve battery
        sensorManager.unregisterListener(this)
    }

    // Handle sensor data
    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    accDataView.text = "Accelerometer:\n X: $x\n Y: $y\n Z: $z\n"
                }
                Sensor.TYPE_GYROSCOPE -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    gyroDataView.text = "Gyroscope:\n X: $x\n Y: $y\n Z: $z\n"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}
