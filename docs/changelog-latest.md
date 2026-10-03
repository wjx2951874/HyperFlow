# v0.6.16.3

1. **搜索框对齐小米短信**：反编译真实短信 App（18.0.0.32）确认搜索栏参数——
   hint/输入字号由 14sp 修正为 **17sp**（`miuix_appcompat_search_edit_text_size` =
   `miuix_font_size_headline1`），搜索图标 16dp → **24dp**，占位"搜索消息"
   整体视觉居中，与小米短信搜索栏一致。
2. **检测功能重构（黄/绿卡强停重启）**：
   - 不再读取 LSP 作用域参数（shell 读 scope 文件存在多路径/格式差异误报），
     LSP 状态以 libxposed service 绑定快照为准。
   - "重新检测"按钮新逻辑：**红卡**（未连接 LSP）→ 直接重新检测（root 读取快）；
     **黄卡/绿卡** → 底部提示"LSP 状态需重启后刷新，正在自动重启；若未自动打开
     请手动打开"，400ms 后自动强停并重启 App（AlarmManager 拉起，进程被杀
     也能自动回到首页）——勾选/取消作用域后无需手动退出重进，检测即真实状态。
3. **短信写入诊断升级**：诊断打点由 `sms/inbox` 子集改为查完整 `sms` 表
   （`_id/address/body/date/read/seen/type`），用于定位"系统 milink 原生写入
   短信库但短信 App 不显示"（type 是否非收件箱、read/seen 标记、写入位置）。
   下次流转短信后抓日志可直接看到系统短信库真实状态。
