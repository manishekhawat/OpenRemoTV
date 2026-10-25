package com.openremotv.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.List;

/**
 * A live channel, full screen, from the TV list on Home (assets/today/tv.txt). Up and down (or channel up and down)
 * change channel; Back returns Home. The streams are HLS, which the TV's own MediaPlayer plays. Its player starts
 * on a playlist's first picture size and is slow to move up, and many channels list 180p first, so the sharpest
 * size is picked from the playlist here and played directly.
 */
public class LiveTvActivity extends Activity {

    static final String EXTRA_CHANNEL = "channel";

    private List<String[]> channels;
    private int current;
    private VideoView video;
    private TextView caption;
    private float density;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        L10n.init(this);
        Radio.stop();   // one sound at a time
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        density = getResources().getDisplayMetrics().density;
        channels = Daily.read(this, "today/tv.txt", 3);
        current = Math.max(0, Math.min(channels.size() - 1, getIntent().getIntExtra(EXTRA_CHANNEL, 0)));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        caption = new TextView(this);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        caption.setTextColor(Color.WHITE);
        caption.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3000000);
        bg.setCornerRadius(dp(12));
        caption.setBackground(bg);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        cp.leftMargin = dp(40);
        cp.topMargin = dp(32);
        root.addView(caption, cp);
        setContentView(root);

        video.setOnPreparedListener(mp -> hideCaptionSoon());
        video.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) showCaption(getString(R.string.tv_tuning));
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) hideCaptionSoon();
            return false;
        });
        video.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, getString(R.string.tv_not_answering, channels.get(current)[0]), Toast.LENGTH_LONG).show();
            return true;
        });
        if (channels.isEmpty()) finish();
        else tune();
    }

    private void tune() {
        String[] ch = channels.get(current);
        showCaption((current + 1) + "  " + ch[0] + "  ·  " + getString(R.string.tv_tuning));
        video.stopPlayback();
        int tuning = ++tunes;
        new Thread(() -> {
            String url = sharpest(ch[2]);
            runOnUiThread(() -> {
                if (tuning != tunes || isFinishing()) return;   // changed channel meanwhile
                video.setVideoURI(Uri.parse(url));
                video.start();
            });
        }, "live-tv-tune").start();
    }

    private int tunes;

    /** The highest-bandwidth stream in an HLS master playlist, or the address itself when it isn't one. */
    static String sharpest(String master) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(master).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            String body;
            try (java.io.InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0 && out.size() < 256 * 1024) out.write(b, 0, n);
                body = out.toString("UTF-8");
            } finally {
                c.disconnect();
            }
            return pickSharpest(c.getURL().toString(), body);
        } catch (Exception e) {
            return master;   // the player can still try the master playlist itself
        }
    }

    static String pickSharpest(String base, String playlist) {
        String[] lines = playlist.split("\\r?\\n");
        long best = -1;
        String pick = null;
        for (int i = 0; i + 1 < lines.length; i++) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("BANDWIDTH=(\\d+)").matcher(lines[i]);
            long bw = m.find() ? Long.parseLong(m.group(1)) : 0;
            String uri = lines[i + 1].trim();
            if (bw > best && !uri.isEmpty() && !uri.startsWith("#")) {
                best = bw;
                pick = java.net.URI.create(base).resolve(uri).toString();
            }
        }
        return pick == null ? base : pick;
    }

    private void showCaption(String text) {
        caption.removeCallbacks(hide);
        caption.setText(text);
        caption.setAlpha(1f);
    }

    private final Runnable hide = () -> caption.animate().alpha(0f).setDuration(400).start();

    private void hideCaptionSoon() {
        caption.setText((current + 1) + "  " + channels.get(current)[0]);
        caption.removeCallbacks(hide);
        caption.postDelayed(hide, 3_000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int step = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP ? 1
                : keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ? -1 : 0;
        if (step != 0 && !channels.isEmpty()) {
            current = (current + step + channels.size()) % channels.size();
            tune();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            hideCaptionSoon();
            caption.setAlpha(1f);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        video.stopPlayback();
        finish();   // live TV doesn't wait in the background
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
