# 敏感数据清理记录

检查日期：2026-10-28。扫描范围包含原仓库所有可达分支、2 个历史标签、11 次提交、源码/配置与旧 APK 内部资源。

## 发现与处置

- 历史 `strings.xml` 中包含 Google Maps API Key；同一 Key 也被打包进仓库内的旧 APK 和 v1.0.1 Release 附件。源码历史以占位符替换，旧 APK 从 Git 历史移除，泄露的 Release 附件删除。
- IDE 部署配置包含个人路径；`.idea/` 从版本控制与历史移除，本地 IDE 配置不再提交。
- 历史 `gradle.properties` 中的个人 JDK 绝对路径移除，使用 `JAVA_HOME` / Android Studio 本地设置。
- 补充签名文件、环境文件、本地密钥配置和 APK/AAB 的忽略规则。
- 添加 Gitleaks 默认规则、Google API Key 和个人目录补充规则；CI 对获取的完整历史执行脱敏扫描。

本报告不记录 Key 内容、前缀片段、访问令牌或带敏感值的扫描输出。构建与测试生成的 APK、扫描工具和审计中间文件不进入提交。

## 验证与边界

历史重写后重新扫描可达分支和标签，并检查新提交文件及旧版打包资源。功能验证沿用本次优化已通过的 15 项单元测试、3 项模拟器测试和 Lint 检查；本次安全清理没有修改应用功能代码。

已公开的 Maps Key 必须在 Google Cloud 中吊销并更换。历史重写无法撤回他人的克隆、下载或缓存；按旧提交 SHA 的访问与托管平台缓存也不能通过推送保证消失，必要时需联系 GitHub Support。

参考：[GitHub 官方敏感数据清理说明](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)。已有克隆在清理后应重新克隆，避免把旧历史重新推回仓库。
