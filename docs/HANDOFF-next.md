# HyperFlow 交接文档（换工具前快照，更新至 2026-10-24，beta46 构建中）

> 用途：本文件供下一个接手的人/AI 快速续上。所有事实以本仓库代码 + `docs/trustservice-auth-chain.md` 为准；本快照之后的改动以 git 历史为准。

## 0. 一句话现状
- 仓库 `wjx2951874/HyperFlow`，工作分支 **`beta`**（正式通道 `main` 不动，`update.json` 停在旧正式版，测试期**不要改**，避免误推正式）。remote URL 内嵌 PAT token，用 `git remote get-url origin` 提取（禁止明文打印）；**持续用同一把 PAT 不轮换**。
- 当前 **beta46 构建中**（versionCode 174）：
  1. **系统框架作用域双兼容**（分身点击/自动捕获密码的根因修复）：用户 LSPosed 勾了「Android系统(android)」但 framework 是 Vector/JingMatrix 分支，system_server 注入 pkgName 传 `"system"`（作用域里「系统框架 system」与「Android系统 android」是两个条目），XposedEntry 只匹配 `"android"` → **HookRemoteOpen / HookKeyguardCapture 在 system_server 根本没装上**（beta44 日志无 system_server 加载记录）。修复 = 双匹配 `"android" || "system"`。
  2. **确认弹窗上移 16dp**（beta42 曾 bottomInset=0 贴底，用户实测太靠下）。
  3. **前缀输入弹窗提升 MainActivity 根层**（原在 FlowScreen 内渲染被悬浮胶囊盖住；提升后 Scaffold 之后绘制 → 盖住胶囊，inApp 保留键盘跟随）。
- 已交付主线状态：
  - **电话流转化**：beta45 重做后待实测（详见 §6）。
  - **A 方案**（beta39 起免明文）：日常解锁缓存 gatekeeper handle，授权时 handle+challenge 直接换 HAT 回传，不碰密码明文；无 handle 自动回退预设置凭证/手动。**注意：依赖 HookKeyguardCapture（system_server），beta46 起作用域修复后才能真正生效**。
  - **预设置入口**（beta40）：功能页「妙享直通」开关下新增显式「锁屏密码预设置」入口（原彩蛋机关保留）。
- 两条主线：① 电话流转化（beta45 等用户装包实测）；② A 方案静默校验（作用域修复后待实测）。

## 1. 项目是什么
小米澎湃 OS 互联增强：Xposed 模块（通知/短信流转、分身双开标识、电话流转化）+ miuix 原生 UI App。被控端 root（Note12 Turbo），发起端小米15（无 root 走 Shizuku），另有 iPhone X（deviceType=11，iOS 端仅认 msg 通知）。

## 2. 目录与构建
- UI：`hyperflow-miuix/app/src/main/kotlin/com/hyperflowplus/`
- hook：`hyperflow-miuix/app/src/main/java/com/hyperflowplus/hooks/`
- 本地**轻量编译**（`assembleRelease` 必 OOM 137，只能走 CI），必须**同时编 Kotlin 和 Java**（只编 Kotlin 会漏 Java import，CI 才暴露）：
```bash
cd hyperflow-miuix
JAVA_HOME=/home/user/.doubao_tools/jdk-21.0.12.1+1 \
ANDROID_HOME=/home/user/.doubao_tools/android-sdk \
/home/user/.doubao_tools/gradle-9.6.0/bin/gradle \
:app:compileReleaseKotlin :app:compileReleaseJavaWithJavac \
--no-daemon --offline --no-watch-fs -q
```
- CI：`.github/workflows/build.yml`，dispatch `POST /repos/wjx2951874/HyperFlow/actions/workflows/build.yml/dispatches` body `{"ref":"beta"}` 返回 204；产物经 `/actions/runs/<id>/artifacts` 取 zip（artifact 名 `HyperFlow-flashable`）。
- 版本单一事实来源**两处必须同步**：`hyperflow-miuix/app/build.gradle.kts`（versionCode/versionName）+ `hyperflow-miuix/ksu-module/module.prop`（**version 与 versionCode 是两行，都要改**，教训：beta37 只改了 version 行导致 versionCode 落后）。

## 3. 设备与角色
- Note12 Turbo（root，被控来电机，装模块，trustservice 在此弹授权盘）。
- 小米15（无 root，Shizuku/aShell You，接收端）。
- iPhone X（ECD92C64，deviceType=11，iOS 端"小米互联服务"App 只认 msg 通知、不处理 voip 消息）。
- 本机锁屏经日志核实 = **6 位数字 PIN**（`NUMERIC / typePin:3 / localPasswordLength:6`）。录入页不能自动识别类型，需用户手选（曾误选成"密码"）。

