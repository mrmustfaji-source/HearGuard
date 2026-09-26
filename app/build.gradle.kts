/*
 * Yahan Kotlin plugin jaan-boojh kar NAHI hai.
 *
 * AGP 9 ka apna built-in Kotlin support hota hai: AGP khud
 * org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 par depend karta hai
 * (uske POM mein likha hai) aur use apply kar deta hai. Wahi `kotlin`
 * extension register karta hai.
 *
 * Isliye `id("org.jetbrains.kotlin.android")` alag se lagane par build
 * yahin ruk jaati thi:
 *
 *     Failed to apply plugin 'org.jetbrains.kotlin.android'.
 *     > Cannot add extension with name 'kotlin', as there is an extension
 *       already registered with that name.
 *
 * Pehle ye takkar `android.builtInKotlin=false` se dabai gayi thi - yaani
 * AGP ka built-in Kotlin band kar ke. Wo flag deprecated hai aur AGP 10
 * mein hat jayega, isliye ab ulta kiya gaya hai: flag hata diya aur plugin
 * ki line hata di. Version bhi wahi 2.2.10 rehta hai, to kuch badla nahi.
 */
plugins {
    id("com.android.application")
}

android {
    namespace = "com.mustfa.heatguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mustfa.heatguard"
        // 30 = Android 11. getThermalHeadroom() yahin se aata hai, to iske
        // neeche jaane par version-guards lagane padte - aur is phone par
        // (Android 17) uski zaroorat hi nahi.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        // AIDL chahiye: Shizuku ke process se baat karne ka interface
        // (IShellService.aidl) isi se generate hota hai.
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/*
 * Kotlin ka jvmTarget naye tareeke se.
 *
 * Pehle `android { kotlinOptions { jvmTarget = "17" } }` tha. Wo purana DSL
 * hai aur AGP 9 use deprecated bata kar warning deta hai; AGP 10 mein wo
 * chalega hi nahi. Naya form `kotlin { }` block hai, jo `android { }` ke
 * BAHAR aata hai - andar rakhne par Gradle use pehchanta nahi.
 */
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Jaan-boojh kar koi dependency nahi.
//
// Ye app sirf battery aur thermal padhti hai aur ek notification bhejti hai -
// ye sab platform API hain. Koi library na hone ka matlab: build kabhi kisi
// missing download par nahi rukegi, aur APK chhoti si rahegi.
dependencies {
    // Shizuku: app ko `shell` (adb) ke adhikaar deta hai, bina root ke.
    //
    // Iske bina app deep sleep nahi laga sakti aur kisi app ko band nahi kar
    // sakti - wo adhikaar (CHANGE_APP_IDLE_STATE, FORCE_STOP_PACKAGES)
    // Android kisi bhi normal app ko deta hi nahi. Shizuku na ho to app
    // chalti rehti hai, bas ye do kaam nahi kar paati.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
