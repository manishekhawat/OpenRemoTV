package com.openremotv.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lets the phone app write the theme over ADB on TVs where the shell can't reach Android/data:
 * {@code content write --uri content://com.openremotv.home.theme/wallpapers/01.webp < file}, and
 * {@code content delete --uri content://com.openremotv.home.theme/wallpapers} to clear the pictures. Only the
 * shell can call it (it needs WRITE_SECURE_SETTINGS, which apps can't hold), and only theme files are accepted.
 */
public final class ThemeProvider extends ContentProvider {
    private static final Pattern TOP = Pattern.compile("config\\.json|theme_revision|CREDITS\\.md");
    private static final Pattern IN_WALLPAPERS = Pattern.compile("[A-Za-z0-9._-]+\\.(webp|jpg|jpeg|png)|captions\\.txt|facts\\.txt");

    @Override public boolean onCreate() {
        return true;
    }

    /** The theme file a URI names, or null when it isn't one. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1 && TOP.matcher(segments.get(0)).matches()) return segments.get(0);
        if (segments.size() == 2 && Theme.WALLPAPERS.equals(segments.get(0)) && IN_WALLPAPERS.matcher(segments.get(1)).matches()) {
            return Theme.WALLPAPERS + "/" + segments.get(1);
        }
        return null;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String path = path(uri.getPathSegments());
        if (path == null) throw new FileNotFoundException("not a theme file: " + uri.getPath());
        File file = new File(Theme.folder(getContext()), path);
        boolean write = mode.contains("w");
        if (write) {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        List<String> segments = uri.getPathSegments();
        File dir = Theme.folder(getContext());
        if (segments.size() == 1 && Theme.WALLPAPERS.equals(segments.get(0))) {
            File[] files = new File(dir, Theme.WALLPAPERS).listFiles();
            int n = 0;
            for (int i = 0; files != null && i < files.length; i++) if (files[i].delete()) n++;
            return n;
        }
        String path = path(segments);
        return path != null && new File(dir, path).delete() ? 1 : 0;
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override public String getType(Uri uri) {
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int update(Uri uri, ContentValues values, String s, String[] a) {
        return 0;
    }
}
