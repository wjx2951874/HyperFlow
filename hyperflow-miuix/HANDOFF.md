# HyperFlow 交接文档（HANDOFF）

> 更新：2026-10-24，beta34 后、推荐作用域修正（99ea896）后
> 交接原因：任务移交新 AI 工具继续开发

---

## 0. 一句话现状

**发送端电话流转链路已验证 100% 打通**（补发广播 → milink → 云端 200），锁屏来电流转正常；**亮屏来电仍不流转**，最新日志分析把根因范围收窄到 InCallUI 广播发出的"15 秒延迟 + milink 冷启动窗口"，见 §2。推荐作用域机制已查实为 `META-INF/xposed/scope.list`（新版 Vector）并修正，**未出包**。远期"自定义应用流转"（其他应用流转）需求方案已定，见 §6。

---

## 1. 仓库与构建

- 公开仓库：`wjx2951874/HyperFlow`，工作分支 `beta`（main 是 v0.5.3 旧线）
- 本地根目录：`/home/user/Doubao/chats/38444811295021314/hyperflow-miuix/hyperflow-miuix`
- 版本事实源：`app/build.gradle.kts`（当前 versionCode=162 / versionName="1.1.0-beta34"）+ `ksu-module/module.prop`
- 编译命令（禁 assembleRelease，会 OOM）：
  ```bash
  cd .../hyperflow-miuix && JAVA_HOME=/home/user/tools/jdk-21.0.12.1+1 ANDROID_HOME=/home/user/Android/Sdk /home/user/tools/gradle-9.7.1/bin/gradle :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --no-daemon --console=plain
  ```
- CI：GitHub Actions `build.yml`，出 `HyperFlow-flashable` artifact
  - 触发：`POST /actions/workflows/build.yml/dispatches -d '{"ref":"beta"}'`（PAT 由原交接方单独提供，勿写入文档/仓库）
  - 下载：`/actions/runs?branch=beta&per_page=1` → artifact → 解出 zip 放 `packages/` 命名 `HyperFlow-1.1.0-beta34.<N>-flashable.zip`
- 交付轮次：beta33.6=fc8e96a（手动输入即捕获）、.7=3646643（电话流转首版）、.8=82ccabe（免预填自动填）、.9=05616c5（电话三hook+底栏三选一）、beta34=e3b9ae7（versionCode 162）、99ea896（scope 修正，未出包）

## 2. 电话流转主线（当前最前线，务必先读）

### 2.1 用户实测现状（beta34）
- **锁屏来电 → 流转正常 ✓**（beta33.9 修复生效）
- **亮屏（解锁）来电 → 对方收不到 ✗**；用户锁 12T 后对方才收到，解锁后流转持续

### 2.2 hf_light.log（2026-10-24 亮屏来电全量日志）关键时间线
```
12:44:57.254  milink(9468) onCreate —— 进程重启（装包后重启），订阅表未加载
12:44:57.553  InCallUI(24503) onBind —— 来电，InCallUI 状态机启动！
12:44:57.570  StatusBarNotifier - resendNotification true false
12:44:57.741  buildAndSendNotification isOnlyUpdateAction
12:44:57.747  isNeedFullScreenIntent...true（设了全屏 intent）
12:44:57.833  milink onNotificationPosted incallui（通知监听收到来电通知）
12:45:12.950  milink NotifTransReceiver: Broadcast received: ON_CALL_VOIP（广播此时才到！）
12:45:12.952  PreferencesManager 订阅解析完成
12:45:12.953  NotificationHandler: isSubscribed true（Xiaomi 15 / iPhone X 订阅了 incallui）
12:45:13.026  VoipTransHelper: voip supported=true, target=Xiaomi 15
12:45:13.033  加密推送云端 → 12:45:13.282 HTTP 200
（之后无第二条 ON_CALL_VOIP 广播、无第二条推送）
```

