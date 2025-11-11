package com.example.racerstats

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.racerstats.track.TrackFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnStart: com.google.android.material.button.MaterialButton
    private lateinit var btnLap: com.google.android.material.button.MaterialButton
    private lateinit var btnStop: com.google.android.material.button.MaterialButton
    
    private val liveFragment = LiveFragment()
    private val fragments = listOf(
        liveFragment,
        TrackFragment(),
        ReviewFragment()
    )

    private lateinit var hudCard: androidx.cardview.widget.CardView
    private lateinit var actionButtons: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)

            // 初始化 ViewPager2
            viewPager = findViewById(R.id.viewPager)
            viewPager.adapter = PagerAdapter(this)
            viewPager.isUserInputEnabled = false  // 禁用滑动切换
            
            // 初始化 HUD 和动作按钮
            hudCard = findViewById(R.id.hudCard)
            actionButtons = findViewById(R.id.actionButtons)
            btnStart = findViewById(R.id.btnStart)
            btnLap = findViewById(R.id.btnLap)
            btnStop = findViewById(R.id.btnStop)
            
            // 确保初始状态正确
            hudCard.visibility = android.view.View.VISIBLE
            actionButtons.visibility = android.view.View.VISIBLE
            
            // 将按钮引用传递给 LiveFragment
            liveFragment.setButtons(btnStart, btnLap, btnStop)
            
            // 在视图完全加载后设置动画相关属性
            viewPager.post {
                try {
                    viewPager.getChildAt(0)?.let { view ->
                        view.overScrollMode = android.view.View.OVER_SCROLL_NEVER
                        if (view is android.widget.FrameLayout) {
                            view.layoutTransition = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error setting ViewPager2 properties: ${e.message}", e)
                }
            }

            // 初始化底部导航
            bottomNav = findViewById(R.id.bottomNav)
            bottomNav.setOnItemSelectedListener { item ->
                // 先立即更新UI控件的可见性，避免动画过渡时的视觉残留
                when (item.itemId) {
                    R.id.nav_live -> {
                        hudCard.visibility = android.view.View.VISIBLE
                        actionButtons.visibility = android.view.View.VISIBLE
                        viewPager.setCurrentItem(0, false) // 禁用切换动画
                    }
                    R.id.nav_track -> {
                        hudCard.visibility = android.view.View.GONE
                        actionButtons.visibility = android.view.View.GONE
                        viewPager.setCurrentItem(1, false) // 禁用切换动画
                    }
                    R.id.nav_review -> {
                        hudCard.visibility = android.view.View.GONE
                        actionButtons.visibility = android.view.View.GONE
                        viewPager.setCurrentItem(2, false) // 禁用切换动画
                    }
                }
                true
            }

            // 监听 ViewPager 页面切换
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    bottomNav.menu.getItem(position).isChecked = true
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        // 在滚动完全停止时更新UI控件的可见性
                        val visibility = if (viewPager.currentItem == 0) {
                            android.view.View.VISIBLE
                        } else {
                            android.view.View.GONE
                        }
                        hudCard.visibility = visibility
                        actionButtons.visibility = visibility
                    } else {
                        // 在滚动过程中保持隐藏状态，避免出现残影
                        hudCard.visibility = android.view.View.GONE
                        actionButtons.visibility = android.view.View.GONE
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "应用初始化错误: ${e.message}", Toast.LENGTH_LONG).show()
            // 如果是严重错误，可以结束应用
            finishAffinity()
        }
    }

    private inner class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = fragments.size
        override fun createFragment(position: Int) = fragments[position]
    }
}