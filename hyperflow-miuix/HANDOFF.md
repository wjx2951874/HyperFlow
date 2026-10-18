# HyperFlow 接手引导（给下一个 AI 工具 / 开发者）

> 本文档是"接手指令"。如果你是接手 HyperFlow 项目的下一个 AI 工具或开发者，**先完整读完本文，再动手**。
> 注意：仓库为**公开仓库**，本文档不含任何密码、PAT、密钥（凭据获取见文末"敏感信息获取"）。

---

## 0. 一句话项目定位

HyperFlow = **小米澎湃 OS（HyperOS）互联通知流转增强模块**，形态是 KernelSU 模块 + LSPosed（libxposed API 102）插件 + Miuix(Compose) 管理 App。作者酷安@翰德姆。
通过 Xposed hook 小米互联服务（`com.milink.service`）、信任服务（`com.xiaomi.trustservice`）、电话（`com.android.incallui`）、system_server 等进程，实现**亮屏也流转（电话跨设备接听）**、**免预填（自动填授权盘密码）**、分身（999 空间）通知流转、短信持久化、妙享直通等能力。

- 公开仓库：`https://github.com/wjx2951874/HyperFlow`
- **工作分支：`beta`**（所有最新迭代都在这；`main` 是 v0.5.3 稳定线，暂不动）
- 当前版本：**1.1.0-beta33（versionCode=161，versionName="1.1.0-beta33"）**，包后缀 .1/.2/.3… 仅区分交付轮次，版本号不变
- **版本单一事实源**：`app/build.gradle.kts`（versionCode/versionName）+ `ksu-module/module.prop`
- 最近提交（beta）：`82ccabe`（免预填自动填修复）、`3646643`（亮屏电话流转补发 ON_CALL_VOIP）

---

## 1. 接手第一步：按顺序读这些文档

| 顺序 | 文件 | 你能获得什么 |
|---|---|---|
| 1 | `README.md` | 项目是什么、功能总览、安装、文档索引、版本状态 |
| 2 | `docs/ARCHITECTURE.md` | 三层结构（App / milink / system_server）、进程注入、数据流 |
| 3 | `docs/FEATURES.md` | 每个功能的原理 → 代码位置 → 为什么这么写 → 已知限制 |
| 4 | `docs/DEVELOPMENT.md` | 构建、发版 bump 清单、8 条目 zip、发布验证、调试 |
| 5 | `docs/ROADMAP.md` | ✅ 已实现 / ⏳ 待验证 / 🔲 规划中 / 已放弃方案（别再走回头路） |
| 6 | `HANDOFF.md`（本文） | **当前最前线（电话流转/免预填）的根因、修复、待验证项、设备拓扑** |

---

## 2. 当前最前线（务必先看）

### 主线① 电话流转（亮屏跨设备接听）—— 修复已交付，**待用户实测**

**用户语义**：「解锁时流转」开关（`force_transfer`）开 = 亮屏也要模拟锁屏流转（跨设备接听）；关 = 零干预回原生（仅真实锁屏可接）。

**根因（hf_lock.log / hf_call3.log 双日志实锤，已完成）**：
- **锁屏来电流转链**：Telecom 绑定 InCallUI（InCallServiceImpl）→ InCallPresenter 状态机 `NO_CALLS→INCOMING` → `StatusBarNotifier.resendNotification` → **InCallUI 自己直接向 `com.milink.service` 发 `ON_CALL_VOIP` 广播**（带完整 Notification）→ milink `NotifTransReceiver` 收到 → `buildSbnFromIntent` → `NotificationHandler.handleNotificationTrans` → 流转成功（Code 200）
- **亮屏来电失败链**：Telecom **不绑定 InCallUI**（InCallController `isRequestedtype:false`，只绑蓝牙 InCallService）→ InCallUI 进程只有后台服务跑 `TelecomAdapter.startForegroundNotification`（来自 `notifyNotification` 的 enableCallStyle 分支），**状态机不启动、不发 ON_CALL_VOIP 广播** → milink 收不到 → 链条从源头断
- 之前"hook milink 模拟锁屏全家桶（KeyguardManager/PowerManager）"方向是**错的**：亮屏时 milink 根本没收到来电事件，那些判定点永远不会被调用（日志 0 条 CALLED 实锤）

