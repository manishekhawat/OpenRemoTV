package com.openremotv.home;

import android.service.dreams.DreamService;
import android.widget.FrameLayout;

/**
 * The screensaver: the theme's photos one a minute, with the time, the weather and the photo's place and fact
 * (see {@link AmbientScene}). Choose it in the TV's Settings › Device Preferences › Screen saver.
 */
public final class ClockDream extends DreamService {
    private AmbientScene scene;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);
        scene = new AmbientScene(root, Theme.load(this));
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene != null) scene.start();
    }

    @Override public void onDreamingStopped() {
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }
}
