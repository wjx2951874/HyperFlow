# HyperFlow 完整交接文档（换工具版）

> **交接时间**：2026-10-30，beta60（versionCode 188，commit fd2eff2）
> **交接原因**：用户对当前工具已失去信心，将把本文件交给新工具继续修。**新工具请先通读全文再动手，勿让用户重复解释背景。**
> **本文件自包含**：所有结论均来自用户设备实测日志与反编译产物，不是推测。本机路径仅供在原有电脑上继续开发时复用；换工具对话时以"证据/结论/代码位置"为准。

---

## 0. 30 秒速览

**项目**：HyperFlow——小米澎湃 OS 互联增强 Xposed/KernelSU 模块（仓库 `wjx2951874/HyperFlow`）。让被拦截的跨设备通知/短信流转、分身通知独立流转、远控授权免输密码。

**当前三件事（按优先级）**：

| # | 主线 | 状态 | 一句话 |
|---|---|---|---|
| 1 | **分身双开**（点击分身通知 → 主微信被同时拉起） | ❌ 用户实测仍复现 | beta59 三层防线已落码交付，但**模块自身拦截日志从未抓到**，无法证明哪层生效/失效 |
| 2 | 锁屏密码自动捕获→授权盘自动填 | ⏳ beta60 已改待验证 | 捕获主通道改为 `LockSettingsService.checkCredential`（重启后首次解锁必走） |
| 3 | 授权 UI 验证后自动关闭 | ⏳ beta58 已修未验收 | 期望日志 `verify OK, UI closed via mActivities` |

**两条铁律（违反必白干）**：
1. **复现双开必须走"流转/远控"路径**（另一台设备/投屏里点微信）——本地"先主微信→分身→锁屏再开"**不触发** mirror 路径，怎么抓都是干净日志。
2. **禁止 `rm -rf /data/adb/lspd/log` 后抓包**——LSPosed daemon 攥着旧文件句柄，新日志写进已删除文件，tar 出来 log/ 永远空。**已两次实锤**（用户两次 tgz 都是空 log/）。正确抓法见 §6。

---

## 1. 项目与仓库（事实，勿凭旧文档）

- 公开仓库：`wjx2951874/HyperFlow`；**CI/交付分支 `beta`**（origin/beta 当前 = beta60 fd2eff2）；`main` 是旧线，仅文档同步。
- 本地根目录（原电脑）：`/home/user/Doubao/chats/38445211817859330/hyperflow`；**Android 工程 `hyperflow-miuix/`**；本地工作分支 `beta54-restore`（推 beta 用 `git push origin beta54-restore:beta`）。
- 版本事实源两处（都要改）：`app/build.gradle.kts`（versionCode/versionName）+ `ksu-module/module.prop`（version 与 versionCode 两行）。当前 **188 / 1.1.0-beta60**。
- 编译（无 gradlew wrapper，用绝对路径 gradle；**禁 assembleRelease，会 OOM**，只编这两个任务即可验证代码）：
  ```bash
  cd /home/user/Doubao/chats/38445211817859330/hyperflow/hyperflow-miuix && JAVA_HOME=/home/user/.doubao_tools/jdk-21.0.12.1+1 ANDROID_HOME=/home/user/.doubao_tools/android-sdk /home/user/.doubao_tools/gradle-9.6.0/bin/gradle :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --no-daemon --offline --no-watch-fs -q
  ```
- CI：GitHub Actions `build.yml`（分支 beta）。改代码后：push beta → dispatch → 取 artifact `HyperFlow-flashable.zip` → 改名交付。
  ```bash
  TOKEN=$(git remote get-url origin | sed -E 's#https://([^@]+)@.*#\1#')   # remote 内嵌 PAT
  git push origin beta54-restore:beta
  curl -s -o /dev/null -X POST -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" -d '{"ref":"beta"}' https://api.github.com/repos/wjx2951874/HyperFlow/actions/workflows/build.yml/dispatches
  ```
  取包：`/actions/runs?branch=beta&per_page=1` → 最新 run → `/actions/artifacts/<id>/zip` → 解出 `HyperFlow-flashable.zip`。
