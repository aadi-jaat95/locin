locin for Android (v1.0)  -  Android 8.0 or newer

You need an APK file. Two ways to get it (pick one):

A) WITHOUT Android Studio (easiest) - GitHub builds it for you
   1. Make a free GitHub account, create a new repository.
   2. Upload ALL files of this folder (the hidden ".github" folder must be included).
   3. Open the "Actions" tab > "Build locin APK" > Run workflow (it also runs on every upload).
   4. When it turns green (a few minutes), open the run and download the "locin-apk" file.
      Unzip it: inside is locin.apk.

B) WITH Android Studio
   1. File > Open > choose this folder, wait for Gradle sync.
   2. Build > Build Bundle(s) / APK(s) > Build APK(s).
   3. APK: app/build/outputs/apk/debug/app-debug.apk

INSTALL ON PHONE
   - Copy the APK to the phone and open it. Allow "Install unknown apps" for your file manager/browser.
   - First launch: allow notifications.
   - Tap "BATTERY SETTINGS" inside locin and set locin to Unrestricted / Don't optimize,
     otherwise some phones (Xiaomi, Oppo, Vivo, Samsung) may stop reminders when the screen is off.

NOTES
   - The voice is your phone's text-to-speech (change it in Android Settings > Text-to-speech).
     Needs "Speech Services by Google" or another TTS engine installed.
   - This is a debug-signed APK, fine for personal use and sharing.
   - Same features as Windows locin: interval, voice lines (add/edit/delete), 3 play modes,
     Pomodoro, snooze, pause/resume, today's focus time.
