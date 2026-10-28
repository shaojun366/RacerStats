# Maps / GPS 接入说明

本文对应仓库现有实现。Maps 用于显示底图、轨迹和起终点；GPS 用于采集位置并驱动录制与计时，两者分开配置。

| 能力 | 当前实现 | 是否需要 Maps Key |
| --- | --- | --- |
| 地图底图、在地图上设置起终点 | Google Maps SDK for Android / `SupportMapFragment` | 需要 |
| 本地轨迹和起终点示意 | 无 Key 时由 `TrackRouteView` 绘制 | 不需要 |
| 手机位置、速度与卫星状态 | Android `LocationManager.GPS_PROVIDER` / `GnssStatus` | 不需要 |
| 手动计时、已有赛道自动计时 | 本地 `SessionViewModel` 与 `timing/` | 不需要 |
| Dragy 外接接收器 | 保留的 BLE 协议示例，尚未验证 | 与 Maps Key 无关 |

当前没有需要填写 URL、Token 的“GPS 云端 API”。Google Maps 的定位蓝点也不负责驱动本项目的自动计时。

## 1. 接入 Google Maps

### 在 Google Cloud 配置

1. 创建或选择 Google Cloud 项目，关联有效结算账号，并在 API Library 启用 **Maps SDK for Android**。
2. 创建 API Key，API 限制仅允许 **Maps SDK for Android**。在密钥限制中勾选服务不等于已经启用服务。
3. 应用限制选择 **Android apps**，添加本项目包名 `com.example.racerstats` 和实际安装 APK 的签名证书 SHA-1。

