# HyperFlow 交接文档（换工具前快照，2026-10-17，beta33/161）

> 用途：本文件供下一个接手的人/AI 快速续上。所有事实以本仓库代码 + `docs/trustservice-auth-chain.md` 为准；本快照之后的改动以 git 历史为准。

## 0. 一句话现状
- 仓库 `wjx2951874/HyperFlow`，工作分支 **`beta`**（正式通道 `main` 不动，`update.json` 停在旧正式版，测试期**不要改**，避免误推正式）。
- 已发最新测试版 beta32/160（CI 成功、用户已装）；本快照把一批 UI 小改 + A-2 主动校验代码写好、本地 Kotlin+Java 编译通过，**尚未 commit/push/出包**（见第 7 节待办动作）。
- 两条主线：① UI 小改收尾；② **A 方案：纯后台静默自动校验锁屏密码、拿真 HAT 回传对端**（不模拟点击、不伪造成功、不碰 UI）。

## 1. 项目是什么
小米澎湃 OS 互联增强：Xposed 模块（通知/短信流转、分身双开标识）+ miuix 原生 UI App。被控端 root（Note12 Turbo），发起端小米15（无 root 走 Shizuku），另有 iPhone。

## 2. 目录与构建
- UI：`hyperflow-miuix/app/src/main/kotlin/com/hyperflowplus/`
- hook：`hyperflow-miuix/app/src/main/java/com/hyperflowplus/hooks/`
- 本地**轻量编译**（`assembleRelease` 必 OOM 137，只能走 CI），必须**同时编 Kotlin 和 Java**（只编 Kotlin 会漏 Java import，CI 才暴露，教训见 beta32 run 39032680213）：
```bash
cd hyperflow-miuix
JAVA_HOME=/home/user/.doubao_tools/jdk-21.0.12.1+1 \
ANDROID_HOME=/home/user/.doubao_tools/android-sdk \
/home/user/.doubao_tools/gradle-9.6.0/bin/gradle \
:app:compileReleaseKotlin :app:compileReleaseJavaWithJavac \
--no-daemon --offline --no-watch-fs -q
```
- CI：`.github/workflows/build.yml`，dispatch `POST /repos/wjx2951874/HyperFlow/actions/workflows/build.yml/dispatches` body `{"ref":"beta"}` 返回 204；artifact 名 `HyperFlow-flashable`。
- 版本单一事实来源两处必须同步：`hyperflow-miuix/app/build.gradle.kts`（versionCode/versionName）+ `hyperflow-miuix/ksu-module/module.prop`。xposed resources 的 module.prop 无 version 字段，不改。

## 3. 设备与角色
- Note12 Turbo（root，被控/mirror，装模块，trustservice 在此弹授权盘）。
- 小米15（无 root，发起连接）。
- 本机锁屏经日志核实 = **6 位数字 PIN**（`NUMERIC / credential is Pin / localPasswordType:3 / typePin:3 / localPasswordLength:6`）。用户曾在录入页手选成"密码"，实际应选 **PIN**；录入页不能自动识别类型，需用户手选。
- 预存凭证已与真实锁屏一致（用户确认，手动复现也对）。

## 4. A 方案 · 主动静默校验（核心进行中）

### 4.1 目标
远控/镜像连接、本机 trustservice 弹授权盘时，**后台直接用预存凭证走系统 gatekeeper 真实校验**，拿到真 HAT 交给回传代理，自动解锁，无需手动输密码、不模拟点击 UI。错密码由系统真实判，会罚时，因此只提交一次、不循环重试。

### 4.2 已逆向校验链（反编译 OS4 MiTrustService.apk，产物在仓库外 `trust-os4/src/sources`，jadx 工具在 `trust-os4/tools/bin/jadx`）
- 会话建立（hook 点）：混淆类 `Z0.b`（= LockSettingUtils）
  - `g(Y0.g, byte[] challenge, String deviceId, String branch, int)` → f489k=1（g 代理，Bind_A 系）
  - `h(k, byte[] challenge, String deviceId, String branch)` → f489k=2（k 代理，Bind_B/Risk_B）
