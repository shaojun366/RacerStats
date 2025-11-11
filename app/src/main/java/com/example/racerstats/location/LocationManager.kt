package com.example.racerstats.location

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DRAGY_SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb" // Dragy Pro 服务 UUID
        private const val DRAGY_DATA_CHAR_UUID = "0000fff1-0000-1000-8000-00805f9b34fb" // 数据特征值 UUID
        private const val MIN_UPDATE_INTERVAL = 10L // 最小更新间隔（毫秒）
        private const val MIN_DISTANCE = 0.1f // 最小距离更新（米）
        private const val RATE_CALCULATION_WINDOW = 1000L // 计算采样率的时间窗口（毫秒）
    }
    
    // 用于计算实际采样率
    private val locationTimestamps = mutableListOf<Long>()
    private var lastRateCalculation = 0L

    private val systemLocationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    private var dragyDevice: BluetoothDevice? = null
    private var dragyGatt: BluetoothGatt? = null
    private var isDragyConnected = false
    private var isGnssCallbackRegistered = false

    // LiveData 用于观察位置和状态更新
    private val _locationData = MutableLiveData<LocationData>()
    val locationData: LiveData<LocationData> = _locationData

    private val _locationUpdates = MutableSharedFlow<LocationData>(replay = 1, extraBufferCapacity = 1)
    val locationUpdates: SharedFlow<LocationData> = _locationUpdates.asSharedFlow()

    private val _connectionStatus = MutableLiveData<ConnectionStatus>()
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus
    
    private val _gnssData = MutableLiveData<GnssData>()
    val gnssData: LiveData<GnssData> = _gnssData
    private var lastLocationData: LocationData? = null
    
    private val gnssCallback = object : android.location.GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
            var satsUsed = 0
            val satsInView = status.satelliteCount
            
            // 计算正在使用的卫星数量
            for (i in 0 until satsInView) {
                if (status.usedInFix(i)) {
                    satsUsed++
                }
            }
            
            // 获取当前精度
            val lastLocation = try {
                systemLocationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
            } catch (e: SecurityException) {
                null
            }
            
            // 计算当前采样率
            val currentTime = System.currentTimeMillis()
            locationTimestamps.add(currentTime)
            
            // 移除超出时间窗口的时间戳
            locationTimestamps.removeAll { it < currentTime - RATE_CALCULATION_WINDOW }
            
            // 计算采样率（每秒采样次数）
            val updateRate = if (locationTimestamps.size > 1) {
                locationTimestamps.size.toFloat() * (1000f / RATE_CALCULATION_WINDOW)
            } else {
                0f
            }
            
            _gnssData.postValue(GnssData(
                satsInView = satsInView,
                satsUsed = satsUsed,
                accuracy = lastLocation?.accuracy ?: 0f,
                updateRate = updateRate
            ))
        }
    }

    // 定义数据类
    data class LocationData(
        val timestamp: Long,
        val latitude: Double,
        val longitude: Double,
        val speed: Float,    // m/s
        val accuracy: Float, // 米
        val altitude: Double,
        val bearing: Float,
        val source: Source
    )

    enum class Source {
        DRAGY_PRO,
        PHONE_GPS
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED_DRAGY,
        CONNECTED_GPS,
        ERROR
    }

    // Dragy Pro 蓝牙连接回调
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isDragyConnected = true
                    _connectionStatus.postValue(ConnectionStatus.CONNECTED_DRAGY)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isDragyConnected = false
                    _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
                    fallbackToPhoneGPS()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(UUID.fromString(DRAGY_SERVICE_UUID))
                val characteristic = service?.getCharacteristic(UUID.fromString(DRAGY_DATA_CHAR_UUID))
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let { char ->
                if (char.uuid == UUID.fromString(DRAGY_DATA_CHAR_UUID)) {
                    parseDragyData(char.value)
                }
            }
        }

        // API 33+ 版本的回调
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == UUID.fromString(DRAGY_DATA_CHAR_UUID)) {
                parseDragyData(value)
            }
        }
    }

    // 手机 GPS 位置监听器
    private val locationListener = LocationListener { location ->
        val rawData = LocationData(
            timestamp = if (location.time != 0L) location.time else System.currentTimeMillis(),
            latitude = location.latitude,
            longitude = location.longitude,
            speed = location.speed,
            accuracy = location.accuracy,
            altitude = location.altitude,
            bearing = location.bearing,
            source = Source.PHONE_GPS
        )
        emitLocationData(rawData)
    }

    // 初始化并连接到 Dragy Pro
    @SuppressLint("MissingPermission")
    fun connectToDragy() {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        
        // 注册 GNSS 状态监听
        if (!isGnssCallbackRegistered) {
            try {
                systemLocationManager.registerGnssStatusCallback(gnssCallback)
                isGnssCallbackRegistered = true
            } catch (e: Exception) {
                _connectionStatus.postValue(ConnectionStatus.ERROR)
            }
        }

        // 扫描并连接 Dragy Pro
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    if (device.name?.contains("Dragy", ignoreCase = true) == true) {
                        scanner.stopScan(this)
                        dragyDevice = device
                        dragyGatt = dragyDevice?.connectGatt(context, false, gattCallback)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _connectionStatus.postValue(ConnectionStatus.ERROR)
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        if (!isGnssCallbackRegistered) {
            try {
                systemLocationManager.registerGnssStatusCallback(gnssCallback)
                isGnssCallbackRegistered = true
            } catch (_: Exception) {
                _connectionStatus.postValue(ConnectionStatus.ERROR)
            }
        }

        if (!isDragyConnected) {
            fallbackToPhoneGPS()
        }
    }

    // 解析 Dragy Pro 数据
    private fun parseDragyData(data: ByteArray) {
        if (data.size < 40) { // 确保数据长度足够
            _connectionStatus.postValue(ConnectionStatus.ERROR)
            return
        }

        try {
            val buffer = ByteBuffer.wrap(data)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            // 解析数据包
            // 注意：这里的数据格式是示例，需要根据实际的 Dragy Pro 协议调整
            val timestamp = System.currentTimeMillis() // 使用系统时间作为时间戳
            val latitude = buffer.getDouble()
            val longitude = buffer.getDouble()
            val speed = buffer.getFloat()
            val accuracy = buffer.getFloat()
            val altitude = buffer.getDouble()
            val bearing = buffer.getFloat()

            // 数据有效性检查
            if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                val rawData = LocationData(
                    timestamp = timestamp,
                    latitude = latitude,
                    longitude = longitude,
                    speed = speed,
                    accuracy = accuracy,
                    altitude = altitude,
                    bearing = bearing,
                    source = Source.DRAGY_PRO
                )
                emitLocationData(rawData)
            } else {
                _connectionStatus.postValue(ConnectionStatus.ERROR)
            }
        } catch (e: Exception) {
            _connectionStatus.postValue(ConnectionStatus.ERROR)
        }
    }

    // 切换到手机 GPS
    @SuppressLint("MissingPermission")
    private fun fallbackToPhoneGPS() {
        if (!isDragyConnected) {
            systemLocationManager.requestLocationUpdates(
                AndroidLocationManager.GPS_PROVIDER,
                MIN_UPDATE_INTERVAL,
                MIN_DISTANCE,
                locationListener
            )
            _connectionStatus.postValue(ConnectionStatus.CONNECTED_GPS)
        }
    }

    // 停止位置更新
    fun stopLocationUpdates() {
        dragyGatt?.disconnect()
        systemLocationManager.removeUpdates(locationListener)
        try {
            if (isGnssCallbackRegistered) {
                systemLocationManager.unregisterGnssStatusCallback(gnssCallback)
                isGnssCallbackRegistered = false
            }
        } catch (e: Exception) {
            // 忽略可能的异常
        }
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
        locationTimestamps.clear()
        lastLocationData = null
    }

    // 释放资源
    fun release() {
        stopLocationUpdates()
        dragyGatt?.close()
    }

    private fun emitLocationData(rawData: LocationData) {
        val enhanced = enhanceSpeed(rawData)
        _locationData.postValue(enhanced)
        _locationUpdates.tryEmit(enhanced)
    }

    private fun enhanceSpeed(raw: LocationData): LocationData {
        val previous = lastLocationData
        val adjustedSpeed = if (raw.speed <= 0f && previous != null) {
            val timeDeltaSeconds = (raw.timestamp - previous.timestamp) / 1000f
            if (timeDeltaSeconds > 0f) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    previous.latitude,
                    previous.longitude,
                    raw.latitude,
                    raw.longitude,
                    results
                )
                val distanceMeters = results.firstOrNull() ?: 0f
                if (distanceMeters > 0.1f) distanceMeters / timeDeltaSeconds else 0f
            } else {
                raw.speed
            }
        } else {
            raw.speed
        }

        val finalSpeed = when {
            adjustedSpeed.isNaN() -> raw.speed
            adjustedSpeed.isInfinite() -> raw.speed
            adjustedSpeed < 0f -> raw.speed
            else -> adjustedSpeed
        }

        val enhanced = raw.copy(speed = finalSpeed)
        lastLocationData = enhanced
        return enhanced
    }
}