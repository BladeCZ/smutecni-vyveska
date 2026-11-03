"""
Smuteční vývěska - Multi-Source Web Scraper & Database Builder
Zdroje:
1. Horníček Moravičany (https://www.pshornicek.cz/c-m-s-page/smutecni-vyveska a /archiv.html)
2. Hrandop Litovel (https://www.hrandop.cz/smutecni-oznameni)
3. Drozd Zábřeh (https://www.pohrebnictvi-drozd.cz/vyveska.php)
"""

import os
import re
import json
import sqlite3
import csv
import shutil
import time
import hashlib
import urllib.parse
import subprocess
from datetime import datetime
import requests

try:
    import cv2
except ImportError:
    cv2 = None

SOURCE_HORNICEK = "Horníček Moravičany"
SOURCE_HRANDOP = "Hrandop Litovel"
SOURCE_DROZD = "Drozd Zábřeh"

BASE_URL_HORNICEK = "https://www.pshornicek.cz"
URL_HORNICEK_VYVESKA = f"{BASE_URL_HORNICEK}/c-m-s-page/smutecni-vyveska"
URL_HORNICEK_ARCHIV = f"{BASE_URL_HORNICEK}/archiv.html"

BASE_URL_HRANDOP = "https://www.hrandop.cz"
URL_HRANDOP_VYVESKA = f"{BASE_URL_HRANDOP}/smutecni-oznameni"

BASE_URL_DROZD = "https://www.pohrebnictvi-drozd.cz"
URL_DROZD_VYVESKA = f"{BASE_URL_DROZD}/vyveska.php"

RELEASE_TAG = "parte-images"
RELEASE_BASE_URL = f"https://github.com/BladeCZ/smutecni-vyveska/releases/download/{RELEASE_TAG}"

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DATA_DIR = os.path.join(ROOT_DIR, "data")
IMAGES_DIR = os.path.join(DATA_DIR, "images")
FACES_DIR = os.path.join(IMAGES_DIR, "faces")
WEB_DATA_DIR = os.path.join(ROOT_DIR, "web", "data")
WEB_IMAGES_DIR = os.path.join(ROOT_DIR, "web", "images")
WEB_FACES_DIR = os.path.join(WEB_IMAGES_DIR, "faces")
DB_JSON_PATH = os.path.join(DATA_DIR, "database.json")
DB_SQLITE_PATH = os.path.join(DATA_DIR, "database.sqlite")
DB_CSV_PATH = os.path.join(DATA_DIR, "database.csv")

LOCAL_TESSDATA = os.path.join(ROOT_DIR, "scraper", "tessdata")

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "cs,en;q=0.9"
}

# GPS souřadnice obcí v regionu (Mohelnicko, Lošticko, Litovelsko, Zábřežsko, Šumpersko, Olomoucko)
TOWN_COORDS = {
    "Loštice": (49.7458, 16.9278),
    "Mohelnice": (49.7769, 16.9194),
    "Moravičany": (49.7569, 16.9636),
    "Pavlov": (49.7408, 16.8803),
    "Úsov": (49.7986, 17.0108),
    "Dubicko": (49.8267, 16.9647),
    "Bohuslavice": (49.8208, 16.9400),
    "Lukavice": (49.8236, 16.9158),
    "Třeština": (49.7967, 16.9639),
    "Krchleby": (49.8117, 16.8322),
    "Podolí": (49.7664, 16.8844),
    "Pískov": (49.8058, 17.0794),
    "Veleboř": (49.8250, 17.0406),
    "Rohle": (49.8639, 17.0233),
    "Studená Loučka": (49.7611, 16.8269),
    "Mírov": (49.7928, 16.8508),
    "Palonín": (49.7367, 16.9739),
    "Zvole": (49.8414, 16.9169),
    "Stavenice": (49.7775, 16.9817),
    "Červená Lhota": (49.7136, 17.0156),
    "Bílá Lhota": (49.7103, 16.9761),
    "Slavoňov": (49.8017, 16.9061),
    "Řimice": (49.7186, 17.0144),
    "Klopina": (49.8225, 17.0308),
    "Police": (49.8147, 17.0014),
    "Medlov": (49.7844, 17.0644),
    "Mladeč": (49.7061, 17.0169),
    "Zábřeh": (49.8825, 16.8722),
    "Litovel": (49.7011, 17.0761),
    "Uničov": (49.7708, 17.1214),
    "Šumperk": (49.9653, 16.9706),
    "Olomouc": (49.5938, 17.2509),
    "Náklo": (49.6525, 17.1269),
    "Chromeč": (49.9547, 16.8744),
    "Rájec": (49.8586, 16.8894),
    "Postřelmov": (49.9086, 16.9069),
    "Bludov": (49.9436, 16.9286),
    "Ruda nad Moravou": (49.9806, 16.8769),
    "Sudkov": (49.9236, 16.9403),
    "Lesnice": (49.8906, 16.9394),
    "Leština": (49.8736, 16.9325),
    "Brníčko": (49.8986, 16.9728),
    "Kolšov": (49.9114, 16.9289),
    "Nemile": (49.8903, 16.8425),
    "Lupěné": (49.8639, 16.8403),
    "Hoštejn": (49.8781, 16.7747),
    "Drozdov": (49.9142, 16.7736),
    "Jedlí": (49.9403, 16.7575),
    "Štíty": (49.9636, 16.7658),
    "Hanušovice": (50.0725, 16.9472),
    "Červenka": (49.7192, 17.0900),
    "Senice na Hané": (49.6589, 17.0875),
    "Náměšť na Hané": (49.5981, 17.0628),
    "Cholina": (49.6625, 17.0497),
    "Bílsko": (49.6442, 17.0225),
    "Haňovice": (49.6808, 17.0581),
    "Nasobůrky": (49.6908, 17.0608),
    "Sobáčov": (49.7131, 17.0489),
    "Tři Dvory": (49.7086, 17.1053),
    "Pňovice": (49.7219, 17.1636),
    "Střeň": (49.6939, 17.1525),
    "Příkazy": (49.6358, 17.1642),
    "Horka nad Moravou": (49.6400, 17.2094),
    "Štěpánov": (49.6850, 17.2206),
    "Bouzov": (49.7044, 16.8906),
    "Hvozd": (49.6356, 16.9153),
    "Luká": (49.6503, 16.9481),
    "Konice": (49.5892, 16.8886),
    "Šternberk": (49.7306, 17.2989),
    "Moravská Třebová": (49.7578, 16.6642),
}

