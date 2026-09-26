import com.mustfa.heatguard.*

/** Asli scenarios, 25 Sep ko isi phone par naape gaye numbers se. */
fun main() {
    var pass = 0; var fail = 0
    fun check(name: String, got: Boolean, want: Boolean, extra: String = "") {
        if (got == want) { pass++; println("  PASS  $name $extra") }
        else { fail++; println("  FAIL  $name -> mila=$got chahiye=$want $extra") }
    }
    val H = 3_600_000L
    fun v(t: Long, temp: Float, lvl: Int, chg: Boolean = false, screen: Boolean = false) =
        Vitals(t, temp, lvl, chg, screen, Float.NaN)

    println("1) Asli kharab haal: battery 41.4 C, charging nahi")
    val hot1 = Rules.judge(v(H, 41.4f, 30), v(0, 41.2f, 32), 0)
    check("pehli garam reading par chup rehta hai", hot1.alert, false)
    check("par ise garam gina", hot1.hot, true)
    val hot2 = Rules.judge(v(2*H, 41.4f, 28), v(H, 41.4f, 30), 1)
    check("doosri lagataar par bolta hai", hot2.alert, true, "-> ${hot2.reason.take(40)}")

    println("2) Theek hone ke baad: 37.6 C")
    val ok = Rules.judge(v(3*H, 37.6f, 25), v(2*H, 38.9f, 27), 2)
    check("thanda phone par chup", ok.alert, false)
    check("streak toot gaya", ok.hot, false)

    println("3) Charging par 42 C (normal garmi)")
    val chg = Rules.judge(v(H, 42.0f, 50, chg = true), v(0, 41.0f, 45, chg = true), 5)
    check("charging par jhootha alarm nahi", chg.alert, false)

    println("4) Charging par 45 C (ye zyada hai)")
    val chgHot = Rules.judge(v(H, 45.0f, 50, chg = true), v(0, 44.5f, 48, chg = true), 1)
    check("charging par bhi 45 par bolta hai", chgHot.alert, true)

    println("5) Screen band, battery 12%/ghanta gir rahi")
    val drain = Rules.judge(v(H, 38.0f, 38), v(0, 38.0f, 50), 0)
    check("tez drain par bolta hai", drain.alert, true, "-> ${drain.drainPerHour.toInt()}%/hr")

    println("6) Screen ON thi aur drain tez (user khud chala raha hai)")
    val used = Rules.judge(v(H, 38.0f, 38, screen = true), v(0, 38.0f, 50, screen = true), 0)
    check("user ke chalane par ilzaam nahi", used.alert, false)

    println("7) Bohot kam antaraal (2 minute) - rate bharosemand nahi")
    val quick = Rules.judge(v(120_000, 38.0f, 49), v(0, 38.0f, 50), 0)
    check("chhote antaraal par chup", quick.alert, false)

    println("8) Battery charge ho kar badh gayi")
    val up = Rules.judge(v(H, 38.0f, 60), v(0, 38.0f, 50), 0)
    check("badhne par drain 0", up.drainPerHour == 0f, true)


    println("9) CPU: launcher jaisa runaway (25% = 2 core), screen band")
    fun cpu(pkg: String, pct: Float) =
        CpuSnapshot(true, listOf(ProcCpu(10100, pkg, pct)))
    val r1 = Rules.judge(v(H, 39f, 40), v(0, 39f, 41), 0, cpu("com.miui.home", 25f), 0)
    check("pehli baar chup", r1.alert, false)
    val r2 = Rules.judge(v(2*H, 39f, 38), v(H, 39f, 40), 0, cpu("com.miui.home", 25f), 1)
    check("doosri baar naam ke saath bolta hai", r2.alert, true, "-> ${r2.culprit}")
    check("culprit sahi", r2.culprit == "com.miui.home", true)

    println("10) CPU tez par screen ON aur phone thanda (user khud chala raha hai)")
    val game = Rules.judge(v(H, 36f, 40, screen = true), v(0, 36f, 45, screen = true),
                           0, cpu("com.game.app", 40f), 1)
    check("game khelne par ilzaam nahi", game.alert, false)

    println("11) Wahi CPU tez par phone bhi garam - ab ilzaam banta hai")
    val gameHot = Rules.judge(v(H, 41.5f, 40, screen = true), v(0, 41f, 45, screen = true),
                              0, cpu("com.game.app", 40f), 1)
    check("garam ho to screen ON par bhi bolta hai", gameHot.alert, true)

    println("12) CPU thoda hi hai (10%) - shor nahi")
    val mild = Rules.judge(v(H, 39f, 40), v(0, 39f, 41), 0, cpu("com.x", 10f), 5)
    check("halke CPU par chup", mild.alert, false)

    println("13) DUMP permission nahi (cpu available=false) - purane niyam chalte hain")
    val noDump = Rules.judge(v(2*H, 41.4f, 28), v(H, 41.4f, 30), 1,
                             CpuSnapshot(false, emptyList()), 0)
    check("bina DUMP ke bhi garmi pakadta hai", noDump.alert, true)
    println()
    println("PASS=$pass  FAIL=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
