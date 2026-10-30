# HyperFlow 交接文档（HANDOFF）

> 更新：2026-10-30，beta60（versionCode 188）后
> 交接原因：用户可能随机更换 AI 工具继续开发，务必先读本文件

---

## 0. 一句话现状

**三条主线：**
1. **分身/双开问题**：beta59 已落地"发送端重建 999 PendingIntent"根治方案（釜底抽薪），**用户实测仍双开，等待 beta60 全量日志定位**——beta60 分身逻辑与 beta59 完全一致，日志有效。
2. **锁屏密码自动填**：beta57 实锤授权链路闭环；用户澄清来源=授权 UI 手输被捕获；**beta60 把捕获主通道改成系统锁屏真正验证入口 `LockSettingsService.checkCredential`**（重启后首次解锁必走，捕获锁屏密码→授权 UI 自动填，一次都不再弹）。
3. **授权 UI 自动关闭**：beta58 已修（mActivities 兜底），**未经用户验收**，随新包一起验收。

---

## 1. 仓库与构建（当前环境事实，勿凭旧文档）

- 公开仓库：`wjx2951874/HyperFlow`；**CI/交付分支 `beta`**（origin/beta 当前 = beta60 fd2eff2）；main 是旧线，仅 HANDOFF 同步。
- 本地根目录：`/home/user/Doubao/chats/38445211817859330/hyperflow`；**工程 `hyperflow-miuix/`**；本地工作分支 `beta54-restore`（推送到 beta 用 `git push origin beta54-restore:beta`）。
- 版本事实源：`app/build.gradle.kts`（versionCode/versionName）+ `ksu-module/module.prop`（同 bump）。当前 **188 / 1.1.0-beta60**。
- 编译命令（无 gradlew wrapper，用绝对路径 gradle；**禁 assembleRelease 会 OOM**）：
  ```bash
  cd /home/user/Doubao/chats/38445211817859330/hyperflow/hyperflow-miuix && JAVA_HOME=/home/user/.doubao_tools/jdk-21.0.12.1+1 ANDROID_HOME=/home/user/.doubao_tools/android-sdk /home/user/.doubao_tools/gradle-9.6.0/bin/gradle :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --no-daemon --offline --no-watch-fs -q
  ```
- CI：GitHub Actions `build.yml`（分支 beta）。触发：
  ```bash
  TOKEN=$(git remote get-url origin | sed -E 's#https://([^@]+)@.*#\1#')   # remote 内嵌 PAT
  curl -s -o /dev/null -X POST -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" -d '{"ref":"beta"}' https://api.github.com/repos/wjx2951874/HyperFlow/actions/workflows/build.yml/dispatches
  ```
  取包：`/actions/runs?branch=beta&per_page=1` → 最新 run → `/actions/artifacts/<id>/zip` → 解出 `HyperFlow-flashable.zip` → 改名交付。
- **近期交付物（本机路径）**：
  - `HyperFlow-1.1.0-beta58-flashable.zip`（186，已交付，双开仍复现）
  - `HyperFlow-1.1.0-beta59-flashable.zip`（187，已交付，用户实测仍双开）
  - beta60（188）已 push + dispatch，取最新 artifact 交付

---

## 2. 分身/双开主线（当前最前线）

### 2.1 已实锤的根因链（beta57 全量模块日志，`/tmp/lsp_log/log/modules_2026-10-30T18:35:01.342404.log`）
- 注入成功：beta57 起 system_server 注入 OK（`onSystemServerStarting` + 四条绿 hook）。
- **点分身通知启动微信时 `callingUid=10277`（=u0 微信自己）**：通知点击走 PendingIntent 路径，Binder 身份是 PendingIntent 创建者（微信自己），**不是 milink** → `findCallingPackage` 判为不在远程白名单 → 放行 u0 → 主微信被开 → 双开。
- 全前缀 hook `startActivity*` 没用（beta58），卡在 caller 白名单层。

