package com.openremotv.home;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opening YouTube on the TV: a search (darshan, recipes) or one video (a link sent from a phone). */
final class YouTube {

    static final String TV = "com.google.android.youtube.tv";
    private static final Pattern ID = Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/))([A-Za-z0-9_-]{11})");

    private YouTube() {
    }

    /** The video id in a YouTube link, or null. */
    static String videoId(String link) {
        Matcher m = ID.matcher(link == null ? "" : link);
        return m.find() ? m.group(1) : null;
    }

    static void search(Context ctx, String query) {
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(TV).putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (start(ctx, search)) return;
        Intent results = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
                .setPackage(TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(ctx, results)) Toast.makeText(ctx, "YouTube is not installed", Toast.LENGTH_LONG).show();
    }

    static boolean play(Context ctx, String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id))
                .setPackage(TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(ctx, view);
    }

    private static boolean start(Context ctx, Intent i) {
        try {
            if (i.resolveActivity(ctx.getPackageManager()) == null) return false;
            ctx.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
