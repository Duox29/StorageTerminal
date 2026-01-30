@echo off
setlocal

REM ====== CONFIGURATION ======
set JAR_NAME=storagemanager-1.0.0.jar

set BASE_DIR=C:\Users\Duox\Documents\MC Modding\StorageManager
set MODS_DIR=D:\GameBackup\PrismLauncher-Windows-MSVC-Portable-8.4\instances\1.21.1\.minecraft\mods
set PRISM_EXE_DIR=D:\GameBackup\PrismLauncher-Windows-MSVC-Portable-8.4
set INSTANCE_NAME=1.21.1

REM ====== DERIVED PATHS ======
set SRC_JAR=%BASE_DIR%\build\libs\%JAR_NAME%

REM ====== BUILD PROJECT ======
echo Running Gradle build...
cd /d "%BASE_DIR%"

call gradlew clean build
IF ERRORLEVEL 1 (
    echo Gradle build failed. Abort.
    pause
    exit /b 1
)

echo Gradle build completed.

REM ====== UPDATE MOD ======
echo Deleting old version...
if exist "%MODS_DIR%" (
    REM Tìm và xóa file dựa trên phần đầu của tên JAR
    del /q "%MODS_DIR%\storagemanager-*.jar"
) else (
    echo Mods directory not found
    pause
    exit /b 1
)

echo Copying new mod: %JAR_NAME%...
copy /y "%SRC_JAR%" "%MODS_DIR%"

echo Mod updated.

REM ====== LAUNCH MINECRAFT ======
cd /d "%PRISM_EXE_DIR%"
start "" PrismLauncher.exe -l "%INSTANCE_NAME%"

echo Done.
exit