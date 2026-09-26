#!/bin/bash
# Rules ki jaanch - bina phone, bina emulator, bina Gradle.
#
# Rules.kt mein koi Android cheez nahi hai, sirf numbers ka hisaab. Isliye use
# seedha laptop par chala kar dekha ja sakta hai ki thresholds sahi hain.
# Threshold badlo to ye chala lena.
# Dhyan: AIDL alag se check hoti hai - tools/check-aidl.ps1 chalaiye.
# Ye script sirf Kotlin rules test karti hai; .aidl file ko chhoo-ti bhi nahi.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../app/src/main/java/com/mustfa/heatguard"
ANDROID="D:/sdk/Android/Sdk/platforms/android-36/android.jar"
KOTLINC="/c/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib/kotlin-compiler.jar"
KLIB=$(cygpath -m "/c/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib/kotlin-stdlib.jar")
OUT=$(cygpath -m /tmp/hg_test_out); rm -rf /tmp/hg_test_out; mkdir -p /tmp/hg_test_out

ARG=/tmp/hg_test_args.txt
{
  echo "-classpath"; echo "$ANDROID"
  echo "-d"; echo "$OUT"
  echo "\"$(cygpath -m "$SRC/Vitals.kt")\""
  echo "\"$(cygpath -m "$SRC/CpuInspector.kt")\""
  echo "\"$(cygpath -m "$SRC/Rules.kt")\""
  echo "\"$(cygpath -m "$HERE/RulesTest.kt")\""
} > "$ARG"

java -jar "$KOTLINC" "@$ARG" 2>&1 | grep -E "error:" && { echo "compile fail"; exit 2; }
java -cp "$OUT;$KLIB;$ANDROID" RulesTestKt
