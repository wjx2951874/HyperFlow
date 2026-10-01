#!/bin/bash
# HyperFlow v0.4.43 发布：zip 组装（8 条目）+ MD5
set -e
ROOT=/home/user/Doubao/chats/38444811295021314/hyperflow-miuix/hyperflow-miuix
VER=0.4.43
OUT=$ROOT/../hf-v0443
APK=$ROOT/app/build/outputs/apk/release/app-release.apk
MOD=$ROOT/ksu-module

rm -rf "$OUT" && mkdir -p "$OUT/system/priv-app/HyperFlowPlus"
cp "$APK" "$OUT/HyperFlowPlus.apk"
cp "$APK" "$OUT/system/priv-app/HyperFlowPlus/HyperFlowPlus.apk"
cp "$MOD/module.prop" "$OUT/module.prop"
cp "$MOD/service.sh" "$OUT/service.sh"
cp "$MOD/README.txt" "$OUT/README.txt"
mkdir -p "$OUT/system/priv-app"  # system/ 与 system/priv-app/ 目录条目
mkdir -p "$OUT/system"

cd "$OUT"
python3 - <<'EOF'
import zipfile, os
base = "HyperFlow-0.4.43-flashable.zip"
entries = [
    "system/", "system/priv-app/", "system/priv-app/HyperFlowPlus/",
    "module.prop", "HyperFlowPlus.apk", "service.sh", "README.txt",
    "system/priv-app/HyperFlowPlus/HyperFlowPlus.apk",
]
with zipfile.ZipFile(base, "w", zipfile.ZIP_DEFLATED) as z:
    for e in entries:
        p = e.rstrip("/")
        if os.path.isdir(p):
            z.writestr(e, "")
        else:
            z.write(e, e)
    for n in z.namelist():
        print(n, z.getinfo(n).file_size)
import hashlib
print("MD5:", hashlib.md5(open(base, "rb").read()).hexdigest())
EOF
