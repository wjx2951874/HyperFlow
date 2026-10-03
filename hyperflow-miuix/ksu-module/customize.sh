#!/system/bin/sh
# HyperFlow v0.6.3：KSU/Magisk 刷入时静默安装/更新管理 App（重启前完成，重启一次全到位）
# 说明：KSU 管理器安装模块时运行本脚本（系统环境，pm 可用）。
#       相比仅靠 service.sh 在 boot 后安装，这里在"重启前"就把 APK 装好，
#       重启后模块与 App 同时为最新版，不再需要"刷完重启一次、再更新 App 一次"。
# v0.6.3：KSU 安装 zip 是白名单式解压，zip 根目录的 APK 不会被解压到模块目录
#         （v0.6.0~0.6.2 因此"HyperFlowPlus.apk not found"）。改为 find 全树搜索，
#         优先模块根，兜底命中 system/priv-app/ 下的实体 APK（KSU 会解压 system 子树）。
MODDIR=${0%/*}
# MODDIR 在部分 KSU 版本以相对路径执行 customize.sh 时会失效，这里兜底
[ -d "$MODDIR" ] || MODDIR=$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")
[ -d "$MODDIR" ] || MODDIR=$(pwd)

APK="$MODDIR/HyperFlowPlus.apk"
[ -f "$APK" ] || APK=$(find "$MODDIR" -maxdepth 4 -name "HyperFlowPlus.apk" 2>/dev/null | head -1)
[ -f "$APK" ] || APK=$(find "$MODDIR" -maxdepth 4 -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK" ] && [ -f "$APK" ]; then
  if pm install -r -g "$APK" 2>/dev/null; then
    echo "HyperFlow: App installed/updated at flash time ($APK)"
  else
    echo "HyperFlow: pm install failed at flash time (fallback: service.sh retries after boot)"
  fi
else
  echo "HyperFlow: no APK found in module dir"
fi
