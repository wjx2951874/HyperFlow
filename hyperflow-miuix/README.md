# HyperFlow

**小米澎湃 OS（HyperOS）互联通知流转增强模块** —— KernelSU / APatch / Magisk 模块 + LSPosed 插件 + Miuix(Compose) 管理 App

作者：酷安@[翰德姆](https://www.coolapk.com/u/4112338) · **正式版 1.1.0**（versionCode 201，2026-11-01 发布）

> **版本状态：稳定版。** v0.4.x ~ v1.1.0-beta 为测试迭代，自 **1.1.0（versionCode 201）** 起为正式版；
> beta61 ~ beta72 的全部改进已收口进该版本，详见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)。
>
> 最新构建：GitHub Actions → `HyperFlow-flashable.zip`（CI 产物）；更新通道默认走稳定版，
> Beta 通道需在 App 设置页连点版本行 10 次解锁。
---

## 这是什么

HyperOS 的「小米互联 / 妙享桌面」通知流转**默认只在锁屏时把通知流转到其他设备**。HyperFlow 通过 Xposed hook 掉小米互联服务（`com.milink.service`）内部的流转判定，实现：

- **亮屏也流转**（等效官方"可流转"状态）
- **分身（999 空间）微信/QQ 的通知也流转**，并且点击分身通知能直接拉起 999 空间的多开应用
- **流转短信在本机持久化**：App 内归档可随时回看，可选写入系统收件箱
- **来电在线接听**：锁屏/亮屏来电都以全屏接听形态流转
- **远控授权免输**：锁屏/授权盘密码自动捕获与自动填充，验证通过自动关闭授权 UI
- 配套 **Miuix 风格管理 App**：首页环境检测、消息页、流转功能开关、主题（悬浮导航栏 + 液态玻璃）、在线更新

## 功能总览（v1.1.0）

| 功能 | 说明 | 代码入口 |
|---|---|---|
| ① 亮屏流转 | 亮屏时强制放行通知流转 | `hooks/HookForceTransfer.java` |
| ② 分身流转 | 999 空间微信/QQ 通知也流转，标题加【分身/自定义前缀】 | `hooks/HookCloneBypass.java` |
| ②-2 点击只开分身 | 点分身通知只拉 999 空间应用，不连带主应用：在 `com.xiaomi.mirror` 进程源头接管用户判定，并对自建 u0 PendingIntent 就地换成 999 | `hooks/HookMirrorCloneOpen.java`（修复） / `hooks/HookMirrorObserve.java`（只读观测） |
| ③ 短信持久化 | 流转短信归档到 App 消息页；可选写入系统收件箱 | `hooks/HookSmsPersist.java` / `hooks/HookSmsSenderEnrich.java` |
| ④ 来电在线接听 | 来电以全屏接听形态流转（亮屏/锁屏均可） | `hooks/HookCallRelay.java` / `hooks/HookCallVoipType.java` / `hooks/HookCallFilterPass.java` |
| ⑤ 远控授权自动填 | 锁屏解锁时捕获凭证（system_server `checkCredential` 主通道）→ 授权盘自动填、不再弹授权盘 | `hooks/HookKeyguardCapture.java` / `hooks/HookTrustAuth*.java` / `CapturedCred.java` |
| ⑤-2 授权 UI 自动关闭 | 验证成功自动 finish 授权界面 | `hooks/HookTrustAuthUiClose.java` |
| 环境检测 | 首页环境检测（Root/KSU/LSP/模块/作用域/互联服务），三态卡 | `ui/HomeScreen.kt` |
| 消息页 | 云端优先 + 本地补显（括号标注）、排序下拉、会话详情、搜索 | `ui/MessagesScreen.kt` / `ui/ConversationScreen.kt` |
| 主题 | 悬浮导航栏 + 液态玻璃（InstallerX 同款结构，可同时开启） | `ui/FloatingBottomBar.kt` / `ui/LiquidNavBar.kt` / `ui/liquid/*` |
| 在线更新 | App 内多通道检测 + 浏览器下载；KSU 模块列表可更新 | `MainActivity.kt`（`checkUpdate`） |

## 架构一句话

**一个 KernelSU 模块**（刷入 `service.sh` + 管理 App）承载 App；**App 进程**负责 UI 与配置读写（su）；**`com.milink.service` 进程**被注入通知流转 / 来电形态 / 短信持久化 hook；**`com.xiaomi.trustservice`** 注入授权盘自动填充与自动关闭；**`com.xiaomi.mirror`** 注入分身点击的源头修复；**`com.android.incallui`** 注入亮屏来电 Voip 通知；**system_server**（`android` / `system` 作用域）经 `onSystemServerStarting` 注入锁屏凭证捕获（`HookKeyguardCapture`，自 beta66 起是该进程唯一保留的 hook）。完整装配见 [`XposedEntry.java`](app/src/main/java/com/hyperflowplus/XposedEntry.java)。

详见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)（进程图、数据流、设计决策）。

## 安装

1. **刷入模块**：KernelSU/APatch 管理器 → 安装 `HyperFlow-flashable.zip`（自动把 App 装为系统应用）
2. **启用 Xposed 模块**：在管理器中启用 **HyperFlow**，作用域勾选这 6 项（模块自带预勾选，也可用 App 内「一键启用」自动写）：
   - `com.milink.service`（小米互联服务：通知流转/来电形态/短信，核心）
   - `com.xiaomi.trustservice`（妙享直通授权盘：远控免密）
   - `com.xiaomi.mirror`（**点击只开分身**的修复所在进程，漏勾会串号）
   - `com.android.incallui`（亮屏来电流转，漏勾则只有锁屏来电会流转）
   - `android` / `system`（系统框架：锁屏凭证捕获。这两个是同一件事的两种框架标识，
     经典 LSPosed 勾 `android`，Vector/JingMatrix 勾 `system`，不确定就两个都勾）

   > 本 App（`com.hyperflowplus`）无需勾选——它自己不需要被 hook。
3. **授权 Root**：KernelSU 授权 `com.milink.service` 与 `com.hyperflowplus` Root 权限（`service.sh` 会自动写入白名单）
4. **重启设备**，打开 App 完成首次引导

> 更新方式：KernelSU 模块列表检查更新（走 GitHub Release）；或 App 内"设置 → 版本与更新"（beta 通道走 `beta` 分支 update.json）。

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

- **当前版本：1.1.0 正式版**（versionCode 201）
- 本轮（beta61 ~ beta72）重点工作：
  - **beta70 —— 点击分身通知不再串号**：根因是 mirror 进程里「把应用搬到投屏屏」的逻辑只按主空间用户（0）查任务，
    于是永远查到主微信窗口并搬上来；改在该逻辑源头接管用户判定。旧的 system_server 拦截方案（`HookRemoteOpen`）已在 beta66 删除。
  - **beta71 / beta72 —— 短信排序**：排序算法原来是两毫秒时间戳相减后强转 `Int`，相差超过约 24.86 天即溢出翻负号；
    改为全量长整数比较，并补齐时间格式的解析、剔除畸形时间戳。
  - **beta59 / beta60 / beta58**：亮屏来电 Voip 通知、锁屏凭证捕获修正类名路径、授权 UI 自动关闭。
  - **beta57**：迁移新式 libxposed 架构 + `onSystemServerStarting` 注入通道。
- 完整历史见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)（1.1.0 详版日志见 `docs/changelog-latest.md`），
  待办与验证结论见 [`docs/ROADMAP.md`](docs/ROADMAP.md)，技术实现见 [`docs/FEATURES.md`](docs/FEATURES.md)。

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