### 2.2 beta59 三层防线（代码已落盘、已交付、待日志验证）
1. **HookCloneBypass 发送端釜底抽薪**（关键）：`markCloneTitle` 只对 `sbn.getUser().hashCode()==999` 的分身通知动手，把 `n.contentIntent` **重建为 `PendingIntent.getActivity(..., UserHandle(999))`**（全程反射：ActivityThread.currentApplication / PendingIntent.getActivity(Context,int,Intent,int,UserHandle) / UserHandle 构造器——公共 stub 全 @hide）。点击直接开 999，u0 无机会。
   - **源码依据（决定方案成立）**：milink 反编译 `milink-jadx/sources/com/xiaomi/dist/notification/handler/data/NotificationData.java`（字段含 `PendingIntent pendingIntent` + `int notificationUid`）与 `NotificationController.java` 约行 423：接收端重建流转通知 `notificationData.pendingIntent() != null ? notificationData.pendingIntent() : getDialogIntent(...)` ——**发送端重建的 999 PendingIntent 作为 parcelable 字段随流转数据带到接收端，接收端点击用它打开**。
   - **u0 不误伤**：判断基于通知所属 user（999 才动手），u0 通知原样。
2. **HookRemoteOpen 重排**：`redirectToClone` 先查 intent 上 `hf_is_clone` 标记（caller 无关直接改 999）；再查远程白名单 caller（ALLOWED_CALLERS={com.milink.service, com.xiaomi.mirror, com.hyperflowplus}）且 userId∈{0,-1} 直接改 999；最后 3 秒时序反推兜底。统一走 `redirect()` 入口（有 userId 参数直接改 / 无则 `relaunchAsClone` 吞调用重发 999）。EXCLUDED_CALLERS={com.miui.home, com.android.launcher3, com.miui.systemui, android} 永远放行。TARGET_PKGS={com.tencent.mm, com.tencent.mobileqq, com.ss.android.lark, com.larksuite.cn, com.alibaba.android.rimet, com.tencent.wework}（QQ 等分身同步覆盖）。
3. **HookMirrorObserve 全路径观察**：新增 `android.app.IActivityTaskManager$Stub$Proxy` 的 startActivity* 家族 hook（框架类不混淆），日志 `[MIRROR-OBS] ATM.<method>` 显示 mirror 进程实际调用。

### 2.3 当前阻塞：等待用户 beta60 有效日志（抓法已修正，勿再 rm log）

**最新实锤（2026-10-30 晚，两次日志分析）**：
- **双开触发点实锤**（beta58 时代 log.old 18:57-19:10）：
  ```
  19:07:27  clone open check: caller=com.xiaomi.mirror pkg=com.tencent.mm userId=0
  19:07:29  clone open: uid=10277 pkg=com.tencent.mm   ← u0 主微信被 mirror 进程拉起
  ```
  双开 = **com.xiaomi.mirror（镜像/远控/投屏）以 userId=0 启动微信**；beta58 时序反推（3 秒内有 u999 记录）拦不住第一发 u0；**beta59 的"远程 caller 直接改 999"正是治这条**。
- **日志抓取方法两次实锤**：`rm -rf /data/adb/lspd/log` 后 LSPosed daemon 仍攥旧文件句柄，新日志写进"已删除文件"，之后 tar `/data/adb/lspd` 永远只有 log.old、log/ 空。**禁止 rm -rf log**（用户两次 tgz 均为空即此因）。
- logcat 通道 0 行模块日志属正常（MiflowLog 优先 libxposed 框架文件日志，不走 logcat）；logcat 里的 `LSPosedFramework: (pkg)...` 行仅部分 hook（如 HookCallRelay）会出。

**正确抓法（装 beta60 重启后）**：
```bash
# 复现（流转/远控到另一台设备，在那边点微信，看到双开）——本地打开桌面不在 hook 范围，不会触发
# 复现完抓（勿 rm、勿 sleep 空窗，复现后再打包）
su -c 'tar -czf /sdcard/hf_b60.tgz /data/adb/lspd/log; logcat -d > /sdcard/hf_b60_logcat.txt; echo 完成'
```
期望关键日志行：
- `clone contentIntent rebuilt -> user 999`（第一层触发）
- `clone open: marker hit` / `remote caller ... redirecting`（第二层命中）
- `[MIRROR-OBS] ATM.`（第三层记录 mirror 实际调用）
- `clone open check: caller=... userId=...` + `callingUid=...`（双开时谁起的、哪个用户）

**拿到日志后**：若重建未生效→查 HookCloneBypass 的 sbn user 判断/反射失败；若 ATM 显示 mirror 走未知 API→按方法名补 hook；若 B 机回传意图完全绕过→在回传处理点精确 hook。

### 2.4 历史死路（勿重试）
①老版 LSPosed"新式进不了 system_server"（对 Vector 不成立）；②multiDexKeepProguard；③"db apk_path 过期"；④systemless/KSU 预装（用户否决）；⑤"主 dex 决定注入"；⑥legacy 化路线（beta55/56，用户实测否决）；⑦给 LSPosed 打补丁分发（用户否决）。

