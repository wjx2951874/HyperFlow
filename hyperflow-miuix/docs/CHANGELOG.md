# HyperFlow 更新日志

> **版本状态**：v0.4.x ~ v0.5.x 全部为**测试版（Beta）**，正式版（Stable）尚未发布；
> 正式版发布时本日志会单独开 v1.x 一节并标注。
> 按版本倒序。每条含：改动内容、修复原因（为什么这么改）、遗留待验证项。
> 版本号与 versionCode 同步：v0.5.x 系列 = 80/81/82/83（v0.4.4x = 76/77/78，v0.4.3x = 74/75…）。

---

## v0.5.3（versionCode 83）— 2026-10-02

- **悬浮 + 玻璃共存（第三版，InstallerX 原版结构，根因级修复）**：
  对照 InstallerX Revived 真实源码（MiuixNavWrapper.kt + MiuixSettingsPage.kt + FloatingBottomBar.kt）——
  悬浮栏必须放 Scaffold 的 **bottomBar 槽**（内容层之外），backdrop 由**胶囊内部 `rememberLayerBackdrop()` 自捕获**
  （捕获窗口层内容，非 App 子树）。MainActivity 重构：删除 BarBlurHost 包裹、删除内容 Box 的 layerBackdrop 依赖、
  悬浮胶囊移入 bottomBar 槽、LiquidNavBar 悬浮分支 `glassEnabled → LiquidGlass`（不再降级 Blur）。
  悬浮 + 玻璃**可同时开启**且不再闪退。
  - 编译坑：删 BarBlurHost 开头时残留两处闭合 `}` → Scaffold 后多一个 `}` 把 MiuixTheme 提前闭合 →
    `showUpd/updPhase` 等全部 Unresolved reference（表现为变量"消失"）；删除多余闭合后构建通过。
- **LSP 检测爆红（第四版，配置态即绿）**：
  前版要求"配置态 && runtimeOk"才绿，但 `xposed_loaded` 时间戳由 milink（**非 root**）进程写入，
  `/data/adb` 与 App 私有 files 均无权限 → runtimeOk 恒 false → 用户明明勾选却永远不绿/黄。
  修复：模块/作用域检测**只看配置态**（modules.list / scope / modules_config.db 命中即绿），
  runtimeOk 提升为 composable 状态，仅用于顶部黄条"已启用但未生效请重启"。
- **分身双通知（第三版，跨进程去重）**：
  RELEASED_KEYS 原为每进程静态表，双链路若发生在不同进程（milink/dist 等）拦不住。
  升级：key 改为**内容指纹**（包名|id|tag|标题|正文前 96 字符）、窗口 20s；
  同步写跨进程共享文件（`/data/adb/hyperflowplus/released_keys` 等候选路径，取第一个可写的）；
  `isDuplicate()` 先查内存表再读共享文件。App 检测/未来清理逻辑可读该文件。
- **KSU 更新日志（结论固化）**：KSU 侧"点模块更新按钮直接下载不弹日志"是 KSU 源码交互
  （主页"新版本可用"卡片点击才弹 changelog）——模块无法改变；App 内"检测更新"弹窗始终显示完整 changelog。
- 待验证：悬浮+玻璃同时开启不闪退且玻璃可见（重点）、分身只出一条（重点）、点击【分身】开 999、LSP 勾选即绿。

## v0.5.2（versionCode 82）— 2026-10-02

- **悬浮+玻璃同时开启闪退（v0.5.1 回归）**：根因=悬浮胶囊在内容页内，BarBlurHost 的 backdrop 捕获
  drawContent() 包含悬浮胶囊自身 → **自捕获递归渲染崩溃**。修复=悬浮时玻璃降级为 Blur 模糊胶囊
  （临时方案，v0.5.3 已根治为 InstallerX 结构）。
- **分身双通知（第二版）**：v0.5.1 的 RELEASED_KEYS 去重检查放在 999 分支之后，999 通知直接 return
  绕过查重 → 双链路两条都放行。修复=dupKey 检查+记录**提前到所有放行分支之前**。
