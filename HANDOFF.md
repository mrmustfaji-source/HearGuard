# Heat Guard - kya ho chuka, kya baaki hai

Ye file agle session ke liye hai. Ismein wo sab likha hai jo is project ke
baare mein jaanna zaroori hai — khaaskar wo cheezein jo **naap kar** pata
chali hain, taki koi unhe dobara na dhoonde.

**Project:** `D:\laptop all data\ALL ANDROID PROJECT\HeatGuard`
**Package:** `com.mustfa.heatguard`
**Phone:** Redmi/POCO `25113PN0EI`, Android 17 / HyperOS V816
**Doosra phone:** Samsung Galaxy S25

---

## 1. Ye app kyun bani

25 Sep 2026: phone 3 din se garam tha, battery udd rahi thi. Wajah nikli MIUI
ka apna launcher `com.miui.home`, jo launcher badalne ke baad ek loop mein
atak gaya:

| | |
|---|---|
| Launcher ne CPU khaya | 3 din 19 ghante (sirf 7 din chal ke) |
| Poore `system_server` ne | 11 ghante (11 din mein) |
| CPU temperature | 99.6 C |
| Battery temperature | 41.4 C |
| `dumpsys battery` CPU attribution | 7180 mAh — battery hi 6248 mAh ki hai |

`am force-stop com.miui.home` se CPU 99.6 C se 44 C par aa gaya.

**Asli nuksaan 3 din tak pata na chalna tha.** App ka maqsad wahi hai.

User aksar launcher badalta hai, to ye dobara ho sakta hai.

---

## 2. Jo ban chuka hai aur verify ho chuka hai

Sab kuch compile-clean hai: **0 errors, 0 warnings, 35 classes, 18/18 tests,
11 XML valid, koi non-ASCII nahi.**

### 2.1 Garmi/battery ka pehra (poora)

| File | Kaam |
|---|---|
| `Vitals.kt` | battery temp/level/charging/screen + thermal headroom |
| `Rules.kt` | kab bolna hai — 18 tests iske liye hain |
| `Store.kt` | SharedPreferences: samples, streaks, history, CPU sample |
| `Scheduler.kt` | AlarmManager, har 15 minute |
| `CheckReceiver.kt` | wahi 15-minute check + remote polling |
| `BootReceiver.kt` | reboot ke baad dobara chalu |
| `Notify.kt` | chetavni + uske buttons |
| `MainActivity.kt` | pehli screen |

**Thresholds** (asli naape hue numbers se, `Rules.kt` mein):

| Haalat | Lakeer |
|---|---|
| Ek app ka CPU | 18% (100% = saare 8 core), lagataar 2 readings |
| Garmi, charging nahi | 41 C, lagataar 2 readings |
| Garmi, charging par | 44 C |
| Screen band par drain | 10% prati ghanta |
| Chetavni ke baad chup | 2 ghante |

Is phone ka aaram ka temperature 37-39 C naapa gaya, gadbad 41.4 C par thi.

**Koi foreground service nahi** — jaan-boojh kar. Battery bachane wali app
khud battery khaye to bekaar hai.

### 2.2 Culprit ka naam (`CpuInspector.kt`)

`dumpsys batterystats` se per-uid `Total cpu time` padhta hai, pichhle sample
se antar leta hai, aur uid → package `PackageManager.getPackagesForUid()` se.

### 2.3 Deep sleep (`DeepSleepActivity.kt`, `Buckets.kt`, `Keep.kt`)

Samsung jaisa, **kaam karne wala** (25 Sep ko dobara likha gaya — pehla
version sirf list dikhata tha, jo bekaar tha):

- Pehli screen: teen row ginti ke saath — Deep sleeping / Sleeping / Never
- Tap: saari apps + tick box, jo pehle se us bucket mein hain unki tick lagi
- Upar do button: **SAB** aur **DONE**
- DONE: tick wali us bucket mein, **tick hatai gayi wapas `active`**

Bucket mapping: Deep sleeping = `restricted` (45), Sleeping = `rare` (40),
Never = `active` (10).

`Keep.kt` mein wo apps hain jinhe bulk button kabhi nahi sulata (call, SMS,
WhatsApp, Telegram, alarm, calendar, bank/UPI/GPay, 2FA).

### 2.4 Shizuku (`Shell.kt`, `ShellService.kt`, `IShellService.aidl`)

`bindUserService` se ek service `shell` (uid 2000) ke process mein chalti hai.
Usi se `am set-standby-bucket`, `am force-stop`, `am start` chalte hain.

### 2.5 Remote control — do raaste