- 文档地图（README / HANDOFF / docs 已全部更新到 beta60）：
  - `hyperflow-miuix/README.md`（功能总览/架构/安装作用域）
  - `hyperflow-miuix/HANDOFF.md`（本文件的仓库版）
  - `hyperflow-miuix/docs/{FEATURES,ROADMAP,CHANGELOG,changelog-latest}.md`
  - 根 `docs/changelog-latest.md`（App 更新通道读它）、根 `update.json`

---

## 2. 分身/双开主线（最前线，用户最恼火）

### 2.1 完整根因链（每环都有日志/源码依据）

```
A. 用户在其他设备/投屏里点"分身微信通知"
   ↓
B. 接收端系统把 intent 回传发送端，由 com.xiaomi.mirror（镜像/远控/投屏进程）在发送端 system_server 执行 startActivity
   ↓  【beta58 时代 log.old 实锤，19:07:27→19:07:29】
   clone open check: caller=com.xiaomi.mirror pkg=com.tencent.mm userId=0
   clone open: uid=10277 pkg=com.tencent.mm      ← uid 10277 = u0 主微信（999×100000+277=99910277 才是分身）
   ↓
C. 主微信（u0）被 mirror 进程以 userId=0 直接拉起 → 与分身（u999）同时在前台 → 双开
```

**为什么 beta57/58 修不掉**：
- beta57 只 hook `startActivity` / `startActivityAsUser`，milink 实测走 `AsCaller/WithOptions/ForUser` 重载 → 漏网。
- beta58 改全前缀 `startsWith("startActivity")`，但**卡在 caller 白名单层**：通知点击走 PendingIntent 路径，`callingUid=10277`（PendingIntent 创建者=微信自己，非 milink）→ 判不在远程白名单 → 放行 u0。
- 且触发者根本不是 milink，是 **com.xiaomi.mirror**——beta58 的 3 秒时序反推（要求 3 秒内有 u999 打开记录）拦不住"第一发 u0"。

**源码依据（决定 beta59 方案成立）**：
- milink 反编译产物：`milink-jadx/sources/com/xiaomi/dist/notification/handler/data/NotificationData.java`（字段含 `PendingIntent pendingIntent` + `int notificationUid`）；
- `.../handler/NotificationController.java` 约行 423：接收端重建流转通知 `notificationData.pendingIntent() != null ? notificationData.pendingIntent() : getDialogIntent(...)`——**发送端重建的 999 PendingIntent 作为 parcelable 字段随流转数据带到接收端，接收端点击用它打开**。→ 所以在发送端把 contentIntent 改成 userId=999 的 PendingIntent，接收端点击就直接开分身，u0 没机会。
- 反编译目录（可复用）：`milink-jadx/sources`、`milink-decompiled`、`milink-os4/sources`、`apk_v058`、`apk_v059`。

**"伪装成 u0 如何识别"（用户两次追问，答案）**：判断发生在伪装之前——发送端 `markCloneTitle` 只看真实 `sbn.getUser().hashCode()==999` 才动手；u0 通知原样放行，不误伤。

### 2.2 beta59 三层防线（代码已落盘，全貌）

**第一层 · HookCloneBypass（发送端釜底抽薪，milink 进程）**：
- 只对分身通知（`sbn.getUser().hashCode()==999`）动手，把 `n.contentIntent` **重建为 `PendingIntent.getActivity(Context, requestCode, intent, flags, UserHandle(999))`**——全程反射（`ActivityThread.currentApplication()` / `UserHandle` 构造器 / PendingIntent 5 参重载，公共 stub 全 @hide）。
- intent 上打 `hf_is_clone` 标记，供第二层兜底识别。
- 期望日志：`clone contentIntent rebuilt -> user 999`。
- 代码：`hooks/HookCloneBypass.java`。

