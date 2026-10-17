# 系统「设置锁屏密码」页提取参考（复刻依据，不自绘）

> 来源：用户提供的 `Settings.apk`（com.android.settings，飞书云盘 B2elbN9tzo8qXFx3l1Ic1WkcnsV）。
> 用途：妙享直通「锁屏密码预设置」录入页照此结构/字段复刻；**只用官方组件，不自绘键盘/图案盘**。
> 说明：本 App 是 Compose(miuix-kmp)，运行时无法加载 Settings 的私有 View（StateEditText/LockPatternView），
> 复刻 = 照系统页的结构、输入类型、校验规则、两遍确认交互，用 miuix 官方等价组件实现，不搬私有类。

## 1. PIN / 混合密码页（choose_lock_password_new.xml）
结构（垂直 LinearLayout，居中）：
1. 顶部说明标题 TextView（约 3 行，居中）。
2. 单个输入框 `miuix.androidbasewidget.widget.StateEditText`：
   - `singleLine=true`
   - PIN：`inputType=0x81`（numberPassword = 数字类 + 密码遮罩）
   - 混合：`inputType=textPassword`（字母数字符号 + 遮罩）
   - `imeOptions=Done`，textStyle 粗体
3. 底部按钮区（include）：确认/下一步。

**两遍确认是分阶段切换，不是同屏两个输入框**（Stage：输入 → NeedToConfirm → ConfirmWrong）：
- 第一遍输入 → 进入确认阶段，再输一遍；
- 两遍不一致 → ConfirmWrong 提示重输，不清空节奏。

## 2. 校验规则（ChooseLockPassword.java，mPasswordMinLength=4 / MaxLength=16）
- **PIN（quality 131072/196608）**：只能纯数字，长度 4–16；含字母/符号报「PIN 含非数字」。
- **混合密码（262144/327680/393216）**：可打印字符，长度 4–16；MIUI 可选最小字母/数字要求，我们简化为任意可打印 4–16 位。
- 非法字符（<空格 或 >127）拒绝。
- 我们录入页只需：长度 4–16、PIN 纯数字、两遍一致。

## 3. 图案页（choose_lock_pattern.xml）
- `com.android.settings.LockPatternView`（3×3）+ 顶部标题/说明。
- 最少连接点数（系统惯例 4 点），两遍图案一致才提交。
- 图案点序列编码需对到 trustservice 的 MiuiLockPatternView（hook 时还原），单独核。

## 4. 复刻到本 App 的落点
- 入口：妙享直通(silent_auth)开启后，「锁屏密码预设置」可点行。
- 类型：root 自动识别本机锁屏类型；读不到给三选（数字PIN / 混合密码 / 图案），可手动切。
- PIN：miuix 官方输入框 + 数字键盘，4–16 位，两遍比对。
- 混合：官方输入框 + 眼睛显隐，4–16 位，两遍。
- 图案：3×3 两遍（编码待核）。
- 已设置：显示类型，可修改/清除；关 silent_auth 自动清凭证。
- 存储：AES-GCM 加密入 config.json（auth_type/auth_secret/auth_pattern）。
