# 妙享直通（自动填密码）与双端远程控制 · 规划 v2

> 版本：1.1.0-beta26 之后规划稿　状态：**待拍板，未实现**
> v2 修正：撤回 v1"系统锁屏 / FBE 首解客观不可行"的错误判断（用户已纠正）。

## 0. v1 错在哪（先认错）

| v1 错误判断 | 用户纠正后的事实 |
|---|---|
| 第三张图 6 圆点盘是**系统锁屏 Keyguard**，模块够不着 | **框是小米互联自己做的**，和系统锁屏密码框**不是同一个框**；属于 `com.milink.service`（HyperOS Interconnectivity 主服务，51MB），模块本就注入 milink 进程，**hook 得到** |
| "重启后首次"=刚开机 FBE 首解、模块没加载 | 指**重启后第一次发起连接**，此时手机很可能已经输过锁屏密码、用户空间早起来了；框是连接授权，不是开机解锁 |
| hook `unlockSwitch` 返回成功 | 那个只是语音助手开关，方向错了，已弃用 |

依据：小米隐私政策明确"跨设备解锁"由小米互联服务**自己收集并校验 PIN（数字/图案/混合）、锁屏密码与密码类型**，即校验逻辑就在 `com.milink.service` 内。["https://privacy.mi.com/Miinterconnectivity/zh_CN/"]

---

## 1. 密码自动填（妙享直通）· 修正后方案

### 1.1 先定位（本地缺主包，需补一步）
- 我手里只有拆分包 `milink-base.apk`（无资源、无授权UI），**6圆点授权界面在完整 `com.milink.service` 主APK里**。
- 两条定位路径，二选一：
  - **抓日志（快）**：复现该框时执行 `adb shell dumpsys activity activities | grep mResumedActivity`（或 logcat 过滤 `milink`/`Unlock`/`verify`），拿到授权 Activity / 类名发我；
  - **给主APK（准）**：`adb shell pm path com.milink.service` 拉出完整APK发我反编译。

### 1.2 实现路线（已反编译修正，2026-10-17）
> 旧的"在 milink 内 hook 校验直通、直接返回成功"已**放弃**：绑定成功必须回传 TEE 签发的 HAT，伪造成功会空 HAT 致配对失败，gatekeeper 绕不过。授权盘宿主实为 `com.xiaomi.trustservice/.remoteservice.locksettings.LockScreenUIActivity`。

1. **hook 自动填（定稿）**：在 `com.xiaomi.trustservice` 进程 hook 输入入口 `A.d.e(String)`，把本地留存密码喂入、**走系统真实校验**拿到真 HAT，只试一次、失败不重试防罚时。
2. 不伪造成功、不模拟逐键点按；按现场 `Z0.b.j()` 读到的类型对齐 PIN/混合/图案。详见 `trustservice-auth-chain.md`。

### 1.3 密码类型全覆盖（按你提醒的四种）
| 类型 | 留存方式 | P1 直通 | P2 填UI |
|---|---|---|---|
| 4 位数字 PIN | 纯数字串 | ✅ 直接比对通过 | ✅ 文本写入 |
| 6 位数字 PIN | 纯数字串 | ✅ | ✅ |
| 混合密码（字母+数字+符号） | 整串 | ✅ | ✅ 文本写入，无需点键盘 |
| 图案 | 存点位序列（相对九宫格 0~8） | ✅ 若校验直通则无感 | ⚠️ 需按序列连线，机型差异大，建议直通为主 |

### 1.4 设置页留存框
- 开"妙享直通"后弹 miuix 密码录入框：选类型 → 录入 → 本地存 `config.json`（`auto_unlock_pwd` + 新增 `auto_unlock_pwd_type`）。
- 确认弹窗写明风险：密码明文落本机配置、开启后任何能发起妙享连接的设备都可能免密，自负安全。

---

## 2. 双端远程控制 · 自研对等（借鉴 Kitea 思路，不照抄）

> 已定稿（2026-10-17 讨论）。目标：两台安卓装同一 App，连接后**对等互控**；iOS 与电脑浏览器只走**网页版看屏/轻操作**。App 级为主，网页版兜底。

### 2.1 角色模型（对等，非 Kitea 单端被控）
- **安卓 ↔ 安卓**：每端既可发起控制对方，也可接受对方看自己；外壳一致（都有「连接设备 / 我的屏幕可被看」入口），避免"只做一边不好看"。
- **iOS**：只当控制端看屏，**不被控、不做原生 App**，直接用网页版。
- **电脑（Windows/Mac 浏览器）**：同样走网页版看手机屏/操作，不装客户端。

### 2.2 被控采集三档（上层一套，只换 provider）
| 档 | 设备 | 权限来源 | 前置 |
|---|---|---|---|
| **root** | Note12 Turbo | `su` 直起，可开机自启 | 无 |
| **Shizuku（adb/shell）** | 小米15（无root） | Shizuku shell(2000)，**不走无障碍** | Shizuku 运行；重启后用无线调试一键重开 |
| MediaProjection 兜底 | 任意 | 录屏授权一次 | 仅当 shell 取屏被限时 |

