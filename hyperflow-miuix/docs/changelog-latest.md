# HyperFlow 1.1.0-beta60 更新日志

## beta60（versionCode 188，commit fd2eff2）——锁屏密码捕获主通道改造
- **锁屏凭证捕获**：主通道从 `LockPatternUtils.verifyCredential`（实测 0 捕获，非真实入口）改为 **`com.android.internal.widget.LockSettingsService.checkCredential(LockscreenCredential,int,int)`**——system_server 内锁屏输密码的真正验证入口，**重启后首次解锁必走**；`verifyCredential` 保留兜底。
- 捕获走 `CapturedCred.write(type, raw)`（公共文件 `/data/system/hyperflow_captured` + trustservice sp），`HookTrustAuth` / `HookTrustAuthAutoFill` 自动填直接复用（`readPublic` → `readLocalSp`），授权盘一次都不再弹。
- **格式注意**：pattern 的 `LockscreenCredential.getCredential()` 返回点阵串，注入格式待实测（pin 已验证 len=6 数字串）。
- 分身逻辑与 beta59 完全一致，无行为变更。

## beta59（versionCode 187，commit a2915e3）——双开根治三层防线（待 beta60 全量日志验证）
1. **HookCloneBypass 发送端釜底抽薪**：`markCloneTitle` 只对 `sbn.getUser().hashCode()==999` 的分身通知动手，把 `n.contentIntent` **重建为 `PendingIntent.getActivity(..., UserHandle(999))`**（全反射，ActivityThread/UserHandle/5 参重载全 @hide）；intent 打 `hf_is_clone` 标记供 HookRemoteOpen 兜底识别。
2. **HookRemoteOpen 重排**：`redirectToClone` 先查 `hf_is_clone` 标记（caller 无关直接改 999）→ 远程白名单 caller（ALLOWED_CALLERS 且 userId∈{0,-1}）直接改 999 → 3 秒时序反推兜底；统一走 `redirect()` 入口（有 userId 参数直接 `args.set`，无则 `relaunchAsClone` 吞调用重发 999）。
3. **HookMirrorObserve 全路径观察**：新增 `android.app.IActivityTaskManager$Stub$Proxy` 的 startActivity* 家族 hook（框架类不混淆），日志 `[MIRROR-OBS] ATM.<method>` 显示 mirror 进程实际调用。

## beta58（versionCode 186，commit 24d4ee9）——双开/授权 UI 修复
- **HookRemoteOpen 全前缀**：`startsWith("startActivity")` 覆盖 AsCaller/WithOptions/ForUser（milink 实测走这些，beta57 只 hook startActivity/AsUser 漏网）。
- **授权 UI 自动关闭**：删混淆字段 f1753J 反射，改 `ActivityThread.mActivities`（框架字段不混淆）遍历 finish 前台 trustservice/lockscreen Activity；HookTrustAuthAuto 的 hookAuthUi 改 hook 框架 `Activity.onResume` 按类名特征过滤 + `closeAuthViaActivityThread()` 双重兜底。

## beta57（versionCode 185，commit f127b0f）——system_server 注入根治
- 恢复新式 libxposed 架构；补 `onSystemServerStarting` 分发（对照 HyperModifier/Vector 的 `SystemServerHookers.kt`）；`git rm assets/xposed_init` 防 legacy 误判；用户实测注入成功（system_server 四条绿 hook）。
- 根因回溯：Compose 引入后多 dex，D8 把 XposedEntry 分到 classes3 → 主 dex 无入口 → system_server 静默跳过注入（beta53 已用 R8 单 dex 解决，beta55/56 legacy 化实测无效被否决）。

## 历史（beta56 及更早）
### beta56：修复 hook 桥双执行 + legacy 探针兜底并出包（用户实测否决 legacy 化路线）
### beta55：legacy 化尝试（被 beta56 用户实测否决，"legacy 不影响功能"不成立）
### beta54：拉回新式基线
### beta53：system_server 注入总根因修复——R8 单 dex 方案生效