### 2.3 结论与未定论
- **推翻旧假设**："亮屏来电 Telecom 不绑定 InCallUI、状态机不启动" —— **日志实锤亮屏时状态机也启动了**（INCOMING、resendNotification、buildAndSendNotification 全跑）
- 反编译确认：`sendNotificationBroadcast(Call)`（StatusBarNotifier.java:878，实例私有）：
  - 内部 `RelayUtils.getPhoneRelayCall(call)` 为真则跳过；否则 `FixedThreadPool.getInstance().execute(new l(3, this, call))` **异步发广播**
  - 触发点：`onStateChange`（1199-1211）：`RelayUtils.isSupportRelayToIphone() && inCallState2==INCOMING && 新来电 && !iPhoneCall && !RelayUtils.getRelayCall()` → sendNotificationBroadcast
  - 另 `buildAndSendNotification` 6 参重载（1368）dump 缺失，内部是否也调广播未确认
- **未定论（下一步要查的）**：
  1. 12:44:57 的原生广播（resendNotification 链）为何 15 秒后才到 milink？可能：milink 冷启动（12:44:57.254）窗口广播丢失 / FixedThreadPool 排队 / 广播系统延迟
  2. 12:45:12.950 那条广播是"原生迟到的"还是"我们补发的"？**区分方法：看 LSPosed 日志页**（MiflowLog 走框架日志不写 logcat！）——搜索 HyperFlow，看有没有 `ON_CALL_VOIP relayed`（补发执行标志）和 `HookCallRelay: PowerManager.isInteractive CALLED`（milink 判定点 hook 生效标志）
  3. 亮屏 vs 锁屏：锁屏流转成功走的是哪条链（onStateChange 广播？resendNotification 链？通知监听路径 onNotificationPosted？）

### 2.4 现有相关代码
- `app/src/main/java/com/hyperflowplus/hooks/HookCallVoipNotify.java`（incallui 进程，beta33.9 三 hook）：
  - ① hook `StatusBarNotifier.sendNotificationBroadcast(Call)` 打"原生已发"时间戳
  - ② hook `RelayUtils.getPhoneRelayCall(Call)`（public static）记录 relay 判定
  - ③ hook `StatusBarNotifier.notifyNotification(Context, NotificationManager, Notification)`（public static，814）延迟 500ms 补发 ON_CALL_VOIP（键名：pkg/id/tag/uid/initialPid/userHandle/notification，复用同一 Notification）
- `app/src/main/java/com/hyperflowplus/hooks/HookCallRelay.java`（milink 进程）：模拟锁屏——hook KeyguardManager.isKeyguardLocked/isKeyguardSecure/inKeyguardRestrictedInputMode/isDeviceLocked → true，PowerManager.isInteractive/isScreenOn → false，Display.getState→OFF/isOn→false，WindowManager.isKeyguardLocked/Secure→true，全部 force_transfer 门控
- `HookForceTransfer.java`：**当前 no-op**（beta33.1 起，注释"放行判定由 HookCallRelay 模拟锁屏完成"）
- **注意**：`RelayUtils.getIPhoneCall` / `isSupportRelayToIphone` / `getRelayCall` 在 onStateChange 里决定是否发广播（1211）——**锁屏 vs 亮屏广播差异可能在这**，建议下一步反编译确认这几个方法

### 2.5 下一步建议（给接手者）
1. 让用户装最新包（含 scope 修正）后，**LSPosed 日志页搜 HyperFlow**，亮屏来电看：`ON_CALL_VOIP relayed` 有无、`HookCallRelay ... CALLED` 有无 → 判定补发是否执行、milink 判定点是否生效
2. 若补发没执行（notifyNotification 亮屏没被调）→ 换挂点：hook `onStateChange`（INCOMING 转移时直接补发广播）或 hook `sendNotificationBroadcast` 的调用路径
3. 若补发执行了但 milink 没收到 → 检查广播构造（可能缺 category="call" / fullScreenIntent 导致接收端不显示，用户实测"锁屏才显示"可能与此有关）
4. 接收端 Xiaomi 15 未 root（有 ADB），如需接收端日志：`adb logcat -v time > /sdcard/x15.log` 抓小米 15 收到 voip 推送后的处理

## 3. 推荐作用域机制（2026-10-24 查实修正）

