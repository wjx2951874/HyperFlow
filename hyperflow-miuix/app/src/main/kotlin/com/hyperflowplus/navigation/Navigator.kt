package com.hyperflowplus.navigation

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 1.1.0 导航栈控制（00 规范 §3.1，参照 Mishka ui/navigation/Navigator.kt）：
 * push 防重复入栈；pop 守卫栈底（size>1），栈底交给系统退出 App。
 */
class Navigator(val backStack: SnapshotStateList<NavKey>) {
    fun push(key: NavKey) {
        if (key !in backStack) backStack.add(key)
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) backStack[backStack.lastIndex] = key else backStack.add(key)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) backStack.removeAt(backStack.lastIndex)
    }

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("LocalNavigator not provided") }
