package com.openremotv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Where the TV is, roughly, when the phone app didn't set a place: the city of the TV's internet address, from
 * ipwho.is (free, no key). Looked up at most once a day and kept in the "geo" preferences, so the weather and the
 * Panchang work offline after the first answer. City-level only; wrong behind a VPN. The phone app's place, when it
 * set one, always wins.
 */
final class GeoIp {
    private static final String PREFS = "geo";
    private static final long REFRESH_MS = 24 * 60 * 60_000L;

    private GeoIp() {}

    /** The cached place as {city, latitude, longitude}, or null when there is none yet. */
    static Object[] cached(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String city = p.getString("city", "");
        if (city.isEmpty()) return null;
        return new Object[]{city, (double) p.getFloat("lat", 0f), (double) p.getFloat("lon", 0f)};
    }

    /** Looks the place up again if the cache is a day old; {@code changed} runs on the main thread if it moved. */
    static void refresh(Context context, Runnable changed) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("fetched", 0) < REFRESH_MS) return;
        new Thread(() -> {
            try {
                JSONObject o = get("https://ipwho.is/");
                if (!o.optBoolean("success", false)) return;
                String city = o.optString("city", "").trim();
                double lat = o.optDouble("latitude", Double.NaN), lon = o.optDouble("longitude", Double.NaN);
                if (city.isEmpty() || Double.isNaN(lat) || Double.isNaN(lon)) return;
                boolean moved = !city.equals(p.getString("city", ""));
                p.edit().putString("city", city).putFloat("lat", (float) lat).putFloat("lon", (float) lon)
                        .putLong("fetched", System.currentTimeMillis()).apply();
                if (moved && changed != null) new Handler(Looper.getMainLooper()).post(changed);
            } catch (Exception ignored) {
                // offline: try again next time Home opens
            }
        }, "geo-ip").start();
    }

    private static JSONObject get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0 && out.size() < 64 * 1024) out.write(b, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            c.disconnect();
        }
    }
}
