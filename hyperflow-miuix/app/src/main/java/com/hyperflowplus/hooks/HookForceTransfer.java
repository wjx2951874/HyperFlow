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
                            // v1.0.1：移除 v0.6.15 短信回灌排除与 v0.6.16.1 的 30s 内容去重
                            // （二者误伤 milink 对同一通知的连续评估，导致亮屏流转失效）。
                            // 开关开启时非来电通知无条件强制放行。
                            if (isCallSbn(sbn)) {
                                // V0.6.16.1 实测结论（用户）：来电 voip 走 milink 广播链路，
                                // 不经 isDeviceSupported —— 亮屏时原生不广播 → 亮屏来电不可流转
                                // （已知限制，标注后期修复）；锁屏时原生广播 voip → 来电可流转。
                                // 本分支只影响"来电通知卡片"：强制不放行 → 锁屏流转时不再多出一张
                                // 冗余卡片（接收端 OS4 走 voip 全屏接听，卡片不需要）。
                                // 注意：OS3/苹果等不支持 voip 的接收端原本靠这张降级卡片接听，
                                // 屏蔽后此类接收端将收不到来电（与用户当前实测环境一致：接收端可 voip）。
                                android.app.Notification callN = sbn.getNotification();
                                MiflowLog.d("call notification: block extra card pkg=" + sbn.getPackageName()
                                        + " category=" + (callN != null ? callN.category : "null")
                                        + " key=" + sbn.getKey()
                                        + " (voip broadcast handles relay; screen-on call relay = known limitation)");
                                return Boolean.FALSE;
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
            MiflowLog.i("HookForceTransfer[gate] installed (v1.0.1: call=block card, others=gated force, no dedup)");
        } catch (Throwable t) {
            MiflowLog.e("HookForceTransfer[gate] install failed", t);
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