- **新版 Vector（JingMatrix，LSPosed 继承者 v2.0）对现代模块（libxposed API 100+）的推荐作用域 = APK 内 `META-INF/xposed/scope.list`（一行一个包名）**，**不读** manifest 的 `xposedscope` meta-data（那是遗留模块 xposedminversion 机制）
- 我们模块是 libxposed API 102 → **scope.list 是唯一生效的推荐作用域**（`app/src/main/resources/META-INF/xposed/scope.list`）
- 已修正（99ea896）：scope.list 补 `com.android.incallui`；arrays.xml 清掉 3 个无 hook 冗余（只留 incallui），保留仅为兼容经典 LSPosed
- **未出包**：scope.list 修正 + arrays.xml 清理需要出 beta34.1 或并入下个包
- 反编译参考：Vector `manager-ui/.../ModuleDetection.kt`（SCOPE_ENTRY="META-INF/xposed/scope.list"，modern 路径不 swap legacy 名称；module.prop 里 minApiVersion/targetApiVersion/staticScope）

## 4. 底栏形态三选一（beta33.9 已交付）

- 设置页「主题」卡片：原「悬浮导航栏」「液态玻璃」两个开关 → `OverlaySpinnerPreference`（外观同款气泡选择）选项：默认/悬浮/液态玻璃（值 standard/floating/liquid）
- `HFState.kt` 新增 `setBottomBarStyle(style)` 同时写 `bottom_bar_style` 枚举 + 旧布尔键 nav_float/glass_effect（MainActivity 只读 bottomBarStyle 渲染不动）

## 5. 设备拓扑与硬偏好（违反会被骂）

- 被控端（来电机）：Redmi Note 12 Turbo，root（KernelSU）+ LSPosed/Vector，当前模块版本 beta34
- 发起端（接收端）：Xiaomi 15，HyperOS 4，**未 root**（有 ADB 权限）；iPhone X 也订阅了电话流转
- 用户硬偏好：零自绘 UI（设置项统一 Miuix 原生组件）；机关=连点「设备协同」标题 10 次 2 秒窗口进 AuthSetup（不弹提示无涟漪）；双开输入框贴底贴输入法点击不蹦；电话流转零人工干预且亮屏保持上半横幅不全屏；状态卡拦截被否（"也交给 mlink"）；验证注入用 **LSPosed 日志页截图**（MiflowLog 走框架日志，logcat 看不到！）；电话里"妙享直通"开关名在 HyperOS 4 上叫 Hyper Flow Beta（用户备注）；每次完成一步汇报
- 免预填相关已完成（beta33.8）：HookTrustAuthAutoFill（授权盘显示延迟 500ms 调 A.d.e 提交留存凭证）、HookTrustAuthUiClose（netbus.h.n 结果 0 → finish）、CapturedCred sp 通道（trustservice 私有 sp hyperflow_captured.xml）、HookKeyguardCapture 日常解锁捕获 0 条问题未解（已被手动捕获替代）

## 6. 自定义应用流转（其他应用流转）——远期主线，需求方案已定

### 6.1 用户需求演变
1. 最初："双开流转仅流转双开应用通知（主应用不流转）"
2. 扩展："自定义应用流转：小米互联清单外应用也能流转；分身精确控制"
3. 提过能否 100% 复刻系统应用锁页面（答：UI 可复刻，像素级没必要）
4. 曾暂缓（"先保留"），现用户点名交接文档必须包含

### 6.2 已拍板设计要点（必守）
1. 流转页「通知流转」分组下加「自定义应用流转」开关（默认关），开启后显示"自定义应用流转设置"条目进二级页（学现有二级页：TopAppBar+卡片+横滑返回，如 AuthSetupScreen）
2. 二级页含：**"其他全部应用"勾选**（=小米互联清单外全部）+ 逐个应用勾选（**主应用与分身分开显示**）
3. 现有"应用双开流转"开关合并进二级页、流转页移除 → 加"全部分身应用"统一按钮/开关（交互形态用户未最终确认，曾建议开关、或关系）
4. "双开标识前缀"设置搬进二级页
5. 小米互联清单内 7 个应用（AppPkgId.getMiPackageList：com.android.incallui/com.android.mms/com.tencent.mm/com.tencent.mobileqq/com.ss.android.lark/com.alibaba.android.rimet/com.tencent.wework）**不放进列表、不受我们控制**——但它们的**分身**要显示可勾
6. hook 点：
   - `isDeviceSupported`（milink NotificationHandler.java:99）：跳过包名订阅 anyMatch 检查（保留 deviceSwitch + 锁屏/息屏判定）
   - `isNotificationValid`（:133）：分身放行条件从"主应用已订阅碰巧放行"改为"独立按勾选名单"