CZECH_MONTHS = {
    "ledna": 1, "leden": 1,
    "února": 2, "unora": 2, "únor": 2,
    "března": 3, "brezna": 3, "březen": 3,
    "dubna": 4, "duben": 4,
    "května": 5, "kvetna": 5, "květen": 5,
    "června": 6, "cervna": 6, "červen": 6,
    "července": 7, "cervence": 7, "červenec": 7,
    "srpna": 8, "srpen": 8,
    "září": 9, "zari": 9,
    "října": 10, "rijna": 10, "říjen": 10,
    "listopadu": 11, "listopad": 11,
    "prosince": 12, "prosinec": 12,
}

def clean_name_and_category(title):
    category = "Smuteční oznámení"
    name = (title or "").strip()
    
    if re.search(r'vzpom[ií]nka', name, re.IGNORECASE):
        category = "Vzpomínka"
        name = re.sub(r'^(?:vzpom[ií]nka\s*[-–:]?\s*)', '', name, flags=re.IGNORECASE).strip()
    elif re.search(r'smut(?:e[cč]n[ií]|\.?)\s*ozn[aá]men[ií]', name, re.IGNORECASE):
        category = "Smuteční oznámení"
        name = re.sub(r'^(?:smut(?:e[cč]n[ií]|\.?)\s*ozn[aá]men[ií]\s*[-–:]?\s*)', '', name, flags=re.IGNORECASE).strip()
    elif re.search(r'parte', name, re.IGNORECASE):
        category = "Smuteční oznámení"
        name = re.sub(r'^(?:parte\s*[-–:]?\s*)', '', name, flags=re.IGNORECASE).strip()

    name = name.strip("-–: \t\r\n\"'")
    return category, name

def normalize_person_name(raw_name):
    if not raw_name:
        return ""
    s = raw_name.strip(" |-\t\r\n\"'.,;:")
    s = re.sub(r'^Mor\.\s+', 'Mgr. ', s)
    s = re.sub(r'\s+', ' ', s)
    words = []
    for w in s.split(' '):
        if len(w) > 2 and w.isupper():
            words.append(w.capitalize())
        else:
            words.append(w)
    return " ".join(words)

def extract_name_from_ocr(text, fallback_name=""):
    """
    Vytáhne celé jméno a příjmení zesnulého z OCR textu parte (pro zdroje jako Drozd Zábřeh nebo Hrandop Litovel,
    kde v HTML chybí celé jméno).
    """
    if not text:
        return normalize_person_name(fallback_name)

    # 1. Hledat řádek bezprostředně po oslovení "pan / paní / pana / slečna"
    m_pan = re.search(
        r'\b(?:pan[ií]?|pana|slečna)\b[ \t,.:]*\n+\s*([^\n]+)',
        text,
        re.IGNORECASE
    )
    if m_pan:
        cand = normalize_person_name(m_pan.group(1))
        if len(cand.split()) >= 2 and not re.search(r'\b(?:zemřel|zesnul|odešel|rodina)\b', cand, re.IGNORECASE):
            return cand

    # 2. Hledat řádek po "opustil(a) (náš milovaný syn/bratr...)"
    m_opustil = re.search(
        r'\b(?:opustil[a]?)\b[^\n]*\n+\s*([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][^\n]+)',
        text,
        re.IGNORECASE
    )
    if m_opustil:
        cand = normalize_person_name(m_opustil.group(1))
        if len(cand.split()) >= 2 and len(cand) <= 45 and not re.search(r'\b(?:zemřel|zesnul|odešel|rodina|rozloučíme)\b', cand, re.IGNORECASE):
            return cand

    return normalize_person_name(fallback_name)

def clean_town(town_str):
    if not town_str:
        return ""
    t = re.sub(r'\s+č\.\s*(?:p\.\s*)?\d+.*$', '', town_str.strip(), flags=re.IGNORECASE)
    t = re.sub(r',.*$', '', t)
    t = re.sub(r'\s+\d+.*$', '', t)
    t = re.sub(r'^[\|\-\s_n1l]+', '', t).strip(" ,.;|-")
    mapping = {
        "Loštic": "Loštice",
        "Lošticích": "Loštice",
        "Pavlova": "Pavlov",
        "Moravičan": "Moravičany",
        "Moravičanech": "Moravičany",
        "Červené Lhoty": "Červená Lhota",
        "Stavenic": "Stavenice",
        "Mirova": "Mírov",
        "Mírova": "Mírov",
        "Lukavice": "Lukavice",
        "Lukavic": "Lukavice",
        "Třeštiny": "Třeština",
        "Třeština": "Třeština",
        "Palonína": "Palonín",
        "Mohelnice": "Mohelnice",
        "Mohelnici": "Mohelnice",
        "Zvole": "Zvole",
        "Zvoli": "Zvole",
        "Bohuslavic": "Bohuslavice",
        "Bohuslavice": "Bohuslavice",
        "Dubicka": "Dubicko",
        "Dubicko": "Dubicko",
        "Krchleb": "Krchleby",
        "Krchleby": "Krchleby",
        "Pískova": "Pískov",
        "Pískov": "Pískov",
        "Usova": "Úsov",
        "Úsova": "Úsov",
        "Úsov": "Úsov",
        "Veleboře": "Veleboř",
        "Veleboř": "Veleboř",
        "Studené Loučky": "Studená Loučka",
        "Studená Loučka": "Studená Loučka",
        "Rohle": "Rohle",
        "Podolí": "Podolí",
        "Zábřeha": "Zábřeh",
        "Zábřehu": "Zábřeh",
        "Zábřeze": "Zábřeh",
        "Zábřeh": "Zábřeh",
        "Litovle": "Litovel",
        "Litovli": "Litovel",
        "Litovlí": "Litovel",
        "Litovel": "Litovel",
        "Šumperka": "Šumperk",
        "Uničova": "Uničov",
        "Olomouce": "Olomouc",
        "Bílé Lhoty": "Bílá Lhota",
        "Slavoňova": "Slavoňov",
        "Řimic": "Řimice",
        "Klopiny": "Klopina",
        "Medlova": "Medlov",
        "Mladče": "Mladeč",
        "Nákla": "Náklo",
        "Nákle": "Náklo",
        "Náklo": "Náklo",
        "Chromče": "Chromeč",
        "Chromči": "Chromeč",
        "Chromeč": "Chromeč",
        "Rájce": "Rájec",
        "Rájci": "Rájec",
        "Rájec": "Rájec",
        "Postřelmova": "Postřelmov",
        "Bludova": "Bludov",
        "Sudkova": "Sudkov",
        "Lesnice": "Lesnice",
        "Leštiny": "Leština",
        "Brníčka": "Brníčko",
        "Kolšova": "Kolšov",
        "Nemile": "Nemile",
        "Hoštejna": "Hoštejn",
        "Drozdova": "Drozdov",
        "Jedlí": "Jedlí",
        "Štítů": "Štíty",
        "Červenky": "Červenka",
        "Choliny": "Cholina",
        "Bílska": "Bílsko",
        "Haňovic": "Haňovice",
        "Nasobůrek": "Nasobůrky",
        "Sobáčova": "Sobáčov",
        "Tří Dvorů": "Tři Dvory",
        "Pňovic": "Pňovice",
        "Střeně": "Střeň",
        "Příkaz": "Příkazy",
        "Štěpánova": "Štěpánov",
        "Bouzova": "Bouzov",
        "Hvozdu": "Hvozd",
        "Luké": "Luká",
    }
    return mapping.get(t, t)

