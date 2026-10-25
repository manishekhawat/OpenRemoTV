package com.openremotv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * weather_city: "24° Haze · AQI 156" in the top bar, from Open-Meteo's free forecast and air quality APIs (no
 * key). One fetch an hour at most, only while Home is on screen; the last answer is cached so Home shows it at
 * once and keeps showing it when the network is down. Nothing is shown until the first fetch succeeds.
 */
final class WeatherPill {
    private static final long REFRESH_MS = 60 * 60_000L;
    private static final long RETRY_MS = 15 * 60_000L;

    private final TextView view;
    private final Extras extras;
    private final SharedPreferences cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean fetching;
    /** widgets: the weather card wants the whole answer; the pill itself then stays hidden. */
    private java.util.function.Consumer<JSONObject> listener;
    private long lastAttempt;

    WeatherPill(Context context, TextView view, Extras extras) {
        this.view = view;
        this.extras = extras;
        cache = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        // A cache for another city is stale.
        if (!extras.weatherCity.equals(cache.getString("city", ""))) {
            cache.edit().clear().putString("city", extras.weatherCity).apply();
        }
        show(cache.getString("text", ""));
    }

    /** Hands every answer (cached now, fresh later) to the weather card, and hides the pill. */
    void setListener(java.util.function.Consumer<JSONObject> listener) {
        this.listener = listener;
        view.setVisibility(View.GONE);
        String raw = cache.getString("raw", "");
        if (!raw.isEmpty()) {
            try {
                listener.accept(new JSONObject(raw));
            } catch (Exception ignored) {
                // a bad cache waits for the next fetch
            }
        } else {
            cache.edit().putLong("fetched", 0).apply();   // no full answer cached yet: fetch at once
        }
    }

    void update() {
        long now = System.currentTimeMillis();
        long fetched = cache.getLong("fetched", 0);
        if (fetching || now - fetched < REFRESH_MS || now - lastAttempt < RETRY_MS) return;
        fetching = true;
        lastAttempt = now;
        worker.execute(() -> {
            JSONObject raw = fetch();
            String text = raw == null ? null : raw.optString("text", null);
            view.post(() -> {
                fetching = false;
                if (text == null) return;
                cache.edit().putString("text", text).putString("raw", raw.toString())
                        .putLong("fetched", System.currentTimeMillis()).apply();
                show(text);
                if (listener != null) listener.accept(raw);
            });
        });
    }

    private void show(String text) {
        view.setText(text);
        view.setContentDescription(extras.weatherCity + " " + text);
        view.setVisibility(text.isEmpty() || listener != null ? View.GONE : View.VISIBLE);
    }

    /**
     * {"text": pill text, "current": {...}, "daily": {...}, "aqi": n}: the current conditions (temperature, feels
     * like, humidity, wind, code) and five days (code, max, min, sunrise, sunset), plus the US AQI when it answers.
     */
    private JSONObject fetch() {
        String at = String.format(Locale.ROOT, "latitude=%.4f&longitude=%.4f", extras.weatherLatitude, extras.weatherLongitude);
        try {
            JSONObject answer = get("https://api.open-meteo.com/v1/forecast?" + at
                    + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                    + "&forecast_days=5&timezone=auto");
            JSONObject weather = answer.getJSONObject("current");
            int temp = (int) Math.round(weather.getDouble("temperature_2m"));
            String text = temp + "° " + condition(weather.optInt("weather_code", -1));
            JSONObject out = new JSONObject();
            out.put("current", weather);
            if (answer.has("daily")) out.put("daily", answer.getJSONObject("daily"));
            try {
                JSONObject air = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + at
                        + "&current=us_aqi").getJSONObject("current");
                if (!air.isNull("us_aqi")) {
                    long aqi = Math.round(air.getDouble("us_aqi"));
                    text += " · AQI " + aqi;
                    out.put("aqi", aqi);
                }
            } catch (Exception ignored) {
                // weather alone is still worth showing
            }
            out.put("text", text.trim());
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0 && out.size() < 64 * 1024) out.write(buffer, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    /** WMO weather codes, as Open-Meteo reports them, in a word or two. */
    static String condition(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Storm";
        return "";
    }
}
