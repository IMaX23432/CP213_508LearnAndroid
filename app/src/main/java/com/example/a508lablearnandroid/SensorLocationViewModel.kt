package com.example.a508_lablearnandroid

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccelerometerData(val x: Float, val y: Float, val z: Float)
data class LocationData(val latitude: Double, val longitude: Double)

class SensorLocationViewModel : ViewModel() {
    private val _accelerometerData = MutableStateFlow(AccelerometerData(0f, 0f, 0f))
    val accelerometerData: StateFlow<AccelerometerData> = _accelerometerData.asStateFlow()

    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData.asStateFlow()

    fun updateAccelerometerData(x: Float, y: Float, z: Float) {
        _accelerometerData.value = AccelerometerData(x, y, z)
    }

    fun updateLocationData(latitude: Double, longitude: Double) {
        _locationData.value = LocationData(latitude, longitude)
    }
}
