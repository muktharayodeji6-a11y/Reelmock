# Testing ReelMock

## Automated (JVM, no device): `gradle testDebugUnitTest`
- `ReelParserTest` - valid IDs, www / m. hosts, query strings + fragments, captions, user-prefixed paths,
  invalid input, lookalike domains, unsupported Instagram paths.
- `LinkIntakeTest` - ACTION_SEND compatibility; ACTION_VIEW parsing (https only, query preserved, errors shown);
  VIEW cold start, VIEW while running (`onNewIntent`), no re-processing on recreation.
  These test the pure `LinkIntake`/`IntakeSession` logic that `MainActivity` delegates to; the Activity itself
  is not instrumented (see device tests below).
- `ManifestTest` - VIEW filter (DEFAULT + BROWSABLE, https only, 3 hosts, `/reel/`, `/reels/`, `/*/reel/*`),
  no `autoVerify`, MAIN/LAUNCHER + SEND filters intact, no permissions.

## adb scripts (phone, USB debugging)
- `scripts/test_share.sh` - ACTION_SEND cases.
- `scripts/test_view.sh` - ACTION_VIEW cases (cold start, running, invalid, lookalike, http, Share regression,
  plus one un-targeted resolution test and `pm get-app-links`).

## On-device checklist
| # | Scenario | Expected |
|---|----------|----------|
| 1 | Share a Reel link to ReelMock | Green "URL RECEIVED" + violet "VIA ANDROID SHARE" |
| 2 | Open a Reel link through ReelMock (VIEW) | "URL RECEIVED" + "VIA URL HANDLER (ACTION_VIEW)" |
| 3 | Reel ID / URL | `.../reel/ABC123/?igsh=..` shows ID `ABC123`, full URL incl. query |
| 4 | Invalid / lookalike / non-https | Red "INVALID URL REJECTED" + reason; last valid reel stays stored |
| 5 | Link arrives while app open | `onNewIntent`; counter +1; history grows (max 10), each row tagged SHARE/LINK |
| 6 | Persistence | Force-stop, relaunch: last URL, ID, source and history still shown |
| 7 | Rotation | State kept, link NOT double-counted |
| 8 | "Open original link" | System chooser listing Instagram/browsers, **not** ReelMock; no loop |
| 9 | Offline | Airplane mode: everything works (no INTERNET permission) |

## Real-device link-handling scenarios (Android 12+; results can differ by OEM)
Android does not let an app claim another company's domain by declaring it. Behaviour is decided by
domain verification and the user's "Open by default" choices. Nothing below is guaranteed interception.

**A. Instagram uninstalled, ReelMock installed.** No verified owner exists. Tapping a Reel link opens the
browser unless the user has approved ReelMock (Settings > Apps > ReelMock > Open by default > add the 3 domains).
After approval ReelMock can open `/reel/`, `/reels/`, `/<user>/reel/` links directly or via a chooser with the
browser. Other instagram.com links (profiles, posts) still go to the browser.

**B. Instagram installed, ReelMock installed.** Instagram's own verified links normally win, so tapping
opens Instagram. ReelMock is usually not offered, even if approved. Do not expect interception.

**C. Both installed, ReelMock not the default handler.** Same as B: Instagram (or the browser) opens the link;
ReelMock never sees it. Use the Share sheet, or approve ReelMock in Open by default and check what happens.

**D. ReelMock explicitly selected.** Only possible if ReelMock is eligible (approved by the user for the domain)
and the system shows a chooser or you pick ReelMock in settings. Choosing it delivers `ACTION_VIEW` to ReelMock:
cold start -> `onCreate`, already running -> `onNewIntent`; the UI shows "VIA URL HANDLER (ACTION_VIEW)".
"Always"/default options in the chooser may be limited for web links on Android 12+.

To test D without relying on the phone's UI: `adb shell pm set-app-links-user-selection --user 0 --package com.leejolt.reelmock true www.instagram.com instagram.com m.instagram.com`
then run the un-targeted case 10 in `scripts/test_view.sh`.

## Status of verification
Unit tests above were compiled with kotlinc 1.9.24 and run with JUnit 4.13.2 on a JVM. The Android parts
(`MainActivity`, `ReelStore`, the HTML UI, manifest merge, Gradle/AGP build) were **not** compiled or run in
the authoring sandbox (no Android SDK / Maven access); they must be verified by the GitHub Actions build and on a device.
