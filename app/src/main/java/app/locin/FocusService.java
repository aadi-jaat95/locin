package app.locin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/** Foreground service: keeps the timer alive with the screen off and speaks the reminders. */
public class FocusService extends Service {
    static final String A_START = "app.locin.START", A_PAUSE = "app.locin.PAUSE", A_STOP = "app.locin.STOP",
            A_SNOOZE = "app.locin.SNOOZE", A_TEST = "app.locin.TEST";
    private static final String CH = "locin_session";
    private static final int NID = 7;

    private Store st;
    private TextToSpeech tts;
    private boolean ready;
    private String pending;
    private final Handler h = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wl;
    private AudioManager am;
    private AudioFocusRequest focusReq;
    private AudioAttributes attrs;
    private long lastTick, lastSave, lastNote;
    private int orderIdx;
    private String lastSpoken;
    private final Random rnd = new Random();

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try { tick(); } catch (Exception ignored) { }
            if (Session.state != 0) h.postDelayed(this, 500);
        }
    };
    private final Runnable idleStop = new Runnable() {
        @Override public void run() {
            if (Session.state == 0) { stopForeground(true); stopSelf(); }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        st = new Store(this);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationChannel c = new NotificationChannel(CH, "Focus session", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setAudioAttributes(attrs);
                int r = tts.setLanguage(Locale.getDefault());
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { unfocus(); }
                    @Override public void onError(String id) { unfocus(); }
                });
                ready = true;
                if (pending != null) { String t = pending; pending = null; speak(t); }
            } else {
                toast("Text-to-speech is not available. Install Google Speech Services and try again.");
            }
        });
    }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        String a = in == null ? null : in.getAction();
        startFg();
        if (A_START.equals(a)) start();
        else if (A_PAUSE.equals(a)) togglePause();
        else if (A_STOP.equals(a)) stopSession();
        else if (A_SNOOZE.equals(a)) snooze();
        else if (A_TEST.equals(a)) {
            String t = in.getStringExtra("text");
            if (t == null || t.trim().isEmpty()) t = activeText();
            if (t != null) speak(t);
            h.removeCallbacks(idleStop);
            h.postDelayed(idleStop, 9000);
        } else if (Session.state == 0) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        Session.state = 0;
        lock(false);
        unfocus();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ session control
    private int interval() {
        int t = st.getInt("min", 2) * 60 + st.getInt("sec", 0);
        return Math.min(10800, Math.max(5, t));
    }

    private void start() {
        if (Session.state != 0) return;
        long now = SystemClock.elapsedRealtime();
        boolean pomo = st.getBool("pomo", false);
        int len = Store.SESSION_MIN[Math.max(0, Math.min(Store.SESSION_MIN.length - 1, st.getInt("session", 0)))];
        Session.state = 1;
        Session.breakPhase = false;
        Session.elapsedMs = 0;
        Session.count = 0;
        Session.todayMs = st.todayMs();
        Session.nextAt = now + interval() * 1000L;
        Session.phaseEnd = pomo ? now + st.getInt("focus", 25) * 60000L : 0;
        Session.sessionEnd = len > 0 ? now + len * 60000L : 0;
        orderIdx = 0;
        lastSpoken = null;
        lastTick = now;
        lastSave = now;
        lock(true);
        if (st.getBool("announce", true)) speak("Focus session started.");
        h.removeCallbacks(idleStop);
        h.removeCallbacks(ticker);
        h.post(ticker);
        notifyNow();
    }

    private void togglePause() {
        long now = SystemClock.elapsedRealtime();
        if (Session.state == 1) {
            Session.state = 2;
            Session.pausedAt = now;
            if (tts != null) tts.stop();
            unfocus();
        } else if (Session.state == 2) {
            long shift = now - Session.pausedAt;
            if (Session.nextAt != 0) Session.nextAt += shift;
            if (Session.phaseEnd != 0) Session.phaseEnd += shift;
            if (Session.sessionEnd != 0) Session.sessionEnd += shift;
            Session.state = 1;
            lastTick = now;
        }
        notifyNow();
    }

    private void snooze() {
        if (Session.state != 1 || Session.breakPhase) return;
        int m = Math.max(1, Math.min(60, st.getInt("snooze", 5)));
        Session.nextAt = SystemClock.elapsedRealtime() + m * 60000L;
        notifyNow();
    }

    private void stopSession() {
        if (tts != null) tts.stop();
        unfocus();
        endSession();
        stopForeground(true);
        stopSelf();
    }

    private void endSession() {
        if (Session.state != 0 && Session.elapsedMs >= 60000) st.addSession();
        st.setTodayMs(Session.todayMs);
        Session.state = 0;
        Session.breakPhase = false;
        Session.nextAt = Session.phaseEnd = Session.sessionEnd = 0;
        lock(false);
        h.removeCallbacks(ticker);
    }

    private void finishSession() {
        if (st.getBool("announce", true)) speak("Session complete. Well done.");
        endSession();
        notifyNow();
        h.removeCallbacks(idleStop);
        h.postDelayed(idleStop, 9000);
    }

    // ------------------------------------------------------------------ timer
    private void tick() {
        long now = SystemClock.elapsedRealtime();
        long dt = Math.min(Math.max(now - lastTick, 0), 5000);
        lastTick = now;
        if (Session.state != 1) { return; }
        Session.elapsedMs += dt;
        if (!Session.breakPhase) Session.todayMs += dt;
        if (Session.sessionEnd != 0 && now >= Session.sessionEnd) { finishSession(); return; }
        if (Session.phaseEnd != 0 && now >= Session.phaseEnd) switchPhase(now);
        else if (!Session.breakPhase && Session.nextAt != 0 && now >= Session.nextAt) fire(now);
        if (now - lastSave > 30000) { lastSave = now; st.setTodayMs(Session.todayMs); }
        if (now - lastNote > 5000) { lastNote = now; notifyNow(); }
    }

    private void fire(long now) {
        String t = choose();
        Session.nextAt = now + interval() * 1000L;
        if (t == null) { toast("No voice lines left. Add one in locin."); return; }
        speak(t);
        Session.count++;
        lastSpoken = t;
    }

    private void switchPhase(long now) {
        int focus = Math.max(1, st.getInt("focus", 25)), brk = Math.max(1, st.getInt("break", 5));
        if (!Session.breakPhase) {
            Session.breakPhase = true;
            Session.phaseEnd = now + brk * 60000L;
            Session.nextAt = 0;
            if (st.getBool("announce", true)) speak("Break time. Rest for " + brk + " minutes.");
        } else {
            Session.breakPhase = false;
            Session.phaseEnd = now + focus * 60000L;
            Session.nextAt = now + interval() * 1000L;
            if (st.getBool("announce", true)) speak("Break over. Back to focus.");
        }
        notifyNow();
    }

    // ------------------------------------------------------------------ voice lines
    private String activeText() {
        ArrayList<String> l = st.phrases();
        if (l.isEmpty()) return null;
        String a = st.active();
        return l.contains(a) ? a : l.get(0);
    }

    private String choose() {
        ArrayList<String> l = st.phrases();
        if (l.isEmpty()) return null;
        int mode = st.getInt("mode", 0);
        if (mode == 1) {
            ArrayList<String> pool = new ArrayList<>(l);
            if (pool.size() > 1 && lastSpoken != null) pool.remove(lastSpoken);
            return pool.get(rnd.nextInt(pool.size()));
        }
        if (mode == 2) {
            String t = l.get(orderIdx % l.size());
            orderIdx = (orderIdx + 1) % l.size();
            return t;
        }
        return activeText();
    }

    private void speak(String t) {
        if (!ready) { pending = t; return; }
        focus();
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0, Math.min(100, st.getInt("volume", 100))) / 100f);
        tts.setSpeechRate(Math.max(50, Math.min(200, st.getInt("rate", 100))) / 100f);
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, b, "locin");
    }

    private void focus() {
        if (focusReq == null) {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs).build();
        }
        am.requestAudioFocus(focusReq);
    }

    private void unfocus() {
        if (focusReq != null) am.abandonAudioFocusRequest(focusReq);
    }

    private void lock(boolean on) {
        if (on) {
            if (wl == null) {
                wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "locin:session");
                wl.setReferenceCounted(false);
            }
            if (!wl.isHeld()) wl.acquire(12 * 60 * 60 * 1000L);
        } else if (wl != null && wl.isHeld()) {
            wl.release();
        }
    }

    private void toast(String m) {
        h.post(() -> Toast.makeText(getApplicationContext(), m, Toast.LENGTH_LONG).show());
    }

    // ------------------------------------------------------------------ notification
    private PendingIntent svc(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, FocusService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification note() {
        long now = SystemClock.elapsedRealtime();
        String txt;
        if (Session.state == 0) txt = "Ready";
        else if (Session.state == 2) txt = "Paused";
        else if (Session.breakPhase) txt = "Break - ends in " + Session.fmt((Session.phaseEnd - now) / 1000);
        else txt = "Focus - next reminder in " + Session.fmt((Session.nextAt - now) / 1000);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat).setContentTitle("locin").setContentText(txt)
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true);
        if (Session.state != 0) {
            b.addAction(0, Session.state == 2 ? "Resume" : "Pause", svc(A_PAUSE, 1));
            b.addAction(0, "Stop", svc(A_STOP, 2));
        }
        return b.build();
    }

    private void startFg() {
        Notification n = note();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NID, n);
    }

    private void notifyNow() {
        getSystemService(NotificationManager.class).notify(NID, note());
    }
}