- **检测爆红（LSP 相关）**：补全 `==DIR` 探测路径（zygisk_lspd/lspd/ksu_lspd/ksu_lsposed 等
  KernelSU 内嵌 LSPosed 变体）；配置态 OK 但运行态未加载 → 黄色"已启用但尚未生效，请重启"提示。
- **KSU 更新日志排查（源码确认）**：KernelSU 原版与 KernelSU-Next 均读 update.json 的
  version/versionCode/zipUrl/changelog，字段全部匹配——**结论**：KSU 只在点击"新版本可用"卡片时弹日志窗，
  模块页"更新"按钮直接下载不弹日志（KSU 侧交互，模块无法控制）。

## v0.5.1（versionCode 81）— 2026-10-02

- **分身双通知根因确认（用户实测纠正）**：主空间实际没收到消息，两条通知是同一个 999 分身消息
  走了小米互联**两条流转链路**（一条过 hook 带【分身】前缀、另一条原样流出）。v0.5.0 误加
  "拦截主空间 user0"方案已撤销。
- **修复=流转侧同 key 去重 + 消息页兜底去重**：HookCloneBypass 加 RELEASED_KEYS（包名|id|tag → 时间戳）
  15s 窗口去重；MessagesScreen 分组 key 去【分身】前缀（带/不带前缀进同一会话），组内同键
  （分钟级时间戳+正文）去重，优先保留带【分身】前缀那条。
- **点击分身通知开错应用修复**：HookRemoteOpen 重写——ALLOWED_CALLERS={milink, 本 App}、
  EXCLUDED_CALLERS={桌面/SystemUI/android}；无 userId 参数重载吞掉原调用，改用
  IActivityTaskManager.startActivityAsUser 11 参重载以 user 999 重发。
- **LSP 检测全红修复**：运行态标记双路径——`/data/adb/hyperflowplus/xposed_loaded`（root 进程可写）
  + `/data/user/0/com.hyperflowplus/files/xposed_loaded`（App 自身进程，勾选本 App 即可写）；
  `==RUNNING` 段读双路径取最大时间戳，48h 内命中即 runtimeOk。
- **KSU 更新无日志**：module.prop updateJson 去掉 `?v=0441` 缓存参数（KSU 一直拉旧缓存/旧非法 JSON）。

## v0.5.0（versionCode 80）— 2026-10-02

- **修复启动白屏闪退（v0.4.43 重大回归）**：根因=自建 RoundedIcons 手写字符串解析器，
  material-icons path 数据中命令字母紧贴数字（如 "12,2C6.48,2"）且含 S/s 平滑命令，
  `toFloat()` 抛 NumberFormatException → 首帧渲染崩溃。修复=改用官方 path DSL 手写节点
  （moveTo/curveTo/lineTo 字面量），S/s 反射控制点手动折算为绝对 curveTo。
- 本版同时含 v0.4.43 全部改动（见下）。

## v0.4.43（versionCode 78）— 2026-10-02

- **分身流转回退「只放行」**：HookCloneBypass 删系统镜像/归一——分身(999)通知原样放行流转
  （带【分身】前缀），主空间不再重复显示分身通知。
- **点击分身通知 → 打开 999**：新建 HookRemoteOpen（system_server hook startActivity/startActivityAsUser），
  条件=调用者 milink + 目标包 ∈ {微信, QQ} + userId==0 + `/data/user/999/<pkg>` 存在 → 末尾 userId 改 999。
  **需 LSP 作用域勾选 android**。
- **悬浮导航栏换 InstallerX 原版 FloatingBottomBar**（液态玻璃/模糊/普通三态）：整段移植
  FloatingBottomBar.kt + DampedDragAnimation.kt + InteractiveHighlight.kt + DragGestureInspector.kt
  （GPL-3.0 同源）；LiquidNavBar 删自绘三层简化版（曾悬浮空白/点击消失/闪退）。
- **首页检测三态**：无 Root=红色「请授予 Root 权限」卡（其他项不显示）；全绿=圆环对勾大卡；
  部分未授权=黄色圆环叹号卡「查看更多」→ 弹窗红标未成功项 + 「去解决」→ 步骤弹窗。