## 历史（beta51）
## 历史（beta51）
### 弹窗"强制出现 + 取消键点了没反应"总根因修复（HyperDialog show 参数从未被使用）
- **根因**：`HyperDialog(show = ...)` 的 show 参数**完全没有被使用**——函数体直接 `if (inApp)`，没有 `if (show)` 包裹。只要 ConfirmDialog 被组合（无论 show 真假）弹窗就无条件渲染。
- **表现**：没开应用内消息的设备切到消息 tab → 未开启页的 ConfirmDialog 被组合 → 弹窗"强制出现"；点取消 → showStartDialog=false 但 HyperDialog 无视 show → 弹窗不关（取消键没反应）。
- **修复（beta51）**：HyperDialog 函数体加 `if (show)` 包裹。切 tab 不再弹、点取消立即关闭。
- 其他弹窗正常的原因：调用方自己包了 `if (showXxx)`（如删除确认），只有 MessagesScreen 的确认窗没包。

## 历史（beta50 并入）
### beta50 图标收小 + 版本行字体对齐
- 图标收小：beta47 中心裁剪 75%（图形 ~80%）→ 桌面偏大 → **beta50 改为中心裁剪 85%（图形 ~71%）**，介于原始 ~60% 与 beta47 ~80% 之间。108px / xxxhdpi 432px 同步重新生成。
- 版本行字体：版本值原为 `FontWeight.Light`（偏细）→ 改为 `FontWeight.Normal`，与其他作者 App（miuix ArrowPreference 标准条目）一致。

## 历史（beta49 并入）
### beta49 亮屏来电手机号（判定失败）真正根因修复——deviceType/deviceId 拿错参数
- **反编译最新版 milink 签名实锤**：`buildPlainMessage(sbn, DeviceSubInfo, TrustedDeviceInfo, String, int)`——`args[1]` 是 **DeviceSubInfo 对象**（含接收端设备的 deviceId/deviceType），`args[4]` 是 `i10`（**消息体普通 int，不是 deviceType**）。
- beta45/46/47/48 一直拿 `args[4]` 当 deviceType（漂移成 148748/195737 乱值；恰好是合法小值时优先采用 → 判定全错）→ 原生 isVoipSupported 恒 false → 亮屏只剩手机号通知。deviceId 也从 `args[3]`（String 内容）错拿。
- **修复（beta49）**：直接从 `args[1]`（DeviceSubInfo）反射 `getDeviceType()`/`getDeviceId()`（小米原生自己就是 `deviceSubInfo.getDeviceType()`，jadx 实锤方法存在且未混淆）；getDeviceType 反射失败再兜底字段遍历。**去掉 args[4] 优先逻辑**。
- **待用户实测**：亮屏来电应为"带名字的 voip 全屏接听"（小米15/新款 iPhone），iPhone X 保持卡片降级。

## 历史（beta48 并入）
### beta48 锁屏双份（通知+voip 一起来）真正根因修复
- **beta45/46 的 isScreenOn() 判断顺序有缺陷**：先查 `PowerManager.isInteractive()`，屏幕亮着就判"亮屏"。来电会点亮屏幕——**锁屏界面亮着时 isInteractive()=true → 直接误判亮屏** → 放行监听路径 → 锁屏通知卡片+voip 双份（用户实测 beta46 依旧双份，与 beta45 声称修复矛盾）。
- **修复**：KeyguardManager 锁屏判定**优先**——`isKeyguardLocked()==true` 即锁屏（即使屏幕亮），保持原生过滤（广播路径独享一份 voip）；亮屏非锁屏才放行监听路径。

