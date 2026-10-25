package com.openremotv.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;

/**
 * A live channel, full screen, from the TV list on Home (assets/today/tv.txt). Up and down (or channel up and down)
 * change channel; Back returns Home. The streams are HLS, played by ExoPlayer rather than the TV's own MediaPlayer:
 * the KM8's MediaPlayer crashes on some channels (its TS parser) and starts every channel on its lowest picture.
 * ExoPlayer is told to play the sharpest picture the TV can decode.
 */
public class LiveTvActivity extends Activity {

    static final String EXTRA_CHANNEL = "channel";

    private List<String[]> channels;
    private int current;
    private ExoPlayer player;
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
        if (channels.isEmpty()) {
            finish();
            return;
        }
        current = Math.max(0, Math.min(channels.size() - 1, getIntent().getIntExtra(EXTRA_CHANNEL, 0)));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView video = new SurfaceView(this);
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

        player = new ExoPlayer.Builder(this).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setForceHighestSupportedBitrate(true).build());
        player.setVideoSurfaceView(video);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_BUFFERING) showCaption(name() + "  ·  " + getString(R.string.tv_tuning));
                else if (s == Player.STATE_READY) hideCaptionSoon();
            }

            @Override public void onPlayerError(PlaybackException e) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition();   // fell behind the live edge: jump back to live
                    player.prepare();
                    return;
                }
                Toast.makeText(LiveTvActivity.this, getString(R.string.tv_not_answering, channels.get(current)[0]),
                        Toast.LENGTH_LONG).show();
            }
        });
        tune();
    }

    private String name() {
        return (current + 1) + "  " + channels.get(current)[0];
    }

    private void tune() {
        showCaption(name() + "  ·  " + getString(R.string.tv_tuning));
        player.setMediaItem(MediaItem.fromUri(channels.get(current)[2]));
        player.prepare();
        player.play();
    }

    private void showCaption(String text) {
        caption.removeCallbacks(hide);
        caption.animate().cancel();
        caption.setText(text);
        caption.setAlpha(1f);
    }

    private final Runnable hide = () -> caption.animate().alpha(0f).setDuration(400).start();

    private void hideCaptionSoon() {
        showCaption(name());
        caption.postDelayed(hide, 3_000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int step = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP ? 1
                : keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ? -1 : 0;
        if (step != 0 && player != null) {
            current = (current + step + channels.size()) % channels.size();
            tune();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            hideCaptionSoon();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
        finish();   // live TV doesn't wait in the background
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
