package com.hyperflowplus.hooks;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 解锁时流转（电话跨设备接听）：亮屏来电时向 milink 补发 ON_CALL_VOIP 流转广播。
 *
 * 根因（hf_lock.log / hf_call3.log 双日志实锤）：
 *   锁屏来电：Telecom 绑定 InCallUI（InCallServiceImpl）→ InCallPresenter 状态机 INCOMING
 *             → StatusBarNotifier.resendNotification → buildAndSendNotification
 *             → ① 发布来电通知（状态栏/通知监听） ② 直接向 com.milink.service 发 ON_CALL_VOIP 广播
 *             → milink NotifTransReceiver 收到广播 → buildSbnFromIntent → NotificationHandler.handleNotificationTrans → 流转成功（Code 200）
 *   亮屏来电：Telecom 不绑定 InCallUI（InCallController isRequestedtype:false，只绑蓝牙 InCallService）
 *             → InCallUI 进程只有后台服务跑 TelecomAdapter（startForegroundNotification），
 *               状态机不启动、不发 ON_CALL_VOIP 广播 → milink 收不到 → 流转链在源头断掉。
 *
 * 修复：hook StatusBarNotifier.notifyNotification（静态方法，亮屏来电时也会被调用——
 *       亮屏日志实锤的 startForegroundNotification 正是 notifyNotification 里 enableCallStyle
 *       分支触发的），在"开关开启 + 当前非锁屏"时，按原生 sendNotificationBroadcast 的格式
 *       补发 ON_CALL_VOIP 广播（复用同一 Notification 对象）。
 *       milink 的 NotifTransReceiver.buildSbnFromIntent 只需 pkg/id/tag/uid/initialPid/
 *       userHandle/notification 七个字段（键名已逐一比对一致），收到即走完整流转链，
 *       与通知监听器是否收到无关。
 *
 * 亮点：零 UI 干预——不模拟锁屏、不启动 InCallUI 全屏、不碰 KeyguardManager/PowerManager
 *       全局判定，亮屏来电横幅仍由 SystemUI 原生显示（上半部分），本模块只是替 InCallUI
 *       补发它"忘了发"的流转广播。锁屏时原生已发，本 hook 直接跳过，不会重复流转。
 */
public class HookCallVoipNotify {

    private static final String TAG = "HyperFlowPlus";
    private static final String ACTION_ON_CALL_VOIP = "com.xiaomi.dist.notification.action.ON_CALL_VOIP";
    private static final String PKG_MILINK = "com.milink.service";
    private static final String NOTIFICATION_TAG = "incall"; // 与 StatusBarNotifier.NOTIFICATION_TAG 一致
    private static final long DEDUP_WINDOW_MS = 3000L;       // 同一来电只补发一次

    private static volatile long sLastSentAt = 0L;

    public static void install(ClassLoader cl) {
        try {
            Class<?> sbn = Class.forName("com.android.incallui.StatusBarNotifier", false, cl);
            Method m = sbn.getDeclaredMethod("notifyNotification",
                    Context.class, NotificationManager.class, Notification.class);
            m.setAccessible(true);
            com.hyperflowplus.XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            try {
                                if (!Config.isForceTransferEnabled()) {
                                    return chain.proceed();
                                }
                                Context ctx = (Context) chain.getArgs().get(0);
                                Notification n = (Notification) chain.getArgs().get(2);
                                if (ctx == null || n == null) {
                                    return chain.proceed();
                                }
                                // 锁屏时原生 resendNotification 已发 ON_CALL_VOIP，跳过避免重复流转
                                KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                                if (km != null && km.isKeyguardLocked()) {
                                    return chain.proceed();
                                }
                                // 去重：短窗口内只补发一次
                                long now = SystemClock.elapsedRealtime();
                                if (now - sLastSentAt < DEDUP_WINDOW_MS) {
                                    return chain.proceed();
                                }
                                sLastSentAt = now;
                                // 仿 StatusBarNotifier.lambda$sendNotificationBroadcast$2 构造 ON_CALL_VOIP 广播
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
                                MiflowLog.i(TAG + ": screen-on call -> ON_CALL_VOIP sent (title="
                                        + n.extras.getCharSequence("android.title") + ")");
                            } catch (Throwable t) {
                                MiflowLog.e(TAG + ": fake ON_CALL_VOIP error", t);
                            }
                            return chain.proceed();
                        }
                    });
            MiflowLog.i(TAG + ": HookCallVoipNotify installed");
        } catch (Throwable t) {
            MiflowLog.e(TAG + ": HookCallVoipNotify install failed", t);
        }
    }
}
