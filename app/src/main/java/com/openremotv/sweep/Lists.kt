package com.openremotv.sweep

import com.openremotv.tv.Tv
import android.content.Context
import org.json.JSONArray

/** One OpenRemoTV sweep list, for a family of TVs (assets/lists.json). */
data class TvList(val id: String, val tv: String, val os: String, val brand: String, val packages: List<String>)

/** What the TV says it is, from getprop. */
data class TvInfo(val model: String, val brand: String, val maker: String, val device: String, val android: String) {
    val title: String get() = listOf(maker.ifEmpty { brand }, model).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * The lists of apps to sweep, and finding the right one for a TV: each family's list is scored against the TV's
 * model, brand, maker and device names; the best few are offered, with the common list for any other TV. Apps the
 * TV needs are locked whatever a list says, and apps that aren't on the TV are skipped.
 */
object Lists {

    @Volatile private var cache: List<TvList>? = null

    fun all(context: Context): List<TvList> = cache ?: run {
        val arr = JSONArray(context.assets.open("lists.json").bufferedReader().readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.getJSONArray("packages")
            TvList(o.getString("id"), o.getString("tv"), o.optString("os"), o.optString("brand"),
                (0 until p.length()).map { p.getString(it) })
        }.also { cache = it }
    }

    suspend fun info(tv: Tv): TvInfo {
        val out = tv.shell(
            "getprop ro.product.model; getprop ro.product.brand; getprop ro.product.manufacturer; " +
                    "getprop ro.product.device; getprop ro.build.version.release"
        ).lines().map { it.trim() }
        fun at(i: Int) = out.getOrElse(i) { "" }
        return TvInfo(at(0), at(1), at(2), at(3), at(4))
    }

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.toSet()
    private fun squash(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** The family lists that look like this TV, best first (at most five); never the common list. */
    fun matches(context: Context, info: TvInfo): List<TvList> {
        val model = squash(info.model)
        val mine = words("${info.model} ${info.brand} ${info.maker} ${info.device}") - setOf("android", "tv", "box")
        return all(context).filter { it.id != COMMON }.map { list ->
            val theirs = words("${list.id} ${list.tv} ${list.brand}")
            var score = (mine intersect theirs).size
            if (model.length >= 3 && (squash(list.id).contains(model) || squash(list.tv).contains(model))) score += 10
            if (info.brand.isNotBlank() && list.brand.equals(info.brand, ignoreCase = true)) score += 2
            list to score
        }.filter { it.second >= 1 }.sortedByDescending { it.second }.take(5).map { it.first }
    }

    /** For any other TV: the extras every Android TV and Google TV ships. */
    fun common(context: Context): TvList = all(context).first { it.id == COMMON }

    private const val COMMON = "common"

    /**
     * Apps a family often wants kept: streaming, the assistant, casting, the updater and Wi-Fi setup, and the stock
     * home screens (those go only once a new home screen owns Home). In a sweep these start switched to "keep".
     */
    fun askFirst(pkg: String): Boolean {
        val p = pkg.lowercase()
        return listOf(
            "youtube", "netflix", "amazonvideo", "primevideo", "hotstar", "sonyliv", "zee5", "graymatrix", "jiocinema",
            "disney", "spotify", "plex", "kodi", "katniss", "assistant", "mediashell", "chromecast", "cast",
            "updater", "ota", "fota", "setupwraith", "launcher", "tvlauncher", "launcherx", "music",
        ).any { p.contains(it) }
    }
}
