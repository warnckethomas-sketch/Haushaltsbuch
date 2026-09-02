#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Haushaltsbuch & Finanzplaner Desktop App
A complete, fully functional Python application with SQLite persistence,
GUI (Tkinter/ttk), monthly overview calculations, income, fixed costs,
recurring expenses, invoices, category management, settings & backup export/import.
"""

import sys
import os
import shutil
import tempfile
import subprocess
import json
import sqlite3
import datetime
import webbrowser
import struct
import zlib
from pathlib import Path

# Check Python version
if sys.version_info < (3, 7):
    print("[FEHLER] Python 3.7 oder neuer wird benötigt!")
    print("Aktuelle Version:", sys.version)
    input("Drücken Sie Enter zum Beenden...")
    sys.exit(1)

# Check Tkinter availability
try:
    import tkinter as tk
    from tkinter import ttk, messagebox, filedialog, simpledialog
except ImportError:
    print("============================================================")
    print(" [FEHLER] Tkinter (GUI-Bibliothek) ist nicht installiert!")
    print("============================================================")
    print("Unter Linux/Ubuntu installieren Sie es mit:")
    print("  sudo apt install python3-tk")
    print("Unter Windows/macOS installieren Sie Python neu von:")
    print("  https://www.python.org/downloads/")
    print("  (Aktivieren Sie dabei 'tcl/tk and IDLE')")
    print("============================================================")
    ans = input("Möchten Sie die Python-Downloadseite im Browser öffnen? (j/n): ")
    if ans.lower() in ["j", "ja", "y", "yes"]:
        webbrowser.open("https://www.python.org/downloads/")
    sys.exit(1)

# Database File Name (im Unterordner 'backups', damit Arbeitsdatenbank und Sicherungen im selben Ordner liegen)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BACKUPS_DIR = os.path.join(SCRIPT_DIR, "backups")
os.makedirs(BACKUPS_DIR, exist_ok=True)

DB_FILE = os.path.join(BACKUPS_DIR, "haushaltsbuch.db")

# Falls eine alte Datenbank im Hauptverzeichnis existiert und noch keine im backups-Ordner, migrieren
OLD_DB_FILE = os.path.join(SCRIPT_DIR, "haushaltsbuch.db")
if os.path.exists(OLD_DB_FILE) and not os.path.exists(DB_FILE):
    try:
        shutil.copy2(OLD_DB_FILE, DB_FILE)
    except Exception:
        pass

def safe_int(val, default=0):
    if val is None:
        return default
    try:
        return int(val)
    except (ValueError, TypeError):
        return default

def safe_float(val, default=0.0):
    if val is None:
        return default
    try:
        return float(val)
    except (ValueError, TypeError):
        return default

def get_german_month_name(month_int):
    m_int = safe_int(month_int, 0)
    months = [
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    ]
    if 1 <= m_int <= 12:
        return months[m_int - 1]
    return f"Monat {month_int}"

GERMAN_MONTHS = [
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember"
]

FREQ_DISPLAY = [
    "Monatlich",
    "Alle 2 Monate",
    "Vierteljährlich (alle 3 Monate)",
    "Halbjährlich (alle 6 Monate)",
    "Jährlich",
    "Gesplittet / Spezifische Monate",
    "Einmalig / Zielmonat"
]

FREQ_TO_CODE = {
    "Monatlich": "MONTHLY",
    "Alle 2 Monate": "BIMONTHLY",
    "Vierteljährlich (alle 3 Monate)": "QUARTERLY",
    "Vierteljährlich": "QUARTERLY",
    "Halbjährlich (alle 6 Monate)": "HALF_YEARLY",
    "Halbjährlich": "HALF_YEARLY",
    "Jährlich": "YEARLY",
    "Gesplittet / Spezifische Monate": "SPLIT_MONTHS",
    "Gesplittet": "SPLIT_MONTHS",
    "Einmalig / Zielmonat": "SPECIFIC_MONTH"
}

CODE_TO_FREQ = {
    "MONTHLY": "Monatlich",
    "BIMONTHLY": "Alle 2 Monate",
    "QUARTERLY": "Vierteljährlich (alle 3 Monate)",
    "HALF_YEARLY": "Halbjährlich (alle 6 Monate)",
    "YEARLY": "Jährlich",
    "SPLIT_MONTHS": "Gesplittet / Spezifische Monate",
    "SPECIFIC_MONTH": "Einmalig / Zielmonat"
}

def check_fixed_cost_due(end_m, end_y, target_m, target_y):
    end_m = safe_int(end_m, 0)
    end_y = safe_int(end_y, 0)
    target_m = safe_int(target_m, 0)
    target_y = safe_int(target_y, 0)
    if end_y > 0 and end_m > 0:
        if (target_y > end_y) or (target_y == end_y and target_m > end_m):
            return False
    return True

def check_recurring_due(freq, month_spec, start_m, start_y, end_m, end_y, target_m, target_y, due_months=""):
    month_spec = safe_int(month_spec, 0)
    start_m = safe_int(start_m, 0)
    start_y = safe_int(start_y, 0)
    end_m = safe_int(end_m, 0)
    end_y = safe_int(end_y, 0)
    target_m = safe_int(target_m, 0)
    target_y = safe_int(target_y, 0)

    # 1. Start check
    if start_y > 0 and start_m > 0:
        if (target_y < start_y) or (target_y == start_y and target_m < start_m):
            return False

    # 2. End check
    if end_y > 0 and end_m > 0:
        if (target_y > end_y) or (target_y == end_y and target_m > end_m):
            return False

    freq_str = str(freq) if freq is not None else ""
    f_code = FREQ_TO_CODE.get(freq_str, freq_str)

    if f_code == "SPLIT_MONTHS":
        if due_months:
            d_m_list = [safe_int(x) for x in str(due_months).split(",") if x.strip().isdigit()]
            return target_m in d_m_list
        return False

    # 3. Turnus check
    s_y = start_y if start_y > 0 else target_y
    s_m = start_m if start_m > 0 else (month_spec if month_spec > 0 else 1)

    m_diff = (target_y - s_y) * 12 + (target_m - s_m)

    if f_code == "MONTHLY":
        return True
    elif f_code == "BIMONTHLY":
        return m_diff >= 0 and m_diff % 2 == 0
    elif f_code == "QUARTERLY":
        return m_diff >= 0 and m_diff % 3 == 0
    elif f_code == "HALF_YEARLY":
        return m_diff >= 0 and m_diff % 6 == 0
    elif f_code == "YEARLY":
        return target_m == s_m
    elif f_code == "SPECIFIC_MONTH":
        if month_spec > 0:
            return target_m == month_spec
        return target_m == s_m and (start_y == 0 or target_y == start_y)

    return True

def get_recurring_rhythm_display(r):
    freq = r[4] if len(r) > 4 else "MONTHLY"
    month_spec = safe_int(r[5] if len(r) > 5 else 0, 0)
    s_m = safe_int(r[7] if len(r) > 7 else 1, 1)
    s_y = safe_int(r[8] if len(r) > 8 else 0, 0)
    due_m = r[12] if len(r) > 12 else ""
    f_code = FREQ_TO_CODE.get(freq, freq)

    if f_code == "SPLIT_MONTHS" and due_m:
        m_indices = [safe_int(x) for x in str(due_m).split(",") if x.strip().isdigit()]
        m_abbrs = [GERMAN_MONTHS[i-1][:3] for i in m_indices if 1 <= i <= 12]
        return f"Gesplittet ({', '.join(m_abbrs)})"
    elif f_code == "YEARLY":
        m_due = s_m if (1 <= s_m <= 12) else (month_spec if 1 <= month_spec <= 12 else 0)
        if 1 <= m_due <= 12:
            return f"Jährlich (im {GERMAN_MONTHS[m_due-1][:3]})"
        return "Jährlich"
    elif f_code == "QUARTERLY":
        if 1 <= s_m <= 12:
            return f"Quartalsweise (ab {GERMAN_MONTHS[s_m-1][:3]})"
        return CODE_TO_FREQ.get(freq, freq)
    elif f_code == "HALF_YEARLY":
        if 1 <= s_m <= 12:
            return f"Halbjährlich (ab {GERMAN_MONTHS[s_m-1][:3]})"
        return CODE_TO_FREQ.get(freq, freq)
    elif f_code == "SPECIFIC_MONTH":
        m_due = month_spec if 1 <= month_spec <= 12 else s_m
        if 1 <= m_due <= 12:
            y_str = f" {s_y}" if s_y > 0 else ""
            return f"Einmalig ({GERMAN_MONTHS[m_due-1][:3]}{y_str})"
        return CODE_TO_FREQ.get(freq, freq)
    else:
        return CODE_TO_FREQ.get(freq, freq)

def get_recurring_due_key(r, target_m=1, target_y=2026):
    freq = r[4] if len(r) > 4 else "MONTHLY"
    month_spec = safe_int(r[5] if len(r) > 5 else 0, 0)
    is_active = safe_int(r[6] if len(r) > 6 else 1, 1)
    s_m = safe_int(r[7] if len(r) > 7 else 1, 1)
    s_y = safe_int(r[8] if len(r) > 8 else 2026, 2026)
    e_m = safe_int(r[9] if len(r) > 9 else 0, 0)
    e_y = safe_int(r[10] if len(r) > 10 else 0, 0)
    due_m = r[12] if len(r) > 12 else ""
    title = str(r[1] if len(r) > 1 else "").lower()
    cat = str(r[3] if len(r) > 3 else "").lower()

    first_m = 99
    if due_m:
        d_m_list = [safe_int(x) for x in str(due_m).split(",") if x.strip().isdigit()]
        if d_m_list:
            first_m = min(d_m_list)
    if first_m == 99:
        if month_spec > 0:
            first_m = month_spec
        elif s_m > 0:
            first_m = s_m
        else:
            first_m = 1

    next_delta = 999
    if target_m > 0 and target_y > 0:
        for delta in range(12):
            m_chk = (target_m - 1 + delta) % 12 + 1
            y_chk = target_y + (target_m - 1 + delta) // 12
            if check_recurring_due(freq, month_spec, s_m, s_y, e_m, e_y, m_chk, y_chk, due_m):
                next_delta = delta
                break

    active_sort = 0 if is_active == 1 else 1
    return (active_sort, next_delta, first_m, title, cat)

def ensure_bank_logo():
    logo_path = os.path.join(SCRIPT_DIR, "bank_logo.png")
    valid = False
    if os.path.exists(logo_path):
        try:
            with open(logo_path, "rb") as f:
                if f.read(8) == b'\x89PNG\r\n\x1a\n':
                    valid = True
        except Exception:
            valid = False

    if not valid:
        url = "https://w7.pngwing.com/pngs/146/630/png-transparent-cooperative-banking-bilanzsumme-bundesverband-der-deutschen-volksbanken-und-raiffeisenbanken-bank-code-bank-blue-angle-text-thumbnail.png"
        try:
            import urllib.request
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = resp.read()
            if data.startswith(b'\x89PNG\r\n\x1a\n'):
                with open(logo_path, "wb") as f:
                    f.write(data)
                valid = True
        except Exception as e:
            print(f"[WARNUNG] Logo konnte nicht heruntergeladen werden: {e}")

    return logo_path if valid and os.path.exists(logo_path) else None

def get_bank_logo_pdf_xobject():
    logo_path = ensure_bank_logo()
    if not logo_path or not os.path.exists(logo_path):
        return None
    try:
        with open(logo_path, "rb") as f:
            data = f.read()
        if data[:8] != b'\x89PNG\r\n\x1a\n':
            return None
            
        pos = 8
        w, h, depth, color_type = 0, 0, 8, 2
        palette = []
        idat_chunks = []
        
        while pos < len(data):
            length, ctype = struct.unpack('>I4s', data[pos:pos+8])
            pos += 8
            cdata = data[pos:pos+length]
            pos += length + 4
            
            if ctype == b'IHDR':
                w, h, depth, color_type, _, _, _ = struct.unpack('>IIBBBBB', cdata)
            elif ctype == b'PLTE':
                palette = [cdata[i:i+3] for i in range(0, len(cdata), 3)]
            elif ctype == b'IDAT':
                idat_chunks.append(cdata)
                
        decompressed = zlib.decompress(b''.join(idat_chunks))
        rgb = bytearray()
        
        if color_type == 3: # Indexed Color
            row_bytes = (w * depth + 7) // 8
            stride = 1 + row_bytes
            for y in range(h):
                row_data = decompressed[y * stride + 1 : (y + 1) * stride]
                if depth == 4:
                    for x in range(w):
                        byte_idx = x // 2
                        bit_shift = 4 if (x % 2 == 0) else 0
                        color_idx = (row_data[byte_idx] >> bit_shift) & 0x0F
                        if color_idx < len(palette):
                            rgb.extend(palette[color_idx])
                        else:
                            rgb.extend(b'\xff\xff\xff')
                elif depth == 8:
                    for x in range(w):
                        color_idx = row_data[x]
                        if color_idx < len(palette):
                            rgb.extend(palette[color_idx])
                        else:
                            rgb.extend(b'\xff\xff\xff')
        elif color_type in (2, 6): # Truecolor RGB / RGBA
            bpp = 3 if color_type == 2 else 4
            stride = 1 + w * bpp
            for y in range(h):
                row_data = decompressed[y * stride + 1 : (y + 1) * stride]
                for x in range(w):
                    px = row_data[x * bpp : x * bpp + 3]
                    rgb.extend(px)
        elif color_type == 0: # Grayscale
            stride = 1 + w
            for y in range(h):
                row_data = decompressed[y * stride + 1 : (y + 1) * stride]
                for x in range(w):
                    g = row_data[x]
                    rgb.extend(bytes([g, g, g]))
                    
        if len(rgb) == w * h * 3:
            return w, h, zlib.compress(bytes(rgb))
    except Exception as e:
        print(f"[WARNUNG] PNG logo for PDF failed: {e}")
    return None

class FinanceDB:
    def __init__(self, db_path=DB_FILE):
        self.db_path = db_path
        self.init_db()
        self.auto_backup()

    def get_connection(self):
        return sqlite3.connect(self.db_path)

    def init_db(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            
            # Categories
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                type TEXT NOT NULL
            )
            """)

            # Income
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS income (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                month INTEGER NOT NULL DEFAULT 0,
                year INTEGER NOT NULL DEFAULT 0,
                is_recurring INTEGER NOT NULL DEFAULT 1
            )
            """)
            cursor.execute("PRAGMA table_info(income)")
            cols = [r[1] for r in cursor.fetchall()]
            if "is_recurring" not in cols:
                cursor.execute("ALTER TABLE income ADD COLUMN is_recurring INTEGER NOT NULL DEFAULT 1")

            # Fixed Costs
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS fixed_costs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                day_of_month INTEGER NOT NULL DEFAULT 1,
                end_month INTEGER DEFAULT 0,
                end_year INTEGER DEFAULT 0
            )
            """)
            cursor.execute("PRAGMA table_info(fixed_costs)")
            cols = [r[1] for r in cursor.fetchall()]
            if "day_of_month" not in cols:
                cursor.execute("ALTER TABLE fixed_costs ADD COLUMN day_of_month INTEGER NOT NULL DEFAULT 1")
            if "end_month" not in cols:
                cursor.execute("ALTER TABLE fixed_costs ADD COLUMN end_month INTEGER DEFAULT 0")
            if "end_year" not in cols:
                cursor.execute("ALTER TABLE fixed_costs ADD COLUMN end_year INTEGER DEFAULT 0")

            # Recurring Expenses
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS recurring_expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                frequency TEXT NOT NULL DEFAULT 'MONTHLY',
                month_specified INTEGER DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 1,
                start_month INTEGER DEFAULT 1,
                start_year INTEGER DEFAULT 2026,
                end_month INTEGER DEFAULT 0,
                end_year INTEGER DEFAULT 0,
                note TEXT DEFAULT '',
                due_months TEXT DEFAULT ''
            )
            """)
            cursor.execute("PRAGMA table_info(recurring_expenses)")
            rec_cols = [r[1] for r in cursor.fetchall()]
            if "start_month" not in rec_cols:
                cursor.execute("ALTER TABLE recurring_expenses ADD COLUMN start_month INTEGER DEFAULT 1")
            if "start_year" not in rec_cols:
                cursor.execute("ALTER TABLE recurring_expenses ADD COLUMN start_year INTEGER DEFAULT 2026")
            if "end_month" not in rec_cols:
                cursor.execute("ALTER TABLE recurring_expenses ADD COLUMN end_month INTEGER DEFAULT 0")
            if "end_year" not in rec_cols:
                cursor.execute("ALTER TABLE recurring_expenses ADD COLUMN end_year INTEGER DEFAULT 0")
            if "note" not in rec_cols:
                cursor.execute("ALTER TABLE recurring_expenses ADD COLUMN note TEXT DEFAULT ''")
            if "due_months" not in rec_cols:
                cursor.execute("ALTER TABLE recurring_expenses ADD COLUMN due_months TEXT DEFAULT ''")

            # Invoices
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                vendor TEXT NOT NULL,
                description TEXT NOT NULL,
                amount REAL NOT NULL,
                due_date TEXT NOT NULL,
                category TEXT NOT NULL,
                is_paid INTEGER NOT NULL DEFAULT 0,
                month INTEGER NOT NULL,
                year INTEGER NOT NULL
            )
            """)

            # Pflegegeld (Gesonderte Einnahme)
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS pflegegeld (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                beguenstigter TEXT NOT NULL DEFAULT '',
                ueberweiser TEXT NOT NULL DEFAULT '',
                pflegegrad TEXT NOT NULL DEFAULT 'Pflegegrad 1',
                gesamtpflegegeld REAL NOT NULL DEFAULT 0.0,
                pflegehilfsmittel REAL NOT NULL DEFAULT 0.0,
                anteil_prozent REAL NOT NULL DEFAULT 100.0,
                ergebnis REAL NOT NULL DEFAULT 0.0,
                gueltigkeit TEXT DEFAULT '',
                rhythmus TEXT DEFAULT 'Monatlich',
                is_active INTEGER NOT NULL DEFAULT 1
            )
            """)

            # Settings
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """)

            # Populate default categories if empty
            cursor.execute("SELECT COUNT(*) FROM categories")
            if cursor.fetchone()[0] == 0:
                default_cats = [
                    ("Gehalt", "Einnahme"),
                    ("Nebeneinkommen", "Einnahme"),
                    ("Miete & Wohnen", "Fixkosten"),
                    ("Versicherungen", "Fixkosten"),
                    ("Strom & Heizung", "Fixkosten"),
                    ("Internet & Telefon", "Fixkosten"),
                    ("Abonnements", "Wiederkehrend"),
                    ("Lebensmittel", "Ausgabe"),
                    ("Freizeit", "Ausgabe"),
                    ("Rechnungen", "Rechnung"),
                    ("Sonstiges", "Sonstiges")
                ]
                cursor.executemany("INSERT INTO categories (name, type) VALUES (?, ?)", default_cats)
            
            conn.commit()

    # --- Settings CRUD ---
    def get_setting(self, key, default=""):
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT value FROM settings WHERE key=?", (key,))
                row = cursor.fetchone()
                return row[0] if row else default
        except Exception:
            return default

    def set_setting(self, key, value):
        try:
            with self.get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", (key, str(value)))
                conn.commit()
        except Exception as e:
            print(f"[FEHLER] Fehler beim Speichern der Einstellung {key}: {e}")

    # --- Auto Backup ---
    def auto_backup(self, max_backups=20):
        """Erstellt beim Starten/Beenden eine automatische Sicherung der Datenbank und als JSON im Ordner 'backups' (wird immer überschrieben)."""
        try:
            if not os.path.exists(self.db_path):
                return None
            
            backup_dir = os.path.join(SCRIPT_DIR, "backups")
            os.makedirs(backup_dir, exist_ok=True)
            
            db_backup_path = os.path.join(backup_dir, "haushaltsbuch_autobackup.db")
            shutil.copy2(self.db_path, db_backup_path)
            
            json_backup_path = os.path.join(backup_dir, "haushaltsbuch_autobackup.json")
            try:
                json_data = self.export_to_json()
                with open(json_backup_path, "w", encoding="utf-8") as f:
                    f.write(json_data)
            except Exception as e:
                print(f"[WARNUNG] JSON Auto-Backup fehlgeschlagen: {e}")
            
            self.set_setting("last_auto_backup", datetime.datetime.now().strftime("%d.%m.%Y um %H:%M:%S Uhr"))
            return backup_dir
        except Exception as e:
            print(f"[WARNUNG] Automatische Sicherung fehlgeschlagen: {e}")
            return None

    def create_manual_auto_backup(self):
        """Erstellt sofort eine frische automatische Sicherung (überschreibt die bestehende Backup-Datei)."""
        res = self.auto_backup()
        if res:
            db_backup_path = os.path.join(res, "haushaltsbuch_autobackup.db")
            return True, db_backup_path
        else:
            return False, "Fehler beim Erstellen der automatischen Sicherung."

    # --- Categories CRUD ---
    def get_categories(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, name, type FROM categories ORDER BY type ASC, name ASC")
            return cursor.fetchall()

    def add_category(self, name, cat_type):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("INSERT INTO categories (name, type) VALUES (?, ?)", (name, cat_type))
            conn.commit()

    def delete_category(self, cat_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM categories WHERE id = ?", (cat_id,))
            conn.commit()

    def update_category(self, cat_id, name, cat_type, old_name=None):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("UPDATE categories SET name = ?, type = ? WHERE id = ?", (name, cat_type, cat_id))
            if old_name and old_name != name:
                cursor.execute("UPDATE income SET category = ? WHERE category = ?", (name, old_name))
                cursor.execute("UPDATE fixed_costs SET category = ? WHERE category = ?", (name, old_name))
                cursor.execute("UPDATE recurring_expenses SET category = ? WHERE category = ?", (name, old_name))
                cursor.execute("UPDATE invoices SET category = ? WHERE category = ?", (name, old_name))
            conn.commit()

    # --- Income CRUD ---
    def get_incomes(self, month=None, year=None):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(income)")
            cols = [r[1] for r in cursor.fetchall()]
            has_rec = "is_recurring" in cols

            if has_rec:
                cursor.execute("SELECT id, title, amount, category, is_active, month, year, is_recurring FROM income")
            else:
                cursor.execute("SELECT id, title, amount, category, is_active, month, year FROM income")

            rows = cursor.fetchall()

            if month is None or year is None:
                return rows

            specific_entries = []
            recurring_entries = []

            for r in rows:
                is_rec = r[7] if len(r) > 7 else 1
                r_m = r[5]
                r_y = r[6]

                if is_rec == 0:
                    if r_m == month and r_y == year:
                        specific_entries.append(r)
                else:
                    if r_m == 0 or (r_m == month and r_y == year) or r_m is None:
                        recurring_entries.append(r)

            specific_titles = {r[1].strip().lower() for r in specific_entries}

            result = list(specific_entries)
            for r in recurring_entries:
                if r[1].strip().lower() not in specific_titles:
                    result.append(r)

            return result

    def get_income_by_id(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(income)")
            cols = [r[1] for r in cursor.fetchall()]
            if "is_recurring" in cols:
                cursor.execute("SELECT id, title, amount, category, is_active, month, year, is_recurring FROM income WHERE id=?", (item_id,))
            else:
                cursor.execute("SELECT id, title, amount, category, is_active, month, year FROM income WHERE id=?", (item_id,))
            return cursor.fetchone()

    def save_income(self, item_id, title, amount, category, is_active, month=0, year=0, is_recurring=1):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(income)")
            cols = [r[1] for r in cursor.fetchall()]
            has_rec = "is_recurring" in cols

            if item_id:
                if has_rec:
                    cursor.execute("UPDATE income SET title=?, amount=?, category=?, is_active=?, month=?, year=?, is_recurring=? WHERE id=?",
                                   (title, amount, category, is_active, month, year, is_recurring, item_id))
                else:
                    cursor.execute("UPDATE income SET title=?, amount=?, category=?, is_active=?, month=?, year=? WHERE id=?",
                                   (title, amount, category, is_active, month, year, item_id))
            else:
                if has_rec:
                    cursor.execute("INSERT INTO income (title, amount, category, is_active, month, year, is_recurring) VALUES (?, ?, ?, ?, ?, ?, ?)",
                                   (title, amount, category, is_active, month, year, is_recurring))
                else:
                    cursor.execute("INSERT INTO income (title, amount, category, is_active, month, year) VALUES (?, ?, ?, ?, ?, ?)",
                                   (title, amount, category, is_active, month, year))
            conn.commit()

    def delete_income(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM income WHERE id=?", (item_id,))
            conn.commit()

    # --- Pflegegeld CRUD ---
    def get_pflegegeld_list(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active FROM pflegegeld ORDER BY id ASC")
            return cursor.fetchall()

    def get_pflegegeld_by_id(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active FROM pflegegeld WHERE id=?", (item_id,))
            return cursor.fetchone()

    def save_pflegegeld(self, item_id, beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active=1):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if item_id:
                cursor.execute("""
                    UPDATE pflegegeld
                    SET beguenstigter=?, ueberweiser=?, pflegegrad=?, gesamtpflegegeld=?, pflegehilfsmittel=?, anteil_prozent=?, ergebnis=?, gueltigkeit=?, rhythmus=?, is_active=?
                    WHERE id=?
                """, (beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active, item_id))
            else:
                cursor.execute("""
                    INSERT INTO pflegegeld (beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active))
            conn.commit()

    def delete_pflegegeld(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM pflegegeld WHERE id=?", (item_id,))
            conn.commit()

    # --- Fixed Costs CRUD ---
    def get_fixed_costs(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(fixed_costs)")
            cols = [r[1] for r in cursor.fetchall()]
            if "end_month" in cols and "end_year" in cols:
                cursor.execute("SELECT id, title, amount, category, is_active, day_of_month, end_month, end_year FROM fixed_costs")
            else:
                cursor.execute("SELECT id, title, amount, category, is_active, day_of_month FROM fixed_costs")
            return cursor.fetchall()

    def get_fixed_cost_by_id(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(fixed_costs)")
            cols = [r[1] for r in cursor.fetchall()]
            if "end_month" in cols and "end_year" in cols:
                cursor.execute("SELECT id, title, amount, category, is_active, day_of_month, end_month, end_year FROM fixed_costs WHERE id=?", (item_id,))
            else:
                cursor.execute("SELECT id, title, amount, category, is_active, day_of_month FROM fixed_costs WHERE id=?", (item_id,))
            return cursor.fetchone()

    def save_fixed_cost(self, item_id, title, amount, category, is_active, day_of_month=1, end_month=0, end_year=0):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(fixed_costs)")
            cols = [r[1] for r in cursor.fetchall()]
            has_end = ("end_month" in cols and "end_year" in cols)
            if item_id:
                if has_end:
                    cursor.execute("UPDATE fixed_costs SET title=?, amount=?, category=?, is_active=?, day_of_month=?, end_month=?, end_year=? WHERE id=?",
                                   (title, amount, category, is_active, day_of_month, end_month, end_year, item_id))
                else:
                    cursor.execute("UPDATE fixed_costs SET title=?, amount=?, category=?, is_active=?, day_of_month=? WHERE id=?",
                                   (title, amount, category, is_active, day_of_month, item_id))
            else:
                if has_end:
                    cursor.execute("INSERT INTO fixed_costs (title, amount, category, is_active, day_of_month, end_month, end_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                                   (title, amount, category, is_active, day_of_month, end_month, end_year))
                else:
                    cursor.execute("INSERT INTO fixed_costs (title, amount, category, is_active, day_of_month) VALUES (?, ?, ?, ?, ?)",
                                   (title, amount, category, is_active, day_of_month))
            conn.commit()

    def delete_fixed_cost(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM fixed_costs WHERE id=?", (item_id,))
            conn.commit()

    # --- Recurring Expenses CRUD ---
    def get_recurring_expenses(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(recurring_expenses)")
            cols = [r[1] for r in cursor.fetchall()]

            if "due_months" in cols:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months FROM recurring_expenses")
            elif "start_month" in cols:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note FROM recurring_expenses")
            else:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active FROM recurring_expenses")
            return cursor.fetchall()

    def get_recurring_expense_by_id(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(recurring_expenses)")
            cols = [r[1] for r in cursor.fetchall()]

            if "due_months" in cols:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months FROM recurring_expenses WHERE id=?", (item_id,))
            elif "start_month" in cols:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note FROM recurring_expenses WHERE id=?", (item_id,))
            else:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active FROM recurring_expenses WHERE id=?", (item_id,))
            return cursor.fetchone()

    def save_recurring_expense(self, item_id, title, amount, category, frequency, month_specified, is_active, start_month=1, start_year=2026, end_month=0, end_year=0, note="", due_months=""):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("PRAGMA table_info(recurring_expenses)")
            cols = [r[1] for r in cursor.fetchall()]

            has_due = "due_months" in cols
            has_extra = "start_month" in cols

            if item_id:
                if has_due:
                    cursor.execute("""
                        UPDATE recurring_expenses
                        SET title=?, amount=?, category=?, frequency=?, month_specified=?, is_active=?, start_month=?, start_year=?, end_month=?, end_year=?, note=?, due_months=?
                        WHERE id=?
                    """, (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months, item_id))
                elif has_extra:
                    cursor.execute("""
                        UPDATE recurring_expenses
                        SET title=?, amount=?, category=?, frequency=?, month_specified=?, is_active=?, start_month=?, start_year=?, end_month=?, end_year=?, note=?
                        WHERE id=?
                    """, (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, item_id))
                else:
                    cursor.execute("""
                        UPDATE recurring_expenses
                        SET title=?, amount=?, category=?, frequency=?, month_specified=?, is_active=?
                        WHERE id=?
                    """, (title, amount, category, frequency, month_specified, is_active, item_id))
            else:
                if has_due:
                    cursor.execute("""
                        INSERT INTO recurring_expenses (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months))
                elif has_extra:
                    cursor.execute("""
                        INSERT INTO recurring_expenses (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note))
                else:
                    cursor.execute("""
                        INSERT INTO recurring_expenses (title, amount, category, frequency, month_specified, is_active)
                        VALUES (?, ?, ?, ?, ?, ?)
                    """, (title, amount, category, frequency, month_specified, is_active))
            conn.commit()

    def delete_recurring_expense(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM recurring_expenses WHERE id=?", (item_id,))
            conn.commit()

    # --- Invoices CRUD ---
    def get_invoices(self, month=None, year=None):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if month and year:
                cursor.execute("SELECT id, vendor, description, amount, due_date, category, is_paid, month, year FROM invoices WHERE month=? AND year=? ORDER BY due_date ASC", (month, year))
            else:
                cursor.execute("SELECT id, vendor, description, amount, due_date, category, is_paid, month, year FROM invoices ORDER BY due_date ASC")
            return cursor.fetchall()

    def get_invoice_by_id(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id, vendor, description, amount, due_date, category, is_paid, month, year FROM invoices WHERE id=?", (item_id,))
            return cursor.fetchone()

    def save_invoice(self, item_id, vendor, description, amount, due_date, category, is_paid, month, year):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            if item_id:
                cursor.execute("UPDATE invoices SET vendor=?, description=?, amount=?, due_date=?, category=?, is_paid=?, month=?, year=? WHERE id=?",
                               (vendor, description, amount, due_date, category, is_paid, month, year, item_id))
            else:
                cursor.execute("INSERT INTO invoices (vendor, description, amount, due_date, category, is_paid, month, year) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                               (vendor, description, amount, due_date, category, is_paid, month, year))
            conn.commit()

    def delete_invoice(self, item_id):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM invoices WHERE id=?", (item_id,))
            conn.commit()

    # --- Backup & Export ---
    def export_to_json(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            data = {
                "version": 1,
                "exportDate": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            }
            
            # Categories
            cursor.execute("SELECT id, name, type FROM categories")
            data["categories"] = [{"id": r[0], "name": r[1], "type": r[2]} for r in cursor.fetchall()]
            
            # Income
            cursor.execute("SELECT id, title, amount, category, is_active, month, year FROM income")
            incomes = []
            for r in cursor.fetchall():
                incomes.append({
                    "id": r[0], "title": r[1], "amount": r[2], "category": r[3],
                    "is_active": r[4], "isActive": bool(r[4]),
                    "month": r[5], "specificMonth": r[5],
                    "year": r[6], "specificYear": r[6]
                })
            data["income"] = incomes
            data["incomes"] = incomes
            
            # Fixed Costs
            cursor.execute("PRAGMA table_info(fixed_costs)")
            cols = [r[1] for r in cursor.fetchall()]
            if "end_month" in cols and "end_year" in cols:
                cursor.execute("SELECT id, title, amount, category, is_active, day_of_month, end_month, end_year FROM fixed_costs")
                fixed = []
                for r in cursor.fetchall():
                    fixed.append({
                        "id": r[0], "title": r[1], "amount": r[2], "category": r[3],
                        "is_active": r[4], "isActive": bool(r[4]),
                        "day_of_month": r[5] if len(r) > 5 else 1,
                        "end_month": r[6] if len(r) > 6 else 0, "endMonth": r[6] if len(r) > 6 else 0,
                        "end_year": r[7] if len(r) > 7 else 0, "endYear": r[7] if len(r) > 7 else 0
                    })
            else:
                cursor.execute("SELECT id, title, amount, category, is_active, day_of_month FROM fixed_costs")
                fixed = []
                for r in cursor.fetchall():
                    fixed.append({
                        "id": r[0], "title": r[1], "amount": r[2], "category": r[3],
                        "is_active": r[4], "isActive": bool(r[4]),
                        "day_of_month": r[5] if len(r) > 5 else 1
                    })
            data["fixed_costs"] = fixed
            data["fixedCosts"] = fixed
            
            # Recurring Expenses
            cursor.execute("PRAGMA table_info(recurring_expenses)")
            cols = [r[1] for r in cursor.fetchall()]
            if "due_months" in cols:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months FROM recurring_expenses")
                recurring = []
                for r in cursor.fetchall():
                    recurring.append({
                        "id": r[0], "title": r[1], "amount": r[2], "category": r[3],
                        "frequency": r[4], "month_specified": r[5],
                        "is_active": r[6], "isActive": bool(r[6]),
                        "start_month": r[7], "startMonth": r[7],
                        "start_year": r[8], "startYear": r[8],
                        "end_month": r[9], "endMonth": r[9],
                        "end_year": r[10], "endYear": r[10],
                        "note": r[11],
                        "due_months": r[12], "dueMonths": r[12]
                    })
            elif "start_month" in cols:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note FROM recurring_expenses")
                recurring = []
                for r in cursor.fetchall():
                    recurring.append({
                        "id": r[0], "title": r[1], "amount": r[2], "category": r[3],
                        "frequency": r[4], "month_specified": r[5],
                        "is_active": r[6], "isActive": bool(r[6]),
                        "start_month": r[7], "startMonth": r[7],
                        "start_year": r[8], "startYear": r[8],
                        "end_month": r[9], "endMonth": r[9],
                        "end_year": r[10], "endYear": r[10],
                        "note": r[11]
                    })
            else:
                cursor.execute("SELECT id, title, amount, category, frequency, month_specified, is_active FROM recurring_expenses")
                recurring = []
                for r in cursor.fetchall():
                    recurring.append({
                        "id": r[0], "title": r[1], "amount": r[2], "category": r[3],
                        "frequency": r[4], "month_specified": r[5],
                        "is_active": r[6], "isActive": bool(r[6])
                    })
            data["recurring_expenses"] = recurring
            data["recurringExpenses"] = recurring
            
            # Invoices
            cursor.execute("SELECT id, vendor, description, amount, due_date, category, is_paid, month, year FROM invoices")
            invoices = []
            for r in cursor.fetchall():
                invoices.append({
                    "id": r[0], "vendor": r[1], "description": r[2], "amount": r[3],
                    "due_date": r[4], "category": r[5],
                    "is_paid": r[6], "isPaid": bool(r[6]),
                    "month": r[7], "dueMonth": r[7],
                    "year": r[8], "dueYear": r[8]
                })
            data["invoices"] = invoices

            # Pflegegeld
            cursor.execute("SELECT id, beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active FROM pflegegeld")
            pflegegeld = []
            for r in cursor.fetchall():
                pflegegeld.append({
                    "id": r[0], "beguenstigter": r[1], "ueberweiser": r[2], "pflegegrad": r[3],
                    "gesamtpflegegeld": r[4], "pflegehilfsmittel": r[5], "anteil_prozent": r[6],
                    "ergebnis": r[7], "gueltigkeit": r[8], "rhythmus": r[9], "is_active": r[10]
                })
            data["pflegegeld"] = pflegegeld
            
            return json.dumps(data, indent=2, ensure_ascii=False)

    def import_from_json(self, json_str):
        raw_data = json.loads(json_str)
        
        # Unwrap nested root structure if present
        data = raw_data
        if isinstance(raw_data, dict):
            if "data" in raw_data and isinstance(raw_data["data"], dict):
                data = raw_data["data"]
            elif "backup" in raw_data and isinstance(raw_data["backup"], dict):
                data = raw_data["backup"]

        with self.get_connection() as conn:
            cursor = conn.cursor()
            today = datetime.date.today()

            # 1. CATEGORIES
            cats_list = data.get("categories") or []
            if cats_list:
                cursor.execute("DELETE FROM categories")
                for c in cats_list:
                    name = c.get("name") or c.get("title") or c.get("category")
                    c_type = c.get("type") or "Ausgabe"
                    if name:
                        cursor.execute("INSERT INTO categories (name, type) VALUES (?, ?)", (str(name), str(c_type)))

            # 2. INCOME / INCOMES
            inc_list = data.get("income") or data.get("incomes") or []
            if inc_list:
                cursor.execute("DELETE FROM income")
                for item in inc_list:
                    title = item.get("title") or item.get("name") or "Einnahme"
                    try:
                        amount = float(item.get("amount", 0.0))
                    except (ValueError, TypeError):
                        amount = 0.0
                    category = item.get("category") or "Gehalt"
                    
                    active_val = item.get("is_active")
                    if active_val is None:
                        active_val = item.get("isActive", True)
                    is_active = 1 if bool(active_val) else 0

                    month = item.get("month") or item.get("specificMonth") or item.get("dueMonth") or today.month
                    year = item.get("year") or item.get("specificYear") or item.get("dueYear") or today.year
                    
                    try: month = int(month)
                    except: month = today.month
                    try: year = int(year)
                    except: year = today.year

                    cursor.execute(
                        "INSERT INTO income (title, amount, category, is_active, month, year) VALUES (?, ?, ?, ?, ?, ?)",
                        (str(title), amount, str(category), is_active, month, year)
                    )

            # 3. FIXED COSTS / FIXEDCOSTS
            fixed_list = data.get("fixed_costs") or data.get("fixedCosts") or []
            if fixed_list:
                cursor.execute("DELETE FROM fixed_costs")
                for item in fixed_list:
                    title = item.get("title") or item.get("name") or "Fixkosten"
                    try:
                        amount = float(item.get("amount", 0.0))
                    except (ValueError, TypeError):
                        amount = 0.0
                    category = item.get("category") or "Fixkosten"

                    active_val = item.get("is_active")
                    if active_val is None:
                        active_val = item.get("isActive", True)
                    is_active = 1 if bool(active_val) else 0

                    try:
                        dom = int(item.get("day_of_month") or item.get("dayOfMonth") or 1)
                    except (ValueError, TypeError):
                        dom = 1

                    end_m = int(item.get("end_month") or item.get("endMonth") or 0)
                    end_y = int(item.get("end_year") or item.get("endYear") or 0)

                    cursor.execute("PRAGMA table_info(fixed_costs)")
                    cols = [r[1] for r in cursor.fetchall()]
                    if "end_month" in cols and "end_year" in cols:
                        cursor.execute(
                            "INSERT INTO fixed_costs (title, amount, category, is_active, day_of_month, end_month, end_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            (str(title), amount, str(category), is_active, dom, end_m, end_y)
                        )
                    else:
                        cursor.execute(
                            "INSERT INTO fixed_costs (title, amount, category, is_active, day_of_month) VALUES (?, ?, ?, ?, ?)",
                            (str(title), amount, str(category), is_active, dom)
                        )

            # 4. RECURRING EXPENSES / RECURRINGEXPENSES
            rec_list = data.get("recurring_expenses") or data.get("recurringExpenses") or []
            if rec_list:
                cursor.execute("DELETE FROM recurring_expenses")
                for item in rec_list:
                    title = item.get("title") or item.get("name") or "Ausgabe"
                    try:
                        amount = float(item.get("amount", 0.0))
                    except (ValueError, TypeError):
                        amount = 0.0
                    category = item.get("category") or "Sonstiges"

                    freq = item.get("frequency")
                    if not freq:
                        interval = item.get("intervalMonths", 1)
                        if interval == 3: freq = "QUARTERLY"
                        elif interval == 12: freq = "YEARLY"
                        else: freq = "MONTHLY"

                    month_spec = item.get("month_specified") or item.get("startMonth") or item.get("specificMonth") or 0
                    try: month_spec = int(month_spec)
                    except: month_spec = 0

                    active_val = item.get("is_active")
                    if active_val is None:
                        active_val = item.get("isActive", True)
                    is_active = 1 if bool(active_val) else 0

                    start_m = int(item.get("start_month") or item.get("startMonth") or 1)
                    start_y = int(item.get("start_year") or item.get("startYear") or today.year)
                    end_m = int(item.get("end_month") or item.get("endMonth") or 0)
                    end_y = int(item.get("end_year") or item.get("endYear") or 0)
                    note = str(item.get("note") or item.get("notes") or "")
                    due_m = str(item.get("due_months") or item.get("dueMonths") or "")

                    cursor.execute("PRAGMA table_info(recurring_expenses)")
                    cols = [r[1] for r in cursor.fetchall()]
                    if "due_months" in cols:
                        cursor.execute(
                            "INSERT INTO recurring_expenses (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            (str(title), amount, str(category), str(freq), month_spec, is_active, start_m, start_y, end_m, end_y, note, due_m)
                        )
                    elif "start_month" in cols:
                        cursor.execute(
                            "INSERT INTO recurring_expenses (title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            (str(title), amount, str(category), str(freq), month_spec, is_active, start_m, start_y, end_m, end_y, note)
                        )
                    else:
                        cursor.execute(
                            "INSERT INTO recurring_expenses (title, amount, category, frequency, month_specified, is_active) VALUES (?, ?, ?, ?, ?, ?)",
                            (str(title), amount, str(category), str(freq), month_spec, is_active)
                        )

            # 5. INVOICES
            inv_list = data.get("invoices") or []
            if inv_list:
                cursor.execute("DELETE FROM invoices")
                for item in inv_list:
                    vendor = item.get("vendor") or item.get("title") or "Rechnungssteller"
                    desc = item.get("description") or item.get("notes") or item.get("title") or "Rechnung"
                    try:
                        amount = float(item.get("amount", 0.0))
                    except (ValueError, TypeError):
                        amount = 0.0

                    due_date = item.get("due_date")
                    due_m = item.get("month") or item.get("dueMonth") or today.month
                    due_y = item.get("year") or item.get("dueYear") or today.year
                    due_d = item.get("day") or item.get("dueDay") or 1
                    try: due_m = int(due_m)
                    except: due_m = today.month
                    try: due_y = int(due_y)
                    except: due_y = today.year
                    try: due_d = int(due_d)
                    except: due_d = 1

                    if not due_date:
                        due_date = f"{due_y:04d}-{due_m:02d}-{due_d:02d}"

                    category = item.get("category") or "Rechnungen"

                    paid_val = item.get("is_paid")
                    if paid_val is None:
                        paid_val = item.get("isPaid", False)
                    is_paid = 1 if bool(paid_val) else 0

                    cursor.execute(
                        "INSERT INTO invoices (vendor, description, amount, due_date, category, is_paid, month, year) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        (str(vendor), str(desc), amount, str(due_date), str(category), is_paid, due_m, due_y)
                    )

            # 6. PFLEGEGELD
            pf_list = data.get("pflegegeld") or []
            if pf_list:
                cursor.execute("DELETE FROM pflegegeld")
                for item in pf_list:
                    cursor.execute("""
                        INSERT INTO pflegegeld (beguenstigter, ueberweiser, pflegegrad, gesamtpflegegeld, pflegehilfsmittel, anteil_prozent, ergebnis, gueltigkeit, rhythmus, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, (
                        str(item.get("beguenstigter", "")),
                        str(item.get("ueberweiser", "")),
                        str(item.get("pflegegrad", "Pflegegrad 1")),
                        float(item.get("gesamtpflegegeld", 0.0)),
                        float(item.get("pflegehilfsmittel", 0.0)),
                        float(item.get("anteil_prozent", 100.0)),
                        float(item.get("ergebnis", 0.0)),
                        str(item.get("gueltigkeit", "")),
                        str(item.get("rhythmus", "Monatlich")),
                        int(item.get("is_active", 1))
                    ))

            conn.commit()


HELVETICA_WIDTHS = {
    ' ': 278, '!': 278, '"': 355, '#': 556, '$': 556, '%': 889, '&': 667, "'": 222,
    '(': 333, ')': 333, '*': 389, '+': 584, ',': 278, '-': 333, '.': 278, '/': 278,
    ':': 278, ';': 278, '<': 584, '=': 584, '>': 584, '?': 556, '@': 1015,
    '[': 333, '\\': 278, ']': 333, '^': 469, '_': 556, '`': 333,
    'A': 667, 'B': 667, 'C': 722, 'D': 722, 'E': 667, 'F': 611, 'G': 778, 'H': 722,
    'I': 278, 'J': 500, 'K': 667, 'L': 556, 'M': 833, 'N': 722, 'O': 778, 'P': 667,
    'Q': 778, 'R': 722, 'S': 667, 'T': 611, 'U': 722, 'V': 667, 'W': 944, 'X': 667,
    'Y': 667, 'Z': 611,
    'a': 556, 'b': 556, 'c': 500, 'd': 556, 'e': 556, 'f': 278, 'g': 556, 'h': 556,
    'i': 222, 'j': 222, 'k': 500, 'l': 222, 'm': 833, 'n': 556, 'o': 556, 'p': 556,
    'q': 556, 'r': 333, 's': 500, 't': 278, 'u': 556, 'v': 500, 'w': 722, 'x': 500,
    'y': 500, 'z': 500, '{': 334, '|': 260, '}': 334, '~': 584,
    '€': 556, 'ä': 556, 'ö': 556, 'ü': 556, 'Ä': 667, 'Ö': 778, 'Ü': 722, 'ß': 556
}

HELVETICA_BOLD_WIDTHS = {
    ' ': 278, '!': 333, '"': 474, '#': 556, '$': 556, '%': 889, '&': 722, "'": 238,
    '(': 333, ')': 333, '*': 389, '+': 584, ',': 278, '-': 333, '.': 278, '/': 278,
    ':': 333, ';': 333, '<': 584, '=': 584, '>': 584, '?': 556, '@': 1015,
    '[': 333, '\\': 278, ']': 333, '^': 584, '_': 556, '`': 333,
    'A': 722, 'B': 722, 'C': 722, 'D': 722, 'E': 667, 'F': 611, 'G': 778, 'H': 778,
    'I': 278, 'J': 556, 'K': 722, 'L': 611, 'M': 833, 'N': 722, 'O': 778, 'P': 667,
    'Q': 778, 'R': 722, 'S': 667, 'T': 611, 'U': 722, 'V': 667, 'W': 1000, 'X': 667,
    'Y': 667, 'Z': 667,
    'a': 556, 'b': 611, 'c': 556, 'd': 611, 'e': 556, 'f': 333, 'g': 611, 'h': 611,
    'i': 278, 'j': 278, 'k': 556, 'l': 278, 'm': 889, 'n': 611, 'o': 611, 'p': 611,
    'q': 611, 'r': 389, 's': 556, 't': 333, 'u': 611, 'v': 556, 'w': 778, 'x': 556,
    'y': 556, 'z': 500, '{': 389, '|': 280, '}': 389, '~': 584,
    '€': 556, 'ä': 556, 'ö': 611, 'ü': 611, 'Ä': 722, 'Ö': 778, 'Ü': 722, 'ß': 611
}


def fmt_de(val):
    return f"{safe_float(val):,.2f} €".replace(",", "X").replace(".", ",").replace("X", ".")


class DIN5008PDFGenerator:
    """
    Pure Python PDF Generator following DIN 5008 standards for German documents.
    Generates A4 vector PDF documents with logo, margins, header info block,
    betreff, formatted tables, sub-totals, summary cards, and page footers.
    Does NOT require external libraries like ReportLab or FPDF.
    """
    def __init__(self, month_name, year, incomes, fixed_costs, recurring, invoices, pflegegeld, tot_inc, tot_exp, balance, month_int=1):
        self.month_name = month_name
        self.year = safe_int(year, 2026)
        self.month_int = safe_int(month_int, 1)
        self.incomes = incomes or []
        self.fixed_costs = fixed_costs or []
        self.recurring = recurring or []
        self.invoices = invoices or []
        self.pflegegeld = pflegegeld or []
        self.tot_inc = safe_float(tot_inc, 0.0)
        self.tot_exp = safe_float(tot_exp, 0.0)
        self.balance = safe_float(balance, 0.0)
        
        self.pages = []
        self.current_stream = []
        self.left = 70.87    # 25 mm margin (DIN 5008)
        self.right = 538.59   # 20 mm margin (595.28 - 56.69)
        self.width = self.right - self.left
        self.bottom_margin = 65.0
        self.y = 765.0

    def _escape(self, text):
        charmap = {
            '€': '\x80', 'ä': '\xe4', 'ö': '\xf6', 'ü': '\xfc',
            'Ä': '\xc4', 'Ö': '\xd6', 'Ü': '\xdc', 'ß': '\xdf',
            '•': ' - ', '–': '-', '—': '-', '“': '"', '”': '"',
            '„': '"', '‘': "'", '’': "'", '…': '...', '°': '\xb0',
            '§': '\xa7', '©': '\xa9', '®': '\xae',
            'é': '\xe9', 'è': '\xe8', 'ê': '\xea', 'á': '\xe1', 'à': '\xe0',
            'ñ': '\xf1', 'ç': '\xe7'
        }
        res = []
        for c in str(text):
            if c in charmap:
                res.append(charmap[c])
            elif ord(c) < 128:
                res.append(c)
            elif ord(c) <= 255:
                res.append(c)
            else:
                pass
        raw = ''.join(res)
        escaped = []
        for ch in raw:
            if ch in ['\\', '(', ')']:
                escaped.append('\\' + ch)
            elif ord(ch) >= 128:
                escaped.append(f'\\{ord(ch):03o}')
            else:
                escaped.append(ch)
        return ''.join(escaped)

    def _string_width(self, text, font, size):
        table = HELVETICA_BOLD_WIDTHS if font == 'F2' else HELVETICA_WIDTHS
        def_w = 600 if font == 'F2' else 556
        units = sum(table.get(ch, def_w) for ch in str(text))
        return (units / 1000.0) * size

    def _text_left(self, text, font, size, x, y):
        esc = self._escape(text)
        self.current_stream.append(f'BT /{font} {size} Tf {x:.2f} {y:.2f} Td ({esc}) Tj ET')

    def _text_right(self, text, font, size, right_x, y):
        esc = self._escape(text)
        w = self._string_width(text, font, size)
        tx = right_x - w
        self.current_stream.append(f'BT /{font} {size} Tf {tx:.2f} {y:.2f} Td ({esc}) Tj ET')

    def _start_page(self):
        if self.current_stream:
            self.pages.append('\n'.join(self.current_stream))
            self.current_stream = []
        self.y = 765.0
        self._draw_header()

    def _draw_header(self):
        s = self.current_stream
        # Logo (Top-Left) - Embed exact bank logo image if available
        img_info = get_bank_logo_pdf_xobject()
        if img_info:
            img_w, img_h, _ = img_info
            logo_h = 28.0
            logo_w = logo_h * (img_w / float(img_h)) if img_h else 28.0
        else:
            logo_w, logo_h = 28.0, 28.0

        # Vertically center logo with left text block, shifted slightly higher
        text_center_y = self.y - 13.0
        logo_y = text_center_y - (logo_h / 2.0) + 4.0
        s.append(f'q {logo_w:.2f} 0 0 {logo_h:.2f} {self.left:.2f} {logo_y:.2f} cm /Im1 Do Q')
        
        # Logo Text next to logo
        text_x = self.left + max(32.0, logo_w + 6.0)
        s.append('0.0 0.25 0.70 rg')
        self._text_left('HAUSHALTSBUCH', 'F2', 12.5, text_x, self.y - 13)
        s.append('0.4 0.4 0.4 rg')
        self._text_left('Monatsauswertung nach DIN 5008', 'F1', 8.5, text_x, self.y - 23)
        
        # Info Block Top-Right (DIN 5008)
        now_str = datetime.datetime.now().strftime('%d.%m.%Y')
        s.append('0.0 0.25 0.70 rg')
        self._text_right('MONATSAUSWERTUNG', 'F2', 9, self.right, self.y - 4)
        s.append('0.2 0.2 0.2 rg')
        self._text_right(f'Zeitraum: {self.month_name} {self.year}', 'F1', 8.5, self.right, self.y - 15)
        self._text_right(f'Erstellt am: {now_str}', 'F1', 8.5, self.right, self.y - 25)
        
        # Sender Line (DIN 5008 Absenderzeile)
        self.y -= 38
        s.append('0.5 0.5 0.5 rg')
        self._text_left('Haushaltsbuch - Privater Haushalt - Monatsauswertung', 'F1', 7.5, self.left, self.y)
        
        # Line Divider
        self.y -= 5
        s.append('0.8 0.8 0.8 RG 0.75 w')
        s.append(f'{self.left} {self.y} m {self.right} {self.y} l S')
        
        # Betreffzeile (Subject Line)
        self.y -= 22
        s.append('0.1 0.1 0.1 rg')
        self._text_left(f'Monatsauswertung für {self.month_name} {self.year}', 'F2', 12, self.left, self.y)
        
        self.y -= 18

    def _check_space(self, needed):
        if self.y - needed < self.bottom_margin:
            self._start_page()

    def _draw_table(self, sec_title, headers, rows, col_widths, total_label, total_val, is_expense=False):
        if not rows and total_val == 0.0:
            return
        self._check_space(35 + len(rows) * 14)
        s = self.current_stream
        
        # Section Title (kräftiges Blau)
        s.append('0.0 0.25 0.70 rg')
        self._text_left(sec_title, 'F2', 9.5, self.left, self.y)
        self.y -= 13
        
        # Header Row Background (frisches DIN 5008 Blau-Hell)
        s.append('0.88 0.92 0.97 rg')
        s.append(f'{self.left} {self.y - 11} {self.width} 14 re f')
        s.append('0.2 0.2 0.2 rg')
        
        cx = self.left
        for idx, h in enumerate(headers):
            w = col_widths[idx]
            col_x_right = cx + w
            is_right = ('Betrag' in h or ' (€)' in h or h == 'Gebucht' or (not is_expense and idx == len(headers) - 1))
            if is_right:
                self._text_right(h, 'F2', 7.5, col_x_right - 4, self.y - 8)
            else:
                self._text_left(h, 'F2', 7.5, cx + 4, self.y - 8)
            cx += w
            
        self.y -= 14
        
        # Rows
        row_count = 0
        for r in rows:
            self._check_space(15)
            s = self.current_stream
            if row_count % 2 == 1:
                s.append('0.91 0.94 0.98 rg')
                s.append(f'{self.left} {self.y - 10} {self.width} 13 re f')
            
            cx = self.left
            for idx, val in enumerate(r):
                w = col_widths[idx]
                col_x_right = cx + w
                val_str = str(val)
                header_name = headers[idx] if idx < len(headers) else ""
                
                is_right = ('Betrag' in header_name or ' (€)' in header_name or header_name == 'Gebucht' or (not is_expense and idx == len(r) - 1))
                if is_right:
                    if 'Betrag' in header_name and is_expense:
                        s.append('0.85 0.15 0.15 rg')
                    elif header_name == 'Gebucht':
                        s.append('0.3 0.3 0.3 rg')
                    else:
                        s.append('0.15 0.15 0.15 rg')
                    self._text_right(val_str, 'F2' if 'Betrag' in header_name else 'F1', 7.5, col_x_right - 4, self.y - 8)
                else:
                    s.append('0.15 0.15 0.15 rg')
                    self._text_left(val_str[:34], 'F1', 7.5, cx + 4, self.y - 8)
                cx += w
            self.y -= 13
            row_count += 1
            
        # Subtotal
        self._check_space(18)
        s = self.current_stream
        s.append('0.75 0.75 0.75 RG 0.5 w')
        s.append(f'{self.left} {self.y} m {self.right} {self.y} l S')
        self.y -= 12
        s.append('0.1 0.1 0.1 rg')
        self._text_left(total_label, 'F2', 8.0, self.left + 4, self.y)
        
        # Align subtotal amount strictly with Betrag / Auszahlung column
        betrag_col_idx = -1
        for i_h, h_title in enumerate(headers):
            if 'Betrag' in h_title or 'Auszahlung' in h_title:
                betrag_col_idx = i_h
        if betrag_col_idx == -1:
            betrag_col_idx = len(headers) - 1
            
        betrag_right_x = self.left + sum(col_widths[:betrag_col_idx+1]) - 4
        if is_expense:
            tot_str = f"-{fmt_de(total_val)}"
            s.append('0.85 0.15 0.15 rg')
        else:
            tot_str = fmt_de(total_val)
            s.append('0.1 0.1 0.1 rg')
        self._text_right(tot_str, 'F2', 8.0, betrag_right_x, self.y)
        self.y -= 16

    def _draw_summary_box(self):
        self._check_space(60)
        s = self.current_stream
        box_h = 48
        box_y = self.y - box_h
        
        # Card Background & kräftiges Blau Border
        s.append('0.96 0.97 0.99 rg')
        s.append(f'{self.left} {box_y} {self.width} {box_h} re f')
        s.append('0.0 0.25 0.70 RG 1.2 w')
        s.append(f'{self.left} {box_y} {self.width} {box_h} re S')
        
        col_w = self.width / 3.0
        
        # Spalte 1: Einnahmen
        x0 = self.left + 12
        s.append('0.0 0.25 0.70 rg')
        self._text_left('EINNAHMEN', 'F2', 8.0, x0, self.y - 16)
        s.append('0.1 0.5 0.1 rg')
        self._text_left(fmt_de(self.tot_inc), 'F2', 11.5, x0, self.y - 35)
        
        # Trennlinie 1
        div1_x = self.left + col_w
        s.append('0.82 0.86 0.92 RG 0.75 w')
        s.append(f'{div1_x} {box_y + 6} m {div1_x} {self.y - 6} l S')
        
        # Spalte 2: Ausgaben gesamt
        x1 = self.left + col_w + 12
        s.append('0.3 0.3 0.3 rg')
        self._text_left('AUSGABEN GESAMT', 'F2', 8.0, x1, self.y - 16)
        s.append('0.85 0.15 0.15 rg')
        self._text_left(f"-{fmt_de(self.tot_exp)}", 'F2', 11.5, x1, self.y - 35)
        
        # Trennlinie 2
        div2_x = self.left + 2 * col_w
        s.append('0.82 0.86 0.92 RG 0.75 w')
        s.append(f'{div2_x} {box_y + 6} m {div2_x} {self.y - 6} l S')
        
        # Spalte 3: Netto-Saldo (kräftiges Blau Hintergrund)
        x2 = self.left + 2 * col_w + 12
        bal_bg = '0.00 0.25 0.70 rg' if self.balance >= 0 else '0.70 0.12 0.12 rg'
        
        s.append(bal_bg)
        s.append(f'{div2_x + 3} {box_y + 3} {col_w - 6} {box_h - 6} re f')
        
        s.append('0.88 0.94 1.00 rg')
        self._text_left('NETTO-SALDO', 'F2', 8.0, x2, self.y - 16)
        s.append('1.00 1.00 1.00 rg')
        self._text_left(fmt_de(self.balance), 'F2', 12.0, x2, self.y - 35)
        
        self.y -= box_h + 12

    def generate_pdf(self):
        self._start_page()
        
        # 1. Summary Box (Gesamtergebnis & Saldo) zuerst anzeigen
        self._draw_summary_box()

        # 2. Einnahmen
        inc_rows = []
        for r in self.incomes:
            if safe_int(r[4], 1) == 1:
                is_rec = safe_int(r[7] if len(r) > 7 else 1, 1)
                if is_rec == 1:
                    rhythm_str = "Monatlich"
                else:
                    r_m = safe_int(r[5] if len(r) > 5 else 0, 0)
                    r_y = safe_int(r[6] if len(r) > 6 else 0, 0)
                    m_name = get_german_month_name(r_m) if r_m > 0 else ""
                    rhythm_str = f"Sonderbetrag ({m_name} {r_y})" if r_m > 0 else "Einmalig"
                inc_rows.append([r[1], r[3], rhythm_str, fmt_de(r[2])])
        inc_rows.sort(key=lambda x: (x[1].lower(), x[0].lower()))
        self._draw_table('1. EINNAHMEN', ['Bezeichnung', 'Kategorie', 'Rhythmus', 'Betrag (€)'], inc_rows, [180, 130, 80, 77.72], 'Summe Einnahmen:', self.tot_inc, is_expense=False)
        
        # 3. Fixkosten
        fix_rows = []
        tot_fix = 0.0
        for r in self.fixed_costs:
            if safe_int(r[4], 1) == 1:
                e_m = safe_int(r[6] if len(r) > 6 else 0, 0)
                e_y = safe_int(r[7] if len(r) > 7 else 0, 0)
                if check_fixed_cost_due(e_m, e_y, self.month_int, self.year):
                    dom = safe_int(r[5] if len(r) > 5 else 1, 1)
                    rhythm_str = f"zum {dom}. des Monats"
                    if e_y > 0 and e_m > 0:
                        rhythm_str += f" (bis {get_german_month_name(e_m)[:3]} {e_y})"
                    amt = safe_float(r[2], 0.0)
                    fix_rows.append([r[1], r[3], rhythm_str, f"-{fmt_de(amt)}", '[ ]'])
                    tot_fix += amt
        fix_rows.sort(key=lambda x: (x[1].lower(), x[0].lower()))
        self._draw_table('2. FIXKOSTEN', ['Bezeichnung', 'Kategorie', 'Rhythmus', 'Betrag (€)', 'Gebucht'], fix_rows, [160, 115, 75, 70.72, 47], 'Summe Fixkosten:', tot_fix, is_expense=True)

        # 4. Wiederkehrende Ausgaben
        rec_rows = []
        tot_rec = 0.0
        sorted_rec = sorted(self.recurring, key=lambda r: get_recurring_due_key(r, self.month_int, self.year))
        for r in sorted_rec:
            is_act = (safe_int(r[6], 1) == 1)
            s_m = safe_int(r[7] if len(r) > 7 else 1, 1)
            s_y = safe_int(r[8] if len(r) > 8 else 2026, 2026)
            e_m = safe_int(r[9] if len(r) > 9 else 0, 0)
            e_y = safe_int(r[10] if len(r) > 10 else 0, 0)
            due_m = r[12] if len(r) > 12 else ""
            amt = safe_float(r[2], 0.0)
            if is_act and check_recurring_due(r[4], r[5], s_m, s_y, e_m, e_y, self.month_int, self.year, due_m):
                freq_str = get_recurring_rhythm_display(r)
                rec_rows.append([r[1], r[3], freq_str, f"-{fmt_de(amt)}", '[ ]'])
                tot_rec += amt
        self._draw_table('3. WIEDERKEHRENDE AUSGABEN', ['Bezeichnung', 'Kategorie', 'Rhythmus', 'Betrag (€)', 'Gebucht'], rec_rows, [160, 115, 75, 70.72, 47], 'Summe Wiederkehrend:', tot_rec, is_expense=True)

        # 5. Rechnungen
        inv_rows = []
        for r in self.invoices:
            due_str = str(r[4]) if (len(r) > 4 and r[4]) else "-"
            inv_rows.append([f'{r[1]} - {r[2]}', r[5], due_str, f"-{fmt_de(r[3])}", '[ ]'])
        inv_rows.sort(key=lambda x: (x[1].lower(), x[0].lower()))
        tot_inv = sum(safe_float(r[3]) for r in self.invoices)
        self._draw_table('4. RECHNUNGEN', ['Rechnungssteller / Titel', 'Kategorie', 'Fälligkeit', 'Betrag (€)', 'Gebucht'], inv_rows, [160, 115, 75, 70.72, 47], 'Summe Rechnungen:', tot_inv, is_expense=True)

        # 6. Pflegegeld Section (Gesondert)
        active_pf = [p for p in self.pflegegeld if safe_int(p[10], 1) == 1]
        if active_pf:
            pf_rows = []
            tot_pf = 0.0
            for p in active_pf:
                label = f"{p[1]} ({p[2]})" if p[2] else p[1]
                p_grad = str(p[3]) if len(p) > 3 else ""
                p_gesamt = fmt_de(p[4])
                p_hilfs = fmt_de(p[5])
                p_anteil = f"{safe_float(p[6]):,.1f} %".replace(".", ",")
                p_rhythm = p[9] if (len(p) > 9 and p[9]) else "Monatlich"
                erg = safe_float(p[7], 0.0)
                pf_rows.append([label, p_grad, p_gesamt, p_hilfs, p_anteil, p_rhythm, fmt_de(erg)])
                tot_pf += erg
            self._draw_table('GESONDERTER BEREICH - PFLEGEGELD (NICHT IN DEN EINNAHMEN ENTHALTEN)',
                             ['Begünstigter / Überweiser', 'Pflegegrad', 'Gesamt (€)', 'Hilfsmittel (€)', 'Anteil (%)', 'Rhythmus', 'Auszahlung (€)'],
                             pf_rows, [120, 55, 60, 60, 45, 55, 72.72], 'Summe Pflegegeld (gesondert):', tot_pf, is_expense=False)

        # Build PDF Bytes
        if self.current_stream:
            self.pages.append('\n'.join(self.current_stream))
            
        objects = []
        def add_obj(c):
            objects.append(c)
            # Objects 1 and 2 are Catalog and Pages, so object IDs start at 3
            return len(objects) + 2
            
        f1_id = add_obj('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>')
        f2_id = add_obj('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>')
        proc_id = add_obj('[ /PDF /Text /ImageB /ImageC /ImageI ]')
        
        # Image XObject for Bank Logo
        img_info = get_bank_logo_pdf_xobject()
        img_obj_id = None
        if img_info:
            img_w, img_h, z_data = img_info
            header_str = f'<< /Type /XObject /Subtype /Image /Width {img_w} /Height {img_h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length {len(z_data)} >>\nstream\n'
            img_bytes = header_str.encode('latin1') + z_data + b'\nendstream'
            img_obj_id = add_obj(img_bytes)

        xobj_res = f'/XObject << /Im1 {img_obj_id} 0 R >> ' if img_obj_id else ''
        res_dict = f'<< /Font << /F1 {f1_id} 0 R /F2 {f2_id} 0 R >> {xobj_res}/ProcSet {proc_id} 0 R >>'

        active_pf = [p for p in self.pflegegeld if safe_int(p[10], 1) == 1]
        tot_pf = sum(safe_float(p[7], 0.0) for p in active_pf)
        gesamtsaldo = self.balance + tot_pf
        fn_str = f'* Gesamtsaldo: {gesamtsaldo:,.2f} \u20ac (Verf\u00fcgbares Saldo {self.balance:,.2f} \u20ac + Pflegegeld {tot_pf:,.2f} \u20ac)'
        fn_esc = self._escape(fn_str)

        page_ids = []
        for p_idx, page_cmd in enumerate(self.pages):
            footer_cmd = [
                '0.35 0.35 0.35 rg',
                f'BT /F1 7.5 Tf {self.left} 50 Td ({fn_esc}) Tj ET',
                '0.7 0.7 0.7 RG 0.5 w',
                f'{self.left} 42 m {self.right} 42 l S',
                '0.4 0.4 0.4 rg',
                'BT /F1 8 Tf',
                f'{self.left} 30 Td ({self._escape(f"Haushaltsbuch - Monatsauswertung {self.month_name} {self.year}")}) Tj ET',
                f'BT /F1 8 Tf',
                f'{self.right - 80} 30 Td ({self._escape(f"Seite {p_idx+1} von {len(self.pages)}")}) Tj ET'
            ]
            full_cmd = page_cmd + '\n' + '\n'.join(footer_cmd)
            stream_bytes = full_cmd.encode('latin1')
            stream_obj_id = add_obj(f'<< /Length {len(stream_bytes)} >>\nstream\n' + full_cmd + '\nendstream')
            page_obj_str = f'<< /Type /Page /Parent 2 0 R /Resources {res_dict} /MediaBox [0 0 595.28 841.89] /Contents {stream_obj_id} 0 R >>'
            page_ids.append(add_obj(page_obj_str))
            
        kids_str = ' '.join([f'{pid} 0 R' for pid in page_ids])
        pages_obj = f'<< /Type /Pages /Kids [{kids_str}] /Count {len(page_ids)} >>'
        catalog_obj = '<< /Type /Catalog /Pages 2 0 R >>'
        
        all_objs = [None, catalog_obj, pages_obj] + objects
        
        out = [b'%PDF-1.4\n%\xe2\xe3\xcf\xd3\n']
        offsets = {}
        pos = len(out[0])
        for i in range(1, len(all_objs)):
            offsets[i] = pos
            obj_val = all_objs[i]
            if isinstance(obj_val, str):
                obj_data = f'{i} 0 obj\n{obj_val}\nendobj\n'.encode('latin1')
            else:
                obj_data = f'{i} 0 obj\n'.encode('latin1') + obj_val + b'\nendobj\n'
            out.append(obj_data)
            pos += len(obj_data)
            
        xref_pos = pos
        xref_str = f'xref\n0 {len(all_objs)}\n0000000000 65535 f \n'
        for i in range(1, len(all_objs)):
            xref_str += f'{offsets[i]:010d} 00000 n \n'
            
        xref_str += f'trailer\n<< /Size {len(all_objs)} /Root 1 0 R >>\nstartxref\n{xref_pos}\n%%EOF\n'
        out.append(xref_str.encode('latin1'))
        return b''.join(out)


APP_THEMES = {
    "android_material": {
        "name": "Android Material You (M3)",
        "icon": "🤖",
        "bg": "#f0f4f8",
        "card": "#ffffff",
        "border": "#d8e2ea",
        "primary": "#006780",
        "accent": "#004f63",
        "success": "#1b6d23",
        "danger": "#ba1a1a",
        "text_p": "#191c1e",
        "text_s": "#40484c",
        "text_m": "#70787d",
        "tree_even": "#ffffff",
        "tree_odd": "#f0f4f8",
        "tab_bg": "#e1e2ec",
        "top_bg": "#ffffff",
        "header_bg": "#e7f0f8",
        "select_bg": "#c2e7ff",
        "select_fg": "#001d33"
    },
    "android_dark": {
        "name": "Android Material Dark",
        "icon": "📱",
        "bg": "#111318",
        "card": "#1d2024",
        "border": "#2c3036",
        "primary": "#83d2e8",
        "accent": "#52b8d0",
        "success": "#8ed98e",
        "danger": "#ffb4ab",
        "text_p": "#e2e2e9",
        "text_s": "#c2c7ce",
        "text_m": "#8c9198",
        "tree_even": "#1d2024",
        "tree_odd": "#15181c",
        "tab_bg": "#2b2f36",
        "top_bg": "#1d2024",
        "header_bg": "#2b2f36",
        "select_bg": "#004f63",
        "select_fg": "#c2e7ff"
    },
    "win11_slate": {
        "name": "Windows 11 Slate",
        "icon": "🟦",
        "bg": "#f3f4f6",
        "card": "#ffffff",
        "border": "#e2e8f0",
        "primary": "#2563eb",
        "accent": "#1d4ed8",
        "success": "#059669",
        "danger": "#dc2626",
        "text_p": "#0f172a",
        "text_s": "#334155",
        "text_m": "#64748b",
        "tree_even": "#ffffff",
        "tree_odd": "#f8fafc",
        "tab_bg": "#e2e8f0",
        "top_bg": "#ffffff",
        "header_bg": "#f1f5f9",
        "select_bg": "#dbeafe",
        "select_fg": "#1e40af"
    },
    "macos_ocean": {
        "name": "macOS Ocean Blue",
        "icon": "🌊",
        "bg": "#f0f9ff",
        "card": "#ffffff",
        "border": "#bae6fd",
        "primary": "#0284c7",
        "accent": "#0369a1",
        "success": "#0d9488",
        "danger": "#e11d48",
        "text_p": "#0c4a6e",
        "text_s": "#0369a1",
        "text_m": "#0284c7",
        "tree_even": "#ffffff",
        "tree_odd": "#f0f9ff",
        "tab_bg": "#e0f2fe",
        "top_bg": "#ffffff",
        "header_bg": "#e0f2fe",
        "select_bg": "#bae6fd",
        "select_fg": "#0369a1"
    },
    "emerald_forest": {
        "name": "Emerald Forest",
        "icon": "🌲",
        "bg": "#f0fdf4",
        "card": "#ffffff",
        "border": "#bbf7d0",
        "primary": "#15803d",
        "accent": "#166534",
        "success": "#16a34a",
        "danger": "#dc2626",
        "text_p": "#064e3b",
        "text_s": "#14532d",
        "text_m": "#15803d",
        "tree_even": "#ffffff",
        "tree_odd": "#f0fdf4",
        "tab_bg": "#dcfce7",
        "top_bg": "#ffffff",
        "header_bg": "#dcfce7",
        "select_bg": "#bbf7d0",
        "select_fg": "#14532d"
    },
    "dark_slate": {
        "name": "Dark Mode Slate",
        "icon": "🌙",
        "bg": "#0f172a",
        "card": "#1e293b",
        "border": "#334155",
        "primary": "#3b82f6",
        "accent": "#60a5fa",
        "success": "#10b981",
        "danger": "#f43f5e",
        "text_p": "#f8fafc",
        "text_s": "#cbd5e1",
        "text_m": "#94a3b8",
        "tree_even": "#1e293b",
        "tree_odd": "#0f172a",
        "tab_bg": "#334155",
        "top_bg": "#1e293b",
        "header_bg": "#334155",
        "select_bg": "#1e3a8a",
        "select_fg": "#ffffff"
    },
    "royal_violet": {
        "name": "Royal Violet",
        "icon": "👑",
        "bg": "#faf5ff",
        "card": "#ffffff",
        "border": "#e9d5ff",
        "primary": "#7e22ce",
        "accent": "#6b21a8",
        "success": "#059669",
        "danger": "#e11d48",
        "text_p": "#3b0764",
        "text_s": "#581c87",
        "text_m": "#7e22ce",
        "tree_even": "#ffffff",
        "tree_odd": "#faf5ff",
        "tab_bg": "#f3e8ff",
        "top_bg": "#ffffff",
        "header_bg": "#f3e8ff",
        "select_bg": "#e9d5ff",
        "select_fg": "#581c87"
    },
    "nordic_mint": {
        "name": "Nordic Mint",
        "icon": "❄️",
        "bg": "#f1f5f9",
        "card": "#ffffff",
        "border": "#99f6e4",
        "primary": "#0d9488",
        "accent": "#0f766e",
        "success": "#10b981",
        "danger": "#f43f5e",
        "text_p": "#0f172a",
        "text_s": "#115e59",
        "text_m": "#0d9488",
        "tree_even": "#ffffff",
        "tree_odd": "#f0fdfa",
        "tab_bg": "#ccfbf1",
        "top_bg": "#ffffff",
        "header_bg": "#ccfbf1",
        "select_bg": "#99f6e4",
        "select_fg": "#115e59"
    },
    "cyber_minimal": {
        "name": "Cyber Minimal OLED",
        "icon": "⚡",
        "bg": "#09090b",
        "card": "#18181b",
        "border": "#27272a",
        "primary": "#22c55e",
        "accent": "#16a34a",
        "success": "#22c55e",
        "danger": "#ef4444",
        "text_p": "#f4f4f5",
        "text_s": "#a1a1aa",
        "text_m": "#71717a",
        "tree_even": "#18181b",
        "tree_odd": "#09090b",
        "tab_bg": "#27272a",
        "top_bg": "#18181b",
        "header_bg": "#27272a",
        "select_bg": "#15803d",
        "select_fg": "#ffffff"
    },
    "nordic_glass": {
        "name": "Nordic Clean Indigo",
        "icon": "💎",
        "bg": "#f8fafc",
        "card": "#ffffff",
        "border": "#e2e8f0",
        "primary": "#4f46e5",
        "accent": "#4338ca",
        "success": "#10b981",
        "danger": "#f43f5e",
        "text_p": "#0f172a",
        "text_s": "#475569",
        "text_m": "#64748b",
        "tree_even": "#ffffff",
        "tree_odd": "#f1f5f9",
        "tab_bg": "#e0e7ff",
        "top_bg": "#ffffff",
        "header_bg": "#e0e7ff",
        "select_bg": "#c7d2fe",
        "select_fg": "#3730a3"
    }
}


class HaushaltsbuchApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Haushaltsbuch & Monatsübersicht")
        
        # Initial minimum window size
        self.minsize(900, 600)

        # Set bank logo as window icon & header image if available
        self.logo_img = None
        self.logo_img_small = None
        logo_p = ensure_bank_logo()
        if logo_p:
            try:
                self.logo_img = tk.PhotoImage(file=logo_p)
                self.wm_iconphoto(True, self.logo_img)
                sub_w = max(1, self.logo_img.width() // 28)
                sub_h = max(1, self.logo_img.height() // 28)
                self.logo_img_small = self.logo_img.subsample(sub_w, sub_h)
            except Exception as e:
                print(f"[WARNUNG] Logo PhotoImage Fehler: {e}")

        # Intercept window close to trigger automatic backup before exit
        self.protocol("WM_DELETE_WINDOW", self.on_exit)

        self.db = FinanceDB()

        # Current selected month and year
        today = datetime.date.today()
        self.current_month = today.month
        self.current_year = today.year

        self.setup_styles()
        self.create_widgets()
        self.refresh_all_views()

        # Dynamische Zentrierung des Hauptfensters mittig auf dem Bildschirm
        self.update_idletasks()
        screen_w = self.winfo_screenwidth()
        screen_h = self.winfo_screenheight()

        # Ausgewogene Startgröße (ca. 1280x800 bzw. 80%/75% des Bildschirms) mit ausreichend Abstand zu den Rändern
        win_w = min(1320, max(1050, int(screen_w * 0.8)))
        win_h = min(820, max(680, int(screen_h * 0.72)))

        if screen_w > 0 and screen_h > 0:
            win_w = min(win_w, max(900, screen_w - 100))
            win_h = min(win_h, max(600, screen_h - 140))

        pos_x = max(0, (screen_w - win_w) // 2)
        pos_y = max(0, (screen_h - win_h) // 2)
        self.geometry(f"{win_w}x{win_h}+{pos_x}+{pos_y}")

        # Vorherige temporäre Druckdateien beim Start bereinigen
        self.cleanup_all_temp_files()

    def cleanup_temp_file(self, fpath):
        try:
            if fpath and os.path.exists(fpath):
                os.remove(fpath)
        except Exception:
            pass

    def cleanup_all_temp_files(self):
        try:
            temp_dir = tempfile.gettempdir()
            for fname in os.listdir(temp_dir):
                if fname.startswith("Haushaltsbuch_Druck_") and fname.endswith(".pdf"):
                    try:
                        os.remove(os.path.join(temp_dir, fname))
                    except Exception:
                        pass
        except Exception:
            pass

    def on_exit(self):
        """Wird beim Beenden des Programms aufgerufen. Erstellt eine finale automatische Sicherung und bereinigt Temp-Dateien."""
        try:
            self.db.create_manual_auto_backup()
        except Exception as e:
            print(f"[WARNUNG] Sicherung beim Schließen fehlgeschlagen: {e}")
        self.cleanup_all_temp_files()
        self.destroy()

    def setup_styles(self):
        style = ttk.Style(self)
        style.theme_use("clam")

        saved_theme = self.db.get_setting("app_theme", "android_material")
        t = APP_THEMES.get(saved_theme, APP_THEMES["android_material"])

        self.active_theme_key = saved_theme
        self.bg_color = t["bg"]
        self.card_bg = t["card"]
        self.border_color = t["border"]
        self.primary_color = t["primary"]
        self.accent_color = t["accent"]
        self.success_color = t["success"]
        self.danger_color = t["danger"]
        self.text_primary = t["text_p"]
        self.text_secondary = t["text_s"]
        self.text_muted = t["text_m"]
        self.tree_even_bg = t["tree_even"]
        self.tree_odd_bg = t["tree_odd"]
        self.top_bg = t["top_bg"]

        self.font_family = "Segoe UI" if sys.platform == "win32" else ("SF Pro Text" if sys.platform == "darwin" else "Segoe UI")

        self.configure(bg=self.bg_color)

        style.configure(".", background=self.bg_color, font=(self.font_family, 10), foreground=self.text_primary)
        
        # Notebook / Tabs
        style.configure("TNotebook", background=self.bg_color, borderwidth=0)
        style.configure("TNotebook.Tab", font=(self.font_family, 10, "bold"), padding=[16, 9], background=t["tab_bg"], foreground=self.text_secondary, borderwidth=0, focuscolor="")
        style.map("TNotebook.Tab", background=[("selected", self.primary_color)], foreground=[("selected", "#ffffff")])

        # Cards & Metric Containers
        style.configure("Card.TFrame", background=self.card_bg, relief="flat", borderwidth=1)
        style.configure("Header.TLabel", font=(self.font_family, 13, "bold"), background=self.card_bg, foreground=self.text_primary)
        style.configure("SubHeader.TLabel", font=(self.font_family, 10, "bold"), background=self.card_bg, foreground=self.text_muted)
        style.configure("MetricValue.TLabel", font=(self.font_family, 16, "bold"), background=self.card_bg, foreground=self.success_color)
        style.configure("MetricValueNeg.TLabel", font=(self.font_family, 16, "bold"), background=self.card_bg, foreground=self.danger_color)
        style.configure("MetricValueNeutral.TLabel", font=(self.font_family, 16, "bold"), background=self.card_bg, foreground=self.primary_color)

        # Buttons
        style.configure("TButton", font=(self.font_family, 10), background=t["tab_bg"], foreground=self.text_secondary, borderwidth=0, relief="flat", padding=[12, 7])
        style.map("TButton", background=[("active", t["border"]), ("disabled", "#cbd5e1")], foreground=[("active", self.text_primary)])

        style.configure("Primary.TButton", font=(self.font_family, 10, "bold"), background=self.primary_color, foreground="#ffffff", borderwidth=0, relief="flat", padding=[14, 8])
        style.map("Primary.TButton", background=[("active", self.accent_color), ("disabled", "#94a3b8")])

        exit_bg = "#fef2f2" if saved_theme != "dark_slate" else "#451a1a"
        exit_active = "#fee2e2" if saved_theme != "dark_slate" else "#7f1d1d"
        style.configure("Exit.TButton", font=(self.font_family, 10, "bold"), background=exit_bg, foreground=self.danger_color, borderwidth=0, relief="flat", padding=[12, 7])
        style.map("Exit.TButton", background=[("active", exit_active)])

        # Treeview / Tables
        style.configure("Treeview", font=(self.font_family, 10), rowheight=32, background=self.card_bg, fieldbackground=self.card_bg, foreground=self.text_primary, borderwidth=0)
        style.configure("Treeview.Heading", font=(self.font_family, 10, "bold"), background=t["header_bg"], foreground=self.text_secondary, relief="flat", borderwidth=0)
        style.map("Treeview", background=[("selected", t["select_bg"])], foreground=[("selected", t["select_fg"])])
        style.map("Treeview.Heading", background=[("active", t["border"])])

    def apply_all_tree_zebra_styles(self):
        """Wendet Zebra-Zeilenfarben auf alle Tabellen an."""
        for tree_attr in ["tree_overview", "tree_income", "tree_pflegegeld", "tree_fixed", "tree_recurring", "tree_invoices"]:
            tree = getattr(self, tree_attr, None)
            if tree:
                tree.tag_configure("even", background=self.tree_even_bg, foreground=self.text_primary)
                tree.tag_configure("odd", background=self.tree_odd_bg, foreground=self.text_primary)
                tree.tag_configure("sep", background=self.border_color, foreground=self.text_primary, font=(self.font_family, 10, "bold"))

    def apply_theme(self, theme_key, parent_dlg=None):
        """Wendet das gewählte Farbdesign sofort live an und speichert es dauerhaft in der Datenbank."""
        self.db.set_setting("app_theme", theme_key)
        self.setup_styles()
        self.configure(bg=self.bg_color)

        if hasattr(self, "top_bar") and self.top_bar:
            self.top_bar.config(bg=self.top_bg, highlightbackground=self.border_color)
        if hasattr(self, "lbl_title") and self.lbl_title:
            self.lbl_title.config(bg=self.top_bg, fg=self.primary_color)
        if hasattr(self, "month_frame") and self.month_frame:
            self.month_frame.config(bg=self.top_bg)
        if hasattr(self, "actions_frame") and self.actions_frame:
            self.actions_frame.config(bg=self.top_bg)
        if hasattr(self, "status_bar") and self.status_bar:
            self.status_bar.config(bg=self.card_bg, fg=self.text_muted, highlightbackground=self.border_color)

        # Update zebra tags on existing treeviews
        self.apply_all_tree_zebra_styles()

        self.refresh_all_views()

        if parent_dlg:
            try:
                parent_dlg.destroy()
            except Exception:
                pass
            self.open_settings_dialog()

    def create_widgets(self):
        # Top Bar: Navigation for Month & Year + Global Quick Actions
        self.top_bar = tk.Frame(self, bg=self.top_bg, height=64, highlightbackground=self.border_color, highlightthickness=1, bd=0)
        self.top_bar.pack(side="top", fill="x", padx=0, pady=0)

        if getattr(self, "logo_img_small", None):
            lbl_logo = tk.Label(self.top_bar, image=self.logo_img_small, bg=self.top_bg)
            lbl_logo.pack(side="left", padx=(16, 4), pady=12)
            self.lbl_title = tk.Label(self.top_bar, text="Haushaltsbuch", font=(self.font_family, 16, "bold"), bg=self.top_bg, fg=self.primary_color)
            self.lbl_title.pack(side="left", padx=(4, 16), pady=12)
        else:
            self.lbl_title = tk.Label(self.top_bar, text="🏛️ 📊 Haushaltsbuch", font=(self.font_family, 16, "bold"), bg=self.top_bg, fg=self.primary_color)
            self.lbl_title.pack(side="left", padx=16, pady=12)

        # Month Navigation
        self.month_frame = tk.Frame(self.top_bar, bg=self.top_bg)
        self.month_frame.pack(side="left", padx=20)

        btn_prev = ttk.Button(self.month_frame, text="◄ Zurück", command=self.prev_month)
        btn_prev.pack(side="left", padx=4)

        self.lbl_month_display = tk.Label(
            self.month_frame,
            text="",
            font=(self.font_family, 11, "bold"),
            bg=self.card_bg,
            fg=self.primary_color,
            relief="flat",
            highlightbackground=self.border_color,
            highlightthickness=1,
            bd=0,
            padx=16,
            pady=6,
            cursor="hand2"
        )
        self.lbl_month_display.pack(side="left", padx=8)
        self.lbl_month_display.bind("<Button-1>", self.open_month_picker)

        btn_next = ttk.Button(self.month_frame, text="Weiter ►", command=self.next_month)
        btn_next.pack(side="left", padx=4)

        # Quick Actions
        self.actions_frame = tk.Frame(self.top_bar, bg=self.top_bg)
        self.actions_frame.pack(side="right", padx=16)

        btn_cats = ttk.Button(self.actions_frame, text="🏷️ Kategorien", command=self.open_categories_dialog)
        btn_cats.pack(side="left", padx=4)

        btn_settings = ttk.Button(self.actions_frame, text="⚙️ Einstellungen / Backup", command=self.open_settings_dialog)
        btn_settings.pack(side="left", padx=4)

        btn_exit = ttk.Button(self.actions_frame, text="🚪 Beenden", command=self.on_exit, style="Exit.TButton")
        btn_exit.pack(side="left", padx=4)

        # Main Tab Control
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=16, pady=12)

        # Create Tab Frames
        self.tab_overview = ttk.Frame(self.notebook)
        self.tab_income = ttk.Frame(self.notebook)
        self.tab_fixed = ttk.Frame(self.notebook)
        self.tab_recurring = ttk.Frame(self.notebook)
        self.tab_invoices = ttk.Frame(self.notebook)
        self.tab_print = ttk.Frame(self.notebook)

        self.notebook.add(self.tab_overview, text=" 📈 Monatsübersicht ")
        self.notebook.add(self.tab_income, text=" 💵 Einnahmen ")
        self.notebook.add(self.tab_fixed, text=" 🏠 Fixkosten ")
        self.notebook.add(self.tab_recurring, text=" 🔄 Wiederkehrend ")
        self.notebook.add(self.tab_invoices, text=" 📄 Rechnungen ")
        self.notebook.add(self.tab_print, text=" 🖨️ Druckansicht ")

        # Build individual view screens
        self.build_overview_tab()
        self.build_income_tab()
        self.build_fixed_tab()
        self.build_recurring_tab()
        self.build_invoices_tab()
        self.build_print_tab()

        # Status Bar
        self.status_bar = tk.Label(
            self,
            text=" Bereit",
            anchor="w",
            font=(self.font_family, 9),
            bg="#ffffff",
            fg=self.text_muted,
            highlightbackground="#e2e8f0",
            highlightthickness=1,
            bd=0,
            padx=12,
            pady=6
        )
        self.status_bar.pack(side="bottom", fill="x")

    def update_month_label(self):
        m_name = get_german_month_name(self.current_month)
        self.lbl_month_display.config(text=f"📅 {m_name} {self.current_year} ▾")

    def prev_month(self):
        if self.current_month == 1:
            self.current_month = 12
            self.current_year -= 1
        else:
            self.current_month -= 1
        self.refresh_all_views()

    def next_month(self):
        if self.current_month == 12:
            self.current_month = 1
            self.current_year += 1
        else:
            self.current_month += 1
        self.refresh_all_views()

    def auto_fit_dialog(self, dlg, min_w=380, min_h=200, pad_w=32, pad_h=32):
        """Passt die Fenstergröße dynamisch an den enthaltenen Inhalt an und zentriert es."""
        dlg.update_idletasks()
        req_w = max(min_w, dlg.winfo_reqwidth() + pad_w)
        req_h = max(min_h, dlg.winfo_reqheight() + pad_h)
        
        parent_x = self.winfo_x() if self.winfo_ismapped() else 0
        parent_y = self.winfo_y() if self.winfo_ismapped() else 0
        parent_w = self.winfo_width() if self.winfo_ismapped() else self.winfo_screenwidth()
        parent_h = self.winfo_height() if self.winfo_ismapped() else self.winfo_screenheight()
        
        pos_x = parent_x + (parent_w // 2) - (req_w // 2)
        pos_y = parent_y + (parent_h // 2) - (req_h // 2)
        dlg.geometry(f"{req_w}x{req_h}+{max(0, pos_x)}+{max(0, pos_y)}")

    def open_month_picker(self, event=None):
        dlg = tk.Toplevel(self)
        dlg.title("Monat & Jahr auswählen")
        dlg.resizable(False, False)
        dlg.withdraw()  # Hide until widgets are packed to prevent blank window issues on Linux

        selected_m = tk.IntVar(value=self.current_month)
        selected_y = tk.IntVar(value=self.current_year)

        # Main padded container
        main_container = ttk.Frame(dlg, padding=16)
        main_container.pack(fill="both", expand=True)

        # 1. Year Header Frame (TOP)
        year_frame = ttk.LabelFrame(main_container, text="Jahr auswählen", padding=8)
        year_frame.pack(fill="x", pady=(0, 12))

        def update_year(delta):
            selected_y.set(selected_y.get() + delta)
            lbl_year.config(text=str(selected_y.get()))

        btn_prev_y = ttk.Button(
            year_frame, text="◄ Zurück", width=10,
            command=lambda: update_year(-1)
        )
        btn_prev_y.pack(side="left", padx=4)

        lbl_year = ttk.Label(
            year_frame, text=str(selected_y.get()), font=("Helvetica", 16, "bold"),
            anchor="center"
        )
        lbl_year.pack(side="left", expand=True, fill="x", padx=8)

        btn_next_y = ttk.Button(
            year_frame, text="Weiter ►", width=10,
            command=lambda: update_year(1)
        )
        btn_next_y.pack(side="right", padx=4)

        # 2. Month Grid Frame (MIDDLE)
        month_frame = ttk.LabelFrame(main_container, text="Monat auswählen", padding=8)
        month_frame.pack(fill="both", expand=True, pady=(0, 12))

        month_buttons = []

        def select_month(m_num):
            selected_m.set(m_num)
            for btn, m_i in month_buttons:
                if m_i == m_num:
                    btn.config(bg="#1b5e20", fg="#ffffff", font=("Helvetica", 10, "bold"), relief="solid", bd=2)
                else:
                    btn.config(bg="#ffffff", fg="#111827", font=("Helvetica", 10), relief="groove", bd=1)

        german_months = [
            "Januar", "Februar", "März", "April",
            "Mai", "Juni", "Juli", "August",
            "September", "Oktober", "November", "Dezember"
        ]

        # 4 rows x 3 columns grid layout
        for i, m_name in enumerate(german_months, start=1):
            row = (i - 1) // 3
            col = (i - 1) % 3
            btn = tk.Button(
                month_frame,
                text=m_name,
                font=("Helvetica", 10),
                bg="#ffffff",
                fg="#111827",
                activebackground="#2e7d32",
                activeforeground="#ffffff",
                relief="groove",
                cursor="hand2",
                padx=6,
                pady=8,
                command=lambda m=i: select_month(m)
            )
            btn.grid(row=row, column=col, padx=4, pady=4, sticky="nsew")
            month_frame.columnconfigure(col, weight=1)
            month_frame.rowconfigure(row, weight=1)
            month_buttons.append((btn, i))

        select_month(selected_m.get())

        # 3. Bottom Action Bar: Heute | Abbrechen | Übernehmen
        btn_bar = ttk.Frame(main_container)
        btn_bar.pack(fill="x", side="bottom")

        def do_today():
            today = datetime.date.today()
            self.current_month = today.month
            self.current_year = today.year
            self.refresh_all_views()
            dlg.destroy()

        def do_apply():
            self.current_month = selected_m.get()
            self.current_year = selected_y.get()
            self.refresh_all_views()
            dlg.destroy()

        btn_today = ttk.Button(btn_bar, text="📅 Heute", command=do_today)
        btn_today.pack(side="left")

        btn_apply = ttk.Button(btn_bar, text="✔ Übernehmen", style="Primary.TButton", command=do_apply)
        btn_apply.pack(side="right", padx=(8, 0))

        btn_cancel = ttk.Button(btn_bar, text="Abbrechen", command=dlg.destroy)
        btn_cancel.pack(side="right")

        # Measure, center dialog over parent, and reveal properly
        self.auto_fit_dialog(dlg, min_w=420, min_h=400)
        dlg.deiconify()
        dlg.transient(self)
        dlg.grab_set()

    # =========================================================================
    # TAB 1: MONATSÜBERSICHT (SUMMARY & CALCULATIONS)
    # =========================================================================
    def build_overview_tab(self):
        # Frame container
        main_frame = tk.Frame(self.tab_overview, bg=self.bg_color)
        main_frame.pack(fill="both", expand=True, padx=8, pady=8)

        # Top Metric Cards Grid
        metrics_frame = tk.Frame(main_frame, bg=self.bg_color)
        metrics_frame.pack(fill="x", pady=(0, 12))

        # Card 1: Einnahmen
        c1 = ttk.Frame(metrics_frame, style="Card.TFrame")
        c1.grid(row=0, column=0, padx=6, pady=4, sticky="nsew")
        ttk.Label(c1, text="Gesamteinnahmen", style="SubHeader.TLabel").pack(anchor="w", padx=12, pady=(10, 2))
        self.lbl_metric_income = ttk.Label(c1, text="0,00 €", style="MetricValue.TLabel")
        self.lbl_metric_income.pack(anchor="w", padx=12, pady=(0, 10))

        # Card 2: Fixkosten
        c2 = ttk.Frame(metrics_frame, style="Card.TFrame")
        c2.grid(row=0, column=1, padx=6, pady=4, sticky="nsew")
        ttk.Label(c2, text="Fixkosten", style="SubHeader.TLabel").pack(anchor="w", padx=12, pady=(10, 2))
        self.lbl_metric_fixed = ttk.Label(c2, text="0,00 €", style="MetricValueNeg.TLabel")
        self.lbl_metric_fixed.pack(anchor="w", padx=12, pady=(0, 10))

        # Card 3: Wiederkehrend
        c3 = ttk.Frame(metrics_frame, style="Card.TFrame")
        c3.grid(row=0, column=2, padx=6, pady=4, sticky="nsew")
        ttk.Label(c3, text="Wiederkehrend", style="SubHeader.TLabel").pack(anchor="w", padx=12, pady=(10, 2))
        self.lbl_metric_recurring = ttk.Label(c3, text="0,00 €", style="MetricValueNeg.TLabel")
        self.lbl_metric_recurring.pack(anchor="w", padx=12, pady=(0, 10))

        # Card 4: Rechnungen
        c4 = ttk.Frame(metrics_frame, style="Card.TFrame")
        c4.grid(row=0, column=3, padx=6, pady=4, sticky="nsew")
        ttk.Label(c4, text="Rechnungen", style="SubHeader.TLabel").pack(anchor="w", padx=12, pady=(10, 2))
        self.lbl_metric_invoices = ttk.Label(c4, text="0,00 €", style="MetricValueNeg.TLabel")
        self.lbl_metric_invoices.pack(anchor="w", padx=12, pady=(0, 10))

        # Card 5: Restbudget / Balance
        c5 = ttk.Frame(metrics_frame, style="Card.TFrame")
        c5.grid(row=0, column=4, padx=6, pady=4, sticky="nsew")
        ttk.Label(c5, text="Verfügbares Saldo", style="SubHeader.TLabel").pack(anchor="w", padx=12, pady=(10, 2))
        self.lbl_metric_balance = ttk.Label(c5, text="0,00 €", style="MetricValueNeutral.TLabel")
        self.lbl_metric_balance.pack(anchor="w", padx=12, pady=(0, 10))

        for col in range(5):
            metrics_frame.columnconfigure(col, weight=1)

        # Summary Table Frame
        table_frame = ttk.Frame(main_frame, style="Card.TFrame")
        table_frame.pack(fill="both", expand=True, pady=(10, 0))

        hdr_frame = ttk.Frame(table_frame, style="Card.TFrame")
        hdr_frame.pack(fill="x", padx=12, pady=10)

        ttk.Label(hdr_frame, text="Kosten- & Einnahmenaufstellung", style="Header.TLabel").pack(side="left")

        ttk.Label(hdr_frame, text=" Sortierung / Filter:").pack(side="left", padx=(15, 5))
        self.combo_overview_sort = ttk.Combobox(
            hdr_frame,
            values=["Nach Kategorie & Bezeichnung", "Nach Typ (Fixkosten, Wiederkehrend, Rechnung...)", "Nach Bezeichnung"],
            state="readonly",
            width=38
        )
        saved_sort = self.db.get_setting("overview_sort_mode", "Nach Kategorie & Bezeichnung")
        if saved_sort not in ["Nach Kategorie & Bezeichnung", "Nach Typ (Fixkosten, Wiederkehrend, Rechnung...)", "Nach Bezeichnung"]:
            saved_sort = "Nach Kategorie & Bezeichnung"
        self.combo_overview_sort.set(saved_sort)
        self.combo_overview_sort.pack(side="left", padx=4)

        def on_overview_sort_change(e=None):
            self.db.set_setting("overview_sort_mode", self.combo_overview_sort.get())
            self.refresh_all_views()

        self.combo_overview_sort.bind("<<ComboboxSelected>>", on_overview_sort_change)

        cols = ("typ", "titel", "kategorie", "wann", "betrag")
        self.tree_overview = ttk.Treeview(table_frame, columns=cols, show="headings", height=15)
        self.tree_overview.heading("typ", text="Typ")
        self.tree_overview.heading("titel", text="Bezeichnung")
        self.tree_overview.heading("kategorie", text="Kategorie")
        self.tree_overview.heading("wann", text="Rhythmus / Fälligkeit")
        self.tree_overview.heading("betrag", text="Betrag (€)")

        self.tree_overview.column("typ", width=120)
        self.tree_overview.column("titel", width=240)
        self.tree_overview.column("kategorie", width=170)
        self.tree_overview.column("wann", width=170)
        self.tree_overview.column("betrag", width=120, anchor="e")

        self.tree_overview.tag_configure("even", background="#ffffff")
        self.tree_overview.tag_configure("odd", background="#f8fafc")
        self.tree_overview.tag_configure("sep", background="#e2e8f0", foreground="#0f172a", font=("Helvetica", 10, "bold"))

        scrollbar = ttk.Scrollbar(table_frame, orient="vertical", command=self.tree_overview.yview)
        self.tree_overview.configure(yscroll=scrollbar.set)
        
        self.tree_overview.pack(side="left", fill="both", expand=True, padx=(12, 0), pady=10)
        scrollbar.pack(side="right", fill="y", padx=(0, 12), pady=10)

    # =========================================================================
    # TAB 2: EINNAHMEN & PFLEGEGELD
    # =========================================================================
    def build_income_tab(self):
        main_frame = tk.Frame(self.tab_income, bg=self.bg_color)
        main_frame.pack(fill="both", expand=True, padx=8, pady=8)

        # ---------------------------------------------------------------------
        # Section 1: Regular Income
        # ---------------------------------------------------------------------
        sec_reg = ttk.LabelFrame(main_frame, text=" 💰 Allgemeine Einnahmen ", padding=8)
        sec_reg.pack(fill="both", expand=True, pady=(0, 8))

        ctrl_bar = tk.Frame(sec_reg, bg=self.bg_color)
        ctrl_bar.pack(fill="x", pady=(0, 6))

        btn_add = ttk.Button(ctrl_bar, text="➕ Einnahme hinzufügen", style="Primary.TButton", command=self.add_income)
        btn_add.pack(side="left", padx=4)

        btn_edit = ttk.Button(ctrl_bar, text="✏️ Bearbeiten", command=self.edit_income)
        btn_edit.pack(side="left", padx=4)

        btn_adjust = ttk.Button(ctrl_bar, text="📅 Für aktuellen Monat anpassen", command=self.adjust_income_for_month)
        btn_adjust.pack(side="left", padx=4)

        btn_del = ttk.Button(ctrl_bar, text="🗑️ Löschen", command=self.delete_income)
        btn_del.pack(side="left", padx=4)

        tree_frame1 = ttk.Frame(sec_reg)
        tree_frame1.pack(fill="both", expand=True)

        cols = ("id", "titel", "betrag", "kategorie", "rhythmus", "status")
        self.tree_income = ttk.Treeview(tree_frame1, columns=cols, show="headings", height=7)
        self.tree_income.heading("id", text="ID")
        self.tree_income.heading("titel", text="Bezeichnung")
        self.tree_income.heading("betrag", text="Betrag (€)")
        self.tree_income.heading("kategorie", text="Kategorie")
        self.tree_income.heading("rhythmus", text="Rhythmus / Gültigkeit")
        self.tree_income.heading("status", text="Aktiv")

        self.tree_income.column("id", width=50, anchor="center")
        self.tree_income.column("titel", width=220)
        self.tree_income.column("betrag", width=110, anchor="e")
        self.tree_income.column("kategorie", width=150)
        self.tree_income.column("rhythmus", width=180)
        self.tree_income.column("status", width=70, anchor="center")

        sb1 = ttk.Scrollbar(tree_frame1, orient="vertical", command=self.tree_income.yview)
        self.tree_income.configure(yscroll=sb1.set)

        self.tree_income.pack(side="left", fill="both", expand=True)
        sb1.pack(side="right", fill="y")

        # ---------------------------------------------------------------------
        # Section 2: Dedicated Pflegegeld Area
        # ---------------------------------------------------------------------
        sec_pf = ttk.LabelFrame(main_frame, text=" 🏥 Gesonderter Bereich: Pflegegeld (wird NICHT zu den allgemeinen Einnahmen hinzugerechnet) ", padding=8)
        sec_pf.pack(fill="both", expand=True, pady=(4, 0))

        ctrl_bar_pf = tk.Frame(sec_pf, bg=self.bg_color)
        ctrl_bar_pf.pack(fill="x", pady=(0, 6))

        btn_add_pf = ttk.Button(ctrl_bar_pf, text="➕ Pflegegeld hinzufügen", style="Primary.TButton", command=self.add_pflegegeld)
        btn_add_pf.pack(side="left", padx=4)

        btn_edit_pf = ttk.Button(ctrl_bar_pf, text="✏️ Bearbeiten", command=self.edit_pflegegeld)
        btn_edit_pf.pack(side="left", padx=4)

        btn_del_pf = ttk.Button(ctrl_bar_pf, text="🗑️ Löschen", command=self.delete_pflegegeld)
        btn_del_pf.pack(side="left", padx=4)

        self.lbl_pflegegeld_total = ttk.Label(ctrl_bar_pf, text="Gesamtes Pflegegeld (gesondert): 0,00 €", font=("Helvetica", 9, "bold"))
        self.lbl_pflegegeld_total.pack(side="right", padx=8)

        tree_frame2 = ttk.Frame(sec_pf)
        tree_frame2.pack(fill="both", expand=True)

        cols_pf = ("id", "beguenstigter", "ueberweiser", "pflegegrad", "gesamt", "hilfsmittel", "anteil", "ergebnis", "gueltigkeit", "rhythmus", "status")
        self.tree_pflegegeld = ttk.Treeview(tree_frame2, columns=cols_pf, show="headings", height=6)
        self.tree_pflegegeld.heading("id", text="ID")
        self.tree_pflegegeld.heading("beguenstigter", text="Begünstigter")
        self.tree_pflegegeld.heading("ueberweiser", text="Überweiser")
        self.tree_pflegegeld.heading("pflegegrad", text="Pflegegrad / Stufe")
        self.tree_pflegegeld.heading("gesamt", text="Gesamt (€)")
        self.tree_pflegegeld.heading("hilfsmittel", text="Hilfsmittel (€)")
        self.tree_pflegegeld.heading("anteil", text="Anteil (%)")
        self.tree_pflegegeld.heading("ergebnis", text="Ergebnis (€)")
        self.tree_pflegegeld.heading("gueltigkeit", text="Gültigkeit")
        self.tree_pflegegeld.heading("rhythmus", text="Rhythmus")
        self.tree_pflegegeld.heading("status", text="Aktiv")

        self.tree_pflegegeld.column("id", width=40, anchor="center")
        self.tree_pflegegeld.column("beguenstigter", width=140)
        self.tree_pflegegeld.column("ueberweiser", width=140)
        self.tree_pflegegeld.column("pflegegrad", width=110, anchor="center")
        self.tree_pflegegeld.column("gesamt", width=95, anchor="e")
        self.tree_pflegegeld.column("hilfsmittel", width=100, anchor="e")
        self.tree_pflegegeld.column("anteil", width=75, anchor="center")
        self.tree_pflegegeld.column("ergebnis", width=105, anchor="e")
        self.tree_pflegegeld.column("gueltigkeit", width=110, anchor="center")
        self.tree_pflegegeld.column("rhythmus", width=100, anchor="center")
        self.tree_pflegegeld.column("status", width=60, anchor="center")

        sb2 = ttk.Scrollbar(tree_frame2, orient="vertical", command=self.tree_pflegegeld.yview)
        self.tree_pflegegeld.configure(yscroll=sb2.set)

        self.tree_pflegegeld.pack(side="left", fill="both", expand=True)
        sb2.pack(side="right", fill="y")

    # =========================================================================
    # TAB 3: FIXKOSTEN
    # =========================================================================
    def build_fixed_tab(self):
        frame = tk.Frame(self.tab_fixed, bg=self.bg_color)
        frame.pack(fill="both", expand=True, padx=8, pady=8)

        ctrl_bar = tk.Frame(frame, bg=self.bg_color)
        ctrl_bar.pack(fill="x", pady=(0, 8))

        btn_add = ttk.Button(ctrl_bar, text="➕ Fixkosten hinzufügen", style="Primary.TButton", command=self.add_fixed_cost)
        btn_add.pack(side="left", padx=4)

        btn_edit = ttk.Button(ctrl_bar, text="✏️ Bearbeiten", command=self.edit_fixed_cost)
        btn_edit.pack(side="left", padx=4)

        btn_del = ttk.Button(ctrl_bar, text="🗑️ Löschen", command=self.delete_fixed_cost)
        btn_del.pack(side="left", padx=4)

        cols = ("id", "titel", "betrag", "kategorie", "faelligkeit", "end_date", "status")
        self.tree_fixed = ttk.Treeview(frame, columns=cols, show="headings", height=15)
        self.tree_fixed.heading("id", text="ID")
        self.tree_fixed.heading("titel", text="Bezeichnung")
        self.tree_fixed.heading("betrag", text="Betrag (€)")
        self.tree_fixed.heading("kategorie", text="Kategorie")
        self.tree_fixed.heading("faelligkeit", text="Fälligkeit")
        self.tree_fixed.heading("end_date", text="Abbuchung bis einschl.")
        self.tree_fixed.heading("status", text="Aktiv")

        self.tree_fixed.column("id", width=45, anchor="center")
        self.tree_fixed.column("titel", width=200)
        self.tree_fixed.column("betrag", width=105, anchor="e")
        self.tree_fixed.column("kategorie", width=140)
        self.tree_fixed.column("faelligkeit", width=130, anchor="center")
        self.tree_fixed.column("end_date", width=150, anchor="center")
        self.tree_fixed.column("status", width=65, anchor="center")

        sb = ttk.Scrollbar(frame, orient="vertical", command=self.tree_fixed.yview)
        self.tree_fixed.configure(yscroll=sb.set)

        self.tree_fixed.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

    # =========================================================================
    # TAB 4: WIEDERKEHRENDE AUSGABEN
    # =========================================================================
    def build_recurring_tab(self):
        frame = tk.Frame(self.tab_recurring, bg=self.bg_color)
        frame.pack(fill="both", expand=True, padx=8, pady=8)

        ctrl_bar = tk.Frame(frame, bg=self.bg_color)
        ctrl_bar.pack(fill="x", pady=(0, 8))

        btn_add = ttk.Button(ctrl_bar, text="➕ Ausführung hinzufügen", style="Primary.TButton", command=self.add_recurring)
        btn_add.pack(side="left", padx=4)

        btn_edit = ttk.Button(ctrl_bar, text="✏️ Bearbeiten", command=self.edit_recurring)
        btn_edit.pack(side="left", padx=4)

        btn_del = ttk.Button(ctrl_bar, text="🗑️ Löschen", command=self.delete_recurring)
        btn_del.pack(side="left", padx=4)

        cols = ("id", "titel", "betrag", "kategorie", "rhythmus", "start", "ende", "notiz", "status")
        self.tree_recurring = ttk.Treeview(frame, columns=cols, show="headings", height=15)
        self.tree_recurring.heading("id", text="ID")
        self.tree_recurring.heading("titel", text="Bezeichnung")
        self.tree_recurring.heading("betrag", text="Betrag (€)")
        self.tree_recurring.heading("kategorie", text="Kategorie")
        self.tree_recurring.heading("rhythmus", text="Intervall / Rhythmus")
        self.tree_recurring.heading("start", text="Erstmalig ab")
        self.tree_recurring.heading("ende", text="Enddatum")
        self.tree_recurring.heading("notiz", text="Notiz")
        self.tree_recurring.heading("status", text="Aktiv")

        self.tree_recurring.column("id", width=45, anchor="center")
        self.tree_recurring.column("titel", width=180)
        self.tree_recurring.column("betrag", width=95, anchor="e")
        self.tree_recurring.column("kategorie", width=130)
        self.tree_recurring.column("rhythmus", width=140)
        self.tree_recurring.column("start", width=95, anchor="center")
        self.tree_recurring.column("ende", width=95, anchor="center")
        self.tree_recurring.column("notiz", width=130)
        self.tree_recurring.column("status", width=60, anchor="center")

        sb = ttk.Scrollbar(frame, orient="vertical", command=self.tree_recurring.yview)
        self.tree_recurring.configure(yscroll=sb.set)

        self.tree_recurring.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

    # =========================================================================
    # TAB 5: RECHNUNGEN
    # =========================================================================
    def build_invoices_tab(self):
        frame = tk.Frame(self.tab_invoices, bg=self.bg_color)
        frame.pack(fill="both", expand=True, padx=8, pady=8)

        ctrl_bar = tk.Frame(frame, bg=self.bg_color)
        ctrl_bar.pack(fill="x", pady=(0, 8))

        btn_add = ttk.Button(ctrl_bar, text="➕ Rechnung hinzufügen", style="Primary.TButton", command=self.add_invoice)
        btn_add.pack(side="left", padx=4)

        btn_edit = ttk.Button(ctrl_bar, text="✏️ Bearbeiten", command=self.edit_invoice)
        btn_edit.pack(side="left", padx=4)

        btn_del = ttk.Button(ctrl_bar, text="🗑️ Löschen", command=self.delete_invoice)
        btn_del.pack(side="left", padx=4)

        cols = ("id", "rechnungssteller", "beschreibung", "betrag", "faelligkeit", "kategorie", "bezahlt")
        self.tree_invoices = ttk.Treeview(frame, columns=cols, show="headings", height=15)
        self.tree_invoices.heading("id", text="ID")
        self.tree_invoices.heading("rechnungssteller", text="Rechnungssteller")
        self.tree_invoices.heading("beschreibung", text="Beschreibung")
        self.tree_invoices.heading("betrag", text="Betrag (€)")
        self.tree_invoices.heading("faelligkeit", text="Fälligkeit")
        self.tree_invoices.heading("kategorie", text="Kategorie")
        self.tree_invoices.heading("bezahlt", text="Bezahlt")

        self.tree_invoices.column("id", width=40, anchor="center")
        self.tree_invoices.column("rechnungssteller", width=180)
        self.tree_invoices.column("beschreibung", width=200)
        self.tree_invoices.column("betrag", width=100, anchor="e")
        self.tree_invoices.column("faelligkeit", width=100, anchor="center")
        self.tree_invoices.column("kategorie", width=140)
        self.tree_invoices.column("bezahlt", width=80, anchor="center")

        sb = ttk.Scrollbar(frame, orient="vertical", command=self.tree_invoices.yview)
        self.tree_invoices.configure(yscroll=sb.set)

        self.tree_invoices.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")

    # =========================================================================
    # TAB 6: DRUCK- & EXPORTVORSCHAU
    # =========================================================================
    def build_print_tab(self):
        frame = tk.Frame(self.tab_print, bg=self.bg_color)
        frame.pack(fill="both", expand=True, padx=8, pady=8)

        ctrl_bar = tk.Frame(frame, bg=self.bg_color)
        ctrl_bar.pack(fill="x", pady=(0, 8))

        btn_print = ttk.Button(ctrl_bar, text="🖨️ Drucken (DIN 5008 PDF)", style="Primary.TButton", command=self.direct_print_pdf)
        btn_print.pack(side="left", padx=4)

        btn_pdf = ttk.Button(ctrl_bar, text="📄 Als PDF speichern", command=self.export_print_pdf)
        btn_pdf.pack(side="left", padx=4)

        btn_refresh = ttk.Button(ctrl_bar, text="🔄 Aktualisieren", command=self.refresh_print_tab)
        btn_refresh.pack(side="left", padx=4)

        # Mittig ausgerichteter Container für die Druckblatt-Vorschau
        center_container = tk.Frame(frame, bg=self.bg_color)
        center_container.pack(fill="both", expand=True)

        paper_frame = tk.Frame(center_container, bg=self.card_bg, highlightbackground=self.border_color, highlightthickness=1, bd=0)
        paper_frame.pack(anchor="center", expand=True, fill="y", padx=12, pady=6)

        self.txt_print = tk.Text(paper_frame, font=("Courier", 9), bg=self.card_bg, fg=self.text_primary, wrap="none", width=95, bd=0, highlightthickness=0, padx=20, pady=20)
        self.txt_print.tag_configure("red", foreground=self.danger_color, font=("Courier", 9, "bold"))
        self.txt_print.tag_configure("green", foreground=self.success_color, font=("Courier", 9, "bold"))
        self.txt_print.tag_configure("header", font=("Courier", 9, "bold"))

        sb_y = ttk.Scrollbar(paper_frame, orient="vertical", command=self.txt_print.yview)
        sb_x = ttk.Scrollbar(paper_frame, orient="horizontal", command=self.txt_print.xview)
        self.txt_print.configure(yscroll=sb_y.set, xscroll=sb_x.set)

        sb_x.pack(side="bottom", fill="x")
        sb_y.pack(side="right", fill="y")
        self.txt_print.pack(side="left", fill="both", expand=True)

    # =========================================================================
    # REFRESH & CALCULATION LOGIC
    # =========================================================================
    def refresh_all_views(self):
        self.update_month_label()
        self.apply_all_tree_zebra_styles()

        # Load data for selected month and year
        incomes = self.db.get_incomes(self.current_month, self.current_year)
        incomes.sort(key=lambda r: (str(r[3]).lower(), str(r[1]).lower()))

        fixed_costs = self.db.get_fixed_costs()
        fixed_costs.sort(key=lambda r: (str(r[3]).lower(), str(r[1]).lower()))

        recurring = self.db.get_recurring_expenses()
        recurring.sort(key=lambda r: get_recurring_due_key(r, self.current_month, self.current_year))

        invoices = self.db.get_invoices(self.current_month, self.current_year)
        invoices.sort(key=lambda r: (str(r[5]).lower(), str(r[1]).lower(), str(r[2]).lower()))

        # 1. Populate Income Tree
        for item in self.tree_income.get_children():
            self.tree_income.delete(item)
        tot_income = 0.0
        for idx, r in enumerate(incomes):
            # r: (id, title, amount, category, is_active, month, year, is_recurring)
            is_rec = safe_int(r[7] if len(r) > 7 else 1, 1)
            r_m = safe_int(r[5], 0)
            r_y = safe_int(r[6], 0)
            is_active_inc = (safe_int(r[4], 1) == 1)
            amt = safe_float(r[2], 0.0)
            if is_rec == 1:
                rhythmus_str = "Monatlich wiederkehrend"
            else:
                m_name = get_german_month_name(r_m) if r_m > 0 else ""
                rhythmus_str = f"Sonderbetrag ({m_name} {r_y})" if r_m > 0 else "Einmalig"

            if is_active_inc:
                tot_income += amt
            status_str = "Ja" if is_active_inc else "Nein"
            zebra = "even" if idx % 2 == 0 else "odd"
            self.tree_income.insert("", "end", values=(r[0], r[1], f"{amt:,.2f} €", r[3], rhythmus_str, status_str), tags=(zebra,))

        # 1b. Populate Pflegegeld Tree (Gesondert - wird NICHT zu tot_income addiert)
        pf_list = self.db.get_pflegegeld_list()
        pf_list.sort(key=lambda r: (str(r[1]).lower(), str(r[2]).lower()))
        for item in self.tree_pflegegeld.get_children():
            self.tree_pflegegeld.delete(item)
        tot_pf = 0.0
        for idx, r in enumerate(pf_list):
            pf_id = r[0]
            beg = r[1]
            ueb = r[2]
            pgrad = r[3]
            gesamt = safe_float(r[4], 0.0)
            hilf = safe_float(r[5], 0.0)
            anteil = safe_float(r[6], 0.0)
            ergebnis = safe_float(r[7], 0.0)
            guelt = r[8]
            rhythm = r[9]
            is_active = (safe_int(r[10], 1) == 1)

            if is_active:
                tot_pf += ergebnis

            status_str = "Ja" if is_active else "Nein"
            zebra = "even" if idx % 2 == 0 else "odd"
            self.tree_pflegegeld.insert("", "end", values=(
                pf_id, beg, ueb, pgrad,
                f"{gesamt:,.2f} €", f"{hilf:,.2f} €", f"{anteil:g} %", f"{ergebnis:,.2f} €",
                guelt, rhythm, status_str
            ), tags=(zebra,))

        if hasattr(self, 'lbl_pflegegeld_total'):
            self.lbl_pflegegeld_total.config(text=f"Gesamtes Pflegegeld (gesondert): {tot_pf:,.2f} €")

        # 2. Populate Fixed Costs Tree
        for item in self.tree_fixed.get_children():
            self.tree_fixed.delete(item)
        tot_fixed = 0.0
        for idx, r in enumerate(fixed_costs):
            # r: (id, title, amount, category, is_active, day_of_month, end_month, end_year)
            is_active_fix = (safe_int(r[4], 1) == 1)
            amt = safe_float(r[2], 0.0)
            dom = safe_int(r[5] if len(r) > 5 else 1, 1)
            e_m = safe_int(r[6] if len(r) > 6 else 0, 0)
            e_y = safe_int(r[7] if len(r) > 7 else 0, 0)
            applies_this_month = is_active_fix and check_fixed_cost_due(e_m, e_y, self.current_month, self.current_year)
            if applies_this_month:
                tot_fixed += amt
            status_str = "Ja" if is_active_fix else "Nein"
            due_str = f"zum {dom}. des Monats"
            if e_m > 0 and e_y > 0:
                end_str = f"{get_german_month_name(e_m)} {e_y}"
            else:
                end_str = "Dauerhaft"
            zebra = "even" if idx % 2 == 0 else "odd"
            self.tree_fixed.insert("", "end", values=(r[0], r[1], f"{amt:,.2f} €", r[3], due_str, end_str, status_str), tags=(zebra,))

        # 3. Populate Recurring Expenses Tree
        for item in self.tree_recurring.get_children():
            self.tree_recurring.delete(item)
        tot_recurring = 0.0
        for idx, r in enumerate(recurring):
            # r: (id, title, amount, category, frequency, month_specified, is_active, start_month, start_year, end_month, end_year, note, due_months)
            is_active = (safe_int(r[6], 1) == 1)
            freq = r[4]
            month_spec = safe_int(r[5], 0)
            s_m = safe_int(r[7] if len(r) > 7 else 1, 1)
            s_y = safe_int(r[8] if len(r) > 8 else 2026, 2026)
            e_m = safe_int(r[9] if len(r) > 9 else 0, 0)
            e_y = safe_int(r[10] if len(r) > 10 else 0, 0)
            note = r[11] if len(r) > 11 else ""
            due_m = r[12] if len(r) > 12 else ""
            amt = safe_float(r[2], 0.0)

            applies_this_month = is_active and check_recurring_due(
                freq, month_spec, s_m, s_y, e_m, e_y, self.current_month, self.current_year, due_m
            )

            if applies_this_month:
                tot_recurring += amt

            f_code = FREQ_TO_CODE.get(freq, freq)
            freq_str = get_recurring_rhythm_display(r)
            if f_code == "SPLIT_MONTHS" and due_m:
                amt_str = f"{amt:,.2f} € / Fälligkeit"
            else:
                amt_str = f"{amt:,.2f} €"

            start_str = f"{get_german_month_name(s_m)} {s_y}" if (s_m > 0 and s_y > 0) else "Sofort"
            end_str = f"{get_german_month_name(e_m)} {e_y}" if (e_m > 0 and e_y > 0) else "Unbegrenzt"
            status_str = "Ja" if is_active else "Nein"

            zebra = "even" if idx % 2 == 0 else "odd"
            self.tree_recurring.insert("", "end", values=(r[0], r[1], amt_str, r[3], freq_str, start_str, end_str, note, status_str), tags=(zebra,))

        # 4. Populate Invoices Tree
        for item in self.tree_invoices.get_children():
            self.tree_invoices.delete(item)
        tot_invoices = 0.0
        for idx, r in enumerate(invoices):
            # r: (id, vendor, description, amount, due_date, category, is_paid, month, year)
            amt = safe_float(r[3], 0.0)
            tot_invoices += amt
            paid_str = "Ja" if safe_int(r[6], 0) == 1 else "Nein"
            zebra = "even" if idx % 2 == 0 else "odd"
            self.tree_invoices.insert("", "end", values=(r[0], r[1], r[2], f"{amt:,.2f} €", r[4], r[5], paid_str), tags=(zebra,))

        # 5. Calculations for Overview
        tot_expenses = tot_fixed + tot_recurring + tot_invoices
        balance = tot_income - tot_expenses

        self.lbl_metric_income.config(text=f"{tot_income:,.2f} €")
        self.lbl_metric_fixed.config(text=f"{tot_fixed:,.2f} €")
        self.lbl_metric_recurring.config(text=f"{tot_recurring:,.2f} €")
        self.lbl_metric_invoices.config(text=f"{tot_invoices:,.2f} €")
        
        balance_str = f"{balance:,.2f} €"
        if balance >= 0:
            self.lbl_metric_balance.config(text=balance_str, style="MetricValue.TLabel")
        else:
            self.lbl_metric_balance.config(text=balance_str, style="MetricValueNeg.TLabel")

        # Overview Tree
        for item in self.tree_overview.get_children():
            self.tree_overview.delete(item)

        cat_totals = {}
        overview_items = []

        for r in incomes:
            if r[4] == 1:
                is_rec = (r[7] == 1) if len(r) > 7 else True
                rhythm_str = "Monatlich" if is_rec else "Einmalig"
                overview_items.append(("Einnahme", r[1], r[3], rhythm_str, f"+{r[2]:,.2f} €", "income", r[0]))
                cat_totals[r[3]] = cat_totals.get(r[3], 0.0) + r[2]

        for r in fixed_costs:
            if r[4] == 1:
                e_m = safe_int(r[6] if len(r) > 6 else 0, 0)
                e_y = safe_int(r[7] if len(r) > 7 else 0, 0)
                if check_fixed_cost_due(e_m, e_y, self.current_month, self.current_year):
                    dom = safe_int(r[5] if len(r) > 5 else 1, 1)
                    rhythm_str = f"zum {dom}. des Monats"
                    overview_items.append(("Fixkosten", r[1], r[3], rhythm_str, f"-{r[2]:,.2f} €", "fixed", r[0]))
                    cat_totals[r[3]] = cat_totals.get(r[3], 0.0) - r[2]

        for r in recurring:
            is_active = (r[6] == 1)
            s_m = r[7] if len(r) > 7 else 1
            s_y = r[8] if len(r) > 8 else 2026
            e_m = r[9] if len(r) > 9 else 0
            e_y = r[10] if len(r) > 10 else 0
            due_m = r[12] if len(r) > 12 else ""

            if is_active and check_recurring_due(r[4], r[5], s_m, s_y, e_m, e_y, self.current_month, self.current_year, due_m):
                rhythm_str = get_recurring_rhythm_display(r)
                overview_items.append(("Wiederkehrend", r[1], r[3], rhythm_str, f"-{r[2]:,.2f} €", "recurring", r[0]))
                cat_totals[r[3]] = cat_totals.get(r[3], 0.0) - r[2]

        for r in invoices:
            due_str = f"Fällig: {r[4]}" if (len(r) > 4 and r[4]) else "-"
            overview_items.append(("Rechnung", f"{r[1]} - {r[2]}", r[5], due_str, f"-{r[3]:,.2f} €", "invoice", r[0]))
            cat_totals[r[5]] = cat_totals.get(r[5], 0.0) - r[3]

        sort_opt = getattr(self, "combo_overview_sort", None)
        sort_mode = sort_opt.get() if sort_opt else "Nach Kategorie & Bezeichnung"

        type_order = {"Einnahme": 1, "Fixkosten": 2, "Wiederkehrend": 3, "Rechnung": 4}

        if sort_mode.startswith("Nach Typ"):
            overview_items.sort(key=lambda item: (type_order.get(item[0], 99), str(item[2]).lower(), str(item[1]).lower()))
        elif sort_mode.startswith("Nach Bezeichnung"):
            overview_items.sort(key=lambda item: (str(item[1]).lower(), str(item[2]).lower()))
        else: # Nach Kategorie & Bezeichnung
            overview_items.sort(key=lambda item: (str(item[2]).lower(), type_order.get(item[0], 99), str(item[1]).lower()))

        current_group = None
        row_idx = 0

        for typ, title, cat, rhythm_str, amt_str, tag, r_id in overview_items:
            if sort_mode.startswith("Nach Kategorie"):
                if current_group != cat:
                    current_group = cat
                    self.tree_overview.insert(
                        "", "end",
                        values=(f"📁 {cat.upper()}", "──────────────────────────────", "──────────────────────", "──────────────────", "──────────"),
                        tags=("sep",)
                    )
                    row_idx = 0
            elif sort_mode.startswith("Nach Typ"):
                if current_group != typ:
                    current_group = typ
                    self.tree_overview.insert(
                        "", "end",
                        values=(f"🔷 📌 {typ.upper()}", "──────────────────────────────", "──────────────────────", "──────────────────", "──────────"),
                        tags=("sep",)
                    )
                    row_idx = 0

            zebra = "even" if row_idx % 2 == 0 else "odd"
            self.tree_overview.insert("", "end", values=(typ, title, cat, rhythm_str, amt_str), tags=(tag, r_id, zebra))
            row_idx += 1

        m_name = get_german_month_name(self.current_month)
        self.refresh_print_tab()
        self.status_bar.config(text=f"Daten für {m_name} {self.current_year} erfolgreich aktualisiert.")

    def refresh_print_tab(self):
        m_name = get_german_month_name(self.current_month)
        incomes = self.db.get_incomes(self.current_month, self.current_year)
        fixed_costs = self.db.get_fixed_costs()
        recurring = self.db.get_recurring_expenses()
        invoices = self.db.get_invoices(self.current_month, self.current_year)
        pflegegeld = self.db.get_pflegegeld_list()

        now_str = datetime.datetime.now().strftime("%d.%m.%Y")

        self.txt_print.delete("1.0", "end")

        box_top = "┌" + "─" * 87 + "┐\n"
        sep     = "├" + "─" * 87 + "┤\n"
        box_bot = "└" + "─" * 87 + "┘\n\n"

        def make_box_line(left, right=""):
            pad = 85 - len(left) - len(right)
            if pad < 0:
                pad = 0
            return f"│ {left}{' ' * pad}{right} │\n"

        l1 = make_box_line("🏛️ HAUSHALTSBUCH", "MONATSAUSWERTUNG")
        l2 = make_box_line("Monatsauswertung nach DIN 5008", f"Zeitraum: {m_name} {self.current_year}")
        l3 = make_box_line("", f"Erstellt am: {now_str}")
        l4 = make_box_line("Absender: Haushaltsbuch • Privater Haushalt • Monatsauswertung")
        l5 = make_box_line(f"BETREFF: Monatsauswertung für {m_name} {self.current_year}")

        self.txt_print.insert("end", box_top, "header")
        self.txt_print.insert("end", l1, "header")
        self.txt_print.insert("end", l2, "header")
        self.txt_print.insert("end", l3, "header")
        self.txt_print.insert("end", sep, "header")
        self.txt_print.insert("end", l4, "header")
        self.txt_print.insert("end", sep, "header")
        self.txt_print.insert("end", l5, "header")
        self.txt_print.insert("end", box_bot, "header")

        divider_89 = "-----------------------------------------------------------------------------------------\n"
        double_divider_89 = "=========================================================================================\n"

        # Totals vorab berechnen für Gesamtergebnis-Box oben
        inc_list = [r for r in incomes if safe_int(r[4], 1) == 1]
        tot_inc = sum(safe_float(r[2], 0.0) for r in inc_list)

        fix_list = []
        tot_fix = 0.0
        for r in fixed_costs:
            if safe_int(r[4], 1) == 1:
                e_m = safe_int(r[6] if len(r) > 6 else 0, 0)
                e_y = safe_int(r[7] if len(r) > 7 else 0, 0)
                if check_fixed_cost_due(e_m, e_y, self.current_month, self.current_year):
                    fix_list.append(r)
                    tot_fix += safe_float(r[2], 0.0)

        rec_list = []
        tot_rec = 0.0
        for r in recurring:
            is_active = (safe_int(r[6], 1) == 1)
            s_m = safe_int(r[7] if len(r) > 7 else 1, 1)
            s_y = safe_int(r[8] if len(r) > 8 else 2026, 2026)
            e_m = safe_int(r[9] if len(r) > 9 else 0, 0)
            e_y = safe_int(r[10] if len(r) > 10 else 0, 0)
            due_m = r[12] if len(r) > 12 else ""
            if is_active and check_recurring_due(r[4], r[5], s_m, s_y, e_m, e_y, self.current_month, self.current_year, due_m):
                rec_list.append(r)
                tot_rec += safe_float(r[2], 0.0)

        inv_list = list(invoices)
        tot_inv = sum(safe_float(r[3], 0.0) for r in inv_list)

        tot_exp = tot_fix + tot_rec + tot_inv
        balance = tot_inc - tot_exp

        str_inc = fmt_de(tot_inc)
        str_exp = f"-{fmt_de(tot_exp)}"
        str_bal = fmt_de(balance)

        # ZUSAMMENFASSUNG NEBENEINANDER OBEN (89 Zeichen genau eingepasst)
        self.txt_print.insert("end", double_divider_89, "header")
        bal_tag = "green" if balance >= 0 else "red"
        self.txt_print.insert("end", "EINNAHMEN: ", "header")
        self.txt_print.insert("end", f"{str_inc:>14}", "header")
        self.txt_print.insert("end", "  |  AUSGABEN GESAMT: ", "header")
        self.txt_print.insert("end", f"{str_exp:>12}", "red")
        self.txt_print.insert("end", "  |  NETTO-SALDO: ", "header")
        self.txt_print.insert("end", f"{str_bal:>12}\n", bal_tag)
        self.txt_print.insert("end", double_divider_89, "header")
        self.txt_print.insert("end", "\n")

        # 1. EINNAHMEN
        self.txt_print.insert("end", "1. EINNAHMEN:\n", "header")
        self.txt_print.insert("end", divider_89)
        self.txt_print.insert("end", f"{'Bezeichnung':<28} {'Kategorie':<20} {'Rhythmus':<18} {'Betrag':>12}\n", "header")
        inc_list.sort(key=lambda r: (str(r[3]).lower(), str(r[1]).lower()))
        for r in inc_list:
            amt = safe_float(r[2], 0.0)
            is_rec = safe_int(r[7] if len(r) > 7 else 1, 1)
            if is_rec == 1:
                rhythm_str = "Monatlich"
            else:
                r_m = safe_int(r[5] if len(r) > 5 else 0, 0)
                r_y = safe_int(r[6] if len(r) > 6 else 0, 0)
                m_label = get_german_month_name(r_m) if r_m > 0 else ""
                rhythm_str = f"Sonderbetrag ({m_label} {r_y})" if r_m > 0 else "Einmalig"
            self.txt_print.insert("end", f"{r[1][:27]:<28} {r[3][:19]:<20} {rhythm_str[:17]:<18} {amt:>12,.2f} €\n")
        self.txt_print.insert("end", f"{'Summe Einnahmen:':<68} {tot_inc:>12,.2f} €\n\n", "header")

        # 2. FIXKOSTEN
        self.txt_print.insert("end", "2. FIXKOSTEN:\n", "header")
        self.txt_print.insert("end", divider_89)
        self.txt_print.insert("end", f"{'Bezeichnung':<28} {'Kategorie':<20} {'Rhythmus':<18} {'Betrag':>12}     Gebucht\n", "header")
        fix_list.sort(key=lambda r: (str(r[3]).lower(), str(r[1]).lower()))
        for r in fix_list:
            amt = safe_float(r[2], 0.0)
            dom = safe_int(r[5] if len(r) > 5 else 1, 1)
            rhythm_str = f"zum {dom}. des Monats"
            self.txt_print.insert("end", f"{r[1][:27]:<28} {r[3][:19]:<20} {rhythm_str[:17]:<18} ")
            self.txt_print.insert("end", f"{-amt:>12,.2f} €", "red")
            self.txt_print.insert("end", "   [ ]\n")
        self.txt_print.insert("end", f"{'Summe Fixkosten:':<68} ")
        self.txt_print.insert("end", f"{-tot_fix:>12,.2f} €\n\n", "red")

        # 3. WIEDERKEHRENDE AUSGABEN
        self.txt_print.insert("end", "3. WIEDERKEHRENDE AUSGABEN:\n", "header")
        self.txt_print.insert("end", divider_89)
        self.txt_print.insert("end", f"{'Bezeichnung':<28} {'Kategorie':<20} {'Rhythmus':<18} {'Betrag':>12}     Gebucht\n", "header")
        rec_list.sort(key=lambda r: get_recurring_due_key(r, self.current_month, self.current_year))
        for r in rec_list:
            amt = safe_float(r[2], 0.0)
            freq_str = get_recurring_rhythm_display(r)
            self.txt_print.insert("end", f"{r[1][:27]:<28} {r[3][:19]:<20} {freq_str[:17]:<18} ")
            self.txt_print.insert("end", f"{-amt:>12,.2f} €", "red")
            self.txt_print.insert("end", "   [ ]\n")
        self.txt_print.insert("end", f"{'Summe Wiederkehrend:':<68} ")
        self.txt_print.insert("end", f"{-tot_rec:>12,.2f} €\n\n", "red")

        # 4. RECHNUNGEN
        self.txt_print.insert("end", "4. RECHNUNGEN:\n", "header")
        self.txt_print.insert("end", divider_89)
        self.txt_print.insert("end", f"{'Rechnungssteller / Titel':<28} {'Kategorie':<20} {'Fälligkeit':<18} {'Betrag':>12}     Gebucht\n", "header")
        inv_list.sort(key=lambda r: (str(r[5]).lower(), str(r[1]).lower()))
        for r in inv_list:
            amt = safe_float(r[3], 0.0)
            title_str = f"{r[1]} - {r[2]}"
            due_str = str(r[4]) if (len(r) > 4 and r[4]) else "-"
            self.txt_print.insert("end", f"{title_str[:27]:<28} {r[5][:19]:<20} {due_str[:17]:<18} ")
            self.txt_print.insert("end", f"{-amt:>12,.2f} €", "red")
            self.txt_print.insert("end", "   [ ]\n")
        self.txt_print.insert("end", f"{'Summe Rechnungen:':<68} ")
        self.txt_print.insert("end", f"{-tot_inv:>12,.2f} €\n\n", "red")

        # Separate Pflegegeld Section in Print View
        active_pf = [p for p in pflegegeld if safe_int(p[10], 1) == 1]
        tot_pf = sum(safe_float(p[7], 0.0) for p in active_pf)
        gesamtsaldo = balance + tot_pf

        if active_pf:
            self.txt_print.insert("end", "\nGESONDERTER BEREICH - PFLEGEGELD (NICHT IN DEN GESAMTEINNAHMEN ENTHALTEN):\n", "header")
            self.txt_print.insert("end", divider_89)
            self.txt_print.insert("end", f"{'Begünstigter / Überweiser':<22} {'Grad':<5} {'Gesamt':>9} {'Hilfsm.':>8} {'Anteil':>6} {'Rhythmus':<13} {'Betrag':>12}\n", "header")
            tot_pf = 0.0
            for p in active_pf:
                label = f"{p[1]} ({p[2]})" if p[2] else p[1]
                p_grad = str(p[3]) if len(p) > 3 else ""
                p_gesamt = f"{safe_float(p[4]):,.2f} €"
                p_hilfs = f"{safe_float(p[5]):,.2f} €"
                p_anteil = f"{safe_float(p[6]):,.1f}%"
                p_rhythm = p[9] if (len(p) > 9 and p[9]) else "Monatlich"
                erg = safe_float(p[7], 0.0)
                self.txt_print.insert("end", f"{label[:21]:<22} {p_grad[:5]:<5} {p_gesamt:>9} {p_hilfs:>8} {p_anteil:>6} {p_rhythm[:12]:<13} {erg:>12,.2f} €\n")
                tot_pf += erg
            self.txt_print.insert("end", f"{'Summe Pflegegeld (gesondert):':<68} {tot_pf:>12,.2f} €\n", "header")
            self.txt_print.insert("end", double_divider_89, "header")

        self.txt_print.insert("end", f"\n* Fußnote: Gesamtsaldo: {gesamtsaldo:,.2f} € (Verfügbares Saldo {balance:,.2f} € + Pflegegeld {tot_pf:,.2f} €)\n")

    def export_print_text(self):
        try:
            year_str = str(self.current_year)
            m_name = get_german_month_name(self.current_month)
            archiv_dir = os.path.join(SCRIPT_DIR, "archiv", year_str)
            os.makedirs(archiv_dir, exist_ok=True)
            default_filename = f"Haushaltsbuch_Auswertung_{m_name}_{year_str}.txt"

            try:
                fpath = filedialog.asksaveasfilename(
                    parent=self,
                    initialdir=archiv_dir,
                    defaultextension=".txt",
                    filetypes=[("Text file", "*.txt"), ("All files", "*.*")],
                    title="Druckansicht speichern",
                    initialfile=default_filename
                )
            except Exception:
                fpath = filedialog.asksaveasfilename(
                    initialdir=archiv_dir,
                    defaultextension=".txt",
                    filetypes=[("Text file", "*.txt"), ("All files", "*.*")],
                    title="Druckansicht speichern",
                    initialfile=default_filename
                )
            if fpath:
                chosen_dir = os.path.dirname(fpath)
                fname = os.path.basename(fpath)
                if os.path.basename(chosen_dir.rstrip("/\\")) == year_str:
                    target_dir = chosen_dir
                else:
                    target_dir = os.path.join(chosen_dir, year_str)

                os.makedirs(target_dir, exist_ok=True)
                final_fpath = os.path.join(target_dir, fname)

                with open(final_fpath, "w", encoding="utf-8") as f:
                    f.write(self.txt_print.get("1.0", "end"))
                messagebox.showinfo("Erfolg", f"Druckansicht gespeichert unter:\n{final_fpath}", parent=self)
        except Exception as e:
            messagebox.showerror("Fehler beim Speichern", f"Fehler beim Speichern der Textdatei:\n{e}", parent=self)

    def direct_print_pdf(self):
        """Generiert die DIN 5008 PDF-Auswertung und öffnet sie im System-PDF-Viewer zum Drucken (CachyOS / Linux / Windows / macOS)."""
        try:
            m_name = get_german_month_name(self.current_month)
            incomes = self.db.get_incomes(self.current_month, self.current_year)
            fixed_costs = self.db.get_fixed_costs()
            recurring = self.db.get_recurring_expenses()
            invoices = self.db.get_invoices(self.current_month, self.current_year)
            pflegegeld = self.db.get_pflegegeld_list()

            tot_inc = sum(safe_float(r[2]) for r in incomes if safe_int(r[4], 1) == 1)
            tot_fix = sum(
                safe_float(r[2]) for r in fixed_costs
                if safe_int(r[4], 1) == 1 and check_fixed_cost_due(
                    safe_int(r[6] if len(r) > 6 else 0, 0),
                    safe_int(r[7] if len(r) > 7 else 0, 0),
                    self.current_month,
                    self.current_year
                )
            )
            tot_rec = 0.0
            for r in recurring:
                is_active = (safe_int(r[6], 1) == 1)
                s_m = safe_int(r[7] if len(r) > 7 else 1, 1)
                s_y = safe_int(r[8] if len(r) > 8 else 2026, 2026)
                e_m = safe_int(r[9] if len(r) > 9 else 0, 0)
                e_y = safe_int(r[10] if len(r) > 10 else 0, 0)
                due_m = r[12] if len(r) > 12 else ""
                amt = safe_float(r[2], 0.0)
                if is_active and check_recurring_due(r[4], r[5], s_m, s_y, e_m, e_y, self.current_month, self.current_year, due_m):
                    tot_rec += amt
            tot_inv = sum(safe_float(r[3]) for r in invoices)
            tot_exp = tot_fix + tot_rec + tot_inv
            balance = tot_inc - tot_exp

            pdf_gen = DIN5008PDFGenerator(
                month_name=m_name,
                year=self.current_year,
                incomes=incomes,
                fixed_costs=fixed_costs,
                recurring=recurring,
                invoices=invoices,
                pflegegeld=pflegegeld,
                tot_inc=tot_inc,
                tot_exp=tot_exp,
                balance=balance,
                month_int=self.current_month
            )
            pdf_bytes = pdf_gen.generate_pdf()

            temp_dir = tempfile.gettempdir()
            temp_path = os.path.join(temp_dir, f"Haushaltsbuch_Druck_{m_name}_{self.current_year}.pdf")
            with open(temp_path, "wb") as f:
                f.write(pdf_bytes)

            opened = False
            if sys.platform.startswith("win"):
                try:
                    os.startfile(temp_path, "print")
                    opened = True
                except Exception:
                    os.startfile(temp_path)
                    opened = True
            elif sys.platform.startswith("darwin"):
                res = subprocess.run(["open", temp_path])
                opened = (res.returncode == 0)
            else:
                # Linux (CachyOS / Arch Linux / KDE / GNOME)
                # Versuche bevorzugt interaktive Viewer/Druckdialoge zu öffnen
                for opener in ["xdg-open", "okular", "evince", "atril", "gio", "gtklp"]:
                    if shutil.which(opener):
                        try:
                            if opener == "gio":
                                subprocess.Popen(["gio", "open", temp_path])
                            else:
                                subprocess.Popen([opener, temp_path])
                            opened = True
                            break
                        except Exception:
                            pass

                if not opened:
                    try:
                        webbrowser.open(f"file://{temp_path}")
                        opened = True
                    except Exception:
                        pass

            if opened:
                messagebox.showinfo(
                    "Druckansicht geöffnet",
                    f"Das DIN 5008 PDF wurde erstellt und im PDF-Viewer geöffnet.\n\n"
                    f"Sie können im geöffneten Fenster direkt 'Drucken' wählen (Strg+P).\n\n"
                    f"Die temporäre Datei wird nach der Nutzung automatisch gelöscht.",
                    parent=self
                )
            else:
                messagebox.showwarning(
                    "Hinweis",
                    f"PDF-Datei wurde erstellt unter:\n{temp_path}\n\nEs konnte jedoch kein PDF-Viewer auf Ihrem System automatisch gestartet werden.",
                    parent=self
                )

            # Automatische Säuberung der temporären Druckdatei
            self.after(15000, lambda: self.cleanup_temp_file(temp_path))
        except Exception as e:
            messagebox.showerror("Druckfehler", f"Fehler beim Erstellen oder Öffnen der Druckansicht:\n{e}", parent=self)

    def export_print_pdf(self):
        try:
            m_name = get_german_month_name(self.current_month)
            incomes = self.db.get_incomes(self.current_month, self.current_year)
            fixed_costs = self.db.get_fixed_costs()
            recurring = self.db.get_recurring_expenses()
            invoices = self.db.get_invoices(self.current_month, self.current_year)
            pflegegeld = self.db.get_pflegegeld_list()

            tot_inc = sum(safe_float(r[2]) for r in incomes if safe_int(r[4], 1) == 1)
            tot_fix = sum(
                safe_float(r[2]) for r in fixed_costs
                if safe_int(r[4], 1) == 1 and check_fixed_cost_due(
                    safe_int(r[6] if len(r) > 6 else 0, 0),
                    safe_int(r[7] if len(r) > 7 else 0, 0),
                    self.current_month,
                    self.current_year
                )
            )
            tot_rec = 0.0
            for r in recurring:
                is_active = (safe_int(r[6], 1) == 1)
                s_m = safe_int(r[7] if len(r) > 7 else 1, 1)
                s_y = safe_int(r[8] if len(r) > 8 else 2026, 2026)
                e_m = safe_int(r[9] if len(r) > 9 else 0, 0)
                e_y = safe_int(r[10] if len(r) > 10 else 0, 0)
                due_m = r[12] if len(r) > 12 else ""
                amt = safe_float(r[2], 0.0)
                if is_active and check_recurring_due(r[4], r[5], s_m, s_y, e_m, e_y, self.current_month, self.current_year, due_m):
                    tot_rec += amt
            tot_inv = sum(safe_float(r[3]) for r in invoices)
            tot_exp = tot_fix + tot_rec + tot_inv
            balance = tot_inc - tot_exp

            year_str = str(self.current_year)
            archiv_dir = os.path.join(SCRIPT_DIR, "archiv", year_str)
            os.makedirs(archiv_dir, exist_ok=True)

            default_filename = f"Haushaltsbuch_Auswertung_{m_name}_{year_str}.pdf"
            try:
                fpath = filedialog.asksaveasfilename(
                    parent=self,
                    initialdir=archiv_dir,
                    defaultextension=".pdf",
                    filetypes=[("PDF Dokument", "*.pdf"), ("Alle Dateien", "*.*")],
                    title="Monatsauswertung (DIN 5008) als PDF speichern",
                    initialfile=default_filename
                )
            except Exception:
                fpath = filedialog.asksaveasfilename(
                    initialdir=archiv_dir,
                    defaultextension=".pdf",
                    filetypes=[("PDF Dokument", "*.pdf"), ("Alle Dateien", "*.*")],
                    title="Monatsauswertung (DIN 5008) als PDF speichern",
                    initialfile=default_filename
                )

            if fpath:
                chosen_dir = os.path.dirname(fpath)
                fname = os.path.basename(fpath)

                if os.path.basename(chosen_dir.rstrip("/\\")) == year_str:
                    target_dir = chosen_dir
                else:
                    target_dir = os.path.join(chosen_dir, year_str)

                os.makedirs(target_dir, exist_ok=True)
                final_fpath = os.path.join(target_dir, fname)

                pdf_gen = DIN5008PDFGenerator(
                    month_name=m_name,
                    year=self.current_year,
                    incomes=incomes,
                    fixed_costs=fixed_costs,
                    recurring=recurring,
                    invoices=invoices,
                    pflegegeld=pflegegeld,
                    tot_inc=tot_inc,
                    tot_exp=tot_exp,
                    balance=balance,
                    month_int=self.current_month
                )
                pdf_bytes = pdf_gen.generate_pdf()
                with open(final_fpath, "wb") as f:
                    f.write(pdf_bytes)
                messagebox.showinfo("Erfolg", f"Monatsauswertung (DIN 5008) erfolgreich als PDF gespeichert unter:\n{final_fpath}", parent=self)
        except Exception as e:
            messagebox.showerror("Fehler beim PDF-Export", f"Fehler beim Generieren oder Speichern des PDF:\n{e}", parent=self)

    # =========================================================================
    # DIALOGS & ACTIONS
    # =========================================================================
    # --- Categories Dialog ---
    def open_categories_dialog(self):
        dlg = tk.Toplevel(self)
        dlg.title("Kategorien verwalten")
        dlg.transient(self)
        dlg.grab_set()

        frame = ttk.Frame(dlg, padding=16)
        frame.pack(fill="both", expand=True)

        ttk.Label(frame, text="🏷️ Vorhandene Kategorien", font=(self.font_family, 12, "bold")).pack(anchor="w", pady=(0, 8))

        tree_frame = ttk.Frame(frame)
        tree_frame.pack(fill="both", expand=True, pady=6)

        cols = ("id", "name", "type")
        tree = ttk.Treeview(tree_frame, columns=cols, show="headings", height=9)
        tree.heading("id", text="ID ▲▼", command=lambda: sort_tree_column("id"))
        tree.heading("name", text="Kategoriename ▲▼", command=lambda: sort_tree_column("name"))
        tree.heading("type", text="Typ ▲▼", command=lambda: sort_tree_column("type"))
        tree.column("id", width=50, anchor="center")
        tree.column("name", width=250)
        tree.column("type", width=140)

        tree.tag_configure("even", background=self.tree_even_bg, foreground=self.text_primary)
        tree.tag_configure("odd", background=self.tree_odd_bg, foreground=self.text_primary)

        tree_scroll = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        tree.configure(yscroll=tree_scroll.set)

        tree.pack(side="left", fill="both", expand=True)
        tree_scroll.pack(side="right", fill="y")

        sort_state = {"col": "type", "reverse": False}

        def load_cats():
            for item in tree.get_children():
                tree.delete(item)
            for idx, c in enumerate(self.db.get_categories()):
                zebra = "even" if idx % 2 == 0 else "odd"
                tree.insert("", "end", values=c, tags=(zebra,))

        def reapply_zebra():
            for idx, item in enumerate(tree.get_children()):
                zebra = "even" if idx % 2 == 0 else "odd"
                tree.item(item, tags=(zebra,))

        def sort_tree_column(col):
            if sort_state["col"] == col:
                sort_state["reverse"] = not sort_state["reverse"]
            else:
                sort_state["col"] = col
                sort_state["reverse"] = False

            col_index = cols.index(col)
            items = [(tree.set(k, col), k) for k in tree.get_children('')]
            
            # Numeric sort for ID, alphanumeric for strings
            if col == "id":
                items.sort(key=lambda x: int(x[0]) if str(x[0]).isdigit() else 0, reverse=sort_state["reverse"])
            else:
                items.sort(key=lambda x: str(x[0]).lower(), reverse=sort_state["reverse"])

            for index, (val, k) in enumerate(items):
                tree.move(k, '', index)

            reapply_zebra()

        load_cats()

        # Add Category Section
        add_f = ttk.LabelFrame(frame, text=" ➕ Neue Kategorie hinzufügen ", padding=10)
        add_f.pack(fill="x", pady=(8, 4))

        ttk.Label(add_f, text="Name:").pack(side="left", padx=(0, 4))
        ent_name = ttk.Entry(add_f, width=18)
        ent_name.pack(side="left", padx=4)

        ttk.Label(add_f, text="Typ:").pack(side="left", padx=(8, 4))
        cmb_type = ttk.Combobox(add_f, values=["Einnahme", "Fixkosten", "Wiederkehrend", "Rechnung", "Ausgabe", "Sonstiges"], width=13, state="readonly")
        cmb_type.set("Ausgabe")
        cmb_type.pack(side="left", padx=4)

        def do_add():
            nm = ent_name.get().strip()
            tp = cmb_type.get().strip()
            if nm:
                try:
                    self.db.add_category(nm, tp)
                    ent_name.delete(0, "end")
                    load_cats()
                    self.refresh_all_views()
                except Exception as e:
                    messagebox.showerror("Fehler", f"Kategorie existiert bereits oder Fehler: {e}", parent=dlg)

        btn_add = ttk.Button(add_f, text="Hinzufügen", style="Primary.TButton", command=do_add)
        btn_add.pack(side="left", padx=(8, 0))

        # Action Buttons (Edit / Delete)
        btn_bar = ttk.Frame(frame)
        btn_bar.pack(fill="x", pady=(8, 0))

        def do_edit():
            sel = tree.selection()
            if not sel:
                messagebox.showwarning("Hinweis", "Bitte wählen Sie zuerst eine Kategorie zum Bearbeiten aus.", parent=dlg)
                return
            item = tree.item(sel[0])
            cat_id, cur_name, cur_type = item["values"][0], item["values"][1], item["values"][2]

            edit_dlg = tk.Toplevel(dlg)
            edit_dlg.title("Kategorie bearbeiten")
            edit_dlg.transient(dlg)
            edit_dlg.grab_set()

            e_frame = ttk.Frame(edit_dlg, padding=16)
            e_frame.pack(fill="both", expand=True)

            ttk.Label(e_frame, text="✏️ Kategorie bearbeiten", font=(self.font_family, 11, "bold")).grid(row=0, column=0, columnspan=2, sticky="w", pady=(0, 10))

            ttk.Label(e_frame, text="Name:").grid(row=1, column=0, sticky="w", pady=4)
            e_name = ttk.Entry(e_frame, width=24)
            e_name.insert(0, str(cur_name))
            e_name.grid(row=1, column=1, pady=4, padx=6, sticky="w")
            e_name.focus_set()

            ttk.Label(e_frame, text="Typ:").grid(row=2, column=0, sticky="w", pady=4)
            e_type = ttk.Combobox(e_frame, values=["Einnahme", "Fixkosten", "Wiederkehrend", "Rechnung", "Ausgabe", "Sonstiges"], width=22, state="readonly")
            e_type.set(str(cur_type) if cur_type in ["Einnahme", "Fixkosten", "Wiederkehrend", "Rechnung", "Ausgabe", "Sonstiges"] else "Ausgabe")
            e_type.grid(row=2, column=1, pady=4, padx=6, sticky="w")

            def save_edit():
                new_nm = e_name.get().strip()
                new_tp = e_type.get().strip()
                if new_nm:
                    try:
                        self.db.update_category(cat_id, new_nm, new_tp, old_name=cur_name)
                        edit_dlg.destroy()
                        load_cats()
                        self.refresh_all_views()
                    except Exception as ex:
                        messagebox.showerror("Fehler", f"Kategorie konnte nicht geändert werden:\n{ex}", parent=edit_dlg)

            ebtn_f = ttk.Frame(e_frame)
            ebtn_f.grid(row=3, column=0, columnspan=2, pady=(12, 0), sticky="e")
            ttk.Button(ebtn_f, text="💾 Speichern", style="Primary.TButton", command=save_edit).pack(side="left", padx=4)
            ttk.Button(ebtn_f, text="Abbrechen", command=edit_dlg.destroy).pack(side="left", padx=4)

            # Auto-adjust edit dialog size to content
            self.auto_fit_dialog(edit_dlg, min_w=380, min_h=200)

        def do_del():
            sel = tree.selection()
            if sel:
                item = tree.item(sel[0])
                cat_id = item["values"][0]
                cat_name = item["values"][1]
                if messagebox.askyesno("Kategorie löschen", f"Möchten Sie die Kategorie '{cat_name}' wirklich löschen?", parent=dlg):
                    self.db.delete_category(cat_id)
                    load_cats()
                    self.refresh_all_views()

        tree.bind("<Double-1>", lambda event: do_edit())

        btn_edit = ttk.Button(btn_bar, text="✏️ Bearbeiten", command=do_edit)
        btn_edit.pack(side="left", padx=(0, 4))

        btn_del = ttk.Button(btn_bar, text="🗑️ Löschen", command=do_del)
        btn_del.pack(side="left", padx=4)

        ttk.Button(btn_bar, text="Schließen", command=dlg.destroy).pack(side="right")

        # Auto-align dialog window dimensions to content
        self.auto_fit_dialog(dlg, min_w=520, min_h=440)

    # --- Settings & Backup Dialog ---
    def open_settings_dialog(self):
        dlg = tk.Toplevel(self)
        dlg.title("Einstellungen & Datensicherung")
        dlg.transient(self)
        dlg.grab_set()

        frame = ttk.Frame(dlg, padding=16)
        frame.pack(fill="both", expand=True)

        ttk.Label(frame, text="⚙️ Einstellungen & Sicherung", font=(self.font_family, 14, "bold")).pack(anchor="w", pady=(0, 10))

        # Section 1: Color Design / Themes
        ttk.Label(frame, text="🎨 Farbdesign & Theme-Auswahl (Sofortige Anwendung)", font=(self.font_family, 10, "bold")).pack(anchor="w", pady=(2, 4))
        
        theme_frame = ttk.Frame(frame)
        theme_frame.pack(fill="x", pady=(2, 6))

        curr_theme = getattr(self, "active_theme_key", self.db.get_setting("app_theme", "android_material"))

        row_i = 0
        col_i = 0
        for t_key, t_info in APP_THEMES.items():
            is_active = (t_key == curr_theme)
            btn_label = f"{t_info['icon']}  {t_info['name']}"
            if is_active:
                btn_label += "  ✓"

            def make_cmd(k=t_key):
                return lambda: self.apply_theme(k, dlg)

            btn_style = "Primary.TButton" if is_active else "TButton"
            b = ttk.Button(theme_frame, text=btn_label, style=btn_style, command=make_cmd(t_key))
            b.grid(row=row_i, column=col_i, padx=4, pady=4, sticky="ew")

            col_i += 1
            if col_i > 1:
                col_i = 0
                row_i += 1

        theme_frame.columnconfigure(0, weight=1)
        theme_frame.columnconfigure(1, weight=1)

        ttk.Separator(frame, orient="horizontal").pack(fill="x", pady=10)

        # Section 2: Automatic Backup
        ttk.Label(frame, text="🛡️ Automatische Sicherung", font=(self.font_family, 10, "bold")).pack(anchor="w", pady=(6, 2))
        ttk.Label(frame, text="Die App erstellt bei jedem Start automatisch eine Sicherungskopie im Ordner 'backups'.", font=(self.font_family, 8, "italic"), wraplength=520, justify="left").pack(anchor="w", pady=(0, 4))

        last_auto = self.db.get_setting("last_auto_backup", "Bisher keine automatische Sicherung")
        lbl_last = ttk.Label(frame, text=f"Letzte Sicherung: {last_auto}", font=(self.font_family, 9))
        lbl_last.pack(anchor="w", pady=(0, 6))

        btn_auto_frame = ttk.Frame(frame)
        btn_auto_frame.pack(fill="x", pady=(0, 8))

        def do_run_auto_backup():
            ok, res = self.db.create_manual_auto_backup()
            if ok:
                new_last = self.db.get_setting("last_auto_backup", "")
                lbl_last.config(text=f"Letzte Sicherung: {new_last}")
                messagebox.showinfo("Erfolg", f"Automatische Sicherung erfolgreich erstellt:\n{res}", parent=dlg)
            else:
                messagebox.showerror("Fehler", f"Fehler bei automatischer Sicherung:\n{res}", parent=dlg)

        def do_open_backup_folder():
            b_dir = os.path.join(SCRIPT_DIR, "backups")
            os.makedirs(b_dir, exist_ok=True)
            try:
                if sys.platform == "win32":
                    os.startfile(b_dir)
                elif sys.platform == "darwin":
                    subprocess.run(["open", b_dir])
                else:
                    subprocess.run(["xdg-open", b_dir])
            except Exception as e:
                messagebox.showinfo("Backup-Ordner", f"Ordnerpfad:\n{b_dir}", parent=dlg)

        btn_run_auto = ttk.Button(btn_auto_frame, text="🔄 Auto-Sicherung jetzt ausführen", command=do_run_auto_backup)
        btn_run_auto.pack(side="left", padx=(0, 6))

        btn_open_folder = ttk.Button(btn_auto_frame, text="📁 Backup-Ordner öffnen", command=do_open_backup_folder)
        btn_open_folder.pack(side="left")

        ttk.Separator(frame, orient="horizontal").pack(fill="x", pady=10)

        # Section 3: Manual JSON Export / Import
        ttk.Label(frame, text="💾 Manuelle Sicherung (JSON Export / Import)", font=(self.font_family, 10, "bold")).pack(anchor="w", pady=(4, 2))

        def do_export():
            fpath = filedialog.asksaveasfilename(
                parent=dlg,
                defaultextension=".json",
                filetypes=[("JSON Backup", "*.json")],
                title="Datensicherung speichern"
            )
            if fpath:
                data = self.db.export_to_json()
                with open(fpath, "w", encoding="utf-8") as f:
                    f.write(data)
                messagebox.showinfo("Erfolg", f"Sicherung erfolgreich gespeichert:\n{fpath}", parent=dlg)

        def do_import():
            fpath = filedialog.askopenfilename(
                parent=dlg,
                filetypes=[("JSON Backup", "*.json"), ("Alle Dateien", "*.*")],
                title="Datensicherung importieren"
            )
            if fpath:
                if messagebox.askyesno("Bestätigung", "Möchten Sie das Backup importieren? Bestehende Daten werden ersetzt."):
                    data_str = ""
                    try:
                        with open(fpath, "r", encoding="utf-8") as f:
                            data_str = f.read()
                    except UnicodeDecodeError:
                        try:
                            with open(fpath, "r", encoding="latin-1") as f:
                                data_str = f.read()
                        except Exception as e:
                            messagebox.showerror("Fehler", f"Datei konnte nicht gelesen werden:\n{e}", parent=dlg)
                            return
                    except Exception as e:
                        messagebox.showerror("Fehler", f"Datei konnte nicht gelesen werden:\n{e}", parent=dlg)
                        return

                    try:
                        self.db.import_from_json(data_str)
                        self.refresh_all_views()
                        messagebox.showinfo("Erfolg", "Sicherung erfolgreich wiederhergestellt!", parent=dlg)
                        dlg.destroy()
                    except Exception as e:
                        messagebox.showerror("Fehler", f"Fehler beim Wiederherstellen der Daten:\n{e}", parent=dlg)

        btn_exp = ttk.Button(frame, text="💾 Backup erstellen (JSON Export)", command=do_export)
        btn_exp.pack(fill="x", pady=2)

        btn_imp = ttk.Button(frame, text="📥 Backup wiederherstellen (JSON Import)", command=do_import)
        btn_imp.pack(fill="x", pady=2)

        ttk.Separator(frame, orient="horizontal").pack(fill="x", pady=10)

        # Section 4: Paths
        ttk.Label(frame, text="📁 Pfade", font=(self.font_family, 10, "bold")).pack(anchor="w", pady=(2, 2))
        
        ttk.Label(frame, text=f"Datenbank: {Path(DB_FILE).absolute()}", font=(self.font_family, 8, "italic")).pack(anchor="w")
        ttk.Label(frame, text=f"Backups:    {Path(os.path.join(SCRIPT_DIR, 'backups')).absolute()}", font=(self.font_family, 8, "italic")).pack(anchor="w", pady=(2, 0))

        # Bottom Button Bar
        btn_close_bar = ttk.Frame(frame)
        btn_close_bar.pack(fill="x", pady=(16, 0))
        ttk.Button(btn_close_bar, text="OK", style="Primary.TButton", command=dlg.destroy).pack(side="right")

        self.auto_fit_dialog(dlg, min_w=580, min_h=580)

    # --- CRUD Forms / Dialogs ---
    def get_cat_names(self):
        return [c[1] for c in self.db.get_categories()]

    def quick_create_category_dialog(self, parent_dlg, target_combobox, default_type="Ausgabe"):
        dlg_cat = tk.Toplevel(parent_dlg)
        dlg_cat.title("Neue Kategorie erstellen")
        dlg_cat.transient(parent_dlg)
        dlg_cat.grab_set()

        f = ttk.Frame(dlg_cat, padding=16)
        f.pack(fill="both", expand=True)

        ttk.Label(f, text="➕ Neue Kategorie erstellen", font=(self.font_family, 11, "bold")).grid(row=0, column=0, columnspan=2, sticky="w", pady=(0, 10))

        ttk.Label(f, text="Name:").grid(row=1, column=0, sticky="w", pady=4)
        ent_name = ttk.Entry(f, width=24)
        ent_name.grid(row=1, column=1, pady=4, sticky="w")
        ent_name.focus_set()

        ttk.Label(f, text="Typ:").grid(row=2, column=0, sticky="w", pady=4)
        cmb_type = ttk.Combobox(f, values=["Einnahme", "Fixkosten", "Wiederkehrend", "Rechnung", "Ausgabe", "Sonstiges"], width=22, state="readonly")
        cmb_type.set(default_type if default_type in ["Einnahme", "Fixkosten", "Wiederkehrend", "Rechnung", "Ausgabe", "Sonstiges"] else "Ausgabe")
        cmb_type.grid(row=2, column=1, pady=4, sticky="w")

        def save_cat():
            nm = ent_name.get().strip()
            tp = cmb_type.get().strip()
            if not nm:
                messagebox.showerror("Fehler", "Bitte einen Kategorienamen eingeben.", parent=dlg_cat)
                return
            try:
                self.db.add_category(nm, tp)
                all_cats = self.get_cat_names()
                target_combobox["values"] = all_cats
                target_combobox.set(nm)
                self.refresh_all_views()
                dlg_cat.destroy()
            except Exception as e:
                messagebox.showerror("Fehler", f"Kategorie konnte nicht hinzugefügt werden:\n{e}", parent=dlg_cat)

        btn_frame = ttk.Frame(f)
        btn_frame.grid(row=3, column=0, columnspan=2, pady=(12, 0), sticky="e")

        ttk.Button(btn_frame, text="Speichern", command=save_cat, style="Primary.TButton").pack(side="left", padx=4)
        ttk.Button(btn_frame, text="Abbrechen", command=dlg_cat.destroy).pack(side="left")

        self.auto_fit_dialog(dlg_cat, min_w=380, min_h=200)

    def open_day_picker_dialog(self, parent_dlg, current_day, on_select_callback):
        dlg_day = tk.Toplevel(parent_dlg)
        dlg_day.title("Tag des Monats auswählen")
        dlg_day.resizable(False, False)
        dlg_day.transient(parent_dlg)
        dlg_day.grab_set()

        f = ttk.Frame(dlg_day, padding=16)
        f.pack(fill="both", expand=True)

        ttk.Label(f, text="📅 Fälligen Tag des Monats wählen", font=(self.font_family, 11, "bold")).pack(anchor="w", pady=(0, 6))
        ttk.Label(f, text="Wählen Sie den Tag (1-31) für die monatliche Abbuchung:", font=(self.font_family, 9)).pack(anchor="w", pady=(0, 10))

        grid_f = ttk.Frame(f)
        grid_f.pack(fill="both", expand=True, pady=4)

        for d in range(1, 32):
            row = (d - 1) // 7
            col = (d - 1) % 7

            def make_cmd(day_num):
                return lambda: [on_select_callback(day_num), dlg_day.destroy()]

            style_name = "Primary.TButton" if d == current_day else "TButton"
            btn = ttk.Button(grid_f, text=str(d), width=4, style=style_name, command=make_cmd(d))
            btn.grid(row=row, column=col, padx=3, pady=3)

        btn_f = ttk.Frame(f)
        btn_f.pack(fill="x", pady=(12, 0))
        ttk.Button(btn_f, text="Abbrechen", command=dlg_day.destroy).pack(side="right")

        self.auto_fit_dialog(dlg_day, min_w=380, min_h=320)

    def edit_overview_item(self, event=None):
        target_item = None
        if event:
            row_id = self.tree_overview.identify_row(event.y) or self.tree_overview.identify("item", event.x, event.y)
            if row_id:
                self.tree_overview.selection_set(row_id)
                target_item = row_id
        if not target_item:
            sel = self.tree_overview.selection()
            if sel:
                target_item = sel[0]
        if not target_item:
            return
        item_data = self.tree_overview.item(target_item)
        tags = item_data.get("tags", [])
        if isinstance(tags, str):
            tags = tags.split()
        if len(tags) >= 2:
            item_type = tags[0]
            try:
                item_id = int(tags[1])
            except (ValueError, TypeError):
                return

            if item_type == "income":
                rec = self.db.get_income_by_id(item_id)
                if rec:
                    is_rec = bool(rec[7]) if len(rec) > 7 else True
                    self.show_income_dialog(item_id=rec[0], title=rec[1], amount=rec[2], cat=rec[3], active=bool(rec[4]), is_recurring=is_rec, month=rec[5], year=rec[6])
            elif item_type == "fixed":
                rec = self.db.get_fixed_cost_by_id(item_id)
                if rec:
                    dom = rec[5] if len(rec) > 5 else 1
                    e_m = rec[6] if len(rec) > 6 else 0
                    e_y = rec[7] if len(rec) > 7 else 0
                    self.show_fixed_cost_dialog(item_id=rec[0], title=rec[1], amount=rec[2], cat=rec[3], active=bool(rec[4]), day_of_month=dom, end_month=e_m, end_year=e_y)
            elif item_type == "recurring":
                rec = self.db.get_recurring_expense_by_id(item_id)
                if rec:
                    freq = rec[4]
                    m_spec = rec[5]
                    active = bool(rec[6])
                    s_m = rec[7] if len(rec) > 7 else 1
                    s_y = rec[8] if len(rec) > 8 else 2026
                    e_m = rec[9] if len(rec) > 9 else 0
                    e_y = rec[10] if len(rec) > 10 else 0
                    note = rec[11] if len(rec) > 11 else ""
                    due_m = rec[12] if len(rec) > 12 else ""
                    self.show_recurring_dialog(item_id=rec[0], title=rec[1], amount=rec[2], cat=rec[3], freq=freq, month_specified=m_spec, active=active, start_m=s_m, start_y=s_y, end_m=e_m, end_y=e_y, note=note, due_months=due_m)
            elif item_type == "invoice":
                rec = self.db.get_invoice_by_id(item_id)
                if rec:
                    self.show_invoice_dialog(item_id=rec[0], vendor=rec[1], desc=rec[2], amount=rec[3], due=rec[4], cat=rec[5], paid=bool(rec[6]))

    # Income
    def add_income(self):
        self.show_income_dialog()

    def edit_income(self, event=None):
        target_item = None
        if event:
            row_id = self.tree_income.identify_row(event.y) or self.tree_income.identify("item", event.x, event.y)
            if row_id:
                self.tree_income.selection_set(row_id)
                target_item = row_id
        if not target_item:
            sel = self.tree_income.selection()
            if sel:
                target_item = sel[0]
        if not target_item:
            messagebox.showwarning("Hinweis", "Bitte wählen Sie einen Eintrag aus.")
            return
        vals = self.tree_income.item(target_item).get("values", [])
        if not vals:
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        try:
            item_id = int(vals[0])
        except (IndexError, ValueError, TypeError):
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        rec = self.db.get_income_by_id(item_id)
        if rec:
            is_rec = bool(rec[7]) if len(rec) > 7 else True
            self.show_income_dialog(
                item_id=rec[0],
                title=rec[1],
                amount=rec[2],
                cat=rec[3],
                active=bool(rec[4]),
                is_recurring=is_rec,
                month=rec[5],
                year=rec[6]
            )

    def adjust_income_for_month(self):
        sel = self.tree_income.selection()
        if not sel:
            messagebox.showwarning("Hinweis", "Bitte wählen Sie eine Einnahme aus, für die Sie den Betrag diesen Monat anpassen möchten.")
            return
        item_id = self.tree_income.item(sel[0])["values"][0]
        rec = self.db.get_income_by_id(item_id)
        if rec:
            is_rec = bool(rec[7]) if len(rec) > 7 else True
            self.show_income_dialog(
                item_id=rec[0] if not is_rec else None,
                title=rec[1],
                amount=rec[2],
                cat=rec[3],
                active=bool(rec[4]),
                is_recurring=is_rec,
                month=self.current_month,
                year=self.current_year,
                force_current_month=True
            )

    def delete_income(self):
        sel = self.tree_income.selection()
        if sel and messagebox.askyesno("Löschen", "Eintrag wirklich löschen?"):
            item_id = self.tree_income.item(sel[0])["values"][0]
            self.db.delete_income(item_id)
            self.refresh_all_views()

    # --- Pflegegeld Dialogs & Handlers ---
    def add_pflegegeld(self):
        self.show_pflegegeld_dialog()

    def edit_pflegegeld(self, event=None):
        target_item = None
        if event:
            row_id = self.tree_pflegegeld.identify_row(event.y) or self.tree_pflegegeld.identify("item", event.x, event.y)
            if row_id:
                self.tree_pflegegeld.selection_set(row_id)
                target_item = row_id
        if not target_item:
            sel = self.tree_pflegegeld.selection()
            if sel:
                target_item = sel[0]
        if not target_item:
            messagebox.showwarning("Hinweis", "Bitte wählen Sie einen Pflegegeld-Eintrag aus.")
            return
        vals = self.tree_pflegegeld.item(target_item).get("values", [])
        if not vals:
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        try:
            item_id = int(vals[0])
        except (IndexError, ValueError, TypeError):
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        rec = self.db.get_pflegegeld_by_id(item_id)
        if rec:
            self.show_pflegegeld_dialog(
                item_id=rec[0],
                beguenstigter=rec[1],
                ueberweiser=rec[2],
                pflegegrad=rec[3],
                gesamt=rec[4],
                hilfsmittel=rec[5],
                anteil=rec[6],
                gueltigkeit=rec[8],
                rhythmus=rec[9],
                active=bool(rec[10])
            )

    def delete_pflegegeld(self):
        sel = self.tree_pflegegeld.selection()
        if not sel:
            messagebox.showwarning("Hinweis", "Bitte wählen Sie einen Pflegegeld-Eintrag aus.")
            return
        if messagebox.askyesno("Löschen", "Pflegegeld-Eintrag wirklich löschen?"):
            item_id = self.tree_pflegegeld.item(sel[0])["values"][0]
            self.db.delete_pflegegeld(item_id)
            self.refresh_all_views()

    def show_pflegegeld_dialog(
        self,
        item_id=None,
        beguenstigter="",
        ueberweiser="",
        pflegegrad="Pflegegrad 1",
        gesamt=0.0,
        hilfsmittel=0.0,
        anteil=100.0,
        gueltigkeit="",
        rhythmus="Monatlich",
        active=True
    ):
        dlg = tk.Toplevel(self)
        dlg.title("Pflegegeld bearbeiten" if item_id else "Pflegegeld hinzufügen")
        dlg.transient(self)
        dlg.grab_set()

        f = ttk.Frame(dlg, padding=16)
        f.pack(fill="both", expand=True)

        ttk.Label(
            f,
            text="🏥 Pflegegeld bearbeiten" if item_id else "🏥 Pflegegeld hinzufügen",
            font=("Helvetica", 12, "bold")
        ).grid(row=0, column=0, columnspan=2, sticky="w", pady=(0, 12))

        # 1. Begünstigter
        ttk.Label(f, text="Begünstigter:").grid(row=1, column=0, sticky="w", pady=4)
        ent_beguenstigter = ttk.Entry(f, width=32)
        ent_beguenstigter.insert(0, beguenstigter)
        ent_beguenstigter.grid(row=1, column=1, pady=4, sticky="w")

        # 2. Überweiser
        ttk.Label(f, text="Überweiser:").grid(row=2, column=0, sticky="w", pady=4)
        ent_ueberweiser = ttk.Entry(f, width=32)
        ent_ueberweiser.insert(0, ueberweiser)
        ent_ueberweiser.grid(row=2, column=1, pady=4, sticky="w")

        # 3. Auswahl Pflegegrad / Stufe
        ttk.Label(f, text="Pflegegrad / Stufe:").grid(row=3, column=0, sticky="w", pady=4)
        p_grades = ["Pflegegrad 1", "Pflegegrad 2", "Pflegegrad 3", "Pflegegrad 4", "Pflegegrad 5"]
        cmb_pflegegrad = ttk.Combobox(f, values=p_grades, width=30, state="readonly")
        cmb_pflegegrad.set(pflegegrad if pflegegrad in p_grades else "Pflegegrad 1")
        cmb_pflegegrad.grid(row=3, column=1, pady=4, sticky="w")

        # 4. Gesamtpflegegeld daneben Pflegehilfsmittel
        ttk.Label(f, text="Gesamtpflegegeld (€):").grid(row=4, column=0, sticky="w", pady=4)
        amt_f = ttk.Frame(f)
        amt_f.grid(row=4, column=1, sticky="w", pady=4)

        ent_gesamt = ttk.Entry(amt_f, width=11)
        if gesamt is not None and gesamt != "":
            try:
                ent_gesamt.insert(0, f"{float(gesamt):.2f}".replace(".", ","))
            except (ValueError, TypeError):
                ent_gesamt.insert(0, str(gesamt))
        ent_gesamt.pack(side="left", padx=(0, 6))

        ttk.Label(amt_f, text="Pflegehilfsmittel (€):").pack(side="left", padx=(4, 4))
        ent_hilfsmittel = ttk.Entry(amt_f, width=11)
        if hilfsmittel is not None and hilfsmittel != "":
            try:
                ent_hilfsmittel.insert(0, f"{float(hilfsmittel):.2f}".replace(".", ","))
            except (ValueError, TypeError):
                ent_hilfsmittel.insert(0, str(hilfsmittel))
        ent_hilfsmittel.pack(side="left")

        # 5. Zusammenfassung Gesamtsumme Pflege (über Anteil des Überweisers)
        ttk.Label(f, text="Gesamtsumme Pflege (€):").grid(row=5, column=0, sticky="w", pady=4)
        lbl_gesamtsumme_pflege = ttk.Label(f, text="0,00 €", font=("Helvetica", 10, "bold"))
        lbl_gesamtsumme_pflege.grid(row=5, column=1, sticky="w", pady=4)

        # 6. Anteil des Überweisers in %
        ttk.Label(f, text="Anteil des Überweisers (%):").grid(row=6, column=0, sticky="w", pady=4)
        ent_anteil = ttk.Entry(f, width=32)
        if anteil is not None and anteil != "":
            try:
                ent_anteil.insert(0, f"{float(anteil):g}".replace(".", ","))
            except (ValueError, TypeError):
                ent_anteil.insert(0, str(anteil))
        else:
            ent_anteil.insert(0, "100")
        ent_anteil.grid(row=6, column=1, pady=4, sticky="w")

        # 7. Ergebnis (Berechnung)
        ttk.Label(f, text="Ergebnis (Auszahlung €):").grid(row=7, column=0, sticky="w", pady=4)
        lbl_ergebnis = ttk.Label(f, text="0,00 €", font=("Helvetica", 11, "bold"), foreground="#2e7d32")
        lbl_ergebnis.grid(row=7, column=1, sticky="w", pady=4)

        def calc_ergebnis(*args):
            try:
                g_val = float(ent_gesamt.get().strip().replace(".", "").replace(",", "."))
            except (ValueError, TypeError):
                g_val = 0.0
            try:
                h_val = float(ent_hilfsmittel.get().strip().replace(".", "").replace(",", "."))
            except (ValueError, TypeError):
                h_val = 0.0
            try:
                a_val = float(ent_anteil.get().strip().replace(".", "").replace(",", "."))
            except (ValueError, TypeError):
                a_val = 100.0
            
            summe_pf = g_val + h_val
            lbl_gesamtsumme_pflege.config(text=f"{summe_pf:,.2f} €")

            res = summe_pf * (a_val / 100.0)
            lbl_ergebnis.config(text=f"{res:,.2f} €")
            return res

        ent_gesamt.bind("<KeyRelease>", calc_ergebnis)
        ent_hilfsmittel.bind("<KeyRelease>", calc_ergebnis)
        ent_anteil.bind("<KeyRelease>", calc_ergebnis)
        calc_ergebnis()

        # 8. Gültigkeit
        ttk.Label(f, text="Gültigkeit:").grid(row=8, column=0, sticky="w", pady=4)
        ent_gueltigkeit = ttk.Entry(f, width=32)
        ent_gueltigkeit.insert(0, gueltigkeit)
        ent_gueltigkeit.grid(row=8, column=1, pady=4, sticky="w")

        # 9. Rhythmus
        ttk.Label(f, text="Rhythmus:").grid(row=9, column=0, sticky="w", pady=4)
        r_list = ["Monatlich", "Alle 2 Monate", "Vierteljährlich", "Halbjährlich", "Jährlich", "Einmalig"]
        cmb_rhythmus = ttk.Combobox(f, values=r_list, width=30, state="readonly")
        cmb_rhythmus.set(rhythmus if rhythmus in r_list else "Monatlich")
        cmb_rhythmus.grid(row=9, column=1, pady=4, sticky="w")

        # 10. Aktiv oder nicht
        var_active = tk.BooleanVar(value=active)
        chk_active = ttk.Checkbutton(f, text="Aktiv (wird erfasst)", variable=var_active)
        chk_active.grid(row=10, column=1, sticky="w", pady=6)

        def save():
            beg_str = ent_beguenstigter.get().strip()
            ueb_str = ent_ueberweiser.get().strip()
            pgrad_str = cmb_pflegegrad.get().strip()
            guelt_str = ent_gueltigkeit.get().strip()
            rhythm_str = cmb_rhythmus.get().strip()

            try:
                g_val = float(ent_gesamt.get().strip().replace(".", "").replace(",", "."))
            except ValueError:
                messagebox.showerror("Fehler", "Bitte einen gültigen Betrag für das Gesamtpflegegeld eingeben.")
                return

            try:
                h_str = ent_hilfsmittel.get().strip().replace(".", "").replace(",", ".")
                h_val = float(h_str) if h_str else 0.0
            except ValueError:
                messagebox.showerror("Fehler", "Bitte einen gültigen Betrag für Pflegehilfsmittel eingeben.")
                return

            try:
                a_str = ent_anteil.get().strip().replace(".", "").replace(",", ".")
                a_val = float(a_str) if a_str else 100.0
            except ValueError:
                messagebox.showerror("Fehler", "Bitte eine gültige Prozentzahl für den Anteil eingeben.")
                return

            res_val = (g_val + h_val) * (a_val / 100.0)

            self.db.save_pflegegeld(
                item_id=item_id,
                beguenstigter=beg_str,
                ueberweiser=ueb_str,
                pflegegrad=pgrad_str,
                gesamtpflegegeld=g_val,
                pflegehilfsmittel=h_val,
                anteil_prozent=a_val,
                ergebnis=res_val,
                gueltigkeit=guelt_str,
                rhythmus=rhythm_str,
                is_active=1 if var_active.get() else 0
            )

            self.refresh_all_views()
            dlg.destroy()

        btn_box = ttk.Frame(f)
        btn_box.grid(row=11, column=0, columnspan=2, pady=(16, 0))
        btn_save = ttk.Button(btn_box, text="💾 Speichern", style="Primary.TButton", command=save)
        btn_save.pack(side="left", padx=6)
        btn_cancel = ttk.Button(btn_box, text="Abbrechen", command=dlg.destroy)
        btn_cancel.pack(side="left", padx=6)

        self.auto_fit_dialog(dlg, min_w=520, min_h=460)

    def show_income_dialog(self, item_id=None, title="", amount=0.0, cat="", active=True, is_recurring=True, month=None, year=None, force_current_month=False):
        dlg = tk.Toplevel(self)
        dlg.title("Einnahme " + ("für aktuellen Monat anpassen" if force_current_month else ("bearbeiten" if item_id else "hinzufügen")))
        dlg.resizable(False, False)
        dlg.transient(self)
        dlg.grab_set()

        f = ttk.Frame(dlg, padding=16)
        f.pack(fill="both", expand=True)

        ttk.Label(f, text="Bezeichnung:").grid(row=0, column=0, sticky="w", pady=6)
        ent_titel = ttk.Entry(f, width=28)
        ent_titel.insert(0, title)
        ent_titel.grid(row=0, column=1, pady=6, sticky="w")

        ttk.Label(f, text="Betrag (€):").grid(row=1, column=0, sticky="w", pady=6)
        ent_amount = ttk.Entry(f, width=28)
        if amount is not None and amount != "":
            try:
                ent_amount.insert(0, f"{float(amount):.2f}".replace(".", ","))
            except (ValueError, TypeError):
                ent_amount.insert(0, str(amount))
        ent_amount.grid(row=1, column=1, pady=6, sticky="w")

        ttk.Label(f, text="Kategorie:").grid(row=2, column=0, sticky="nw", pady=6)
        cat_f = ttk.Frame(f)
        cat_f.grid(row=2, column=1, pady=6, sticky="w")
        cmb_cat = ttk.Combobox(cat_f, values=self.get_cat_names(), width=28)
        if cat: cmb_cat.set(cat)
        elif self.get_cat_names(): cmb_cat.set(self.get_cat_names()[0])
        cmb_cat.pack(anchor="w")
        btn_new_cat = ttk.Button(cat_f, text="➕ Neue Kategorie erstellen", command=lambda: self.quick_create_category_dialog(dlg, cmb_cat, "Einnahme"))
        btn_new_cat.pack(anchor="w", pady=(4, 0))

        # Wiederkehrend Checkbox
        var_rec = tk.BooleanVar(value=is_recurring)
        chk_rec = ttk.Checkbutton(f, text="Monatlich wiederkehrend (jeden Monat verbuchen)", variable=var_rec)
        chk_rec.grid(row=3, column=0, columnspan=2, sticky="w", pady=(10, 4))

        # Radio buttons for scope / applicability
        m_name = get_german_month_name(self.current_month)
        initial_scope = "current" if (force_current_month or (month and month > 0 and not is_recurring)) else ("all" if is_recurring else "current")
        scope_var = tk.StringVar(value=initial_scope)

        lbl_scope = ttk.Label(f, text="Gültigkeit / Anpassung:", font=("Helvetica", 9, "bold"))
        lbl_scope.grid(row=4, column=0, columnspan=2, sticky="w", pady=(8, 2))

        rb_all = ttk.Radiobutton(
            f,
            text="Dauerhaft für alle Monate übernehmen (Hauptbetrag)",
            variable=scope_var,
            value="all"
        )
        rb_all.grid(row=5, column=0, columnspan=2, sticky="w", padx=16, pady=2)

        rb_current = ttk.Radiobutton(
            f,
            text=f"Sonderbetrag nur für {m_name} {self.current_year} anpassen",
            variable=scope_var,
            value="current"
        )
        rb_current.grid(row=6, column=0, columnspan=2, sticky="w", padx=16, pady=2)

        def toggle_rec_state():
            if not var_rec.get():
                scope_var.set("current")
                rb_all.config(state="disabled")
            else:
                rb_all.config(state="normal")

        chk_rec.config(command=toggle_rec_state)
        toggle_rec_state()

        var_active = tk.BooleanVar(value=active)
        chk_active = ttk.Checkbutton(f, text="Aktiv für Berechnung", variable=var_active)
        chk_active.grid(row=7, column=0, columnspan=2, sticky="w", pady=(8, 12))

        def save():
            t = ent_titel.get().strip()
            if not t:
                messagebox.showerror("Fehler", "Bitte eine Bezeichnung eingeben.")
                return
            try:
                a = float(ent_amount.get().replace(",", "."))
            except ValueError:
                messagebox.showerror("Fehler", "Gültigen Betrag eingeben.")
                return
            c = cmb_cat.get().strip()

            is_r = 1 if var_rec.get() else 0
            sc = scope_var.get()

            if sc == "all" and is_r == 1:
                save_m = 0
                save_y = 0
                save_is_r = 1
                target_id = item_id
            else:
                save_m = self.current_month
                save_y = self.current_year
                save_is_r = 0
                # If editing a recurring entry but choosing current month adjustment, create new month override entry
                if item_id and is_recurring and sc == "current":
                    target_id = None
                else:
                    target_id = item_id

            self.db.save_income(target_id, t, a, c, 1 if var_active.get() else 0, save_m, save_y, save_is_r)
            self.refresh_all_views()
            dlg.destroy()

        btn_box = ttk.Frame(f)
        btn_box.grid(row=8, column=0, columnspan=2, sticky="e", pady=8)

        ttk.Button(btn_box, text="Abbrechen", command=dlg.destroy).pack(side="right", padx=4)
        ttk.Button(btn_box, text="Speichern", style="Primary.TButton", command=save).pack(side="right", padx=4)

        self.auto_fit_dialog(dlg, min_w=460, min_h=380)

    # Fixed Costs
    def add_fixed_cost(self):
        self.show_fixed_cost_dialog()

    def edit_fixed_cost(self, event=None):
        target_item = None
        if event:
            row_id = self.tree_fixed.identify_row(event.y) or self.tree_fixed.identify("item", event.x, event.y)
            if row_id:
                self.tree_fixed.selection_set(row_id)
                target_item = row_id
        if not target_item:
            sel = self.tree_fixed.selection()
            if sel:
                target_item = sel[0]
        if not target_item:
            messagebox.showwarning("Hinweis", "Bitte wählen Sie einen Eintrag aus.")
            return
        vals = self.tree_fixed.item(target_item).get("values", [])
        if not vals:
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        try:
            item_id = int(vals[0])
        except (IndexError, ValueError, TypeError):
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        rec = self.db.get_fixed_cost_by_id(item_id)
        if rec:
            dom = rec[5] if len(rec) > 5 else 1
            e_m = rec[6] if len(rec) > 6 else 0
            e_y = rec[7] if len(rec) > 7 else 0
            self.show_fixed_cost_dialog(item_id=rec[0], title=rec[1], amount=rec[2], cat=rec[3], active=bool(rec[4]), day_of_month=dom, end_month=e_m, end_year=e_y)

    def delete_fixed_cost(self):
        sel = self.tree_fixed.selection()
        if sel and messagebox.askyesno("Löschen", "Eintrag wirklich löschen?"):
            item_id = self.tree_fixed.item(sel[0])["values"][0]
            self.db.delete_fixed_cost(item_id)
            self.refresh_all_views()

    def show_fixed_cost_dialog(self, item_id=None, title="", amount=0.0, cat="", active=True, day_of_month=1, end_month=0, end_year=0):
        dlg = tk.Toplevel(self)
        dlg.title("Fixkosten " + ("bearbeiten" if item_id else "hinzufügen"))
        dlg.transient(self)
        dlg.grab_set()

        f = ttk.Frame(dlg, padding=16)
        f.pack(fill="both", expand=True)

        ttk.Label(f, text="Bezeichnung:").grid(row=0, column=0, sticky="w", pady=4)
        ent_titel = ttk.Entry(f, width=28)
        ent_titel.insert(0, title)
        ent_titel.grid(row=0, column=1, pady=4, sticky="w")

        ttk.Label(f, text="Betrag (€):").grid(row=1, column=0, sticky="w", pady=4)
        ent_amount = ttk.Entry(f, width=28)
        if amount is not None and amount != "":
            try:
                ent_amount.insert(0, f"{float(amount):.2f}".replace(".", ","))
            except (ValueError, TypeError):
                ent_amount.insert(0, str(amount))
        ent_amount.grid(row=1, column=1, pady=4, sticky="w")

        ttk.Label(f, text="Kategorie:").grid(row=2, column=0, sticky="nw", pady=4)
        cat_f = ttk.Frame(f)
        cat_f.grid(row=2, column=1, pady=4, sticky="w")
        cmb_cat = ttk.Combobox(cat_f, values=self.get_cat_names(), width=26)
        if cat: cmb_cat.set(cat)
        elif self.get_cat_names(): cmb_cat.set(self.get_cat_names()[0])
        cmb_cat.pack(anchor="w")
        btn_new_cat = ttk.Button(cat_f, text="➕ Neue Kategorie erstellen", command=lambda: self.quick_create_category_dialog(dlg, cmb_cat, "Fixkosten"))
        btn_new_cat.pack(anchor="w", pady=(4, 0))

        # Fälliger Tag im Monat
        var_day = tk.IntVar(value=day_of_month if day_of_month else 1)
        ttk.Label(f, text="Fälligkeit:").grid(row=3, column=0, sticky="nw", pady=6)
        day_f = ttk.Frame(f)
        day_f.grid(row=3, column=1, pady=6, sticky="w")

        lbl_day = ttk.Label(day_f, text=f"zum {var_day.get()}. des Monats", font=(self.font_family, 10, "bold"), foreground="#1e293b")
        lbl_day.pack(anchor="w")

        def set_selected_day(new_day):
            var_day.set(new_day)
            lbl_day.config(text=f"zum {new_day}. des Monats")

        btn_pick_day = ttk.Button(day_f, text="📅 Tag im Monat wählen", command=lambda: self.open_day_picker_dialog(dlg, var_day.get(), set_selected_day))
        btn_pick_day.pack(anchor="w", pady=(4, 0))

        # Abbuchung bis einschließlich (Befristung)
        has_end_init = (end_year > 0 and end_month > 0)
        var_has_end = tk.BooleanVar(value=has_end_init)
        chk_has_end = ttk.Checkbutton(f, text="Abbuchung bis einschließlich", variable=var_has_end)
        chk_has_end.grid(row=4, column=0, columnspan=2, sticky="w", pady=(10, 2))

        end_frame = ttk.Frame(f)
        end_frame.grid(row=5, column=0, columnspan=2, sticky="w", padx=20, pady=(0, 6))

        ttk.Label(end_frame, text="Endet im:").pack(side="left", padx=(0, 6))
        cmb_end_m = ttk.Combobox(end_frame, values=GERMAN_MONTHS, width=12, state="readonly")
        init_em = end_month if (1 <= end_month <= 12) else self.current_month
        cmb_end_m.set(GERMAN_MONTHS[init_em - 1])
        cmb_end_m.pack(side="left", padx=(0, 6))

        spn_end_y = ttk.Spinbox(end_frame, from_=2020, to=2040, width=8)
        init_ey = end_year if end_year >= 2020 else self.current_year
        spn_end_y.set(init_ey)
        spn_end_y.pack(side="left")

        def toggle_end_state():
            if var_has_end.get():
                cmb_end_m.config(state="readonly")
                spn_end_y.config(state="normal")
            else:
                cmb_end_m.config(state="disabled")
                spn_end_y.config(state="disabled")

        chk_has_end.config(command=toggle_end_state)
        toggle_end_state()

        var_active = tk.BooleanVar(value=active)
        chk_active = ttk.Checkbutton(f, text="Aktiv für Berechnung", variable=var_active)
        chk_active.grid(row=6, column=0, columnspan=2, sticky="w", pady=8)

        def save():
            t = ent_titel.get().strip()
            if not t:
                messagebox.showerror("Fehler", "Bitte eine Bezeichnung eingeben.")
                return
            try:
                a = float(ent_amount.get().replace(",", "."))
            except ValueError:
                messagebox.showerror("Fehler", "Gültigen Betrag eingeben.")
                return
            c = cmb_cat.get().strip()

            if var_has_end.get():
                try:
                    em_idx = GERMAN_MONTHS.index(cmb_end_m.get().strip()) + 1
                except ValueError:
                    em_idx = 12
                try:
                    ey_val = int(spn_end_y.get())
                except ValueError:
                    ey_val = self.current_year
            else:
                em_idx = 0
                ey_val = 0

            self.db.save_fixed_cost(item_id, t, a, c, 1 if var_active.get() else 0, var_day.get(), em_idx, ey_val)
            self.refresh_all_views()
            dlg.destroy()

        btn_box = ttk.Frame(f)
        btn_box.grid(row=7, column=0, columnspan=2, sticky="e", pady=12)
        ttk.Button(btn_box, text="Abbrechen", command=dlg.destroy).pack(side="right", padx=4)
        ttk.Button(btn_box, text="Speichern", style="Primary.TButton", command=save).pack(side="right", padx=4)

        self.auto_fit_dialog(dlg, min_w=460, min_h=420)

    # Recurring Expenses
    def add_recurring(self):
        self.show_recurring_dialog()

    def edit_recurring(self, event=None):
        target_item = None
        if event:
            row_id = self.tree_recurring.identify_row(event.y) or self.tree_recurring.identify("item", event.x, event.y)
            if row_id:
                self.tree_recurring.selection_set(row_id)
                target_item = row_id
        if not target_item:
            sel = self.tree_recurring.selection()
            if sel:
                target_item = sel[0]
        if not target_item:
            messagebox.showwarning("Hinweis", "Bitte wählen Sie einen Eintrag aus.")
            return
        vals = self.tree_recurring.item(target_item).get("values", [])
        if not vals:
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        try:
            item_id = int(vals[0])
        except (IndexError, ValueError, TypeError):
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        rec = self.db.get_recurring_expense_by_id(item_id)
        if rec:
            freq = rec[4]
            m_spec = rec[5]
            active = bool(rec[6])
            s_m = rec[7] if len(rec) > 7 else 1
            s_y = rec[8] if len(rec) > 8 else 2026
            e_m = rec[9] if len(rec) > 9 else 0
            e_y = rec[10] if len(rec) > 10 else 0
            note = rec[11] if len(rec) > 11 else ""
            due_m = rec[12] if len(rec) > 12 else ""

            self.show_recurring_dialog(
                item_id=rec[0],
                title=rec[1],
                amount=rec[2],
                cat=rec[3],
                freq=freq,
                month_specified=m_spec,
                active=active,
                start_m=s_m,
                start_y=s_y,
                end_m=e_m,
                end_y=e_y,
                note=note,
                due_months=due_m
            )

    def delete_recurring(self):
        sel = self.tree_recurring.selection()
        if sel and messagebox.askyesno("Löschen", "Eintrag wirklich löschen?"):
            item_id = self.tree_recurring.item(sel[0])["values"][0]
            self.db.delete_recurring_expense(item_id)
            self.refresh_all_views()

    def show_recurring_dialog(self, item_id=None, title="", amount=0.0, cat="", freq="MONTHLY", month_specified=0, active=True, start_m=1, start_y=2026, end_m=0, end_y=0, note="", due_months=""):
        dlg = tk.Toplevel(self)
        dlg.title("Wiederkehrende Ausgabe " + ("bearbeiten" if item_id else "hinzufügen"))
        dlg.resizable(False, False)
        dlg.transient(self)
        dlg.grab_set()

        f = ttk.Frame(dlg, padding=16)
        f.pack(fill="both", expand=True)

        row_idx = 0

        ttk.Label(f, text="Bezeichnung:").grid(row=row_idx, column=0, sticky="w", pady=6)
        ent_titel = ttk.Entry(f, width=32)
        ent_titel.insert(0, title)
        ent_titel.grid(row=row_idx, column=1, columnspan=2, pady=6, sticky="w")
        row_idx += 1

        ttk.Label(f, text="Kategorie:").grid(row=row_idx, column=0, sticky="nw", pady=6)
        cat_f = ttk.Frame(f)
        cat_f.grid(row=row_idx, column=1, columnspan=2, pady=6, sticky="w")
        cmb_cat = ttk.Combobox(cat_f, values=self.get_cat_names(), width=30)
        if cat: cmb_cat.set(cat)
        elif self.get_cat_names(): cmb_cat.set(self.get_cat_names()[0])
        cmb_cat.pack(anchor="w")
        btn_new_cat = ttk.Button(cat_f, text="➕ Neue Kategorie erstellen", command=lambda: self.quick_create_category_dialog(dlg, cmb_cat, "Wiederkehrend"))
        btn_new_cat.pack(anchor="w", pady=(4, 0))
        row_idx += 1

        ttk.Label(f, text="Intervall / Rhythmus:").grid(row=row_idx, column=0, sticky="w", pady=6)
        cmb_freq = ttk.Combobox(f, values=FREQ_DISPLAY, width=30, state="readonly")
        curr_freq_disp = CODE_TO_FREQ.get(freq, "Monatlich")
        cmb_freq.set(curr_freq_disp)
        cmb_freq.grid(row=row_idx, column=1, columnspan=2, pady=6, sticky="w")
        row_idx += 1

        lbl_amount = ttk.Label(f, text="Betrag (€):")
        lbl_amount.grid(row=row_idx, column=0, sticky="w", pady=6)
        ent_amount = ttk.Entry(f, width=32)
        ent_amount.grid(row=row_idx, column=1, columnspan=2, pady=6, sticky="w")
        row_idx += 1

        # Frame for Gesplittet / Spezifische Monate
        split_frame = ttk.LabelFrame(f, text="Fälligkeitsmonate (Gesplittet)", padding=8)
        month_vars = [tk.BooleanVar() for _ in range(12)]
        month_abbrs = ["Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"]

        if due_months:
            d_m_list = [int(x) for x in str(due_months).split(",") if x.strip().isdigit()]
            for m_i in d_m_list:
                if 1 <= m_i <= 12:
                    month_vars[m_i - 1].set(True)

        def update_split_calc(*args):
            try:
                val_str = ent_amount.get().strip().replace(",", ".")
                tot_val = float(val_str) if val_str else 0.0
            except ValueError:
                tot_val = 0.0

            checked_count = sum(1 for v in month_vars if v.get())
            if checked_count > 0:
                per_due = tot_val / checked_count
                formatted = f"{per_due:,.2f} €".replace(",", "X").replace(".", ",").replace("X", ".")
                lbl_per_due.config(text=f"Betrag pro Fälligkeit: {formatted}  ({checked_count} Fälligkeit{'en' if checked_count > 1 else ''} / Jahr)")
            else:
                lbl_per_due.config(text="Betrag pro Fälligkeit: 0,00 € (Bitte Fälligkeitsmonate wählen)")

        for idx in range(12):
            r_num = idx // 4
            c_num = idx % 4
            chk = ttk.Checkbutton(
                split_frame,
                text=month_abbrs[idx],
                variable=month_vars[idx],
                command=update_split_calc
            )
            chk.grid(row=r_num, column=c_num, sticky="w", padx=6, pady=3)

        lbl_per_due = ttk.Label(split_frame, text="Betrag pro Fälligkeit: 0,00 €", font=("Helvetica", 9, "bold"), foreground="#1e88e5")
        lbl_per_due.grid(row=3, column=0, columnspan=4, sticky="w", padx=4, pady=(8, 2))

        split_row_idx = row_idx
        row_idx += 1

        # Populate ent_amount based on mode
        f_code_init = FREQ_TO_CODE.get(freq, freq)
        if f_code_init == "SPLIT_MONTHS":
            checked_c = sum(1 for v in month_vars if v.get())
            init_tot = amount * (checked_c if checked_c > 0 else 1)
            if init_tot:
                ent_amount.insert(0, f"{init_tot:.2f}".replace(".", ","))
        else:
            if amount:
                ent_amount.insert(0, f"{amount:.2f}".replace(".", ","))

        def on_freq_changed(event=None):
            sel_freq = cmb_freq.get().strip()
            if sel_freq == "Gesplittet / Spezifische Monate":
                lbl_amount.config(text="Gesamtbetrag (€):")
                split_frame.grid(row=split_row_idx, column=0, columnspan=3, sticky="ew", pady=6)
                update_split_calc()
            else:
                lbl_amount.config(text="Betrag (€):")
                split_frame.grid_forget()

        cmb_freq.bind("<<ComboboxSelected>>", on_freq_changed)
        ent_amount.bind("<KeyRelease>", update_split_calc)

        # Initial visibility of split_frame
        on_freq_changed()

        # Start / Erstmalige Fälligkeit
        ttk.Label(f, text="Erstmalig ab (Start):").grid(row=row_idx, column=0, sticky="w", pady=6)
        start_frame = ttk.Frame(f)
        start_frame.grid(row=row_idx, column=1, columnspan=2, sticky="w", pady=6)

        cmb_start_m = ttk.Combobox(start_frame, values=GERMAN_MONTHS, width=12, state="readonly")
        init_sm = start_m if (1 <= start_m <= 12) else self.current_month
        cmb_start_m.set(GERMAN_MONTHS[init_sm - 1])
        cmb_start_m.pack(side="left", padx=(0, 6))

        spn_start_y = ttk.Spinbox(start_frame, from_=2020, to=2040, width=8)
        init_sy = start_y if start_y >= 2020 else self.current_year
        spn_start_y.set(init_sy)
        spn_start_y.pack(side="left")
        row_idx += 1

        # Enddatum festlegen
        has_end_init = (end_y > 0 and end_m > 0)
        var_has_end = tk.BooleanVar(value=has_end_init)
        chk_has_end = ttk.Checkbutton(f, text="Enddatum festlegen", variable=var_has_end)
        chk_has_end.grid(row=row_idx, column=0, columnspan=3, sticky="w", pady=(8, 2))
        row_idx += 1

        end_frame = ttk.Frame(f)
        end_frame.grid(row=row_idx, column=0, columnspan=3, sticky="w", padx=20, pady=(0, 6))

        ttk.Label(end_frame, text="Endet im:").pack(side="left", padx=(0, 6))
        cmb_end_m = ttk.Combobox(end_frame, values=GERMAN_MONTHS, width=12, state="readonly")
        init_em = end_m if (1 <= end_m <= 12) else self.current_month
        cmb_end_m.set(GERMAN_MONTHS[init_em - 1])
        cmb_end_m.pack(side="left", padx=(0, 6))

        spn_end_y = ttk.Spinbox(end_frame, from_=2020, to=2040, width=8)
        init_ey = end_y if end_y >= 2020 else self.current_year
        spn_end_y.set(init_ey)
        spn_end_y.pack(side="left")
        row_idx += 1

        def toggle_end_state():
            if var_has_end.get():
                cmb_end_m.config(state="readonly")
                spn_end_y.config(state="normal")
            else:
                cmb_end_m.config(state="disabled")
                spn_end_y.config(state="disabled")

        chk_has_end.config(command=toggle_end_state)
        toggle_end_state()

        ttk.Label(f, text="Notiz / Bemerkung:").grid(row=row_idx, column=0, sticky="w", pady=6)
        ent_note = ttk.Entry(f, width=32)
        ent_note.insert(0, note)
        ent_note.grid(row=row_idx, column=1, columnspan=2, pady=6, sticky="w")
        row_idx += 1

        var_active = tk.BooleanVar(value=active)
        chk_active = ttk.Checkbutton(f, text="Aktiv für Berechnung", variable=var_active)
        chk_active.grid(row=row_idx, column=0, columnspan=3, sticky="w", pady=10)
        row_idx += 1

        def save():
            t = ent_titel.get().strip()
            if not t:
                messagebox.showerror("Fehler", "Bitte eine Bezeichnung eingeben.")
                return
            try:
                raw_amt = float(ent_amount.get().replace(",", "."))
            except ValueError:
                messagebox.showerror("Fehler", "Gültigen Betrag eingeben.")
                return

            c = cmb_cat.get().strip()
            freq_disp = cmb_freq.get().strip()
            fr_code = FREQ_TO_CODE.get(freq_disp, "MONTHLY")

            due_months_str = ""
            final_amount = raw_amt

            if fr_code == "SPLIT_MONTHS":
                checked_indices = [i + 1 for i, v in enumerate(month_vars) if v.get()]
                if not checked_indices:
                    messagebox.showerror("Fehler", "Bitte mindestens einen Fälligkeitsmonat auswählen.")
                    return
                due_months_str = ",".join(map(str, checked_indices))
                final_amount = raw_amt / len(checked_indices)

            try:
                sm_idx = GERMAN_MONTHS.index(cmb_start_m.get().strip()) + 1
            except ValueError:
                sm_idx = 1
            try:
                sy_val = int(spn_start_y.get())
            except ValueError:
                sy_val = self.current_year

            if var_has_end.get():
                try:
                    em_idx = GERMAN_MONTHS.index(cmb_end_m.get().strip()) + 1
                except ValueError:
                    em_idx = 12
                try:
                    ey_val = int(spn_end_y.get())
                except ValueError:
                    ey_val = self.current_year
            else:
                em_idx = 0
                ey_val = 0

            nt = ent_note.get().strip()

            self.db.save_recurring_expense(
                item_id, t, final_amount, c, fr_code, 0,
                1 if var_active.get() else 0,
                start_month=sm_idx,
                start_year=sy_val,
                end_month=em_idx,
                end_year=ey_val,
                note=nt,
                due_months=due_months_str
            )
            self.refresh_all_views()
            dlg.destroy()

        btn_box = ttk.Frame(f)
        btn_box.grid(row=row_idx, column=0, columnspan=3, sticky="e", pady=12)

        ttk.Button(btn_box, text="Abbrechen", command=dlg.destroy).pack(side="right", padx=4)
        ttk.Button(btn_box, text="Speichern", style="Primary.TButton", command=save).pack(side="right", padx=4)

        self.auto_fit_dialog(dlg, min_w=540, min_h=560)

    # Invoices
    def add_invoice(self):
        self.show_invoice_dialog()

    def edit_invoice(self, event=None):
        target_item = None
        if event:
            row_id = self.tree_invoices.identify_row(event.y) or self.tree_invoices.identify("item", event.x, event.y)
            if row_id:
                self.tree_invoices.selection_set(row_id)
                target_item = row_id
        if not target_item:
            sel = self.tree_invoices.selection()
            if sel:
                target_item = sel[0]
        if not target_item:
            messagebox.showwarning("Hinweis", "Bitte wählen Sie einen Eintrag aus.")
            return
        vals = self.tree_invoices.item(target_item).get("values", [])
        if not vals:
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        try:
            item_id = int(vals[0])
        except (IndexError, ValueError, TypeError):
            messagebox.showwarning("Hinweis", "Ungültige Auswahl.")
            return
        rec = self.db.get_invoice_by_id(item_id)
        if rec:
            self.show_invoice_dialog(item_id=rec[0], vendor=rec[1], desc=rec[2], amount=rec[3], due=rec[4], cat=rec[5], paid=bool(rec[6]))

    def delete_invoice(self):
        sel = self.tree_invoices.selection()
        if sel and messagebox.askyesno("Löschen", "Eintrag wirklich löschen?"):
            item_id = self.tree_invoices.item(sel[0])["values"][0]
            self.db.delete_invoice(item_id)
            self.refresh_all_views()

    def show_invoice_dialog(self, item_id=None, vendor="", desc="", amount=0.0, due="", cat="", paid=False):
        dlg = tk.Toplevel(self)
        dlg.title("Rechnung " + ("bearbeiten" if item_id else "hinzufügen"))
        dlg.transient(self)
        dlg.grab_set()

        f = ttk.Frame(dlg, padding=16)
        f.pack(fill="both", expand=True)

        ttk.Label(f, text="Rechnungssteller:").grid(row=0, column=0, sticky="w", pady=4)
        ent_vendor = ttk.Entry(f, width=25)
        ent_vendor.insert(0, vendor)
        ent_vendor.grid(row=0, column=1, pady=4)

        ttk.Label(f, text="Beschreibung:").grid(row=1, column=0, sticky="w", pady=4)
        ent_desc = ttk.Entry(f, width=25)
        ent_desc.insert(0, desc)
        ent_desc.grid(row=1, column=1, pady=4)

        ttk.Label(f, text="Betrag (€):").grid(row=2, column=0, sticky="w", pady=4)
        ent_amount = ttk.Entry(f, width=25)
        if amount is not None and amount != "":
            try:
                ent_amount.insert(0, f"{float(amount):.2f}".replace(".", ","))
            except (ValueError, TypeError):
                ent_amount.insert(0, str(amount))
        ent_amount.grid(row=2, column=1, pady=4)

        ttk.Label(f, text="Fälligkeit (JJJJ-MM-TT):").grid(row=3, column=0, sticky="w", pady=4)
        ent_due = ttk.Entry(f, width=25)
        ent_due.insert(0, due if due else datetime.date.today().strftime("%Y-%m-%d"))
        ent_due.grid(row=3, column=1, pady=4)

        ttk.Label(f, text="Kategorie:").grid(row=4, column=0, sticky="nw", pady=4)
        cat_f = ttk.Frame(f)
        cat_f.grid(row=4, column=1, pady=4, sticky="w")
        cmb_cat = ttk.Combobox(cat_f, values=self.get_cat_names(), width=25)
        if cat: cmb_cat.set(cat)
        elif self.get_cat_names(): cmb_cat.set(self.get_cat_names()[0])
        cmb_cat.pack(anchor="w")
        btn_new_cat = ttk.Button(cat_f, text="➕ Neue Kategorie erstellen", command=lambda: self.quick_create_category_dialog(dlg, cmb_cat, "Rechnung"))
        btn_new_cat.pack(anchor="w", pady=(4, 0))

        var_paid = tk.BooleanVar(value=paid)
        chk_paid = ttk.Checkbutton(f, text="Bereits bezahlt", variable=var_paid)
        chk_paid.grid(row=5, column=1, sticky="w", pady=8)

        def save():
            v = ent_vendor.get().strip()
            d = ent_desc.get().strip()
            try:
                a = float(ent_amount.get().replace(",", "."))
            except ValueError:
                messagebox.showerror("Fehler", "Gültigen Betrag eingeben.")
                return
            du = ent_due.get().strip()
            c = cmb_cat.get().strip()
            self.db.save_invoice(item_id, v, d, a, du, c, 1 if var_paid.get() else 0, self.current_month, self.current_year)
            self.refresh_all_views()
            dlg.destroy()

        btn_box = ttk.Frame(f)
        btn_box.grid(row=6, column=0, columnspan=2, sticky="e", pady=12)
        ttk.Button(btn_box, text="Abbrechen", command=dlg.destroy).pack(side="right", padx=4)
        ttk.Button(btn_box, text="Speichern", style="Primary.TButton", command=save).pack(side="right", padx=4)

        self.auto_fit_dialog(dlg, min_w=420, min_h=340)


if __name__ == "__main__":
    app = HaushaltsbuchApp()
    app.mainloop()
