# Clean
./gradlew clean

# Builds the debug APK using the project’s Gradle wrapper
./gradlew assembleDebug

# Connects ADB to the Fire TV over Wi-Fi using its IP address
adb connect 192.168.6.25

# Clears all existing logcat logs on the Fire TV
adb logcat -c

# Deletes the previous crash log file to avoid mixing old logs
rm firetv-crash.log

# Starts capturing device logs with timestamps and saves them to a file
# captures logs from all apps and the system
adb logcat -v time > firetv-crash.log
adb logcat -s "DiokkoPlayer" "OkHttp" "M3UParser" "Retrofit" | grep -i "511\|error\|exception"
adb logcat --pid=$(adb shell pidof -s com.diokko.player.debug)
adb logcat -d | grep -E "PlaylistRepo|OkHttp|HTTP|M3UParser|Error|Exception|511" | tail -100

# Android has multiple log buffers
# (main, system, events, crash, radio, vitals, metrics)
# Dump everything currently in the log `main, crash, system` buffers
adb logcat -v time -d > firetv-crash.log

# Dumping all of them increases your chances of catching the crash.
adb logcat -v time -b all -d > firetv-crash.log

# Dump specific buffers
adb logcat -v time -b main,system,crash -d

# Installs (or reinstalls) the debug APK onto the Fire TV
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check installed apps packages
$ adb shell pm list packages
package:com.diokko.player.debug

# Launches the app by triggering its main launcher activity
```sh
adb shell monkey -p com.diokko.player.debug -c android.intent.category.LAUNCHER 1  # send a launch intent like tapping the app icon
adb shell am start -n com.diokko.player/.player.VideoPlayerActivity
```





adb shell pidof com.diokko.firetvplayer
adb logcat -v time --pid=<pid> firetv-crash.log



cat firetv-crash.log | grep -i "diokko\|com.diokko"
cat firetv-crash.log | grep -i "compose\|AndroidRuntime\|FATAL"


Android Gradle plugin version 8.7.3 has an upgrade available. Start the AGP Upgrade Assistant to update this project's AGP version.





# =========================
# Android / Kotlin / Gradle
# =========================

# Gradle files
.gradle/
build/
**/build/

# Local Gradle cache
.gradle/
local.properties

# Android Studio
.idea/
*.iml
*.hprof

# Kotlin
*.kotlin_module

# Android generated files
captures/
output.json
navigation/

# Android Studio Navigation editor temp files
*.nav

# External native build
.externalNativeBuild/

# Google Services / Firebase
google-services.json
*.keystore

# Log files
*.log

# MacOS
.DS_Store

# Windows
Thumbs.db
desktop.ini

# VS Code
.vscode/

# Firebase crashlytics
crashlytics-build.log

# JetBrains
.idea/
*.iws
*.ipr

# NDK
obj/
.cxx/




12-25 16:46:36.680 W/M3UParser(14789): Processed 2610000 lines, 1304999 entries (CH:45430 MV:187511 SR:1065191)...
12-25 16:46:37.178 W/PlaylistRepo(14789): All groups processed: 1294 total
12-25 16:46:37.180 W/M3UParser(14789): === Streaming Parse Complete ===
12-25 16:46:37.180 W/M3UParser(14789): Processed 2610509 lines
12-25 16:46:37.180 W/M3UParser(14789): Total entries: 1305254
12-25 16:46:37.180 W/M3UParser(14789): Channels: 45430
12-25 16:46:37.180 W/M3UParser(14789): Movies: 187511
12-25 16:46:37.180 W/M3UParser(14789): Series: 1065446
12-25 16:46:37.180 W/M3UParser(14789): Groups: 1294
12-25 16:46:37.181 W/PlaylistRepo(14789): === Refresh Complete ===



M3U Format:
```
#EXTM3U
#EXTINF:-1 tvg-id="" tvg-name="#####  FAVORITES #####" tvg-logo="" group-title="SWEDEN HD & HEVC",#####  FAVORITES #####
http://line.yo-ott.com:80/4462c4c019/28bb86ec64b9/333985
#EXTINF:-1 tvg-id="SVT1.se" tvg-name="SWE| SVT 1" tvg-logo="http://picons.cmshulk.com/picons/79662.png" group-title="SWEDEN HD & HEVC",SWE| SVT 1
http://line.yo-ott.com:80/4462c4c019/28bb86ec64b9/79662
#EXTINF:-1 tvg-id="SVT1.se" tvg-name="SWE| SVT 1 HD" tvg-logo="http://picons.cmshulk.com/picons/309562.png" group-title="SWEDEN HD & HEVC",SWE| SVT 1 HD
http://line.yo-ott.com:80/4462c4c019/28bb86ec64b9/309547
#EXTINF:-1 tvg-id="" tvg-name="##### MOVIES #####" tvg-logo="" group-title="SWEDEN HD & HEVC",##### MOVIES #####
http://line.yo-ott.com:80/4462c4c019/28bb86ec64b9/333986
#EXTINF:-1 tvg-id="SFkanalen.se" tvg-name="SWE| SF-KANALEN" tvg-logo="http://picons.cmshulk.com/picons/79955.png" group-title="SWEDEN HD & HEVC",SWE| SF-KANALEN
http://line.yo-ott.com:80/4462c4c019/28bb86ec64b9/79955
#EXTINF:-1 tvg-id="" tvg-name="SWE| SKY SHOWTIME 1 HD" tvg-logo="http://picons.cmshulk.com/picons/965340.png" group-title="SWEDEN HD & HEVC",SWE| SKY SHOWTIME 1 HD
```



