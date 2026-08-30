@echo off
setlocal
set "PROJ=%~dp0"
set "TOOLS=%PROJ%..\android-tools"
set "JDK=C:\Program Files\Java\jdk-25"
set "SDK=%TOOLS%\sdk"
set "BT=%SDK%\build-tools\36.0.0"
set "AJAR=%SDK%\platforms\android-34\android.jar"
set "PATH=%JDK%\bin;%PATH%"
set "OUT=%PROJ%out"

if not exist "%OUT%" mkdir "%OUT%"
if not exist "%OUT%\classes" mkdir "%OUT%\classes"
if not exist "%OUT%\gen" mkdir "%OUT%\gen"

echo [1/6] aapt2 compile resources...
"%BT%\aapt2.exe" compile --dir "%PROJ%res" -o "%OUT%\res.zip"
if errorlevel 1 goto :err

echo [2/6] aapt2 link...
"%BT%\aapt2.exe" link -o "%OUT%\base.apk" -I "%AJAR%" --manifest "%PROJ%AndroidManifest.xml" -R "%OUT%\res.zip" --java "%OUT%\gen" --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34 --version-code 2 --version-name 1.1
if errorlevel 1 goto :err

echo [3/6] javac...
dir /s /b "%PROJ%src\*.java" "%OUT%\gen\*.java" > "%OUT%\sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "%AJAR%" -d "%OUT%\classes" "@%OUT%\sources.txt"
if errorlevel 1 goto :err

echo [4/6] d8 dex...
dir /s /b "%OUT%\classes\*.class" > "%OUT%\dexlist.txt"
call "%BT%\d8.bat" --release --lib "%AJAR%" --min-api 26 --output "%OUT%" "@%OUT%\dexlist.txt"
if not exist "%OUT%\classes.dex" goto :err

echo [5/6] package + zipalign...
jar uf "%OUT%\base.apk" -C "%OUT%" classes.dex
if not exist "%OUT%\classes.dex" goto :err
"%BT%\zipalign.exe" -f -p 4 "%OUT%\base.apk" "%OUT%\aligned.apk"
if not exist "%OUT%\aligned.apk" goto :err

if not exist "%OUT%\release.keystore" (
    echo Creating signing keystore...
    keytool -genkeypair -keystore "%OUT%\release.keystore" -alias reminder -keyalg RSA -keysize 2048 -validity 10000 -storepass sedentary2026 -keypass sedentary2026 -dname "CN=Sedentary Reminder,O=Personal,C=CN"
    if errorlevel 1 goto :err
)

echo [6/6] apksigner sign...
call "%BT%\apksigner.bat" sign --ks "%OUT%\release.keystore" --ks-pass pass:sedentary2026 --key-pass pass:sedentary2026 --out "%OUT%\SedentaryReminder.apk" "%OUT%\aligned.apk"
if errorlevel 1 goto :err

call "%BT%\apksigner.bat" verify "%OUT%\SedentaryReminder.apk"
if errorlevel 1 goto :err

echo.
echo BUILD OK: %OUT%\SedentaryReminder.apk
exit /b 0

:err
echo BUILD FAILED
exit /b 1
