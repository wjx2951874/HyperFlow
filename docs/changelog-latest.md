# v0.6.6

1. App 刷入时联网直装：KSU 执行 customize.sh 阶段压缩包尚未解压（已实证），v0.6.6 起 workflow 将 APK 独立上传为 GitHub Release asset，customize.sh 刷入时联网直下 APK（gh-proxy 镜像 + GitHub 直连双通道）并 pm install —— 刷入即装好 App，无需等 boot；下载失败静默，由 service.sh 在重启后兜底（压缩包内 APK 仍保留兜底）。

## v0.6.5

1. App 自动安装机制修正（安装失败的真正根因）：实测 KSU 执行 customize.sh 时模块目录里只有 module.prop，其余文件（含 APK）尚未解压——这是执行时机问题，不是路径问题，find 在此阶段永远找不到 APK。改为：customize.sh 阶段能装则装（部分变体可见），找不到不报错，由 service.sh 在 boot 后（模块完整落盘）自动安装 App 并输出安装日志到 /data/local/tmp/hyperflow_install.log。刷入后重启一次，模块与 App 全部到位。
2. update.json zipUrl 同步修复：此前发版只同步 version/versionCode，zipUrl 停在 v0.6.1，导致"检测到新版但下载到旧包"。自本版起三处（version/versionCode/zipUrl）一并同步。



1. App 自动安装机制修正（安装失败的真正根因）：实测 KSU 执行 customize.sh 时模块目录里只有 module.prop，其余文件（含 APK）尚未解压——这是执行时机问题，不是路径问题，find 在此阶段永远找不到 APK。改为：customize.sh 阶段能装则装（部分变体可见），找不到不报错，由 service.sh 在 boot 后（模块完整落盘）自动安装 App 并输出安装日志到 /data/local/tmp/hyperflow_install.log。刷入后重启一次，模块与 App 全部到位。
2. update.json zipUrl 同步修复：此前发版只同步 version/versionCode，zipUrl 停在 v0.6.1，导致"检测到新版但下载到旧包"。自本版起三处（version/versionCode/zipUrl）一并同步。

## v0.6.4

1. 环境检测升级为秒级实时：采用 libxposed service 绑定（参考 HyperModifier 同款机制）——模块在 LSPosed 启用时 App 进程实时连接框架 daemon，直接拿到框架实时返回的模块作用域配置。推荐作用域/模块启用判定不再依赖 root + ps/maps/文件探针慢探测，毫秒更新、无 root 依赖，勾选即绿。
2. 更新下载修复：update.json 的 zipUrl 此前停在 v0.6.1（历次发版只同步了 version/versionCode），导致 App/KSU 检测到新版、实际下载却拿到 0.6.1 安装包。
3. KSU 刷入安装 APK 再加固：find 不限深度搜索 + 打印模块目录诊断，若仍找不到可从安装日志直接定位 APK 实际位置。

## v0.6.3

1. KSU 刷入时静默安装 APK 修复：KSU 安装 zip 为白名单式解压，zip 根目录的 APK 不会解压到模块目录（v0.6.0~0.6.2 因此报 "HyperFlowPlus.apk not found"，App 装不上）。customize.sh/service.sh 改为 find 全树搜索 APK，兜底命中 system/priv-app 下的实体 APK，重启一次模块与 App 全部到位。

## v0.6.2

1. 环境检测根治（MLPID 匹配）：检测脚本用进程名精确匹配 milink 失败（Android 进程名 15 字符截断），导致 milink 明明注入了模块却永远判定"推荐作用域未就绪"。改为宽松匹配 milink 关键字，三个推荐域勾选后检测为绿。
2. 首页/流转/消息/设置四个页面标题整体下移（18→30dp），与内容一起往下对齐，不再贴状态栏。
3. 消息搜索框文案"搜索短信"改为"搜索消息"。
4. 会话排序切换修复：切到"最新在前"现在直接定位到最顶部最新消息；切到"最早在前"定位到底部最新消息（导航栏避让区上方可见）。
5. 设置-调试-"软重启框架"入口暂时移除（防误触）。

## v0.6.1

1. 日志文件名带版本号（hf_log_v0.6.1_时间戳.txt）+ 内容头部显式版本标记，日志列表一眼区分新旧，不再混着旧版日志。
2. 分身通知"先开 0 再开 999"根治：补 ActivityTaskManagerService(ATMS) hook（Android 12+ 真实启动路径），第一发请求即改到分身空间 999，主空间 0 不再出现；userId=999 自幂等，不会双开。
3. 刷入时静默安装/更新管理 App：新增 customize.sh，KSU 刷入瞬间 pm install 新版 APK，重启一次模块+App 全部到位（service.sh 保留 boot 后兜底）。
4. 更新检测修复：update.json 同步至最新版本（此前停在 0.5.15.3 导致 KSU/App 内都检测不到新版本）。
5. 版本号体系调整：v0.6.0 起 versionCode 递增（100/101），已刷最新版时 KSU 显示"已是最新"属正常。

## v0.6.0 历史

1. 首页内容整体下移 26dp（对齐 LSPosed/KSU 首页间距，状态卡不再贴顶）。
2. 日志列表新增"一键删除全部"（顶部删除图标 + 确认弹窗）。
3. 软重启框架加确认弹窗，防止误触。
4. 推荐作用域修复：scope.list 补 system/android 双标识（LSPosed 2.2.0 只读 META-INF/xposed/scope.list，arrays.xml 无效）。
5. 电话亮屏流转：改回 milink 原生分流（OS4 走 voip 全屏 / OS3 降级通知），HookForceTransfer 模拟锁屏三件套（isDeviceSupported 放行 + isKeyguardLocked 模拟 + isInteractive 模拟灭屏），来电 hack 全部移除。