## 历史（beta47 并入）
### beta47 图标放大 + 弹窗上移
- 图标放大：白色环形图形从 ~60% 提升到 ~80%（中心裁剪 75% 放大回 2048），桌面占满不留白。108px / xxxhdpi 432px 同步更新。
- 双开标识前缀输入弹窗上移 16dp（对齐"解锁时流转"等确认弹窗），仍盖住悬浮胶囊、保留键盘跟随。

## 一、图标放大（桌面占满）
- 原图标白色环形图形仅占容器 ~60%，四周蓝底留白偏大，桌面观感"没占满"。中心裁剪 75% 后放大回 2048：图形占比提升至 ~80%，蓝色容器占满画布边缘（图标 mask 裁圆角不受影响）。108px / xxxhdpi 432px 两份已同步更新。

## 二、双开标识前缀弹窗上移
- 前缀输入弹窗（点"双开标识前缀"后出现的输入框）原 bottomInset=0 贴底，用户要求上移 → **16dp**，与"解锁时流转"等确认弹窗（ConfirmDialog 默认 16dp）一致。弹窗仍盖住悬浮胶囊、保留键盘跟随。

## 历史（beta46 并入）

## 一、系统框架作用域修复（分身点击/自动捕获密码的根因）
- **根因**（用户 LSPosed 作用域截图 + beta44 日志实锤）：用户勾选了「Android系统(android)」，但 framework 是 Vector/JingMatrix 分支，system_server 注入时 `pkgName` 传的是 **`"system"`**（作用域里「系统框架 system」与「Android系统 android」是两个不同条目），而 XposedEntry 只匹配 `"android"` → **system_server 里 HookRemoteOpen / HookKeyguardCapture 根本没装上**：
  - 分身微信点击拉镜像 → 无人改写 userId → 主微信被打开（分身同开）
  - 自动捕获密码 → 缓存 handle 路径完全没跑 → 结果为空
- **修复**：system_server 分支改为 `"android" || "system"` 双匹配，日志带 pkgName 可确认。

## 二、弹窗细节修复
- 确认弹窗（开启/关闭功能时的弹窗）太贴底：beta42 曾改 bottomInset=0，用户实测太靠下 → 默认 **16dp**（Popup 独立窗口仍盖住悬浮导航栏，抬 16dp 不露栏）。
- 双开标识前缀输入框被悬浮胶囊盖住：前缀弹窗原在 FlowScreen 内渲染（悬浮胶囊在其后绘制 → 盖住输入框）。**提升到 MainActivity 根层渲染**（Scaffold 之后绘制，在悬浮胶囊之上；inApp 保留键盘跟随，贴键盘顶）。

## 三、新图标落地 + 品牌名统一
- 新图标（App 桌面图标 + 模块图标，蓝白 HyperOS 风格）：环形回流箭头 + 四向连接节点，体现"内容持续流转、多设备始终互联"（功能不只是流转：来电/短信/通知流转、妙享直通、双开、镜像）。落地 `drawable/ic_launcher_new.png`（108px）+ `drawable-xxxhdpi/ic_launcher_new.png`（432px），manifest `android:icon` 切换；旧 `ic_launcher.xml` 保留未删。
- 品牌名统一：日志 TAG（MiflowLog/HookCallVoipNotify/HookTrustAuthAutoFill）、zip 内安装包目录与文件名（build.yml flash 路径）、customize.sh/service.sh 的 APK 名、全部文档 `HyperFlowPlus` → `HyperFlow`。**包名 `com.hyperflowplus` 保持不变**（LSPosed 作用域/root 白名单/数据目录兼容）。
- 双开标识前缀输入框被悬浮胶囊盖住：前缀弹窗原在 FlowScreen 内渲染（悬浮胶囊在其后绘制 → 盖住输入框）。**提升到 MainActivity 根层渲染**（Scaffold 之后绘制，在悬浮胶囊之上；inApp 保留键盘跟随，贴键盘顶）。

