#!/bin/bash
# Poore project ka Kotlin type-check, bina Gradle ke.
#
# Gradle Claude ke sandbox mein chalta hi nahi ("Unable to establish loopback
# connection"), isliye ye script hai. Ye asli kotlinc chalati hai, asli
# android.jar, asli AIDL-generated classes aur asli Shizuku jar ke saath.
#
# R.java ka stub yahan banta hai. Uske do jaal jinme main pehle phans chuka
# hoon:
#
#   1. `sort -u` duplicate resource ko chup-chaap gira deta hai, to duplicate
#      string kabhi pakdi hi nahi jaati thi (build mein pakdi gayi).
#      -> Isliye duplicate ki jaanch tools/check-res.sh alag se karti hai,
#         aur ye script use pehle chalati hai.
#
#   2. Stub mein jo resource type main bhool jaun, uski har entry "unresolved
#      reference" ban kar aati hai aur asli galti jaisi lagti hai.
#      -> Isliye neeche SAARE types hain: string, layout, id, drawable,
#         style, color, mipmap, xml, array, plurals.
#
#   style ka naam XML mein dot se hota hai (Theme.HeatGuard.Dark) par R mein
#   underscore se (Theme_HeatGuard_Dark) - isliye tr se badla jata hai.
#
# Chalane ka tarika:  bash tools/check-kotlin.sh

set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
SRC="$ROOT/app/src/main/java/com/mustfa/heatguard"
RES="$ROOT/app/src/main/res"
ANDROID="D:/sdk/Android/Sdk/platforms/android-36/android.jar"
KOTLINC="/c/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib/kotlin-compiler.jar"

# ---- 0. Resources pehle (duplicate/gayab yahin pakdi jaati hain) ----
bash "$HERE/check-res.sh" || { echo "Resources fail - Kotlin check ka matlab nahi."; exit 1; }

WORK=/tmp/hg_check; rm -rf "$WORK"; mkdir -p "$WORK/stub/com/mustfa/heatguard" "$WORK/stubout" "$WORK/aidl" "$WORK/out" "$WORK/libs"

# ---- 1. R.java stub ----
R="$WORK/stub/com/mustfa/heatguard/R.java"
{
  echo "package com.mustfa.heatguard;"
  echo "public final class R {"

  emit() {  # emit <className> <naam ki list>
    echo "  public static final class $1 {"
    shift
    for n in "$@"; do echo "    public static final int $n=1;"; done
    echo "  }"
  }

  emit string $(grep -rhoE '<string name="[^"]+"' "$RES"/values/*.xml 2>/dev/null | sed 's/.*name="//;s/"//' | sort -u)
  emit color  $(grep -rhoE '<color name="[^"]+"'  "$RES"/values/*.xml 2>/dev/null | sed 's/.*name="//;s/"//' | sort -u)
  emit array  $(grep -rhoE '<(string|integer)-array name="[^"]+"' "$RES"/values/*.xml 2>/dev/null | sed 's/.*name="//;s/"//' | sort -u)
  # style: dot -> underscore
  emit style  $(grep -rhoE '<style name="[^"]+"' "$RES"/values/*.xml 2>/dev/null | sed 's/.*name="//;s/"//' | tr '.' '_' | sort -u)
  emit layout $(ls "$RES"/layout/*.xml 2>/dev/null | xargs -rn1 basename | sed 's/\.xml//' | sort -u)
  emit id     $(grep -rhoE 'android:id="@\+id/[A-Za-z0-9_]+"' "$RES"/layout/*.xml 2>/dev/null | sed 's/.*@+id\///;s/"//' | sort -u)
  emit drawable $(ls "$RES"/drawable*/* 2>/dev/null | xargs -rn1 basename | sed 's/\.[a-z]*$//' | sort -u)
  emit mipmap   $(ls "$RES"/mipmap*/* 2>/dev/null | xargs -rn1 basename | sed 's/\.[a-z]*$//' | sort -u)
  emit xml      $(ls "$RES"/xml/* 2>/dev/null | xargs -rn1 basename | sed 's/\.[a-z]*$//' | sort -u)

  echo "}"
} > "$R"
javac -nowarn -d "$WORK/stubout" "$R" 2>&1 | head -5

# ---- 2. AIDL -> Java -> classes ----
AIDLOUT="/c/Users/$USERNAME/AppData/Local/Temp/hg_aidl_check/out"
if [ ! -d "$AIDLOUT" ]; then
  powershell -NoProfile -File "$(cygpath -w "$HERE/check-aidl.ps1")" >/dev/null 2>&1
fi
if [ -d "$AIDLOUT" ]; then
  find "$AIDLOUT" -name "*.java" -exec javac -nowarn -cp "$ANDROID" -d "$WORK/aidl" {} + 2>&1 | head -3
fi

# ---- 3. Shizuku ke jars (Gradle cache se) ----
for aar in $(find "D:/caches/gradle" -ipath "*dev.rikka.shizuku*" -name "*.aar" 2>/dev/null); do
  n=$(basename "$aar" .aar)
  mkdir -p "$WORK/libs/$n" && (cd "$WORK/libs/$n" && unzip -oq "$aar" classes.jar 2>/dev/null)
done
LIBS=$(find "$WORK/libs" -name "classes.jar" | while read j; do printf ";%s" "$(cygpath -m "$j")"; done)

# ---- 4. Kotlin ----
CP="$ANDROID;$(cygpath -m "$WORK/stubout");$(cygpath -m "$WORK/aidl")$LIBS"
ARG="$WORK/args.txt"
{
  echo "-classpath"; echo "$CP"
  echo "-d"; echo "$(cygpath -m "$WORK/out")"
  for f in "$SRC"/*.kt; do echo "\"$(cygpath -m "$f")\""; done
} > "$ARG"

java -jar "$KOTLINC" "@$ARG" > "$WORK/log.txt" 2>&1
rc=$?
classes=$(find "$WORK/out" -name "*.class" 2>/dev/null | wc -l)
errors=$(grep -c "error:" "$WORK/log.txt")
warnings=$(grep -c "warning:" "$WORK/log.txt")

echo "KOTLIN: exit=$rc  classes=$classes  errors=$errors  warnings=$warnings"
if [ "$errors" -gt 0 ]; then
  grep "error:" "$WORK/log.txt" | head -20
  exit 1
fi
if [ "$classes" -eq 0 ]; then
  echo "!! Ek bhi class nahi bani - compiler shayad chala hi nahi. Ye result kuch prove nahi karta."
  head -5 "$WORK/log.txt"
  exit 2
fi
grep "warning:" "$WORK/log.txt" | head -10
exit 0
