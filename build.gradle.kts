/*
 * Sirf AGP.
 *
 * Kotlin plugin yahan nahi hai kyunki AGP 9 use khud lekar aata hai
 * (kotlin-gradle-plugin 2.2.10, AGP ke POM mein) aur khud apply karta hai.
 * Use dobara declare karne par app ki build "Cannot add extension with name
 * 'kotlin'" bol kar ruk jaati hai - app/build.gradle.kts mein poora kissa
 * likha hai.
 *
 * Version RakshaPDF wala hi hai, jo is machine ke Gradle cache mein maujood
 * hai - to build bina internet ke bhi chal jati hai.
 */
plugins {
    id("com.android.application") version "9.3.2" apply false
}
