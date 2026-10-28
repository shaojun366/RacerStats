# RacerStats

原生 Android 赛道计时应用：用手机 GPS 记录路线，按起终点自动记圈，在实时仪表盘和赛道历史中查看成绩。

Kotlin · XML / ViewBinding · ViewModel · Room · Hilt · Google Maps

[Maps / GPS 接入](docs/maps-gps-integration.md) · [完整界面预览](docs/preview.md) · [自动计时规则](docs/automatic-timing.md) · [构建检查](https://github.com/shaojun366/RacerStats/actions/workflows/android.yml)

## 界面预览

| 实时计时 | 赛道库 | 圈速回顾 |
| --- | --- | --- |
| <img src="docs/screenshots/automatic-live.png" alt="自动计时与实时仪表盘" width="260"> | <img src="docs/screenshots/track-library.png" alt="赛道列表与录制入口" width="260"> | <img src="docs/screenshots/automatic-review.png" alt="按赛道筛选的圈速历史" width="260"> |

截图来自 Android 模拟器实际运行，赛道与成绩均为合成测试数据。未配置 Maps Key 的页面使用本地轨迹示意；更多页面见[项目预览](docs/preview.md)。

## 已实现

- **自动与手动计时**：选择已保存赛道后自动检测过线；闭合赛道连续记圈，独立起终点按次记录。也可使用手动开始、暂停、过线与结束。
- **实时仪表盘**：显示当前圈、Delta、预测用时、GPS 速度、最高速度、实际更新频率、卫星与定位精度。没有有效参考圈时显示占位符。
- **赛道管理**：录制、暂停与继续 GPS 轨迹，在地图上设置起终点，保存、查看、编辑与删除赛道；未配置地图时仍可查看本地轨迹示意。
- **成绩回顾**：保存最近 500 条成绩，按赛道或手动模式筛选；各赛道独立计算最佳成绩，筛选后的清空仅影响当前范围。
- **统一深色 UI**：清晰的计时数字、卡片信息层次、赛道空状态和地图配置提示，支持窄屏滚动与固定操作区。

## 快速构建

需要 **JDK 17**、**Android SDK 34** 和 **Build Tools 34**。最低运行版本为 **Android 8.0 / API 26**。

```sh
git clone https://github.com/shaojun366/RacerStats.git
cd RacerStats
./gradlew assembleDebug
```

Windows 使用 `gradlew.bat`。通过 `JAVA_HOME` 或 Android Studio 的 Gradle JDK 设置选择 JDK 17，通过 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir` 指定 SDK。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。也可在成功的 [GitHub Actions](https://github.com/shaojun366/RacerStats/actions/workflows/android.yml) 运行中下载构建产物；CI 默认不注入地图密钥。

### 配置地图

在 Google Cloud 中启用 **Maps SDK for Android** 并配置受限密钥，随后在个人的 `~/.gradle/gradle.properties` 添加：

```properties
MAPS_API_KEY=your_key_here
```

也支持环境变量 `MAPS_API_KEY`；Gradle 属性优先。配置后重新构建并安装应用。真实密钥不要写入仓库内的 `gradle.properties`、资源文件或 README。

**手机 GPS 不需要 Maps Key。** 缺少密钥仍能构建、查看本地轨迹示意、修改赛道名称、手动计时和使用已有赛道自动计时；采集轨迹也可进行，但设置起终点并保存新赛道需要完成地图接入。Google Cloud、包名 / SHA-1 限制、配置优先级及排错步骤见 [Maps / GPS 接入说明](docs/maps-gps-integration.md)。

## 使用流程

1. 在“赛道”页进入录制，授予精确位置权限并开启系统定位。开始录制后行进至少 100 米。
2. 在地图上点击两点设置横跨路线的起点线；开放路线可再设置独立终点线。保存赛道，保存失败时可继续修改或录制。
3. 从赛道详情进入计时，或在“实时”页选择赛道，点击“启用自动计时”。首次通过起点后开始，以首次通过方向为准。
4. 闭合赛道再次同向通过起点完成一圈；独立终点路线到达终点完成一次，再等待下一次起点。
5. 在“回顾”页按赛道查看成绩与最佳圈。使用手动模式时，点击“手动过线”记录未关联赛道的成绩。

录制页的“匹配容差”用于保存时识别相似赛道：命中已有路线会更新该赛道，而非创建副本；它不控制 GPS 精度或自动过线判定。

旋转屏幕保留当前会话。应用进入后台会暂停计时和录制；自动计时暂停或定位质量不足会放弃未完成圈，继续后需重新通过起点，手动计时则保留暂停前用时。已完成成绩保留，进程结束后不恢复未完成计时或未保存轨迹。

## 开发与验证

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

设备测试需连接 Android 设备或启动模拟器。测试使用合成轨迹，覆盖过线几何与方向、抖动和静止漂移、GPS 断点、历史筛选、旧记录兼容、赛道修改后重新加载，以及计时、数据库与页面恢复。

GitHub Actions 执行单元测试、Lint、APK 构建和 Git 全历史密钥扫描。

| 代码入口 | 职责 |
| --- | --- |
| [`SessionViewModel.kt`](app/src/main/java/com/example/racerstats/SessionViewModel.kt) | 会话、GPS 样本处理与圈速历史 |
| [`timing/`](app/src/main/java/com/example/racerstats/timing) | 单调时钟、过线判定与参考圈 |
| [`location/`](app/src/main/java/com/example/racerstats/location) | 系统 GPS、GNSS 状态与权限 |
| [`maps/`](app/src/main/java/com/example/racerstats/maps) | 地图配置提示与无 Key 时的本地轨迹示意 |
| [`track/`](app/src/main/java/com/example/racerstats/track) | 录制、Room 存储与赛道页面 |

## 当前边界

- 自动计时已通过合成轨迹和模拟器验证；真实精度、丢星表现和功耗仍需真机 / 实车测试。毫秒格式与插值结果不代表手机具备毫秒级定位精度。
- 手机 GPS 流程不依赖蓝牙。Dragy 解析保留为协议示例，尚未完成真实设备协议验证与接入。
- 当前没有后台定位服务、圈速趋势图或数据导出，也没有自建云端同步服务。
- 历史公开密钥和构建产物已做仓库清理；曾公开的密钥仍需在服务商控制台撤销或轮换，见[安全清理记录](docs/security-cleanup.md)。

更多开发说明见[自动计时说明](docs/automatic-timing.md)和[优化记录](docs/optimization-notes.md)。

## License

[MIT](LICENSE)
