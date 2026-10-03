#!/system/bin/sh
# HyperFlow 辅助模块：KSU 白名单 + 自动安装/更新管理 App（boot 后执行，模块已完整落盘）
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")
[ -d "$MODDIR" ] || MODDIR=$(pwd)

KSUD=$(command -v ksud)
if [ -n "$KSUD" ]; then
  "$KSUD" allowlist add com.milink.service 2>/dev/null || true
  "$KSUD" allowlist add com.hyperflowplus 2>/dev/null || true
else
  echo "HyperFlow: ksud not found, add com.milink.service & com.hyperflowplus to KSU allowlist manually" > /data/local/tmp/hyperflow_helper.log
fi

# 自动安装/更新管理 App：模块 boot 后完整挂载，find 必中 system/priv-app 实体 APK。
# 后台等待系统启动完成；未安装则装，版本旧则覆盖更新。
# v0.6.5：加日志到 /data/local/tmp/hyperflow_install.log（找不到/失败可定位）
APK="$MODDIR/HyperFlowPlus.apk"
[ -f "$APK" ] || APK=$(find "$MODDIR" -name "HyperFlowPlus.apk" 2>/dev/null | head -1)
[ -f "$APK" ] || APK=$(find "$MODDIR" -name "*.apk" 2>/dev/null | head -1)
(
  sleep 5
  n=0
  while [ "$(getprop sys.boot_completed)" != "1" ] && [ $n -lt 180 ]; do
    sleep 2
    n=$((n+1))
  done
  {
    echo "== $(date) =="
    echo "MODDIR=$MODDIR APK=$APK"
    if [ -n "$APK" ] && [ -f "$APK" ]; then
      MOD_VER=$(grep '^versionCode=' "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2)
      APP_VER=$(dumpsys package com.hyperflowplus 2>/dev/null | grep 'versionCode=' | head -1 | cut -d= -f2 | cut -d' ' -f1)
      echo "MOD_VER=$MOD_VER APP_VER=$APP_VER"
      if [ -z "$APP_VER" ]; then
        pm install -r -g "$APK" 2>&1
      elif [ -n "$MOD_VER" ] && [ "$APP_VER" -lt "$MOD_VER" ] 2>/dev/null; then
        pm install -r -g "$APK" 2>&1
      else
        echo "App already up to date"
      fi
    else
      echo "NO APK FOUND, module dir:"
      ls -laR "$MODDIR" 2>/dev/null | head -40
    fi
  } >> /data/local/tmp/hyperflow_install.log 2>&1
) &
