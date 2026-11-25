# Veil — Android Privacy Browser (Firebase Enabled)

## 1. Connect your Firebase Android config
This project is pre-wired to your provisioned Firebase project (`gen-lang-client-0078851774`) and Cloud Firestore database (`ai-studio-veilandroidpriva-79384458-5581-46fb-a992-b7e14a2ff883`).

1. Open [Firebase Console](https://console.firebase.google.com/project/gen-lang-client-0078851774/settings/general) and click **Add app → Android**.
2. Enter package name: `com.veil.browser`.
3. Download `google-services.json` and place it at `app/google-services.json`.
   - Gradle automatically activates the `com.google.gms.google-services` plugin as soon as `app/google-services.json` is present.

## 2. Build an APK locally (Android Studio / JDK 17)
1. Unzip `veil-android-privacy-browser.zip` and open the folder in **Android Studio** (JDK 17).
2. Let Gradle sync GeckoView, `tor-android:0.4.9.17`, and the Firebase Android BoM (`firebase-auth`, `firebase-firestore`).
3. Run **Build > Build Bundle(s) / APK(s) > Build APK(s)** or execute:
   ```bash
   gradle :app:assembleDebug
   ```
4. Install `app/build/outputs/apk/debug/app-debug.apk` on your Android device.

## 3. Build in the cloud (GitHub Actions + Optional Firebase App Distribution)
1. Base64-encode your `google-services.json` (`base64 -w 0 app/google-services.json`) and save it as a repository secret named `GOOGLE_SERVICES_JSON_B64`.
2. Push this folder to GitHub and run the **Build Android APK (Firebase Enabled)** workflow under **Actions**.
3. Download `veil-debug-apk` (or uncomment the Firebase App Distribution step to push directly to testers).
