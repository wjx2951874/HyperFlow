# 分身通知点击跳主微信 · 运行时实测证据（2026-10-17）

> 观测包 `HookMirrorObserve`（只读，挂 com.xiaomi.mirror）复现两次：苹果 ECD92C64（display2）、小米15 90266701（display3）。
> 日志：mirror_obs_iphone.txt / mirror_obs_mi.txt（全量 logcat，已 grep 复核）。

## 结论先行

通知点击其实走了**两条互相打架的路径**：

- **A 正路（正确）**：`ShareNotificationHelper.launchApp → trySend → SystemOsHelper.sendPendingIntentCompat(PendingIntent, launchDisplayId=虚拟屏)`。
  通知 contentIntent 绑定 user 999，系统据此把 **u999 ChattingMainUI(task2968)** 拉上虚拟屏。证据：
  - 苹果 161 `ActivityRecord{... u999 com.tencent.mm/.ui.chatting.variants.ChattingMainUI}`、169 `Task #2968 A=99910277 on display 2`、183 `FgBgScene -> uid=99910277`。
  - 小米 260/273 `u999 ChattingMainUI t2968 displayId=3`。
- **B 错路（多余且有害）**：`RelayAppLocalHelper.openAppMirrorByRelay(n0)` **固定查 user 0**，取到 u0 主微信 `LauncherUI(task2964)`，建副屏后经 AMS `moveRootTaskToDisplay` 把 **u0 task2964 搬上虚拟屏并置顶**，覆盖掉 A。

## n0 为什么固定拿 u0（反编译 C0689s0.java:1846-1851）

```java
o7 = q4.u0.o(0, y7);            // 第一参硬编码 0 = user0，取主空间前台 taskId
iArr = new int[]{o7};
ComponentName L7 = q4.u0.L(0, i8); // 同样 user0 取组件名 → LauncherUI
...
r0(str, i8, iArr2, componentName, y7, ...); // 建副屏 + 搬该 u0 task
```

`openAppMirrorByRelay -> foreground activity, ComponentInfo{com.tencent.mm/ui.LauncherUI}`（日志 117/129）就是这一步。它是"镜像前台 app"通用路径，不区分本次是不是分身通知。

## 两次结局

| 设备 | 时序 | 最终前台 |
|---|---|---|
| 苹果 display2 | A 先上 u999 ChattingMainUI(161) → B 的 u0 LauncherUI task2964 被 moveRootTaskToDisplay 上屏(216/218/288) → 408 input focus=u0 LauncherUI，559 u999 task2968 visible=false | **u0 主微信赢**，看到主 |
| 小米 display3 | B u0 task2964 先 focus(196) → A u999 task2968 短暂 focus(401/441) → u0 task2964 被应用锁 AppLockActivity 接管留屏(572/623/1006) | **先闪主→到分身→又回主/锁**，两个 task 都在屏 |

## 关键调用栈（B 搬 task）

- 苹果 218 / 小米 178：`RootWindowContainer.moveRootTaskToDisplay → ActivityTaskManagerService.moveRootTaskToDisplay → IActivityTaskManager$Stub.onTransact`（调用方 com.xiaomi.mirror）。
- A 的 send 栈：`SystemOsHelper.h ← relay.s0.E0(startActivityInner) ← s0.C ← d0.a`（观测 hook 已抓到 PendingIntent.send）。

## 修法评估

- **乙（首选）**：通知点击(openMirrorScreenFrom=IPHONE_CROSS_DEVICE_NOTIFICATION)且目标属分身白名单时，**保留建虚拟屏，只跳过 B 把 u0 task2964 搬上屏/置顶**，让 A 的 u999 PendingIntent 独占虚拟屏。普通"妙享桌面点图标"非通知来源，仍走 n0，不受影响。
  - 注意：不能整个 return n0，否则虚拟屏没建、A 的 launchDisplayId 落空；要只拦"搬 u0 task / 置顶"动作。
- **甲（不推荐）**：把 n0 里 `q4.u0.o/L` 的 0 动态改 999。n0 是通用镜像路径，影响 car/普通镜像，且 A 已直接 send，仍可能双 task 冲突。
- **丙（备选兜底）**：system_server 拦 `moveRootTaskToDisplay`，被搬 u0 task 且本次通知属 999 则拒。需跨进程维护会话态，复杂。

## 待实现确认点

1. n0 入参无 openMirrorScreenFrom 标记；需在 `ShareNotificationHelper.launchAppPageForRemoteNotification`(H3/g.java:545) 置一个"本次通知点击"会话标记，n0/r0 读到即跳过搬 u0 task。
2. 定位"搬 u0 task"的确切执行点（r0 回调 E() / s3.U.U0 建屏完成后），只抑制搬运，不删建屏。
3. 苹果(纯镜像)与小米(子屏/接力)两条 display 都要验证 u999 独占。
