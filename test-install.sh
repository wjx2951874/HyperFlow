#!/usr/bin/env bash
# ============================================================
# HyperFlow 测试包一键构建（不发版）
# 用法：./test-install.sh
# 产物：hyperflow/HyperFlow-test.apk（覆盖安装用，正式签名）
#       hyperflow/HyperFlow-test-flashable.zip（仅改 hook 时才需要刷模块）
# 注意：构建后需把 APK 传给手机，手机直接覆盖安装即可测 UI；
#       更新检测因 versionCode=128 > 线上 127 自动静默，不会弹更新。
# ============================================================
set -e
cd "$(dirname "$0")/hyperflow-miuix"

JAVA_HOME=/home/user/.doubao_tools/jdk-21.0.12.1+1 \
ANDROID_HOME=/home/user/.doubao_tools/android-sdk \
/home/user/.doubao_tools/gradle-9.6.0/bin/gradle :app:assembleRelease --no-daemon

APK=app/build/outputs/apk/release/app-release.apk
OUT_APK=../HyperFlow-test.apk
OUT_ZIP=../HyperFlow-test-flashable.zip
cp "$APK" "$OUT_APK"

# 组装 flashable zip（改 hook 时才需要刷；纯 UI 改动用不到）
rm -rf /tmp/hf_test_flash && mkdir -p /tmp/hf_test_flash/system/priv-app/HyperFlowPlus
cp "$APK" /tmp/hf_test_flash/system/priv-app/HyperFlowPlus/HyperFlowPlus.apk
cp "$APK" /tmp/hf_test_flash/HyperFlowPlus.apk
cp ../hyperflow-miuix/ksu-module/service.sh /tmp/hf_test_flash/service.sh
cp ../hyperflow-miuix/ksu-module/customize.sh /tmp/hf_test_flash/customize.sh
cp ../hyperflow-miuix/ksu-module/module.prop /tmp/hf_test_flash/module.prop
cp ../hyperflow-miuix/ksu-module/README.txt /tmp/hf_test_flash/README.txt
(cd /tmp/hf_test_flash && zip -r -X "$OLDPWD/$OUT_ZIP" . >/dev/null)

echo ""
echo "✅ 构建完成（versionCode 128 / 1.0.2-test）"
echo "   APK（覆盖安装测 UI）：$(cd .. && pwd)/$OUT_APK"
echo "   zip（仅改 hook 时刷模块）：$(cd .. && pwd)/$OUT_ZIP"
echo ""
echo "手机端操作：下载 APK → 直接覆盖安装 → 打开 App 查看效果"
echo "（无需卸载、不丢数据；模块未改动则无需刷 zip/重启）"