- root：SurfaceControl/scrcpy 直取屏 + 注入。
- Shizuku：经 Shizuku API 拿 shell，推 server / `app_process` 起服务 / `input` 注入，不用无障碍；重启后 Shizuku 停、无线调试一键重启。
- 注入全程 shell/root，不引入无障碍服务。

### 2.3 网络（只做公网 IPv6，不买中继、不上 Tailscale）
- 端到端公网 IPv6 直连，免 NAT / 端口映射 / 组网。
- 手机拿全局地址（`240e:`/`2408:`/`2409:`/`260e:`，非 `fe80::`）；控制端也要能访问 IPv6。
- 前缀轮换 → **DDNS** 域名持续指向最新 IPv6；浏览器/直连用域名或 `http://[IPv6]:端口`（IPv6 带方括号）。
- **DDNS 选型（2026-10-17 定）**：M1 起步用 **DuckDNS**（免费子域名、一句话 API 更新、注册即用）；DDNS 做成可换插件，以后不满意可切 Dynv6 / Cloudflare（需自备域名），不锁死。
- 不做中继服务器；IPv6 不通即连不上（明确取舍）。

### 2.3.1 视频流双模式（用户自选，照云鸢思路）
参照「云鸢」视频流做法，做**两档并列、独立开关**，连接后用户自选：
| 模式 | 采集/编码 | 通道 | 适用 |
|---|---|---|---|
| **低清 MJPEG（兜底）** | screencap 逐帧 JPEG，浏览器 `<img>` 轮播 | http + token | 应急看屏、无硬编/低配、弱网 |
| **高清硬编 H.264（主线）** | minicap 高帧 → MediaCodec **硬件编码** H.264/H.265 | **强制 HTTPS**（自签证书，控制端手动信任）+ MSE/WebRTC 播放 | 流畅成品体验 |

- **为什么硬编档必须 HTTPS**：MSE / WebRTC 是浏览器安全上下文能力，http 页面禁用（仅 localhost 例外），所以硬编流开启即 https；MJPEG 兜底档 http 可跑。
- **独立开关**：硬件编码耗电发热，视频流按需单独开，与"连接/设备列表"分离，不常亮编码。
- 链路：minicap → MediaCodec 硬编 H.264 → HTTPS 隧道 → 浏览器 MSE 解码 → 触控坐标回传 → 被控端 `input` 注入。
- 待补（等用户提供云鸢 apk 反编译）：编码器 Profile/码率/关键帧参数、自签证书生成与挂载、MSE fMP4 切片、token 挂在 https 上的方式。

### 2.4 配对（无服务器）
- 被控端生成配对串/二维码（含 DDNS 域名、端口、一次性配对码/公钥）；**扫码为主，支持手动粘贴**。
- 一次交换加密密钥，防误连；之后靠 DDNS 域名访问。

### 2.5 分期（先单向跑通，外壳对等）
| 期 | 内容 |
|---|---|
| M1 | root 档 A 控 B：取屏 + 触控注入，IPv6+DDNS 扫码/手动连，网页可看 |
| M2 | 对等反向 B 控 A + 配对密钥交换 |
| M3 | Shizuku 档接入（小米15免root被控），MediaProjection 兜底 |

### 2.6 安全红线
- 配对码一次性、密钥本地加密；公网暴露只开必要端口；能上 HTTPS 就上，避免密码/控制流明文。
- 高权限控制=整机钥匙，弱口令不外泄。

---

## 3. 进度与待办
### 密码自动填（妙享直通）
- [x] 定位授权盘宿主 = `com.xiaomi.trustservice`，挖穿校验链路与 HAT 回传（trustservice-auth-chain.md）
- [x] 配置键 `auth_type/auth_secret/auth_pattern` + AES-GCM 本地加密、HFState 存取/清除
- [x] 写 HookTrustAuth（hook `A.d.e(String)`，PIN/混合已替换并编译通过）+ XposedEntry 分支 + 双作用域(arrays.xml/scope.list)
- [ ] HookTrustAuth 图案档：把留存点序列还原成 MiuiLockPatternView 编码串（编码待核）
- [x] 复刻系统密码录入 UI（AuthSetupScreen.kt，编译通过：数字密码 4–16 纯数字两遍 / 混合 4–16 眼睛显隐两遍 / root 自动识别 locksettings.password_type / 清除；仅用 miuix 官方组件；FlowScreen 直通开关下加入口、关直通清凭证）
- [ ] HookTrustAuth 图案档：把留存点序列还原成 MiuiLockPatternView 编码串（编码待核）；图案录入页占位

### 双端远控（已定稿，见第 2 节）
- [x] 路线拍板：对等 App、root/Shizuku 双采集、IPv6+DDNS、扫码配对、网页兼 iOS/电脑
- [x] 视频流双模式定案：MJPEG 兜底（http）+ 硬编 H.264 主线（HTTPS/MSE），独立开关，用户自选；DDNS=M1 用 DuckDNS 可换
- [ ] 等用户提供云鸢 apk → 反编译编码器参数/自签证书/MSE切片/token 挂载
- [ ] M1 原型：root 单向（input tap/swipe 注入）+ 双模式视频流 + IPv6/DuckDNS + 网页看屏
- [ ] M2 对等反向；M3 Shizuku 免root档
- 待补：端口规划、HTTPS 自签证书方案（实施时定，先拆云鸢）。
