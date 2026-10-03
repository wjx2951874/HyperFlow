# v0.5.15.3

1. 环境检测根治（探针 → 实时 maps 证据）：原检测依赖文件探针，但 /data/adb 只有 root 能写、system_server 实际是 uid=system 写不进去 → 模块明明已注入 system_server 却永远检测为"未勾选"。现改为直接检查模块 dex 被注入了哪些进程（system_server=系统框架生效 / com.milink.service=小米互联生效），无权限问题，检测到即真生效。
2. 推荐作用域补系统框架：xposedscope 同时写入 android（经典 LSPosed）与 system（新版 Vector）双标识，两版框架都认。
3. 诊断日志：环境无 sqlite3 时输出明确提示（db 只能 UI 勾选）。