**修复（beta33.7，commit `3646643`，已交付待实测）**：
- 新增 `HookCallVoipNotify`：hook `StatusBarNotifier.notifyNotification(Context, NotificationManager, Notification)`（静态，亮屏来电时必被调用），在 `force_transfer` 开启 + 非锁屏（`KeyguardManager.isKeyguardLocked()==false`）时，**按原生广播格式补发 ON_CALL_VOIP**（复用同一 Notification 对象，7 个键名逐一比对 `NotifTransReceiver.buildSbnFromIntent`：pkg/id/tag/uid/initialPid/userHandle/notification）
- **零 UI 干预**：不模拟锁屏、不启动全屏，亮屏来电横幅仍由 SystemUI 原生显示（上半部分）——用户明确要求
- 锁屏时原生已发，hook 跳过（isKeyguardLocked==true），不重复流转；3 秒去重窗口
- XposedEntry 的 incallui 分支**只装 HookCallVoipNotify**（不再装 HookCallRelay/HookForceTransfer——会改掉 isKeyguardLocked 导致跳过判断失效）
- 作用域 arrays.xml 已加 `com.android.incallui`

**用户当前唯一担忧（已通过方案规避）**：亮屏来电**必须保持上半部分横幅，不得全屏显示**。

### 主线② 免预填/自动捕获密码 —— 修复已交付，**待用户实测**

**用户语义**：妙享直通开启后，跨设备连接要求输入密码验证时，**自动填**本地已捕获的密码完成验证，免手动输入。

**链路**：
1. **捕获**：用户手动输入一次（授权盘）→ `A.d.e(String)` 被调 → `HookTrustAuth` 拦截到无留存值（=用户手输）→ 反射 `A.d` 实例字段 `a` 取类型码（1=PIN/2=图案/3=混合）→ 经 `CapturedCred.writeSp` 落 trustservice 私有 sp（`hyperflow_captured.xml`，键 hf_cap_type/hf_cap_secret/hf_cap_pattern）
2. **自动填**：下次连接 → 授权盘出现 → 自动注入

**根因（trustservice 反编译实锤，已修复）**：`A.d.e(String)` 是"输满自动提交"入口（`T0/b.java` case 0：键盘点击收集、**输入长度达到要求才调用**）——只 hook A.d.e 无法实现自动填（用户不输入时它不被调用）。**修复（beta33.8，commit `82ccabe`）**：新增 `HookTrustAuthAutoFill`，hook 三个输入视图（`NumericKeypadView`/`AlphanumericKeypadView`/`GestureDrawingView`）的 `setAuthInputCompleteListener(Q0.b)`——授权盘**一显示**就拿到 A.d 实例，延迟 500ms 后直接调 `A.d.e(留存凭证)`（等效替用户瞬间输满提交，走系统 gatekeeper 真实校验拿真 HAT）。凭证三级回退：`CapturedCred.readLocalSp()` → `readPublic()` → `Config` 预存。

**相关已完成**：`HookTrustAuthUiClose`（验证成功后自动关授权盘 UI——hook `netbus.h.n(int)` 结果码 0 → 反射 `LockScreenUIActivity.f1753J` → finish；用户实测"手动输入会关"✓）。

**已答复用户的问题**：system_server 日常解锁捕获（`HookKeyguardCapture` hook `LockPatternUtils.verifyCredential`）为什么 0 条——三可能：① 作用域勾完未重启 system_server 未重注入；② 指纹/面部解锁不走 verifyCredential；③ HyperOS4 三参签名可能已变→静默跳过。**该链已被手动输入捕获（sp 通道）替代，不阻塞。**

### 主线③ 双开标识输入框（inApp 覆盖层）—— 已交付（beta33.4），用户后续又报过 bug

- 用户要求：输入框**贴底贴输入法、点击不蹦**（"要死啊/狗屎"式催修过）
- 三次修法：Popup 内 imePadding（无效，Popup 窗口读不到真实 IME inset）→ Popup 外手动 IME 高度 padding（更糟，ADJUST_PAN 整体上移+双重位移）→ **最终 b4387a7 = inApp 覆盖层**：`HyperDialog` 新增 inApp 参数，inApp=true 时直接 App 内 Box 覆盖层 + `Modifier.imePadding()`（MainActivity 内容树内真实响应键盘），Popup 分支恢复纯 bottomInset
- **注意**：用户 beta33.8 前后又提过"双开前缀输入框不贴底、点框上下蹦"——**inApp 方案是否真正修好需要复测确认**（若仍蹦，查 MainActivity 是否对 Box 覆盖层正确应用 imePadding、有无多个 inset 叠加）

