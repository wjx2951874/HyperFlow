# 云鸢 Kitea v0.7.8 实拆证据（gitee sutoliu/kitea，2026-10-17 下载）

> 来源：Gitee 公开仓库 `sutoliu/kitea`（GPL-3.0）。root 模块 `云鸢v0.7.8.zip` + ADB 档 `Kitea_0.3.2.apk` 均已下载到本地 `kitea-dl/`。
> 目的：给我们自研对等远控抄"骨架决策"，不照抄代码。

## 1. 两个发行包分别是什么
| 包 | 形态 | 权限 | 主体 |
|---|---|---|---|
| 云鸢v0.7.8.zip | Magisk 模块 | **root** | `bin/arm64-v8a/kitea`（Rust 二进制，6.9MB），`service.sh` 开机自启 |
| Kitea_0.3.2.apk | 普通安装包 | **免 root，走 Shizuku** | Java 壳（4 个类）+ 内嵌 Shizuku SDK(`moe.shizuku`/`rikka.shizuku`) + 内嵌模块 zip |

ADB 档环境枚举（`ni.java`）四档：`ROOT(0) / SHIZUKU(1) / DAEMON(2) / NONE(3)`，先探测 /data/adb/modules，否则落 Shizuku。

## 2. root 模块启动链（service.sh，可照抄）
1. `wait_netconn`：ping 223.5.5.5 / 8.8.8.8 最多 9 次等网络；
2. `killall kitea` 清旧进程；
3. `nohup $MODDIR/kitea >/dev/null 2>&1 &` 后台常驻；
4. 监听 `0.0.0.0:8888`（默认端口，网页可改）。
- 证书/配置随模块目录：`config.json`、`cert.pem`、`key.pem`，**替换即热更新**，无需重启进程。
- IPv6 抓取（customize.sh，可直接抄）：
  `ip -6 -o addr show | awk '/mngtmpaddr/ && $4 ~ /^240/ {sub("/.*","",$4); print $4; exit}'`

## 3. 屏幕采集：借 scrcpy，自己不编码（关键）
Rust 不写 MediaCodec，而是把 scrcpy-server.jar 推到 `/data/local/tmp/kitea-scrcpy-server.jar`（先写 `.tmp` 再原子 mv），然后 `app_process` + `CLASSPATH` 启动：
```
com.genymobile.scrcpy.Server 4.1
  log_level=error audio=false control=true
  video_codec_options=i-frame-interval=1
  tunnel_forward=true send_device_meta=false
  send_dummy_byte=false cleanup=true stay_awake=true
  (动态拼 video_bit_rate= / max_fps= / width= / height= / scid=)
```
- **硬编 H.264 由 scrcpy 的 MediaCodec 完成**，Rust 只转发字节；`control=true` 开控制通道，`i-frame-interval=1` 保证 MSE 每 1 秒有 IDR 可起播。
- 兜底 `compat.rs`：scrcpy 不可用时退回 `/screencap` 逐帧 BMP（兼容模式，给 iPhone 等播不了流的浏览器）。

## 4. 前端三档播放（二进制内嵌 JS，strings 实读）
| 档 | 浏览器技术 | 条件 |
|---|---|---|
| WebCodec 性能模式 | `new VideoDecoder({codec, codedWidth, codedHeight, optimizeForLatency:true})` | **必须 HTTPS**，延迟最低 |
| MSE | `video/mp4; codecs="avc1..."` 喂 fMP4 | HTTPS，通用安卓/桌面浏览器 |
| 兼容模式 | screencap BMP 图轮播 | http 也能跑，兜底 |
- 这正好对应我们定的"双模式（硬编 HTTPS 主线 + MJPEG/BMP 兜底）用户自选"。

## 5. HTTPS / 鉴权（rustls，非系统 OpenSSL）
- TLS 用 Rust `rustls-0.23.45`；内置 **selfsign** 自签证书生成（14 处），页面可上传/替换 `cert.pem`+`key.pem`，热更新。
- 路由：`/api/setup` 首次自设密码、`/api/login` 登录；鉴权头 `authorization` + `x-auth-active`，base64url；闲置自动登出（idle），持续使用不掉线。
- HTTP/2（h2 crate）已支持。

## 6. ADB/Shizuku 档部署（M3 可抄）
- 产物落 shell 可写目录 `/data/local/tmp/kitea/`：二进制 `kitea` chmod 0755、`config.json` chmod 0600（base64 写入）；
- 命令执行抽象在混淆类 kj（环境=ROOT 走 su，SHIZUKU 走 Shizuku newProcess），`--version` 自校验；
- ConsoleActivity 是 WebView 壳，直接加载本地服务 url；通知栏常驻、重启/退出动作、"安装模块"按钮。

## 7. 我们照抄 vs 自研对照
| 点 | 抄 | 说明 |
|---|---|---|
| 开机自启/等网络/nohup | ✅ | root 档 service.sh 直接借鉴 |
| IPv6 全局地址抓取 | ✅ | awk 240/mngtmpaddr 命令原样可用 |
| scrcpy 采集 + MediaCodec 硬编 | ✅ 思路 | 比自己写硬编省事，control 通道拿触控 |
| 三档播放(WebCodec/MSE/兼容) | ✅ | 前端分层，HTTPS 硬编、http 兜底 |
| 自签证书 + pem 热更新 | ✅ | rustls/自签逻辑照思路，我们可用 Java 自签 |
| Shizuku 产物落 /data/local/tmp、config 0600 | ✅ | M3 免root档直接用 |
| Rust 重写 | ❌ | 我们用 Android 原生(Kotlin)，只借架构 |
| 默认端口 8888 | 可换 | 我们自定，规划里再定 |

## 待补（继续抠或实测）
- 控制通道触控坐标消息格式（scrcpy control 二进制事件，前端→ws→Rust→scrcpy）；
- 画质码率档位与 max_fps 默认值；
- DDNS 它本身不做，靠用户自配——我们用 DuckDNS 补这层。
