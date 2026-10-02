# HyperFlow 功能详解

每个功能一节：**实现原理 → 代码位置 → 为什么这么写 → 已知限制**。
状态标记：✅ 已实现 · ⏳ 已实现待验证 · 🔲 未实现/规划中

---

## 功能① 亮屏流转（✅）

**原理**：反编译 `com.milink.service` 的 `NotificationHandler` 确认——官方流转判定
`isDeviceSupported(StatusBarNotification, DeviceSubInfo)` 中有：
```java
if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
// "device is not locked and screen is on" → return false（亮屏拒绝流转）
```
主方案：**拦截 `isDeviceSupported` 直接返回 true** —— 等效"模拟锁屏"。
兜底：拦截 `KeyguardManager#isKeyguardLocked`，仅当调用栈含 `NotificationHandler` 时放行。

**代码位置**：`java/com/hyperflowplus/hooks/HookForceTransfer.java`
**开关**：`force_transfer`（流转页"亮屏流转"）

**为什么这么写**：返回 true 是最小侵入；副作用（互联侧看到设备"已锁屏/可流转"）用户已接受。
**限制**：只影响 milink 的流转判定；部分机型亮屏时互联面板状态可能显示异常。

---

## 功能② 分身流转（✅）

**原理**：`isNotificationValid(StatusBarNotification)` 官方逻辑对 user 999（分身空间）返回 false
（分身被拒根源）。模块改为：**999 分身通知直接放行（不改 user）**，标题加【分身】前缀，
供接收端分组/识别；普通应用通知全量放行。

**去重（重要）**：同一条 999 消息会走小米互联**两条流转链路**（一条经过本 hook 带【分身】、
一条原样流出），且可能发生在不同进程。因此：
- 去重 key = **内容指纹**：`包名|id|tag|标题|正文(前96字符)`
- 窗口 = 20s，内存表 + **跨进程共享文件**（`/data/adb/hyperflowplus/released_keys` 等候选路径，
  取第一个可写的），`isDuplicate()` 先查内存再查文件
- 检查**必须在任何放行分支（含 999）之前**

**代码位置**：`java/com/hyperflowplus/hooks/HookCloneBypass.java`
**开关**：`clone_transfer`（流转页"分身流转"）
**计数器**：`cnt_clone`

**历史教训**：
- V0.2.4 做"user 归一"（把 999 改 0）→ 发送端 systemui 也显示一条一样的（重复 bug）→ 已废弃
- V0.4.43 镜像方案（system_server 再造 user 0 镜像通知）→ 复杂度高、需额外作用域 → 废弃
- 最终方案"只放行"最初就是对的——**不会让两个微信都显示**，只是点击打不开分身微信

**限制**：接收端若未装本模块，仍可能看到带/不带【分身】两条（去重在发送端，接收端无 hook）。

---

## 功能②-2 点击分身通知 → 打开 999 微信/QQ（✅ 部分待验证）

**原理**：接收端点击分身通知 → 系统"跨设备镜像打开应用"把 Intent 发回发送端 →
milink 在 **system_server** 执行 `startActivity`（目标包=微信/QQ，userId=0 → 会打开主空间）。
hook 拦截该调用：
- 条件：调用者是 `com.milink.service`（或本 App 消息页）&& 目标包 ∈ {com.tencent.mm, com.tencent.mobileqq}
  && 该包在 user 999 空间存在（`/data/user/999/<pkg>` 存在=已开双开）
- 动作：把 userId 改写为 **999** 重发 → 直接打开多开应用
- 其余情况（本地打开、其他应用、未开双开）**一律走系统原逻辑**，绝不误伤

**代码位置**：`java/com/hyperflowplus/hooks/HookRemoteOpen.java`（注入 system_server，`android` 作用域）
**关键实现细节**：
- 无 `int userId` 参数的重载（`startActivity(intent)`）也要拦——反射 `IActivityTaskManager.startActivityAsUser` 11 参重载以 user 999 重发
- `ALLOWED_CALLERS` / `EXCLUDED_CALLERS` 白黑名单见文件头注释

**限制**：仅适配微信、QQ（其他多开应用暂未适配，按需逐个加）。

---

## 功能③ 短信持久化（✅）

**背景**：接收端 `SmsMessageHandler.handleMiMessage()` 把流转短信写入
`content://com.android.mms.flow.provider/messageflow`（含来源设备名），但 OS4 短信 App
**没有展示"流转消息"的界面** → 数据在、看不见。

**模块策略（两层互补）**：
- **A. flow 归档（默认开）**：App 消息页直接读 flow provider 展示（MIUI 风格会话列表）
- **B. 写系统收件箱（默认关）**：可选把短信注入 `content://sms/inbox`，系统短信 App 可见

