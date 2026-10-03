package com.hyperflowplus.hooks;

import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 亮屏强制流转（V0.2 libxposed 重写；v0.6.14 来电精准强制流转）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isDeviceSupported(StatusBarNotification sbn, DeviceSubInfo info) {
 *       ...（deviceSwitch / 订阅 / 灵动岛过滤）
 *       if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *       Log.e(TAG, "device is not locked and screen is on");
 *       return false;   // ← 亮屏拒绝流转
 *   }
 *
 * v0.6.14 门控（用户确认）：
 *   - 来电通知（CATEGORY_CALL / incallui / dialer / phone）→ 强制 TRUE（精准流转）：
 *     isDeviceSupported 首个参数就是 sbn，可精确区分来电，只对来电放行（等效仅来电的
 *     亮屏判定变可流转），亮屏来电也能流转；接收端能力判定（OS4→voip 全屏 / OS3·苹果
 *     →降级卡片）与同 key 去重交给 milink 原生——不碰 system_server、不碰其他通知；
 *   - 其他通知：isDeviceSupported 强制 true（功能①亮屏强制流转，v0.6.8 行为保留）；
 *   - 运行状态卡拦截保留（V0.3.16 起）。
 *   - 历史教训：v0.6.10 的 HookCallSimLock（system_server 全局模拟锁屏）误伤正常通话
 *     流程（isKeyguardLocked/isInteractive 被正常来电流程大量调用），连锁屏 voip 都断；
 *     已删除。全过滤来电卡片（v0.6.10）同样错误——卡片是 OS3/苹果接收端的降级通道。
 */
public class HookForceTransfer {