**第二层 · HookRemoteOpen（system_server，android/system 作用域）**：
- `redirectToClone` 判定顺序：① intent 有 `hf_is_clone` 标记（caller 无关，直接改 999）→ ② 远程白名单 caller 且 userId∈{0,-1}（直接改 999）→ ③ 3 秒时序反推（3 秒内有 u999 打开记录）兜底。
- 统一走 `redirect()` 入口：有 userId 参数的调用直接 `args.set(999)`；无 userId 参数的重载 `relaunchAsClone` **吞掉原调用重发 999**。
- **核心常量**：
  - `TARGET_PKGS = {com.tencent.mm, com.tencent.mobileqq, com.ss.android.lark, com.larksuite.cn, com.alibaba.android.rimet, com.tencent.wework}`（QQ/飞书/钉钉/企业微信分身同步覆盖）
  - `ALLOWED_CALLERS = {com.milink.service, com.xiaomi.mirror, com.hyperflowplus}`
  - `EXCLUDED_CALLERS = {com.miui.home, com.android.launcher3, com.miui.systemui, android}`（本地打开/系统 UI 永远放行）
  - `USER_CLONE = 999`；extra 名 `hf_is_clone`；`lastCloneOpenTime` 3 秒窗口
  - `locateIndices`：callingPackage=首个 String 参数；userId 仅 AsUser/AsCaller/ForUser 取末尾 int，否则 -1
- 期望日志：`clone open: marker hit` / `remote caller ... redirecting` / `clone open check: caller=... userId=... callingUid=...`。
- 代码：`hooks/HookRemoteOpen.java`。

**第三层 · HookMirrorObserve（观察，mirror/system_server）**：
- hook `android.app.IActivityTaskManager$Stub$Proxy` 的 startActivity* 家族（框架类不混淆），日志 `[MIRROR-OBS] ATM.<method>`——记录 mirror 进程实际调用了什么、什么参数。
- 目的：如果前两层没拦住，这层告诉你"mirror 到底走的哪个 API"。
- 代码：`hooks/HookMirrorObserve.java`。

### 2.3 当前阻塞：模块自身拦截日志从未抓到（机制已实锤，不是没打日志）

用户两次按抓包抓回日志（`hf_full.tgz`、`hf_b60.tgz`），**log/ 永远空**：
- **原因实锤**：LSPosed daemon 在 `rm -rf log` 后仍攥着旧文件句柄，新日志写进"已删除文件"；之后 `tar /data/adb/lspd` 只有 log.old，log/ 空。**两次都是这么抓的，两次都空**。
- logcat 里 0 条模块行：属**历史已知正常**（MiflowLog 优先走 libxposed 框架文件日志，不走 logcat）；但 logcat 里能看到 `LSPosedFramework: (com.milink.crossdeviceservice)...HookCallRelay` 这类模块行，证明 logcat 通道本身是通的。
- 用户最新 logcat（`hf_b60.txt`，2754 行）**双开现场实锤**，但**无任何 clone/MIRROR-OBS 模块行**：
  - `Task{#3746 type=standard A=10277:com.tencent.mm} userId=0`（u0 主微信被拉起）
  - `displayId=14 ownerPackageName=com.xiaomi.mirror`（镜像显示在场）
  - `uid=99910277 ... RequestorPkg=com.tencent.mm`（u999 分身进程在场）
  - `ClipboardService Denying ... user 0 / user 999`（两个用户空间并存）
- 结论：双开确实发生（u0 task + u999 分身 + mirror 并存），但 **`remote caller ... redirecting` 是否命中，至今无直接证据**。

---

## 3. 正确抓日志方法（唯一有效法，用户实测通道）

```bash
# 前提：已装 beta60+ 并重启（重启后模块文件日志才会新建）
# ① 复现：把手机流转/远控到另一台设备（或投屏），在那边点分身微信的通知 → 看到双开
#    （本地桌面打开不在 hook 范围，不会触发，别用本地三步复现）
# ② 复现完立刻打包（勿 rm、勿 sleep 空窗）：
su -c 'tar -czf /sdcard/hf_b60.tgz /data/adb/lspd/log; logcat -d > /sdcard/hf_b60_logcat.txt; echo 完成'
# ③ 把 /sdcard/hf_b60.tgz + /sdcard/hf_b60_logcat.txt 传给工具
```

**拿到日志后的判定（决策树）**：
| 看到什么 | 说明 | 下一步 |
|---|---|---|
| `clone contentIntent rebuilt -> user 999` 缺失 | 第一层没触发：sbn user 判断错 / 反射失败 | 查 HookCloneBypass 的 user 判断与反射 |
| 有 rebuilt 但双开 | 重建 PendingIntent 没随流转生效 | 查 NotificationController 重建路径是否用上 `pendingIntent()` 字段 |
| `remote caller ... redirecting` 缺失但 rebuilt 在 | 第二层 caller 白名单没命中 mirror | 查 callingPackage 定位逻辑（locateIndices） |
| `[MIRROR-OBS] ATM.<method>` 有但没拦住 | mirror 走的 API 不在 hook 范围 | 按方法名补 hook |
| 日志里完全没有任何模块行 | 模块没跑/作用域没生效 | 先验模块装载（§7），再谈逻辑 |

