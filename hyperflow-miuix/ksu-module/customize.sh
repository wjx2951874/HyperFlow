#!/system/bin/sh
# HyperFlow v0.6.6：KSU 刷入时静默安装/更新管理 App
# 实证（v0.6.4 日志）：KSU 执行 customize.sh 时模块目录里只有 module.prop，
# 压缩包内 APK 尚未解压 → 本地 find 在此阶段永远找不到。
# 因此 v0.6.6 起：customize.sh 联网直下 GitHub Release 独立 APK asset 安装，
# 找不到 curl/wget 或下载失败时静默，由 service.sh 在 boot 后（模块完整落盘）兜底安装。
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR=$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")
[ -d "$MODDIR" ] || MODDIR=$(pwd)

install_apk() { # $1=apk路径
  pm install -r -g "$1" 2>/dev/null
}

# 通道1：从 KSU 下载的模块 zip 源文件提取 APK（v0.6.12）——不走网络，省 47MB 二次下载。
# KSU 刷写时 zip 常见存放路径：/data/adb/modules_update/、/data/adb/、/data/local/tmp/、/data/cache/
ZIP_SRC=""
for D in /data/adb/modules_update /data/adb /data/local/tmp /data/cache; do
  [ -d "$D" ] || continue
  F=$(ls "$D"/*.zip 2>/dev/null | grep -iE "hyperflow" | head -1)
  [ -n "$F" ] || F=$(ls "$D"/*.zip 2>/dev/null | head -1)
  if [ -n "$F" ] && [ -f "$F" ]; then ZIP_SRC="$F"; break; fi
done
if [ -n "$ZIP_SRC" ] && command -v unzip >/dev/null 2>&1; then
  APK_IN_ZIP=$(unzip -l "$ZIP_SRC" 2>/dev/null | grep -oE "[^ ]+\.apk" | head -1)
  if [ -n "$APK_IN_ZIP" ]; then
    unzip -p "$ZIP_SRC" "$APK_IN_ZIP" > /data/local/tmp/hf_zip.apk 2>/dev/null
    if [ -s /data/local/tmp/hf_zip.apk ]; then
      if install_apk /data/local/tmp/hf_zip.apk; then
        echo "HyperFlow: App installed from module zip ($ZIP_SRC -> $APK_IN_ZIP)"
        rm -f /data/local/tmp/hf_zip.apk
        exit 0
      fi
    fi
    rm -f /data/local/tmp/hf_zip.apk
    echo "HyperFlow: zip found ($ZIP_SRC) but APK extract/install failed, fallback"
  else
    echo "HyperFlow: zip found ($ZIP_SRC) but no apk inside, fallback"
  fi
else
  echo "HyperFlow: no module zip source found (looked: modules_update /adb /local/tmp /cache), fallback"
fi

# 通道2：模块目录本地 APK（部分 KSU/Magisk 变体解压可见）
APK="$MODDIR/HyperFlowPlus.apk"
[ -f "$APK" ] || APK=$(find "$MODDIR" -name "HyperFlowPlus.apk" 2>/dev/null | head -1)
[ -f "$APK" ] || APK=$(find "$MODDIR" -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK" ] && [ -f "$APK" ]; then
  if install_apk "$APK"; then
    echo "HyperFlow: App installed/updated from module dir ($APK)"
    exit 0
  fi
fi

# 通道2：联网直下 GitHub Release 独立 APK（v0.6.6；镜像 gh-proxy + 直连双通道）
# v0.6.11：App 已装且版本不低于模块 → 直接跳过联网下载（老用户刷入不再多下 47MB APK）
VER=$(grep '^version=' "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2)
MOD_CODE=$(grep '^versionCode=' "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2)
APP_CODE=$(dumpsys package com.hyperflowplus 2>/dev/null | grep 'versionCode=' | head -1 | cut -d= -f2 | cut -d' ' -f1)
if [ -n "$APP_CODE" ] && [ -n "$MOD_CODE" ] && [ "$APP_CODE" -ge "$MOD_CODE" ] 2>/dev/null; then
  echo "HyperFlow: App already v$APP_CODE >= module v$MOD_CODE, skip network APK"
  exit 0
fi
if [ -n "$VER" ]; then
  TMP1=/data/local/tmp/hf_net.apk
  DL=""
  echo "HyperFlow: downloading App $VER (~47MB) ..."
  for U in \
    "https://gh-proxy.com/https://github.com/wjx2951874/HyperFlow/releases/download/v${VER}/HyperFlow-${VER}.apk" \
    "https://ghfast.top/https://github.com/wjx2951874/HyperFlow/releases/download/v${VER}/HyperFlow-${VER}.apk" \
    "https://github.com/wjx2951874/HyperFlow/releases/download/v${VER}/HyperFlow-${VER}.apk"
  do
    if command -v curl >/dev/null 2>&1; then
      curl -k -L -s --max-time 180 -o "$TMP1" "$U" && [ -s "$TMP1" ] && { DL=1; break; }
    elif command -v wget >/dev/null 2>&1; then
      wget -q -O "$TMP1" "$U" && [ -s "$TMP1" ] && { DL=1; break; }
    else
      break
    fi
  done
  if [ -n "$DL" ]; then
    if install_apk "$TMP1"; then
      echo "HyperFlow: App installed/updated from network ($VER)"
      rm -f "$TMP1"
      exit 0
    fi
    rm -f "$TMP1"
  fi
  echo "HyperFlow: network APK download failed for $VER (service.sh will retry after boot)"
else
  echo "HyperFlow: no version in module.prop (service.sh will retry after boot)"
fi