## 历史（beta45 并入）
### beta45 来电流转（按用户拍板重做）
### 1. 移除强制 iPhone voip（用原生判定）
- 删除 `HookCallVoipForce`（强制 deviceType=11 判定支持 voip）及对应配置开关，**完全走原生 `isVoipSupported` 判定**：
  - 新款 iPhone / 小米15 / 其他 Android：原生判定支持 voip → 亮屏来电标 voip 全屏接听
  - iPhone X 等原生不支持的：自动保持 msg 降级通知
- 锁屏（广播来源）继续走原生 buildPlainMessage 判定，不干预（锁屏原逻辑）。

### 2. 修复亮屏来电"只有手机号通知"（deviceType 参数漂移）
- **根因**（beta44 LSPosed 日志实锤）：milink 更新后 `buildPlainMessage` 的 args[4] 不再是 deviceType，拿到 148748/195737 等无意义大数 → 原生 `isVoipSupported(sbn, deviceId, 乱值)` 恒 false → 亮屏全部降级成 msg 通知。
- **修复**：deviceType 改为多级获取——args[4] 合法小值优先；否则**反射 DeviceSubInfo 内部字段**遍历找合法小整数 deviceType（1=手机 4=平板 11=iPhone），不硬编码混淆字段名。

### 3. 锁屏不再双份（通知+voip 一起来）
- **根因**：beta38"来电一律放行"后，锁屏来电**广播路径**（原生 voip）和**监听路径**（被放行的原始通知）同时流转 → 用户实测"锁屏通知和 voip 一起来"。
- **修复**：`HookCallFilterPass` 加**真实屏幕状态判定**——锁屏时保持原生过滤（监听路径不流转，广播路径独享一份 voip）；真实亮屏才放行监听路径。
- 锁屏判定用进程内 PowerManager/KeyguardManager（beta38 起 HookCallRelay 不再模拟全局屏幕状态，进程内 API 为真实值）；查询失败 fallback 亮屏（放行），宁可偶发双份也不全灭（beta37 教训）。

### 4. voip 判定失败时拦截流转（不发降级通知）
- 按用户要求：亮屏来电若 voip 判定失败（deviceType 拿不到 / 原生调用抛错），**拦截本次流转（不发通知）**，避免"本来该 voip 的设备收到一条只有手机号的降级通知"。降级通知仅保留给原生判定明确不支持 voip 的设备（iPhone X）。

## 历史（beta42/43/44 并入）
- beta44：普通通知亮屏流转修复（HookCallRelay 对所有通知放行，来电/非来电同待遇，不模拟屏幕状态）。
- beta43：锁屏密码预设置页卡死修复（RootExec.su 加 8 秒超时，超时自动落手动选择）。
- beta42：确认弹窗贴底 + 前缀弹窗键盘适配 + 免预填调试彩蛋机关（点标题 10 次显示）。

---

# 历史（beta41 及之前）

## 修复

### 1. 电话流转化：按用户实测重排（beta38，亮屏 voip / 锁屏原逻辑 / 单份流转）
- **beta37 翻车复盘**：①锁屏时拦监听路径，实测锁屏没有独立广播路径兜底 → 锁屏全灭；②VoipType 反射 DeviceSubInfo.getDeviceType() 取 deviceType（方法不存在 → -1），原生判定恒 false → 亮屏全降级通知；③用户明确否决复杂去重。
- **beta38 修复**：
  - `HookCallFilterPass`：来电**一律放行**（锁屏/亮屏不分，锁屏不再拦截），同一来电通知 3 秒内**只放行第一次**（系统回调多次也只让 milink 处理一次 → 每台设备永远只收 1 份）。
  - `HookCallVoipType`：**不硬编码任何设备**，复用原生 `isVoipSupported` 判定——**只给原生判定支持 voip 的设备在亮屏标 voip**（小米15 全屏接听、新款 iPhone 亮屏也收 voip 来电）；原生不支持的（iPhone X）自动保持 msg 通知。deviceType 改为从 buildPlainMessage 真实参数（args[4]）取。锁屏（广播来源）消息类型完全走原生判定（锁屏原逻辑，不干预）。
  - `HookCallRelay`：保持精准化（不模拟屏幕状态，voip 会话建立不受干扰）。