### 主线④ 分身 999 概率双开 —— 已修（ab5d9e1）未实测

- `HookCloneBypass.markCloneTitle` 给分身通知反射打 `hf_is_clone` 标记，`HookRemoteOpen` 见标即改 999（userId==0 setUserId 999；无参签名 relaunchAsClone 反射 11 参兜底）
- 用户反馈过"有时拉到 999、有时两个都拉起覆盖主微信"——**概率性问题未完全解决**，PendingIntent.getIntent() 拿到的是复制品，putExtra 是否生效有不确定性；待实测/进一步加固

---

## 3. 构建与出包（新工具必读）

```bash
# 本地编译验证（禁止 assembleRelease 会 OOM）
cd hyperflow-miuix  # 仓库根
JAVA_HOME=/home/user/tools/jdk-21.0.12.1+1 ANDROID_HOME=/home/user/Android/Sdk \
  /home/user/tools/gradle-9.7.1/bin/gradle :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --no-daemon --console=plain
```

```bash
# CI 出包（GitHub Actions，workflow: build.yml，ref: beta）
curl -X POST -H "Authorization: Bearer <PAT>" -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/wjx2951874/HyperFlow/actions/workflows/build.yml/dispatches -d '{"ref":"beta"}'
# 查 run
curl -H "Authorization: Bearer <PAT>" "https://api.github.com/repos/wjx2951874/HyperFlow/actions/runs?branch=beta&per_page=1"
# 下载 artifact（名 HyperFlow-flashable，解出 HyperFlow-flashable.zip）
# 交付包命名：packages/HyperFlow-1.1.0-beta33.<N>-flashable.zip（<N> 递增，仅区分轮次）
```

**交付包索引**（本机 `/home/user/Doubao/chats/38444811295021314/packages/`）：
- beta33.6 `fc8e96a`：手动输入即捕获（HookTrustAuth 捕获落 sp）
- beta33.7 `3646643`：**电话流转修复**（HookCallVoipNotify 补发 ON_CALL_VOIP）← 待实测
- beta33.8 `82ccabe`：**免预填自动填修复**（HookTrustAuthAutoFill）← 待实测

---

## 4. 关键反编译产物与证据（本机路径）

- InCallUI：`/home/user/Doubao/chats/38444811295021314/reverse/incallui_out/sources`（APK 附件：电话_40.01.58.apk，/product/priv-app/InCallUI/InCallUI.apk，build 2026-10-10 f8580b0c4）
  - `StatusBarNotifier.java`：updateInCallNotification（z4 发布判定，**不含 isKeyguardLocked**）/ notifyNotification（808 行，enableCallStyle→startForeground）/ sendNotificationBroadcast（ON_CALL_VOIP 广播）/ NOTIFICATION_TAG="incall"
  - `InCallPresenter.java`：startOrFinishUi（931 行，INCOMING→"Start Full Screen in call UI"）/ 1035 行组合判定 / 2239 行 forceProcessIncoming
  - `TelecomAdapter.java`：startForegroundNotification（244 行）
- MiTrustService：`/home/user/Doubao/chats/38444811295021314/reverse/trust_out/sources`（APK：MiTrustService.apk）
  - `A/d.java`：A.d.e(String)=提交入口（281 行未反编译）；实例字段 a=类型码（1=PIN/2=图案/3=混合）；f4c=AuthInputView
  - `T0/b.java`：case 0 数字键盘点击→输满调 `((A.d) listener).e(f2)`
  - `Q0/c.java`：抽象类，三个实现类 NumericKeypadView/AlphanumericKeypadView/GestureDrawingView；listener 字段：Numeric=f1665m、Alphanumeric=h、Gesture=h
  - `com/xiaomi/trustservice/remoteservice/locksettings/LockScreenUIActivity.java`：f1753J=静态当前 Activity；onCreate 调 `f1756B.a(4, timeout, h(7,this))`
