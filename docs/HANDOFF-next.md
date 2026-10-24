# HyperFlow 交接文档（换工具前快照，更新至 2026-10-24，beta42 构建中）

> 用途：本文件供下一个接手的人/AI 快速续上。所有事实以本仓库代码 + `docs/trustservice-auth-chain.md` 为准；本快照之后的改动以 git 历史为准。

## 0. 一句话现状
- 仓库 `wjx2951874/HyperFlow`，工作分支 **`beta`**（正式通道 `main` 不动，`update.json` 停在旧正式版，测试期**不要改**，避免误推正式）。remote URL 内嵌 PAT token，用 `git remote get-url origin` 提取（禁止明文打印）；**持续用同一把 PAT 不轮换**。
- 当前 **beta42 构建中**（走 CI，未本地编译）：
  1. 确认弹窗（解锁时流转/双开/妙享直通等）统一**贴底 bottomInset=0**——Popup 独立窗口盖住悬浮导航栏，系统 Dialog 同款（原 88dp 智能避让导致偏上）。
  2. **前缀弹窗"飞上面"根治**：MainActivity 显式 `windowSoftInputMode="adjustResize"`（原默认 adjustPan 键盘弹出把整个窗口顶上去）+ 前缀弹窗 `bottomInset=0.dp` 贴输入法顶。
  3. 「免预填调试 · 检测捕获状态」**改彩蛋机关**：默认不显示，点击 AuthSetupScreen 大标题「锁屏密码预设置」10 次（2 秒窗口）才显示（FlowScreen 设备协同机关同款 pattern）。
- 已交付主线状态：
  - **电话流转化**（beta38 起回归可用）：来电一律放行（锁屏/亮屏不分）+ 同一来电 3 秒只放行一次；VoipType 复用原生 isVoipSupported 判定（**deviceType 从真实参数 args[4] 取**，修 beta37 反射拿 -1 bug）；Relay 精准化。**用户已装未反馈电话实测结果**。
  - **A 方案**（beta39 起免明文）：日常解锁缓存 gatekeeper handle，授权时 handle+challenge 直接换 HAT 回传，不碰密码明文；无 handle 自动回退预设置凭证/手动。
  - **预设置入口**（beta40）：功能页「妙享直通」开关下新增显式「锁屏密码预设置」入口（原彩蛋机关保留）。
- 两条主线：① 电话流转化（等用户装包实测反馈）；② A 方案静默校验（免明文 HAT 已实现，待实测）。

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

## 6. 电话流转化主线（2026-10-24，当前最前线，beta38 起回归可用）

### 6.1 根因（hf_light2.log + LSPosed 实锤 + 反编译）
- 亮屏来电走监听路径（`NOTIFICATION_FROM_BROADCAST=false`），被 `AppNotificationFilter.filterCall` 过滤（`!fromBroadcast && isLocalVoipSupported()` 恒真）→ 不流转。锁屏走广播路径（自带广播标志）→ 放行。
- 间歇挂断（beta36 实测）：同一来电通知被系统推送多轮（onNotificationPosted 多次），milink 每轮都流转 → 每台接收设备收多份 voip → 会话冲突挂断。
- 亮屏只有通知而非 voip：`buildPlainMessage` 判定 `fromBroadcast && isVoipSupported` 短路，监听路径恒 msg。

### 6.2 三个 hook 现状（beta38 最终形态）
1. **HookCallFilterPass**（milink，hook `AppNotificationFilter.filterCall`）：来电（callType==1）一律放行（**锁屏/亮屏不分**——beta37 曾加"真实锁屏拦截"，实测锁屏无独立广播路径兜底，拦截即全灭，已删）；**同一来电通知 3 秒内只放行第一次**（ConcurrentHashMap key+elapsedRealtime，只让 milink 处理一次 → 每设备永远 1 份）。去电/非 call 保持原过滤。
2. **HookCallRelay**（milink，hook `NotificationHandler.isDeviceSupported`）：精准版——不模拟任何屏幕状态（beta36 全局模拟锁屏导致小米15 接听时 milink 查 isInteractive=false 误判息屏中断会话），仅来电且原判定拒绝时返回 true。
3. **HookCallVoipType**（milink，hook `NotificationBuilder.buildPlainMessage` 返回值）：**只对非广播来源的来电**，复用原生 `VoipTransHelper.isVoipSupported(sbn, deviceId, deviceType)` 判定——支持 voip 的设备（小米15、新款 iPhone）亮屏也标 voip；不支持（iPhone X）保持 msg 降级通知。**deviceType 必须从 buildPlainMessage 真实参数 args[4] 取**（beta37 反射 DeviceSubInfo.getDeviceType() 拿到 -1 → 全降级 msg，已修）。锁屏（广播来源）消息类型完全走原生判定，不干预。
- 附：HookCallVoipForce（beta35，iPhone 强制 voip）仍在但被原生判定取代，未删。

### 6.3 官方能力边界
- 澎湃 OS 3 最新升级新增"小米手机来电同步到 iPhone 后在 iPhone 端接听"（双向）；新款 iPhone 认 voip（用户实测），iPhone X（旧 iOS/旧 App）不认 → 降级通知。
- 跨设备通话官方接收端此前仅平板/电脑，iOS 端接听为最新新增。

