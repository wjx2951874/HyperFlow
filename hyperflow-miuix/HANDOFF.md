# HyperFlow 接手引导（给下一个 AI 工具 / 开发者）

> 本文档是"接手指令"。如果你是接手 HyperFlow 项目的下一个 AI 工具或开发者，**先完整读完本文，再动手**。
> 本文档不含任何密码、PAT、密钥；内部凭据见文末"敏感信息获取"。

---

## 0. 一句话项目定位

HyperFlow = **小米澎湃 OS（HyperOS）互联通知流转增强模块**，形态是 KernelSU 模块 + LSPosed 插件 + Miuix(Compose) 管理 App。作者酷安@翰德姆。通过 Xposed hook 小米互联服务（`com.milink.service`）的流转判定，实现亮屏也流转、分身（999 空间）通知流转、短信持久化、来电在线接听等能力。

- 公开仓库：`https://github.com/wjx2951874/HyperFlow`（branch: main）
- 当前版本：**v0.5.3（versionCode 83），全部为测试版（Beta），正式版（Stable，v1.x）尚未发布**
- 最近提交：`62ac3f5`（版本状态标注）、`27acd74`（update.json zipUrl 修复）、`dd65e8f`（全仓库中文注释 + docs 体系）

---

## 1. 接手第一步：按顺序读这些文档

不要跳读。仓库内所有源码已逐文件中文注释，文档体系如下：

| 顺序 | 文件 | 你能获得什么 |
|---|---|---|
| 1 | `README.md` | 项目是什么、功能总览、安装、文档索引、版本状态 |
| 2 | `docs/ARCHITECTURE.md` | 三层结构（App / milink / system_server）、进程注入、数据流、12 条设计决策 |
| 3 | `docs/FEATURES.md` | 每个功能的原理 → 代码位置 → 为什么这么写 → 已知限制 |
| 4 | `docs/DEVELOPMENT.md` | 构建、发版 bump 清单、8 条目 zip、发布验证、调试 |
| 5 | `docs/ROADMAP.md` | ✅ 已实现 / ⏳ 待验证 / 🔲 规划中 / 已放弃方案（别再走回头路） |
| 6 | `docs/CHANGELOG.md` | v0.4.30 ~ v0.5.3 全版本历史（含踩坑与修复原因） |
| 7 | 飞书接手指南 | 仓库外实操细节（本地构建路径、Actions secrets、发版防错、全部历史踩坑）：`https://my.feishu.cn/docx/MIVXdD99OoxsXdxNzFLc92mP6Bc` |

读完这 7 项，你对项目的理解应达到：能说出每个 hook 在哪个进程、配置存在哪、弹窗为什么用 Popup 而不是 Dialog、悬浮+玻璃为什么不能嵌在内容 Box 里。

---

## 2. 环境与构建

### 本地构建（已验证可用，其他工具可直接复用）