### 2. A 方案免明文 HAT（beta39）
- 日常解锁（system_server）时缓存 gatekeeper handle 到 /data/system/hyperflow_handle；授权时 handle+challenge 直接换 HAT 回传，不碰密码明文；无 handle 自动回退预设置凭证/手动。

### 3. 预设置入口显示（beta40）
- 功能页「妙享直通」开关下新增「锁屏密码预设置」ArrowPreference 显式入口。

### 4. 设置页提示小字（beta37）
- 主题「外观」+「底栏形态」加提示小字。

# 历史（beta37 及之前）

## 修复

### 1. 间歇性"连接已断开/电话被挂断"三根因（beta37，LSPosed 日志 2026-10-24 15:48 实锤；beta38 已按实测重排）
- 同 beta38 修复说明（三 hook 变化见上）。beta37 版本为：FilterPass 加真实锁屏拦截+去重、VoipType 用原生判定（deviceType 取错）、Relay 精准化——其中 FilterPass 锁屏拦截与 VoipType 取错值已实测翻车，由 beta38 修正；Relay 精准化保留。

### 2. iPhone X 恢复来电通知（beta36 回归，beta38 继续）
- beta36 对所有设备硬编码 voip → iPhone 连通知都不显示；beta37/38 改为原生判定 → 支持 voip 的设备收 voip，iPhone X 自动降级 msg 通知。

### 3. 设置页提示小字（beta37）
- 主题「外观」新增提示"切换后立即生效，无需重启"；「底栏形态」新增提示"悬浮导航栏为胶囊样式，液态玻璃带模糊背景"。

## 修复

### 1. 亮屏来电跨设备流转（电话流转化 · 根因修复）
- **问题**：锁屏来电流转正常；**亮屏来电对方收不到**（Note12 Turbo 为来电机，小米15 / iPhone X 为接收端）。
- **根因（hf_light2.log 实锤 + 反编译交叉验证）**：来电通知经监听路径即时到达 milink，但被 `AppNotificationFilter.filterCall` 静默过滤——
  - 通知**非广播来源**（`NOTIFICATION_FROM_BROADCAST=false`），且本机 `com.android.phone` 声明 `supportiOS=true` → 判定"本机自己会接，别转走" → `notification is invalid, skip transfer`；
  - 锁屏走**广播路径**（ON_CALL_VOIP 广播通知自带广播标志）→ 放行。这就是"锁屏通、亮屏不通"的原因。
- **修复**：milink 进程新增 `HookCallFilterPass`，对**来电（callType=1）强制放行过滤**，通知监听路径即走完整流转链，不再依赖补发广播。去电（callType=2）与非电话通知保持原过滤。受「功能① 亮屏强制流转」开关控制。

### 2. iPhone voip 直接接听强制放行（配合官方 2026-10-24 新功能）
- **背景**：小米官方澎湃 OS 3 最新升级（10-24 起推送）新增"小米手机来电同步到 iPhone 后可在 iPhone 端接听"（双向）；但 iOS 端"小米互联服务"App 尚未注册 relayCall 服务，milink 判定 iPhone（deviceType=11）不支持 voip → 只推通知卡片。
- **修复**：新增 `HookCallVoipForce`，仅对 iPhone（deviceType=11）强制 `isRemoteVoipSupported=true`，让转发消息标记为 voip 类型，由 iOS 端 App 决定是否弹 CallKit 接听（实测定论）。小米手机接收端保持原判定不动。受「force_iphone_voip」开关控制（默认开）。

