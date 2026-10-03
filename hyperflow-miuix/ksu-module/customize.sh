#!/system/bin/sh
# HyperFlow v0.6.5：KSU 刷入时尝试安装/更新管理 App（重启后由 service.sh 兜底）
# 实证（v0.6.4 日志）：KSU 执行 customize.sh 时模块目录里只有 module.prop，
# 其余文件（含 APK）尚未解压 → 此阶段 find 永远找不到 APK，属执行时机问题。
# 因此：此处能找到就装（部分 KSU/Magisk 变体可见），找不到不报错，
# 由 service.sh 在 boot 后（模块完整落盘）自动安装，重启一次即"模块+App 全部到位"。
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
    echo "HyperFlow: pm install failed at flash time (service.sh will retry after boot)"
  fi
else
  echo "HyperFlow: APK not present at flash stage (KSU hasn't extracted module files yet); App will be installed by service.sh after reboot"
fi