**B 的风险（写系统收件箱）**：可能存在错误显示、重复互联、多次复现等问题 → 默认关闭，
开启走 **10 秒倒计时确认弹窗**，提示"遇到异常请及时关闭"。

**发送端补号（HookSmsSenderEnrich）**：流转协议 `NotificationMessage` 本身不含原始号码
（标题只是系统识别出的服务商名）。发送侧构造完成后查询本机收件箱最新短信，
把原始 address 反射写入 `NotificationMessage.focusParam`（接收端原本不消费该字段），
由接收端读出用于注入。查不到则原样传输，不影响流转。

**代码位置**：`hooks/HookSmsPersist.java`（接收端）/ `hooks/HookSmsSenderEnrich.java`（发送端）
**开关**：`sms_persist`（归档）/ `sms_numeric_only`（仅纯数字号码写入收件箱）/ 写系统开关（流转页）
**计数器**：`cnt_sms` / `cnt_sms_inject` / `cnt_sms_skip`

---

## 功能④ 来电在线接听（✅）

**原理**：反编译确认来电在发送端有两条流转路径：
- ① 广播链路：系统来电广播 → `NotifTransReceiver.buildSbnFromIntent()`（extras 打
  `notification_from_broadcast=true`）→ `buildPlainMessage()` 判 voip →
  接收端 `CallHandler.sendVoipBroadcast()` **全屏接听**。锁屏时正常；亮屏时无广播 → 没有全屏。
- ② 通知链路：InCallUI 来电通知 → `onNotificationPosted()` → `isDeviceSupported()`
  → `buildPlainMessage()` 判 msg → 接收端 **通知卡片**。

模块：hook `buildPlainMessage`，对来电通知强制打 voip 广播标记（双保险：通知链路也走全屏接听），
来电在亮屏/锁屏都以**全屏接听**形态流转。

**代码位置**：`hooks/HookCallRelay.java`
**计数器**：`cnt_call_relay`
**限制**：见文件头注释（V0.3.16 反编译结论），部分机型来电形态可能有差异。

---

## 功能⑤ 自动输锁屏密码（🔲 占位，P1）

**现状**：开关与密码存储已实现（`auto_unlock` + `auto_unlock_pwd`，明文，使用者自行填写）；
hook 为**占位实现**（只记录开关状态），功能待 P0 验证。

**候选实现路线（未定）**：
1. AccessibilityService 监听"输入锁屏密码"弹窗，自动填 Password 并点确认
2. LSPosed hook SystemUI 的解锁对话框，直接注入密码（需先定位 OS4 弹窗类）

**代码位置**：`hooks/HookAutoUnlock.java`

---

## 消息页（✅）

**数据源**：flow provider（小米端流转数据）+ 本地归档（本机历史）。

**去重规则（用户确认的方案）**：
1. 云端（小米端）有 → 本地隐藏，只显示云端
2. 本地有而云端没有 → 显示本地，标题加括号标注（`（本地）`）
3. 分组 key 去掉【分身】前缀 → 带/不带前缀进同一会话；组内同键（分钟级时间戳+正文）去重，
   优先保留带【分身】前缀那条

**排序**：KSU 式右上角下拉（分组 + 单选行）；默认从新到旧（`archive_sort`），
详情页排序默认新在前（`detail_sort`）。任何排序变更自增 `sortVersion` 强制列表/详情重算。

**代码位置**：`ui/MessagesScreen.kt`（列表）/ `ui/ConversationScreen.kt`（会话详情）
**状态标记**：`HFState.liveAvailable`（小米端最近是否有数据；false = 当前显示本地历史）

---

## 首页环境检测（✅ v0.5.3 修复）

**检测项（6 项）**：Root / KernelSU / LSP 框架 / 模块启用 / 作用域勾选 / 互联服务 milink 安装。

**检测方式**：一次 su 执行 Shell 脚本读 LSPosed 配置：
- `==LSP`：探测 lspd/lsposed/zygisk_lspd/ksu_lspd 等目录变体
- `==MODULES`：读 `modules.list`（旧版框架）
- `==SCOPE`：读 scope 目录文件（旧版）
- `==DB`：读 `modules_config.db`（LSPosed 1.9+，SQLite 二进制 grep 包名）
- `==RUNNING`：读运行态时间戳（`/data/adb/hyperflowplus/xposed_loaded` 双路径）

**判定（v0.5.3 起）**：**配置态即绿**——db/scope/modules.list 命中模块即绿（3），未命中红（0）；
运行态时间戳只用于**顶部黄条提示**"已启用但尚未生效，请重启"。

**为什么 v0.5.3 改判定**：运行态时间戳由 milink（非 root）进程写入，`/data/adb` 与 App 私有目录
都无权限 → 时间戳永远写不成功 → 之前要求"配置态 && 运行态"导致用户明明勾选也永远不绿（爆红）。

