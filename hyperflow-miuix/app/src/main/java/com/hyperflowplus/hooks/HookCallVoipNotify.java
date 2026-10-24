package com.hyperflowplus.hooks;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 解锁时流转（电话跨设备接听）：来电时确保 ON_CALL_VOIP 流转广播一定发出。
 *
 * 根因（hf_lock.log / hf_call3.log 双日志实锤）：
 *   息屏锁屏来电：Telecom 绑定 InCallUI（InCallServiceImpl）→ InCallPresenter 状态机 INCOMING
 *             → StatusBarNotifier.resendNotification → buildAndSendNotification
 *             → ① 发布来电通知（notifyNotification） ② 调 sendNotificationBroadcast(Call)
 *               向 com.milink.service 发 ON_CALL_VOIP 广播 → milink 流转成功（Code 200）
 *   亮屏来电（解锁 或 锁屏界面）：Telecom 不绑定 InCallUI（InCallController isRequestedtype:false，
 *             只绑蓝牙 InCallService）→ InCallUI 状态机不启动，只有后台服务跑
 *             TelecomAdapter.startForegroundNotification（来自 notifyNotification 的
 *             enableCallStyle 分支）→ 原生 sendNotificationBroadcast 整个不被调用
 *             → 广播缺失 → milink 收不到 → 流转链在源头断掉。
 *
 * 修复（beta33.9 重写）：
 *   hook 三个点，让「广播一定发出、且不重复、不回流」：
 *   ① sendNotificationBroadcast(Call)（原生广播出口，息屏锁屏时原生必调）→ 只打"原生已发"时间戳。
 *   ② RelayUtils.getPhoneRelayCall(Call)（relay 防回流判定，原生在 sendNotificationBroadcast 内调用）
 *      → 记录最近判定结果与时间。
 *   ③ notifyNotification(Context, NotificationManager, Notification)（静态，所有来电场景都会被调）
 *      → force_transfer 开启时延迟 500ms 补发 ON_CALL_VOIP（按原生 lambda$sendNotificationBroadcast$2
 *        的键名格式，复用同一 Notification 对象）。
 *      延迟的目的：给原生链一个先跑的机会——息屏锁屏时原生会在几百 ms 内调 sendNotificationBroadcast，
 *      延迟后检测到"原生已发"就跳过，避免重复流转；亮屏/锁屏界面原生从不调，延迟后补发。
 *
 * 三重防重复/防回流：
 *   - 原生 3s 内已调 sendNotificationBroadcast → 跳过（息屏锁屏场景，原生负责发）
 *   - 最近判定为 relay 来电（3s 内）→ 跳过（对端流转来的电话不再转回，跟随原生防回流）
 *   - 自身 3s 去重窗口 → 同一来电只补发一次
 *
 * 亮点：零 UI 干预——不模拟锁屏、不启动 InCallUI 全屏、不碰 KeyguardManager/PowerManager
 *       全局判定，亮屏来电横幅仍由 SystemUI 原生显示（上半部分），本模块只是替 InCallUI
 *       补发它"忘了发"的流转广播。
 */
public class HookCallVoipNotify {

    private static final String TAG = "HyperFlow";
    private static final String ACTION_ON_CALL_VOIP = "com.xiaomi.dist.notification.action.ON_CALL_VOIP";
    private static final String PKG_MILINK = "com.milink.service";
    private static final String NOTIFICATION_TAG = "incall"; // 与 StatusBarNotifier.NOTIFICATION_TAG 一致
    private static final long DEDUP_WINDOW_MS = 3000L;       // 同一来电只补发一次
    private static final long NATIVE_CHECK_DELAY_MS = 500L;  // 延迟补发：等原生链先跑

    private static volatile long sLastNativeBroadcastAt = 0L; // 原生 sendNotificationBroadcast 最近调用时间（息屏锁屏原生链）
    private static volatile boolean sLastRelayCall = false;   // 最近 RelayUtils.getPhoneRelayCall 判定结果
    private static volatile long sLastRelayAt = 0L;           // 最近 relay 判定时间
    private static volatile long sLastSentAt = 0L;            // 本 hook 最近补发时间

