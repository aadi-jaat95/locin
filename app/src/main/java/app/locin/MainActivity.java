package app.locin;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    static final int BG = 0xFF090D14, PANEL = 0xFF111925, PANEL2 = 0xFF151F2D, BORDER = 0xFF253448,
            TEXT = 0xFFEAF2FF, MUTED = 0xFF91A2B8, CYAN = 0xFF39D9FF, GREEN = 0xFF8BFFB0,
            ORANGE = 0xFFFFB86B, RED = 0xFFFF6B81;
    static final int MAX_LEN = 200;

    private Store st;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView tTimer, tNextTitle, tNext, tCount, tToday, pill, tRate, tVol, info;
    private Button bStart, bPause, bStop, bSnooze, bTest, bReset;
    private EditText eMin, eSec, eFocus, eBreak, eSnooze, eLine;
    private Spinner spSession, spMode;
    private CheckBox cPomo, cAnnounce;
    private SeekBar sbRate, sbVol;
    private LinearLayout rows;
    private ArrayList<String> phrases;
    private String active;
    private int sel = -1;
    private boolean loading = true, resetArmed = false;
    private View rootView;
    private final TextView.OnEditorActionListener doneListener = (v, a, e) -> { hideKeyboard(); return true; };

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View f = getCurrentFocus();
        if (imm != null) imm.hideSoftInputFromWindow(f != null ? f.getWindowToken() : rootView.getWindowToken(), 0);
        if (rootView != null) rootView.requestFocus();   // park focus on the page so no EditText re-opens the keyboard
    }

    /** Tap anywhere outside the text box that has focus and the keyboard closes. */
    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View f = getCurrentFocus();
            if (f instanceof EditText) {
                int[] loc = new int[2];
                f.getLocationOnScreen(loc);
                float x = ev.getRawX(), y = ev.getRawY();
                if (x < loc[0] || x > loc[0] + f.getWidth() || y < loc[1] || y > loc[1] + f.getHeight()) hideKeyboard();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ------------------------------------------------------------------ lifecycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new Store(this);
        phrases = st.phrases();
        active = st.active();
        if (!phrases.contains(active)) active = phrases.isEmpty() ? "" : phrases.get(0);
        buildUi();
        loadValues();
        loading = false;
        refreshRows();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private final Runnable hud = new Runnable() {
        @Override public void run() { updateHud(); h.postDelayed(this, 500); }
    };

    @Override protected void onResume() { super.onResume(); h.post(hud); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(hud); saveSettings(); }

    // ------------------------------------------------------------------ UI building
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView tv(String s, int sp, int color, boolean mono) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (mono) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return t;
    }

    private Button btn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(color);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setBackground(box(PANEL2, BORDER, 10));
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams lp(float weight, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(weight > 0 ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        p.setMargins(dp(3), dp(top), dp(3), 0);
        return p;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout panel(LinearLayout parent, String title) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(box(PANEL, BORDER, 12));
        p.setPadding(dp(14), dp(12), dp(14), dp(14));
        p.addView(tv(title, 12, CYAN, true));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.setMargins(0, dp(12), 0, 0);
        parent.addView(p, l);
        return p;
    }

    private LinearLayout card(String title, int color, TextView[] out, int idx) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(PANEL, BORDER, 12));
        c.setPadding(dp(12), dp(8), dp(12), dp(10));
        TextView t = tv(title, 9, MUTED, true);
        TextView v = tv("00:00", 22, color, true);
        c.addView(t);
        c.addView(v);
        out[idx] = t;
        out[idx + 1] = v;
        return c;
    }

    private EditText num(int width) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        e.setTextColor(TEXT);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener(doneListener);
        e.setMinWidth(dp(width));
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { if (!loading) saveSettings(); }
        });
        return e;
    }

    private void addLabeled(LinearLayout parent, String label, View... views) {
        LinearLayout r = row();
        TextView l = tv(label, 13, MUTED, false);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        for (View v : views) r.addView(v);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, 0);
        parent.addView(r, p);
    }

    private Spinner spinner(String[] items, AdapterView.OnItemSelectedListener l) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setOnItemSelectedListener(l);
        return s;
    }

    private final AdapterView.OnItemSelectedListener saver = new AdapterView.OnItemSelectedListener() {
        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (!loading) saveSettings(); }
        @Override public void onNothingSelected(AdapterView<?> p) { }
    };

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        sv.setFocusableInTouchMode(true);
        rootView = sv;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        sv.addView(root);

        // header
        LinearLayout head = row();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        head.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(tv("locin", 26, TEXT, true));
        titles.addView(tv("STUDY FOCUS COMPANION", 10, MUTED, true));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        pill = tv("IDLE", 12, MUTED, true);
        pill.setBackground(box(PANEL2, BORDER, 8));
        pill.setPadding(dp(12), dp(5), dp(12), dp(5));
        head.addView(pill);
        root.addView(head);

        // HUD cards
        TextView[] c = new TextView[8];
        LinearLayout r1 = row(), r2 = row();
        r1.addView(card("SESSION TIME", CYAN, c, 0), lp(1, 0));
        r1.addView(card("NEXT REMINDER", GREEN, c, 2), lp(1, 0));
        r2.addView(card("REMINDERS SPOKEN", ORANGE, c, 4), lp(1, 6));
        r2.addView(card("FOCUSED TODAY", TEXT, c, 6), lp(1, 6));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(14), 0, 0);
        root.addView(r1, hp);
        root.addView(r2, new LinearLayout.LayoutParams(-1, -2));
        tTimer = c[1]; tNextTitle = c[2]; tNext = c[3]; tCount = c[5]; tToday = c[7];
        c[4].setText("REMINDERS SPOKEN");

        // controls
        LinearLayout ctl = panel(root, "CONTROLS");
        LinearLayout b1 = row(), b2 = row();
        bStart = btn("START", GREEN, v -> beginSession());
        bPause = btn("PAUSE", ORANGE, v -> send(FocusService.A_PAUSE, null));
        bStop = btn("STOP", RED, v -> send(FocusService.A_STOP, null));
        bSnooze = btn("SNOOZE", TEXT, v -> send(FocusService.A_SNOOZE, null));
        bTest = btn("TEST VOICE", CYAN, v -> testVoice());
        b1.addView(bStart, lp(1, 0)); b1.addView(bPause, lp(1, 0)); b1.addView(bStop, lp(1, 0));
        b2.addView(bSnooze, lp(1, 6)); b2.addView(bTest, lp(1, 6));
        ctl.addView(b1, new LinearLayout.LayoutParams(-1, -2));
        ctl.addView(b2, new LinearLayout.LayoutParams(-1, -2));

        // timing
        LinearLayout tm = panel(root, "TIMING");
        eMin = num(56); eSec = num(56);
        addLabeled(tm, "Remind every (min / sec)", eMin, eSec);
        spSession = spinner(Store.SESSIONS, saver);
        addLabeled(tm, "Session length", spSession);
        cPomo = new CheckBox(this);
        cPomo.setText("Pomodoro mode (focus / break)");
        cPomo.setTextColor(TEXT);
        cPomo.setOnCheckedChangeListener((v, on) -> { eFocus.setEnabled(on); eBreak.setEnabled(on); if (!loading) saveSettings(); });
        tm.addView(cPomo);
        eFocus = num(56); eBreak = num(56);
        addLabeled(tm, "Focus / break (min)", eFocus, eBreak);
        eSnooze = num(56);
        addLabeled(tm, "Snooze for (min)", eSnooze);

        // voice
        LinearLayout vc = panel(root, "VOICE");
        tRate = tv("", 12, CYAN, true);
        sbRate = new SeekBar(this);
        sbRate.setMax(150);
        vc.addView(tv("Speech speed", 13, MUTED, false));
        vc.addView(sbRate);
        vc.addView(tRate);
        tVol = tv("", 12, GREEN, true);
        sbVol = new SeekBar(this);
        sbVol.setMax(100);
        vc.addView(tv("Voice volume", 13, MUTED, false));
        vc.addView(sbVol);
        vc.addView(tVol);
        SeekBar.OnSeekBarChangeListener sl = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { showSeek(); if (!loading) saveSettings(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        };
        sbRate.setOnSeekBarChangeListener(sl);
        sbVol.setOnSeekBarChangeListener(sl);
        cAnnounce = new CheckBox(this);
        cAnnounce.setText("Announce session start, break and finish");
        cAnnounce.setTextColor(TEXT);
        cAnnounce.setOnCheckedChangeListener((v, on) -> { if (!loading) saveSettings(); });
        vc.addView(cAnnounce);
        vc.addView(tv("The voice itself comes from your phone's text-to-speech settings.", 11, MUTED, false));
        Button bat = btn("BATTERY SETTINGS (keep reminders reliable)", ORANGE, v -> {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception e) { toast("Open Settings > Battery and allow locin to run in the background."); }
        });
        vc.addView(bat, lp(0, 10));

        // voice lines
        LinearLayout vl = panel(root, "VOICE LINES");
        spMode = spinner(Store.MODES, saver);
        addLabeled(vl, "Play mode", spMode);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setBackgroundColor(0xFF0B111B);
        vl.addView(rows, lp(0, 8));
        info = tv("", 11, MUTED, false);
        vl.addView(info);
        eLine = new EditText(this);
        eLine.setHint("Type a voice line");
        eLine.setHintTextColor(MUTED);
        eLine.setTextColor(TEXT);
        eLine.setSingleLine(true);
        eLine.setImeOptions(EditorInfo.IME_ACTION_DONE);
        eLine.setOnEditorActionListener(doneListener);
        vl.addView(eLine, lp(0, 6));
        LinearLayout e1 = row(), e2 = row();
        e1.addView(btn("ADD", GREEN, v -> addPhrase()), lp(1, 4));
        e1.addView(btn("UPDATE", CYAN, v -> updatePhrase()), lp(1, 4));
        e1.addView(btn("DELETE", RED, v -> deletePhrase()), lp(1, 4));
        bReset = btn("RESET ALL", ORANGE, v -> resetPhrases());
        e2.addView(btn("SET ACTIVE", GREEN, v -> setActive()), lp(1, 4));
        e2.addView(btn("SPEAK", CYAN, v -> speakSelected()), lp(1, 4));
        e2.addView(bReset, lp(1, 4));
        vl.addView(e1, new LinearLayout.LayoutParams(-1, -2));
        vl.addView(e2, new LinearLayout.LayoutParams(-1, -2));

        setContentView(sv);
    }

    // ------------------------------------------------------------------ settings
    private void loadValues() {
        eMin.setText(String.valueOf(st.getInt("min", 2)));
        eSec.setText(String.valueOf(st.getInt("sec", 0)));
        eFocus.setText(String.valueOf(st.getInt("focus", 25)));
        eBreak.setText(String.valueOf(st.getInt("break", 5)));
        eSnooze.setText(String.valueOf(st.getInt("snooze", 5)));
        spSession.setSelection(Math.max(0, Math.min(Store.SESSIONS.length - 1, st.getInt("session", 0))));
        spMode.setSelection(Math.max(0, Math.min(Store.MODES.length - 1, st.getInt("mode", 0))));
        boolean pomo = st.getBool("pomo", false);
        cPomo.setChecked(pomo);
        eFocus.setEnabled(pomo);
        eBreak.setEnabled(pomo);
        cAnnounce.setChecked(st.getBool("announce", true));
        sbRate.setProgress(Math.max(0, Math.min(150, st.getInt("rate", 100) - 50)));
        sbVol.setProgress(Math.max(0, Math.min(100, st.getInt("volume", 100))));
        showSeek();
    }

    private void showSeek() {
        tRate.setText((sbRate.getProgress() + 50) + "% speed");
        tVol.setText(sbVol.getProgress() + "% volume");
    }

    private int intOf(EditText e, int d) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception x) { return d; }
    }

    private void saveSettings() {
        if (loading || st == null) return;
        st.putInt("min", intOf(eMin, 0));
        st.putInt("sec", intOf(eSec, 0));
        st.putInt("focus", intOf(eFocus, 25));
        st.putInt("break", intOf(eBreak, 5));
        st.putInt("snooze", intOf(eSnooze, 5));
        st.putInt("session", spSession.getSelectedItemPosition());
        st.putInt("mode", spMode.getSelectedItemPosition());
        st.putBool("pomo", cPomo.isChecked());
        st.putBool("announce", cAnnounce.isChecked());
        st.putInt("rate", sbRate.getProgress() + 50);
        st.putInt("volume", sbVol.getProgress());
    }

    // ------------------------------------------------------------------ session buttons
    private void send(String action, String text) {
        Intent i = new Intent(this, FocusService.class).setAction(action);
        if (text != null) i.putExtra("text", text);
        startForegroundService(i);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    private void beginSession() {
        saveSettings();
        int secs = intOf(eSec, 0), mins = intOf(eMin, 0);
        int total = mins * 60 + secs;
        if (secs > 59 || total < 5 || total > 10800) { toast("Reminder interval must be between 5 seconds and 180 minutes."); return; }
        if (cPomo.isChecked()) {
            int f = intOf(eFocus, 0), b = intOf(eBreak, 0);
            if (f < 1 || f > 180 || b < 1 || b > 60) { toast("Pomodoro: focus must be 1-180 min and break 1-60 min."); return; }
        }
        if (phrases.isEmpty()) { toast("Add at least one voice line first."); return; }
        send(FocusService.A_START, null);
    }

    private void testVoice() {
        saveSettings();
        if (phrases.isEmpty()) { toast("Add at least one voice line first."); return; }
        send(FocusService.A_TEST, active);
    }

    // ------------------------------------------------------------------ HUD
    private void setEn(Button b, boolean on) { b.setEnabled(on); b.setAlpha(on ? 1f : 0.35f); }

    private void updateHud() {
        long now = SystemClock.elapsedRealtime();
        int s = Session.state;
        tTimer.setText(Session.fmt(Session.elapsedMs / 1000));
        String title = "NEXT REMINDER";
        long val;
        if (s == 0) {
            val = Math.min(10800, Math.max(5, intOf(eMin, 0) * 60 + intOf(eSec, 0)));
        } else {
            long ref = s == 2 ? Session.pausedAt : now;
            long target = Session.breakPhase ? Session.phaseEnd : Session.nextAt;
            if (Session.breakPhase) title = "BREAK ENDS IN";
            val = Math.max(0, (target - ref) / 1000);
        }
        tNextTitle.setText(title);
        tNext.setText(Session.fmt(val));
        tCount.setText(String.valueOf(Session.count));
        tToday.setText(Session.fmt((s == 0 ? st.todayMs() : Session.todayMs) / 1000));
        boolean idle = s == 0;
        setEn(bStart, idle);
        setEn(bPause, !idle);
        setEn(bStop, !idle);
        setEn(bSnooze, s == 1 && !Session.breakPhase);
        bPause.setText(s == 2 ? "RESUME" : "PAUSE");
        if (idle) { pill.setText("IDLE"); pill.setTextColor(MUTED); }
        else if (s == 2) { pill.setText("PAUSED"); pill.setTextColor(ORANGE); }
        else if (Session.breakPhase) { pill.setText("BREAK"); pill.setTextColor(CYAN); }
        else { pill.setText("FOCUS"); pill.setTextColor(GREEN); }
    }

    // ------------------------------------------------------------------ voice lines manager
    private void refreshRows() {
        rows.removeAllViews();
        for (int i = 0; i < phrases.size(); i++) {
            final int idx = i;
            String p = phrases.get(i);
            TextView t = tv((p.equals(active) ? "\u25CF  " : "     ") + p, 14, TEXT, false);
            t.setPadding(dp(10), dp(10), dp(10), dp(10));
            t.setBackgroundColor(i == sel ? 0xFF1D3A52 : 0x00000000);
            t.setOnClickListener(v -> { sel = idx; eLine.setText(phrases.get(idx)); refreshRows(); });
            rows.addView(t);
        }
        info.setText(phrases.size() + " line(s)  \u2022  \u25CF = active line  \u2022  tap a line to edit it");
    }

    private void persist() {
        st.savePhrases(phrases);
        st.setActive(active);
        refreshRows();
    }

    private String clean() {
        String t = eLine.getText().toString().trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) { toast("Type a voice line first."); return null; }
        if (t.length() > MAX_LEN) { toast("Line too long (" + t.length() + "). Keep it under " + MAX_LEN + " characters."); return null; }
        return t;
    }

    private void addPhrase() {
        String t = clean();
        if (t == null) return;
        if (phrases.contains(t)) { toast("That line already exists."); return; }
        phrases.add(t);
        if (active.isEmpty()) active = t;
        sel = phrases.size() - 1;
        eLine.setText("");
        persist();
        toast("Voice line added.");
    }

    private void updatePhrase() {
        if (sel < 0 || sel >= phrases.size()) { toast("Tap a line in the list first, edit it, then press UPDATE."); return; }
        String t = clean();
        if (t == null) return;
        if (!t.equals(phrases.get(sel)) && phrases.contains(t)) { toast("Another line already has that text."); return; }
        if (phrases.get(sel).equals(active)) active = t;
        phrases.set(sel, t);
        persist();
        toast("Voice line updated.");
    }

    private void deletePhrase() {
        if (sel < 0 || sel >= phrases.size()) { toast("Tap a line to delete."); return; }
        String removed = phrases.remove(sel);
        if (removed.equals(active)) active = phrases.isEmpty() ? "" : phrases.get(Math.min(sel, phrases.size() - 1));
        sel = phrases.isEmpty() ? -1 : Math.min(sel, phrases.size() - 1);
        eLine.setText("");
        persist();
        toast("Voice line deleted.");
    }

    private void setActive() {
        if (sel < 0 || sel >= phrases.size()) { toast("Tap a line to make it active."); return; }
        active = phrases.get(sel);
        persist();
        toast("Active line set.");
    }

    private void speakSelected() {
        String t = sel >= 0 && sel < phrases.size() ? phrases.get(sel) : eLine.getText().toString().trim();
        if (t.isEmpty()) { toast("Tap a line (or type one) to preview."); return; }
        saveSettings();
        send(FocusService.A_TEST, t);
    }

    private void resetPhrases() {
        if (!resetArmed) {
            resetArmed = true;
            bReset.setText("SURE? TAP AGAIN");
            h.postDelayed(() -> { resetArmed = false; bReset.setText("RESET ALL"); }, 3000);
            return;
        }
        resetArmed = false;
        bReset.setText("RESET ALL");
        phrases = new ArrayList<>(java.util.Arrays.asList(Store.DEFAULTS));
        active = phrases.get(0);
        sel = -1;
        eLine.setText("");
        persist();
        toast("Voice lines reset to defaults.");
    }
}
