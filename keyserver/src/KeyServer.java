import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * The phone app's helper on the TV, started once in the background by the adb shell (app_process) and then talked
 * to over its own TCP port, not through adb: long-lived adb shell streams left adbd threads spinning on the KM8.
 * Usage: CLASSPATH=km8keys.dex app_process / KeyServer <port> <token>
 *
 * The first line from the phone must be the token. Then, one command per line:
 *   "23"               press key 23
 *   "t:hello"          type text
 *   "f:<name>:<size>"  followed by exactly <size> bytes: saved as /sdcard/Download/KM8Remote/<name>,
 *                      or /data/local/tmp/<name> for "tmp/<name>"
 *   "e:<package>:1|0"  turn an app on, or off for this user (what "pm enable" and "pm disable-user" do)
 * Each command answers one line: "ok", "untypeable" or "error ...". It quits after 15 minutes with no phone.
 * Starting a JVM for every "input keyevent" took about six seconds on the KM8; this pays that cost once.
 */
public final class KeyServer {
    private static final int IDLE_MS = 15 * 60_000;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        String token = args[1];
        Class<?> im = Class.forName("android.hardware.input.InputManager");
        Object manager = im.getMethod("getInstance").invoke(null);
        Method inject = im.getMethod("injectInputEvent", InputEvent.class, int.class);
        KeyCharacterMap keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        try (ServerSocket server = new ServerSocket(port)) {
            server.setSoTimeout(IDLE_MS);
            while (true) {
                Socket phone;
                try {
                    phone = server.accept();
                } catch (SocketTimeoutException idle) {
                    return;
                }
                try (Socket s = phone) {
                    s.setTcpNoDelay(true);
                    s.setSoTimeout(IDLE_MS);
                    serve(s, token, manager, inject, keys);
                } catch (Exception ignored) {
                    // the phone went away; wait for the next one
                }
            }
        }
    }

    private static void serve(Socket s, String token, Object manager, Method inject, KeyCharacterMap keys)
            throws Exception {
        InputStream in = new BufferedInputStream(s.getInputStream());
        OutputStream out = s.getOutputStream();
        if (!token.equals(line(in))) return;
        say(out, "ready");
        for (String line; (line = line(in)) != null; ) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                if (line.startsWith("t:")) {
                    KeyEvent[] events = keys.getEvents(line.substring(2).toCharArray());
                    if (events == null) {
                        say(out, "untypeable");
                        continue;
                    }
                    for (KeyEvent e : events) inject.invoke(manager, e, 0);
                } else if (line.startsWith("f:")) {
                    int at = line.lastIndexOf(':');
                    String asked = line.substring(2, at);
                    String name = new File(asked).getName();
                    long size = Long.parseLong(line.substring(at + 1));
                    // "tmp/x.apk" goes to /data/local/tmp, where pm install can read it; the rest to Downloads.
                    File dir = new File(asked.startsWith("tmp/") ? "/data/local/tmp" : "/sdcard/Download/KM8Remote");
                    dir.mkdirs();
                    try (FileOutputStream f = new FileOutputStream(new File(dir, name))) {
                        byte[] buffer = new byte[64 * 1024];
                        long left = size;
                        while (left > 0) {
                            int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                            if (n < 0) throw new java.io.EOFException("file cut short");
                            f.write(buffer, 0, n);
                            left -= n;
                        }
                    }
                } else if (line.startsWith("e:")) {
                    int at = line.lastIndexOf(':');
                    setEnabled(line.substring(2, at), line.endsWith(":1"));
                } else {
                    int code = Integer.parseInt(line);
                    long now = SystemClock.uptimeMillis();
                    inject.invoke(manager, event(now, KeyEvent.ACTION_DOWN, code), 0);
                    inject.invoke(manager, event(now, KeyEvent.ACTION_UP, code), 0);
                }
                say(out, "ok");
            } catch (java.io.IOException e) {
                throw e;
            } catch (Exception e) {
                say(out, "error " + e);
            }
        }
    }

    /** IPackageManager.setApplicationEnabledSetting, as the shell may: ENABLED (1) or DISABLED_USER (3), user 0. */
    private static void setEnabled(String pkg, boolean on) throws Exception {
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class)
                .invoke(null, "package");
        Object pm = Class.forName("android.content.pm.IPackageManager$Stub")
                .getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        pm.getClass().getMethod("setApplicationEnabledSetting", String.class, int.class, int.class, int.class,
                String.class).invoke(pm, pkg, on ? 1 : 3, 0, 0, "com.android.shell");
    }

    private static String line(InputStream in) throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        for (int c; (c = in.read()) != -1; ) {
            if (c == '\n') return b.toString();
            b.append((char) c);
            if (b.length() > 4096) return b.toString();
        }
        return b.length() == 0 ? null : b.toString();
    }

    private static void say(OutputStream out, String text) throws java.io.IOException {
        out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static KeyEvent event(long downTime, int action, int code) {
        return new KeyEvent(downTime, SystemClock.uptimeMillis(), action, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }
}