| | `Remote.kt` (Telegram) | `Link.kt` (app-to-app) |
|---|---|---|
| Doosre phone par app chahiye | nahi | haan |
| Relay | Telegram Bot API | ntfy.sh |
| UI | Telegram app khud | `LinkActivity` ke buttons |

**Dono ek hi engine use karte hain: `Remote.execute()`.** Naya command wahan
jodne se dono mein aa jata hai.

Commands: `/status /top /apps /open /stop /sleep /wake /sh /help`

**Telegram control link (26 Sep).** User ne maanga: "app mein ek button ho,
link Telegram par jaye, doosre phone se link par tap karne se poora control
mil jaye." Ab aise chalta hai:

1. `RemoteActivity` ka sabse upar wala button -> `Remote.newInvite()` ->
   `getMe` se bot ka @naam, phir `https://t.me/<bot>?start=<14-akshar code>`.
2. Link seedha Telegram mein share hoti hai (`org.telegram.messenger` wagairah,
   na mile to aam share sheet) aur clipboard mein bhi.
3. Doosre phone par tap -> bot chat -> START -> bot ko `/start <code>` jata
   hai -> `poll()` code milata hai -> wahi chat malik, code mita diya jata hai.
4. Jawab ke saath **reply keyboard** jata hai (Status, CPU, Back, Home,
   Recents, Vol +/-, Apps, Sleep all, Screen on/off, Settings, Help).

Teen baatein jo aasani se toot sakti hain:

- **Keyboard ka label hi bheja gaya text hai.** Isliye `alias()` mein har
  label ka command hona chahiye. `tools/remote-test.sh` ye check karta hai
  (commands ki list `Remote.kt` se khud nikalta hai). Negative test kiya:
  bina alias wala "Torch" button jodne par FAIL=1 aaya.
- Code ek baar ka hai, 30 minute mein marta hai. Nayi link ka code sahi ho
  to wo **pehle se jude malik ko bhi badal deta hai** - jaan-boojh kar, kyunki
  link sirf app ka button dabane se banti hai.
- Kabhi link na bani ho to purana "pehla message wala malik" tarika abhi bhi
  chalta hai, taaki pehle se chal raha setup na toote.

`ListenService` ab Telegram ko bhi har 3 second poochhta hai jab
`Remote.isPaired()` ho (warna command 15 minute tak pada rehta). Uska "Control
band" button sirf tez sunna band karta hai, Telegram remote ko nahi.

`LinkActivity` 25 Sep ko dobara likha gaya, kyunki user isi par atak gaya tha
(screen sirf khaali "Pairing code" box dikhati thi). Ab role hamesha upar
dikhta hai, "Role badlo" ka button hai, aur 1-2-3-4 karke kadam likhe hain.
Pairing code 32 se **12 akshar** kar diya gaya.

---

## 3. NAAP KAR pata chali baatein — inhe dobara mat dhoondhna

Ye sab is phone par khud chala kar dekha gaya hai. Documentation se nahi liya.

### 3.1 App kya NAHI kar sakti

```
$ run-as com.mustfa.heatguard cat /proc/<pid>/stat
cat: Permission denied                  <- doosri app ka CPU nahi padh sakte

$ run-as com.mustfa.heatguard dumpsys cpuinfo
Can't find service: cpuinfo             <- DUMP milne ke BAAD bhi

$ pm grant com.mustfa.heatguard android.permission.CHANGE_APP_IDLE_STATE
SecurityException: is not a changeable permission type

$ run-as com.mustfa.heatguard am set-standby-bucket <pkg> restricted
SecurityException: Access denied, requires CHANGE_APP_IDLE_STATE
```

`FORCE_STOP_PACKAGES` aur `CHANGE_APP_IDLE_STATE` dono `signature|privileged`
hain — adb se bhi grant nahi hoti. **Shizuku hi ek rasta hai.**

### 3.2 Permissions ka protection level (isi phone par)

| Permission | Level | Mil sakti? |
|---|---|---|
| `DUMP` | `signature\|privileged\|development` | **adb se, ek baar** |
| `PACKAGE_USAGE_STATS` | `...development\|appop` | user khud Settings se |
| `KILL_BACKGROUND_PROCESSES` | `normal` | apne aap |
| `FORCE_STOP_PACKAGES` | `signature\|privileged` | **kabhi nahi** |
| `CHANGE_APP_IDLE_STATE` | `signature\|privileged` | **kabhi nahi** |
| `WRITE_SECURE_SETTINGS` | `...development...` | adb se (use nahi hui) |

### 3.3 Kaunse dumpsys app se chalte hain

**DUMP + usage access dono milne ke baad:**

