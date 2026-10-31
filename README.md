# HyperFlow

一款增强小米澎湃互联体验的工具（Xposed / KernelSU 模块）。

让被系统拦截的跨设备通知、来电与短信在设备间顺畅流转：**亮屏强制流转**、**分身通知独立流转且点开不串号**、**妙享直通远控免输密码**、**流转短信 App 内归档**，并支持全屏消息搜索与一键在线更新。

> **当前版本**：正式版 **1.1.0**（versionCode 201）
> 工程源码与二次开发引导在 [`hyperflow-miuix/`](hyperflow-miuix/)（README / HANDOFF.md / docs 完整文档）。
> 历史技术资料（逆向分析、方案推演）已归档至 [`docs/dev/`](docs/dev/)，根目录 `docs/` 只保留面向用户的更新日志。

## 功能

| 功能 | 说明 |
|---|---|
| **亮屏流转** | 亮屏状态下被系统拦截的跨设备通知，强制放行流转到另一台设备 |
| **双开通知流转** | 微信 / QQ / 钉钉等分身应用通知独立流转，标题带可自定的【双开】前缀，跨进程去重 |
| **点击只开分身** | 在另一台设备点击分身通知，只会拉起 999 空间的分身，**不会连带把主微信/主应用一起拽起来** |
| **妙享直通免密** | 锁屏凭证捕获 + 远控授权盘自动填充，验证通过后自动关闭授权界面 |
| **来电流转** | 来电以 VoIP 形态流转到另一台设备，亮屏来电同样生效 |
| **短信归档** | 流转短信自动存入 App 消息页，可归档、排序、搜索、删除；可选写入系统短信收件箱（默认关） |
| **消息搜索** | 全屏搜索，结果按「会话 / 信息」分组，命中关键词高亮 |
| **环境检测** | Root / LSPosed / 模块作用域一键检测与引导 |
| **在线更新** | App 内一键检测更新，稳定通道 + Beta 通道（Beta 需在设置页连点版本行解锁） |

## 环境要求

- 小米澎湃 OS（HyperOS），已开启「互联互通 / 小米互联」
- Root 权限（KernelSU / APatch / Magisk 均可）
- Xposed 框架：LSPosed 2.2+，或 Vector / JingMatrix 分支（适配 libxposed API 102）

## 安装

1. 在 Root 管理器中刷入 `HyperFlow-flashable.zip`（App 会被预置为系统应用）；
2. 在 Xposed 管理器中启用 **HyperFlow** 模块，并勾选下列作用域：

| 作用域包名 | 作用 |
|---|---|
| `com.milink.service` | 小米互联服务（通知流转、来电、短信，核心） |
| `com.xiaomi.trustservice` | 妙享直通授权盘（远控免密） |
| `com.xiaomi.mirror` | 分身通知源头（点击只开分身） |
| `com.android.incallui` | 电话界面（亮屏来电流转） |
| `android` | 系统框架（锁屏凭证捕获，经典 LSPosed） |
| `system` | 系统框架（Vector / JingMatrix 分支走这一条） |

   > `android` 与 `system` 是同一件事的两种框架标识——你可以根据自己装的框架勾选，不确定就两个都勾。
   > 模块自身（`com.hyperflowplus`）无需勾选，已排除在推荐作用域之外。

3. **重启设备**；
4. 打开 App，按引导完成环境检测，在「流转 / 消息」页开启需要的开关。

> Root 授权：`service.sh` 会自动把 `com.milink.service` 与 `com.hyperflowplus` 写入白名单。
> 短信写入系统收件箱属于可选功能，默认关闭；开启后另一台设备也需要 Root + 本模块才能完整显示。

## 构建

**环境**：JDK 21 · Android SDK（compileSdk 37 / build-tools 36）· Gradle 9.x

```bash
# 编译 Release APK（在 hyperflow-miuix 目录下）
gradle :app:assembleRelease --no-daemon

# 产物
#   hyperflow-miuix/app/build/outputs/apk/release/app-release.apk
```

- **签名**：存在 `keystore.jks` 时使用 release 签名（别名/口令走环境变量 `HF_KEY_ALIAS` / `HF_KEY_PASS`），否则回退 debug 签名；
- **flashable zip**：由 GitHub Actions（`.github/workflows/build.yml`）在构建后自动组装（priv-app 预置 APK + service.sh + customize.sh + module.prop），`push main` 或打 `v*` tag 触发，产物发布到 Release；
- 版本号以 `ksu-module/module.prop` 为单一事实来源（`version` / `versionCode`），构建时同步写入 APK 与 `update.json`。

## 引用的库

| 库 | 用途 |
|---|---|
| [Miuix KMP](https://github.com/yukonga/miuix-kmp) 0.9.4（ui / preference / icons / blur / nav / squircle） | HyperOS 风格界面（顶部栏、设置项、图标、柔光玻璃、导航转场） |
| Jetpack Compose BOM 2025.06.01（ui / foundation / material-icons） | Compose UI 基础 |
| androidx.activity-compose 1.9.3 / core-ktx 1.13.1 / navigationevent 1.1.2 | Activity 集成 / 基础组件 / 预测性返回 |
| [libxposed](https://github.com/LSPosed/libxposed) 102.0.0（api compileOnly + service） | Xposed 框架 Hook 与运行时检测 |
| [hiddenapibypass](https://github.com/LSPosed/HiddenApiBypass) 6.1 | 调用 `@hide` 系统 API |
| kotlinx-serialization-json 1.9.0 / org.json 20240303 | 导航状态持久化 / 配置解析 |

## 免责声明

- 本模块仅供个人设备学习与自用，禁止用于商业用途或二次分发；
- 使用本模块可能不符合设备厂商的服务条款与预期，由此产生的任何后果（包括但不限于保修失效、账号限制、系统异常）由使用者自行承担；
- 本模块为独立实现，不包含、不转载任何厂商专有代码、资源或商标；
- 流转数据仅在同一小米账号的设备间经系统原生通道传输，仅在本机处理与保存，**不会上传至任何服务器**；
- 请遵守所在地区法律法规，禁止将本模块用于窃取、监控他人设备信息等任何非法用途。

## 更新与反馈

- 酷安 @翰德姆（更新、反馈、催更都在酷安）
- 更新日志见 [docs/changelog-latest.md](docs/changelog-latest.md)