- 校验方法 `Z0.b.d(bVar, LockscreenCredential cred, h ui, byte[] challenge, String branch)`：
  1. `LockPatternUtils.verifyCredential(cred, 0, 1)` → VerifyCredentialResponse；**SDK≥35 用 `isMatched()`**（本机 SDK37），≤35 才用 getResponseCode。
  2. 匹配失败 → 罚时，不回传。
  3. 成功 → `getGatekeeperPasswordHandle()` → `verifyGatekeeperPasswordHandle(handle, e(challenge), 0)` → `getGatekeeperHAT()`。
  4. `e(challenge)`：前 8 字节 **little-endian** 转 long（`j |= (b[i]&255)<<(i*8)`）。
  5. 按分支回传（f489k=2，k 代理）：`Risk_B→k.x(hat)`、`Bind_B→k.j(hat)`、`Bind_A→k.l(p0c, hat)`（两参，暂未自动）；f489k=1（g 代理）：`Bind_A→g.c(hat)`、`Bind_B→g.e(hat)`、`Auth_Risk_B→g.v(hat)`。
- 凭证构造复用系统自己的 `Z0.b.c(int type, String str)`：**3=createPin / 4=createPassword / 1=createPattern**（1 内部 `byteArrayToPattern(str.getBytes())` 得 List 再 createPattern）。

### 4.3 A-1 实测日志（beta32 已抓，a1_key.log）
- hook 命中：`A-1 session establish [h] args=[k@1207ce0, byte[8], "7528A0D1", "Risk_B"]`，单例 class=Z0.b，静态分支 `f489k=2 / f490l="Risk_B"`。
- 单例实例字段（运行时按类型，**勿信 jadx 占位名 f494g 等**）：
  - Class a、Class b（两个 Class 引用）
  - `LockPatternUtils c`（=f491c，校验用）
  - `MiuiLockPatternUtils d`
  - `g e = null`（f492e，本分支空）
  - `k f = k@1207ce0`（f493f，回传代理 = h() 的 args[0]）
  - `byte[] g = byte[8]`（f494g，challenge，正好 8 字节）
  - `String h = "7528A0D1"`（deviceId）
  - `int i`
- 本次走 **Risk_B**（日常连接的风险信任授权，非首次配对 Bind_A/B）。手动成功链路：checkPassword → verifycredential for HAT → gatekeeperd verify success → lockscreen verify success → riskAuthSuccess → `k.x(hat)`（setRiskHATV2）→ riskResponse 0 → auth success, call UI to unlock → onAuthResult。

### 4.4 当前 A-2 代码状态（`hooks/HookTrustAuthAuto.java`，本地编译通过）
- install 门控：`Config.KEY_SILENT_AUTH="silent_auth"`（功能页"妙享直通"开关），关闭则 skip。
- hook `Z0.b.h`，`proceed()` 后开后台线程 `autoVerify(proxy=args[0], challenge=args[1], branch=args[3])`。
- 取凭证 `Config.getAuthType()/getAuthSecret()(AES-GCM解密)/getAuthPattern()`；复用 `b.c(type,str)` 构造，**PIN/password/pattern 三型都走系统构造器**。
- verifyCredential → isMatched → HAT → 代理回传：`Risk_B→x`、`Bind_B→j` 已自动；`Bind_A`(l 两参)、f489k=1 分支先打日志放行。
- **失败计数**：静态 `AtomicInteger failCount`，连续失败达 `MAX_FAIL=3` 后本进程放弃自动、回退手动（不主动重复提交，避免罚时）；成功归零。
- 异常一律 catch 放行，不拦原 UI。

### 4.5 A 线待办（下一棒）
1. **实机联调 beta33**：开妙享直通、PIN 录入正确，发起连接看日志 `A-2: SUCCESS, HAT(...B) sent via k.x, auto ok`；若 mismatch 会打 `fail=n/3, pass through`。
2. 补 `Bind_A→k.l(p0c,hat)`（两参，第二参 p0c=Context 单例 `P0.c.f234v.a()`）。
3. 图案分支实机验证（走 b.c(1,pattern)，点编号串 1..9 按连线拼接）。
4. 观察错密码罚时行为，必要时把 failCount 改成 per-boot（`isRiskAuthAlreadyDoneForCurrentBoot` 同口径）。
5. 被动版 `hooks/HookTrustAuth.java`（hook `A.d.e(String)` 替换入参喂给 UI）保留作兜底。