| Service | Chalta hai? |
|---|---|
| `batterystats` | HAAN — per-uid CPU yahin se aata hai |
| `procstats` | HAAN — par sirf memory, CPU nahi |
| `usagestats` | HAAN — standby buckets yahin se |
| `power`, `thermalservice` | HAAN |
| `cpuinfo`, `battery`, `meminfo` | **NAHI** — "Can't find service" |

`dumpsys batterystats --checkin` (compact CSV) ko `INTERACT_ACROSS_USERS`
chahiye, jo app ko nahi milti. Isliye poora dump lekar phone par hi `grep`
se filter kiya jata hai (~1 second lagta hai, 15 minute mein ek baar).

### 3.4 Paimane ka farak

`dumpsys cpuinfo` / `batterystats` mein **100% = saare 8 core**. `top` mein
ek core = 100%. Launcher `top` par 200% tha = yahan **25%**. `Rules.kt` ke
threshold is (dumpsys wale) paimane par hain.

### 3.5 Shizuku API

- **`Shizuku.newProcess()` 13.1.5 mein hai hi nahi** — hata diya gaya. Sahi
  rasta `bindUserService` hai.
- Builder mein **`processNameSuffix()`** hai, `processName()` nahi.
- AIDL mein `destroy()` ko id deni hi padti hai (`= 16777114`), aur AIDL ka
  niyam hai "sab ko id do ya kisi ko nahi" — to `exec()` ko bhi (`= 1`).

### 3.6 Build setup

- **AGP 9 khud `kotlin-gradle-plugin:2.2.10` laata hai** (AGP ke POM mein).
  `id("org.jetbrains.kotlin.android")` alag se lagane par build rukti hai:
  *"Cannot add extension with name 'kotlin'"*. Isliye wo line nahi hai.
- `gradle.properties` mein ab sirf 3 lines hain. RakshaPDF se copy kiye 10
  legacy flags hata diye gaye — sab deprecated the aur AGP 10 mein hat
  jayenge.
- `kotlinOptions { }` purana DSL hai; `kotlin { compilerOptions { } }` use
  hota hai, jo `android { }` ke **bahar** aata hai.

---

### 3.7 Ek feature chup-chaap mar chuka hai - dobara mat hone dena

Ek doosre AI ne MainActivity ko remote-control pad bana diya, aur us chakkar
mein garmi/CPU wali poori screen gayab ho gayi.

Nuksaan sirf dikhne ka nahi tha. `Store.setEnabled()` ko bulane wala EKLAUTA
button usi screen par tha. Uske bina `CheckReceiver` ki pehli line:

    if (!Store.isEnabled(context)) return

hamesha sach hoti thi - yaani har 15 minute ka check, garmi ki chetavni, aur
CPU khane wali app ka naam, sab band pada tha. Jabki app bani hi iske liye
thi. Compile bilkul clean tha; aisi cheez compiler kabhi nahi pakadta.

`RemoteActivity` (Telegram) bhi manifest mein maujood thi par usse kholne ka
koi rasta nahi bacha tha - menu aur layout dono se hat gaya tha.

Isliye ab wo screen `GuardActivity.kt` mein ALAG hai, MainActivity ke andar
nahi. Menu (Heat & CPU) aur MainActivity ke button, dono se khulti hai.

**Screen ya button hatane se pehle ye chala lena:**

    grep -rn "Store.setEnabled" app/src/main/java/ | grep -v Store.kt
    grep -rn "GuardActivity::class\|RemoteActivity::class" app/src/main/java/

Agar kisi zaroori kaam ko bulane wali sirf EK jagah bachi ho, to wo screen
hatai nahi ja sakti.

---

## 4. Verify kaise karein (Gradle ke bina)

Claude ke sandbox mein **Gradle chalta hi nahi** (`Unable to establish
loopback connection`). Isliye ye teen check hain:

```bash
# 1. AIDL - asli aidl.exe se (build rukne wali galti yahi pakadti hai)
powershell -File tools\check-aidl.ps1

# 2. Resources - duplicate ya gayab string (build rukne wali galti)
bash tools/check-res.sh

# 2. Rules ki logic - bina phone, bina emulator
bash tools/run-tests.sh          # 18 tests

# 3. Kotlin type-check - poora project, asli Shizuku jar aur AIDL ke saath
bash tools/check-kotlin.sh

# 4. Telegram buttons + control link ka regex (check-kotlin ke baad)
bash tools/remote-test.sh        # 24 tests
```

`check-aidl.ps1` ko jaan-boojh kar **galat AIDL** de kar test kiya gaya tha —
usne error pakda (exit=1). Ye check jhooth nahi bolta.

`check-res.sh` ko bhi dono tarah se test kiya gaya (duplicate daal kar, aur
string hata kar) - dono baar exit=1 aaya.

