package com.km8.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Sending to the TV: a YouTube link plays in YouTube; a photo, video or song is copied over and opened there. */
class Send(private val context: Context, private val tv: Tv) {

    /** Plays a YouTube link on the TV; false when the text has no YouTube video in it. */
    suspend fun youtube(text: String): Boolean {
        val id = videoId(text) ?: return false
        tv.shell(
            "am start -a android.intent.action.VIEW -d " + Tv.shellQuote("https://www.youtube.com/watch?v=$id") +
                    " com.google.android.youtube.tv"
        )
        return true
    }

    /**
     * Copies a picked or shared file into the TV's Download/KM8Remote folder and opens it there with whatever app
     * the TV has for its type. Returns the name it got on the TV.
     */
    suspend fun file(uri: Uri): String {
        val name = displayName(uri).replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "file" }
        val type = context.contentResolver.getType(uri) ?: "*/*"
        val local = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)!!.use { input -> local.outputStream().use { input.copyTo(it) } }
        val remote = "/sdcard/Download/KM8Remote/$name"
        try {
            tv.upload(local, name)
        } finally {
            local.delete()
        }
        if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")) {
            tv.shell(
                "am start -a android.intent.action.VIEW -d " + Tv.shellQuote("file://$remote") +
                        " -t " + Tv.shellQuote(type)
            )
        }
        return name
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: ""
        }
        return uri.lastPathSegment ?: "file"
    }

    companion object {
        private val ID = Regex(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/))([A-Za-z0-9_-]{11})"
        )

        fun videoId(text: String): String? = ID.find(text)?.groupValues?.get(1)
    }
}
