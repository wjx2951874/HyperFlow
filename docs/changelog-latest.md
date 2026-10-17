# HyperFlow 1.1.0-beta26 更新日志

## 修复

### 1. 修复外观选择器点击闪退
- **问题**：设置页外观选择器（跟随系统/浅色/深色）点击后闪退
- **根源**：`OverlayDropdownPreference` 需要在 `Scaffold` 里提供 `popupHost`，我们的 Scaffold 没有这个参数
- **修复**：在 Scaffold 里加上 `popupHost = { }` 参数（学原版 KernelSU 的写法）

### 2. 修复二级菜单返回键直接退出 App
- **问题**：日志列表、开源许可页面按系统返回键会直接退出 App，应该返回上一级
- **修复**：在 NavDisplay 外面加了全局 BackHandler，栈中多于 1 页时弹栈

### 3. KSU 文案改成支持多 Root 方案
- **改动**：所有 "KernelSU" 文案改成 "KernelSU / Magisk / APatch"
- **引导步骤**：打开 Root 管理器（KernelSU / Magisk / APatch）

## 回退

### 1. 禁用了 HookCallVoip
- **原因**：hook 了 `handleNotificationTrans()`、`filterCall()`、`isLocalVoipSupported()` 等方法后，电话流转完全失效（卡片和 voip 都没有）
- **回退**：暂时禁用所有 hook，恢复到之前的状态（锁屏可以流转，亮屏不行）

## 已知问题

- 外观选择器可能还会闪退（需要你测试）
- 妙享直通（跳过密码）功能不可用
- 电话流转：锁屏可以，亮屏不行
- iOS 点击分身通知跳转到 999 可能不稳定