**Ek zaroori sabak:** R.java ka stub `sort -u` se banta hai, aur `-u`
duplicate ko chup-chaap gira deta tha - isi wajah se ek duplicate string
Kotlin check se nikal kar seedha build mein pakdi gayi. Jo cheez check ko
saaf banati hai, wahi galti chhupa sakti hai.

**Build hamesha user karega.**

---

## 5. Kya BAAKI hai

### 5.1 Screen dekhna aur chalana (likh chuka, phone par abhi nahi chalaya)

`ScreenService` (jise control kiya ja raha hai) aur `ScreenActivity` (jo
control karta hai). Shizuku se `screencap` PNG `/data/local/tmp` par, shell
process khud JPEG (chaudaai 480) bana kar `byte[]` lautata hai - app wo path
padh nahi sakti, isliye file app tak nahi aati. Tap/swipe `input`, keys sirf
Back/Home/Recents.

Session: Redmi par button se chalu, notification, 10 minute baad khud band.
Samsung par "Screen dekho aur chalao".

**ntfy.sh ki limit (source `config.go` se, is phone par nahi naapi):** burst
60, phir 1 request / 5 second. Isliye tasveer har ~10 second. Video nahi.

**Encryption:** topic = SHA-256(code) ka tukda, key alag hash (`LinkCrypto`).
Relay ko code nahi dikhta. Purana topic (seedha code) ab use nahi hota -
dono phone par naya build lagao, code dubara daalne ki zaroorat nahi.

### 5.2 Chhoti cheezein

- `Remote.kt` ka `/sleep` abhi `Buckets` + `Keep` use karta hai; naye
  `DeepSleepActivity` jaisa "tick hatao = wapas active" wala behaviour usme
  nahi hai.
- `MainActivity` ki screen ab kaafi lambi ho gayi hai (bahut saare button).
- `RemoteActivity` (Telegram) aur `LinkActivity` (app-to-app) do alag screens
  hain. User ne "internet se, kahin se bhi" chuna hai — shayad Telegram wala
  hata kar sirf `Link` rakhna behtar ho.

### 5.3 Jo kabhi nahi ho payega (user ko bata diya gaya hai)

- App khud kisi app ko force-stop ya deep sleep nahi kar sakti — Shizuku ke
  bina. Ye Android ki rok hai, code ki kami nahi.
- Ek button se saari apps deep sleep — sirf Shizuku ke saath.

---

## 6. Phone se judne ka tarika

```bash
# IP badalti rehti hai - .phone-ip.txt mein latest hai
adb connect <ip>:5555

# Naya setup (USB laga kar):
adb tcpip 5555
adb shell ip route get 1.1.1.1      # IP nikalne ke liye
```

Aakhri IP: `10.113.129.217` (badal sakti hai).

**Laptop se phone sambhalne ka tool:** `..\phone-care.ps1`
(`-Fix`, `-Watch -Auto`, `-Notifications`, `-MuteAll`, `-DeepSleepUnused`,
`-SleepStatus`). Usme MIUI ke do jaal likhe hain (appops `--uid`, PowerShell
quoting).

### Kotlin type-check ka poora command

```bash
cd "D:/laptop all data/ALL ANDROID PROJECT/HeatGuard"
# R ka stub banao (strings.xml + layout se), phir:
java -jar "/c/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib/kotlin-compiler.jar" "@argfile"
# classpath mein: android.jar ; R stub ; aidl-generated classes ; shizuku classes.jar
```

Shizuku ka jar: `dev.rikka.shizuku:api:13.1.5` ka AAR khol kar `classes.jar`.

---

## 7. Build aur install

```bash
cd "D:\laptop all data\ALL ANDROID PROJECT\HeatGuard"
gradlew --stop
gradlew clean assembleDebug
```

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell pm grant com.mustfa.heatguard android.permission.DUMP
```

`DUMP` ek hi baar chahiye, reboot ke baad bhi rehti hai. Usage access user
khud app ke button se deta hai.

Shizuku: install kijiye → "Start via Wireless debugging" (phone par wireless
debugging pehle se ON hai, laptop ki zaroorat nahi) → app mein "Shizuku se
ijazat maango".

---

## 8. User ke baare mein

- Jawab **poora Hinglish** mein chahiye.
- Andaza laga kar code mat do — is project mein 4 baar andaza galat nikla
  (`newProcess`, `processName`, AIDL ids, `builtInKotlin`). Har baar jar/POM
  khol kar dekhne se sahi jawab mila.
- Aadha kaam karne wali cheez se user ko chidh hoti hai (deep sleep ka pehla
  version, Link ki pairing screen). Ek cheez poori karna do adhoori se behtar.
