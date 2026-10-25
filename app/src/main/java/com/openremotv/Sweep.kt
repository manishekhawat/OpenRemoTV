package com.openremotv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One app the sweep offers: switched on means it will be turned off. */
data class SweepItem(val pkg: String, val sweep: Boolean) {
    val name: String get() = Apps.label(pkg)
}

/**
 * The sweep, the TidyTV way: back up first (always), offer the tested list's apps that are on this TV with a switch
 * each, turn them off one at a time (logged before each change), swap the home screen only if the new one really
 * takes Home, and put everything back with one tap. The first backup of each TV is kept forever as "original":
 * Undo goes back to it, however many sweeps came after.
 */
class Sweeper(private val context: Context, private val tv: Tv, private val apps: Apps) {

    private val dir = File(context.filesDir, "backups").apply { mkdirs() }

    private fun key(info: TvInfo) = (info.title + "-" + tv.host).lowercase().replace(Regex("[^a-z0-9]+"), "-")

    fun hasOriginal(info: TvInfo) = File(dir, "${key(info)}-original.json").exists()

    /** Saves the TV's apps, what is off, its home screen and animation speeds, on the phone and on the TV. */
    suspend fun backup(info: TvInfo): Int {
        val all = packages(tv.shell("pm list packages"))
        val off = packages(tv.shell("pm list packages -d"))
        val json = JSONObject()
            .put("tv", info.title).put("host", tv.host).put("android", info.android)
            .put("time", System.currentTimeMillis())
            .put("packages", JSONArray(all)).put("disabled", JSONArray(off))
            .put("home", home())
            .put("animations", tv.shell(
                "settings get global window_animation_scale; settings get global transition_animation_scale; " +
                        "settings get global animator_duration_scale").trim())
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
        val file = File(dir, "${key(info)}-$stamp.json").apply { writeText(json.toString(2)) }
        val original = File(dir, "${key(info)}-original.json")
        if (!original.exists()) file.copyTo(original)
        runCatching { tv.upload(file, "backup-$stamp.json") }
        return all.size
    }

    /** The current Home activity, "package/activity". */
    suspend fun home(): String = tv.shell(
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    ).lines().map { it.trim() }.lastOrNull { it.contains('/') && !it.contains(' ') } ?: ""

    /** The list's apps that are on this TV and switched on, minus anything the TV needs. */
    suspend fun candidates(list: TvList): List<SweepItem> {
        val installed = packages(tv.shell("pm list packages")).toSet()
        val off = packages(tv.shell("pm list packages -d")).toSet()
        val homePkg = home().substringBefore('/')
        // Wi-Fi set up by setupwraith goes with it (cleanyourtv's Home guard): keep it when it owns the network.
        val wraithOwnsWifi = tv.shell("dumpsys wifi | grep -o 'cname=[^ ]*' | head -3").contains("setupwraith")
        return list.packages.filter { it in installed && it !in off && !Apps.isProtected(it) && it != homePkg }
            .filterNot { wraithOwnsWifi && it.contains("setupwraith") }
            .map { SweepItem(it, sweep = !Lists.askFirst(it)) }
    }

    /** Turns the chosen apps off one by one; returns how many went off. */
    suspend fun sweep(pkgs: List<String>, progress: (Int, String) -> Unit): Int {
        var done = 0
        pkgs.forEachIndexed { i, pkg ->
            progress(i, pkg)
            if (runCatching { apps.setEnabled(pkg, false) }.isSuccess) done++
        }
        return done
    }

    /**
     * Makes {@code pkg}'s Home activity the home screen and checks that it took; if not, the old one comes back.
     * Returns true when the new home screen owns Home.
     */
    suspend fun setHome(pkg: String): Boolean {
        val before = home()
        val component = tv.shell(
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        ).lines().map { it.trim() }.firstOrNull { it.startsWith("$pkg/") } ?: return false
        tv.shell("cmd package set-home-activity --user 0 $component")
        if (home() == component) {
            tv.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
            return true
        }
        if (before.isNotEmpty()) tv.shell("cmd package set-home-activity --user 0 $before")
        return false
    }

    /**
     * Installs the bundled home screen (assets/openremotv-home.apk, cleanyourtv's launcher), puts one of the bundled
     * themes on it (assets/themes/<name>: "manga" for MangaHome, "india" for IndiaMinimalist) and makes it Home.
     */
    suspend fun installHome(theme: String, progress: (String) -> Unit): Boolean {
        progress("Installing the home screen…")
        if (!installMangaHome()) return false
        progress("Putting on the theme…")
        applyTheme(theme, progress)
        return true
    }

