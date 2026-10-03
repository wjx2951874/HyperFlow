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
 * 功能① 亮屏强制流转（V0.2 libxposed 重写；v0.5.15.4 改回原版分流 + 模拟锁屏）。
 *
 * 反编译确认（NotificationHandler）：
 *   private boolean isDeviceSupported(StatusBarNotification sbn, DeviceSubInfo info) {
 *       ...（deviceSwitch / 订阅 / 灵动岛过滤）
 *       if (keyguardManager.isKeyguardLocked() || !powerManager.isInteractive()) return true;
 *       Log.e(TAG, "device is not locked and screen is on");
 *       return false;   // ← 亮屏拒绝流转
 *   }
 *
 * 方案（用户确认）：不改 milink 的来电分流决策（OS4 走 voip 全屏 / OS3 降级通知），
 * 只"模拟锁屏"让 milink 自己按原生锁屏逻辑跑：
 *   1) isDeviceSupported 直接返回 true —— 等效"模拟锁屏"（亮屏也放行，含来电通知）；
 *   2) KeyguardManager#isKeyguardLocked：milink 调用栈返回 true；
 *   3) PowerManager#isInteractive：milink 调用栈返回 false（模拟灭屏，
 *      覆盖来电广播链路里对屏幕状态的另一处门控）。
 * 来电通知不再短路（原短路是为防"广播 voip + 通知卡片"双显示，改回原版后
 * 由 milink 自身同 key 去重处理，不需要我们插手）。
 */
public class HookForceTransfer {

    public static void install(ClassLoader cl) {
        // v0.6.9：完全恢复系统原逻辑（用户确认）—— 不装任何 hook，流转门控 100% 交回系统。
        // 系统原生行为：亮屏拒绝流转（isDeviceSupported=false）、锁屏放行（true）；
        // 来电分流系统原生处理（锁屏 OS4 走 voip 全屏、OS3 降级通知；亮屏不流转）。
        // 此前"模拟锁屏/强制放行"导致系统原本不会流转的通知也被流转，全部去除。
        MiflowLog.i("HookForceTransfer: reverted to pure stock logic (no hook installed)");
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
