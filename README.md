# HyperFlow

一款增强小米澎湃互联体验的工具（Xposed / KernelSU 模块）。

让被系统拦截的跨设备通知与短信在设备间顺畅流转：亮屏强制流转、分身通知独立流转、流转短信持久化保存，并支持 App 内消息搜索与一键更新。

## 功能

- **亮屏流转**：亮屏时被系统拦截的跨设备通知可流转至其他设备
- **分身流转**：微信、QQ、钉钉等分身应用通知独立流转，可在其他设备点击打开并回复（蓝牙/局域网）
- **短信持久化**：流转短信自动存入 App 消息页，可归档、搜索、删除，可选写入系统短信收件箱
- **消息搜索**：KernelSU 同款全屏搜索，结果分「会话/信息」两组，命中关键词高亮
- **环境检测**：Root / LSPosed / 模块作用域一键检测与引导
- **在线更新**：App 内一键检测更新

## 环境要求

- 小米澎湃 OS（HyperOS），开启「互联互通 / 小米互联」
- KernelSU（Root 权限）
- LSPosed 2.2+ 框架（KernelSU 内嵌 LSPosed 亦可）

## 安装

1. 在 KernelSU 模块管理器刷入 `HyperFlow-flashable.zip`（或直接安装 APK）；
2. 在 LSPosed 中启用 HyperFlow 模块，并勾选推荐作用域：**小米互联服务（com.milink.service）** + **系统框架**（App 内可一键写入配置）；
3. 重启设备；
4. 打开 App，按引导完成环境检测，在「流转」页开启需要的开关。

> 短信写入系统收件箱：另一台设备需已获得 Root 权限并安装本模块才能完整显示，否则可能出现写入成功但不显示等问题。

## 构建

**环境**：JDK 17 · Android SDK（compileSdk 37 / build-tools 36）· Gradle 9.x

```bash
# 编译 Release APK（hyperflow-miuix 目录下）
gradle :app:assembleRelease --no-daemon

# 产物
#   hyperflow-miuix/app/build/outputs/apk/release/app-release.apk
```

- **签名**：存在 `keystore.jks` 时使用 release 签名（别名/口令走环境变量 `HF_KEY_ALIAS` / `HF_KEY_PASS`），否则回退 debug 签名；
- **flashable zip**：由 GitHub Actions（`.github/workflows/build.yml`）在构建后自动组装（priv-app 预置 APK + service.sh + customize.sh + module.prop），`push main` 或打 `v*` tag 即触发，产物自动发布到 Release；
- 版本号以 `ksu-module/module.prop` 为单一事实来源（`version` / `versionCode`），构建时同步写入 APK 与 update.json。

## 引用的库

| 库 | 用途 |
|---|---|
| [Miuix KMP](https://github.com/yukonga/miuix-kmp) 0.9.4（ui / preference / icons / blur） | HyperOS 风格界面（顶部栏、设置项、图标、柔光玻璃） |
| Jetpack Compose BOM 2025.06.01（ui / foundation / material-icons） | Compose UI 基础 |
| androidx.activity-compose 1.9.3 / core-ktx 1.13.1 | Activity 集成 / 基础组件 |
| [libxposed](https://github.com/LSPosed/libxposed) 102.0.0（api compileOnly + service） | LSPosed 框架 Hook 与运行时检测 |
| org.json 20240303 | 配置与数据解析 |

## 免责声明

- 本模块仅供个人设备学习与调试使用，禁止用于商业用途或再次分发；
- 使用本模块可能不符合设备厂商的服务条款与预期，由此产生的任何后果（包括但不限于保修失效、账号限制、系统异常）由使用者自行承担；
- 本模块为独立实现，不包含、不转载任何厂商专有代码、资源或商标；
- 流转数据仅在同一小米账号的设备间传输，仅在本机处理与保存，不会上传至任何服务器；
- 请遵守所在地区法律法规，禁止将本模块用于窃取、监控他人设备信息等任何非法用途。

## 更新与反馈

- 酷安 @翰德姆（更新、反馈、催更都在酷安）
- 更新日志见 [docs/changelog-latest.md](docs/changelog-latest.md)