---

## 4. 代码地图（全量，换工具直接读）

```
hyperflow-miuix/app/src/main/java/com/hyperflowplus/
├── XposedEntry.java          # hook 装配总入口（新式 libxposed；system_server 经 onSystemServerStarting）
├── Config.java               # 全部配置键（注释已更新到 beta60）
└── hooks/
    ├── HookCloneBypass.java      # 分身通知放行+标题前缀+【关键】contentIntent 重建 999 PendingIntent
    ├── HookRemoteOpen.java       # 【关键】system_server 拦截 startActivity*，重定向到 999
    ├── HookMirrorObserve.java    # 【关键】ATM Proxy startActivity* 全路径观察
    ├── HookForceTransfer.java    # 亮屏流转
    ├── HookSmsPersist.java / HookSmsSenderEnrich.java  # 短信持久化
    ├── HookCallRelay.java / HookCallVoipType.java / HookCallFilterPass.java  # 来电流转
    ├── HookKeyguardCapture.java  # 【锁屏】凭证捕获（beta60 主通道 LockSettingsService.checkCredential）
    ├── CapturedCred.java         # 凭证存储：/data/system/hyperflow_captured + trustservice sp
    ├── HookTrustAuth.java / HookTrustAuthAuto.java / HookTrustAuthAutoFill.java  # 授权盘自动填
    ├── HookTrustAuthUiClose.java # 【锁屏副线B】授权 UI 自动关闭
    └── HookAutoUnlock.java       # milink 侧跳过验证（silent_auth）
```

模块作用域（modules_config.db 实读）：`com.hyperflowplus` / `com.milink.service` / `android`(system) / `com.xiaomi.mirror` / `com.xiaomi.trustservice` / `com.android.incallui`。

---

## 5. 锁屏密码自动捕获（副线 A，beta60 已改待验证）

**链路（beta57 日志 18:38:04 实锤闭环）**：
```
A-2: pending credential pin (len=6)
CapturedCred: pin -> sp-only          ← 公共文件写入失败仅 sp 成功（sp 够用）
HookTrustAuth: hit, orig=246810 -> inject=246810
A-2: HAT sent, auto ok
```

**用户澄清（重要，勿再误解）**：
- 之前捕获到的密码**不是 App 预存**、**不是系统锁屏读取**——是**授权 UI 手输被捕获落 sp**。
- **授权 UI 要的密码 = 系统锁屏密码本身**（用户确认同一把）。
- 用户目标原话：**"他在输入锁屏码的时候就把锁屏码记下来。后期在授权的时候，就直接用那个锁屏密码就行了，不会再弹任何一次授权盘"**。

**beta60 改动**：`HookKeyguardCapture` 主通道从 `LockPatternUtils.verifyCredential`（实测 0 捕获，非真实入口）改为 **`com.android.internal.widget.LockSettingsService.checkCredential(LockscreenCredential, int, int)`**——system_server 内锁屏输密码的真正验证入口，**重启后首次解锁必走**；`verifyCredential` 留兜底。捕获走 `CapturedCred.write(type, raw)` 同一套存储（公共文件 + sp），`HookTrustAuth`/`HookTrustAuthAutoFill` 自动填直接复用（`readPublic` → `readLocalSp`）。

**风险点（待实测）**：pattern 的 `LockscreenCredential.getCredential()` 返回点阵串，注入格式未验证（pin 已验证 len=6 数字串 OK）。

**trustservice 反编译关键类**：`A.d.e(String)` 提交入口；`A.d.a` int 类型码（1=PIN/2=图案/3=混合）；`Z0.b` 单例（g/h/c）；`netbus.h.n(int)` 校验收尾（0 成功）；`Q0.d` post 回传。

---

## 6. 授权 UI 自动关闭（副线 B，beta58 已修未验收）

