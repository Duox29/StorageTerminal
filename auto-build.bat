@echo off
setlocal

REM ====== BUILD PROJECT ======
echo Running Gradle build...
cd /d "C:\Users\Duox\Documents\MC Modding\Base"

call gradlew clean build
IF ERRORLEVEL 1 (
    echo Gradle build failed. Abort.
    pause
    exit /b 1
)

echo Gradle build completed.

REM ====== MOD PATHS ======
set MODS_DIR=D:\GameBackup\PrismLauncher-Windows-MSVC-Portable-8.4\instances\1.21.1\.minecraft\mods
set SRC_JAR=C:\Users\Duox\Documents\MC Modding\Base\build\libs\advancedutilities-1.0.0.jar

REM ====== UPDATE MOD ======
echo Deleting old mods...
if exist "%MODS_DIR%" (
    del /q "%MODS_DIR%\*"
) else (
    echo Mods directory not found
    pause
    exit /b 1
)

echo Copying new mod...
copy /y "%SRC_JAR%" "%MODS_DIR%"

echo Mod updated.

REM ====== LAUNCH MINECRAFT ======
cd /d D:\GameBackup\PrismLauncher-Windows-MSVC-Portable-8.4
start "" PrismLauncher.exe -l "1.21.1"

echo Done.
exit