## 5. 已收口的结论
- **弹窗闪退（beta31 根治，commit 15ba6e0）**：miuix 菜单 `NavigationBackHandler` 需 `LocalNavigationEventDispatcherOwner`，菜单提层后读不到 null 即 `IllegalStateException: No NavigationEventDispatcher was provided`。根治 = `AppNavigation` 最外层 `CompositionLocalProvider` 提供兜底空 `NavigationEventDispatcher()`。已全局不再复发。
- **消息排序菜单拆分（beta32）**：旧菜单把"依据(发送人/时间)"和"方向(正/倒序)"混在一个单选，出现双勾。已拆两组互斥单选，存储格式不变（name_asc/name_desc/time_asc/time_desc）。会话详情本就是干净两项。

## 6. 本轮未提交的 UI 小改（已编译通过）
- 底部导航第 2 tab「流转」→「功能」（label + 顶部 title 同步），页内"通知流转"分组名保留。
- 设置页「版本」标题 FontWeight.Medium（原 Regular 偏细）。
- 双开前缀条目 summary 改「现在为【内容】，点击可修改」。
- 前缀编辑弹窗：`HyperDialog` 新增 `centered` 参数（true=屏幕居中、不套 bottomInset；false=原底部，默认不变，其他 20+ 调用方不受影响）；前缀弹窗 centered=true + 说明"标题前自动包进【】，留空用默认「双开」"，删 imeBottom 随键盘飘移。
- 外观"跟随系统"**不需要实时切换**（用户撤销，勿再加回）。

## 7. 提交前待办动作（下一棒直接做）
1. 本地已编译通过；核对 `changelog-latest.md` 头部改为 beta33 并写清 A-2 + UI 小改。
2. `git add` 改动（MainActivity.kt / FlowScreen.kt / HyperDialog.kt / SettingsScreen.kt / HookTrustAuthAuto.java / build.gradle.kts / ksu-module/module.prop / docs）。
3. commit → push `beta` → dispatch CI → 等 success → 取 artifact `HyperFlow-flashable` 交付。
4. 注意 CI 必须 Java+Kotlin 都编过（漏 import 会 cannot find symbol）。

## 8. 抓日志命令（root Termux，先启动后复现，别先贴停止命令）
```bash
su
logcat -c
nohup logcat -v threadtime > /sdcard/a1.log 2>&1 &
# 对端发起连接、本机弹授权盘；过完后：
pkill -f "logcat -v threadtime"
grep -E "HyperFlowPlus|A-1|A-2|MiTrustService|gatekeeper|verifyCredential" /sdcard/a1.log > /sdcard/a1_key.log
```
日志走 LSPosed 管理器日志页（TAG=`HyperFlowPlus`），回退 android.util.Log。

## 9. 遗留登记（未开始，勿丢）
- 消息页三点级联菜单改 HyperIsland 样式（miuix 自带 OverlayIconCascadingDropdownMenu，无需加依赖）。
- 电话流转：锁屏可、亮屏不行（待抓日志）。
- 乙方案分身 hook 跳过搬 u0 主微信 task；1.2.0 双向对等对控；隐私合规 A4/A3；工程质量 B1-B3；消息页改造 M14-M19。
- 已知噪音：HookSmsPersist install failed（用户未提，不动）。

## 10. 关键陷阱
- jadx 混淆字段/方法名运行时不保留，**反射一律按类型匹配或用方法名+参数类型**，不硬编码占位名。
- 本地只编 Kotlin 会漏 Java 符号；assembleRelease 本地 OOM，只能 CI。
- `update.json` / main 正式通道测试期不动。
- 密码类错了会被系统罚时，自动校验只提交一次、失败计数封顶 3，不循环。