- **LSP 检测运行态双保险**：XposedEntry 被加载时写时间戳；detect() 读 `==RUNNING` 段，
  最近 48h 加载过才算生效——框架关闭/去作用域不再误判"环境正常"。
- **澎湃 OS 版本串补全**：su 脚本加 ro.build.display.id / ro.miui.ui.version.name / incremental。
- **图标体积优化**：自建 RoundedIcons.kt（三枚圆环 ImageVector），移除 material-icons-extended 全量库
  ——APK 从 47MB 降到 25MB（v0.5.0 起改用官方 path DSL 手写节点修复解析崩溃）。

## v0.4.42（versionCode 77）— 2026-10-02

- 弹窗改为**窗口层 Popup**（与引导页同层级），不再被悬浮/导航栏遮挡；开启悬浮/玻璃时自动再上移
  （smartInset 88dp/40dp）。
- 液态玻璃 backdrop 只捕获内容页（`Box.layerBackdrop` + isLayerBackdrop 判断），悬浮开启不再空白。
- 首页环境检测改 6 项（Root/KSU/LSP/模块/作用域/互联），全绿折叠为小米风格大对号；先显缓存防红绿跳变。
- 修复机型显示错误（dev[3]=marketname、dev[4]=ksud）；澎湃 OS 行加完整版本 code。
- 消息排序默认 time_desc；会话页加返回箭头 + 排序点击直接 toggle。

## v0.4.41（versionCode 76）— 2026-10-02

- 三层液态玻璃（基础层+透明捕获层+组合折射指示器，InstallerX/KernelSU 同源），修复开启空白/无玻璃。
- LSP 检测修复：新版 LSPosed 启用状态在 SQLite `modules_config.db`（modules 表 enabled），
  脚本加 `==DB/==HF_IN_DB` 查询。
- 消息去重：云端（小米互联）优先，本地重复隐藏；本地独有加（本地）标注。
- 消息排序：右上角 Sort 下拉（发送人/时间 + 倒序），Popup 锚定不闪退。
- 设备信息拆分：机型大框 + 型号/澎湃OS/Android/内核/Root 小卡。
- 更新：App 下载调系统浏览器（通知栏进度）；update.json changelog 8 行。
- 弹窗 bottomInset 40dp 贴底，引导退出关应用，移除"由衷感谢"字样。

## v0.4.40 — 2026-10-02

- 悬浮液态改单层稳定版（弃用 combinedBackdrop/layerBackdrop 组合捕获，此前部分设备开启悬浮底部空白）。
- LSP 检测 find 全盘遍历 modules.list/scope（覆盖全部变体路径）。
- 弹窗位置贴底（悬浮 56/普通 24dp，学引导页遮住导航栏一部分——用户最终要求）。
- 引导「退出」直接关应用；移除"由衷感谢"字样。

## v0.4.39 — 2026-10-02

- **更新链路全面修复**：App 检测改**收集制**（并行收集所有通道结果取 versionCode 最大，
  此前先到先得，镜像缓存旧版误报"已最新"）；update.json 全字段同步修复
  （此前 versionCode 停在 71/zipUrl 指旧版/changelog 旧内容——KSU 无日志+下载失败根因）；
  KSU 通道换 ghfast.top + 时间戳参数；检测弹窗显示命中通道域名。

## v0.4.38 — 2026-10-02

- 悬浮液态移植 InstallerX/KernelSU 同源三层结构（基础层/透明捕获层/指示器层），光效=vibrancy+blur+lens+innerShadow+高光。
- 弹窗全部上移（悬浮 160dp/普通 120dp）；引导页深色改浅黑；HyperDialog 显隐修复。

## v0.4.37 — 2026-10-01

- 悬浮液态稳定版（弃复杂绘制链改 Miuix 组件+玻璃背景）；LSPosed 检测 6 路径+模糊匹配+detect.log；
  启动 Toast 报上次闪退。（未实际装机，已被 v0.4.38 替代）

## v0.4.36 — 2026-10-01

- **KSU 在线更新通道修复**：updateJson 换 ghproxy.net（ghfast.top 部分国内网络不可达）；
  App 检测到新版本点「更新」→「选择更新方式」弹窗（直接下载 / 去 KSU 检测）；
  直接下载多通道并行，下载完成提示到 KSU 本地安装；update.json 内嵌 changelog 一次请求秒加载。