    public static void install(ClassLoader cl) {
        // v0.6.10：只改电话，其他通知维持 v0.6.8 的强制流转（用户明确"其他先不要改"）。
        //
        // 上一版（v0.5.15.4 起"模拟锁屏"）isDeviceSupported 一律 true + Keyguard/PowerManager
        // 模拟锁屏，来电通知也被强制放行（亮屏出现系统本不该流转的来电卡片、锁屏双份、
        // 接听后"电话"通知）——用户实测确认不是 milink 原始逻辑。
        //
        // 本版门控逻辑：
        //   - 来电通知（isCallSbn：CATEGORY_CALL / incallui / dialer / phone）→ 直接 proceed，
        //     完全交给 milink 原生判定（亮屏拒绝 / 锁屏按 OS4 走 voip 全屏、OS3 降级通知卡），
        //     模块零干预；
        //   - 不装 KeyguardManager/PowerManager 模拟锁屏 —— 否则来电 proceed 后 milink 内部
        //     锁屏判断仍被模拟成"锁屏"，来电就不是原始逻辑了（广播链路来电 voip 同样受影响）；
        //   - 其他通知：维持 v0.6.8 行为（isDeviceSupported 强制 true = 亮屏强制流转功能①）；
        //   - 运行状态卡拦截保留（V0.3.16 起，运行时状态同步，非真实通知）。
        try {
            Class<?> handler = Class.forName("com.xiaomi.dist.notification.listener.handle.NotificationHandler", false, cl);
            Class<?> devInfo = Class.forName("com.xiaomi.dist.notification.common.data.DeviceSubInfo", false, cl);
            Method m = handler.getDeclaredMethod("isDeviceSupported", StatusBarNotification.class, devInfo);
            m.setAccessible(true);
            XposedEntry.get().hook(m)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            StatusBarNotification sbn = (StatusBarNotification) chain.getArg(0);
                            android.app.Notification gateN = sbn.getNotification();
                            MiflowLog.v("isDeviceSupported: pkg=" + sbn.getPackageName()
                                    + " category=" + (gateN != null ? gateN.category : "null")
                                    + " key=" + sbn.getKey());
                            // v0.6.14：开关接入 hook！关闭时完全走系统原判定（milink 对带流转
                            // 来源标记的消息在接收端会自行拒绝二次流转 → 不再回灌双份）
                            if (!Config.isForceTransferEnabled()) {
                                MiflowLog.d("force transfer DISABLED: stock decision (no gate)");
                                return chain.proceed();
                            }
                            // V0.6.15：回灌排除——功能①开启时，"流转来源"的短信通知不强制
                            // （走系统原判定，系统对流转来源消息拒绝二次流转 → 不再双份）。
                            // 接收侧 HookSmsPersist 已记录最近流转短信指纹，命中则 proceed。
                            // 本机真实收到的短信通知不在指纹里 → 功能①照常强制流转。
                            if (isSmsSbn(sbn)) {
                                String sender = smsTitle(sbn);
                                String body = smsBody(sbn);
                                if (HookSmsPersist.matchRecentSms(sender, body)) {
                                    MiflowLog.d("flow-source sms notification -> skip force (prevent echo): pkg="
                                            + sbn.getPackageName() + " sender=" + sender + " key=" + sbn.getKey());
                                    return chain.proceed();
                                }
                            }
                            // V0.6.16.1：解锁二次流转去重 —— 30s 内同内容指纹只强制放行一次。
                            // 锁屏首流转后，解锁瞬间 milink 对同一通知重新评估（屏幕状态刷新触发
                            // 通知重发/重评估），原生因"亮屏"本应拒绝，但下方无条件 TRUE 覆盖导致
                            // 二次流转。命中指纹 → proceed 交还原生（亮屏原生拒绝 → 不再流转）；
                            // 新通知（新指纹）→ 照常强制流转。键用内容指纹而非通知 key：
                            // 解锁重发的通知 key 可能变化，内容不变。
                            String fp = fingerprintFor(sbn);
                            Long lastDedup = DEDUP.get(fp);
                            long nowMs = System.currentTimeMillis();
                            if (lastDedup != null && nowMs - lastDedup < DEDUP_WINDOW_MS) {
                                MiflowLog.d("dedup: recently force-transferred -> stock decision: pkg="
                                        + sbn.getPackageName() + " fp=" + fp + " key=" + sbn.getKey());
                                return chain.proceed();
                            }
                            if (DEDUP.size() > 64) {
                                DEDUP.entrySet().removeIf(e -> nowMs - e.getValue() > CLEANUP_MS);
                            }
                            DEDUP.put(fp, nowMs);
                            if (isCallSbn(sbn)) {
                                // v0.6.14 精准强制流转：isDeviceSupported 首个参数就是 sbn，
                                // 可精确区分来电——只对来电返回 TRUE（等效仅来电的亮屏判定放行），
                                // 亮屏来电也能流转；接收端能力判定（OS4→voip 全屏 / OS3·苹果→降级
                                // 卡片）与同 key 去重交给 milink 原生。不再全局模拟锁屏
                                //（HookCallSimLock 误伤 system_server 正常通话流程，已移除）。
                                Config.bump(Config.CNT_CALL_RELAY);
                                android.app.Notification callN = sbn.getNotification();
                                MiflowLog.d("call notification: MATCH pkg=" + sbn.getPackageName()
                                        + " category=" + (callN != null ? callN.category : "null")
                                        + " key=" + sbn.getKey()
                                        + " -> force gate (precise, stock degrade on receiver)");
                                return Boolean.TRUE;
                            }
                            if (isRunnableStateCard(sbn)) {
                                MiflowLog.d("skip runnable-state card (not a real notification)");
                                return Boolean.FALSE;
                            }
                            // 其他通知：功能①亮屏强制流转（开关已接入，仅开启时生效）
                            Config.bump(Config.CNT_FORCE);
                            MiflowLog.d("force transfer: gate overridden (non-call notification)");
                            return Boolean.TRUE;
                        }
                    });
            MiflowLog.i("HookForceTransfer[gate] installed (v0.6.15: call=precise force, others=forced, sms echo excluded)");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[gate] install failed", t);
        }
    }

    // ===== V0.6.16.1 解锁二次流转去重 =====
    private static final long DEDUP_WINDOW_MS = 30 * 1000L;
    private static final long CLEANUP_MS = 60 * 1000L;
    private static final java.util.Map<String, Long> DEDUP = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 内容指纹：来电=call|pkg|标题(号码/联系人)；短信=sms|pkg|发送人|正文；
     * 其他通知=other|pkg|标题|正文。同一内容在去重窗口内只强制流转一次。
     */
    private static String fingerprintFor(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            android.app.Notification n = sbn.getNotification();
            CharSequence t = n != null ? n.extras.getCharSequence("android.title") : null;
            CharSequence tx = n != null ? n.extras.getCharSequence("android.text") : null;
            String title = t == null ? "" : t.toString();
            String text = tx == null ? "" : tx.toString();
            if (isCallSbn(sbn)) {
                return "call|" + pkg + "|" + title;
            }
            if (isSmsSbn(sbn)) {
                return "sms|" + pkg + "|" + smsTitle(sbn) + "|" + smsBody(sbn);
            }
            return "other|" + pkg + "|" + title + "|" + text;
        } catch (Throwable t) {
            return "";
        }
    }

    // ===== V0.6.15 短信回灌排除 =====
    private static final java.util.Set<String> SMS_PKGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.android.mms", "com.miui.mms", "com.android.phone",
            "com.android.messaging", "com.google.android.apps.messaging"));

    private static boolean isSmsSbn(StatusBarNotification sbn) {
        return SMS_PKGS.contains(sbn.getPackageName());
    }

    private static String smsTitle(StatusBarNotification sbn) {
        try {
            android.app.Notification n = sbn.getNotification();
            if (n == null) return "";
            CharSequence t = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
            return t == null ? "" : t.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String smsBody(StatusBarNotification sbn) {
        try {
            android.app.Notification n = sbn.getNotification();
            if (n == null) return "";
            CharSequence t = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
            return t == null ? "" : t.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * V0.3.16：拦截"应用运行状态"流转卡（如"短信正在运行/如需回复请前往/停止应用"），
     * 这类是 milink 的运行时状态同步，不是真实通知，不应跨设备流转。
     */
    private static boolean isRunnableStateCard(StatusBarNotification sbn) {
        try {
            android.app.Notification n = sbn.getNotification();
            if (n == null) {
                return false;
            }
            StringBuilder sb = new StringBuilder();
            if (n.tickerText != null) {
                sb.append(n.tickerText);
            }
            if (n.extras != null) {
                CharSequence t = n.extras.getCharSequence("android.title");
                CharSequence tx = n.extras.getCharSequence("android.text");
                if (t != null) {
                    sb.append(' ').append(t);
                }
                if (tx != null) {
                    sb.append(' ').append(tx);
                }
            }
            String s = sb.toString();
            return s.contains("正在运行") || s.contains("如需回复请前往")
                    || s.contains("停止应用") || s.contains("了解详情")
                    || s.contains("点按即可");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 是否为来电相关通知：类别 call 或包名 incallui/dialer/phone（v0.6.9 移植 v0.5.15.3） */
    private static boolean isCallSbn(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg != null && (pkg.contains("incallui") || pkg.contains("dialer")
                    || "com.android.phone".equals(pkg))) {
                return true;
            }
            android.app.Notification n = sbn.getNotification();
            return n != null && n.category != null
                    && n.category.equals(android.app.Notification.CATEGORY_CALL);
        } catch (Throwable t) {
            return false;
        }
    }
}