- milink：`/home/user/Doubao/chats/38444811295021314/reverse/out3/sources`
  - `com/xiaomi/dist/notification/listener/NotifTransReceiver.java`：onReceive → buildSbnFromIntent（键名 pkg/id/tag/uid/initialPid/userHandle/notification）→ handleNotificationTrans
- **关键日志证据**（附件）：`hf_lock.log`（锁屏来电全链 6579→ON_CALL_VOIP→200）、`hf_call3.log`（亮屏来电 0 条 HyperFlowPlus、InCallUI 无状态机）、`hf_full2.log`（唯一来电在模块加载前）、`hf_cap.log`/`hf_call.log`/`hf_call2.log`（-c 清启动行的抓法问题，判断注入**必须用 LSPosed App 日志页截图**）

---

## 5. hook 清单（16 个）

CapturedCred（工具）、HookAutoUnlock、HookCallRelay（milink 模拟锁屏——**电话流转已弃用此路线，但保留**）、HookCallVoip（no-op）、**HookCallVoipNotify（电话流转新）**、HookCloneBypass、HookForceTransfer（no-op）、HookKeyguardCapture、HookMirrorObserve、HookRemoteOpen、HookSmsPersist、HookSmsSenderEnrich、**HookTrustAuthAutoFill（自动填新）**、HookTrustAuth、HookTrustAuthAuto、HookTrustAuthUiClose。

注入点（XposedEntry.java）：system_server（HookRemoteOpen[system]/HookKeyguardCapture）、com.xiaomi.trustservice（HookTrustAuth/Auto/AutoFill/UiClose）、com.android.incallui（**HookCallVoipNotify**）、com.milink*（其余）。

---

## 6. 设备拓扑与用户硬偏好

- 被控端：Redmi Note 12 Turbo（root、装模块、LSPosed；deviceId 7528A0D1/ACD0FAFC 双 ID）；对端 Xiaomi 15=90266701（主）、REDMI K90=E539144A
- 环境：HyperOS 4.0.2.0.XPCCNXM.D01；milink 网络进程 pid 9327（lyra）、通知监听/跨设备服务 pid 9045；模块注入目标 `com.milink.service:provider`
- **用户硬偏好（违反会被骂）**：
  - **零自绘**：任意自绘 Switch 均弃用
  - **机关**：连点「设备协同」标题 10 次（2 秒窗口）直接进预设置，**不弹开启提示、无涟漪**
  - 双开输入框必须贴底贴输入法、点击不蹦
  - 电话流转零人工干预、亮屏来电保持上半部分横幅、**不得全屏**
  - 状态卡拦截被否（"也交给 mlink"）
  - 每次完成一步汇报；在意速度与迭代次数
  - 验证注入**用 LSPosed App 日志页截图**，不用 logcat -c 后的抓包

---

## 7. 未完成项（按优先级）

1. **用户实测验收**：beta33.7（亮屏电话流转）+ beta33.8（授权盘自动填）——等用户实测反馈后处理
2. **分身 999 概率双开**（主线④）：待实测/加固（PendingIntent 复制品 putExtra 不确定性）
3. **双开输入框**：inApp 方案是否真修好需复测（用户最新反馈过仍蹦）
4. **1.1 正式版发布**（用户等电话流转做好后执行）：去 beta 后缀（versionName 1.1.0、module.prop）、update.json zipUrl 更新、GitHub Release 发布
5. 早期已完结：密码类型选择页纯文字、OS/Android 版本拆分、HyperOS 4.0.x 全称、应用版本免"模块"字样、妙享直通文案逐字稿、机关移位去涟漪、LSP 作用域按开关检测（f2a27e9）、设备信息 root 兼容

---

## 8. 敏感信息获取

- GitHub PAT：**在会话历史中**（ghp_ 开头，用户确认可用，dispatch 返回 204）。公开仓库禁止写入任何凭据。
- 飞书文档（早期实操细节/发版防错）：`https://my.feishu.cn/docx/MIVXdD99OoxsXdxNzFLc92mP6Bc`
- 反编译工具：`/home/user/Doubao/chats/38444811295021314/tools/bin/jadx`（用法：`jadx -d reverse/<out> --no-res <apk>`）
