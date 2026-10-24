package com.hyperflowplus.hooks;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;
import com.hyperflowplus.XposedEntry;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedInterface.Hooker;

/**
 * 功能① 亮屏来电流转 · 过滤放行（milink 进程）。
 *
 * 根因（hf_light2.log 实锤 + 反编译交叉验证）：
 *   incallui 来电通知通过 NotificationListener 即时到达 milink（onNotificationPosted），
 *   但被 AppNotificationFilter.filterCall() 静默过滤：
 *     if (!"call".equals(category)) return true;
 *     if (callType == 2) return true;                          // 去电
 *     boolean fromBroadcast = extras.getBoolean(NOTIFICATION_FROM_BROADCAST, false);
 *     return !fromBroadcast && mVoipTransHelper.isLocalVoipSupported();
 *   —— 亮屏通知走监听路径：fromBroadcast=false；本机 com.android.phone 声明 supportiOS=true
 *      → isLocalVoipSupported()=true → 返回 true（过滤）→ "notification is invalid, skip transfer"
 *      → 不流转。
 *   锁屏能流转是因为锁屏走广播路径（ON_CALL_VOIP 广播通知自带 NOTIFICATION_FROM_BROADCAST=true → 放行）。
 *
 * 修复：hook filterCall，对**来电（callType==1，INCOMING）且真实处于亮屏状态**强制放行
 * （返回 false，不过滤），通知监听路径即走完整流转链，不再依赖 incallui 补发广播
 * （补发方案 beta33.9 未触发，见交接 §2）。
 * 去电（callType==2）与 category 非 call 保持原过滤，避免误流转。
 *
 * 锁屏双流转防护（beta37）：锁屏来电时 incallui 发 ON_CALL_VOIP 广播（广播路径，
 * 通知自带 NOTIFICATION_FROM_BROADCAST=true，filterCall 原生放行）；同时监听路径的
 * 原始通知也会到 filterCall（fromBroadcast=false，原生被过滤）。若一律放行监听路径，
 * 小米15 会收到广播+监听**多份** voip 消息竞态冲突 → 间歇性"当前连接已断开，请回到
 * 原设备接听电话"（用户 beta35/36 实测，锁屏也出现）。因此**真实锁屏时不放行**
 * （保持原生过滤、广播路径独享）；真实亮屏才放行。
 * 重复流转去重（beta37，LSPosed 日志实锤）：同一来电通知会被 onNotificationPosted
 * 触发多轮（通知更新多次），每轮都放行 → 每台接收设备收到同一来电多份 voip 消息 → 冲突。
 * 同一 sbn key 短时间窗（3s）内只放行一次。
 * 注意：HookCallRelay 已把进程内 KeyguardManager/PowerManager/Display 全部 hook 成
 * "锁屏态"，本 hook 必须**跨进程 Binder 到 system_server** 取真实锁屏状态
 * （IWindowManager.isKeyguardLocked），不能读进程内 API（会拿到模拟值）。
 *
 * 门控：功能① force_transfer（与 HookCallRelay 同开关）。
 */
public class HookCallFilterPass {

    private static final String CALL_TYPE = "android.callType";
    private static final int CALL_INCOMING = 1;

    /**
     * 重复流转去重（beta37，LSPosed 日志实锤）：
     * 同一来电通知会被 onNotificationPosted 触发多轮（通知更新多次），每轮都走完整流转链
     * → 每台接收设备收到同一来电的**多份 voip 消息** → 小米15 多份会话冲突 → 间歇性
     * "当前连接已断开，请回到原设备接听电话"/被挂断（有时去重成功就正常，失败就断）。
     * 用 sbn.getKey()（pkg:id:tag:user）识别同一通知，短时间窗（3s）内只放行一次。
     */
    private static final long DUP_WINDOW_MS = 3000L;
    private static final java.util.Map<String, Long> releasedKeys =
            new java.util.concurrent.ConcurrentHashMap<>(16);

    private static boolean isDuplicateRelease(String key) {
        long now = android.os.SystemClock.elapsedRealtime();
        Long last = releasedKeys.get(key);
        if (last != null && (now - last.longValue()) < DUP_WINDOW_MS) {
            return true;
        }
        releasedKeys.put(key, Long.valueOf(now));
        return false;
    }

    /**
     * 跨进程取真实锁屏状态（Binder 到 system_server）。
     * 不能用进程内 KeyguardManager/PowerManager（被 HookCallRelay 模拟为锁屏态）。
     * 查不到时保守返回 true（按锁屏处理、不放行），宁可漏放行也不引入双流转。
     */
    private static boolean isRealKeyguardLocked() {
        try {
            Object wms = Class.forName("android.view.WindowManagerGlobal")
                    .getMethod("getWindowManagerService").invoke(null);
            return ((Boolean) wms.getClass().getMethod("isKeyguardLocked").invoke(wms)).booleanValue();
        } catch (Throwable t) {
            MiflowLog.e("HookCallFilterPass: real keyguard check failed, treat as locked", t);
            return true;
        }
    }

    public static void install(ClassLoader cl) {
        if (!Config.isForceTransferEnabled()) {
            MiflowLog.i("HookCallFilterPass: force_transfer off, skip");
            return;
        }
        try {
            Class<?> cls = cl.loadClass("com.xiaomi.dist.notification.listener.handle.AppNotificationFilter");
            Method filterCall = cls.getDeclaredMethod("filterCall", StatusBarNotification.class);
            filterCall.setAccessible(true);
            XposedEntry.get().hook(filterCall)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(new Hooker() {
                        @Override
                        public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                // 只有"被过滤"才考虑放行；已放行的不动
                                if (!(result instanceof Boolean) || ((Boolean) result).booleanValue()) {
                                    Object arg0 = chain.getArgs().get(0);
                                    if (arg0 instanceof StatusBarNotification) {
                                        Notification n = ((StatusBarNotification) arg0).getNotification();
                                        Bundle ex = n != null ? n.extras : null;
                                        int callType = ex != null ? ex.getInt(CALL_TYPE, -1) : -1;
                                        if (callType == CALL_INCOMING) {
                                            if (isRealKeyguardLocked()) {
                                                // 真实锁屏：保持原生过滤（广播路径独享），避免双流转
                                                MiflowLog.i("HookCallFilterPass: callType=" + callType
                                                        + " but real keyguard locked, keep native filter (no double transfer)");
                                            } else {
                                                String key = ((StatusBarNotification) arg0).getKey();
                                                if (key != null && isDuplicateRelease(key)) {
                                                    // 同一通知短时间窗内的重复触发：保持过滤，避免重复流转
                                                    MiflowLog.i("HookCallFilterPass: callType=" + callType
                                                            + " dup notification key=" + key
                                                            + " within window, keep filtered");
                                                } else {
                                                    MiflowLog.i("HookCallFilterPass: callType=" + callType
                                                            + " (incoming) screen-on, filter released, allow transfer");
                                                    return Boolean.FALSE;
                                                }
                                            }
                                        } else {
                                            MiflowLog.i("HookCallFilterPass: filtered, callType=" + callType
                                                    + " kept filtered");
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                MiflowLog.e("HookCallFilterPass: intercept failed, pass through", t);
                            }
                            return result;
                        }
                    });
            MiflowLog.i("HookCallFilterPass: installed on AppNotificationFilter.filterCall");
        } catch (ClassNotFoundException e) {
            MiflowLog.w("HookCallFilterPass: AppNotificationFilter not found (version mismatch?), skip");
        } catch (Throwable t) {
            MiflowLog.e("HookCallFilterPass: install failed", t);
        }
    }
}
