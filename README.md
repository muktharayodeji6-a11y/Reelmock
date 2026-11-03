# ReelMock (Android, Kotlin + WebView)

Demo app that receives Instagram Reel links two ways, validates them, extracts the Reel ID, stores them
locally, and shows them in a bundled dark HTML UI marked DEMONSTRATION MODE.

1. **Android Share sheet** (`ACTION_SEND`, `text/plain`)
2. **Android URL handling** (`ACTION_VIEW`, https links to instagram.com / www.instagram.com / m.instagram.com
   under `/reel/`, `/reels/`, `/<username>/reel/`) when the user has chosen ReelMock to open such links

The UI shows which path each link came in on ("VIA ANDROID SHARE" / "VIA URL HANDLER (ACTION_VIEW)").

- Package `com.leejolt.reelmock`, minSdk 26, target/compile SDK 34 (Android 14)
- No INTERNET permission, no server, no Instagram login or credentials. WebView has `blockNetworkLoads`,
  no file access, and a strict CSP. The video area is a generated animation, clearly labelled as mock.
- **No `autoVerify`.** ReelMock does not own instagram.com and cannot be a verified App Link handler.
  The manifest filter is only a pre-filter; `ReelParser` does the real validation (host allow-list,
  path, ID format; lookalike domains and `user@host` tricks are rejected).
- **"Open original link"** uses the system chooser with ReelMock excluded (`EXTRA_EXCLUDE_COMPONENTS`),
  so it can never loop back into ReelMock. Android still decides which installed app/browser may open it.

## Important Android limitations (read before testing)
- On Android 12+ a generic web link only goes to an app that is *verified* for the domain or *approved by
  the user* in Settings > Apps > ReelMock > Open by default. An unapproved ReelMock is not offered at all;
  the link goes to the browser (or Instagram).
- If Instagram is installed, its verified App Links normally take precedence. Automatic interception of
  Instagram links is **not guaranteed**, and OEM skins (Vivo Funtouch) may differ.
- The Share sheet path always works, regardless of any of this.

## Layout
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/leejolt/reelmock/
    MainActivity.kt   Activity + WebView; adapts Intent -> LinkIntake; safe openOriginal()
    LinkIntake.kt     pure Kotlin: SEND/VIEW handling + IntakeSession lifecycle rules (JVM-testable)
    ReelParser.kt     pure Kotlin URL validator / Reel ID extractor (unchanged)
    ReelStore.kt      SharedPreferences storage (+ source: share/view)
app/src/main/assets/index.html        <- HTML/CSS/JS UI
app/src/test/.../{ReelParserTest,LinkIntakeTest,ManifestTest}.kt
.github/workflows/build.yml           <- builds the APK in the cloud (unchanged)
scripts/test_share.sh, scripts/test_view.sh, TESTING.md
```

## Build the APK

### A) GitHub Actions (no PC, no Android Studio)
1. Create a GitHub repo and push this folder's **contents** (so `.github/` is at the repo root) to `main`.
2. Repo -> **Actions** -> "Build ReelMock APK" (runs on push; or "Run workflow").
3. Open the finished run -> **Artifacts** -> `ReelMock-debug-apk` -> download, unzip -> `app-debug.apk`.
4. Install it (allow "install unknown apps" for your browser/file manager).

### B) Android Studio
File -> Open -> this folder -> let Gradle sync (JDK 17) -> Build -> Build APK(s).
APK: `app/build/outputs/apk/debug/app-debug.apk`.

### C) Command line
Install JDK 17, Android SDK (platform 34), Gradle 8.7, set `ANDROID_HOME`, then:
`gradle testDebugUnitTest assembleDebug`

The debug APK is signed with the auto-generated debug key, which is installable. For Play Store you'd need a release keystore.
