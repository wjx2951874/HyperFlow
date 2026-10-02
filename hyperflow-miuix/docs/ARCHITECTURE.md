# HyperFlow 架构

> 阅读顺序：先看本文件了解整体，再看 [FEATURES.md](FEATURES.md) 了解每个功能的实现细节，最后 [DEVELOPMENT.md](DEVELOPMENT.md) 上手开发。

## 1. 三层结构总览

HyperFlow 不是单一 App，而是**一个可刷入的 KernelSU 模块**，内部包含三层：

```
┌─────────────────────────────────────────────────────────────┐
│ ① 管理 App（com.hyperflowplus，Compose + Miuix）            │
│    进程：独立 App 进程                                       │
│    职责：UI（首页/流转/消息/设置）+ 配置读写（su）+ 环境检测  │
├─────────────────────────────────────────────────────────────┤
│ ② Xposed hooks（libxposed API，注入两个目标进程）           │
│    ├─ com.milink.service 进程：5 个通知流转 hook             │
│    └─ system_server 进程（android 作用域）：1 个点击拉 999   │
├─────────────────────────────────────────────────────────────┤
│ ③ KernelSU 模块（ksu-module/）                              │
│    service.sh：KSU 白名单 + 自动安装/升级管理 App            │
│    module.prop：模块元数据 + updateJson（在线更新入口）      │
└─────────────────────────────────────────────────────────────┘
```

### 1.1 为什么是"模块 + App"双载体？

- 管理 App 要作为**系统应用**存在（需要读 milink 进程的 SharedPreferences、稳定后台），
  直接 `pm install` 的系统应用在部分机型会被清理 → 通过 KernelSU 模块刷入 `system/priv-app/` 最稳。
- `service.sh` 在开机后自动 `pm install -r -g`，保证 App 版本跟随模块版本（未装则装、旧版则覆盖）。
- 同时模块也是 LSPosed 识别 App 的载体（LSPosed 的"模块"其实指 APK，作用域见下）。

## 2. 进程注入（XposedEntry）

入口类：`java/com/hyperflowplus/XposedEntry.java`（libxposed API，`META-INF/xposed/java_init.list` 注册）。

```
onPackageLoaded(param)
├─ 无条件 markLoaded()          # 写运行态时间戳（供首页检测判断"模块是否真被加载"）
├─ package == "android"        → 注入 system_server
│    └─ HookRemoteOpen.installSystem(cl)
│       # 拦截"跨设备打开应用"的 startActivity 请求，分身通知点击 → 999 空间
├─ package == "com.milink.service" → 注入小米互联服务
│    ├─ HookForceTransfer      # ① 亮屏强制流转（模拟锁屏）
│    ├─ HookCallRelay          # ④ 来电在线接听
│    ├─ HookCloneBypass        # ② 分身通知流转（999 放行 + 【分身】前缀 + 去重）
│    ├─ HookSmsPersist         # ③ 接收端短信持久化（App 归档 + 写系统收件箱）
│    ├─ HookSmsSenderEnrich    # ③ 发送端补号（把原始号码写进流转协议）
│    └─ HookAutoUnlock         # ⑤ 自动输密码（占位，P1）
└─ 其他包名 → return（不注入）
```

**作用域（LSPosed）**：`app/src/main/res/values/arrays.xml` 预勾选三个——
`com.milink.service`（核心）、`com.hyperflowplus`（运行态标记）、`android`（点击拉 999 必须）。

**为什么 hook 用 Java 而 UI 用 Kotlin？** 历史原因：hook 从 V0.2 起就是 Java（libxposed API 直接反射），
迁移成本高且已稳定；UI 在 V0.4.0 从手写 View 全面迁移到 Kotlin + Compose + Miuix。

## 3. 配置与数据流

### 3.1 全局配置（config.json）

