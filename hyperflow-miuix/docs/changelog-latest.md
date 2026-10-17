# HyperFlow 1.1.0-beta33 更新日志

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