    public static void install(ClassLoader cl) {
        try {
            Class<?> sbn = Class.forName("com.android.incallui.StatusBarNotifier", false, cl);
            Class<?> callCls = Class.forName("com.android.incallui.Call", false, cl);

            // ---- ① 原生广播出口打标：息屏锁屏时原生会调 sendNotificationBroadcast(Call) ----
            try {
                Method mNative = sbn.getDeclaredMethod("sendNotificationBroadcast", callCls);
                mNative.setAccessible(true);
                com.hyperflowplus.XposedEntry.get().hook(mNative)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                sLastNativeBroadcastAt = SystemClock.elapsedRealtime();
                                return chain.proceed();
                            }
                        });
            } catch (Throwable t) {
                MiflowLog.e(TAG + ": hook sendNotificationBroadcast failed", t);
            }

            // ---- ② relay 防回流判定打标：原生在 sendNotificationBroadcast 内调它决定是否跳过 ----
            try {
                Class<?> relayCls = Class.forName("com.android.incallui.relay.RelayUtils", false, cl);
                Method mRelay = relayCls.getDeclaredMethod("getPhoneRelayCall", callCls);
                mRelay.setAccessible(true);
                com.hyperflowplus.XposedEntry.get().hook(mRelay)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(new Hooker() {
                            @Override
                            public Object intercept(Chain chain) throws Throwable {
                                Object r = chain.proceed();
                                sLastRelayCall = Boolean.TRUE.equals(r);
                                sLastRelayAt = SystemClock.elapsedRealtime();
                                return r;
                            }
                        });
            } catch (Throwable t) {
                MiflowLog.e(TAG + ": hook getPhoneRelayCall failed", t);
            }

            // ---- ③ 补发触发点：notifyNotification（所有来电场景都被调用）----
            Method m = sbn.getDeclaredMethod("notifyNotification",
                    Context.class, NotificationManager.class, Notification.class);
            m.setAccessible(true);
            com.hyperflowplus.XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            if (!Config.isForceTransferEnabled()) {
                                return chain.proceed();
                            }
                            final Context ctx = (Context) chain.getArgs().get(0);
                            final Notification n = (Notification) chain.getArgs().get(2);
                            if (ctx == null || n == null) {
                                return chain.proceed();
                            }
                            // 延迟补发：给原生链一个先跑的机会。
                            // 息屏锁屏：原生随后调 sendNotificationBroadcast → 延迟后检测到 → 跳过；
                            // 亮屏/锁屏界面：原生从不调 → 延迟后补发。
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        long now = SystemClock.elapsedRealtime();
                                        if (now - sLastNativeBroadcastAt < DEDUP_WINDOW_MS) {
                                            MiflowLog.d(TAG + ": native ON_CALL_VOIP already sent, skip");
                                            return;
                                        }
                                        if (sLastRelayCall && now - sLastRelayAt < DEDUP_WINDOW_MS) {
                                            MiflowLog.d(TAG + ": relay call, skip (follow native anti-loop)");
                                            return;
                                        }
                                        if (now - sLastSentAt < DEDUP_WINDOW_MS) {
                                            MiflowLog.d(TAG + ": already relayed recently, skip");
                                            return;
                                        }
                                        sLastSentAt = now;
                                        // 仿原生 lambda$sendNotificationBroadcast$2 构造 ON_CALL_VOIP 广播
                                        Intent intent = new Intent(ACTION_ON_CALL_VOIP);
                                        intent.setPackage(PKG_MILINK);
                                        intent.putExtra("pkg", "com.android.incallui");
                                        intent.putExtra("id", 1);
                                        intent.putExtra("tag", NOTIFICATION_TAG);
                                        intent.putExtra("uid", Process.myUid());
                                        intent.putExtra("initialPid", Process.myPid());
                                        intent.putExtra("userHandle", Process.myUserHandle());
                                        intent.putExtra("notification", n);
                                        ctx.sendBroadcast(intent);
                                        MiflowLog.i(TAG + ": ON_CALL_VOIP relayed (title="
                                                + n.extras.getCharSequence("android.title") + ")");
                                    } catch (Throwable t) {
                                        MiflowLog.e(TAG + ": fake ON_CALL_VOIP error", t);
                                    }
                                }
                            }, NATIVE_CHECK_DELAY_MS);
                            return chain.proceed();
                        }
                    });
            MiflowLog.i(TAG + ": HookCallVoipNotify installed");
        } catch (Throwable t) {
            MiflowLog.e(TAG + ": HookCallVoipNotify install failed", t);
        }
    }
}