def detect_gender(name, text=""):
    if " a " in (name or ""):
        return "MF"
    clean_n = re.sub(r'^(?:Ing\.|Mgr\.|MUDr\.|MVDr\.|JUDr\.|Bc\.|PhDr\.|RNDr\.|DiS\.)\s*', '', name or "", flags=re.IGNORECASE).strip()
    parts = clean_n.split()
    surname = parts[-1] if parts else ""
    first_name = parts[0] if parts else ""
    female_first = {
        "Marie", "Eliška", "Jana", "Marta", "Eva", "Bedřiška", "Helena", "Miloslava",
        "Anežka", "Růžena", "Alena", "Božena", "Hana", "Iveta", "Vlasta", "Věra",
        "Jiřina", "Jaroslava", "Zdeňka", "Ludmila", "Anna", "Květa", "Květoslava",
        "Zuzana", "Petra", "Lucie", "Pavlína", "Kateřina", "Lenka", "Dagmar", "Irena",
        "Jarmila", "Libuše", "Milada", "Bronislava"
    }
    if surname.endswith(("ová", "á")) or first_name in female_first:
        return "F"
    return "M"

def normalize_age(raw_age, raw_text=""):
    text_to_check = f"{raw_age or ''} {raw_text or ''}"
    m_pair = re.search(r'\b(\d{1,3}\s+a\s+\d{1,3})\s*let\b', text_to_check, re.IGNORECASE)
    if m_pair:
        return f"{m_pair.group(1)} let"
    m_num = re.search(r'(\d{1,3})\s*let\b', raw_age or "", re.IGNORECASE)
    if not m_num:
        m_num = re.search(r'(?:ve\s+v[eě]ku|do[zž]il(?:a)?\s+se)\s+(?:nedo[zž]it[yý]ch\s+)?(\d{1,3})\s*let', raw_text or "", re.IGNORECASE)
    if m_num:
        return f"{m_num.group(1)} let"
    if "vesěkuZulet" in (raw_text or ""):
        return "71 let"
    return (raw_age or "").strip()

def normalize_czech_date(text, fallback_date_str=""):
    """
    Najde datum úmrtí v textu parte a vrátí (display_date 'DD. MM. RRRR', iso_date 'RRRR-MM-DD').
    Hledá před pasáží o pohřbu ('rozloučíme', 'Zádušní mše'), aby nedošlo k záměně s datem pohřbu.
    Podporuje jak 'dne 29. října 2026', tak 've čtvrtek 29. října 2026' (Hrandop Litovel).
    """
    search_space = text or ""
    split_m = re.split(r'(?:rozlou[cč][ií]me|Z[aá]du[sš]n[ií]\s+m[sš]e|Datum\s+a\s+m[ií]sto\s+posledn[ií]ho)', search_space, maxsplit=1, flags=re.IGNORECASE)
    before_funeral = split_m[0] if split_m else search_space

    months_pattern = "|".join(CZECH_MONTHS.keys())
    for candidate in [before_funeral, fallback_date_str, search_space]:
        if not candidate:
            continue
        m = re.search(
            rf'(?:dne\s+|(?:pondělí|úterý|středu|čtvrtek|pátek|sobotu|neděli)\s+)?([0-9Il]{{1,2}})\.\s*({months_pattern})\b(?:\s+(\d{{4}}))?',
            candidate,
            re.IGNORECASE
        )
        if m:
            raw_day = m.group(1).replace('I', '1').replace('l', '1')
            if not raw_day.isdigit():
                continue
            day = int(raw_day)
            month_word = m.group(2).lower()
            month = CZECH_MONTHS.get(month_word)
            year = int(m.group(3)) if m.group(3) else datetime.now().year
            if month and 1 <= day <= 31:
                display = f"{day:02d}. {month:02d}. {year}"
                iso = f"{year:04d}-{month:02d}-{day:02d}"
                return display, iso

    return "", ""

