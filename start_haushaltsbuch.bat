@echo off
chcp 65001 >nul
title Haushaltsbuch - Python Check & Starter

echo ============================================================
echo   Haushaltsbuch ^& Monatsuebersicht - Starter Script
echo ============================================================
echo.

REM Check if python or py launcher exists
set PYTHON_CMD=

where python >nul 2>nul
if %errorlevel% equ 0 (
    set PYTHON_CMD=python
    goto FOUND
)

where py >nul 2>nul
if %errorlevel% equ 0 (
    set PYTHON_CMD=py
    goto FOUND
)

:NOT_FOUND
echo [HINWEIS] Python wurde auf Ihrem System NICHT gefunden!
echo.
echo Dieses Programm benoetigt Python 3 (inkl. Tkinter), um zu starten.
echo.
set /p INSTALL_CHOICE="Moechten Sie die Python-Downloadseite jetzt im Browser oeffnen? (J/N): "

if /i "%INSTALL_CHOICE%"=="J" (
    echo.
    echo Oeffne https://www.python.org/downloads/ ...
    start https://www.python.org/downloads/
    echo.
    echo BITTE BEACHTEN SIE BEIM INSTALLIEREN:
    echo  - Aktivieren Sie das Kontrollkaestchen "Add Python.exe to PATH"
    echo.
    pause
    exit /b
) else if /i "%INSTALL_CHOICE%"=="Y" (
    echo.
    echo Oeffne https://www.python.org/downloads/ ...
    start https://www.python.org/downloads/
    echo.
    echo BITTE BEACHTEN SIE BEIM INSTALLIEREN:
    echo  - Aktivieren Sie das Kontrollkaestchen "Add Python.exe to PATH"
    echo.
    pause
    exit /b
) else (
    echo.
    echo Python wird benoetigt. Bitte installieren Sie Python manuell von https://www.python.org
    echo.
    pause
    exit /b
)

:FOUND
echo [OK] Python gefunden (%PYTHON_CMD%).
echo Starte Haushaltsbuch...
echo.

%PYTHON_CMD% haushaltsbuch.py

if %errorlevel% neq 0 (
    echo.
    echo [FEHLER] Das Skript wurde mit einem Fehler beendet.
    echo Falls Tkinter fehlt, installieren Sie bitte Python mit Tcl/Tk-Unterstuetzung.
    echo.
    pause
)