### 3.（回顾）弹窗闪退 beta31 根治 / 排序菜单拆分 / UI 小改（beta33-34 已交付）
- 弹窗闪退（消息排序/显示模式/外观/会话排序）已在 beta31 根治：`AppNavigation` 最外层 provide 兜底 `NavigationEventDispatcherOwner`，不再复发。
- 消息排序菜单拆「依据（发送人/时间）× 方向（正/倒序）」两组互斥单选（beta32）。
- 底部导航第 2 tab「流转」→「功能」；设置页「版本」加粗；双开前缀提示改「现在为【内容】，点击可修改」+ 弹窗屏幕居中（beta33）。

## 进展（开发埋点，不影响现有行为）

### A 方案 · 主动后台静默校验（beta33-34 已并入，遗留补参/实测）
- `HookTrustAuthAuto` 挂会话建立点，PIN/混合密码/图案三型凭证构造复用系统构造器，`verifyCredential → getGatekeeperPasswordHandle → getGatekeeperHAT → 按分支回传`；失败计数 3 次封顶回退手动；`Bind_A→k.l` 两参未自动、图案实机验证未做。

---

# 历史（beta33 及之前）

## 修复

### 1. 消息列表排序菜单：把"排序依据"和"排序方向"拆成两组单选
- **问题**：旧菜单把「按发送人 / 按时间」（排序依据）和「切换为正序/倒序」（排序方向）混在同一个单选列表里，选了"按时间+倒序"时，"按时间"和"切换为正序"两项同时打勾，分不清是按什么排、先后怎么定。
- **修复**：菜单拆成两组各自互斥单选——
  - 排序依据：按发送人 / 按时间
  - 排序方向：正序 / 倒序
- 排序存储格式不变（`name_asc/name_desc/time_asc/time_desc`），列表排序逻辑无需改动。
- 会话详情页「最新在前 / 最早在前」本就是干净的两项单选，未改动。

### 2.（回顾）弹窗闪退已在 beta31 根治
- **问题**：消息排序、显示模式、外观、会话详情排序等 miuix 弹窗菜单点击即崩。
- **根源**：miuix 菜单内部的 `NavigationBackHandler` 需要 `LocalNavigationEventDispatcherOwner`，菜单被提层到独立 composition 后读不到 entry 内的 owner（null）即抛 `IllegalStateException: No NavigationEventDispatcher was provided`。`renderInRootScaffold=true` 只改绘制层级、补不上 owner。
- **根治**：在 `AppNavigation` 最外层（`MiuixTheme` 内、`NavDisplay` 外）用 `CompositionLocalProvider` 提供一个兜底空 `NavigationEventDispatcher()`，entry 内 `NavDisplay` 自行覆盖；不再依赖 `renderInRootScaffold` 开关。

### 3. 界面小调整
- 底部导航第 2 个 tab「流转」改名为「功能」（顶部页名同步）；页面内"通知流转"分组能力名保留。
- 设置页「版本」标题字重由 Regular 提到 Medium，与其他条目对齐，不再偏细。
- 双开标识前缀：条目提示改为「现在为【内容】，点击可修改」；编辑弹窗由底部随键盘飘移改为屏幕居中稳定卡片（标题+说明+输入框+取消/保存）。

### 4. 锁屏密码类型自动识别修复（beta33 追加）
- **问题**：预设置页点「预设置」仍无法自动识别本机锁屏类型（数字/图案/混合），总是落到手选。
- **原因**：识别只靠 `sqlite3 /data/system/locksettings.db` 一条通道——多数 ROM 未内置 sqlite3 二进制直接失败；且 0x10000（图案 SOMETHING 与简单数字 NUMERIC 共用值）会被误判。
- **修复**：改双通道——
  - 通道1（首选）：`cmd lock_settings get-credential-type`（Android 11+ 系统服务权威回答，不依赖 sqlite3），1=图案 / 2=数字PIN / 3=混合密码；
  - 通道2（兜底）：sqlite3 读 `lockscreen.password_type`，仅认 0x20000=数字、0x40000/0x80000/0x90000=混合，0x10000 歧义值放弃判断避免误判；
  - 两通道都失败时打 `HyperFlow` 日志（含两通道原始输出）便于真机排查，界面落手动选择。

