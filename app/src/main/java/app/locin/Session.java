package app.locin;

import java.util.Locale;

/** Live session state shared between the service (writer) and the UI (reader). Same process. */
final class Session {
    static volatile int state = 0;            // 0 idle, 1 running, 2 paused
    static volatile boolean breakPhase = false;
    static volatile long elapsedMs = 0;
    static volatile int count = 0;
    static volatile long nextAt = 0;          // SystemClock.elapsedRealtime deadlines, 0 = none
    static volatile long phaseEnd = 0;
    static volatile long sessionEnd = 0;
    static volatile long pausedAt = 0;
    static volatile long todayMs = 0;

    static String fmt(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%02d:%02d", m, s);
    }
}
