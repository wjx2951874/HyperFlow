# Kitea（云鸢）实现拆解 + HyperFlow 抄法对照

> 来源：gitee.com/sutoliu/kitea ，安装包 modules_update/云鸢v0.7.8.zip（Magisk/KSU 模块）
> 目的：用户要求"抄他的实现方式"。本文先讲清它**实际怎么做**，再给我们(Kotlin/Xposed模块)怎么照搬形态，以及版权边界。

## 0. 版权先说清
- 仓库挂 **GPL-3.0**，但**核心 Rust 源码未公开**：仓库只有 LICENSE、README、changelog、更新 json 和二进制包，拿不到代码。
- 结论：**无代码可逐行抄**；我们只借鉴"功能形态 / 交互 / 协议思路"（思路不受版权保护），用 Kotlin 自行重写，不构成 GPL 衍生作品，不受传染。

## 1. 包形态（和我们 HyperFlow 模块完全同构，最值得抄）
v0.7.8 是一个标准 Magisk/KSU 模块 zip：
```
module.prop            # id=Kitea version=0.7.8 versionCode=83 updateJson=...
service.sh             # 开机等网络 → killall → nohup 拉起主程序
customize.sh           # 按 ARCH 抽 arm64/x86_64 二进制、--version 校验、打印访问地址
bin/arm64-v8a/kitea    # Rust 主程序(6.9MB)，前端页面内嵌其中
bin/x86_64/kitea
webroot/index.html     # 仅跳转 0.0.0.0:8888
```
**service.sh 启动逻辑（可直接照搬到我们的 service.sh）：**
```sh
wait_netconn() { i=9; until ping -c1 223.5.5.5 || ping -c1 8.8.8.8 || [ $i -le 0 ]; do i=$((i-1)); sleep 9; done; }
wait_netconn
MODDIR=${0%/*}; cd $MODDIR
killall kitea 2>/dev/null
nohup ${MODDIR}/kitea >/dev/null 2>&1 &
```
**customize.sh 自动取公网 IPv6 提示地址（照抄这条 awk）：**
```sh
IPv6Addr=$(/system/bin/ip -6 -o addr show | awk '/mngtmpaddr/ && $4 ~ /^240/ {sub("/.*","",$4); print $4; exit}')
# 打印 http://[$IPv6Addr]:8888
```
config.json / cert.pem / key.pem 跨升级保留（cp_default_config）。

## 2. 技术栈（Rust，我们用 Kotlin 等价替换）
| Kitea(Rust) | 作用 | HyperFlow(Kotlin) 等价 |
|---|---|---|
| hyper + h2 | HTTP/1.1 + HTTP/2 服务 | NanoHTTPD / OkHttp 内置 Server（先做 HTTP/1.1） |
| rustls | HTTPS/TLS1.2/1.3 | 自签证书 + 轻量 TLS，首期可只 HTTP、预留 443/自定义端口 |
| argon2 (PHC 串) | 密码哈希存储 | bcrypt/argon2 库或 PBKDF2，禁止明文存 |
| tungstenite | WebSocket | OkHttp WebSocket / Java-WebSocket |
| 内嵌前端 | 单页控制台 | assets 放静态 HTML/JS，服务返回 |

## 3. 路由表（已从二进制提取，照此设计接口）
| 模块 | 端点 | 说明 |
|---|---|---|
| 首次设密 | `POST /api/setup` | 无密码时浏览器自设，之后变登录 |
| 登录 | `POST /api/login` | 发 Bearer token |
| 改密 | `POST /api/change-password` | |
| 设备信息 | `GET /api/info` | 型号/CPU/内存/存储/电池/网络IPv4+IPv6 |
| 文件 | `/api/files/list` `/read` `/upload` `/create` `/rename` `/move` `/copy` `/delete` `/attr` `/sign` | sign 出临时签名下载链接，list 分页 |
| 进程 | `/api/process/list` `/kill` | 读 /proc，按内存/CPU 排序，结束进程 |
| 终端 | `WS /api/terminal/ws` | pty，cols/rows resize，双向字节流 |
| 屏幕 | `WS /api/screen/ws` | screencap 采集→h264→MSE；jpgMode 轮询兜底；fps/bitrate/分辨率/性能模式；远程键鼠 |
| WebDAV | `/dav` | 挂 /sdcard 当轻NAS，只读模式，独立密码 |
| 站点配置 | `/api/site/title|lang|order|pro`、`/api/server/config`、`/api/webdav/config`、`/api/modules/toggle` | 标题/多语/排序/端口/模块开关 |

