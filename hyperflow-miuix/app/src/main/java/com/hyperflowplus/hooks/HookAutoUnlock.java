package com.hyperflowplus.hooks;

import com.hyperflowplus.Config;
import com.hyperflowplus.MiflowLog;

/**
 * 功能⑤ 重启后被妙享桌面拦截时自动输入锁屏密码（P1，开发中）。
 *
 * V0.2.5：WebUI 已提供「自动输密码」开关与密码存储（明文，使用者自行填写）；
 * 本 hook 为占位实现——开关状态与密码已持久化到 Config，功能实现待 P0 验证
 * （弹窗 UI 组件定位、密码输入控件层级、鉴权链路）。
 *
 * 候选实现路线（P0 验证后再选）：
 *  1. AccessibilityService 监听「输入锁屏密码」弹窗，自动填 Password 字段并点击确认
 *  2. LSPosed hook SystemUI 的解锁对话框，直接注入密码
 *     —— 需要先定位 OS4 弹窗类（如 MiuiKeyguardPinView / 妙享桌面提示框）
 *
 * 本文件为占位，不影响 ①②③ 功能。
 */
public class HookAutoUnlock {

    public static void install(ClassLoader cl) {
        if (!Config.isAutoUnlockEnabled()) {
            MiflowLog.i("HookAutoUnlock: disabled by switch, skip");
            return;
        }
        // P1：TODO（见 README 开发计划 P1-5）
        boolean hasPwd = Config.getString(Config.KEY_AUTO_UNLOCK_PASSWORD, "").length() > 0;
        MiflowLog.i("HookAutoUnlock: P1 placeholder, enabled, pwd=" + (hasPwd ? "set" : "not set"));
    }
}