    /** Copies a bundled theme into the home screen's theme folder the way cleanyourtv's tools/theme.sh does, then restarts it. */
    suspend fun applyTheme(theme: String, progress: (String) -> Unit) {
        val base = "themes/$theme"
        val files = mutableListOf<String>()
        fun walk(dir: String) {
            for (name in context.assets.list(dir).orEmpty()) {
                val path = "$dir/$name"
                if (context.assets.list(path).isNullOrEmpty()) files += path.removePrefix("$base/") else walk(path)
            }
        }
        walk(base)
        tv.shell("mkdir -p $THEME_DIR/wallpapers; rm -f $THEME_DIR/wallpapers/*.jpg $THEME_DIR/wallpapers/*.png " +
                "$THEME_DIR/wallpapers/captions.txt $THEME_DIR/wallpapers/facts.txt; content delete --uri $THEME_URI/wallpapers 2>/dev/null")
        files.forEachIndexed { i, path ->
            progress("Copying ${if (theme == "manga") "MangaHome" else "IndiaMinimalist"} to your TV… ${i + 1} of ${files.size} files")
            val local = File(context.cacheDir, "theme-" + path.replace('/', '-'))
            context.assets.open("$base/$path").use { input -> local.outputStream().use { input.copyTo(it) } }
            try {
                put(local, path)
            } finally {
                local.delete()
            }
        }
        val revision = File(context.cacheDir, "theme_revision").apply { writeText(java.util.UUID.randomUUID().toString().replace("-", "")) }
        put(revision, "theme_revision")
        revision.delete()
        tv.shell(RESTART)
    }

    /** One theme file into place: through the launcher's provider where `content write` works, else a plain copy. */
    private suspend fun put(local: File, path: String) {
        val staged = "rst-" + path.replace('/', '-')
        tv.upload(local, "tmp/$staged")
        val tmp = "/data/local/tmp/$staged"
        tv.shell("rm -f $THEME_DIR/$path 2>/dev/null; { o=\$(content write --uri $THEME_URI/$path < $tmp 2>&1) && " +
                "case \"\$o\" in *'Unsupported operation'*) false ;; esac; } || cp $tmp $THEME_DIR/$path 2>/dev/null; rm -f $tmp")
    }

    /** Installs the bundled Manga Home (assets/openremotv-home.apk) and makes it Home. */
    suspend fun installMangaHome(): Boolean {
        val apk = File(context.cacheDir, "openremotv-home.apk")
        context.assets.open("openremotv-home.apk").use { input -> apk.outputStream().use { input.copyTo(it) } }
        try {
            tv.upload(apk, "tmp/openremotv-home.apk")
        } finally {
            apk.delete()
        }
        val out = tv.shell("pm install -r -g /data/local/tmp/openremotv-home.apk; rm -f /data/local/tmp/openremotv-home.apk")
        check(out.contains("Success")) { out.trim() }
        return setHome("com.openremotv.home")
    }

    /**
     * Undo anytime: back to the TV's original backup. Every app that was on then and is off now goes back on, the
     * original home screen and animation speeds come back. Returns how many apps came back.
     */
    suspend fun restoreAll(info: TvInfo): Int {
        val original = File(dir, "${key(info)}-original.json")
        var back = apps.undoAll()
        if (!original.exists()) return back
        val json = JSONObject(original.readText())
        val wasOff = json.getJSONArray("disabled").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val nowOff = packages(tv.shell("pm list packages -d"))
        for (pkg in nowOff) {
            if (pkg !in wasOff && runCatching { tv.setAppEnabled(pkg, true) }.isSuccess) back++
        }
        val home = json.optString("home")
        if (home.isNotEmpty() && home() != home) tv.shell("cmd package set-home-activity --user 0 $home")
        val speeds = json.optString("animations").lines().map { it.trim() }
        val names = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        names.zip(speeds).forEach { (n, v) -> if (v.matches(Regex("-?\\d+(\\.\\d+)?"))) tv.shell("settings put global $n $v") }
        return back
    }

    companion object {
        private const val THEME_DIR = "/sdcard/Android/data/com.openremotv.home/files"
        private const val THEME_URI = "content://com.openremotv.home.theme"
        /** cleanyourtv's theme.sh restart: back through the Home intent, with its accessibility helpers put back. */
        private const val RESTART = "a=\$(settings get secure enabled_accessibility_services); am force-stop com.openremotv.home; " +
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; case \"\$a\" in *com.openremotv.home*) " +
            "sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"

        fun packages(out: String) =
            out.lines().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }
    }
}