---

## 3. 锁屏密码自动填（副线 A，beta60 已改待验证）

### 3.1 链路（beta57 日志 18:38:04 实锤闭环）
`A-2: pending credential pin (len=6)` → `CapturedCred: pin (len=6) -> sp-only`（公共文件 /data/system/hyperflow_captured 写入失败仅 sp 成功，sp 够用）→ `HookTrustAuth: hit, orig=246810 -> inject=246810` → `A-2: HAT sent, auto ok`。

### 3.2 用户澄清 + 新需求（重要）
- **来源**：不是 App 预存（Config 路径不打 CapturedCred 日志），不是系统锁屏读取（旧 HookKeyguardCapture 0 捕获，用户解锁走指纹/面部）——是**授权 UI 手输被 Z0.b.c/A-2 捕获落 sp**。
- **授权 UI 要的密码 = 系统锁屏密码本身**（用户确认同一把）。
- **用户目标**：**系统锁屏输密码解锁时捕获** → 授权 UI 自动填 → **一次授权盘都不弹**。
- **beta60 改动**：`HookKeyguardCapture` 主通道从 `LockPatternUtils.verifyCredential`（0 捕获，非真实入口）改为 **`com.android.internal.widget.LockSettingsService.checkCredential(LockscreenCredential,int,int)`**（system_server 内 Keyguard 输密码真正验证入口，**重启后首次解锁必走**）；`verifyCredential` 留兜底。捕获走 `CapturedCred.write(type, raw)` 同一套存储（公共文件 + trustservice sp），`HookTrustAuth` 自动填直接复用（`readPublic` → `readLocalSp`）。
- **格式注意**：pattern 的 `LockscreenCredential.getCredential()` 返回点阵串；A.d.e(String) 注入格式待用户实测（pin 已验证 len=6 数字串）。

### 3.3 关键类速查（trustservice 反编译实锤）
`A.d.e(String)` 提交入口；`A.d.a` int 类型码（1=PIN/2=图案/3=混合）；`Z0.b` 单例（g/h/c(int,String)）；`netbus.h.n(int)` 校验收尾（0 成功）；`Q0.d` 负责 post（超时/通知/起线程回传 HAT）；LockScreenUIActivity 静态字段 f1753J（已失效，勿再用）。

---

## 4. 授权 UI 自动关闭（副线 B，beta58 已修未验收）

- beta57 实锤：`HookTrustAuthUiClose: post-finish failed` + `A-2: LockScreenUIActivity.onResume not found`——混淆字段 f1753J / 混淆类名在用户 ROM 已变 → 验证成功但 UI 停留。
- beta58 修复（已交付）：删 f1753J 反射，改 `ActivityThread.mActivities`（框架字段不混淆）遍历找前台 trustservice/lockscreen Activity 并 finish；HookTrustAuthAuto 的 hookAuthUi 从混淆类 `...LockScreenUIActivity.onResume` 改为 hook 框架 `Activity.onResume` 按类名特征过滤；新增 `closeAuthViaActivityThread()` 双重兜底。**未验收**，期望日志 `verify OK, UI closed via mActivities`。

---

## 5. 用户偏好/情绪（trace 依据，务必遵守）

- 全程诚实不先打保票；反感反复抓日志、**强烈反感 grep 过滤版**（"抓得不对"）；要求"开始抓→复现→结束"全量一次到位（完整文件/压缩包，不贴文本）。
- 要"最新 libxposed + 注入 system + 参考同类 LSP 模块源码"；已两次追问 u0/u999 区别与流转伪装下如何识别 999——**方案必须有源码依据支撑**。
- 口语化不耐烦（"你别逼我"、"有点生气"），换工具后先读本文件再动手，别让他重复解释背景。
- 换工具后 GitHub 实时同步进度 + 本文件是最低要求。

## 6. 日志/证据文件（本机）

- beta57 全量模块日志：`/tmp/lsp_log/log/modules_2026-10-30T18:35:01.342404.log`（1106 行核心证据）。
- LSPosed zip 附件：`/home/user/.doubao/agent_mode/workspace/.sessions/38445211817859330/attachments/LSPosed_20261030_184110.zip`
- milink 反编译（可复用）：`/home/user/Doubao/chats/38445211817859330/milink-jadx/sources`（jadx）、`milink-decompiled`、`milink-os4/sources`、`apk_v058`、`apk_v059`、`settings-apk`。
- 用户最新截图（双提示）：https://aka.doubaocdn.com/s/W5F9thHGxj