## 4. A 方案 · 静默校验（beta39 起免明文）

### 4.1 目标
远控/镜像连接、本机 trustservice 弹授权盘时，**后台自动完成校验**，无需手动输密码、不模拟点击 UI。错密码由系统真实判（会罚时），只提交一次、失败计数封顶 3 不循环。

### 4.2 校验链（反编译 OS4 MiTrustService.apk，产物仓库外 `trust-os4/src/sources`，jadx 在 `trust-os4/tools/bin/jadx`）
- 会话建立（hook 点）：混淆类 `Z0.b`（= LockSettingUtils）：`g(...)` → f489k=1（g 代理，Bind_A 系）；`h(k, byte[] challenge, String deviceId, String branch)` → f489k=2（k 代理，Bind_B/Risk_B）。
- 校验：`verifyCredential(cred, 0, 1)` → `isMatched()`（SDK≥35）→ `getGatekeeperPasswordHandle()` → `verifyGatekeeperPasswordHandle(handle, e(challenge), 0)` → `getGatekeeperHAT()`。`e(challenge)` = 前 8 字节 little-endian 转 long。
- 按分支回传（f489k=2，k 代理）：`Risk_B→k.x(hat)`、`Bind_B→k.j(hat)`、`Bind_A→k.l(p0c, hat)`（两参，**暂未自动**）；f489k=1（g 代理）：`Bind_A→g.c(hat)`、`Bind_B→g.e(hat)`、`Auth_Risk_B→g.v(hat)`。
- 凭证构造复用系统 `Z0.b.c(int,String)`：3=createPin / 4=createPassword / 1=createPattern。

### 4.3 免明文 HAT 通道（beta39 新增，当前首选）
- **HookKeyguardCapture（system_server 作用域）**：用户**日常解锁**（重启后首次输密码）时 hook `LockPatternUtils.verifyCredential` 成功 → ① `capture()` 提取明文凭证写 `CapturedCred`（**这条在用户机器上实测为空**：getCredential 提不出明文，勿再依赖）；② 新增：取 `VerifyCredentialResponse.getGatekeeperPasswordHandle()` → `CapturedCred.writeHandle()` 缓存到 `/data/system/hyperflow_handle`（JSON：handle+ts）。
- **HookTrustAuthAuto.autoVerify（trustservice 作用域）**：优先 `CapturedCred.readHandle()` → 用缓存的 handle + 本次 challenge 调 `verifyGatekeeperPasswordHandle` → 拿 HAT → 按分支回传（Risk_B→x / Bind_B→j）→ 自动关 UI。**全程不碰密码明文**。handle 失效/无 → 回退预设置凭证路径（Config 里 AES 加密存的密码）→ 再不行手动。
- 关键日志：解锁后 `CapturedCred: handle cached (...)`；授权时 `A-2: HAT via cached handle (no plaintext), branch=..., auto ok, closing UI`。
- 约束：handle 有效期与系统"多久后重新要求输密码"设置相关；**作用域必须勾 `android`**（否则日常解锁 hook 不装，handle 永为空）。

### 4.4 预设置凭证路径（beta40 入口已显示）
- 功能页「妙享直通」开关下新增 **ArrowPreference「锁屏密码预设置」**（原彩蛋机关"连点设备协同标题 10 次"保留）→ `Route.AuthSetup`（AuthSetupScreen）。
- 录入 PIN/password/pattern，AES-GCM 加密存 Config（KEY_AUTH_TYPE/KEY_AUTH_SECRET/KEY_AUTH_PATTERN）。关闭妙享直通即 `clearAuth()` 清凭证。

### 4.5 A 线待办（下一棒）
1. **实测免明文通道**：装 beta40+ → 重启 → **先日常解锁一次**（看 LSPosed 是否出 `CapturedCred: handle cached`）→ 发起连接 → 看是否出 `A-2: HAT via cached handle` 且自动通过。
2. 若 `handle cached` 不出：确认模块作用域是否勾了 `android`（system_server）；LSPosed 日志搜 `HookKeyguardCapture: hooked`。
3. 补 `Bind_A→k.l(p0c,hat)` 两参回传（p0c=Context 单例 `P0.c.f234v.a()`）。
4. 图案分支实机验证（走 b.c(1,pattern)，点编号 1..9 按连线拼接）。
5. 被动版 `hooks/HookTrustAuth.java`（hook A.d.e 喂 UI）保留兜底。

