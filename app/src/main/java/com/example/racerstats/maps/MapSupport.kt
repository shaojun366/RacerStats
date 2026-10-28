package com.example.racerstats.maps

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.racerstats.R
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Maps is optional: Android GPS and saved course timing do not use this key. */
object MapSupport {
    @Suppress("DEPRECATION")
    fun isConfigured(context: Context): Boolean {
        // Read the manifest placeholder injected by Gradle; never log or display its value.
        val key = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.GET_META_DATA
        ).metaData?.getString("com.google.android.geo.API_KEY").orEmpty().trim()
        return key.isNotEmpty() && key != "your_key_here" && !key.startsWith("YOUR_")
    }

    fun bind(
        fragment: Fragment,
        containerId: Int,
        status: TextView,
        preview: TrackRouteView,
        callback: OnMapReadyCallback
    ): Boolean {
        val configured = isConfigured(fragment.requireContext())
        fragment.requireView().findViewById<View>(containerId).visibility =
            if (configured) View.VISIBLE else View.GONE
        preview.visibility = if (configured) View.GONE else View.VISIBLE
        status.visibility = if (configured) View.GONE else View.VISIBLE
        status.setText(R.string.maps_unconfigured)
        status.setOnClickListener {
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.maps_setup_title)
                .setMessage(R.string.maps_setup_message)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.maps_setup_docs) { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://github.com/shaojun366/RacerStats/blob/main/docs/maps-gps-integration.md"
                    ))
                    try {
                        fragment.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        android.widget.Toast.makeText(fragment.requireContext(),
                            R.string.maps_no_browser, android.widget.Toast.LENGTH_LONG).show()
                    }
                }.show()
        }
        if (configured) {
            val manager = fragment.childFragmentManager
            val map = manager.findFragmentById(containerId) as? SupportMapFragment
                ?: SupportMapFragment.newInstance().also {
                    manager.beginTransaction().replace(containerId, it).commitNow()
                }
            val boundView = fragment.requireView()
            map.getMapAsync { googleMap ->
                if (fragment.view === boundView) callback.onMapReady(googleMap)
            }
        }
        return configured
    }
}
