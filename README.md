# Heat Guard

Ek chhoti si Android app jo aapke phone ki garmi aur battery-drain par nazar
rakhti hai, aur gadbad hote hi bata deti hai.

## Ye kyun bani

25 Sep 2026: phone 3 din se garam tha aur battery udd rahi thi. Wajah nikli
MIUI ka apna launcher, `com.miui.home`, jo launcher badalne ke baad background
mein ek loop mein atak gaya tha:

| | |
|---|---|
| Launcher ne CPU khaya | 3 din 19 ghante (7 din chal ke) |
| Poore Android system_server ne | 11 ghante (11 din mein) |
| CPU temperature | 99.6 C |
| Battery temperature | 41.4 C |
| `dumpsys battery` ka CPU attribution | 7180 mAh — jabki battery hi 6248 mAh ki hai |

Launcher band karte hi CPU 99.6 C se 44 C par aa gaya.

**Asli nuksaan 3 din tak pata na chalna tha.** Ye app wahi theek karti hai.

## Do mode — aur poora mode kaise chalu hota hai

### Simple mode (bina kisi setup ke)

Battery ka temperature, battery level se nikla drain rate, aur thermal
headroom. Ye teeno bina kisi permission ke milte hain. Gadbad par notification
+ Battery usage screen ka button.

### Poora mode — **app ka NAAM bhi bata deti hai**

Ek baar laptop se ye chala dijiye:

```
adb shell pm grant com.mustfa.heatguard android.permission.DUMP
```

Bas ek hi baar. **Reboot ke baad bhi rehti hai.**

Uske baad app `dumpsys cpuinfo` padh sakti hai, aur notification seedha ye
kehti hai: *"com.miui.home phone garam kar rahi hai (25%)"* — aur uska button
aapko seedha us app ke page par le jaata hai jahan **Force stop** ka button
hota hai. Do tap mein kaam khatam.

### Ye kyun itna ghuma-phira kar

Manifest mein `DUMP` likh dene bhar se wo milti nahi. Uska protection level
is phone par `signature|privileged|**development**` hai — us `development`
ki wajah se `adb shell pm grant` de sakta hai, aur koi tareeka nahi.

Seedha `/proc` padhne ka raasta band hai:

```
$ run-as <app> head -1 /proc/stat
head: /proc/stat: Permission denied
```

Lekin `dumpsys` ka darwaza khula hai, bas taala `DUMP` ka hai:

```
$ run-as <app> dumpsys batterystats
Permission Denial: ... due to missing android.permission.DUMP permission
```

Wahi taala kholna hai. (`BatteryStatsManager` ka seedha API bhi dekha tha —
wo public SDK mein hai hi nahi, `javap` se check kiya.)

### Shizuku ke saath: sab kuch ek tap mein

Shizuku ek free app hai jo aapki app ko `shell` (adb) ke adhikaar de deti
hai, bina root ke. Uske chalte hi Heat Guard ye kar leti hai:

- **Culprit app ko sach mein band karna** — notification par "Abhi band karo"
- **Samsung jaisa deep sleep** — ek button se saari unused apps
- Wapas jagana bhi ek button se

Kaise chalu karein (laptop ki zaroorat nahi, aapke phone par wireless
debugging pehle se ON hai):

1. Shizuku install kijiye (Play Store / GitHub)
2. Usme "Start via Wireless debugging" chuniye, wo khud pair kar lega
3. Heat Guard → "Deep sleep / background limits" → "Shizuku se ijazat maango"

**Reboot ke baad Shizuku dobara start karna padta hai** (step 2). Bhool gaye
to app chalti rahegi, bas ye do kaam nahi kar paayegi — aur screen aapko saaf
bata degi ki Shizuku band hai.

Deep sleep ka bulk button in apps ko **kabhi nahi** sulata: call, SMS,
WhatsApp, Telegram, alarm, calendar, bank/UPI/Google Pay, 2FA authenticator.
Wo so gayin to OTP aur alarm ruk jayenge. (`Keep.kt` mein list hai.)

### Shizuku ke bina jo nahi ho sakta