7. 分身识别：小米双开 = user 999（XSpace 多用户，milink CLONE_APP_USERID=999 实锤）。**普通三方 App 无 root 无法 getInstalledApplicationsAsUser(999)**（需要 INTERACT_ACROSS_USERS 系统权限）；系统应用锁靠 com.miui.securitycore 签名权限。我们的路线：有 root 走 RootExec `pm list packages --user 999`，无 root 有 Shizuku 走同命令，都没有则隐藏分身区+提示
8. 新版可能路线（未验证）：Android 13+ 官方 Clone Profile 机制——若小米新版双开迁到官方 clone profile，普通 App 可 `UserManager.getUserProfiles()` + `getInstalledApplicationsAsUser` 枚举。验证方法：`su -c "pm list users"` 看 999 注册类型。**用户说"分身过一会再说"，暂不动作**

### 6.3 二级页实现骨架参考
- 现有二级页：AuthSetupScreen（TopAppBar + Card + BackHandler）
- 路由：Routes.kt 添加 `@Serializable data object` 导航目标
- 应用列表数据源：PackageManager 主用户枚举 + root/Shizuku 999 枚举

## 7. 反编译产物与关键文件索引

- 反编译根：`/home/user/Doubao/chats/38444811295021314/reverse/`（工具 `reverse/tools/bin/jadx`）
- InCallUI：`reverse/incallui_out/sources`（APK：/product/priv-app/InCallUI/InCallUI.apk）
  - StatusBarNotifier.java：notifyNotification:814（hook 点）、sendNotificationBroadcast:878（实例私有，FixedThreadPool 异步）、onStateChange:1199-1211（广播触发条件）、buildAndSendNotification:381/1368、NOTIFICATION_TAG="incall"
  - RelayUtils.getPhoneRelayCall:116（public static，hook 点）
  - TelecomAdapter.startForegroundNotification:244（唯一调用方=notifyNotification:816）
- milink：`reverse/out3/sources`
  - NotificationHandler.java：isDeviceSupported:99（keyguard 判定在 126，`keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()`）、isNotificationValid:133（999 分身拒绝）、lambda$handleNotificationTrans$0（流转主循环）
  - DeviceSubInfo：`com.xiaomi.dist.notification.common.data.DeviceSubInfo`（getDeviceSwitch/getSubscriptionAppInfoList/SubscriptionAppInfo.getPackageName）
  - NotifTransReceiver / NotifTransListenerService（onNotificationPosted:101）/ NotificationBuilder / VoipTransHelper（relay call 能力判定）
- 信任服务：`reverse/trust_out/sources`（免预填 hook 参考）

## 8. 未完成项（按优先级）

1. **电话亮屏流转**（当前最前线）：按 §2.5 定位 15 秒延迟 / 补发是否执行 / 亮屏 vs 锁屏广播差异；目标：亮屏来电对方也能收到（且亮屏保持上半横幅不全屏）
2. **推荐作用域修正出包**：scope.list + arrays.xml 修正（99ea896）已推送未出包
3. **自定义应用流转二级页**（§6）：用户重新提起时按已定方案开工
4. 1.1 正式版发布流程（Release + update.json + 版本号流程，未细化）
5. 分身 999 枚举路线实测（root pm list users / pm list packages --user 999）
6. HookKeyguardCapture 日常解锁捕获 0 条问题（已被手动捕获替代，不阻塞）

## 9. 交接注意事项

- **MiflowLog 走 libxposed 框架日志（LSPosed 日志页），不写 logcat**！看模块日志必须用 LSPosed 日志页搜索 `HyperFlow`
- logcat 里 milink 的 tag 是 `dist:crossdeviceservice`（进程 com.milink.service），InCallUI 是 `InCall`
- 用户设备上 MyHyperModifier 模块会疯狂刷 LSPosed 日志（miui.systemui.plugin），需用搜索框过滤
- 用户沟通风格：要现成包/命令，反感长篇解释和低效方案；每次完成一步汇报
