#!/bin/bash
# Resources ki jaanch - build chalane se pehle.
#
# YE ISLIYE BANI
# --------------
# Ek duplicate string seedha user ki build mein pakdi gayi:
#
#     Found item String/deep_done more than one time
#
# Aur wo mere apne check ki kami thi. Kotlin check ke liye main R.java ka
# stub banata hoon, aur us stub mein naam `sort -u` se nikalte the. `-u`
# duplicate ko chup-chaap gira deta hai - to stub theek ban jata tha, Kotlin
# clean compile hota tha, aur duplicate pakda hi nahi jata tha.
#
# Sabak: jo cheez check ko SAAF banati hai, wahi galti chhupa sakti hai.
# Isliye ab duplicate ki jaanch ALAG se hoti hai, stub banne se pehle.
#
# Chalane ka tarika:  bash tools/check-res.sh

set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
RES="$HERE/../app/src/main/res"
rc=0

# ---- 1. Duplicate names (har resource type ke liye) ----
for kind in string color dimen bool integer; do
  dupes=$(grep -rhoE "<$kind name=\"[^\"]+\"" "$RES"/values*/*.xml 2>/dev/null \
    | sed "s/.*name=\"//;s/\"//" | sort | uniq -d)
  if [ -n "$dupes" ]; then
    echo "DUPLICATE <$kind>:"
    echo "$dupes" | sed 's/^/    /'
    rc=1
  fi
done

# ---- 2. Code jo aisi string maangta hai jo hai hi nahi ----
#
# Ye R stub wali dooserी kami hai: stub sirf un naamon se banta hai jo
# strings.xml mein hain, to "gayab string" kabhi pakdi hi nahi jaati thi -
# Kotlin ko wo naam mil hi nahi sakta tha, aur error ko "expected R noise"
# samajh liya jata.
have=$(grep -rhoE "<string name=\"[^\"]+\"" "$RES"/values/strings.xml 2>/dev/null \
  | sed 's/.*name="//;s/"//' | sort -u)
used=$(grep -rhoE "R\.string\.[A-Za-z0-9_]+" "$HERE/../app/src/main/java" 2>/dev/null \
  | sed 's/R\.string\.//' | sort -u)
missing=$(comm -13 <(echo "$have") <(echo "$used"))
if [ -n "$missing" ]; then
  echo "CODE MAANGTA HAI PAR strings.xml MEIN NAHI:"
  echo "$missing" | sed 's/^/    /'
  rc=1
fi

# ---- 3. Layout ke id jo code maangta hai ----
layoutIds=$(grep -rhoE 'android:id="@\+id/[A-Za-z0-9_]+"' "$RES"/layout/*.xml 2>/dev/null \
  | sed 's/.*@+id\///;s/"//' | sort -u)
# `android.R.id.content` jaisi platform ID bhi isi "R.id.xxx" shakal mein
# dikhti hai, isliye poora qualified naam pakad kar "android.R.id." wali
# lines pehle hata di jaati hain - warna har activity ka apna
# `android.R.id.content` ek "missing id" ban kar aata (ye kabhi layout.xml
# mein hoga hi nahi, wo Android khud deta hai).
usedIds=$(grep -rhoE "[A-Za-z0-9_.]*R\.id\.[A-Za-z0-9_]+" "$HERE/../app/src/main/java" 2>/dev/null \
  | grep -v "^android\.R\.id\." \
  | sed 's/.*R\.id\.//' | sort -u)
missingIds=$(comm -13 <(echo "$layoutIds") <(echo "$usedIds"))
if [ -n "$missingIds" ]; then
  echo "CODE MAANGTA HAI PAR LAYOUT MEIN NAHI:"
  echo "$missingIds" | sed 's/^/    /'
  rc=1
fi

if [ $rc -eq 0 ]; then
  echo "RESOURCES OK"
  echo "  strings: $(echo "$have" | grep -c .)   code mein use: $(echo "$used" | grep -c .)"
fi
exit $rc
