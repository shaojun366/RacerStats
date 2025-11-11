# RacerStats 🏁

[![Version](https://img.shields.io/badge/version-v1.0.1-blue.svg)](https://github.com/shaojun366/RacerStats/releases)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Kotlin-purple.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-orange.svg)](LICENSE)

> **专业的 Android 赛道计时与分析应用**  
> 实时圈速预测、Delta 对比、轨迹录制与匹配，助力车手提升赛道表现

## 🌟 功能特色

### 🎯 **实时计时系统**
- **精准圈速计算**: 基于 GPS 的高精度计时系统
- **Delta 实时对比**: 与最佳圈速的即时差值显示
- **圈速预测**: 智能算法预测当前圈完成时间
- **HUD 显示**: 350 km/h 速度表，实时数据可视化

### 🗺️ **赛道管理**
- **GPS 轨迹录制**: 高频采样记录完整赛道数据
- **起终点线设置**: 灵活配置赛道起点和终点线
- **轨迹匹配算法**: 智能识别相似路线和圈速计算
- **多场景适配**: 支持专业赛道、街道、公路等不同环境

### ⚙️ **宽容度调节**
- **精密赛道模式** (25m): 适用于 F1 赛道、专业卡丁车场
- **街道赛道模式** (40m): 城市街道赛道、临时封闭路段
- **一般道路模式** (50m): 乡村公路、山路驾驶
- **高速公路模式** (80m): 高速公路、宽阔直道场景

### 📊 **数据分析**
- **历史圈速记录**: 完整的圈速数据存储和回顾
- **性能趋势分析**: 圈速改进轨迹可视化
- **最佳圈速追踪**: 个人最佳成绩记录
- **数据导出**: 支持数据分享和进一步分析

## 🏗️ 技术架构

### **核心技术栈**
- **语言**: Kotlin 100%
- **架构**: MVVM + Repository Pattern
- **依赖注入**: Hilt (Dagger)
- **数据库**: Room (SQLite)
- **地图服务**: Google Maps SDK
- **状态管理**: StateFlow + LiveData

### **主要模块**
```
📦 RacerStats
├── 📱 Live Module        # 实时计时和 HUD 显示
├── 🎯 Track Module       # 赛道录制和轨迹管理  
├── 📊 Review Module      # 历史数据分析和回顾
├── 🔧 Core Components    # 共享组件和工具类
└── 📚 Documentation     # 开发文档和使用说明
```

## 🚀 快速开始

### **环境要求**
- Android Studio Hedgehog | 2023.1.1+
- Kotlin 1.9.0+
- Android SDK 34+
- 最低支持 Android 7.0 (API 24)

### **克隆项目**
```bash
git clone https://github.com/shaojun366/RacerStats.git
cd RacerStats
```

### **配置 Google Maps**
1. 获取 Google Maps API Key
2. 在 `app/src/main/res/values/strings.xml` 中配置:
```xml
<string name="google_maps_key">YOUR_API_KEY_HERE</string>
```

### **构建运行**
```bash
# 构建调试版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 📱 应用截图

| 实时计时界面 | 赛道录制界面 | 数据分析界面 |
|-------------|-------------|-------------|
| ![Live](docs/screenshots/live.png) | ![Track](docs/screenshots/track.png) | ![Review](docs/screenshots/review.png) |

## 📖 使用指南

### **开始计时**
1. 打开应用，切换到 **Live** 标签页
2. 点击 **"开始"** 按钮启动 GPS 定位
3. 进入赛道，应用将自动识别圈速

### **录制赛道**
1. 切换到 **Track** 标签页
2. 选择合适的**宽容度设置**
3. 点击 **"开始录制"** 开始采集轨迹
4. 设置**起终点线**完成赛道配置

### **宽容度配置**
选择适合您驾驶环境的匹配精度:
- 🏎️ **精密赛道**: 专业赛车场，要求极高精度
- 🏙️ **街道赛道**: 城市街道，平衡精度与容错
- 🛣️ **一般道路**: 乡村公路，适中容错范围  
- 🛤️ **高速公路**: 高速环境，最大容错范围

## 🔧 开发指南

### **项目结构**
```
app/src/main/
├── java/com/example/racerstats/
│   ├── live/              # 实时计时模块
│   ├── track/             # 赛道管理模块
│   ├── review/            # 数据回顾模块
│   ├── location/          # GPS 定位服务
│   └── MainActivity.kt    # 主入口
├── res/
│   ├── layout/           # UI 布局文件
│   ├── values/           # 资源配置
│   └── drawable/         # 图标和图片
└── AndroidManifest.xml   # 应用配置
```

### **核心算法**
- **轨迹匹配**: Douglas-Peucker 简化 + Hausdorff 距离
- **圈速计算**: 基于起终点线的精确计时
- **Delta 算法**: 实时位置与最佳圈对比
- **速度平滑**: 0.7/0.3 权重滤波，消除 GPS 抖动

### **数据模型**
```kotlin
// 核心数据结构
data class LapData(val lapTime: Long, val timestamp: Long)
data class GpsPoint(val latitude: Double, val longitude: Double)
data class TrackDetails(val track: Track, val points: List<GpsPoint>)
```

## 📋 版本历程

### **v1.0.1** (2025-11-12) 🎉
- ✨ **新增**: 赛道宽容度调节功能
- 🎨 **优化**: 录制界面 UI 布局
- 📚 **完善**: 功能文档和使用说明
- 🔧 **改进**: StateFlow 状态管理

### **v1.0.0** (2025-11-07)
- 🎉 **发布**: 首个稳定版本
- 🏁 **实现**: 核心计时功能
- 🗺️ **支持**: 基础赛道录制
- 📊 **提供**: HUD 实时显示

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### **开发流程**
1. Fork 项目到您的账户
2. 创建功能分支: `git checkout -b feature/AmazingFeature`
3. 提交更改: `git commit -m 'Add AmazingFeature'`
4. 推送分支: `git push origin feature/AmazingFeature`
5. 提交 Pull Request

### **代码规范**
- 遵循 Kotlin 官方代码风格
- 使用有意义的变量和函数命名
- 添加必要的注释和文档
- 确保所有测试通过

## 🐛 问题反馈

遇到问题？请通过以下方式联系:
- 📧 **提交 Issue**: [GitHub Issues](https://github.com/shaojun366/RacerStats/issues)
- 💬 **功能建议**: 欢迎在 Issues 中提出新功能想法
- 🔧 **Bug 报告**: 请提供详细的复现步骤

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源协议

## 🙏 致谢

- **Google Maps**: 提供精确的地图服务支持
- **Android Jetpack**: 现代化的 Android 开发组件
- **Kotlin**: 简洁高效的编程语言
- **Community**: 感谢所有贡献者和用户的支持

---

<p align="center">
  <strong>🏁 让每一圈都更快！</strong><br>
  <sub>RacerStats - 专业的赛道计时伙伴</sub>
</p>

<p align="center">
  <a href="https://github.com/shaojun366/RacerStats">⭐ Star</a> |
  <a href="https://github.com/shaojun366/RacerStats/issues">🐛 Report Bug</a> |
  <a href="https://github.com/shaojun366/RacerStats/issues">💡 Request Feature</a>
</p>