- 位置：`/data/adb/hyperflowplus/config.json`（root 可读写）
- 读写方：
  - App 进程：`HFState`（Compose 状态）+ `Config.java` 通过 **su** 直读直写
  - hook 进程（milink）：`Config.java` su 读取 + **800ms 缓存**（通知触发时懒加载，开关切换秒级生效）
- 全部 key 见 `java/com/hyperflowplus/Config.java`（`force_transfer` / `clone_transfer` / `sms_persist` / `archive_app` / `nav_float` / `glass_effect` / `debug_mode` / `archive_sort` / `detail_sort` …）

### 3.2 运行时计数器

- 存储：milink 进程 SharedPreferences（hook 内 bump 就地写）
- App 通过 su 解析 milink 的 prefs XML 展示（首页/流转页的计数）

### 3.3 通知流转数据流（发送端 → 接收端）

```
发送端（root 设备，装 HyperFlow）
  系统通知 (post)
    └─ com.milink.service 的 NotificationListener 收到
         ├─ isDeviceSupported()      ← HookForceTransfer 强制 true（亮屏放行）
         ├─ isNotificationValid()    ← HookCloneBypass：999 分身放行 + 去重 + 打【分身】前缀
         └─ buildPlainMessage()      ← 序列化前兜底（标题前缀 / 来电 voip 标记）
              └─ NotificationMessage 经小米账号通道流转
接收端（root 设备，装 HyperFlow）
  SmsMessageHandler.handleMiMessage()
    └─ HookSmsPersist：写入 flow provider（消息页数据源）+ 可选注入系统收件箱
```

**点击分身通知（接收端）→ 打开 999 应用**：

```
接收端点击分身通知
  → 系统"跨设备镜像打开应用"把 Intent 发回发送端
  → milink 在 system_server 执行 startActivity（目标包=微信/QQ，userId=0）
  → HookRemoteOpen：检测到"调用者=milink && 目标包 ∈ {微信,QQ} && 该包存在 999 空间"
       → 改写 userId=999 重发 → 直接打开多开微信/QQ
```

## 4. 关键设计决策（为什么这么写）

| # | 决策 | 原因 / 历史教训 |
|---|---|---|
| D1 | 亮屏流转 = hook `isDeviceSupported` 返回 true（等效模拟锁屏） | 反编译确认官方逻辑"锁屏才流转"；返回 true 是最小侵入方案。副作用：互联侧显示设备"已锁屏"，用户接受 |
| D2 | 分身流转 = **只放行不改 user**（不做归一、不做镜像） | V0.2.4 做"user 归一"→ 发送端 systemui 也显示一条（重复 bug）；V0.4.43 镜像方案复杂度高 → 放弃。只放行 + 标题【分身】前缀最稳 |
| D3 | 分身去重 = 内容指纹 key + 跨进程共享文件表（20s 窗口） | 同一条 999 消息会走小米互联两条流转链路（一条过 hook、一条原样），且可能在不同进程；单进程静态表拦不住第二条 |
| D4 | 点击拉 999 = hook system_server 的 startActivity（而非镜像通知） | 系统"跨设备打开"请求发到 system_server；在此改写 userId 最接近系统原逻辑，其余调用一律放行不误伤 |
| D5 | 短信持久化两层（flow 归档 + 写系统收件箱，默认只归档） | OS4 短信 App 不展示"流转消息"界面 → App 内归档兜底；写系统收件箱有回环/重复风险（测试多次复现）→ 默认关 + 10 秒倒计时确认弹窗 |
| D6 | 弹窗一律用 Compose `Popup`（Anchor 定位）而非系统 `Dialog` | 系统 Dialog 在悬浮窗/部分机型上闪退（用户实测）；Popup 不闪退、可精确控制位置与深浅色 |
| D7 | 弹窗智能移位（开悬浮/玻璃上移 88dp，否则 40dp） | 底部导航栏会遮挡弹窗下半部分；上移量按是否开启悬浮/玻璃动态调整 |
| D8 | 悬浮 + 玻璃 = InstallerX 同款结构（悬浮放 Scaffold bottomBar 槽 + 胶囊内部 `rememberLayerBackdrop` 自捕获） | V0.5.1 悬浮嵌内容 Box + 外层 backdrop 捕获 → 自捕获递归闪退；InstallerX Revived 源码确认正确结构：内容层之外放悬浮栏，backdrop 由胶囊自捕获窗口层 |
| D9 | 环境检测 LSP = **配置态即绿**（db/scope 命中即绿），运行态只做黄条提示 | V0.5.2 及以前要求"配置态 + 运行态"双通过才绿；但运行态时间戳由 milink（非 root）进程写，权限不足永远写不成功 → 用户勾选也爆红。配置态即绿 + 黄条提示"未重启"最贴近用户预期 |
| D10 | 更新检测多通道并行 + 取 versionCode 最大 | GitHub 国内直连超时；jsDelivr 对 update.json 有 12h+ 陈旧缓存且 ?v= 无效 → 已弃用，改用 ghproxy 系镜像 + raw 兜底，多通道取最大版本防误判"已是最新" |
| D11 | 更新下载走系统浏览器（`ACTION_VIEW`） | App 内下载进度不可见、用户无感知；浏览器下载有进度且可断点 |
| D12 | 配置只读 `/data/adb/hyperflowplus/config.json`，不启用 HTTP WebUI | V0.2.6 起放弃 WebUI：App 是唯一入口，su 读写秒级生效，攻击面更小 |