## 5. 已收口的结论
- **弹窗闪退（beta31 根治，commit 15ba6e0）**：miuix 菜单 `NavigationBackHandler` 需 `LocalNavigationEventDispatcherOwner`，菜单提层后读不到 null 即崩。根治 = `AppNavigation` 最外层 `CompositionLocalProvider` 兜底空 `NavigationEventDispatcher()`。**已全局不复发**（排序/显示模式/外观/会话排序点击均不再崩）。
- **消息排序菜单拆分（beta32）**："依据(发送人/时间)"和"方向(正/倒序)"拆两组互斥单选，存储格式不变（name_asc/name_desc/time_asc/time_desc）。
- **双开前缀**：条目提示改「现在为【内容】，点击可修改」；编辑弹窗 HyperDialog `centered=true` 屏幕居中、删 imeBottom 随键盘飘移。
- 外观"跟随系统"**不需要实时切换**（用户撤销，勿再加回）。

## 6. 电话流转化主线（2026-10-24，当前最前线，beta45 重做后待实测）

### 6.1 根因（hf_light2.log + LSPosed 实锤 + 反编译）
- 亮屏来电走监听路径（`NOTIFICATION_FROM_BROADCAST=false`），被 `AppNotificationFilter.filterCall` 过滤（`!fromBroadcast && isLocalVoipSupported()` 恒真）→ 不流转。锁屏走广播路径（自带广播标志）→ 放行。
- 间歇挂断（beta36 实测）：同一来电通知被系统推送多轮（onNotificationPosted 多次），milink 每轮都流转 → 每台接收设备收多份 voip → 会话冲突挂断。
- 亮屏只有通知而非 voip：`buildPlainMessage` 判定 `fromBroadcast && isVoipSupported` 短路，监听路径恒 msg。

### 6.2 三个 hook 现状（beta45 最终形态）
1. **HookCallFilterPass**（milink，hook `AppNotificationFilter.filterCall`）：来电（callType==1）且**真实亮屏**才放行；**真实锁屏保持原生过滤**（监听路径不流转，锁屏广播路径独享一份 voip，防"通知+voip 双份"——用户 beta44 实测双份）。锁屏判定用进程内 PowerManager.isInteractive + KeyguardManager.isKeyguardLocked（beta38 起 Relay 不再模拟全局屏幕状态，进程内 API 为真实值）；查询失败 fallback 亮屏放行（宁可偶发双份也不全灭——beta37 教训：跨进程 Binder fallback 成锁屏不放行 → 来电全灭）。**同一来电通知 3 秒内只放行第一次**（ConcurrentHashMap key+elapsedRealtime）。去电/非 call 保持原过滤。
2. **HookCallRelay**（milink，hook `NotificationHandler.isDeviceSupported`）：精准版——不模拟任何屏幕状态，仅来电且原判定拒绝时返回 true（普通通知亮屏也放行，beta44 起）。
3. **HookCallVoipType**（milink，hook `NotificationBuilder.buildPlainMessage` 返回值）：**只对非广播来源的来电**，复用原生 `VoipTransHelper.isVoipSupported(sbn, deviceId, deviceType)` 判定——支持 voip 的设备（小米15、新款 iPhone）亮屏也标 voip；不支持（iPhone X）保持 msg 降级通知；**判定失败（deviceType 拿不到/原生调用抛错）→ return null 拦截流转不发通知**（用户拍板：voip 发不出来就别发通知，降级通知只给原生明确不支持 voip 的设备）。
   - **deviceType 获取（beta45 重写，关键）**：args[4] 曾是真值（beta36 实测 11/1 正常），但 milink 更新后**漂移成无意义大数（148748/195737/34469）** → 原生判定恒 false → 亮屏全降级 msg（用户实测"只有手机号通知"）。修复 = args[4] 合法小值优先，否则反射 DeviceSubInfo（args[1]）内部字段遍历找合法小整数 deviceType。**禁止再裸信 args[4]**，也禁止反射 DeviceSubInfo.getDeviceType()（方法不存在拿 -1）。
- 附：**HookCallVoipForce 已删除**（beta45，文件本体 + Config 开关 + XposedEntry 安装点 + import 全清），用户拍板用原生判定。

