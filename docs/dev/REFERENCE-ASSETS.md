# 外部参考素材台账（REFERENCE-ASSETS）

> **用途**：对话上下文会丢失，所有用户提供/后期要复用的外部项目、APK、来源、结论都登记在这里。
> **新会话开工先读本文件**，再按「对应文档」深入，不要凭记忆。维护原则：新素材来了就追加一行，已用掉的标注状态。
> 分支：`beta`。更新日期：2026-10-17。

---

## A. 双端远程控制参考

### A1. 云鸢 Kitea（主要抄对象，root 模块 + ADB 免root档）
- **官网**：https://kitea.suto.top
- **源码/下载**：Gitee `sutoliu/kitea`（GPL-3.0，无 Release，文件在 `modules_update/`）
  - root 模块：`云鸢v0.7.8.zip`（Magisk，Rust 二进制 arm64/x86_64，service.sh 开机自启，:8888）
  - ADB 档：`Kitea_0.3.2.apk`（内嵌 Shizuku SDK `moe.shizuku`/`rikka.shizuku`，免 root 走 Shizuku）
- **用途**：自研对等远控的骨架参考（采屏/硬编/HTTPS/鉴权/Shizuku 部署）。
- **实拆结论**（详见 `docs/kitea-teardown-0.7.8-evidence.md`）：
  - 采屏不自编码，推 scrcpy-server.jar 到 /data/local/tmp，`app_process` 起 `com.genymobile.scrcpy.Server 4.1`，`control=true`、`video_codec_options=i-frame-interval=1`、`audio=false`、`stay_awake=true`，借 scrcpy MediaCodec 硬编 H.264。
  - 前端三档：WebCodec(VideoDecoder, optimizeForLatency, **需HTTPS**) / MSE(`video/mp4;avc1`) / 兼容 screencap BMP 轮播(http可)。
  - HTTPS：rustls，自签 cert.pem/key.pem 热更新；鉴权 `/api/setup`首设密码、`/api/login`、头 `authorization`+`x-auth-active`、闲置自动登出。
  - 环境四档 ROOT/SHIZUKU/DAEMON/NONE；Shizuku 档产物落 `/data/local/tmp/kitea/`，config 0600。
  - IPv6 抓取命令（可抄）：`ip -6 -o addr show | awk '/mngtmpaddr/ && $4 ~ /^240/ {sub("/.*","",$4); print $4; exit}'`
- **待补**：触控坐标控制消息格式、默认码率/max_fps 档位；DDNS 它不做，我们用 DuckDNS 补。
- 历史对照：`docs/kitea-teardown.md`。

---

## B. 妙享直通 · 密码自动填参考（hook trustservice）

### B1. MiTrustService.apk（授权盘宿主，核心 hook 目标）
- **包名**：`com.xiaomi.trustservice`（6.6MB，用户真机提取）
- **用途**：连接授权密码校验宿主；hook 注入点类 `A.d`（package A）方法 `e(String)`。
- **类型映射**：0=NUMERIC(PIN)→`r6.c(3,str)`；1=GESTURE(图案)→`r6.c(1,str)`；2=ALPHANUMERIC(混合)→`r6.c(4,str)`。
- **结论文档**：`docs/trustservice-auth-chain.md`。
- **状态**：PIN/混合已替换编译通过；图案编码待核 MiuiLockPatternView。

### B2. MiLinkOS4Cn.apk（小米互联主包，98MB）
- **路径**：Termux `pm path com.milink.service` → `/product/app/MiLinkOS4Cn/MiLinkOS4Cn.apk`（用户已 cp 到 Download/milink_apk）。
- **用途**：确认应用层不含6圆点盘、不做明文密码校验，真校验在 trustservice。旧 hook `unlockSwitch` 方向错误已弃用。

### B3. focus.log / focus.txt（dumpsys window 取证）
- **用途**：实锤授权盘前台宿主 = `com.xiaomi.trustservice/.remoteservice.locksettings.LockScreenUIActivity`；排除 `com.miui.securitycenter` 应用锁干扰。

### B4. MIUISecurityCenter.apk（66MB）
- **用途**：应用锁，非授权盘宿主，已排除；反编译停滞弃用。

### B5. Settings.apk（系统锁屏密码设置页，录入页复刻依据）
- **来源**：飞书文件 token `B2elbN9tzo8qXFx3l1Ic1WkcnsV`（130MB，aapt2 解 AXML）。
- **结论文档**：`docs/system-password-ui-reference.md`。
- **要点**：PIN 纯数字 4–16、混合可打印 4–16、两遍分阶段确认；输入用官方 StateEditText(numberPassword/textPassword)；图案页 LockPatternView 3×3。PIN 对外命名「数字密码」。

---

## C. 方案定稿索引（不用重新讨论）
- 远控定稿（对等App/root+Shizuku/IPv6+DuckDNS/扫码配对/网页兼iOS电脑/双模式视频流）：`docs/planning-auto-unlock-and-remote-control.md` 第2节。
- 密码自动填方案：同文档第1节 + `docs/trustservice-auth-chain.md`。

## D. 目标设备与环境
- Note12 Turbo（root，KSU/Magisk/APatch 皆可能）：被控 root 档。
- 小米15（无root）：走 Shizuku，M3。
- iPhone X/13/14：只当控制端看屏，走网页，不原生 App。
- 网络：公网 IPv6 直连 + DDNS；不买中继、不上 Tailscale、不裸 ADB。

## E. 关键仓库/凭据位（开发用，勿外泄）
- GitHub：`wjx2951874/HyperFlow`，分支 beta/main/test/ui-overhaul。
- 模块全局配置 `/data/adb/hyperflowplus/config.json`；AES-GCM 密钥内置常量派生（跨进程共享，不用 Keystore）。
