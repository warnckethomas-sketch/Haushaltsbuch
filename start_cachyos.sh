#!/usr/bin/env bash
# Haushaltsbuch - CachyOS / Arch Linux Starter

echo "============================================================"
echo "  Haushaltsbuch - CachyOS / Arch Linux Starter"
echo "============================================================"
echo ""

# Check Python & Tkinter on CachyOS
if ! command -v python &>/dev/null && ! command -v python3 &>/dev/null; then
    echo "[HINWEIS] Python ist auf diesem CachyOS-System noch nicht installiert."
    read -p "Möchten Sie Python und Tkinter über pacman installieren? (j/n): " choice
    if [[ "$choice" =~ ^[jJyY]$ ]]; then
        sudo pacman -S --needed python tk
    else
        echo "Abgebrochen. Zum manuellen Installieren: sudo pacman -S python tk"
        exit 1
    fi
fi

PYTHON_CMD="python"
if ! command -v python &>/dev/null; then
    PYTHON_CMD="python3"
fi

# Check Tkinter module
$PYTHON_CMD -c "import tkinter" &>/dev/null
if [ $? -ne 0 ]; then
    echo "[HINWEIS] Tkinter fehlt für das GUI."
    read -p "Möchten Sie 'tk' (Tkinter) jetzt über pacman installieren? (j/n): " choice
    if [[ "$choice" =~ ^[jJyY]$ ]]; then
        sudo pacman -S --needed tk
    else
        echo "Abgebrochen. Befehl: sudo pacman -S tk"
        exit 1
    fi
fi

echo "[OK] CachyOS Umgebung bereit! Starte Haushaltsbuch..."
echo ""
$PYTHON_CMD haushaltsbuch.py
