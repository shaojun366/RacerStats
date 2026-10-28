package com.example.racerstats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val session: SessionViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pager = findViewById<ViewPager2>(R.id.viewPager)
        val navigation = findViewById<BottomNavigationView>(R.id.bottomNav)
        pager.adapter = PagerAdapter(this)
        pager.isUserInputEnabled = false
        val destinations = listOf(R.id.nav_live, R.id.nav_track, R.id.nav_review)
        navigation.setOnItemSelectedListener { item ->
            val position = destinations.indexOf(item.itemId)
            if (position < 0) false else {
                pager.setCurrentItem(position, false)
                true
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                navigation.menu.findItem(destinations[position]).isChecked = true
            }
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val host = supportFragmentManager.fragments.filterIsInstance<NavHostFragment>().firstOrNull()
                if (pager.currentItem == 1 && host?.navController?.popBackStack() == true) return
                if (pager.currentItem != 0) pager.setCurrentItem(0, false) else finish()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        session.onForeground()
    }

    override fun onStop() {
        if (!isChangingConfigurations) session.onBackground()
        super.onStop()
    }

    // FragmentStateAdapter restores its own instances after configuration changes.
    private class PagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> LiveFragment()
            1 -> NavHostFragment.create(R.navigation.nav_graph)
            else -> ReviewFragment()
        }
    }
}