def extract_face_portrait(image_path, item_id, force=False):
    """
    Detekuje obličej na fotografii v horní části parte a uloží čistý portrétní výřez.
    Pokud na parte fotografie obličeje není, vrátí prázdný řetězec.
    """
    if cv2 is None or not os.path.exists(image_path):
        return ""

    os.makedirs(FACES_DIR, exist_ok=True)
    os.makedirs(WEB_FACES_DIR, exist_ok=True)
    face_filename = f"{item_id}.jpg"
    target_face_path = os.path.join(FACES_DIR, face_filename)
    web_face_path = os.path.join(WEB_FACES_DIR, face_filename)
    rel_face_path = f"images/faces/{face_filename}"

    if not force and os.path.exists(target_face_path) and os.path.getsize(target_face_path) > 0:
        if not os.path.exists(web_face_path):
            shutil.copy2(target_face_path, web_face_path)
        return rel_face_path

    img = cv2.imread(image_path)
    if img is None:
        return ""

    h, w = img.shape[:2]
    # Portrétní fotografie na parte (horní polovina y: 1 % až 48 %, x: 18 % až 96 %)
    y1, y2 = int(h * 0.01), int(h * 0.48)
    x1, x2 = int(w * 0.18), int(w * 0.96)
    roi = img[y1:y2, x1:x2]
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)

    cascade_alt2 = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_alt2.xml')
    cascade_default = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

    raw_candidates = []
    for sf in (1.05, 1.06, 1.08):
        f1 = cascade_alt2.detectMultiScale(
            gray, scaleFactor=sf, minNeighbors=4,
            minSize=(int(w * 0.055), int(w * 0.055)),
            maxSize=(int(w * 0.45), int(w * 0.45))
        )
        if len(f1) > 0:
            raw_candidates.extend(list(f1))
            break
    if not raw_candidates:
        for sf in (1.05, 1.06, 1.08):
            f2 = cascade_default.detectMultiScale(
                gray, scaleFactor=sf, minNeighbors=6,
                minSize=(int(w * 0.055), int(w * 0.055)),
                maxSize=(int(w * 0.45), int(w * 0.45))
            )
            if len(f2) > 0:
                raw_candidates.extend(list(f2))
                break

    # Odfiltrovat kreslené ornamenty (růže, křížky na bílém pozadí), kde většinu plochy tvoří bílý papír
    faces = []
    for (fx, fy, fw, fh) in raw_candidates:
        box_gray = gray[fy:fy+fh, fx:fx+fw]
        if box_gray.size == 0:
            continue
        white_ratio = float((box_gray > 235).mean())
        midtone_ratio = float(((box_gray >= 40) & (box_gray <= 225)).mean())
        if white_ratio < 0.42 and midtone_ratio > 0.45:
            faces.append((fx, fy, fw, fh))

    if not faces:
        for p in (target_face_path, web_face_path):
            if os.path.exists(p):
                try:
                    os.remove(p)
                except Exception:
                    pass
        return ""

    fx, fy, fw, fh = max(faces, key=lambda b: b[2] * b[3])
    abs_x = x1 + fx
    abs_y = y1 + fy

    pad_x = int(fw * 0.32)
    pad_top = int(fh * 0.38)
    pad_bot = int(fh * 0.42)

    cx1 = max(0, abs_x - pad_x)
    cy1 = max(0, abs_y - pad_top)
    cx2 = min(w, abs_x + fw + pad_x)
    cy2 = min(h, abs_y + fh + pad_bot)

    crop = img[cy1:cy2, cx1:cx2]
    if crop.size == 0:
        return ""

    resized = cv2.resize(crop, (160, 160), interpolation=cv2.INTER_AREA)
    cv2.imwrite(target_face_path, resized, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
    shutil.copy2(target_face_path, web_face_path)
    return rel_face_path

def find_tesseract():
    common_paths = [
        "tesseract",
        r"C:\Program Files\Tesseract-OCR\tesseract.exe",
        r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
        "/usr/bin/tesseract"
    ]
    for p in common_paths:
        try:
            res = subprocess.run([p, "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if res.returncode == 0:
                return p
        except Exception:
            pass
    return None

def run_ocr(image_path):
    tess_exe = find_tesseract()
    if not tess_exe:
        return ""
    
    cmd = [tess_exe, image_path, "stdout", "-l", "ces"]
    if os.path.exists(LOCAL_TESSDATA):
        cmd.extend(["--tessdata-dir", LOCAL_TESSDATA])
        
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding="utf-8", errors="ignore")
        return res.stdout
    except Exception as e:
        print(f"  Chyba při spuštění OCR na {image_path}: {e}")
        return ""

def parse_parte_text(text, category="", name=""):
    extracted_name = extract_name_from_ocr(text, name)
    final_name = extracted_name if (not name or len(name.split()) < 2) else normalize_person_name(name)

    info = {
        "name": final_name,
        "residence": "",
        "age": "",
        "death_date": "",
        "death_date_iso": "",
        "death_cause": "",
        "ceremony": "",
        "gender": detect_gender(final_name, text),
        "raw_text": (text or "").strip()
    }

    if not text:
        return info

    # 1. Bydliště: hledáme v pasáži po slově pan/paní/opustil (abychom nebrali citáty v záhlaví)
    after_pan_split = re.split(r'\b(?:pan[ií]?|pana|slečna|opustil[a]?)\b', text, maxsplit=1, flags=re.IGNORECASE)
    residence_zone = after_pan_split[1] if len(after_pan_split) > 1 else text
    before_zemrel_split = re.split(r'\b(?:Zemřel|Zemřela|Zesnul|Zesnula|Odešel|Odešla)\b', residence_zone, maxsplit=1, flags=re.IGNORECASE)
    residence_zone = before_zemrel_split[0] if before_zemrel_split else residence_zone

    # Hledání 'bytem Zábřeh, ...' nebo 'bytem Rájec 14' (Drozd Zábřeh)
    bytem_m = re.search(r'\bbytem[ \t]+([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+(?:[ \t]+[A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+)?)', residence_zone, re.IGNORECASE)
    if bytem_m:
        info["residence"] = clean_town(bytem_m.group(1))

    # Hledání 'z Mohelnice', 'z Nákla', 'ze Studené Loučky' atd.
    if not info["residence"]:
        res_m = re.search(r'\bze?[ \t]+([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+(?:[ \t]+[A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+)?)(?=[ \t]*\n|[ \t]*$)', residence_zone)
        if res_m:
            raw_res = res_m.group(1).strip()
            if not re.search(r'\b(?:dne|ve|v|pan|paní|srdci|bolestí|Lisieux)\b', raw_res, re.IGNORECASE):
                info["residence"] = clean_town(raw_res)

    # Pokud je na řádku tvar 'Třeština č. 1', 'Rohle č. 34', 'Podolí č. 65', 'Bohuslavice č. 35'
    if not info["residence"]:
        addr_m = re.search(r'([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+(?:[ \t]+[A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+)?)[ \t]+č\.[ \t]*(?:p\.[ \t]*)?\d+', residence_zone)
        if addr_m:
            info["residence"] = clean_town(addr_m.group(1))

    # Hledání 'spjat s Litovlí' (Hrandop Litovel)
    if not info["residence"]:
        spjat_m = re.search(r'spjat\s+s\s+([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+)', text)
        if spjat_m:
            info["residence"] = clean_town(spjat_m.group(1))

    # Samostatný řádek s názvem obce pod jménem (např. 'Chromeč' u Drozd Zábřeh)
    if not info["residence"]:
        for line in [l.strip(" |-\t\r\n.,") for l in residence_zone.splitlines() if l.strip()]:
            cleaned_line = clean_town(line)
            if cleaned_line in TOWN_COORDS:
                info["residence"] = cleaned_line
                break

    # 2. Věk (sjednocený tvar "XX let")
    raw_age = ""
    age_m = re.search(r'(?:ve\s+v[eě]ku|do[zž]il(?:a)?\s+se)\s+([^.\n]+?let)', text, re.IGNORECASE)
    if age_m:
        raw_age = age_m.group(1).strip()
    info["age"] = normalize_age(raw_age, text)

    # 3. Datum úmrtí (vždy jednotný formát DD. MM. RRRR + ISO RRRR-MM-DD)
    disp_date, iso_date = normalize_czech_date(text)
    info["death_date"] = disp_date
    info["death_date_iso"] = iso_date

    # 4. Důvod / způsob úmrtí
    cause_m = re.search(r'(?:Zemřel[a]?|Zesnul[a]?|Odešel|Odešla)\s+((?:náhle|tiše|nečekaně|neočekávaně|tragicky|po\s+[^,\n]+?nemoci)[^.\n]*)', text, re.IGNORECASE)
    if cause_m:
        cause = cause_m.group(1).strip()
        cause = re.sub(r'\s+(?:v|ve)\s+(?:pondělí|úterý|středu|čtvrtek|pátek|sobotu|neděli)\b.*$', '', cause, flags=re.IGNORECASE)
        cause = re.sub(r'\s+dne\s+\d+.*$', '', cause, flags=re.IGNORECASE)
        cause = re.sub(r'[,;]+$', '', cause).strip()
        if len(cause) > 2 and not cause.lower().startswith("dne"):
            info["death_cause"] = cause
    elif re.search(r'\btragicky\s+opustil[a]?\b', text, re.IGNORECASE):
        info["death_cause"] = "tragicky"
    elif re.search(r'\bpo\s+delší\s+nemoci\b', text, re.IGNORECASE):
        info["death_cause"] = "po delší nemoci"
    elif re.search(r'\bpo\s+krátké\s+nemoci\b', text, re.IGNORECASE):
        info["death_cause"] = "po krátké nemoci"

    # 5. Poslední rozloučení / obřad
    if category == "Vzpomínka":
        info["ceremony"] = "Vzpomínka na zesnulého"
    elif re.search(r'bez\s+smutečního\s+obřadu', text, re.IGNORECASE):
        info["ceremony"] = "Bez smutečního obřadu (na přání zesnulého)"
    elif re.search(r'(?:uctíte\s+tichou\s+vzpomínkou|tělesné\s+pozůstatky\s+budou\s+zpopelněny)', text, re.IGNORECASE) and not re.search(r'rozloučíme', text, re.IGNORECASE):
        info["ceremony"] = "Tiché rozloučení bez smutečního obřadu"
    else:
        cer_match = re.search(r'rozloučíme\s+([\s\S]+?)(?=\.?\s*(?:Po\s+smutečn|Po\s+církevn|Jménem|Děkujeme|Za\s+milující|\n\n|$))', text, re.IGNORECASE)
        if cer_match:
            clean_cer = " ".join([l.strip("| \"'n1l") for l in cer_match.group(1).splitlines() if l.strip()])
            clean_cer = re.sub(r'\s+', ' ', clean_cer).strip(" .")
            info["ceremony"] = clean_cer
        else:
            upres_m = re.search(r'(Datum\s+a\s+místo\s+posledního\s+rozloučení[\s\S]+?)(?=\n\n|ZARMOUCENÁ|$)', text, re.IGNORECASE)
            if upres_m:
                info["ceremony"] = " ".join([l.strip() for l in upres_m.group(1).splitlines() if l.strip()])

    return info

def fetch_html(url):
    print(f"Stahuji HTML: {url}")
    last_err = None
    # 1. Pokus přes requests
    for attempt in range(1, 3):
        try:
            resp = requests.get(url, headers=HEADERS, timeout=15)
            if resp.status_code == 200 and len(resp.text) > 500:
                resp.encoding = 'utf-8'
                return resp.text
            last_err = RuntimeError(f"HTTP {resp.status_code}")
        except Exception as e:
            last_err = e
            time.sleep(1)

    # 2. Pokus přes systémový curl (prochází přes Wedos TLS filtr u hrandop.cz)
    for curl_bin in ("curl.exe", "curl"):
        try:
            res = subprocess.run(
                [curl_bin, "-sL", "--max-time", "20", "-A", HEADERS["User-Agent"], url],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding="utf-8", errors="ignore"
            )
            if res.returncode == 0 and len(res.stdout) > 500 and "wedos.status.online" not in res.stdout:
                return res.stdout
        except Exception:
            pass

    # 3. Fallback přes záložní proxy
    proxy_url = f"https://api.allorigins.win/raw?url={urllib.parse.quote(url, safe='')}"
    try:
        print(f"  Zkouším záložní proxy pro stažení HTML...")
        resp = requests.get(proxy_url, headers=HEADERS, timeout=20)
        if resp.status_code == 200 and len(resp.text) > 500:
            resp.encoding = 'utf-8'
            return resp.text
    except Exception as e:
        print(f"  Záložní proxy selhala: {e}")

    raise last_err or RuntimeError(f"Nepodařilo se stáhnout {url}")

def parse_hornicek_html(html, source_type):
    pattern = r'<a[^>]+href=[\'"]([^\'"]+)[\'"][^>]*title=[\'"]([^\'"]+)[\'"]'
    items = []
    for match in re.finditer(pattern, html, re.IGNORECASE):
        raw_href = match.group(1).strip()
        title = match.group(2).strip()
        if not re.search(r'\.(?:jpg|jpeg|png|webp)', raw_href, re.IGNORECASE):
            continue
        clean_rel = raw_href.replace('../..//', '').replace('../../', '').replace('..//', '').lstrip('/')
        full_image_url = f"{BASE_URL_HORNICEK}/{clean_rel}"
        filename = clean_rel.split('/')[-1]
        item_id = filename.split('.')[0]
        category, name = clean_name_and_category(title)
        items.append({
            "id": item_id,
            "filename": filename,
            "original_title": title,
            "category": category,
            "name": name,
            "image_url": full_image_url,
            "source": source_type,
            "source_name": SOURCE_HORNICEK
        })
    return items

def parse_hrandop_html(html):
    pattern = r'<a[^>]+href=[\'"]([^\'"]*ModuleSmutecniOznameni[^\'"]+)[\'"][^>]*title=[\'"]([^\'"]*)[\'"]'
    items = []
    for match in re.finditer(pattern, html, re.IGNORECASE):
        raw_href = match.group(1).strip().lstrip('/')
        title = match.group(2).strip()
        if not re.search(r'\.(?:jpg|jpeg|png|webp)$', raw_href, re.IGNORECASE):
            continue
        full_image_url = f"{BASE_URL_HRANDOP}/{raw_href}"
        item_id = "hrandop_" + hashlib.md5(raw_href.encode("utf-8")).hexdigest()[:24]
        filename = f"{item_id}.jpg"
        category, name = clean_name_and_category(title)
        items.append({
            "id": item_id,
            "filename": filename,
            "original_title": title or "Smuteční oznámení",
            "category": category,
            "name": name,
            "image_url": full_image_url,
            "source": "vyveska",
            "source_name": SOURCE_HRANDOP
        })
    return items

def parse_drozd_html(html):
    pattern = r'url\([\'"]?(parte/[^\'")]+)[\'"]?\)'
    items = []
    for match in re.finditer(pattern, html, re.IGNORECASE):
        raw_rel = match.group(1).strip().lstrip('/')
        if not re.search(r'\.(?:jpg|jpeg|png|webp)$', raw_rel, re.IGNORECASE):
            continue
        full_image_url = f"{BASE_URL_DROZD}/{raw_rel}"
        item_id = "drozd_" + hashlib.md5(raw_rel.encode("utf-8")).hexdigest()[:26]
        filename = f"{item_id}.jpg"
        items.append({
            "id": item_id,
            "filename": filename,
            "original_title": raw_rel.split('/')[-1],
            "category": "Smuteční oznámení",
            "name": "",
            "image_url": full_image_url,
            "source": "vyveska",
            "source_name": SOURCE_DROZD
        })
    return items

def download_image(url, target_path):
    os.makedirs(WEB_IMAGES_DIR, exist_ok=True)
    web_target = os.path.join(WEB_IMAGES_DIR, os.path.basename(target_path))
    if os.path.exists(target_path) and os.path.getsize(target_path) > 0:
        if not os.path.exists(web_target):
            shutil.copy2(target_path, web_target)
        return False
    print(f"  Stahuji nový obrázek: {os.path.basename(target_path)}")
    # 1. Pokus přes requests
    for attempt_url in [url, f"https://api.allorigins.win/raw?url={urllib.parse.quote(url, safe='')}"]:
        try:
            resp = requests.get(attempt_url, headers=HEADERS, timeout=25)
            if resp.status_code == 200 and len(resp.content) > 1000 and b"<!DOCTYPE html" not in resp.content[:200]:
                with open(target_path, 'wb') as f:
                    f.write(resp.content)
                shutil.copy2(target_path, web_target)
                return True
        except Exception:
            pass

    # 2. Pokus přes curl (pro hrandop.cz apod.)
    for curl_bin in ("curl.exe", "curl"):
        try:
            res = subprocess.run(
                [curl_bin, "-sL", "--max-time", "25", "-A", HEADERS["User-Agent"], "-o", target_path, url],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE
            )
            if res.returncode == 0 and os.path.exists(target_path) and os.path.getsize(target_path) > 1000:
                shutil.copy2(target_path, web_target)
                return True
        except Exception:
            pass

    print(f"  Chyba při stahování {url}")
    return False

def deduplicate_records(records):
    """
    Sloučí duplicitní záznamy se stejným jménem, kategorií a zdrojem.
    """
    by_key = {}
    for item_id, r in list(records.items()):
        src = r.get("source_name", SOURCE_HORNICEK)
        key = (r.get("name", "").strip().lower(), r.get("category", "").strip().lower(), src.lower())
        if not key[0]:
            by_key[(item_id, "", src.lower())] = r
            continue
        if key not in by_key:
            by_key[key] = r
        else:
            prev = by_key[key]
            if r.get("is_active") and not prev.get("is_active"):
                by_key[key] = r
            elif r.get("last_seen", "") > prev.get("last_seen", "") and not prev.get("is_active"):
                by_key[key] = r

    return {r["id"]: r for r in by_key.values()}

def save_databases(records):
    os.makedirs(DATA_DIR, exist_ok=True)
    os.makedirs(WEB_DATA_DIR, exist_ok=True)
    os.makedirs(WEB_IMAGES_DIR, exist_ok=True)
    
    records_list = list(records.values())
    records_list.sort(
        key=lambda x: (
            x.get("death_date_iso") or "0000-00-00",
            1 if x.get("is_active") else 0,
            x.get("last_seen") or ""
        ),
        reverse=True
    )
    
    for path in [DB_JSON_PATH, os.path.join(WEB_DATA_DIR, "database.json")]:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(records_list, f, ensure_ascii=False, indent=2)
    print(f"Uložen JSON ({len(records_list)} záznamů): {DB_JSON_PATH}")
    
    conn = sqlite3.connect(DB_SQLITE_PATH)
    cur = conn.cursor()
    cur.execute("DROP TABLE IF EXISTS obituaries")
    cur.execute("""
    CREATE TABLE IF NOT EXISTS obituaries (
        id TEXT PRIMARY KEY,
        name TEXT,
        gender TEXT,
        category TEXT,
        source_name TEXT,
        original_title TEXT,
        is_active INTEGER,
        is_archived INTEGER,
        image_url TEXT,
        local_image_path TEXT,
        release_image_url TEXT,
        face_image_path TEXT,
        release_face_url TEXT,
        age TEXT,
        death_date TEXT,
        death_date_iso TEXT,
        death_cause TEXT,
        residence TEXT,
        lat REAL,
        lon REAL,
        ceremony TEXT,
        raw_text TEXT,
        first_seen TEXT,
        last_seen TEXT
    )
    """)
    
    for r in records_list:
        cur.execute("""
        INSERT OR REPLACE INTO obituaries (
            id, name, gender, category, source_name, original_title, is_active, is_archived,
            image_url, local_image_path, release_image_url, face_image_path, release_face_url,
            age, death_date, death_date_iso, death_cause, residence, lat, lon, ceremony,
            raw_text, first_seen, last_seen
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            r.get("id"),
            r.get("name"),
            r.get("gender", ""),
            r.get("category"),
            r.get("source_name", SOURCE_HORNICEK),
            r.get("original_title"),
            1 if r.get("is_active") else 0,
            1 if r.get("is_archived") else 0,
            r.get("image_url"),
            r.get("local_image_path"),
            r.get("release_image_url", ""),
            r.get("face_image_path", ""),
            r.get("release_face_url", ""),
            r.get("age", ""),
            r.get("death_date", ""),
            r.get("death_date_iso", ""),
            r.get("death_cause", ""),
            r.get("residence", ""),
            r.get("lat"),
            r.get("lon"),
            r.get("ceremony", ""),
            r.get("raw_text", ""),
            r.get("first_seen"),
            r.get("last_seen")
        ))
    conn.commit()
    conn.close()
    print(f"Uložen SQLite: {DB_SQLITE_PATH}")

    for path in [DB_CSV_PATH, os.path.join(WEB_DATA_DIR, "database.csv")]:
        with open(path, "w", encoding="utf-8-sig", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["ID", "Jméno", "Pohlaví", "Věk", "Město/Bydliště", "Datum úmrtí", "Důvod úmrtí", "Místo a čas obřadu", "Kategorie", "Zdroj", "Aktivní na vývěsce", "V archivu", "URL obrázku", "Release URL parte", "Release URL obličeje", "Prvně nalezeno", "Naposledy viděno"])
            for r in records_list:
                writer.writerow([
                    r.get("id"),
                    r.get("name"),
                    r.get("gender", ""),
                    r.get("age", ""),
                    r.get("residence", ""),
                    r.get("death_date", ""),
                    r.get("death_cause", ""),
                    r.get("ceremony", ""),
                    r.get("category"),
                    r.get("source_name", SOURCE_HORNICEK),
                    "ANO" if r.get("is_active") else "NE",
                    "ANO" if r.get("is_archived") else "NE",
                    r.get("image_url"),
                    r.get("release_image_url", ""),
                    r.get("release_face_url", ""),
                    r.get("first_seen"),
                    r.get("last_seen")
                ])
    print(f"Uložen CSV: {DB_CSV_PATH}")

def update_source_active_flags(existing_records, source_name, active_items):
    """
    Aktualizuje příznaky is_active a is_archived pouze pro daný zdroj (source_name)
    a pouze tehdy, když se podařilo stáhnout alespoň 1 položku z dané vývěsky.
    """
    if not active_items:
        return
    active_ids = {item["id"] for item in active_items}
    for r in existing_records.values():
        if r.get("source_name") == source_name:
            if r["id"] in active_ids:
                r["is_active"] = True
                r["is_archived"] = False
            else:
                r["is_active"] = False
                r["is_archived"] = True

def run_scraper(enrich_existing_only=False):
    os.makedirs(IMAGES_DIR, exist_ok=True)
    os.makedirs(FACES_DIR, exist_ok=True)
    os.makedirs(WEB_FACES_DIR, exist_ok=True)
    
    existing_records = {}
    if os.path.exists(DB_JSON_PATH):
        try:
            with open(DB_JSON_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                for item in data:
                    # Přejmenovat starší označení "PS Horníček" na "Horníček Moravičany"
                    if item.get("source_name") in (None, "", "PS Horníček", "ps hornicek"):
                        item["source_name"] = SOURCE_HORNICEK
                    existing_records[item["id"]] = item
            print(f"Načteno {len(existing_records)} existujících záznamů.")
        except Exception as e:
            print(f"Varování: Nepodařilo se načíst stávající JSON: {e}")

    now_iso = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    if not enrich_existing_only:
        # 1. Zdroj: Horníček Moravičany (vývěska + archiv)
        try:
            hornicek_vyveska_html = fetch_html(URL_HORNICEK_VYVESKA)
            hornicek_vyveska_items = parse_hornicek_html(hornicek_vyveska_html, "vyveska")
            print(f"[{SOURCE_HORNICEK}] Nalezeno {len(hornicek_vyveska_items)} parte na vývěsce.")
        except Exception as e:
            print(f"[{SOURCE_HORNICEK}] Chyba při stahování vývěsky: {e}")
            hornicek_vyveska_items = []

        try:
            hornicek_archiv_html = fetch_html(URL_HORNICEK_ARCHIV)
            hornicek_archiv_items = parse_hornicek_html(hornicek_archiv_html, "archiv")
            print(f"[{SOURCE_HORNICEK}] Nalezeno {len(hornicek_archiv_items)} parte v archivu.")
        except Exception as e:
            print(f"[{SOURCE_HORNICEK}] Chyba při stahování archivu: {e}")
            hornicek_archiv_items = []

        update_source_active_flags(existing_records, SOURCE_HORNICEK, hornicek_vyveska_items)

        # 2. Zdroj: Hrandop Litovel
        try:
            hrandop_html = fetch_html(URL_HRANDOP_VYVESKA)
            hrandop_items = parse_hrandop_html(hrandop_html)
            print(f"[{SOURCE_HRANDOP}] Nalezeno {len(hrandop_items)} parte na vývěsce.")
        except Exception as e:
            print(f"[{SOURCE_HRANDOP}] Chyba při stahování vývěsky: {e}")
            hrandop_items = []

        update_source_active_flags(existing_records, SOURCE_HRANDOP, hrandop_items)

        # 3. Zdroj: Drozd Zábřeh
        try:
            drozd_html = fetch_html(URL_DROZD_VYVESKA)
            drozd_items = parse_drozd_html(drozd_html)
            print(f"[{SOURCE_DROZD}] Nalezeno {len(drozd_items)} parte na vývěsce.")
        except Exception as e:
            print(f"[{SOURCE_DROZD}] Chyba při stahování vývěsky: {e}")
            drozd_items = []

        update_source_active_flags(existing_records, SOURCE_DROZD, drozd_items)

        all_found = []
        for item in hornicek_vyveska_items + hrandop_items + drozd_items:
            item["is_active"] = True
            item["is_archived"] = False
            all_found.append(item)

        for item in hornicek_archiv_items:
            item["is_active"] = False
            item["is_archived"] = True
            all_found.append(item)

        if not all_found and existing_records:
            print("Varování: Žádný ze zdrojových webů není dostupný. Ponechávám stávající databázi beze změny.")
            return

        for item in all_found:
            item_id = item["id"]
            img_filename = item["filename"]
            local_img_path = os.path.join(IMAGES_DIR, img_filename)
            rel_img_path = f"images/{img_filename}"
            download_image(item["image_url"], local_img_path)
            
            if item_id not in existing_records:
                existing_records[item_id] = {
                    "id": item_id,
                    "name": item["name"],
                    "gender": detect_gender(item["name"]),
                    "category": item["category"],
                    "source_name": item.get("source_name", SOURCE_HORNICEK),
                    "original_title": item["original_title"],
                    "is_active": item["is_active"],
                    "is_archived": item["is_archived"],
                    "image_url": item["image_url"],
                    "local_image_path": rel_img_path,
                    "release_image_url": f"{RELEASE_BASE_URL}/{img_filename}",
                    "face_image_path": "",
                    "release_face_url": "",
                    "first_seen": now_iso,
                    "last_seen": now_iso,
                    "age": "",
                    "death_date": "",
                    "death_date_iso": "",
                    "death_cause": "",
                    "residence": "",
                    "lat": None,
                    "lon": None,
                    "ceremony": "",
                    "raw_text": ""
                }
            else:
                existing = existing_records[item_id]
                existing["source_name"] = item.get("source_name", existing.get("source_name", SOURCE_HORNICEK))
                if item["is_active"]:
                    existing["is_active"] = True
                    existing["is_archived"] = False
                if item["is_archived"]:
                    existing["is_archived"] = True
                existing["last_seen"] = now_iso
                existing["local_image_path"] = rel_img_path
                existing["release_image_url"] = f"{RELEASE_BASE_URL}/{img_filename}"

    for r in existing_records.values():
        if not r.get("is_active") and not r.get("is_archived"):
            r["is_archived"] = True

    print(f"\nKontroluji OCR rozpoznávání textu, sjednocení dat a detekci obličejů...")
    for item_id, item in existing_records.items():
        fname = item.get("filename") or os.path.basename(item.get("local_image_path", "")) or f"{item_id}.jpg"
        img_path = os.path.join(IMAGES_DIR, fname)

        raw_text = item.get("raw_text", "")
        if (not raw_text or enrich_existing_only) and os.path.exists(img_path):
            raw_text = run_ocr(img_path)

        parsed = parse_parte_text(raw_text, item.get("category", ""), item.get("name", ""))
        item["raw_text"] = parsed["raw_text"]
        if parsed["name"]:
            item["name"] = parsed["name"]
        item["gender"] = parsed["gender"]
        if item.get("source_name") in (None, "", "PS Horníček", "ps hornicek"):
            item["source_name"] = SOURCE_HORNICEK
        item["residence"] = parsed["residence"] or clean_town(item.get("residence", ""))
        if item["residence"] == "Lisieux":
            item["residence"] = "Mohelnice"
        item["age"] = parsed["age"] or normalize_age(item.get("age", ""), raw_text)
        disp_date, iso_date = normalize_czech_date(raw_text, item.get("death_date", ""))
        item["death_date"] = disp_date
        item["death_date_iso"] = iso_date
        if parsed["death_cause"]:
            item["death_cause"] = parsed["death_cause"]
        if parsed["ceremony"]:
            item["ceremony"] = parsed["ceremony"]

        coords = TOWN_COORDS.get(item.get("residence", ""))
        if coords:
            item["lat"], item["lon"] = coords[0], coords[1]
        else:
            item["lat"], item["lon"] = None, None

        item["release_image_url"] = f"{RELEASE_BASE_URL}/{fname}"

        if os.path.exists(img_path):
            face_rel = extract_face_portrait(img_path, item_id, force=enrich_existing_only)
            item["face_image_path"] = face_rel
            item["release_face_url"] = f"{RELEASE_BASE_URL}/face_{item_id}.jpg" if face_rel else ""
        else:
            if item.get("face_image_path"):
                item["release_face_url"] = f"{RELEASE_BASE_URL}/face_{item_id}.jpg"
            else:
                item["release_face_url"] = ""

    existing_records = deduplicate_records(existing_records)

    # Aplikace pravidla archivace: aktuální 1 týden (7 dní) od data úmrtí,
    # bez ohledu na to, zda je či není jako aktuální na některém ze zdrojových webů.
    today_date = datetime.now().date()
    for r in existing_records.values():
        ref_iso = (r.get("death_date_iso") or "").strip()
        if not ref_iso:
            first_seen = (r.get("first_seen") or "").strip()
            if len(first_seen) >= 10:
                ref_iso = first_seen[:10]
        is_recent = False
        if ref_iso:
            try:
                d = datetime.strptime(ref_iso[:10], "%Y-%m-%d").date()
                is_recent = (today_date - d).days <= 7
            except Exception:
                is_recent = bool(r.get("is_active"))
        else:
            is_recent = bool(r.get("is_active"))
        r["is_active"] = bool(is_recent)
        r["is_archived"] = not bool(is_recent)

    save_databases(existing_records)
    print(f"\nHotovo! Všechny záznamy ({len(existing_records)}) byly aktualizovány a uloženy.")

if __name__ == "__main__":
    run_scraper(enrich_existing_only=False)