App khud **force-stop nahi kar sakti**. Uske liye `FORCE_STOP_PACKAGES`
chahiye, jiska level `signature|privileged` hai — wo adb se bhi grant nahi
hoti, sirf firmware ke saath sign hui apps ko milti hai. Isiliye app naam
bata kar aapko seedha us button tak pahuncha deti hai.

**Deep sleep** (Samsung jaisa) bhi app ke bas ka nahi:
`CHANGE_APP_IDLE_STATE` ka level bhi `signature|privileged` hai. Wo kaam
`phone-care.ps1` (laptop wali script) karti hai — `-DeepSleepUnused`,
`-SleepStatus`, `-DeepSleep`, `-WakeUp`.

**3 din → 20-30 minute.** Yahi is app ka poora maqsad hai.

## Kab bolti hai

Ye numbers hawa se nahi liye — isi phone par naape gaye (aaram ka temperature
37-39 C nikla, gadbad 41.4 C par thi):

| Haalat | Lakeer |
|---|---|
| **Ek app ka CPU** (poora mode) | 18%, **lagataar do** readings par |
| Garmi, charging nahi | 41 C, **lagataar do** readings par |
| Garmi, charging par | 44 C (charging khud garmi deti hai) |
| Screen band par drain | 10% prati ghanta |
| Ek chetavni ke baad chup | 2 ghante ("1 ghante chup" button alag se) |

CPU ka 18% dhyan se chuna hai. `dumpsys cpuinfo` ka paimana `top` se alag
hai: wahan **100% = saare 8 core** (aakhri line `1.9% TOTAL` isi ka sabooot
hai). 25 Sep wala launcher `top` par 200% tha — yahan wo **25%** banta hai.
18% matlab lagbhag 1.5 core lagataar; koi normal app itna nahi khaati
(`system_server` bhi 2-5% par rehta hai).

Agar aap khud koi bhaari app chala rahe hain (game, video) aur phone thanda
hai, to CPU wali chetavni nahi aati — us par ilzaam lagana galat hoga. Phone
garam bhi ho ya screen band ho, tabhi bolti hai.

Screen ON rehne par drain wali chetavni nahi aati — tab aap khud phone chala
rahe hain, app ka kasoor nahi.

## Doosre phone se control (Samsung se Xiaomi)

Do tareeke hain, **dono ek saath chal sakte hain**. Dono ek hi command-engine
use karte hain (`Remote.execute`), isliye kaam dono jagah bilkul ek jaisa hota
hai — naya command jodte hi dono mein aa jata hai.

### 1. App dono phone mein ho (buttons wala)

App → **"Doosre phone se control karo"**

- Xiaomi par: *"Is phone ko control KIYA JAYEGA"* → ek pairing code dikhega
- Samsung par: *"Is phone se control KARUNGA"* → wahi code daal dijiye

Bas. Uske baad Samsung par buttons aa jate hain: Status, CPU, Saari apps,
Deep sleep, aur ek box jismein app ka naam likh kar **Kholo / Band karo**.

Beech mein **ntfy.sh** hai — ek free, khula HTTP relay. Na account, na server,
na Firebase project. Do phone internet par seedha nahi jud sakte (dono NAT ke
peechhe hote hain), isliye beech mein kuch na kuch chahiye hi.

### 2. App sirf Xiaomi mein ho (Telegram wala)

App → **"Samsung se control karo (Telegram)"**

@BotFather se ek bot banaiye, token app mein paste kijiye, bot ko ek message
bhejiye. Ab Samsung ke Telegram se hi command chalte hain — koi nayi app nahi.

### Password kabhi type nahi karna padta

Dono mein ek **one-time pairing** hai (code ya token), bilkul Bluetooth pair
karne ki tarah. Uske baad kabhi kuch nahi — button dabaiye, kaam ho jata hai.

Wo pairing hata nahi sakte: wahi ek cheez hai jo tay karti hai ki phone sirf
**aap** control kar sakein. Bina uske duniya ka koi bhi command bhej sakta.

### Commands

