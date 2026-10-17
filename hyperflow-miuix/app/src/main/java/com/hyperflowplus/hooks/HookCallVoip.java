package com.hyperflowplus.hooks;

import com.hyperflowplus.MiflowLog;

/**
 * v1.1.0-beta25：修复亮屏来电流转 + iOS 强开 voip
 *
 * 暂时禁用所有 hook，先恢复电话流转。
 */
public class HookCallVoip {

    public static void install(ClassLoader cl) {
        // 暂时禁用所有 hook，先恢复电话流转
        MiflowLog.i("HookCallVoip: all hooks disabled for debugging");
    }
}
