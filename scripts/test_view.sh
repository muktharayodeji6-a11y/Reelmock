#!/usr/bin/env bash
# ACTION_VIEW (URL handler) tests via adb. Phone: USB debugging on, `adb devices` shows it.
# Cases 1-9 target ReelMock explicitly (-n), so they test ReelMock's own handling and work
# regardless of Android's link-approval state. Case 10 does NOT use -n: it asks Android to
# resolve the link and shows who really gets it on this phone (may be Instagram or the browser).
PKG=com.leejolt.reelmock
view()  { adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE \
  -d "'$1'" -n $PKG/.MainActivity; sleep 2; }
echo "0. cold start check"; adb shell am force-stop $PKG
echo "1. valid reel (COLD START)";   view "https://www.instagram.com/reel/ABC123/"
echo "2. with ?igsh= (RUNNING)";     view "https://www.instagram.com/reel/C8xYz_9-AbC/?igsh=MWx0eXk"
echo "3. m.instagram.com";           view "https://m.instagram.com/reel/MOBIL3/"
echo "4. /reels/";                   view "https://instagram.com/reels/REELS9/"
echo "5. /user/reel/";               view "https://www.instagram.com/someuser/reel/USER77/"
echo "6. INVALID (post path)";       view "https://www.instagram.com/p/ABC123/"
echo "7. INVALID (lookalike)";       view "https://instagram.com.evil.com/reel/ABC123/"
echo "8. INVALID (http)";            view "http://www.instagram.com/reel/ABC123/"
echo "9. Share still works"
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'Look https://www.instagram.com/reel/SHARE55/'" -n $PKG/.MainActivity; sleep 2
echo "10. Android resolution (no -n) - see who opens:"
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE \
  -d "'https://www.instagram.com/reel/RESOLV1/'"; sleep 2
echo; echo "Link approval state (Android 12+):"; adb shell pm get-app-links $PKG
echo "Simulate the user approving ReelMock for a domain:"
echo "  adb shell pm set-app-links-user-selection --user 0 --package $PKG true www.instagram.com instagram.com m.instagram.com"
echo "Undo: adb shell pm set-app-links-user-selection --user 0 --package $PKG false www.instagram.com instagram.com m.instagram.com"