## 5. 目录速览

```
hyperflow-miuix/
├── app/
│   ├── build.gradle.kts            # versionCode/versionName 在此（发布 bump 处之一）
│   └── src/main/
│       ├── kotlin/com/hyperflowplus/
│       │   ├── HFApplication.kt     # Application：初始化 Config/RootExec/崩溃日志
│       │   ├── HFState.kt           # 全局 Compose 状态 + su 数据加载 + 环境检测缓存
│       │   ├── MainActivity.kt      # 入口：Scaffold + 4 Tab + 更新弹窗 + 引导/许可
│       │   └── ui/                  # 各页面与组件（见 README 功能表）
│       ├── java/com/hyperflowplus/
│       │   ├── XposedEntry.java     # libxposed 入口（进程分发 + hook 装配）
│       │   ├── Config.java          # 全局配置 key + updateJsonUrls + 计数器
│       │   ├── RootExec.java        # su 执行封装（App 侧）
│       │   ├── MiflowLog.java       # 日志
│       │   └── hooks/               # 7 个 hook（见第 2 节）
│       ├── resources/META-INF/xposed/  # LSPosed 识别元数据（作用域/API 版本）
│       └── res/values/arrays.xml    # LSPosed 预勾选作用域
├── ksu-module/                      # module.prop + service.sh + README.txt（模块载体）
├── libxposed-stub/                  # libxposed API 本地桩（坐标源不稳定时编译用）
├── .github/workflows/build.yml      # GitHub Actions 云构建（CI）
├── update.json                      # 在线更新元数据（version/zipUrl/changelog）【仓库根】
└── docs/                            # 本文档体系
```

## 6. 更新链路（App / KSU 两个入口）

```
App 内"检测更新"（MainActivity.checkUpdate）
  多通道并行 GET update.json（ghproxy 系 + raw）
  → 取 versionCode 最大 → 弹"更新日志"窗（完整 changelog）→ 浏览器下载 zip
KSU 模块列表"检查更新"
  → 读 module.prop 的 updateJson → 主页"新版本可用"卡片（点击弹 changelog）
  → 模块详情页"更新"按钮（KSU 交互：直接下载，不弹日志——这是 KSU 侧行为，模块无法改变）
```

> 注意：KSU 只有点击主页"新版本可用"卡片才弹更新日志；详情页"更新"按钮直接下载是 KernelSU 源码行为。