### 6.3 官方能力边界
- 澎湃 OS 3 最新升级新增"小米手机来电同步到 iPhone 后在 iPhone 端接听"（双向）；新款 iPhone 认 voip（用户实测），iPhone X（旧 iOS/旧 App）不认 → 降级通知。
- 跨设备通话官方接收端此前仅平板/电脑，iOS 端接听为最新新增。

### 6.4 电话线待验证（beta45 待用户装包实测）
- 亮屏来电 ×5：小米15 只收 1 份 voip、不挂断、**有通讯录名字**；iPhone X 收 msg 通知。
- 锁屏来电 ×5：**只收 1 份 voip**（不再"通知+voip 一起来"）。
- 预期日志：`filter released once (screen-on), allow transfer`（亮屏放行）、`incoming while keyguard locked, keep native filter`（锁屏监听路径拦截）、`same incoming call within window, skip`（重复回调跳过）、`native isVoipSupported=false, degrade to notification`（iPhone 降级）、`msg->voip (native isVoipSupported=true)`（小米 voip）、`voip verdict failed (...) drop transfer`（判定失败拦截）。
- **注意核对**：`HookCallVoipType: deviceType=X from DeviceSubInfo field Y`——若反射拿到的 deviceType 不对（比如拿到非设备类型的 int 字段），亮屏 voip 判定可能仍 false → 需要看 DeviceSubInfo 字段名再精调。

### 6.5 抓日志要点（已踩坑）
- Note12 Termux：`su -c 'nohup logcat -v threadtime > /sdcard/nt.log 2>&1 &'`（**必须 nohup**，否则 su 退出即杀后台）。
- 小米15 aShell You（Shizuku）：**不要加 & 后台**（执行器不保留后台任务），前台挂着等复现。
- 模块日志只在 **LSPosed 日志页**（TAG=HyperFlowPlus），logcat 看不到；**LSPosed zip 优先**。

## 7. 已提交的 UI 改动汇总
- beta42：确认弹窗贴底盖悬浮导航栏（bottomInset=0）；前缀弹窗键盘"飞上面"根治（adjustResize + bottomInset=0）；免预填调试入口改"点标题 10 次"彩蛋机关。
- beta41：设置页「外观」→「界面外观」，小字"可单独设置App的界面外观"。
- beta40：功能页妙享直通下新增「锁屏密码预设置」显式入口。
- beta37：主题「外观」+「底栏形态」加提示小字（"切换后立即生效，无需重启" / "悬浮导航栏为胶囊样式，液态玻璃带模糊背景"）。
- 历史：tab「流转」→「功能」；「版本」标题 FontWeight.Medium；双开前缀提示+居中弹窗。

## 8. 当前提交前待办（beta46，直接做）
1. 本地未编译（环境 gradle/JDK 版本混乱，直接走 CI）；版本已 bump **174 / 1.1.0-beta46**（build.gradle.kts + ksu-module/module.prop 两处已同步，version 与 versionCode 两行都要改）。
2. `changelog-latest.md` 头部已改 beta46；`HANDOFF-next.md` §0/§7/§8/§10/§11 已同步。
3. `git add`（**XposedEntry.java、ConfirmDialog.kt、FlowScreen.kt、MainActivity.kt、SettingsScreen.kt** + 2 个 docs + 2 个版本文件，**勿 add `hyperflow-miuix/.kotlin/` 临时目录**）→ commit → push `beta` → dispatch CI → 等 success → 取 artifact `HyperFlow-flashable` → 校验 module.prop version=1.1.0-beta46/versionCode=174 → present_files 交付。
4. CI 必须 Java+Kotlin 都编过（MainActivity 新 import Arrangement/TextField、FlowScreen 删弹窗后多余 import 只是 warning）。
5. 交付后引导用户装包重启，重点验证：①**LSPosed 日志出现 `HyperFlow loaded in system_server (pkgName=system)`**（作用域修复生效标志）；②分身微信点击不再同时开主微信；③自动捕获密码不再为空（/data/system/hyperflow_handle 有内容）；④确认弹窗不再贴底；⑤前缀输入框在悬浮胶囊上层。
6. 后续待办延续：电话 §6.4 连测（beta45 已交付待实测）、A 方案 §4.5 实测。

## 9. 抓日志命令（root Termux，先启动后复现，别先贴停止命令）
```bash
su; logcat -c; nohup logcat -v threadtime > /sdcard/a1.log 2>&1 &
# 复现（对端发起连接/来电）后：
pkill -f "logcat -v threadtime"
grep -E "HyperFlowPlus|A-1|A-2|MiTrustService|gatekeeper|verifyCredential" /sdcard/a1.log > /sdcard/a1_key.log
```
日志走 LSPosed 管理器日志页（TAG=`HyperFlowPlus`），回退 android.util.Log。

