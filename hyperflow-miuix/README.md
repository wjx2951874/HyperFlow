# HyperFlow

**小米澎湃OS 互联通知流转增强模块**（KernelSU / APatch / Magisk + LSPosed）

作者：酷安@[翰德姆](https://www.coolapk.com/u/4112338) · 测试版持续迭代中

---

## 功能

- **亮屏流转**：亮屏状态下强制放行通知流转（模拟锁屏），解决官方"仅锁屏可流转"限制
- **分身流转**：微信 / QQ 分身（999）通知也参与流转
- **短信持久化**：流转短信按发送人归档到本机，可随时回看（不依赖系统短信 App）
- **来电在线接听**：锁屏场景下转为 OS4 在线接听形态
- **Miuix 风格 UI**：Compose 重写的设置/消息界面，AndroidLiquidGlass 柔光玻璃
- **消息会话页**：短信 App 样式的会话列表 + 时间排序（列表/正文独立）

## 安装

1. 刷入 `HyperFlow-x.y.z-flashable.zip`（KernelSU 管理器 → 刷入模块）
2. 安装配套 `HyperFlow.apk`（刷入时自动安装；未自动安装时手动安装一次）
3. LSPosed 中启用 **HyperFlow** 模块，作用域勾选：
   - `com.milink.service`（妙享桌面/互联服务）
   - `com.hyperflowplus`（本模块 App）
4. KernelSU 授权 `com.milink.service` 与 `com.hyperflowplus` Root 权限
5. 打开 App 完成首次引导

> 更新：KernelSU 模块列表内可直接检查并一键更新（走 GitHub Release 通道）；App 内"设置 → 版本与更新"亦可。

## 设备支持

- 已测试：Redmi Note 12 Turbo（marble）/ OS4 / Android 17 / KernelSU
- 需要：澎湃 OS 2+（HyperOS 互联）、root 设备一台（接收端），发送端无需 root
- 仅同一小米账号下的设备之间流转

## 责任声明

- 本模块仅供个人设备调试学习使用，请遵守各软件服务条款
- 流转数据仅在同一小米账号设备间传输，不经过第三方服务器
- 使用过程中遇到问题，欢迎酷安反馈：[@翰德姆](https://www.coolapk.com/u/4112338)

## 更新日志

### V0.4.3
- 消息解析修复（正确切分键值对 / 来源设备读取 notification_ref / 时间紧凑格式兼容）
- 消息页改为短信 App 会话样式（列表=发送人+预览+时间；详情=来源+圆角气泡+时间标签）
- 消息页右上角排序图标（消息列表 / 正文列表独立排序）
- 首次引导页（功能说明 + 状态检测 + 互关酷安）
- 设备信息详情弹窗（机型 / 澎湃OS / Android / 内核 / Root / KSU）
- AndroidLiquidGlass 柔光玻璃
- 顶部标题随页面（消息页只显示"消息"）
- KSU 模块标题去版本号；更新通道指向 GitHub Release

### V0.4.2
- 修复渲染崩溃（libhwui 渲染树成环 SIGSEGV）

### V0.4.0
- UI 全面迁移 Miuix（Compose）；消息归档；排序；秒开缓存

---
*HyperFlow · 酷安@翰德姆 · 让澎湃互联更好用*
