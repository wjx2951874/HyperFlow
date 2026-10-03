#!/system/bin/sh
# HyperFlow v0.6.0：KSU/Magisk 刷入时静默安装/更新管理 App（重启前完成，重启一次全到位）
# 说明：KSU 管理器安装模块时运行本脚本（系统环境，pm 可用）。
#       相比仅靠 service.sh 在 boot 后安装，这里在"重启前"就把 APK 装好，
#       重启后模块与 App 同时为最新版，不再需要"刷完重启一次、再更新 App 一次"。
MODDIR=${0%/*}

APK="$MODDIR/HyperFlowPlus.apk"
if [ -f "$APK" ]; then
  if pm install -r -g "$APK" 2>/dev/null; then
    echo "HyperFlow: App installed/updated at flash time (v0.6.0)"
  else
    echo "HyperFlow: pm install failed at flash time (fallback: service.sh retries after boot)"
  fi
else
  echo "HyperFlow: HyperFlowPlus.apk not found in module dir"
fi
