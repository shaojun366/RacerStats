package com.example.racerstats.location

data class GnssData(
    val satsInView: Int,    // 可见卫星数量
    val satsUsed: Int,      // 正在使用的卫星数量
    val accuracy: Float,     // 精度（米）
    val updateRate: Float    // 更新频率（Hz）
)