**缓存防跳变**：检测结果缓存到 SharedPreferences，进首页先显缓存（避免红→绿跳变难看），
后台重查后更新。

**UI 三态**：绿大对勾 / 黄叹号"查看更多"（点击弹窗列出未通过项，红色标注 + "去解决"步骤）/
无 root 时红卡"请授予 root 权限"（其他项不显示——没 root 也查不到）。

**代码位置**：`ui/HomeScreen.kt`

---

## 引导页（✅）

**流程**：协议确认（勾选框 + 我知道了）→ 酷安关注 → 环境检测。
- 退出引导 = 直接关闭应用
- 设置页"关于"区域**连点 3 次"HyperFlow"** 可重新进入引导
- 引导弹窗位置/样式是**全 App 弹窗的基准**（见下方弹窗体系）

**代码位置**：`ui/OnboardingScreen.kt`

---

## 主题：悬浮导航栏 + 液态玻璃（✅ v0.5.3 重构）

**结构（InstallerX Revived 同款，v0.5.3 根因级修复）**：
- 悬浮胶囊放 **Scaffold 的 bottomBar 槽**（内容层之外）
- backdrop 由胶囊内部 **`rememberLayerBackdrop()` 自捕获**（捕获窗口层内容，非 App 子树）
- `glassEnabled`（液态玻璃开关）→ `LiquidGlass`；关闭 → 普通胶囊
- 悬浮 + 玻璃**可同时开启**，且不闪退

**为什么之前闪退**：v0.5.1 悬浮胶囊嵌在内容 Box 内 + 外层 BarBlurHost 的 backdrop 捕获
`drawContent()` 把胶囊自身也卷进去 → 自捕获递归渲染崩溃（且状态持久化 → 每次启动都崩）。
v0.5.2 的临时方案（悬浮时玻璃降级 Blur）不崩但玻璃失效；v0.5.3 换成 InstallerX 原版结构后
两者都成立。

**代码位置**：`ui/FloatingBottomBar.kt`（悬浮胶囊本体，含导航项/拖动手势底座）、
`ui/LiquidNavBar.kt`（悬浮/普通导航栏切换）、`ui/liquid/`（LiquidGlass 折射效果：
CombinedBackdrop / Lens / Vibrancy / InnerShadow）、`MainActivity.kt`（Scaffold 装配）

**开关**：`nav_float`（悬浮导航栏）/ `glass_effect`（液态玻璃）

---

## 在线更新（✅）

**App 内"检测更新"**：
1. 多通道并行 GET `update.json`（ghproxy.net / ghfast.top / gh-proxy.com / raw 兜底）
2. 取 versionCode 最大者（防镜像缓存旧版误判"已是最新"）
3. 弹"更新日志"窗（版本号 + 可滚动 changelog + 取消/更新）
4. 更新 → 系统浏览器下载 zip（App 内下载进度不可见 → 用浏览器）

**KSU 侧更新**：模块列表检查更新（读 module.prop updateJson）→
主页"新版本可用"卡片（点击弹 changelog）→ 详情页"更新"按钮直接下载（KSU 源码行为，不弹日志）。

**代码位置**：`MainActivity.kt`（`checkUpdate`）/ `Config.updateJsonUrls()`
**元数据**：仓库根 `update.json`（version/versionCode/zipUrl/changelog，python json.dumps 维护，保证合法 JSON）

---

## 弹窗体系（✅）

**基准**：引导页弹窗（用户认可的样式）——浅黑卡片、标题正文居中、底部按钮区
（左白 cancel / 右蓝 confirm）。

**实现**：全部用 Compose `Popup`（锚定组件 + 智能移位），**不用系统 Dialog**（曾闪退）。

**智能移位**：开启悬浮/液态玻璃时弹窗上移 88dp（避开悬浮胶囊），否则上移 40dp
（避免被底部导航栏遮挡）。全局常量 `com.hyperflowplus.ui.smartInset`。

**倒计时**：`ConfirmDialog` 支持 `countdownSec`——确认按钮倒计时内不可点
（短信写系统 10 秒；引导协议"我知道了"同理）。

**代码位置**：`ui/HyperDialog.kt`（弹窗壳）/ `ui/ConfirmDialog.kt`（确认/倒计时弹窗）/
`MainActivity.kt`（smartInset 计算）

---

## 调试模式（✅ 基础版）

- 设置页"调试"开关：开启后记录详细日志
- 可抓取 logcat（过滤 hyperflow/milink/LSPosed 关键词）
- 首页环境检测失败时 `/data/adb/hyperflowplus/detect.log` 保存原始检测输出，便于排查

**代码位置**：`ui/SettingsScreen.kt` / `ui/HomeScreen.kt`
