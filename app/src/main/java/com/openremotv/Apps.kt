package com.openremotv

import android.content.Context
import org.json.JSONArray

/** One app on the TV. */
data class TvApp(
    val pkg: String,
    val enabled: Boolean,
    val system: Boolean,
    /** Opens from the TV's home screen; the activity to start, or null. */
    val launch: String?,
) {
    val name: String get() = Apps.label(pkg)
    val protected: Boolean get() = Apps.isProtected(pkg)
}

/**
 * Reading the TV's apps, opening them, and turning them off and on the safe way: "disable for this user", never
 * uninstall, so everything comes back with one switch. What the TV needs to work can't be switched off here at
 * all. Every change goes into a log on the phone (and a copy on the TV), which "Undo everything" replays backwards.
 */
class Apps(private val context: Context, private val tv: Tv) {

    private val prefs = context.getSharedPreferences("tidy", Context.MODE_PRIVATE)

    suspend fun list(): List<TvApp> {
        val all = packages(tv.shell("pm list packages"))
        val system = packages(tv.shell("pm list packages -s")).toSet()
        val disabled = packages(tv.shell("pm list packages -d")).toSet()
        val launch = launchable()
        return all.map { TvApp(it, it !in disabled, it in system, launch[it]) }
            .sortedWith(compareBy({ it.launch == null }, { it.name.lowercase() }))
    }

    /** Package to "pkg/activity" for everything on the TV's home screen (Leanback launcher activities). */
    suspend fun launchable(): Map<String, String> {
        val out = tv.shell(
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"
        )
        return out.lines().map { it.trim() }
            .filter { it.contains('/') && !it.contains(' ') }
            .associateBy { it.substringBefore('/') }
    }

    suspend fun open(app: TvApp) {
        val component = app.launch ?: return
        tv.shell("am start -n " + Tv.shellQuote(component))
    }

    suspend fun setEnabled(pkg: String, on: Boolean): String {
        require(on || !isProtected(pkg)) { "$pkg is needed by the TV" }
        tv.setAppEnabled(pkg, on)
        record(pkg, on)
        return "ok"
    }

    /** Turns back on everything this app ever turned off, newest first, and clears the log. */
    suspend fun undoAll(): Int {
        val off = changes().filter { !it.second }.map { it.first }.distinct()
        for (pkg in off) tv.setAppEnabled(pkg, true)
        prefs.edit().remove("log").apply()
        tv.shell("echo \"$(date) undo all\" >> /sdcard/OpenRemoTV/changes.txt")
        return off.size
    }

    /** What should stay off: the TV's first cleanup (2026-10-24) and everything switched off here since. */
    fun meantToBeOff(): Set<String> {
        val first = try {
            context.assets.open("cleanup-2026-10-24.txt").bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (e: Exception) {
            emptyList()
        }
        val state = LinkedHashMap<String, Boolean>()
        first.forEach { state[it] = false }
        changes().forEach { (pkg, on) -> state[pkg] = on }
        return state.filterValues { !it }.keys
    }

    private fun changes(): List<Pair<String, Boolean>> {
        val arr = JSONArray(prefs.getString("log", "[]"))
        return (0 until arr.length()).map { arr.getJSONArray(it).let { e -> e.getString(0) to e.getBoolean(1) } }
    }

    private suspend fun record(pkg: String, on: Boolean) {
        val arr = JSONArray(prefs.getString("log", "[]"))
        arr.put(JSONArray().put(pkg).put(on))
        prefs.edit().putString("log", arr.toString()).apply()
        tv.shell("mkdir -p /sdcard/OpenRemoTV; echo \"$(date) ${if (on) "on " else "off"} $pkg\" >> /sdcard/OpenRemoTV/changes.txt")
    }

    companion object {
        private fun packages(out: String) =
            out.lines().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }

        /**
         * What the TV needs: Android itself, Google's core, settings, the keyboards, the stores, the remote and
         * pairing services, the TV inputs, and this TV's home screen. These never get a switch.
         */
        private val PROTECTED_PREFIXES = listOf(
            "android", "com.android.", "com.google.android.gms", "com.google.android.gsf",
            "com.google.android.ext.", "com.google.android.packageinstaller", "com.google.android.permissioncontroller",
            "com.google.android.overlay", "com.google.android.webview", "com.google.android.tv.remote",
            "com.google.android.tvsetup", "com.google.android.katniss", "com.google.android.inputmethod.latin",
            "com.google.android.leanback.ime", "com.google.android.tv.frameworkpackagestubs",
            "com.google.android.apps.mediashell", "com.android.vending", "com.droidlogic.", "com.amlogic.",
            "com.openremotv.home", "com.google.android.backdrop",
        )

        fun isProtected(pkg: String): Boolean {
            if (pkg == "com.google.android.tvlauncher" || pkg.startsWith("com.google.android.leanbacklauncher")) {
                return false   // the old home screens: the new one replaced them
            }
            if (pkg.startsWith("com.android.providers.")) {
                return !(pkg.endsWith("calendar") || pkg.endsWith("contacts") || pkg.endsWith("userdictionary") ||
                        pkg.endsWith("blockednumber"))
            }
            return PROTECTED_PREFIXES.any { pkg == it || pkg.startsWith(it) }
        }

        private val NAMES = mapOf(
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "in.startv.hotstar" to "JioHotstar",
            "com.sonyliv" to "SonyLIV",
            "com.graymatrix.did" to "ZEE5",
            "com.google.android.tv" to "Live Channels",
            "by.stari4ek.tvirl" to "TVirl",
            "com.android.vending" to "Play Store",
            "com.android.tv.settings" to "Settings",
            "com.droidlogic.tv.settings" to "TV settings",
            "com.openremotv.home" to "Home screen",
            "com.google.android.tvlauncher" to "Old Google TV home",
            "com.google.android.leanbacklauncher" to "Old Android TV home",
            "com.google.android.music" to "Play Music",
            "com.google.android.videos" to "Play Movies",
            "com.google.android.play.games" to "Play Games",
            "com.jio.web.androidtv" to "JioSphere browser",
            "com.google.android.marvin.talkback" to "TalkBack",
            "com.google.android.katniss" to "Google Assistant",
            "com.google.android.apps.mediashell" to "Chromecast built-in",
            "com.netflix.ninja" to "Netflix",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
        )

        /** A readable name: a known one, or the package's last words made into a title. */
        fun label(pkg: String): String = NAMES[pkg] ?: pkg.split('.')
            .filter { it !in setOf("com", "org", "in", "tv", "android", "google", "app", "apps", "net") }
            .takeLast(2).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            .ifEmpty { pkg }
    }
}