## 10. 遗留登记（未开始/未完成，勿丢）
- **电话线**：beta45 已重做未实测——按 §6.4 连测并导 LSPosed zip 回传核对日志，重点：亮屏 voip 带名字、锁屏单份、iPhone 降级、DeviceSubInfo 反射拿到的 deviceType 是否正确。
- **分身微信点击拉镜像主微信仍同时打开**：**根因已实锤 = system_server 作用域标识不匹配**（用户勾 android 但 JingMatrix 传 system，HookRemoteOpen 没装上）。beta46 已修双匹配，装包后验证；若仍同开再看 mirror 进程 PendingIntent 双发（rc=-1 后 rc=0）是否为双份。
- **自动捕获密码结果为空**：**同一根因**（HookKeyguardCapture 在 system_server 没装上）。beta46 起作用域修复后应能缓存 handle；若还空再查 HookKeyguardCapture 的 gatekeeper handle 缓存链。
- **锁屏验证 UI 不自动消失**（有结论）：trustservice 已装。反编译确认：①`LockScreenUIActivity.onResume` **不存在**（A-2 兜底必然 not found）；②原生成功路径 `Q0.d.run(i2==0)` 确实不 finish（只取消超时+通知上层回传+起线程）；③HookTrustAuthUiClose（netbus.h.n code==0 → 补 finish）已装上，理论能关。装包实测确认；若仍不关，需 hook 正确的成功回调（onStop 的 isDeviceLocked 检查 / Q0.a.j 链路）。
- **A 线**：免明文 handle 实测（依赖 system_server 作用域，beta46 后验证）；Bind_A→k.l(p0c,hat) 两参未自动；图案分支实机验证；错密码罚时观察。
- iPhone 接听：iOS 端小米互联服务需最新版并注册 com.ios.phone relayCall 服务，iPhone X 实测定论。
- 消息页三点级联菜单改 HyperIsland 样式（miuix 自带 OverlayIconCascadingDropdownMenu）。
- 乙方案分身 hook 跳过搬 u0 主微信 task；1.2.0 双向对等对控；隐私合规 A4/A3；工程质量 B1-B3；消息页改造 M14-M19。
- 已知噪音：HookSmsPersist install failed（用户未提，不动）。

## 11. 关键陷阱
- jadx 混淆字段/方法名运行时不保留，**反射一律按类型匹配或用方法名+参数类型**，不硬编码占位名。
- 本地只编 Kotlin 会漏 Java 符号；assembleRelease 本地 OOM，只能 CI。
- `update.json` / main 正式通道测试期不动。
- 密码类错了会被系统罚时，自动校验只提交一次、失败计数封顶 3，不循环。
- **system_server 作用域标识双兼容**：经典 LSPosed 传 pkgName=`android`，Vector/JingMatrix 分支传 `system`（用户作用域截图里「系统框架 system」与「Android系统 android」是两条，勾 android 不生效）。XposedEntry 必须同时匹配两者；用户框架版本变化时先查 LSPosed 日志有没有 `HyperFlow loaded in system_server`。
- 电话 hook 的 deviceType：**args[4] 已漂移不可信（beta44 拿到 148748 等乱值）**，必须用反射 DeviceSubInfo 字段兜底（见 §6.2 第 3 条）；但仍**禁止**反射 DeviceSubInfo.getDeviceType() 方法（不存在，拿 -1）。
- 锁屏"原逻辑"= 锁屏时 FilterPass **保持原生过滤**（监听路径不流转，广播路径独享原生 voip 一份）——beta45 已按用户拍板实现，不再是 beta37"锁屏不放行导致全灭"（beta37 是跨进程 Binder 判定失败 fallback 错，且当时 Relay 还在模拟全局屏幕状态；现在用进程内真实 API + fallback 亮屏放行）。
- beta37 曾实测"锁屏拦监听路径全灭"——根因是跨进程锁屏判定失败 fallback 成"锁屏不放行"；beta45 改为进程内 API + fallback"亮屏放行"，若实测锁屏又出现"通知+voip 双份"，是 isScreenOn 误判锁屏（KeyguardManager 在 milink 进程可能拿不到真实状态），需改用跨进程但**失败时放行**。
