package app.locin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Settings, voice lines and daily stats (SharedPreferences). */
final class Store {
    static final String[] DEFAULTS = {
        "Reset. Breathe. Eyes back on the page.",
        "Lock in. One task. No distractions.",
        "Come back to the problem. You can solve this.",
        "Keep the momentum. Do the next small step.",
        "Back to work. Your future self will thank you.",
        "Discipline beats motivation. Lock back in.",
        "Mission control, return to your study objective.",
        "You drifted. It happens. Refocus now.",
        "Easy does it. Bring your attention back.",
        "Quick check. Are you still doing the task you chose?",
        "One step at a time. Return to the question.",
        "Stay with it. Finish this block strong.",
        "Close the mental tabs. Return to the page.",
        "Your only job right now is the next five minutes.",
        "Notice the distraction, then return to the task.",
        "Read the next line carefully. Start there.",
        "No need to feel motivated. Just resume.",
        "Focus is a rep. Do one more."
    };
    static final String[] MODES = {"Selected line", "Random", "In order"};
    static final String[] SESSIONS = {"Unlimited", "15 minutes", "25 minutes", "30 minutes", "45 minutes",
        "60 minutes", "90 minutes", "120 minutes", "180 minutes"};
    static final int[] SESSION_MIN = {0, 15, 25, 30, 45, 60, 90, 120, 180};

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("locin", Context.MODE_PRIVATE);
    }

    int getInt(String k, int d) { return p.getInt(k, d); }
    void putInt(String k, int v) { p.edit().putInt(k, v).apply(); }
    boolean getBool(String k, boolean d) { return p.getBoolean(k, d); }
    void putBool(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }

    ArrayList<String> phrases() {
        ArrayList<String> l = new ArrayList<>();
        String s = p.getString("phrases", null);
        if (s == null) { Collections.addAll(l, DEFAULTS); return l; }
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        } catch (Exception e) {
            l.clear();
            Collections.addAll(l, DEFAULTS);
        }
        return l;
    }

    void savePhrases(List<String> l) {
        p.edit().putString("phrases", new JSONArray(l).toString()).apply();
    }

    String active() { return p.getString("active", ""); }
    void setActive(String s) { p.edit().putString("active", s).apply(); }

    // ---- daily stats (reset automatically when the date changes)
    private void rollover() {
        String today = LocalDate.now().toString();
        if (!today.equals(p.getString("stat_date", ""))) {
            p.edit().putString("stat_date", today).putLong("stat_ms", 0L).putInt("stat_n", 0).apply();
        }
    }
    long todayMs() { rollover(); return p.getLong("stat_ms", 0L); }
    void setTodayMs(long ms) { rollover(); p.edit().putLong("stat_ms", ms).apply(); }
    int todaySessions() { rollover(); return p.getInt("stat_n", 0); }
    void addSession() { rollover(); p.edit().putInt("stat_n", p.getInt("stat_n", 0) + 1).apply(); }
}
