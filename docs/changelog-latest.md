# HyperFlow 1.1.0-beta60 更新日志

## beta60（versionCode 188，commit fd2eff2）——锁屏密码捕获主通道改造
- **锁屏凭证捕获**：主通道从 `LockPatternUtils.verifyCredential`（实测 0 捕获，非真实入口）改为 **`com.android.internal.widget.LockSettingsService.checkCredential(LockscreenCredential,int,int)`**——system_server 内锁屏输密码的真正验证入口，**重启后首次解锁必走**；`verifyCredential` 保留兜底。
- 捕获走 `CapturedCred.write(type, raw)`（公共文件 + trustservice sp），`HookTrustAuth` 自动填直接复用，**授权盘一次都不再弹**。
- pattern 点阵串注入格式待实测（pin 已验证 len=6 数字串）。分身逻辑与 beta59 完全一致。

## beta59（versionCode 187，commit a2915e3）——双开根治三层防线（待日志验证）
1. **HookCloneBypass 发送端釜底抽薪**：分身通知 contentIntent **重建为 userId=999 的 PendingIntent**（全反射，ActivityThread/UserHandle/5 参重载全 @hide）；intent 打 `hf_is_clone` 标记。
2. **HookRemoteOpen 重排**：先查 `hf_is_clone` 标记 → 远程白名单 caller（userId∈{0,-1}）直接改 999 → 3 秒时序反推兜底；统一走 `redirect()` 入口。
3. **HookMirrorObserve 全路径观察**：新增 `IActivityTaskManager$Stub$Proxy` startActivity* 家族 hook，日志 `[MIRROR-OBS] ATM.<method>`。

## beta58（versionCode 186，commit 24d4ee9）
- HookRemoteOpen 全前缀 `startsWith("startActivity")`（milink 实测走 AsCaller/WithOptions/ForUser）。
- 授权 UI 自动关闭：改 `ActivityThread.mActivities`（框架字段）遍历 finish + hook 框架 `Activity.onResume` 按类名过滤 + `closeAuthViaActivityThread()` 兜底。

## beta57（versionCode 185，commit f127b0f）——system_server 注入根治
- 恢复新式 libxposed 架构，补 `onSystemServerStarting` 分发（对照 HyperModifier/Vector 机制）；删 `assets/xposed_init` 防 legacy 误判；用户实测注入成功。

## 历史（beta56 及更早，详见 hyperflow-miuix/docs/changelog-latest.md）

## 历史（beta33）

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

## 进展（开发埋点，不影响现有行为）

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
- 电话流转：锁屏可流转，亮屏不行。
