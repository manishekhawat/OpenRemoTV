import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Runs on the TV under the adb shell (app_process), once per connection, and injects keys as fast as the phone
 * sends them: "23" presses key 23, "t:hello" types text. Starting a JVM for every "input keyevent" took about six
 * seconds on the KM8; this pays that cost once. The shell user may inject input (INJECT_EVENTS).
 */
public final class KeyServer {
    public static void main(String[] args) throws Exception {
        Class<?> im = Class.forName("android.hardware.input.InputManager");
        Object manager = im.getMethod("getInstance").invoke(null);
        Method inject = im.getMethod("injectInputEvent", InputEvent.class, int.class);
        KeyCharacterMap keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("ready");
        System.out.flush();
        for (String line; (line = in.readLine()) != null; ) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                if (line.startsWith("t:")) {
                    KeyEvent[] events = keys.getEvents(line.substring(2).toCharArray());
                    if (events == null) {
                        System.out.println("untypeable");
                    } else {
                        for (KeyEvent e : events) inject.invoke(manager, e, 0);
                        System.out.println("ok");
                    }
                } else {
                    int code = Integer.parseInt(line);
                    long now = SystemClock.uptimeMillis();
                    inject.invoke(manager, event(now, KeyEvent.ACTION_DOWN, code), 0);
                    inject.invoke(manager, event(now, KeyEvent.ACTION_UP, code), 0);
                    System.out.println("ok");
                }
            } catch (Exception e) {
                System.out.println("error " + e);
            }
            System.out.flush();
        }
    }

    private static KeyEvent event(long downTime, int action, int code) {
        return new KeyEvent(downTime, SystemClock.uptimeMillis(), action, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }
}
