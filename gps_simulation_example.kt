// 在 LiveFragment 或 TrackFragment 中添加测试方法
private fun simulateGPSMovement() {
    val testPoints = listOf(
        Pair(39.9042, 116.4074), // 起点
        Pair(39.9052, 116.4084), // 点1
        Pair(39.9062, 116.4094), // 点2
        Pair(39.9072, 116.4104), // 点3
        Pair(39.9082, 116.4114), // 点4
        Pair(39.9092, 116.4124), // 点5
        Pair(39.9042, 116.4074)  // 回到起点
    )
    
    var currentIndex = 0
    val handler = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            if (currentIndex < testPoints.size) {
                val point = testPoints[currentIndex]
                
                // 创建模拟的 LocationData
                val mockLocationData = LocationData(
                    timestamp = System.currentTimeMillis(),
                    latitude = point.first,
                    longitude = point.second,
                    speed = (50..120).random().toFloat() / 3.6f, // 随机速度 50-120 km/h 转 m/s
                    accuracy = 3.0f,
                    altitude = 100.0,
                    bearing = 0f,
                    source = LocationManager.Source.PHONE_GPS
                )
                
                // 如果在录制中，添加到 TrackRecorder
                // trackRecorder.addPoint(point.first, point.second, 100.0)
                
                currentIndex++
                handler.postDelayed(this, 2000) // 每2秒一个点
            }
        }
    }
    
    handler.post(runnable)
}

// 添加测试按钮（临时用于开发调试）
private fun addTestButton() {
    val testButton = Button(requireContext())
    testButton.text = "Simulate GPS"
    testButton.setOnClickListener {
        simulateGPSMovement()
    }
    // 将按钮添加到布局中
}