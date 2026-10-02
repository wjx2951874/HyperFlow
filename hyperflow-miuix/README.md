# HyperFlow

**小米澎湃 OS（HyperOS）互联通知流转增强模块** —— KernelSU / APatch / Magisk 模块 + LSPosed 插件 + Miuix(Compose) 管理 App

作者：酷安@[翰德姆](https://www.coolapk.com/u/4112338) · 测试版持续迭代中（当前 v0.5.3）

> **⚠️ 版本状态：当前所有版本均为测试版（Beta）**。v0.4.x ~ v0.5.x 全部属于测试迭代，
> **正式版（Stable）尚未发布**，正式版版本号（v1.x）与发布时间未定，届时会在此处与
> `docs/CHANGELOG.md` 明确标注。

---

## 这是什么

HyperOS 的「小米互联 / 妙享桌面」通知流转**默认只在锁屏时把通知流转到其他设备**。HyperFlow 通过 Xposed hook 掉小米互联服务（`com.milink.service`）内部的流转判定，实现：

- **亮屏也流转**（模拟锁屏，等效官方"可流转"状态）
- **分身（999 空间）微信/QQ 的通知也流转**，并且点击分身通知能直接拉起 999 空间的多开应用
- **流转短信在本机持久化**：App 内归档可随时回看，可选写入系统收件箱
- **来电在线接听**：锁屏/亮屏来电都以全屏接听形态流转
- 配套 **Miuix 风格管理 App**：首页环境检测、消息页、流转功能开关、主题（悬浮导航栏 + 液态玻璃）、在线更新

## 功能总览（v0.5.3）

| 功能 | 说明 | 代码入口 |
|---|---|---|
| ① 亮屏流转 | 亮屏时强制放行通知流转（模拟锁屏） | `hooks/HookForceTransfer.java` |
| ② 分身流转 | 999 空间微信/QQ 通知也流转，标题加【分身】前缀 | `hooks/HookCloneBypass.java` |
| ②-2 点击拉 999 | 点击分身通知 → 直接打开 999 空间微信/QQ | `hooks/HookRemoteOpen.java` |
| ③ 短信持久化 | 流转短信归档到 App 消息页；可选写入系统收件箱 | `hooks/HookSmsPersist.java` / `hooks/HookSmsSenderEnrich.java` |
| ④ 来电在线接听 | 来电以全屏接听形态流转（亮屏/锁屏均可） | `hooks/HookCallRelay.java` |
| ⑤ 自动输锁屏密码 | P1 开发中（占位实现） | `hooks/HookAutoUnlock.java` |
| 环境检测 | 首页 6 项检测（Root/KSU/LSP/模块/作用域/互联服务），三态卡 | `ui/HomeScreen.kt` |
| 消息页 | 云端优先 + 本地补显（括号标注）、KSU 式排序下拉、会话详情 | `ui/MessagesScreen.kt` / `ui/ConversationScreen.kt` |
| 主题 | 悬浮导航栏 + 液态玻璃（InstallerX 同款结构，可同时开启） | `ui/FloatingBottomBar.kt` / `ui/LiquidNavBar.kt` / `ui/liquid/*` |
| 在线更新 | App 内多通道检测 + 浏览器下载；KSU 模块列表可更新 | `MainActivity.kt`（`checkUpdate`） |

## 架构一句话

**一个 KernelSU 模块**（刷入 `service.sh` + 管理 App）承载 App；**App 进程**负责 UI 与配置读写（su）；**`com.milink.service` 进程**被 LSPosed 注入 5 个通知流转 hook；**system_server 进程**（`android` 作用域）被注入 1 个"点击分身通知拉 999"的 hook。

详见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)（进程图、数据流、设计决策）。

## 安装

1. **刷入模块**：KernelSU/APatch 管理器 → 安装 `HyperFlow-x.y.z-flashable.zip`（自动把 App 装为系统应用）
2. **启用 LSPosed 模块**：LSPosed 中启用 **HyperFlow**，作用域建议勾选（模块自带预勾选）：
   - `com.milink.service`（小米互联服务，核心）
   - `com.hyperflowplus`（本 App，用于运行态标记）
   - `android`（system_server，点击分身通知拉 999 需要）
3. **授权 Root**：KernelSU 授权 `com.milink.service` 与 `com.hyperflowplus` Root 权限（`service.sh` 会自动写入白名单）
4. **重启设备**，打开 App 完成首次引导

> 更新方式：KernelSU 模块列表检查更新（走 GitHub Release）；或 App 内"设置 → 版本与更新"。

## 设备支持

- 已测试：Redmi Note 12 Turbo（marble）/ 澎湃 OS 4 / Android 17 / KernelSU
- 需要：澎湃 OS 2+（HyperOS 互联）、**接收端为 root 设备**（发送端无需 root）
- 仅同一小米账号下的设备之间流转；数据不经过第三方服务器

## 文档索引

- **[`HANDOFF.md`](HANDOFF.md) —— 接手引导（给下一个 AI 工具 / 开发者，读完即可无缝接手）**
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) —— 整体架构与设计决策
- [`docs/FEATURES.md`](docs/FEATURES.md) —— 每个功能的实现原理 / 代码位置 / 为什么这么写 / 已知限制
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) —— 开发环境、构建、发布流程
- [`docs/ROADMAP.md`](docs/ROADMAP.md) —— ✅已实现 / ⏳待验证 / 🔲规划中 完整清单
- [`docs/CHANGELOG.md`](docs/CHANGELOG.md) —— 完整更新日志

## 开发状态

- 当前版本：**v0.5.3**（versionCode 83）
- v0.5.3 重点：悬浮导航栏 + 液态玻璃重构为 InstallerX 同款结构（可同时开启、修复自捕获闪退）；LSP 检测改为配置态即绿；分身双通知跨进程去重。
- 完整历史见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)，待办见 [`docs/ROADMAP.md`](docs/ROADMAP.md)

## AI 辅助生成声明

本项目由 AI 辅助开发、调试与文案撰写，并经人工逐项验证与迭代；**并非完全由 AI 生成**。若你发现任何问题，欢迎在酷安向作者反馈。

## 开源致谢与许可

构建于以下开源项目之上，特此致谢（完整许可列表见 App 内"设置 → 关于 → 开源许可"）：

- **[Miuix](https://github.com/compose-miuix-ui/miuix)**（Apache-2.0）—— Compose Multiplatform UI 组件库（HyperOS 原生质感）
- **[KernelSU](https://github.com/tiann/KernelSU)**（GPL-3.0）—— 内核级 Root 与模块机制
- **[LSPosed](https://github.com/LSPosed/LSPosed)**（GPL-3.0）—— Xposed 框架与 libxposed API
- **[InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived)**（GPL-3.0）—— 悬浮导航栏 / 液态玻璃结构参考（`Scaffold bottomBar` + `rememberLayerBackdrop` 方案）

各开源项目版权归其原作者所有，请遵守对应许可证条款。

## 责任声明

- 本模块仅供个人设备调试学习使用，请遵守各软件服务条款
- 流转数据仅在同一小米账号设备间传输，不经过第三方服务器
- 使用过程中遇到问题，欢迎酷安反馈：[@翰德姆](https://www.coolapk.com/u/4112338)
