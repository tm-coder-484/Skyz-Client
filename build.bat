@echo off
setlocal enabledelayedexpansion

title Skyz Client — Build

echo.
echo  =====================================================
echo   SKYZ CLIENT v2.5.0  ^|  Fabric 1.21.11 Build Tool
echo  =====================================================
echo.

:: ── Preflight: Java ──────────────────────────────────────────────────────────
echo [1/4] Checking Java...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo  ERROR: Java not found in PATH.
    echo  Please install Java 21 (Temurin recommended):
    echo  https://adoptium.net/temurin/releases/?version=21
    echo.
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('java -version 2^>^&1') do (
    set JAVA_VER_LINE=%%i
    goto :got_java_ver
)
:got_java_ver
echo  Found: %JAVA_VER_LINE%

:: Check for Java 21+
java -version 2>&1 | findstr /C:"version \"21" /C:"version \"22" /C:"version \"23" /C:"version \"24" >nul
if %ERRORLEVEL% neq 0 (
    echo.
    echo  WARNING: Java 21+ is required for Minecraft 1.21.11 modding.
    echo  You may have an older version. Attempting build anyway...
    echo.
)

echo.

:: ── Preflight: gradle-wrapper.jar ────────────────────────────────────────────
echo [2/4] Checking Gradle wrapper...
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo.
    echo  gradle-wrapper.jar not found. Downloading...
    echo.

    :: Try PowerShell to download it
    powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar' }" 2>nul

    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        :: Fallback: try certutil
        certutil -urlcache -split -f "https://services.gradle.org/distributions/gradle-8.8-wrapper.jar" "gradle\wrapper\gradle-wrapper.jar" >nul 2>&1
    )

    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        echo  ERROR: Could not download gradle-wrapper.jar automatically.
        echo  Please download it manually from:
        echo  https://services.gradle.org/distributions/gradle-8.8-bin.zip
        echo  and extract gradle-wrapper.jar into gradle\wrapper\
        echo.
        pause
        exit /b 1
    )
    echo  Downloaded successfully.
)
echo  OK.
echo.

:: ── Clean previous build ─────────────────────────────────────────────────────
echo [3/4] Cleaning previous build output...
if exist "build\libs" (
    del /q "build\libs\*.jar" >nul 2>&1
)
echo  Done.
echo.

:: ── Gradle build ─────────────────────────────────────────────────────────────
echo [4/4] Building mod jar (this downloads MC + Fabric on first run)...
echo  This may take 5-15 minutes the first time while assets download.
echo  Subsequent builds take ~30 seconds.
echo.

call gradlew.bat build --info 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo  =====================================================
    echo   BUILD FAILED
    echo  =====================================================
    echo.
    echo  Common fixes:
    echo   - Make sure Java 21 is installed and JAVA_HOME is set
    echo   - Check your internet connection (first build needs it)
    echo   - Run:  gradlew.bat cleanloom  then try again
    echo   - See build output above for the specific error
    echo.
    pause
    exit /b 1
)

:: ── Locate output jar ────────────────────────────────────────────────────────
echo.
echo  =====================================================
echo   BUILD SUCCESSFUL
echo  =====================================================
echo.

set JAR_FILE=
for %%f in (build\libs\skyz-client-*.jar) do (
    echo %%f | findstr /v "sources" >nul
    if !ERRORLEVEL! equ 0 set JAR_FILE=%%f
)

if not "!JAR_FILE!"=="" (
    echo  Output jar:
    echo    !JAR_FILE!
    echo.
    echo  Install instructions:
    echo    1. Copy  !JAR_FILE!  into your .minecraft\mods\ folder
    echo    2. Make sure Fabric Loader 0.16.10 is installed
    echo    3. Make sure Fabric API 0.140.2+1.21.11 is in your mods folder
    echo    4. Launch Minecraft 1.21.11 with the Fabric profile
    echo.
) else (
    echo  Jar built — check build\libs\ for the output file.
    echo.
)

:: Offer to open the output folder
set /p OPEN_FOLDER="Open build\libs\ folder now? [y/N]: "
if /i "!OPEN_FOLDER!"=="y" start explorer build\libs

echo.
pause
endlocal
