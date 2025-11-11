package com.example.racerstats.location

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager(private val fragment: Fragment) {

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private var permissionCallback: ((Boolean) -> Unit)? = null
    
    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkSystemSettings()
        } else {
            permissionCallback?.invoke(false)
        }
    }

    private val locationSettingsLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isLocationEnabled()) {
            checkBluetoothSettings()
        } else {
            permissionCallback?.invoke(false)
        }
    }

    private val bluetoothSettingsLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val isBluetoothEnabled = isBluetoothEnabled()
        permissionCallback?.invoke(isBluetoothEnabled)
    }

    fun checkAndRequestPermissions(callback: (Boolean) -> Unit) {
        permissionCallback = callback
        
        when {
            !hasRequiredPermissions() -> {
                requestPermissions()
            }
            !isLocationEnabled() -> {
                promptEnableLocation()
            }
            !isBluetoothEnabled() -> {
                promptEnableBluetooth()
            }
            else -> {
                callback(true)
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(fragment.requireContext(), it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (shouldShowRequestRationale()) {
            showPermissionRationaleDialog()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun shouldShowRequestRationale(): Boolean {
        return requiredPermissions.any {
            fragment.shouldShowRequestPermissionRationale(it)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("需要权限")
            .setMessage("RaceStats 需要位置和蓝牙权限来获取赛车数据。请在设置中授予这些权限。")
            .setPositiveButton("确定") { _, _ ->
                permissionLauncher.launch(requiredPermissions)
            }
            .setNegativeButton("取消") { _, _ ->
                permissionCallback?.invoke(false)
            }
            .show()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = fragment.requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun promptEnableLocation() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("启用位置服务")
            .setMessage("RaceStats 需要启用位置服务来获取位置数据。是否现在启用？")
            .setPositiveButton("设置") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                locationSettingsLauncher.launch(intent)
            }
            .setNegativeButton("取消") { _, _ ->
                permissionCallback?.invoke(false)
            }
            .show()
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = fragment.requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.isEnabled == true
    }

    private fun promptEnableBluetooth() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("启用蓝牙")
            .setMessage("RaceStats 需要启用蓝牙来连接 Dragy Pro。是否现在启用？")
            .setPositiveButton("设置") { _, _ ->
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothSettingsLauncher.launch(intent)
            }
            .setNegativeButton("取消") { _, _ ->
                permissionCallback?.invoke(false)
            }
            .show()
    }

    private fun checkSystemSettings() {
        if (!isLocationEnabled()) {
            promptEnableLocation()
        } else if (!isBluetoothEnabled()) {
            promptEnableBluetooth()
        } else {
            permissionCallback?.invoke(true)
        }
    }
    
    private fun checkBluetoothSettings() {
        if (!isBluetoothEnabled()) {
            promptEnableBluetooth()
        } else {
            permissionCallback?.invoke(true)
        }
    }
}