### 5. CI 构建链修复（beta33 追加）
- **问题**：`.github/workflows/build.yml` 的 setup-java 仍为 17，而项目要求 Java 21，CI 直接跑必然 `invalid source release: 21` 失败（此前包是本地 JDK21 环境编的，CI 配置未同步）。
- **修复**：java-version 17 → 21，与项目 `sourceCompatibility=21` 对齐，CI 恢复可出包。

## 进展（beta33 时点）

### A 方案 · 主动后台静默校验 A-1（只读观测）
- 新增 `HookTrustAuthAuto`，挂在 `com.xiaomi.trustservice` 的会话建立点 `Z0.b.g()/h()`。
- 当前为 A-1 观测版：`proceed()` 让原流程照常走（UI 照弹、不抢流程、不回传），随后后台遍历静态单例字段，按"类型 + 值摘要"打印 challenge、回传代理、分支标识，供真实连接日志定位。
- A-2 待 A-1 日志确认字段后，再接 `verifyCredential → getGatekeeperPasswordHandle → verifyGatekeeperPasswordHandle → getGatekeeperHAT → 按分支回传真 HAT`，实现不用手输、后台自动过校验。
- 旧被动填值 `HookTrustAuth`（hook `A.d.e` 替换入参）保留不动。

### A 方案 · A-2 主动静默校验（本版编码，待实机联调）
- A-1 已在真实连接命中 Risk_B 分支，dump 出单例字段（见 `docs/HANDOFF-next.md` 第 4.3 节）。
- `HookTrustAuthAuto` 在 `Z0.b.h()` 命中后开后台线程：复用系统 `b.c(type,str)` 构造凭证（PIN/混合密码/图案三型）→ `verifyCredential` 真实校验 → 取真 HAT → 按分支回传（Risk_B→`k.x`、Bind_B→`k.j`）。
- 不模拟点击、不伪造成功；失败/异常静默放行回手动，**连续失败满 3 次本进程放弃自动**，避免反复触发系统罚时。
- 待联调：实机确认 `A-2: SUCCESS, HAT sent via k.x`；补 Bind_A(`k.l` 两参)；图案实机验证。

## 已知问题

- A-2 已编码，待实机联调确认自动过；Bind_A/图案分支待补。
- 电话流转：锁屏可流转，亮屏不行 —— **已修复（beta35：HookCallFilterPass 放行亮屏来电），待实机联调确认**。

### 6. A-2 成功后自动关闭锁屏验证输入框（beta33 追加）
- **问题**：A-2 后台真实校验 + 回传真 HAT 后系统侧已解锁（再次连接可直连、取消输入框即进入系统），但信任服务的锁屏验证输入框（LockScreenUIActivity）仍挂着等输入，甚至被流转到对端显示，体验上"验证成功却卡在弹窗"。
- **修复**：hook `LockScreenUIActivity.onResume` 记录实例；A-2 成功回传 HAT 后置 `authOk` 并在主线程自动 `finish()` 输入框；若输入框晚于校验弹出，onResume 时见 `authOk=true` 直接关闭。失败/异常仍静默放行回手动。

### 7. 设备信息 UI 调整 + 系统版本串完整化（beta33 追加）
- OS 版本与 Android 版本拆成两行显示；设备信息去掉图标只留文字左对齐。
- 系统版本串：优先 `ro.build.version.incremental`（小米完整版本如 "4.0.2.0.XPCCNXM.D01"，与系统设置页"OS 版本"一致），其次 display.id；小米 HyperOS name/code 若为 "OS4.0" 简称则归一为 "HyperOS 4.0"。
