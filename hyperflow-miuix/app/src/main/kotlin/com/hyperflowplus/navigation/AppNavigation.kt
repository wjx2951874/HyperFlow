package com.hyperflowplus.navigation

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hyperflowplus.HFState
import com.hyperflowplus.HyperFlowApp
import com.hyperflowplus.ui.AuthSetupScreen
import com.hyperflowplus.ui.LicensesScreen
import com.hyperflowplus.ui.LogListScreen
import com.hyperflowplus.ui.ThemeSettingsScreen
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme

/**
 * 1.1.0 导航栈装配（00 规范 §3.2，参照 Mishka ui/navigation/AppNavigation.kt）：
 * - MiuixTheme（深浅色/Monet/自定义主题色/AMOLED）提升到全局，覆盖 NavDisplay 所有页面；
 * - NavDisplay + MiuixDefault 转场 + 圆角裁切压暗；二级页挂横滑返回；
 * - 系统返回/全面屏手势 → navigator.pop()，只有 Main 在栈底时返回才退出 App。
 */
@Composable
fun AppNavigation() {
    val state = HFState
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val mode = when (state.themeMode) {
        "light" -> if (state.monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        "dark" -> if (state.monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        else -> if (state.monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
    val controller = remember(state.themeMode, state.monet, state.keyColor) {
        ThemeController(mode, keyColor = Color(state.keyColor))
    }
    // 系统栏随主题实时联动（App 内手动切深浅色时状态栏/导航栏图标即时翻转）
    val activity = LocalContext.current as ComponentActivity
    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000) { dark },
            navigationBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000) { dark }
        )
    }
    // AMOLED 纯黑：深色模式下主要容器色覆盖为纯黑
    val amoledColors = if (state.amoled && dark) {
        darkColorScheme().copy(
            background = Color.Black, surface = Color.Black,
            surfaceVariant = Color(0xFF1C1C1E),
            surfaceContainer = Color.Black, surfaceContainerHigh = Color(0xFF111111),
            surfaceContainerHighest = Color(0xFF1C1C1E),
        )
    } else null

    // ★ 必须显式传路由超类型 rememberNavBackStack<Route>(...)：不写 <Route> 会把 T
    // 推断成 Route.Main，之后 push 其他子类型会在状态保存时序列化失败（miuix-nav 文档 warning）
    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember { Navigator(backStack) }
    // 横滑返回方向：RTL 翻转（miuix-nav 的 NavSwipeDirection 是物理方向，不自动镜像）
    val swipeBackDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl)
        NavSwipeDirection.RightToLeft else NavSwipeDirection.LeftToRight

    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            // 全局返回键处理：栈中多于 1 页时弹栈，否则不处理（让系统退出 App）
            BackHandler(enabled = backStack.size > 1) {
                navigator.pop()
            }
            NavDisplay(
                backStack = backStack,
                onBack = { navigator.pop() },                    // ← 系统返回/全面屏手势走这里弹栈
                transition = NavTransitions.MiuixDefault,        // ★ 滑动+视差转场（显式写出）
                effects = NavDisplayEffects(                     // ★ 转场圆角裁切 + 压暗
                    cornerClipRadius = rememberNavSystemCornerRadius(),
                ),
            ) {
                entry<Route.Main> { HyperFlowApp() }  // 根页不给滑动关闭
                entry<Route.Logs>(swipeDismiss = swipeBackDirection) {
                    LogListScreen(onBack = { navigator.pop() })
                }
                entry<Route.Licenses>(swipeDismiss = swipeBackDirection) {
                    LicensesScreen(onBack = { navigator.pop() })
                }
                entry<Route.Theme>(swipeDismiss = swipeBackDirection) {
                    ThemeSettingsScreen(HFState, onBack = { navigator.pop() })
                }
                entry<Route.AuthSetup>(swipeDismiss = swipeBackDirection) {
                    AuthSetupScreen(HFState, onBack = { navigator.pop() })
                }
            }
        }
    }
    if (amoledColors != null) {
        MiuixTheme(colors = amoledColors) { content() }
    } else {
        MiuixTheme(controller = controller) { content() }
    }
}
