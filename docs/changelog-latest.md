# HyperFlow 1.1.0-beta25 更新日志

## 修复

### 1. 修复运行状态卡片不该流转却流转了
- **问题**：开启模块后，"短信正在运行"等运行状态卡片开始流转到其他设备，原生本来不应该流转的
- **根源**：HookCloneBypass 里有一段"普通用户全量放行"的代码，导致所有通知都跳过了小米原生的 `mAppNotificationFilter` 过滤
- **修复**：只放行分身（user 999）的通知，普通用户交给小米原生自己处理

### 2. 修复亮屏时来电不流转的问题
- **问题**：锁屏时电话可以流转，但亮屏时电话不流转
- **根源**：亮屏时来电通知走直接监听路径，`notification_from_broadcast = false`，所以不走 voip 路径
- **修复**：
  - hook `handleNotificationTrans()`：对于来电通知，手动设置 `notification_from_broadcast = true`
  - hook `filterCall()`：让亮屏时的来电通知也不过滤（放行）
  - 这样亮屏时来电就会自动走 voip 路径，显示全屏来电界面

### 3. iOS 设备强开 voip
- **问题**：iPhone X（iOS 16）等旧机型不支持跨设备通话，iPhone 13、14 都支持
- **原因**：小米服务器端做了设备白名单，旧机型没加进去
- **方案**：hook `isRemoteVoipSupported()` 方法，对于 iOS 设备，强制返回 true
- **注意**：如果 iPhone 上的"小米互联服务"App 真的不支持，可能会显示异常

## 已知问题

- 外观选择器点击闪退（还没修复）
- 妙享直通（跳过密码）功能不可用
- 日志列表/开源许可返回键会直接退出 App
- iOS 点击分身通知跳转到 999 可能不稳定
