# MiTrustService 授权密码校验链路定位（妙享直通自动填依据）

> 来源：用户抓取的 `com.xiaomi.trustservice`（MiTrustService.apk），jadx 反编译。
> 目标：跨设备连接/重新授权时弹出的 6 圆点密码盘，自动填入用户留存的锁屏密码。

## 1. 宿主（focus.log 实锤）
- 前台 Activity：`com.xiaomi.trustservice / .remoteservice.locksettings.LockScreenUIActivity`（t2923）。
- 不在 com.milink.service，也不是 SystemUI；milink 通过 binder 触发 trustservice 拉起此界面。

## 2. UI 三套（对应三种锁屏类型）
`com.xiaomi.trustservice.lockscreenui.AuthInputView` 按类型切换：
| 类型 | 视图 | 输入完成传入 |
|---|---|---|
| PIN（4/6位数字） | `numeric/NumericKeypadView` + `CircleView`(6圆点) | 数字明文字符串 |
| 混合密码（字母数字符号） | `keypad/AlphanumericKeypadView` | 密码明文字符串 |
| 图案 | `gesture/MiuiLockPatternView`(3×3) | 点序列编码（字节） |

## 3. 调用链（自下而上）
1. 视图完成 → `AuthInputView.setAuthInputCompleteListener` → **`A.d.e(String 输入串)`**（最佳 hook 注入点）。
2. `A.d.e` 按类型构造凭证 `Z0.b.c(type, str)`：
   - type 3 PIN → `LockscreenCredential.createPin(str)`
   - type 4 Password → `createPassword(str)`
   - type 1 Pattern → `createPattern(byteArrayToPattern(str.getBytes()))`
3. 起线程 `Z0.a(b, credential, callback, challenge, client)`。
4. 校验 `Z0.b`（LockSettingUtils）：
   - 普通鉴权分支：`LockPatternUtils.checkCredential` / `verifyCredential(...).isMatched()` → OK/fail/RETRY罚时。
   - 绑定/重新授权分支：`Z0.b.d(...)` → `verifyCredential` 成功后取 `getGatekeeperPasswordHandle` + `verifyGatekeeperPasswordHandle` 得 **HAT（硬件认证令牌）**，再发给对端（Bind_A/B、Risk_B）。

## 4. 为什么选"自动填"而不是 hook 直通判成功
- 绑定分支成功后必须把 gatekeeper **HAT** 回传对端完成配对；HAT 由 TEE 签发，hook 伪造返回成功会得到空 HAT，导致配对/重新授权失败。
- `verifyCredential` 是系统 gatekeeper 真实比对，无法在应用层绕过。
- 因此：**hook `A.d.e` 用留存密码走真实校验**，通过即拿到真 HAT，最稳、无伪造。

## 5. Hook 方案（任务79）
- 注入点（已复核 jadx）：`A.d.e(String)`（`package A; public final class d implements Q0.b`，方法 `public void e(String)`）。入参即用户输入串，进入时若已开自动直通且已存密码，用留存值替换入参再放行原流程。
- 类型由实例字段 `this.a`（构造时传入的原始类型码）经 `p.AbstractC0255e.b(a)` 归一：
  - 0 = NUMERIC（PIN）→ `r6.c(3, str)` = createPin
  - 1 = GESTURE（图案）→ `r6.c(1, str)` = createPattern（str 为视图产出的编码串）
  - 2 = ALPHANUMERIC（混合）→ `r6.c(4, str)` = createPassword
- PIN：直接替换数字串。混合：直接替换密码串。图案：留存 3×3 点位序列，hook 时还原成 `e` 期望的编码串（编码待核 MiuiLockPatternView）。
- 配置沿用 `auto_unlock` / `auto_unlock_pwd`；图案单独存点序列字段。
- 注意罚时（错密码后 getLockoutAttemptDeadline），自动填只在密码正确时触发，避免连续错误触发锁定；只试一次，失败不重试。
