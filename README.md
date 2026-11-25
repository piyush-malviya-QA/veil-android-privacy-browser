# Veil — Android Privacy Browser

A privacy-focused Android browser built on Mozilla GeckoView.

## Build an APK locally (Android Studio / JDK 17)
1. Open the folder in **Android Studio** (JDK 17) and let Gradle sync.
2. Run **Build > Build Bundle(s) / APK(s) > Build APK(s)** or execute:
   ```bash
   gradle :app:assembleDebug
   ```
3. Install `app/build/outputs/apk/debug/app-debug.apk` on your Android device.

## Build in the cloud (GitHub Actions)
Run the **Build Android APK** workflow under **Actions**, then download the `veil-debug-apk` artifact.
