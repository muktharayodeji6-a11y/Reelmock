#!/usr/bin/env bash
# Simulates the Android share sheet with adb. Phone: USB debugging on, `adb devices` shows it.
PKG=com.leejolt.reelmock
share() { adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'$1'" -n $PKG/.MainActivity; sleep 2; }
echo "1. valid reel";        share "https://www.instagram.com/reel/ABC123/"
echo "2. with ?igsh=";       share "https://www.instagram.com/reel/C8xYz_9-AbC/?igsh=MWx0eXk"
echo "3. caption + link";    share "Look at this https://www.instagram.com/reel/XYZ789/"
echo "4. INVALID (host)";    share "https://www.youtube.com/reel/ABC123/"
echo "5. INVALID (no link)"; share "just some text"
echo "6. INVALID (spoof)";   share "https://instagram.com.evil.com/reel/ABC123/"
echo "7. repeat same URL";   share "https://www.instagram.com/reel/ABC123/"
echo "Persistence: force-stop then relaunch"
adb shell am force-stop $PKG; adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null
