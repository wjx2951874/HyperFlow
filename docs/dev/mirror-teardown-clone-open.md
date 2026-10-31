# com.xiaomi.mirror 拆解：分身通知/镜像打开为何落到主空间(u0)

> 来源：用户提供 `MirrorOS4.apk`（22M），jadx `--no-res` 解包到工作区 `mirror-jadx/`。
> 目的：定位「跨设备点分身微信 → 先跳主微信」的真实决策点，替代旧的 system_server HookRemoteOpen。
> 对应日志：`clone_full.log`（12:56:12 苹果 ECD92C64 / 12:56:40 小米15 90266701）。

## 1. 结论先行

旧 `HookRemoteOpen`（hook system_server 的 AMS/ATMS.startActivity，按 Binder.getCallingUid 反查包名白名单）
**全程零命中**：远程打开根本不是 system_server 普通 startActivity 触发，而是 **com.xiaomi.mirror 进程自己
用 u0 的 Context 重新解析并拉起**。mirror 进程运行在主空间，`getPackageManager()` 只解析 u0，
所以无论远端点的是不是分身通知，本机都被拉成主微信。

## 2. 两条通道（远端能力不同）

| | 通道A 纯镜像（iPhone ECD92C64, deviceType=11） | 通道B 子屏/接力（小米15 90266701, AndroidPhone, handoff_switch=true, is_support_send_app=true, is_subscreen_enabled=1） |
|---|---|---|
| 入口 | CallProvider.launchAppFromPendingIntent → RelayAppLocalHelper.openAppMirrorByRelay | 同左，额外在本机物理屏 display0 也起一发 |
| 落地 | u999 ChattingMainUI 被系统预热后 circulate 到虚拟屏8 | 先 u0 主 LauncherUI(A=10277,display0,41.319) → 再 u999 ChattingMainUI moveTaskToFront 到屏9(41.456) |
| 现象 | 落在主 | 先闪主、再到分身，两个微信都被控 |

两次通知 `notificationKey` 都带 `999|` 前缀、uid `99910277`（分身通知本身正确），差异不在通知。

## 3. mirror 内三处「自己 new Intent 起 u0」的点（根因都在解析层）

### 3.1 RelayAppLocalHelper（混淆类 `com/xiaomi/mirror/relay/C0689s0.java`）
- `n0(...)` = 日志里的 `openAppMirrorByRelay`（1826 行）：
  - 1846 `o7 = q4.u0.o(0, y7)`、1851 `ComponentName L7 = q4.u0.L(0, i8)`，第一个参数硬编码 `0`（user 0）。
  - 1853 `L7 = q4.B0.d(appContext, y7)`（Android 15+ 分支）。
- `D0(ComponentName, int displayId, String deviceId)`（1224 行，日志 `startActivity`）：
  - 1230 `ActivityOptions.makeBasic().setLaunchDisplayId(i7)`；
  - 1231-1232 `new Intent(); intent.setComponent(componentName);`
  - 1238 `intent.putExtra("android.intent.extra.xspace_userid_selected", false);`
  - 1244 `Mirror.C().startActivity(intent, launchDisplayId.toBundle());` —— **无 UserHandle、无 startActivityAsUser，默认 u0**。
- `E0(...)` = 日志 `startActivityInner`（1251 行）：1255 先判 `H3.g.s(...)` / `AbstractC1535k.l(...)`，不满足才走 D0。

### 3.2 ShareNotificationHelper.launchTargetApp（`H3/g.java` x()，~593 行）
- 对应日志 `ShareNotificationHelper: notiNotificationReceivedToRemote: LaunchInfo{mPackageName=com.tencent.mm, mPendingIntent=...}`。
- `B0.d(Mirror.C(), str2)` 解析 → `new Intent().setComponent()` → `xspace_userid_selected=false` → `Mirror.C().startActivity(intent, options)`，同样 u0。

### 3.3 CallProvider.o(String pkg)（`provider/CallProvider.java` 2304 行，launchTargetApp）
- `B0.d(Mirror.C(), str)` → new Intent → xspace false → `Mirror.C().startActivity(intent)`，u0。

### 3.4 解析层根因 `q4/B0.d(Context, String pkg)`（55 行）
```
pm = context.getPackageManager();
intent = MAIN + LAUNCHER, setPackage(pkg);
resolveInfo = pm.resolveActivity(intent, 0);   // 普通 PM，只返回当前 user(0) 的 launcher
return new ComponentName(pkg, resolveInfo.activityInfo.name);
```
**普通 PackageManager 看不到 user 999 的分身组件**，所以即便想指 999，这里也解析不出分身 LauncherUI。

## 4. 唯一「自带 user」的路径：通知 PendingIntent

- `com/xiaomi/mirror/utils/SystemOsHelper.h(PendingIntent, Bundle)`（121 行，sendPendingIntentCompat）：
  SDK34+ `pendingIntent.send(bundle)`，否则 7 参 `send(...)`。**裸转发，不决定 user**；user 由 PendingIntent 创建时绑定（分身通知的 PendingIntent 属于 999）。
- `com/xiaomi/mirror/d0.java` d()（182 行）：`MirrorMenu.getPendingIntent()` → `setLaunchDisplayId` → `pendingIntent.send(...)` 到子屏。
- CallProvider 1429/1452 MI_CLAW 分支也经 `SystemOsHelper.h(pendingIntent, bundle)`。

## 5. 候选 hook 点（待运行时日志确认哪条真正被走到）

- **路线甲（mirror 进程内改解析+起 user，最彻底）**：把 `q4.B0.d` 换成 `resolveActivityAsUser(999)`，
  并在 D0 / H3.g.x / CallProvider.o 的 startActivity 上带 `UserHandle(999)` 或走系统跨 user 启动。
  前提：mirror(uid 1000/system) 是否被允许跨 user 启动、ActivityOptions 是否支持 setLaunchUser，需运行时验证。
- **路线乙（只转发原 PendingIntent，不自起 u0）**：拦 SystemOsHelper.h / CallProvider.launchAppFromPendingIntent，
  命中分身通知时抑制 3.1/3.2/3.3 的自起 u0，直接 send 原 999 PendingIntent。改动小，但需确认远端是否真的把 999 PendingIntent 完整带到本机。
- **路线丙（system_server 兜底增强，复用旧 HookRemoteOpen）**：旧白名单按包名反查失败（callingUid=1000 mirror）。
  改为：目标包∈TARGET 且 999 已双开、调用方为 mirror/milink(uid 1000)、落在 mirror 虚拟屏时，直接把 user 改 999；
  需严格排除本机桌面手动开主微信。

## 6. 待运行时确认（需在 mirror 进程加临时日志）
1. 点分身通知后，到底走 3.1/3.2/3.3 哪一个自起，还是只走第 4 节 PendingIntent.send。
2. 远端带来的 PendingIntent 是否仍是 999（未被 milink 重新包装成 u0）。
3. mirror 进程跨 user(999) startActivity 是否被系统允许。

## 7. 作用域现状
- 模块当前只在 `android`(system_server)、`com.xiaomi.trustservice`、`com.milink*` 装配（XposedEntry.onPackageLoaded）。
- `com.xiaomi.mirror` **未在作用域**（arrays.xml），路线甲/乙需先把它加进 LSPosed 作用域并在 XposedEntry 加 mirror 分支。