(group-title="SWEDEN HD & HEVC",SWE| SVT 1) this portion is a group of "SWEDEN HD & HEVC" and the channel name is "SWE| SVT 1". And tvg-logo is a logo for the channel.
(tvg-name="#####  FAVORITES #####") this is the first section is within the channel. It should be a bar to seperate the different channel types within the channel groups. It should not be clickable and a divider.

Live tv is good, but the Movies and Series dont seem to always display on the channels. Please refine the playlist parsing using the following guides. The movies URL's have "/movie/" and the series URL's has "/series/". The Live TV has random characters (4462c4c019) like "http://line.yo-ott.com:80/4462c4c019/28bb86ec64b9/333985". Parse it in a way that if it has these it is put in the correct category.

- This is a movie (URL has /movie/)
  http://line.yo-ott.com:80/movie/4462c4c019/28bb86ec64b9/1350286.mkv
  #EXTINF:-1 tvg-id="" tvg-name="A+ - Billie Eilish: The World's A Little Blurry (2021)" tvg-logo="https://image.tmdb.org/t/p/w600_and_h900_bestv2/bDQ95W5LPHW9FHlPj3QX3jvM9Z7.jpg" group-title="APPLE+ MOVIES",A+ - Billie Eilish: The World's A Little Blurry (2021)


- This is a Series (URL has /series/)
  http://line.yo-ott.com:80/series/4462c4c019/28bb86ec64b9/1364511.mkv
  #EXTINF:-1 tvg-id="" tvg-name="NF - Kaala Paani (2023) S01 E05" tvg-logo="https://image.tmdb.org/t/p/w185/2xgDPsf2yFfgrsK1E0UEMdnwc3I.jpg" group-title="|MULTI| NETFLIX ASIA",NF - Kaala Paani (2023) S01 E05





- Taking Screen shots
```sh
abdou@My_Notebook MINGW64 ~/workspace/apps/diokko-player-v5 (test3)
$ adb shell
brandenburg:/ $ screencap -p /sdcard/test1.png                                                                                                                                     
Init wrapper sys mutex successful. Pid:20411
brandenburg:/ $ exit

abdou@My_Notebook MINGW64 ~/workspace/apps/diokko-player-v5 (test3)
$ adb shell ls -lh //sdcard/test1.png
-rw-rw---- 1 root sdcard_rw 1.1M 2025-12-26 07:15 //sdcard/test1.png


abdou@My_Notebook MINGW64 ~/workspace/apps/diokko-player-v5 (test3)
$ adb pull //sdcard/test1.png
//sdcard/test1.png: 1 file pulled, 0 skipped. 8.9 MB/s (1250329 bytes in 0.134s)


abdou@My_Notebook MINGW64 ~/workspace/apps/diokko-player-v5 (test3)
$ adb shell rm //sdcard/test1.png
```


- Download playlist
  curl -L \
  "http://line.yo-ott.com/get.php?username=4462c4c019&password=28bb86ec64b9&type=m3u_plus&output=ts" \
  -o playlist.m3u



# EPG
curl "http://line.yo-ott.com:80/xmltv.php?username=4462c4c019&password=28bb86ec64b9" -o epg.xml





┌──────────────────────────────────────────────────────┐
│  19:00     19:30     20:00     20:30     21:00       │  ← Time bar
├─────────────┬──────────┬─────────┬─────────┬─────────┤
│ [LOGO] AZ   │ News     │ News    │ Debate  │ Live    │
│ [LOGO] BBC  │ Movie    │ Movie   │ Movie   │ Movie   │
│ [LOGO] CNN  │ Report   │ Report  │ Docu    │ Talk    │
│ [LOGO] ESPN │ Match    │ Match   │ Analysis│ Sports  │
├─────────────┴──────────┴─────────┴─────────┴─────────┤
│  ▲▼ Channel  ◀▶ Time     OK = Info     Back = Exit  │
└──────────────────────────────────────────────────────┘



# Database viewing
```shell
# Enter the FireTV shell
adb shell

# Switch to your app's identity
run-as com.diokko.player.debug

# Copy the database to the public /sdcard/ folder
# (Change 'diokko.db' to whatever name you used in your Room setup)
cp databases/diokko_database /sdcard/diokko_database

# Exit the app's identity
exit

# Exit the FireTV shell
exit

adb pull //sdcard/diokko_database diokko_database.sqlite
adb shell rm //sdcard/diokko_database
```

# Show group titles
```sh
$ awk -F'group-title="' '{ if (NF>1) { split($2,a,"\""); print a[1] } }' ./playlist.m3u | grep -i adult | sort -u
```