配置要求与控制台入口以 [Maps SDK for Android 官方接入文档](https://developers.google.com/maps/documentation/android-sdk/get-api-key)为准。此项目没有使用 Maps JavaScript API、Places 或 Routes，无需为现有功能额外启用它们。

在项目根目录查看签名：

```sh
./gradlew signingReport
```

Windows 使用 `gradlew.bat signingReport`。本地调试使用 debug 证书，发布构建使用 release 证书；使用 Play App Signing 分发时，应登记 Play Console 中应用签名证书的 SHA-1。上传证书与分发应用的签名证书可能不同，参见 [Google 签名认证说明](https://developers.google.com/android/guides/client-auth)。项目目前没有内置 release 签名配置。

开发与发布可使用不同密钥，分别限制对应的签名。APK 内会包含客户端 Maps Key，因此排除源代码中的密钥不能替代服务端的应用与 API 限制，参见 [Maps API 安全建议](https://developers.google.com/maps/api-security-best-practices)。

### 在本地注入 Key

推荐编辑个人 Gradle 配置文件 `~/.gradle/gradle.properties`，Windows 对应 `%USERPROFILE%\.gradle\gradle.properties`。如果文件已有其他设置，只追加这一项：

```properties
# 仅在个人配置文件中替换占位符；不要提交真实值。
MAPS_API_KEY=your_key_here
```

也支持由本地环境或 CI Secret 注入环境变量 `MAPS_API_KEY`。不要把真实值直接写进公共工作流、脚本或命令示例。

当前读取顺序在 [`app/build.gradle.kts`](../app/build.gradle.kts) 中：

```kotlin
manifestPlaceholders["MAPS_API_KEY"] = providers.gradleProperty("MAPS_API_KEY")
    .orElse(providers.environmentVariable("MAPS_API_KEY")).getOrElse("")
```

随后由 [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml) 注入 SDK：

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

注意配置范围：

- **Gradle 属性优先于环境变量**，包括值为空的同名属性。切换环境变量时，先移除旧的同名 Gradle 属性。
- `local.properties` 在本项目中仅用于 SDK 路径等 Android 本地配置，**不会读取其中的 `MAPS_API_KEY`**。
- `.env`、`secrets.properties` 和 `google-services.json` 也不会被当前构建脚本用于 Maps 配置。
- Key 在构建时注入；修改配置后需要重新构建、安装 APK，单纯重启旧 APK 不会更新 Key。
- 未配置 Key 可完成构建；CI 默认采用此模式，不验证真实地图授权。

[`MapSupport.kt`](../app/src/main/java/com/example/racerstats/maps/MapSupport.kt) 检测到未配置 Key 时不会初始化 Google Maps，而是显示接入提示与本地北向轨迹示意。详情和编辑页仍可查看已保存路线，编辑页仍能改名；录制页可采集 GPS，但设置起终点线需要地图，因此新赛道仍需接好地图才能完整保存。本地示意没有道路底图，不提供点击设线功能。

这里检查的是 Key 是否填写，不是授权是否有效：填写错误、受限规则不匹配或网络不可用时，仍需按下文排错。

Maps 页面需要设备上可用的 Google Play services，以及能够访问地图服务的网络；模拟器选择带 Google APIs 或 Google Play 的镜像，参见 [Maps Android 项目配置](https://developers.google.com/maps/documentation/android-sdk/config)。本项目最低 Android 版本仍以自身的 `minSdk = 26` 为准。

## 2. 接入手机 GPS

### 权限与系统设置

Manifest 已声明 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`。[`PermissionManager.kt`](../app/src/main/java/com/example/racerstats/location/PermissionManager.kt) 负责请求前台精确位置，并在系统 GPS 关闭时引导到定位设置。Android 12 及以上即使声明精确权限，用户仍可选择仅提供大致位置；赛道功能需要用户授予精确位置，参见 [Android 位置权限说明](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)。

手机 GPS 不需要蓝牙权限或 Google Cloud Key。底图无法加载时，不应通过修改 GPS API 配置来排查；先分别检查地图授权和设备定位。

### 数据链路与单位

```text
Android GPS_PROVIDER
    → location/LocationManager.LocationData
    ├─ LiveData → SessionViewModel → 自动过线 / 实时仪表盘
    └─ SharedFlow → TrackViewModel → TrackRecorder → Room 赛道
```

入口为 [`location/LocationManager.kt`](../app/src/main/java/com/example/racerstats/location/LocationManager.kt)。实际调用系统的 `requestLocationUpdates(GPS_PROVIDER, …)`，不是 `FusedLocationProviderClient`。`GPS_PROVIDER` 使用 GNSS 卫星，响应速度和定位精度会受信号条件影响，参见 [Android GPS_PROVIDER](https://developer.android.com/reference/android/location/LocationManager#GPS_PROVIDER)。

| 字段 | 约定 |
| --- | --- |
| `timestamp` | 单调时间，毫秒；手机来源为 `elapsedRealtimeNanos / 1_000_000` |
| `latitude` / `longitude` | 纬度 / 经度，角度；保持与当前 GPS 和地图一致的坐标基准 |
| `speed` | 米 / 秒，UI 显示时转换为 km/h |
| `accuracy` | 水平精度估计，米；未知精度不能伪装成高质量定位 |
| `altitude` | 米 |
| `bearing` | 行进方向角，度 |
| `source` | 当前为 `PHONE_GPS`；外接来源尚未投入使用 |

使用单调时间是为了避免系统日期调整影响排序和圈速；它只能在同一设备、同一次启动周期内比较。Android 的 `accuracy` 是水平位置的不确定性估计，并非保证误差，见 [Location API](https://developer.android.com/reference/android/location/Location)。成绩的日期另外使用墙上时钟，不能拿它替换 GPS 计时时间戳。

代码申请的最小间隔为 `10 ms`，最小距离为 `0.1 m`，**这不代表设备能输出 100 Hz GPS**。仪表盘显示最近有效位置样本的实际到达频率；卫星状态回调不会被当成位置样本。保留设备报告的有效零速度，仅在速度缺失且位移足够可信时估算速度。

自动计时还会检查样本新鲜度、精度、速度、方向和断点。当前有效过线精度要求为 `0 < accuracy ≤ 20 m`；超过 3 秒的定位断点会中断未完成圈。完整阈值与边界见[自动计时说明](automatic-timing.md)。

录制页的**匹配容差**传给 `TrackManager.saveTrack → findMatchingTrack → TrackMatcher.calculateSimilarity`，用于保存时识别相似路线。达到相似度要求时，会更新已有赛道的名称、路线和起终点，保留其赛道 ID；否则创建新赛道。它不是 GPS 精度设置，也不是自动计时的过线半径。

### 生命周期与数据保存

应用进入后台暂停计时和录制，并停止其定位更新。当前没有前台定位服务或后台定位权限流程。自动计时恢复后等待重新通过起点；手动计时保留暂停前用时。

赛道存储在本地 Room 数据库，最近 500 条圈速保存在本地 SharedPreferences。项目没有自建成绩上传服务；Android 系统备份与设备迁移由系统设置和应用备份规则控制，不能据此保证数据永不离开设备。

## 3. 后续替换地图或接外置 GPS

以下是扩展接入点，尚未实现对应能力：

- **其他地图 SDK**：录制、编辑、详情页目前直接使用 `GoogleMap`、`LatLng` 和 Marker / Polyline 类型；替换时需要同步调整这些页面与依赖。若服务商使用不同坐标基准，应在显示适配层显式转换，避免把偏移坐标直接写回计时路线。
- **外置 GPS / Dragy**：先确认真实 BLE 服务、通知订阅、数据帧结构、单位、时间基准和断线行为，再适配为 `LocationData`。现有 UUID / 解析代码是待验证示例，不能作为已确认协议。当前手机 GPS 不会自动扫描 Dragy。
- **外部时间戳**：设备 UTC 或自身计数器不能直接写入 `timestamp`；需要建立与 Android 单调时钟的映射，并处理延迟和重新连接。否则样本会被新鲜度检查丢弃，或导致错误圈速。
- **后台记录**：需单独设计定位服务、通知、权限、功耗和进程恢复，不能只移除当前 `onStop` 暂停逻辑。

扩展后先增加合成轨迹 / 录制回放测试，再进行真机接收器和实际路线验证。

## 4. 排错

| 现象 | 检查项 |
| --- | --- |
| 显示本地轨迹示意与地图未配置提示 | 本地 Gradle 属性 / 环境变量是否存在；是否重新构建并安装 |
| Key 已配置但底图仍空白 | Maps SDK 是否启用、结算状态、API 限制、包名与当前签名 SHA-1、网络和 Play services |
| Debug 能显示，Release / Play 版本空白 | 检查实际分发版本的签名；不能只登记本机 debug 或上传证书 |
| 改了环境变量但仍使用旧配置 | 同名 Gradle 属性优先；移除旧属性后重建 |
| 地图正常但 GPS 一直等待 | 精确位置权限、系统 GPS 开关、设备接收条件；地图加载成功不代表获得了新位置 |
| 自动计时不触发 | 是否启用并等待起点、样本精度 / 新鲜度、实际穿越有限起点线段且方向正确；查看自动计时规则 |
| 频率低于预期 | 看实际位置频率，检查设备和接收条件；更小的请求间隔不能提升硬件能力 |

排查授权失败可在本地查看 Logcat，但分享前应移除 Key、坐标和个人路径。不要通过取消所有 Key 限制来发布应用。曾公开的密钥应撤销或轮换，历史清理说明见[安全清理记录](security-cleanup.md)。