```
/status          battery, garmi, drain
/top             kaun CPU kha raha hai
/apps            saari apps ke naam
/apps <tukda>    dhoondho
/open <naam>     app kholo
/stop <naam>     app band karo
/sleep           saari unused apps deep sleep
/sleep <naam>    ek app deep sleep
/wake <naam>     wapas jagao
/sh <command>    koi bhi shell command
/help
```

`/open`, `/stop`, `/sleep`, `/wake` aur `/sh` **Shizuku ke saath hi** poori
tarah chalte hain. `/open` ke liye khaas taur par: Android 10 se koi app
background se doosri activity shuru nahi kar sakti, aur remote ka matlab hi
background hai — Shizuku ke `am start` par ye paabandi nahi hai.

### Der kitni lagegi

**Chetavni (phone → aap): turant.** Phone khud bhejta hai.

**Aapka command (aap → phone): 15 minute tak.** Phone jab jeb mein pada ho to
Android use bar-bar jagne nahi deta (Doze). Phone chalu ho to jawab turant.
Isse tez karne ka ek hi tareeka hai — ek permanent notification wali service —
jo is app ke maqsad ke khilaf hai.

### Kya dhyan rakhein

Command aur uska jawab relay ke server se guzarta hai (ntfy.sh ya Telegram),
**bina encryption ke**. Pairing code kisi ko na mile to koi padh nahi sakta,
par jo cheez aap kisi server par nahi bhejna chahte, wo `/sh` se mat nikaliye.

## Banane ka tarika

```
cd "D:\laptop all data\ALL ANDROID PROJECT\HeatGuard"
gradlew --stop
gradlew clean assembleDebug
```

APK yahan milegi: `app\build\outputs\apk\debug\app-debug.apk`

Phone par daalne ke liye (phone WiFi debugging par juda ho):

```
D:\sdk\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

`gradlew --stop` pehle isliye ki Gradle ka daemon purani file yaad rakhta hai
aur "BUILD SUCCESSFUL" bol kar purani APK de deta hai.

## Chalane ke baad

1. App kholiye, **"Pehra chalu karo"** dabaiye
2. Notification ki ijazat de dijiye (warna chetavni dikhegi hi nahi)
3. Poora mode chahiye to laptop se ek baar:
   `adb shell pm grant com.mustfa.heatguard android.permission.DUMP`
4. Bas. App band kar dijiye, wo peechhe se dekhti rahegi

App ki pehli screen khud bata deti hai ki kaun sa mode chal raha hai — poora
mode mein wo abhi ke top 5 CPU khaane wale bhi dikhati hai.

Reboot ke baad khud shuru ho jaati hai.

## Rules test karna

`Rules.kt` mein koi Android cheez nahi — sirf numbers ka hisaab. Isliye bina
phone, bina emulator, bina Gradle test ho jata hai:

```
bash tools/run-tests.sh
```

11 scenarios chalte hain, jisme 25 Sep wale asli numbers bhi shaamil hain.
Threshold badlein to ye chala lijiyega.

## Is app ke bare mein

- **Koi dependency nahi.** Sirf platform API. Isliye build kabhi kisi missing
  download par nahi rukegi, aur APK chhoti rehti hai.
- **Koi INTERNET permission nahi.** Kuch bhejti nahi, koi ad nahi, koi
  tracking nahi. Sab phone par hi.
- **Koi foreground service nahi.** Ek app jo garmi pakadne ke liye khud CPU
  khaye wo apne hi maqsad ke khilaf hai. `AlarmManager` ka inexact alarm
  system ke doosre wake-ups ke saath jud jata hai — phone iske liye alag se
  jagta bhi nahi.

## Agar isse zyada chahiye

Agar aap sach mein chahte hain ki app **naam bataye aur khud maar de**, to ek
hi rasta hai: **Shizuku**. Wo ek free app hai jo aapke `adb` wale adhikaar app
ko de deti hai. Uske saath ye app `top` bhi chala sakti hai aur `force-stop`
bhi.

Kimat ye hai ki **har reboot ke baad Shizuku ko ek baar shuru karna padta
hai** — aur bhool gaye to app chupchaap aadhi-adhoori chalti rahegi. Aapka
wireless debugging pehle se ON hai, to aapke liye ye mushkil nahi.

Chahiye to bol dijiyega, jod dunga.