## v0.4.35 — 2026-10-01

- **液态玻璃根因修复**：BarBlurHost 移至 Scaffold 外层使 backdrop 传达到 bottomBar
  （此前 bottomBar 读不到 backdrop，玻璃从未真正启用）。
- 首页环境检测拆 6 个独立小块 + 汇总行；KernelSU 并入 Root 项；删"重新授权 Root"。
- 悬浮栏恢复开源原版（Miuix FloatingNavigationBar / HyperIsland 液态胶囊），悬浮不挤占内容区。
- 弹窗上移（普通 88dp/悬浮 120dp）+ 深色模式浅黑卡片。
- LSPosed 检测修正：modules.list 按包名匹配、作用域检测 scope/com.hyperflowplus 文件。
- 关于页去 AI 辅助字样。

## v0.4.34 — 2026-10-01

- **在线更新全链路国内可达修复**（用户实测：检测超长 + 误报 0.4.31 最新 + KSU 下载失败）：
  ① App 检测并行化（最快 5s 出结果）；
  ② **根因：jsDelivr 对 update.json 有 12h+ 陈旧缓存且 `?v=` 时间戳参数无效** → 已从通道移除；
  ③ App 通道改 GitHub 国内加速镜像（ghfast.top / gh-proxy.com / ghproxy.net）+ raw 兜底；
  ④ module.prop updateJson 改 ghfast 代理 raw；⑤ zipUrl 改 ghfast 代理 Release（jsDelivr 对 16MB zip 不稳已弃）；
  ⑥ 更新弹窗改 KSU「更新日志」样式（版本号 + 可滚动 changelog + 取消/更新）。

## v0.4.33 / v0.4.32 — 2026-10-01

- **v0.4.32**：①环境检测重做——读 LSPosed 真实配置（==LSP/==MODULES/==SCOPE 四路径）识别
  "模块已启用/作用域已勾选"，Miuix 绿勾/红叉状态图标，点击未通过项进引导新页一键修复；
  引导页 协议→酷安关注→反馈 3 阶段；设置-关于连点 3 次重开引导。
  ②液态玻璃专项（参考 HyperIsland Apache-2.0）：LiquidNavBar 四模式（普通/悬浮/普通+液态/悬浮+液态），
  blur+lens(AGSL RuntimeShader 色散)+vibrancy(饱和度)+双光源高光，选中指示器独立液态渲染；
  BarBlurHost 捕获全页 backdrop；设置-主题两开关（悬浮导航栏/液态玻璃）。
- **v0.4.33**：衔接版本（构建细节省略）。

## v0.4.31 — 2026-10-01

- **弹窗全面切换覆盖层方案（HyperDialog，修复全部闪退）**——此前凡与弹窗相关的操作均闪退
  （写入系统短信、排序、检测更新等），换 Popup 覆盖层方案后全量稳定。
- 引导页协议卡限高滚动 + 按钮固定；启动秒加载（缓存快照）。
- 4 Tab 导航（首页环境检测红黄绿 / 流转 / 消息 / 设置）。
- 设置页重构（主题/调试/关于）；完整机型显示。

## v0.4.30 — 2026-09-30

- 关闭 App 内消息勾选框两段式确认；写入系统短信弹窗标题带"开启" + 10s 倒计时。
- 遗留：用户实测"凡弹窗相关均闪退" → 触发 v0.4.31 弹窗重构。

---

## 历史版本要点（v0.4.29 及更早）

- **v0.4.x 早期**：纯 Java hooks 构建链 → v0.4.0 起 UI 迁移 Miuix(Compose)；
  短信持久化、来电在线接听、亮屏流转逐步稳定。
- **V0.3.x**：libxposed API 101/102 迁移（XposedEntry 入口）；配置改 `/data/adb` 全局 JSON，
  弃 HTTP WebUI（V0.2.6 起）；发送端补号 HookSmsSenderEnrich（V0.3.14）。
- **V0.2.x**：hook 从 XposedBridge 迁移 libxposed；自动输密码开关占位（P1）。
