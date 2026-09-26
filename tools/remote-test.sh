#!/bin/bash
# Telegram remote ki jaanch: buttons ke label aur control link ka /start regex.
#
# Ye test Remote.kt ki PEHLE SE compile hui classes par chalta hai, jo
# check-kotlin.sh /tmp/hg_check/out mein chhod jati hai. Isliye yahan poora
# project dobara compile nahi hota - agar wo folder na ho to check-kotlin.sh
# khud chal jati hai.
#
# Chalane ka tarika:  bash tools/remote-test.sh
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../app/src/main/java/com/mustfa/heatguard"
ANDROID="D:/sdk/Android/Sdk/platforms/android-36/android.jar"
KOTLINC="/c/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib/kotlin-compiler.jar"
KLIB=$(cygpath -m "/c/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib/kotlin-stdlib.jar")
APP=/tmp/hg_check/out

[ -d "$APP" ] || bash "$HERE/check-kotlin.sh" || exit 1
[ -d "$APP" ] || { echo "$APP nahi mila - check-kotlin.sh chalaiye."; exit 1; }

# Commands ki list Remote.kt se, haath se nahi.
#
# Pattern jaan-boojh kar poore quote wale token par hai ("/status"), isliye
# help() ke andar likhe "/status - battery ..." jaise lambe vaakya isme nahi
# aate. Yaani yahan wahi aata hai jo execute() ki when-branch mein hai.
grep -oE '"/[a-z]+"' "$SRC/Remote.kt" | tr -d '"' | sort -u > /tmp/hg_cmds.txt
echo "Remote.kt mein commands: $(wc -l < /tmp/hg_cmds.txt)"

OUT=$(cygpath -m /tmp/hg_remote_out); rm -rf /tmp/hg_remote_out; mkdir -p /tmp/hg_remote_out
ARG=/tmp/hg_remote_args.txt
{
  echo "-classpath"; echo "$ANDROID;$(cygpath -m $APP)"
  echo "-d"; echo "$OUT"
  echo "\"$(cygpath -m "$HERE/RemoteTest.kt")\""
} > "$ARG"

java -jar "$KOTLINC" "@$ARG" 2>&1 | grep -E "error:" && { echo "compile fail"; exit 2; }
# Commands ki list Windows path ke roop mein jaati hai: JVM ko Git Bash ka
# /tmp nahi dikhta, use \tmp samajh kar dhoondhta hai aur nahi milta.
java -cp "$OUT;$(cygpath -m $APP);$KLIB;$ANDROID" RemoteTestKt "$(cygpath -m /tmp/hg_cmds.txt)"
