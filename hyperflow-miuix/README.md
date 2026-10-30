# HyperFlow

**小米澎湃 OS（HyperOS）互联通知流转增强模块** —— KernelSU / APatch / Magisk 模块 + LSPosed 插件 + Miuix(Compose) 管理 App

作者：酷安@[翰德姆](https://www.coolapk.com/u/4112338) · 测试版持续迭代中（当前 **1.1.0-beta60**，versionCode 188）

> **⚠️ 版本状态：当前所有版本均为测试版（Beta）**。v0.4.x ~ v1.1.0-beta 全部属于测试迭代，
> **正式版（Stable）尚未发布**，正式版版本号与发布时间未定，届时会在此处与
> `docs/CHANGELOG.md` 明确标注。
>
> 最新构建：GitHub Actions `beta` 分支出包 → `HyperFlow-flashable.zip`（CI 产物）。
---

## 这是什么

HyperOS 的「小米互联 / 妙享桌面」通知流转**默认只在锁屏时把通知流转到其他设备**。HyperFlow 通过 Xposed hook 掉小米互联服务（`com.milink.service`）内部的流转判定，实现：

- **亮屏也流转**（等效官方"可流转"状态）
- **分身（999 空间）微信/QQ 的通知也流转**，并且点击分身通知能直接拉起 999 空间的多开应用
- **流转短信在本机持久化**：App 内归档可随时回看，可选写入系统收件箱
- **来电在线接听**：锁屏/亮屏来电都以全屏接听形态流转
- **远控授权免输**：锁屏/授权盘密码自动捕获与自动填充，验证通过自动关闭授权 UI
- 配套 **Miuix 风格管理 App**：首页环境检测、消息页、流转功能开关、主题（悬浮导航栏 + 液态玻璃）、在线更新

## 功能总览（v1.1.0-beta60）

| 功能 | 说明 | 代码入口 |
|---|---|---|
| ① 亮屏流转 | 亮屏时强制放行通知流转 | `hooks/HookForceTransfer.java` |
| ② 分身流转 | 999 空间微信/QQ 通知也流转，标题加【分身/自定义前缀】 | `hooks/HookCloneBypass.java` |
| ②-2 点击拉 999 | 点击分身通知 → 打开 999 空间微信/QQ/飞书/钉钉/企业微信（发送端重建 999 PendingIntent + 远程 caller 直改，三层防线） | `hooks/HookCloneBypass.java` / `hooks/HookRemoteOpen.java` / `hooks/HookMirrorObserve.java` |
| ③ 短信持久化 | 流转短信归档到 App 消息页；可选写入系统收件箱 | `hooks/HookSmsPersist.java` / `hooks/HookSmsSenderEnrich.java` |
| ④ 来电在线接听 | 来电以全屏接听形态流转（亮屏/锁屏均可） | `hooks/HookCallRelay.java` / `hooks/HookCallVoipType.java` / `hooks/HookCallFilterPass.java` |
| ⑤ 远控授权自动填 | 锁屏解锁时捕获凭证（system_server `checkCredential` 主通道）→ 授权盘自动填、不再弹授权盘 | `hooks/HookKeyguardCapture.java` / `hooks/HookTrustAuth*.java` / `CapturedCred.java` |
| ⑤-2 授权 UI 自动关闭 | 验证成功自动 finish 授权界面 | `hooks/HookTrustAuthUiClose.java` |
| 环境检测 | 首页环境检测（Root/KSU/LSP/模块/作用域/互联服务），三态卡 | `ui/HomeScreen.kt` |
| 消息页 | 云端优先 + 本地补显（括号标注）、排序下拉、会话详情、搜索 | `ui/MessagesScreen.kt` / `ui/ConversationScreen.kt` |
| 主题 | 悬浮导航栏 + 液态玻璃（InstallerX 同款结构，可同时开启） | `ui/FloatingBottomBar.kt` / `ui/LiquidNavBar.kt` / `ui/liquid/*` |
| 在线更新 | App 内多通道检测 + 浏览器下载；KSU 模块列表可更新 | `MainActivity.kt`（`checkUpdate`） |

## 架构一句话

**一个 KernelSU 模块**（刷入 `service.sh` + 管理 App）承载 App；**App 进程**负责 UI 与配置读写（su）；**`com.milink.service` 进程**被 LSPosed 注入通知流转 / 来电形态 hook；**system_server 进程**（`android` / `system` 作用域）自 beta57 起经 `onSystemServerStarting` 注入分身点击拦截（HookRemoteOpen）、镜像启动观察（HookMirrorObserve）与锁屏凭证捕获（HookKeyguardCapture）；**`com.xiaomi.trustservice`** 注入授权盘自动填与自动关闭；**`com.xiaomi.mirror`** 注入镜像启动全路径观察。完整 hook 装配见 [`XposedEntry.java`](app/src/main/java/com/hyperflowplus/XposedEntry.java)。

详见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)（进程图、数据流、设计决策）。

## 安装

1. **刷入模块**：KernelSU/APatch 管理器 → 安装 `HyperFlow-flashable.zip`（自动把 App 装为系统应用）
2. **启用 LSPosed 模块**：LSPosed 中启用 **HyperFlow**，作用域建议勾选（模块自带预勾选）：
   - `com.milink.service`（小米互联服务：通知流转/来电形态，核心）
   - `com.hyperflowplus`（本 App，用于运行态标记）
   - `android` / `system`（system_server：分身点击拦截、镜像启动观察、锁屏凭证捕获）
   - `com.xiaomi.trustservice`（授权盘自动填与自动关闭）
   - `com.xiaomi.mirror`（镜像/远控进程：启动观察）
   - `com.android.incallui`（来电形态辅助）
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

- 当前版本：**1.1.0-beta60**（versionCode 188，commit fd2eff2，CI run 41051778338）
- beta60 重点：分身逻辑与 beta59 一致（三层防线）；锁屏凭证捕获主通道改为 `LockSettingsService.checkCredential`（重启后首次解锁必走，不再依赖授权盘手输）；授权 UI 自动关闭（beta58 已修）随包验收
- beta59 重点：双开根治三层防线——发送端把分身通知 contentIntent 重建为 userId=999 的 PendingIntent（点击直接开分身）、HookRemoteOpen 标记检查提前 + 远程 caller 直改 999、HookMirrorObserve 全路径观察
- beta57 重点：恢复新式 libxposed 架构 + `onSystemServerStarting` 注入 system_server 通道（对照 HyperModifier/Vector 机制），删 `assets/xposed_init` 防 legacy 误判
- 完整历史见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)（beta54 起详见 `docs/changelog-latest.md`），待办见 [`docs/ROADMAP.md`](docs/ROADMAP.md)

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