- beta57 实锤失败：`HookTrustAuthUiClose: post-finish failed` + `LockScreenUIActivity.onResume not found`——混淆字段 f1753J / 混淆类名在用户 ROM 已变 → 验证成功但 UI 停留。
- beta58 修复：删 f1753J 反射 → 遍历 `ActivityThread.mActivities`（框架字段不混淆）finish 前台 trustservice/lockscreen Activity；hook 框架 `Activity.onResume` 按类名特征过滤；`closeAuthViaActivityThread()` 双重兜底。
- **未验收**。期望日志：`verify OK, UI closed via mActivities`。

---

## 7. 验证模块到底跑没跑（日志全空时先做这步）

- LSPosed 管理器 → HyperFlow → 查看作用域勾选（§4 六个）；若 mirror/trustservice 未勾，注入不会发生。
- 重启后 LSPosed 日志应出现 `=== HyperFlow loaded in system_server ===` 与 `HookRemoteOpen[system] installed` 等四条绿 hook（beta57 起用户实测过一次全绿截图）。
- 若连这都没有：查 `assets/xposed_init`（必须不存在，删了防 legacy 误判）、主 dex 是否有 XposedEntry（beta53 起 R8 单 dex 已解决，勿再动 multiDex）。

---

## 8. 已知死路（勿重试，全部实测否决）

1. 老版 LSPosed"新式进不了 system_server"（对 Vector 不成立，beta57 已证新式可进）
2. multiDexKeepProguard（AGP 9.4.1 忽略，beta52 实锤无效）
3. "db apk_path 过期"理论
4. systemless/KSU 预装（用户否决）
5. "主 dex 决定注入"（已由 R8 单 dex 解决）
6. **legacy 化路线**（beta55/56，用户实测否决"legacy 不影响功能"）
7. 给 LSPosed 打补丁分发（用户否决）
8. **`rm -rf log` 后抓包**（log/ 永远空，两次实锤——见 §3）

---

## 9. 验收标准（修好的定义，用户会拿这个回来验证）

| 主线 | 通过标准 |
|---|---|
| 双开 | 流转/远控到另一台设备点分身微信通知 → **只开 u999 分身**，u0 主微信不弹；本地三步复现（主微信→分身→锁屏再开）不误伤 |
| 锁屏捕获 | 重启后锁屏输密码解锁（pin/pattern 都要试）→ 之后远控授权**一次授权盘都不弹**，直接验证通过 |
| UI 自动关闭 | 授权验证通过后授权 UI **自动消失**（无需手点） |

回归：QQ/飞书/钉钉/企业微信分身同一套防线；普通通知流转/短信/来电功能不受影响。

---

## 10. 用户硬性偏好（换工具务必遵守）

- 方案必须带**源码依据**（反编译 milink/trustservice 实证 + 日志实锤），拒绝"我觉得/应该"。
- 抓日志要"开始抓→复现→结束"**全量一次到位**（完整文件/压缩包），**强烈反感 grep 过滤版**。
- 修好一条再动下一条；改完代码 → push beta → CI 出包 → 交付 flashable zip（`present_files`/网盘直链均可）。
- GitHub 实时同步进度 + 本文件保持最新，是**最低要求**（用户会随机换工具，每轮都可能是最后一句）。
- 用户设备：小米 15（远控 UI 显示"翰德姆的Xiaomi 15"）；用户身份：开发者本人，技术对话可直接上术语。

---

## 11. 日志/证据文件（原电脑，可复用）

- beta57 全量模块日志：`/tmp/lsp_log/log/modules_2026-10-30T18:35:01.342404.log`（1106 行核心证据）
- 用户最新 logcat：`.../attachments/hf_b60.txt`（2754 行双开现场）+ `hf_b60.tgz`（解包仅 log.old）
- LSPosed zip：`.../attachments/LSPosed_20261030_184110.zip`
- milink/trustservice 反编译：`/home/user/Doubao/chats/38445211817859330/milink-jadx`、`milink-decompiled`、`milink-os4`、`apk_v058`、`apk_v059`
- 用户截图（双提示复现）：https://aka.doubaocdn.com/s/W5F9thHGxj ；注入成功截图：https://aka.doubaocdn.com/s/SkC8JZ8ZAp
