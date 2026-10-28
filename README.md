# RacerStats 🏁

原生 Android 赛道计时与 GPS 轨迹记录应用，使用 Kotlin、ViewModel、Room、Hilt 和 Google Maps。

## 当前功能

- **手动圈速计时**：开始、暂停、继续、手动过线、结束；使用单调时钟，暂停时间不会计入圈速。
- **自动过线计时**：选择已保存赛道，首次通过起点后开始；闭合赛道连续记圈，独立起终点按次记录。支持过线时刻插值、方向检查、抖动与定位断点处理。
- **实时仪表盘**：GPS 速度、最高速度、实际定位更新频率、卫星和精度信息。
- **参考圈对比**：完成有有效 GPS 轨迹的首圈后，按距离插值计算 Delta，按当前速度预测剩余时间。无参考数据时显示占位符。
- **圈速回顾**：保留最近 500 条成绩及赛道信息，支持按赛道筛选和清理；旧版手动记录继续可用，不混比不同赛道的最佳成绩。
- **赛道管理**：录制轨迹、暂停/继续、在地图上设置起终点线、保存、查看、重命名和删除赛道。
- **深色界面**：固定计时操作区、可滚动的仪表盘、窄屏自适应数字和清晰的空状态。

## 构建

需要 **JDK 17**、Android SDK 34 和 Android SDK Build Tools 34。最低运行版本为 **Android 8.0 / API 26**。

```sh
git clone https://github.com/shaojun366/RacerStats.git
cd RacerStats
./gradlew assembleDebug
```

Windows 使用 `gradlew.bat`。通过 `JAVA_HOME` 或 Android Studio 的 Gradle JDK 设置选择 JDK 17；通过 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir` 指定 SDK。项目不包含个人电脑的 JDK/SDK 路径。

### 地图配置

在 Google Cloud 项目中启用 Maps SDK for Android，并将有效的 API Key 放入个人的 `~/.gradle/gradle.properties`：

```properties
MAPS_API_KEY=your_key_here
```

也可以设置同名环境变量 `MAPS_API_KEY`。密钥由 Gradle 写入 AndroidManifest 的占位符，无需修改受版本控制的资源文件。未配置密钥时仍可构建并使用手机 GPS 计时；已保存赛道的自动计时不依赖底图加载，录制和编辑地图底图需要有效密钥。

## 使用

1. 在“赛道”页点击 `+` 录制轨迹，在地图上点击两点设置起点线；开放路线可再设置独立终点线。轨迹至少 100 米，保存失败仍可继续录制或修改后重试。
2. 在“实时”页点击顶部选择按钮，选择赛道后点击“启用自动计时”；也可从赛道详情进入。授予精确位置权限并开启 GPS，手机定位无需蓝牙。
3. 首次通过起点时开始计时，以首次通过方向为准。闭合赛道再次同向通过起点记圈；独立终点只在通过终点时完成一次，再等待起点。
4. 选择“手动计时”后，按“开始计时”和“手动过线”记录不关联赛道的成绩。
5. 在“回顾”页按赛道查看成绩和最佳圈，差值仍以各次计时的最佳圈为基准。筛选后清空只删除当前筛选的记录。

屏幕旋转保留当前计时；应用进入后台会暂停计时和录制。自动计时暂停或 GPS 质量不足会放弃未完成圈，继续后需重新通过起点；手动计时保留暂停前的用时。已完成成绩保留，当前没有后台定位服务，进程终止后不会恢复未完成计时或未保存轨迹。

## 验证

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

回归测试覆盖自动过线几何与方向、连续圈/独立终点、抖动与静止漂移、定位断点、圈速筛选、旧记录兼容、赛道修改后重新加载，以及原有计时、保存、数据库与页面恢复。GitHub Actions 执行单元测试、Lint、APK 构建和全历史密钥扫描。

## 当前边界

- 自动计时已通过合成轨迹和模拟器验证，真实精度、丢星、设备功耗仍需真机/实车测试。插值结果不代表手机具有毫秒级定位精度。
- 原有 Dragy 数据包解析仍是协议示例，自动扫描已从手机 GPS 流程中移除；外接设备需要协议确认和真机验证。
- 圈速趋势图和数据导出尚未实现；GPS 精度与更新频率取决于设备和接收条件。

本次修改及验证说明见 [优化记录](docs/optimization-notes.md)。
自动计时规则与后续开发说明见 [自动计时说明](docs/automatic-timing.md)。

## 界面预览

| 实时计时 | 圈速回顾 |
| --- | --- |
| ![自动计时](docs/screenshots/automatic-live.png) | ![赛道圈速回顾](docs/screenshots/automatic-review.png) |

截图来自 Android 模拟器，回顾页显示的是集成测试产生的计时记录。

## License

[MIT](LICENSE)
