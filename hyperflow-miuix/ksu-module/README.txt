HyperFlow V0.5.3 · 作者 酷安@翰德姆
澎湃OS 互联通知流转增强（KernelSU / APatch / Magisk 模块）

【功能】
- 亮屏流转：亮屏强制放行通知流转（模拟锁屏）
- 分身流转：999 空间微信/QQ 通知也流转（标题带【分身】前缀，跨进程去重）
- 点击分身通知 → 直接打开 999 空间微信/QQ（需 LSP 作用域勾选 android）
- 短信持久化：流转短信 App 内归档，可选写入系统收件箱（默认关，10 秒倒计时确认）
- 来电在线接听：来电以全屏接听形态流转
- Miuix 管理 App：环境检测 / 消息页 / 主题（悬浮导航栏+液态玻璃）/ 在线更新

【刷入】
1. KernelSU/APatch 管理器 → 安装本 zip → 重启（App 自动安装为系统应用）
2. LSPosed 启用 HyperFlow 模块，作用域建议：com.milink.service + com.hyperflowplus + android
3. KernelSU 授权 com.milink.service 与 com.hyperflowplus Root（service.sh 自动写白名单）

【在线更新】
- KernelSU 模块列表检查更新（主页"新版本可用"卡片点击弹更新日志）
- 或 App 内"设置 → 版本与更新"

【注意】
- 写系统收件箱可能存在错误显示/重复互联，遇到异常请及时关闭
- 详细文档见仓库 docs/（架构/功能/开发/路线图/更新日志）
