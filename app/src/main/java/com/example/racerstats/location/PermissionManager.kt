package com.example.racerstats.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/** Phone GPS works independently of optional Bluetooth hardware. Construct before onCreate. */
class PermissionManager(private val fragment: Fragment) {
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private var pending: ((Boolean) -> Unit)? = null
    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasPermission()) checkLocationSettings() else finish(false)
    }
    private val settingsLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { finish(hasPermission() && isLocationEnabled()) }

    fun checkAndRequestPermissions(callback: (Boolean) -> Unit) {
        if (pending != null) return
        pending = callback
        if (hasPermission()) checkLocationSettings() else permissionLauncher.launch(permissions)
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(
        fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = fragment.requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun checkLocationSettings() {
        if (isLocationEnabled()) {
            finish(true)
            return
        }
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("启用 GPS 定位")
            .setMessage("赛道计时需要精确位置，请开启系统定位服务。")
            .setPositiveButton("前往设置") { _, _ ->
                settingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("取消") { _, _ -> finish(false) }
            .setOnCancelListener { finish(false) }
            .show()
    }

    private fun finish(granted: Boolean) {
        val callback = pending
        pending = null
        callback?.invoke(granted)
    }
}