## 4. 认证机制（照抄思路）
- 首次 `/api/setup` 设密码 → argon2 哈希存 config.json；之后 `/api/login` 校验返回 token。
- 后续请求头 `Authorization: Bearer <token>`；WebSocket 用子协议带 token（`new WebSocket(url,[token])`）。
- 安全：闲置自动登出、session_ttl 可配、改密、可选 HTTPS、默认端口 8888 可改。

## 5. 屏幕远控怎么做的（关键，root / adb 双模式）

### 5.1 Kitea 实际用的是 root 模式（内嵌 scrcpy-server）
二进制里挖出屏幕模块 `src/modules/screen/scrcpy.rs`，它**不自己写采集编码，而是内嵌 scrcpy-server 4.1**：
- 把 `scrcpy-server.jar` 放到 `/data/local/tmp/kitea-scrcpy-server.jar`。
- 用 app_process 直接拉起（root 模块身份）：
  ```
  /bin/app_process CLASSPATH=<jar> com.genymobile.scrcpy.Server 4.1 \
    log_level=error audio=false control=true \
    video_codec_options=i-frame-interval=1 \
    tunnel_forward=true send_device_meta=false send_dummy_byte=false \
    cleanup=true stay_awake=true
  ```
- 采集/编码：scrcpy-server 内部用 **MediaCodec 硬件编码 h264**（root 下直接经 SurfaceControl/SurfaceFlinger 取屏，无需投影授权），不是逐帧 screencap。
- 通道：`tunnel_forward=true` 在设备本地建隧道，Rust 进程读 h264 NAL 流 → WebSocket → 浏览器 MSE 播放；`control=true` 反向写控制事件注入键鼠。
- 兜底 jpgMode：不支持 MSE/视频流的浏览器（旧 iPhone）退化为 `screencap` 抓 JPG 轮询。
- `stay_awake=true` 保持常亮、`i-frame-interval=1` 每秒关键帧便于快速起播、`audio=false` 不传声。

### 5.2 root 模式 vs adb 模式对照
| 环节 | root 模式（Kitea 采用） | adb 模式（scrcpy 原生，免root） |
|---|---|---|
| 启动身份 | 模块 root 直接 `app_process` 起 server | 电脑 `adb push` server 到 `/data/local/tmp`，`adb shell app_process` 起 |
| 取屏 | SurfaceControl/SurfaceFlinger 底层直取，**免授权弹窗** | SurfaceControl 离屏虚拟显示 / MediaProjection，首次可能要一次投影授权 |
| 编码 | MediaCodec 硬件 h264（相同） | MediaCodec 硬件 h264（相同） |
| 数据通道 | `tunnel_forward` 设备本地隧道，Rust 进程直连 | 走 **adb 通道**转发视频流与控制事件，`adb forward` 端口 |
| 键鼠注入 | server 经 InputManager/`/dev/input` 注入 | 同样 server 内注入，借 adb shell 权限 |
| 权限要求 | 需 root | 只需无线调试/adb，手机不装 App，断开不留文件 |
| 外网 | 被控端自己监听，IPv6 直连浏览器 | 控制端必须能 adb 连到手机（IPv6/内网穿透到 adb 端口） |

### 5.3 我们落地选择
- 我们模块本来就有 root → **直接照 Kitea 的 root 内嵌 scrcpy-server 路线**最省事，浏览器经 WS 拿 MSE 流。
- 若要做免 root 的 adb 备选：控制端用 adb 连，server 经 adb 转发，浏览器只做展示层。
- 首期可先 JPG 轮询截屏保底，再上 h264/MSE。

## 6. 网络与外网
- 监听 `0.0.0.0:8888`，公网 IPv4/IPv6 端到端直连，**不用 NAT/端口映射/Tailscale**。
- IPv6 前缀会变 → DDNS 域名指向；无公网 IP → 内网穿透转发 8888。
- 安全红线：改默认密码、尽量 HTTPS、不长期暴露弱口令。

## 7. HyperFlow 落地优先级建议
1. P0 模块自启 + 内置 HTTP 服务 + 登录(argon2) + `/api/info` + 静态前端。
2. P1 文件浏览/下载/上传 + 进程列表/结束 + 终端 WS（pty）。
3. P2 屏幕：先 JPG 轮询截屏，再上 h264/MSE；WebDAV。
4. P3 HTTPS、端口自定义、多语、DDNS 提示。

> 注意：这是**独立远控子系统**，与"妙享直通自动填密码"是两条线，不要混在一个 hook 里。