### 6.4 电话线待验证（用户已装 beta38，未反馈）
- 亮屏来电 ×5：小米15 只收 1 份 voip、不挂断；iPhone X 收 msg 通知。
- 锁屏来电 ×5：同样稳定 1 份。
- 预期日志：`filter released once, allow transfer`（放行）、`same incoming call within window, skip`（第二次回调跳过）、`native isVoipSupported=false, degrade to notification`（iPhone 降级）、`msg->voip (native isVoipSupported=true)`（小米 voip）。

### 6.5 抓日志要点（已踩坑）
- Note12 Termux：`su -c 'nohup logcat -v threadtime > /sdcard/nt.log 2>&1 &'`（**必须 nohup**，否则 su 退出即杀后台）。
- 小米15 aShell You（Shizuku）：**不要加 & 后台**（执行器不保留后台任务），前台挂着等复现。
- 模块日志只在 **LSPosed 日志页**（TAG=HyperFlowPlus），logcat 看不到；**LSPosed zip 优先**。

## 7. 已提交的 UI 改动汇总
- beta42（构建中）：确认弹窗贴底盖悬浮导航栏（bottomInset=0）；前缀弹窗键盘"飞上面"根治（adjustResize + bottomInset=0）；免预填调试入口改"点标题 10 次"彩蛋机关。
- beta41：设置页「外观」→「界面外观」，小字"可单独设置App的界面外观"。
- beta40：功能页妙享直通下新增「锁屏密码预设置」显式入口。
- beta37：主题「外观」+「底栏形态」加提示小字（"切换后立即生效，无需重启" / "悬浮导航栏为胶囊样式，液态玻璃带模糊背景"）。
- 历史：tab「流转」→「功能」；「版本」标题 FontWeight.Medium；双开前缀提示+居中弹窗。

## 8. 当前提交前待办（beta42，直接做）
1. 本地未编译（环境 gradle/JDK 版本混乱，直接走 CI）；版本已 bump **170 / 1.1.0-beta42**（build.gradle.kts + ksu-module/module.prop 两处已同步）。
2. `changelog-latest.md` 头部已改 beta42；`HANDOFF-next.md` §0/§7/§8 已同步。
3. `git add`（4 个文件 + 2 个 docs，**勿 add `hyperflow-miuix/.kotlin/` 临时目录**）→ commit → push `beta` → dispatch CI → 等 success → 取 artifact `HyperFlow-flashable` → present_files 交付。
4. CI 必须 Java+Kotlin 都编过（漏 import 会 cannot find symbol）。
5. 交付后引导用户装包重启，重点验证：①确认弹窗是否贴底盖住悬浮胶囊；②前缀弹窗键盘弹出是否贴输入法不飞上面；③点"锁屏密码预设置"标题 10 次是否出现"检测捕获状态"。
6. 后续待办延续：电话 §6.4 连测、A 方案 §4.5 实测。

## 9. 抓日志命令（root Termux，先启动后复现，别先贴停止命令）
```bash
su; logcat -c; nohup logcat -v threadtime > /sdcard/a1.log 2>&1 &
# 复现（对端发起连接/来电）后：
pkill -f "logcat -v threadtime"
grep -E "HyperFlowPlus|A-1|A-2|MiTrustService|gatekeeper|verifyCredential" /sdcard/a1.log > /sdcard/a1_key.log
```
日志走 LSPosed 管理器日志页（TAG=`HyperFlowPlus`），回退 android.util.Log。

## 10. 遗留登记（未开始/未完成，勿丢）
- **电话线**：beta38 用户已装未反馈——按 §6.4 连测并导 LSPosed zip 回传核对三条日志。
- **A 线**：免明文 handle 实测（§4.5 步骤 1-2）；Bind_A→k.l(p0c,hat) 两参未自动；图案分支实机验证；错密码罚时观察。
- iPhone 接听：iOS 端小米互联服务需最新版并注册 com.ios.phone relayCall 服务，iPhone X 实测定论。
- 消息页三点级联菜单改 HyperIsland 样式（miuix 自带 OverlayIconCascadingDropdownMenu）。
- 乙方案分身 hook 跳过搬 u0 主微信 task；1.2.0 双向对等对控；隐私合规 A4/A3；工程质量 B1-B3；消息页改造 M14-M19。
- 已知噪音：HookSmsPersist install failed（用户未提，不动）。

## 11. 关键陷阱
- jadx 混淆字段/方法名运行时不保留，**反射一律按类型匹配或用方法名+参数类型**，不硬编码占位名。
- 本地只编 Kotlin 会漏 Java 符号；assembleRelease 本地 OOM，只能 CI。
- `update.json` / main 正式通道测试期不动。
- 密码类错了会被系统罚时，自动校验只提交一次、失败计数封顶 3，不循环。
- 电话 hook 的 deviceType 只能从方法真实参数取（args[4]），勿反射 DeviceSubInfo。
- 锁屏"原逻辑"拦截监听路径会全灭（beta37 实测），锁屏必须走监听路径放行。
