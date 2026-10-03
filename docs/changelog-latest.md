# v0.6.2

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
