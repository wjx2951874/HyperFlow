# HyperFlow 开发指南

面向想要**看懂、接手、继续开发**本项目的开发者/AI 工具。先读 [ARCHITECTURE.md](ARCHITECTURE.md) 与 [FEATURES.md](FEATURES.md)。

## 0. 技术栈

| 层 | 技术 | 说明 |
|---|---|---|
| UI | Kotlin + Jetpack Compose + **Miuix**（`top.yukonga.miuix.kmp`） | HyperOS 原生质感组件库 |
| Xposed | Java + **libxposed**（LSPosed 新 API） | `META-INF/xposed/` 注册 |
| Root 模块 | KernelSU / APatch / Magisk | `ksu-module/` 刷入 |
| 构建 | Gradle + AGP（JVM 17） | 本地与 GitHub Actions 均可 |

## 1. 环境准备

- JDK 17、Android SDK（compileSdk 35 / minSdk 33）
- Gradle 工程根：`hyperflow-miuix/`
- libxposed API：`libxposed-stub/` 本地桩（坐标源不稳定时编译用）

## 2. 构建

```bash
# 编译检查（快）
gradle :app:compileDebugKotlin

# 完整 Release APK
gradle :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

Release APK 需要签名（本地 keystore 或 CI 签名；密钥信息属私有，不入库、不写入本文档）。

## 3. 发布新版本（版本号 bump 清单）

**每次发版必须同步修改 4 处**（版本号不一致会导致更新检测/安装判断错乱）：

1. `app/build.gradle.kts` — `versionCode`（整数 +1）/ `versionName`（如 `0.5.3`）
2. `ksu-module/module.prop` — `version=` / `versionCode=`
3. 仓库根 `update.json` — `version` / `versionCode` / `zipUrl`（Release 下载直链）/ `changelog`
4. Release 发布 — 打 tag `v0.5.3`，上传 `HyperFlow-x.y.z-flashable.zip`

**update.json 维护规范**：必须用 `python json.dumps(ensure_ascii=False, indent=2)` 生成后
再 `json.loads` 校验一遍再提交——手写 JSON 出错会让 KSU/App 都解析失败（历史教训）。

### 组装可刷入 zip（8 条目，KSU 模块格式）

```
HyperFlow-x.y.z-flashable.zip
├── module.prop          # 模块元数据 + updateJson
├── HyperFlowPlus.apk    # 管理 App（根目录一份）
├── service.sh           # 开机：KSU 白名单 + 自动安装/升级 App
├── README.txt           # 模块内说明
└── system/
    └── priv-app/
        └── HyperFlowPlus/
            └── HyperFlowPlus.apk   # 系统应用位（刷入自动装）
```

（`scripts-build-v0443.sh` 有现成的 python zipfile 组装先例，可参考改版本号复用。）

### 发布后验证（必做）

1. **update.json 三通道可达**：gh-proxy.com / cdn.jsdelivr.net / raw.githubusercontent.com
   分别强刷（加时间戳参数绕 CDN 缓存），确认返回最新版本号
2. App 内"检测更新"应显示新版本 + 完整 changelog
3. KSU 模块列表应出现"新版本可用"（点击卡片弹 changelog）

## 4. 代码结构速记

```
java/com/hyperflowplus/
├── XposedEntry.java       # 入口：进程分发 + hook 装配（改 hook 先看这里）
├── Config.java            # 所有配置 key / 更新通道 / 计数器 —— 新功能先在这里加 key
├── RootExec.java          # su 封装（App 侧执行 shell）
├── MiflowLog.java         # 日志
└── hooks/                 # 每个 hook 一个文件，文件头有完整原理注释
kotlin/com/hyperflowplus/
├── HFState.kt             # 全局状态（Compose mutableStateOf）+ su 数据加载 + 检测缓存
├── MainActivity.kt        # Scaffold + 4 Tab + 弹窗智能移位 + 更新检测 + 引导/许可分发
└── ui/                    # 页面与组件（README 功能表有映射）
```

## 5. 开发规范

1. **hook 改动要防误伤**：所有 hook 先判断目标包名/调用者/条件，不匹配一律走系统原逻辑
2. **配置 key 集中管理**：新增开关先在 `Config.java` 定义，App 与 hook 共用同一 key
3. **弹窗用 Popup，不用 Dialog**（Dialog 在悬浮窗场景闪退）
4. **注释语言**：中文（本项目约定，为了让接手者看得懂）
5. **私密信息不入库**：密钥、密码、内部发布凭据绝不写入任何公开文件（含本仓库 docs/）

## 6. 常见调试手段

- 日志 tag：`HyperFlow`（MiflowLog 统一前缀）
- 环境检测失败时看 `/data/adb/hyperflowplus/detect.log`（检测脚本原始输出）
- 运行态标记文件：`/data/adb/hyperflowplus/xposed_loaded`（模块被 LSP 真正加载的时间戳）
- 全局配置：`/data/adb/hyperflowplus/config.json`（su 可读可改，秒级生效）
- 计数器：milink 进程 SharedPreferences（`hyperflowplus_cfg`，App 经 su 解析 XML 展示）
