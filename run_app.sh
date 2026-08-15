#!/bin/bash
set -e
cd "$(dirname "$0")"
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ~/Desktop/woojintoday-debug.apk
ADB=~/Library/Android/sdk/platform-tools/adb
"$ADB" install -r ~/Desktop/woojintoday-debug.apk
"$ADB" shell am force-stop com.daejin.woojintoday
"$ADB" shell am start -n com.daejin.woojintoday/com.daejin.woojintoday.MainActivity
echo "설치 및 실행 완료"
