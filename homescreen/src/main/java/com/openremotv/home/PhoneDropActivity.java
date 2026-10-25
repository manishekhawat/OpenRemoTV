package com.openremotv.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Phone to TV: a QR code that opens a small page on the phone (same Wi-Fi) to send a YouTube link or a photo to
 * the TV. The page is served by a tiny HTTP server in this activity, on port 8765, only while it is on screen, and
 * only at a random path shown in the QR code, so nothing else on the network can reach it. A YouTube link plays in
 * YouTube; a photo fills the screen until Back.
 */
public class PhoneDropActivity extends Activity {

    private static final int PORT = 8765;
    private static final int MAX_PHOTO = 25 * 1024 * 1024;

    private final String token = token();
    private volatile ServerSocket server;
    private ImageView photo;
    private TextView status;
    private float density;
    /** MangaHome: a comic page, like ComicHome. */
    private boolean comic;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        L10n.init(this);
        density = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        boolean comic = false;
        try {
            Theme extras = Theme.load(this);
            comic = extras.mangaLayout || extras.mangaStyle;
        } catch (RuntimeException ignored) {
            // the plain look
        }
        this.comic = comic;
        if (comic) root.setBackground(new ComicHome.PageDrawable(density));
        else root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1A1430, 0xFF0B1020, 0xFF02040A}));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(80), dp(40), dp(80), dp(40));
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String address = address();
        ImageView qr = new ImageView(this);
        qr.setBackgroundColor(Color.WHITE);
        if (comic) qr.setBackground(new ComicHome.PanelDrawable(density, -1.5f, false));
        qr.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (comic) qr.setPadding(dp(22), dp(22), dp(28), dp(28));
        if (address != null) qr.setImageBitmap(qr("http://" + address + ":" + PORT + "/" + token, dp(260)));
        row.addView(qr, new LinearLayout.LayoutParams(dp(288), dp(288)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(dp(48));
        row.addView(text, tp);
        if (comic) {
            LinearLayout strip = new LinearLayout(this);
            strip.setGravity(Gravity.CENTER_VERTICAL);
            strip.addView(new ComicHome.Remo(this), new LinearLayout.LayoutParams(dp(84), dp(84)));
            TextView bubble = text(20, 0xFFFFFFFF, getString(R.string.drop_scan_me));
            bubble.setBackground(new ComicHome.BubbleDrawable(density));
            bubble.setPadding(dp(16), dp(8), dp(16), dp(18));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMarginStart(dp(10));
            strip.addView(bubble, bp);
            text.addView(strip);
        }
        text.addView(text(32, 0xFFFFFFFF, comic ? getString(R.string.drop_title).toUpperCase(java.util.Locale.getDefault()) : getString(R.string.drop_title)));
        TextView how = text(19, 0xE6FFFFFF, getString(R.string.drop_steps));
        how.setLineSpacing(dp(8), 1f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(18);
        text.addView(how, hp);
        status = text(17, 0xFFFFD54F, getString(address == null ? R.string.drop_no_network : R.string.drop_waiting));
        if (comic) {
            status.setBackground(new ComicHome.CaptionDrawable(density));
            status.setPadding(dp(10), dp(4), dp(10), dp(4));
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(22);
        text.addView(status, sp);
        text.addView(text(15, 0x99FFFFFF, getString(R.string.drop_close)), sp);

        photo = new ImageView(this);
        photo.setBackgroundColor(Color.BLACK);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setVisibility(View.GONE);
        root.addView(photo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Thread t = new Thread(this::serve, "phone-drop");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void onStop() {
        ServerSocket s = server;
        server = null;
        try {
            if (s != null) s.close();
        } catch (Exception ignored) {
            // closing anyway
        }
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && photo.getVisibility() == View.VISIBLE) {
            photo.setVisibility(View.GONE);
            photo.setImageDrawable(null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void serve() {
        try (ServerSocket s = new ServerSocket()) {
            s.setReuseAddress(true);   // a quick reopen while the last socket is in TIME_WAIT
            s.bind(new java.net.InetSocketAddress(PORT));
            server = s;
            while (server == s) {
                try (Socket c = s.accept()) {
                    c.setSoTimeout(30_000);
                    handle(c);
                } catch (Exception ignored) {
                    // one bad request; keep listening
                }
            }
        } catch (Exception e) {
            runOnUiThread(() -> status.setText(getString(R.string.drop_port_failed, PORT)));
        }
    }

    private void handle(Socket c) throws Exception {
        InputStream in = c.getInputStream();
        String requestLine = line(in);
        if (requestLine == null) return;
        int length = 0;
        for (String h; (h = line(in)) != null && !h.isEmpty(); ) {
            if (h.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                length = Integer.parseInt(h.substring(15).trim());
            }
        }
        String[] parts = requestLine.split(" ");
        String method = parts[0], path = parts.length > 1 ? parts[1] : "/";
        OutputStream out = c.getOutputStream();
        String base = "/" + token;
        if (method.equals("GET") && path.equals(base)) {
            reply(out, "200 OK", "text/html; charset=utf-8", page());
        } else if (method.equals("POST") && path.equals(base + "/link") && length < 8192) {
            String body = new String(read(in, length), StandardCharsets.UTF_8);
            String link = body.startsWith("url=") ? URLDecoder.decode(body.substring(4), "UTF-8").trim() : "";
            reply(out, "200 OK", "text/plain; charset=utf-8", openLink(link));
        } else if (method.equals("POST") && path.equals(base + "/photo") && length > 0 && length <= MAX_PHOTO) {
            byte[] data = read(in, length);
            reply(out, "200 OK", "text/plain; charset=utf-8", showPhoto(data));
        } else {
            reply(out, "404 Not Found", "text/plain", "Not here.");
        }
    }

    private String openLink(String link) {
        String id = YouTube.videoId(link);
        if (id == null) return "That doesn't look like a YouTube link.";
        runOnUiThread(() -> {
            status.setText("Playing your video in YouTube…");
            if (!YouTube.play(this, id)) status.setText("YouTube is not installed.");
        });
        return "Sent! It's playing on the TV.";
    }

    private String showPhoto(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0) return "That file isn't a photo the TV can show.";
        int sample = 1;
        while (o.outWidth / sample > 3840 || o.outHeight / sample > 2160) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (bmp == null) return "That file isn't a photo the TV can show.";
        try {
            File dir = new File(getFilesDir(), "drop");
            if (dir.isDirectory() || dir.mkdirs()) {
                try (FileOutputStream f = new FileOutputStream(new File(dir, "last.jpg"))) {
                    f.write(data);
                }
            }
        } catch (Exception ignored) {
            // showing it matters more than keeping it
        }
        runOnUiThread(() -> {
            photo.setImageBitmap(bmp);
            photo.setVisibility(View.VISIBLE);
            status.setText("Showing your photo. Press Back to close it.");
        });
        return "Sent! Your photo is on the TV.";
    }

    private static String page() {
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>Send to TV</title><style>body{font:18px system-ui,sans-serif;margin:24px;background:#0b1020;color:#fff}"
                + "input,button{font:inherit;width:100%;box-sizing:border-box;padding:14px;margin:8px 0;border-radius:12px;border:0}"
                + "button{background:#ffd54f;color:#000;font-weight:600}h2{margin-top:32px}#s{color:#ffd54f;min-height:1.5em}</style></head><body>"
                + "<h1>Send to TV</h1><p id=s></p>"
                + "<h2>YouTube link</h2><input id=u placeholder='Paste a YouTube link'><button onclick=link()>Send link</button>"
                + "<h2>Photo</h2><input id=f type=file accept='image/*'><button onclick=pic()>Send photo</button>"
                + "<script>var b=location.pathname;function say(t){document.getElementById('s').textContent=t}"
                + "function link(){say('Sending…');fetch(b+'/link',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:'url='+encodeURIComponent(document.getElementById('u').value)}).then(r=>r.text()).then(say).catch(()=>say('The TV did not answer.'))}"
                + "function pic(){var f=document.getElementById('f').files[0];if(!f){say('Pick a photo first.');return}say('Sending…');"
                + "fetch(b+'/photo',{method:'POST',body:f}).then(r=>r.text()).then(say).catch(()=>say('The TV did not answer.'))}"
                + "</script></body></html>";
    }

    private static void reply(OutputStream out, String code, String type, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + code + "\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private static String line(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int ch; (ch = in.read()) != -1; ) {
            if (ch == '\n') return b.toString("UTF-8").replace("\r", "");
            if (b.size() > 8192) return null;
            b.write(ch);
        }
        return b.size() == 0 ? null : b.toString("UTF-8");
    }

    private static byte[] read(InputStream in, int length) throws Exception {
        byte[] data = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(data, off, length - off);
            if (n < 0) break;
            off += n;
        }
        return data;
    }

    /**
     * The TV's IPv4 address on the network it is actually using, or null. The active network's own address, not
     * the first interface found: some TVs keep a stale address on its unplugged Ethernet port.
     */
    private String address() {
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            android.net.LinkProperties lp = cm == null ? null : cm.getLinkProperties(cm.getActiveNetwork());
            if (lp != null) {
                for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                    InetAddress a = la.getAddress();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // fall back to the interfaces
        }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || !ni.getName().startsWith("wlan")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // no network
        }
        return null;
    }

    private static String token() {
        String chars = "abcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 8; i++) s.append(chars.charAt(r.nextInt(chars.length())));
        return s.toString();
    }

    private static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            Bitmap b = Bitmap.createBitmap(m.getWidth(), m.getHeight(), Bitmap.Config.RGB_565);
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) b.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView text(int sp, int color, String s) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(comic ? ComicHome.INK : color);
        t.setText(s);
        try {
            t.setTypeface(UiFont.get(this, UiFont.REGULAR));
        } catch (Exception ignored) {
            // the system font is fine
        }
        if (comic && sp >= 20) t.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        return t;
    }
}
