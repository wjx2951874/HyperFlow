#!/system/bin/sh
# HyperFlow 辅助模块：KSU 白名单 + 自动安装/更新管理 App
MODDIR=${0%/*}

KSUD=$(command -v ksud)
if [ -n "$KSUD" ]; then
  "$KSUD" allowlist add com.milink.service 2>/dev/null || true
  "$KSUD" allowlist add com.hyperflowplus 2>/dev/null || true
else
  echo "HyperFlow: ksud not found, add com.milink.service & com.hyperflowplus to KSU allowlist manually" > /data/local/tmp/hyperflow_helper.log
fi

# 自动安装/更新管理 App（后台等待系统启动完成；未安装则装，版本旧则覆盖更新）
APK="$MODDIR/HyperFlowPlus.apk"
if [ -f "$APK" ]; then
  (
    n=0
    while [ "$(getprop sys.boot_completed)" != "1" ] && [ $n -lt 180 ]; do
      sleep 2
      n=$((n+1))
    done
    MOD_VER=$(grep '^versionCode=' "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2)
    APP_VER=$(dumpsys package com.hyperflowplus 2>/dev/null | grep 'versionCode=' | head -1 | cut -d= -f2 | cut -d' ' -f1)
    if [ -z "$APP_VER" ]; then
      pm install -r -g "$APK" 2>/dev/null
    elif [ -n "$MOD_VER" ] && [ "$APP_VER" -lt "$MOD_VER" ] 2>/dev/null; then
      pm install -r -g "$APK" 2>/dev/null
    fi
  ) &
fi
