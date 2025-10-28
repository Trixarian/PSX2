@echo off
REM Build signed AAB for Google Play release

echo Building release AAB...
call gradlew.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build successful!
    echo ========================================
    echo.
    echo AAB location:
    dir /b app\build\outputs\bundle\release\*.aab
    echo.
    echo Full path:
    cd
    echo \app\build\outputs\bundle\release\
    echo.
) else (
    echo.
    echo ========================================
    echo Build failed!
    echo ========================================
    echo.
)

pause
