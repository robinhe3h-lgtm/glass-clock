# iOS Clock Wallpaper

A live wallpaper for Android that draws the iOS 26 style lock screen clock:
small date on top, huge tall glass-like digits, and mountains in front.

## Build and install

1. Install **Android Studio** (free).
2. Unzip this project, then **File → Open** and pick the `IOSClockWallpaper` folder.
3. Wait for Gradle sync to finish (first time takes a few minutes).
4. Turn on **Developer options → USB debugging** on your phone and plug it in.
5. Press the green **Run** button. The app installs on your phone.
6. Open the app, pick a color, tap **Set as wallpaper**, and choose **Lock screen** (or both).

To get an APK to share: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## How the look is made

- The digits are drawn by the app itself (`TallDigits.kt`), so no font file is needed.
- The glass look is a see-through gradient: dim at the top, bright at the bottom.
- Mountains are drawn on top so they cut off the bottom of the digits.

## Tweaks (in `ClockWallpaperService.kt`, `drawClock`)

- Taller or shorter clock: change `dh = h * 0.40f`.
- Wider or narrower: change `w * 0.86f`.
- Clock position: change `top = h * 0.105f`.
- Thicker or thinner strokes: change `0.265f` in `TallDigits.strokeWidth`.
- Colors and mountains: `Presets.kt`.

## Notes

- The date uses your phone's bold font. To use another, put a font file at
  `app/src/main/assets/fonts/clock.ttf`.
- Your phone's built-in lock screen clock may still show on top. Hide it in
  Settings → Lock screen → Clock style (name varies by brand).
- On Android 14+ the clock appears only on the lock screen; the home screen stays clean.
  On older versions the clock shows on both if you set the wallpaper for both.
