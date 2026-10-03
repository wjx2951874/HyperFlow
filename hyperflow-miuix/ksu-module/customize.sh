#!/system/bin/sh
# HyperFlow v0.6.4：KSU/Magisk 刷入时静默安装/更新管理 App（重启前完成，重启一次全到位）
# 说明：KSU 管理器安装模块时运行本脚本（系统环境，pm 可用）。
# v0.6.3：KSU 安装 zip 是白名单式解压，zip 根目录的 APK 不会被解压到模块目录
#         （v0.6.0~0.6.2 因此"HyperFlowPlus.apk not found"）。改为 find 全树搜索，
#         兜底命中 system/priv-app/ 下的实体 APK（KSU 会解压 system 子树）。
# v0.6.4：find 不限深度 + 打印 MODDIR 与搜索结果，若仍找不到可直接从安装日志定位 APK 实际位置。
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")
[ -d "$MODDIR" ] || MODDIR=$(pwd)

APK="$MODDIR/HyperFlowPlus.apk"
[ -f "$APK" ] || APK=$(find "$MODDIR" -name "HyperFlowPlus.apk" 2>/dev/null | head -1)
[ -f "$APK" ] || APK=$(find "$MODDIR" -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK" ] && [ -f "$APK" ]; then
  echo "HyperFlow: found APK at $APK"
  if pm install -r -g "$APK" 2>/dev/null; then
    echo "HyperFlow: App installed/updated at flash time"
  else
    echo "HyperFlow: pm install failed at flash time (fallback: service.sh retries after boot)"
  fi
else
  echo "HyperFlow: no APK found. MODDIR=$MODDIR"
  echo "HyperFlow: module dir listing:"
  ls -laR "$MODDIR" 2>/dev/null | head -60
fi