```bash
# 环境变量（本机路径，与 GitHub Actions 对齐）
export JAVA_HOME=/home/user/tools/jdk-17.0.13+11
export ANDROID_HOME=/home/user/Android/Sdk
GRADLE=/home/user/tools/gradle-9.7.1/bin/gradle

# 工程目录（git 根下的子目录）
cd hyperflow-miuix/hyperflow-miuix

# 编译验证（约 40s）
$GRADLE :app:compileDebugJavaWithJavac :app:compileDebugKotlin

# 发布构建（约 52s）
$GRADLE :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

签名密钥：仓库根 `keystore.jks`（已 gitignore，不入库）。`app/build.gradle.kts` 保留了 storeFile 回退与密钥口令默认值，未设环境变量时用默认值即可出正式签名包。**公开文档不写密钥内容。**

### GitHub Actions（其他工具可直接用，无需本地装环境）

- workflow：`.github/workflows/build.yml`，跑在 GitHub 托管 runner（ubuntu-latest）
- 触发：push 到 main / 打 `v*` tag / 手动运行
- 首次跑约几分钟（下载 Gradle 9.7.1 + SDK），`setup-gradle` 自动缓存，第二次起秒级
- 产物：APK + 8 条目 flashable zip（artifact 下载）；打 tag 自动创建 GitHub Release
- 签名 secrets（可选，不配则 debug 签名）：`HF_KEYSTORE_B64` / `HF_KEY_ALIAS` / `HF_KEY_PASS`，在仓库 Settings → Secrets and variables → Actions 配置

---

## 3. 核心约束（用户硬规则，违反会被打回）

用户验收极严，以下是反复确认过的硬约束：

1. **不要自绘 UI 组件**：悬浮导航栏、液态玻璃、弹窗、图标都用开源项目原本就有的实现（InstallerX Revived / KernelSU / Miuix），不要自己画。
2. **弹窗体系**：全部走 **Window 层 Popup**（禁用系统 Dialog，悬浮窗场景会闪退）；深浅色统一浅黑 `0xFF262626`（不纯黑）；标题正文居中、从下往上弹出；开启悬浮/玻璃时上移 88dp，否则 40dp，防导航栏遮挡。引导页酷安关注弹窗是全 App 弹窗的样式基准。
3. **悬浮导航栏 + 液态玻璃**：两开关分开、可同时开启；结构必须是 Scaffold bottomBar 槽 + 胶囊内部 `rememberLayerBackdrop()` 自捕获（嵌在内容 Box 内会自捕获递归打崩）。
4. **选中项拖动切 tab**：用户要的就是这个（按住选中项胶囊左右拖动切换底部 tab，InstallerX/KernelSU 同源，`DampedDragAnimation` 实现）。**用户明确不要"整条胶囊自由移动位置"**。
5. **LSP 检测**：用**配置态**判定（模块/作用域勾选 + modules_config.db grep），不要要求运行态时间戳（milink 非 root 进程写不了 → 永远爆红）；未重启仅顶部黄条。
6. **分身去重**：key = 包名|id|tag|标题+正文前 96 字符（内容指纹）+ 跨进程共享文件（`/data/adb/hyperflowplus/released_keys` 等三级回退）。**不要用"user 归一"方案**（发送端也会显示一条 → 重复）。
7. **更新通道**：弃用 jsDelivr（12h+ 陈旧缓存），走 ghproxy 系镜像多通道并行（ghproxy.net / ghfast.top / gh-proxy.com / raw 兜底）；App 下载安装包走浏览器（转圈看不到进度）。
8. **消息去重**：云端（小米端）有则本地隐藏；本地有而云端无则显示并加（本地）标注。
9. **图标**：绿勾用空心圆环 `Rounded.CheckCircleOutline`（KSU 主页同款，用户坚持 miuix 同源），不要自绘对号。
10. **"由衷感谢"类文案已去掉**，关于页作者放第二行、开源许可分开列表（不跳 GitHub）、不做关于页的下一页。

---

## 4. 待办与方向（接手后优先做这些）

详见 `docs/ROADMAP.md`，当前重点：

**⏳ 待真机验证（v0.5.3 刚改，用户未最终确认）**
- 悬浮 + 玻璃同开不闪退、玻璃折射可见
- 分身只出一条（跨进程去重）
- 点击【分身】通知拉起 999 微信/QQ
- LSP 勾选即绿（配置态判定）

**🔲 规划中**
- KSU 详情页"点更新直接弹日志"（改详情页更新提示为日志样式，方案未定）
- 分身点击拉 999 更多应用（QQ 已适配，其他逐个加）
- 自动输锁屏密码（P1，无障碍 / hook SystemUI 解锁，未定）
- 澎湃 OS 版本串补全、消息来源标记增强、更新日志国内可达优化

**正式版发布**：用户尚未确定 v1.0 时间与范围，发布前需与用户确认哪些功能达到稳定标准。

---

## 5. 提交与发布纪律

### 日常提交
- 按文件清单 `git add`（**勿 `git add -A`**，避免卷进 `hf-v0xx/` 产物目录与 `docs-internal/` 内部文档）
- push 前 `git status --short` 核对
- 构建产物（`.gradle/`、`app/build/`、`*.apk`、`*.zip`、`keystore.jks`）已 gitignore，不要入库

### 发版（bump 4 处必须同步）
1. `app/build.gradle.kts` — versionCode（整数 +1）/ versionName
2. `ksu-module/module.prop` — version / versionCode
3. 仓库根 `update.json` — version / versionCode / **zipUrl** / changelog
4. GitHub Release — 打 tag `v0.5.x`，上传 `HyperFlow-x.y.z-flashable.zip`

> **⚠️ 血泪教训（v0.5.3 踩坑）**：update.json 的 **zipUrl 最容易漏改**。v0.5.3 时只同步了 version/versionCode/changelog，zipUrl 仍指向 v0.5.1 旧包，导致 KSU 检测到"新版本"却下载旧包/下载失败。发版后必须：
> ```bash
> curl -s https://raw.githubusercontent.com/wjx2951874/HyperFlow/main/update.json | grep zipUrl
> ```
> 确认指向本次 Release 资产，且该 URL `curl -I` 返回 200。

### flashable zip 固定 8 条目
`system/`、`system/priv-app/`、`system/priv-app/HyperFlowPlus/`、`module.prop`（sed 重写版本）、`HyperFlowPlus.apk`（根目录）、`service.sh`、`README.txt`、`system/priv-app/HyperFlowPlus/HyperFlowPlus.apk`。组装脚本先例：`scripts-build-v0443.sh`。

---

## 6. 架构关键点速记（改 hook 前必读）

**XposedEntry 作用域分发**（`app/src/main/java/com/hyperflowplus/XposedEntry.java`）：

| 包名匹配 | 分发到 |
|---|---|
| `android` | HookRemoteOpen（system_server，分身点击拉 999：白名单调用方 + 11 参 startActivityAsUser 重发 user 999） |
| `com.milink.service` | HookForceTransfer（亮屏流转）/ HookCallRelay（来电在线接听）/ HookCloneBypass（分身流转去重）/ HookSmsPersist（短信持久化）/ HookSmsSenderEnrich / HookAutoUnlock |
| 其他包 | 仅 markLoaded（不注入业务） |

**配置与计数器**：
- 配置：`/data/adb/hyperflowplus/config.json`（App 以 su 读写；hook 侧只读 + 800ms TTL 缓存）
- 计数器：milink prefs 的 `hyperflowplus_cfg`
- LSPosed 注册：`app/src/main/assets/xposed_init`（必须入库，缺失会导致模块无法注册）

---

## 7. 敏感信息获取（公开文档不含密码）

- **内部交接文档**（含完整历史与内部凭据）：git 根 `docs-internal/HyperFlow-交接与更新日志.md`（工作区另有副本），**已 gitignore、勿 push、勿公开引用**。接手者需从项目所有者处获取本地访问权限。
- **GitHub PAT / keystore 口令**：不在任何公开文档中。接手者从项目所有者处获取，或使用自己的 PAT（仓库需写权限）。
- **飞书接手指南**（仓库外实操细节）：`https://my.feishu.cn/docx/MIVXdD99OoxsXdxNzFLc92mP6Bc`

---

## 8. 联系作者

酷安@[翰德姆](https://www.coolapk.com/u/4112338) —— 功能需求、验收标准、正式版计划均以作者确认为准。

---

*接手后如有疑问，先查 `docs/` 与飞书接手指南；仍无法解决再联系作者。不要在未确认的情况下擅自改变上述硬约束。*
