#!/usr/bin/env bash
# Haushaltsbuch - Linux/macOS Python Check & Starter

echo "============================================================"
echo "  Haushaltsbuch & Monatsübersicht - Starter Script"
echo "============================================================"
echo ""

PYTHON_BIN=""

if command -v python3 &>/dev/null; then
    PYTHON_BIN="python3"
elif command -v python &>/dev/null; then
    PYTHON_BIN="python"
fi

install_python_and_tk() {
    if command -v pacman &>/dev/null; then
        echo "CachyOS / Arch Linux erkannt!"
        echo "Führe aus: sudo pacman -S --needed python tk"
        sudo pacman -S --needed python tk
        PYTHON_BIN="python"
    elif command -v apt &>/dev/null; then
        echo "Debian / Ubuntu / Mint erkannt!"
        echo "Führe aus: sudo apt update && sudo apt install -y python3 python3-tk"
        sudo apt update && sudo apt install -y python3 python3-tk
        PYTHON_BIN="python3"
    elif command -v dnf &>/dev/null; then
        echo "Fedora / RHEL erkannt!"
        echo "Führe aus: sudo dnf install -y python3 python3-tkinter"
        sudo dnf install -y python3 python3-tkinter
        PYTHON_BIN="python3"
    elif command -v zypper &>/dev/null; then
        echo "openSUSE erkannt!"
        echo "Führe aus: sudo zypper install -y python3 python3-tk"
        sudo zypper install -y python3 python3-tk
        PYTHON_BIN="python3"
    elif command -v brew &>/dev/null; then
        echo "macOS Homebrew erkannt!"
        echo "Führe aus: brew install python python-tk"
        brew install python python-tk
        PYTHON_BIN="python3"
    else
        echo "Paketmanager nicht erkannt. Bitte installieren Sie Python und Tkinter manuell."
        echo "Für CachyOS / Arch Linux: sudo pacman -S python tk"
        exit 1
    fi
}

if [ -z "$PYTHON_BIN" ]; then
    echo "[HINWEIS] Python wurde auf Ihrem System NICHT gefunden!"
    echo ""
    read -p "Möchten Sie versuchen, Python & Tkinter jetzt zu installieren? (j/n): " choice
    case "$choice" in 
        j|J|y|Y )
            install_python_and_tk
            ;;
        * )
            echo "Python wird benötigt. Für CachyOS/Arch führen Sie aus: sudo pacman -S python tk"
            exit 1
            ;;
    esac
fi

# Check if Tkinter is installed
$PYTHON_BIN -c "import tkinter" &>/dev/null
if [ $? -ne 0 ]; then
    echo "[HINWEIS] Python ist zwar vorhanden, aber Tkinter (GUI) fehlt!"
    read -p "Möchten Sie Tkinter jetzt installieren? (j/n): " choice
    case "$choice" in 
        j|J|y|Y )
            install_python_and_tk
            ;;
        * )
            echo "Tkinter wird benötigt. Für CachyOS/Arch führen Sie aus: sudo pacman -S tk"
            exit 1
            ;;
    esac
fi

echo "[OK] Python gefunden ($PYTHON_BIN)."
echo "Starte Haushaltsbuch..."
echo ""

$PYTHON_BIN haushaltsbuch.py
