# HyperFlow 1.1.0-beta37 更新日志

## 修复

### 1. 间歇性"连接已断开/电话被挂断"根治（电话流转化 · 三根因，LSPosed 日志 2026-10-24 15:48 实锤）
- **现象（beta36 实测）**：亮屏/锁屏来电流转后，小米15 间歇弹"当前连接已断开，请回到原设备接听电话"、电话被直接挂断；时好时坏、有时连续。
- **根因 A · 同一来电重复流转（日志实锤）**：同一来电通知会被 `onNotificationPosted` 触发多轮（响铃刷新），每轮都完整走流转链 → 每台接收设备收到同一来电的**多份 voip 消息** → 小米15 多份会话冲突 → 挂断。日志每通来电出现 2 轮 `HookCallFilterPass released`。
- **修复 A**：`HookCallFilterPass` 新增**同通知去重**——以 `sbn.getKey()` 识别同一通知，3 秒窗口内只放行一次，亮屏每设备稳定 1 份。
- **根因 B · 锁屏双流转**：锁屏来电时广播路径（ON_CALL_VOIP）1 份 + 监听路径放行 2 份 = 3 份消息 → 必冲突。
- **修复 B**：`HookCallFilterPass` 新增**跨进程真实锁屏判定**（Binder 到 system_server 的 `IWindowManager.isKeyguardLocked`，绕开进程内被模拟的 API）；真实锁屏不放行监听路径（广播路径独享），查不到保守按锁屏处理。
- **根因 C · 全局模拟锁屏干扰 voip 会话**：旧 `HookCallRelay` 把 milink 进程内 KeyguardManager/PowerManager/Display 全套 hook 成"锁屏态"，小米15 接听时 milink 查 `isInteractive=false` 误判息屏 → 中断会话 → 间歇挂断。
- **修复 C**：`HookCallRelay` 重写为**精准版**——不再模拟任何系统状态，只 hook `NotificationHandler.isDeviceSupported`，对来电且原判定拒绝时返回 true（放行流转），短信/其他通知与 voip 会话建立全部恢复真实状态。

### 2. iPhone X 恢复来电通知（beta36 回归修复）
- **现象（beta36 实测）**：iPhone X 收不到任何通知（连 msg 都没有）。原因：beta36 对所有设备来电硬编码改 voip，iPhone 端"小米互联服务"App 不处理 voip 消息 → 直接丢弃。
- **修复**：`HookCallVoipType` 不再硬编码设备类型，**复用原生 `VoipTransHelper.isVoipSupported` 判定**（反编译确认：来电 + 转移开关 + 本机支持 + 远端 relayCall flags 达标才 voip）——小米15 全屏接听 voip，iPhone X 自动降级为 msg 通知。完全对齐原生"支持 voip 推送 voip、不支持降级通知"语义。

### 3. 设置页提示小字
- 主题「外观」下新增提示："切换后立即生效，无需重启"。
- 主题「底栏形态」下新增提示："悬浮导航栏为胶囊样式，液态玻璃带模糊背景"。

## 进展（开发埋点，不影响现有行为）

### A 方案 · 主动后台静默校验（beta33-34 已并入，遗留补参/实测）
- `HookTrustAuthAuto` 挂会话建立点，PIN/混合密码/图案三型凭证构造复用系统构造器，`verifyCredential → getGatekeeperPasswordHandle → getGatekeeperHAT → 按分支回传`；失败计数 3 次封顶回退手动；`Bind_A→k.l` 两参未自动、图案实机验证未做。

---

# 历史（beta35 及之前）

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
  - 两通道都失败时打 `HyperFlowPlus` 日志（含两通道原始输出）便于真机排查，界面落手动选择。

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
