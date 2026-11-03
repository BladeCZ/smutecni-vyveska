"""
Smuteční vývěska PS Horníček - Web Scraper & Database Builder
Stahuje parte z https://www.pshornicek.cz/c-m-s-page/smutecni-vyveska a /archiv.html
"""

import os
import re
import json
import sqlite3
import csv
import shutil
import time
import urllib.parse
import subprocess
from datetime import datetime
import requests

try:
    import cv2
except ImportError:
    cv2 = None

BASE_URL = "https://www.pshornicek.cz"
URL_VYVESKA = f"{BASE_URL}/c-m-s-page/smutecni-vyveska"
URL_ARCHIV = f"{BASE_URL}/archiv.html"
DEFAULT_SOURCE_NAME = "PS Horníček"

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
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "cs,en;q=0.9"
}

# GPS souřadnice obcí v regionu pro filtrování podle vzdálenosti (např. Loštice a okolí 20 km)
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
    "Zábřeh": (49.8825, 16.8722),
    "Litovel": (49.7011, 17.0761),
    "Uničov": (49.7708, 17.1214),
    "Šumperk": (49.9653, 16.9706),
    "Olomouc": (49.5938, 17.2509),
    "Bílá Lhota": (49.7103, 16.9761),
    "Slavoňov": (49.8017, 16.9061),
    "Řimice": (49.7186, 17.0144),
    "Klopina": (49.8225, 17.0308),
    "Police": (49.8147, 17.0014),
    "Medlov": (49.7844, 17.0644),
    "Mladeč": (49.7061, 17.0169),
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
    name = title.strip()
    
    if re.search(r'vzpom[ií]nka', title, re.IGNORECASE):
        category = "Vzpomínka"
        name = re.sub(r'^(?:vzpom[ií]nka\s*[-–:]?\s*)', '', name, flags=re.IGNORECASE).strip()
    elif re.search(r'smut(?:e[cč]n[ií]|\.?)\s*ozn[aá]men[ií]', title, re.IGNORECASE):
        category = "Smuteční oznámení"
        name = re.sub(r'^(?:smut(?:e[cč]n[ií]|\.?)\s*ozn[aá]men[ií]\s*[-–:]?\s*)', '', name, flags=re.IGNORECASE).strip()
    elif re.search(r'parte', title, re.IGNORECASE):
        category = "Smuteční oznámení"
        name = re.sub(r'^(?:parte\s*[-–:]?\s*)', '', name, flags=re.IGNORECASE).strip()

    name = name.strip("-–: \t\r\n\"'")
    return category, name

def clean_town(town_str):
    if not town_str:
        return ""
    t = re.sub(r'\s+č\.\s*(?:p\.\s*)?\d+.*$', '', town_str.strip(), flags=re.IGNORECASE)
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
        "Litovle": "Litovel",
        "Šumperka": "Šumperk",
        "Uničova": "Uničov",
        "Olomouce": "Olomouc",
        "Bílé Lhoty": "Bílá Lhota",
        "Slavoňova": "Slavoňov",
        "Řimic": "Řimice",
        "Klopiny": "Klopina",
        "Medlova": "Medlov",
        "Mladče": "Mladeč",
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
        "Zuzana", "Petra", "Lucie", "Pavlína", " Kateřina", "Lenka", "Dagmar", "Irena"
    }
    if surname.endswith(("ová", "á")) or first_name in female_first:
        return "F"
    return "M"

def normalize_age(raw_age, raw_text=""):
    text_to_check = f"{raw_age or ''} {raw_text or ''}"
    # Speciální případ manželů (např. 74 a 77 let)
    m_pair = re.search(r'\b(\d{1,3}\s+a\s+\d{1,3})\s*let\b', text_to_check, re.IGNORECASE)
    if m_pair:
        return f"{m_pair.group(1)} let"
    # Číselný věk (např. "ve věku nedožitých 77 let" -> "77 let")
    m_num = re.search(r'(\d{1,3})\s*let\b', raw_age or "", re.IGNORECASE)
    if not m_num:
        m_num = re.search(r'(?:ve\s+v[eě]ku|do[zž]il(?:a)?\s+se)\s+(?:nedo[zž]it[yý]ch\s+)?(\d{1,3})\s*let', raw_text or "", re.IGNORECASE)
    if m_num:
        return f"{m_num.group(1)} let"
    # Oprava známé OCR chyby u Josefa Vaňka ("vesěkuZulet" -> 71 let)
    if "vesěkuZulet" in (raw_text or ""):
        return "71 let"
    return (raw_age or "").strip()

def normalize_czech_date(text, fallback_date_str=""):
    """
    Najde datum úmrtí v textu parte a vrátí (display_date 'DD. MM. RRRR', iso_date 'RRRR-MM-DD').
    Hledá před pasáží o pohřbu ('rozloučíme', 'Zádušní mše'), aby nedošlo k záměně s datem pohřbu.
    """
    search_space = text or ""
    split_m = re.split(r'(?:rozlou[cč][ií]me|Z[aá]du[sš]n[ií]\s+m[sš]e)', search_space, maxsplit=1, flags=re.IGNORECASE)
    before_funeral = split_m[0] if split_m else search_space

    for candidate in [before_funeral, fallback_date_str, search_space]:
        if not candidate:
            continue
        # Povolit i OCR záměnu '1.' za 'I.' nebo 'l.' po slově 'dne'
        m = re.search(
            r'dne\s+([0-9Il]{1,2})\.?\s+([a-záčďéěíňóřšťúůýž]+)(?:\s+(\d{4}))?',
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
    # Portrétní fotografie na parte se vždy nachází v horní části (y: 4 % až 40 %, x: 18 % až 82 %)
    y1, y2 = int(h * 0.04), int(h * 0.40)
    x1, x2 = int(w * 0.18), int(w * 0.82)
    roi = img[y1:y2, x1:x2]
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)

    cascade_alt2 = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_alt2.xml')
    cascade_default = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

    faces = []
    for sf in (1.05, 1.06, 1.08):
        f1 = cascade_alt2.detectMultiScale(
            gray, scaleFactor=sf, minNeighbors=4,
            minSize=(int(w * 0.06), int(w * 0.06)),
            maxSize=(int(w * 0.28), int(w * 0.28))
        )
        if len(f1) > 0:
            faces = list(f1)
            break
    if not faces:
        for sf in (1.06, 1.08):
            f2 = cascade_default.detectMultiScale(
                gray, scaleFactor=sf, minNeighbors=6,
                minSize=(int(w * 0.06), int(w * 0.06)),
                maxSize=(int(w * 0.28), int(w * 0.28))
            )
            if len(f2) > 0:
                faces = list(f2)
                break

    if not faces:
        return ""

    fx, fy, fw, fh = max(faces, key=lambda b: b[2] * b[3])
    abs_x = x1 + fx
    abs_y = y1 + fy

    # Přidat rozumný okraj kolem obličeje (vlasy, brada, ramena)
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
    info = {
        "residence": "",
        "age": "",
        "death_date": "",
        "death_date_iso": "",
        "death_cause": "",
        "ceremony": "",
        "gender": detect_gender(name, text),
        "raw_text": (text or "").strip()
    }

    if not text:
        return info

    # 1. Bydliště: hledáme v pasáži po slově pan/paní (nebo po jménu), abychom nebrali citáty v záhlaví ("Terezie z Lisieux")
    after_pan_split = re.split(r'\b(?:pan[ií]?|pana)\b', text, maxsplit=1, flags=re.IGNORECASE)
    residence_zone = after_pan_split[1] if len(after_pan_split) > 1 else text
    before_zemrel_split = re.split(r'\b(?:Zemřel|Zemřela|Odešel|Odešla)\b', residence_zone, maxsplit=1, flags=re.IGNORECASE)
    residence_zone = before_zemrel_split[0] if before_zemrel_split else residence_zone

    # Hledání 'z Mohelnice', 'ze Studené Loučky' atd.
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
    cause_m = re.search(r'(?:Zemřel|Zemřela|Odešel|Odešla)\s+((?:náhle|tiše|nečekaně|tragicky|po\s+[^,\n]+?nemoci)[^.\n]*)', text, re.IGNORECASE)
    if cause_m:
        cause = cause_m.group(1).strip()
        cause = re.sub(r'\s+(?:v|ve)\s+(?:pondělí|úterý|středu|čtvrtek|pátek|sobotu|neděli)\b.*$', '', cause, flags=re.IGNORECASE)
        cause = re.sub(r'\s+dne\s+\d+.*$', '', cause, flags=re.IGNORECASE)
        cause = re.sub(r'[,;]+$', '', cause).strip()
        if len(cause) > 2 and not cause.lower().startswith("dne"):
            info["death_cause"] = cause

    # 5. Poslední rozloučení / obřad
    if category == "Vzpomínka":
        info["ceremony"] = "Vzpomínka na zesnulého"
    elif re.search(r'bez\s+smutečního\s+obřadu', text, re.IGNORECASE):
        info["ceremony"] = "Bez smutečního obřadu (na přání zesnulého)"
    elif re.search(r'(?:uctíte\s+tichou\s+vzpomínkou|tělesné\s+pozůstatky\s+budou\s+zpopelněny)', text, re.IGNORECASE) and not re.search(r'rozloučíme', text, re.IGNORECASE):
        info["ceremony"] = "Tiché rozloučení bez smutečního obřadu"
    else:
        cer_match = re.search(r'rozloučíme\s+([\s\S]+?)(?=\.\s*(?:Po\s+smutečních|Po\s+církevních|Jménem|Děkujeme|\n\n|$))', text, re.IGNORECASE)
        if cer_match:
            clean_cer = " ".join([l.strip("| \"'n1l") for l in cer_match.group(1).splitlines() if l.strip()])
            clean_cer = re.sub(r'\s+', ' ', clean_cer).strip()
            info["ceremony"] = clean_cer

    return info

def fetch_html(url):
    print(f"Stahuji HTML: {url}")
    last_err = None
    for attempt in range(1, 3):
        try:
            resp = requests.get(url, headers=HEADERS, timeout=15)
            if resp.status_code == 200 and len(resp.text) > 500:
                resp.encoding = 'utf-8'
                return resp.text
            last_err = RuntimeError(f"HTTP {resp.status_code}")
        except Exception as e:
            last_err = e
            print(f"  Pokus {attempt} selhal ({e})...")
            time.sleep(2)

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

def parse_items_from_html(html, source_type):
    pattern = r'<a[^>]+href=[\'"]([^\'"]+)[\'"][^>]*title=[\'"]([^\'"]+)[\'"]'
    items = []
    
    for match in re.finditer(pattern, html, re.IGNORECASE):
        raw_href = match.group(1).strip()
        title = match.group(2).strip()
        
        if not re.search(r'\.(?:jpg|jpeg|png|webp)', raw_href, re.IGNORECASE):
            continue
            
        clean_rel = raw_href.replace('../..//', '').replace('../../', '').replace('..//', '').lstrip('/')
        full_image_url = f"{BASE_URL}/{clean_rel}"
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
            "source_name": DEFAULT_SOURCE_NAME
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
    for attempt_url in [url, f"https://api.allorigins.win/raw?url={urllib.parse.quote(url, safe='')}"]:
        try:
            resp = requests.get(attempt_url, headers=HEADERS, timeout=25)
            if resp.status_code == 200 and len(resp.content) > 1000:
                with open(target_path, 'wb') as f:
                    f.write(resp.content)
                shutil.copy2(target_path, web_target)
                return True
        except Exception as e:
            print(f"  Pokus o stažení obrázku selhal ({e})...")
    print(f"  Chyba při stahování {url}")
    return False

def deduplicate_records(records):
    """
    Sloučí duplicitní záznamy se stejným jménem a kategorií (vzniklé např. při přesunu z vývěsky do archivu pod novým hashem).
    """
    by_key = {}
    for item_id, r in list(records.items()):
        key = (r.get("name", "").strip().lower(), r.get("category", "").strip().lower())
        if not key[0]:
            continue
        if key not in by_key:
            by_key[key] = r
        else:
            prev = by_key[key]
            # Preferovat aktivní záznam, případně nověji viděný
            if r.get("is_active") and not prev.get("is_active"):
                by_key[key] = r
            elif r.get("last_seen", "") > prev.get("last_seen", "") and not prev.get("is_active"):
                by_key[key] = r

    deduped = {r["id"]: r for r in by_key.values()}
    return deduped

def save_databases(records):
    os.makedirs(DATA_DIR, exist_ok=True)
    os.makedirs(WEB_DATA_DIR, exist_ok=True)
    os.makedirs(WEB_IMAGES_DIR, exist_ok=True)
    
    records_list = list(records.values())
    # Výchozí řazení: podle data úmrtí (od nejnovějších / posledních sestupně)
    records_list.sort(
        key=lambda x: (
            x.get("death_date_iso") or "0000-00-00",
            1 if x.get("is_active") else 0,
            x.get("last_seen") or ""
        ),
        reverse=True
    )
    
    # 1. JSON databáze v data/ i web/data/
    for path in [DB_JSON_PATH, os.path.join(WEB_DATA_DIR, "database.json")]:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(records_list, f, ensure_ascii=False, indent=2)
    print(f"Uložen JSON ({len(records_list)} záznamů): {DB_JSON_PATH}")
    
    # 2. SQLite databáze
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
        face_image_path TEXT,
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
            image_url, local_image_path, face_image_path, age, death_date, death_date_iso,
            death_cause, residence, lat, lon, ceremony, raw_text, first_seen, last_seen
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            r.get("id"),
            r.get("name"),
            r.get("gender", ""),
            r.get("category"),
            r.get("source_name", DEFAULT_SOURCE_NAME),
            r.get("original_title"),
            1 if r.get("is_active") else 0,
            1 if r.get("is_archived") else 0,
            r.get("image_url"),
            r.get("local_image_path"),
            r.get("face_image_path", ""),
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

    # 3. CSV export pro Excel
    for path in [DB_CSV_PATH, os.path.join(WEB_DATA_DIR, "database.csv")]:
        with open(path, "w", encoding="utf-8-sig", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["ID", "Jméno", "Pohlaví", "Věk", "Město/Bydliště", "Datum úmrtí", "Důvod úmrtí", "Místo a čas obřadu", "Kategorie", "Zdroj", "Aktivní na vývěsce", "V archivu", "URL obrázku", "Lokální soubor", "Portrét obličeje", "Prvně nalezeno", "Naposledy viděno"])
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
                    r.get("source_name", DEFAULT_SOURCE_NAME),
                    "ANO" if r.get("is_active") else "NE",
                    "ANO" if r.get("is_archived") else "NE",
                    r.get("image_url"),
                    r.get("local_image_path"),
                    r.get("face_image_path", ""),
                    r.get("first_seen"),
                    r.get("last_seen")
                ])
    print(f"Uložen CSV: {DB_CSV_PATH}")

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
                    existing_records[item["id"]] = item
            print(f"Načteno {len(existing_records)} existujících záznamů.")
        except Exception as e:
            print(f"Varování: Nepodařilo se načíst stávající JSON: {e}")

    now_iso = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    if not enrich_existing_only:
        try:
            vyveska_html = fetch_html(URL_VYVESKA)
            vyveska_items = parse_items_from_html(vyveska_html, "vyveska")
            print(f"Nalezeno {len(vyveska_items)} parte na aktuální vývěsce.")
        except Exception as e:
            print(f"Chyba při stahování vývěsky: {e}")
            vyveska_items = []

        try:
            archiv_html = fetch_html(URL_ARCHIV)
            archiv_items = parse_items_from_html(archiv_html, "archiv")
            print(f"Nalezeno {len(archiv_items)} parte v archivu.")
        except Exception as e:
            print(f"Chyba při stahování archivu: {e}")
            archiv_items = []

        if not vyveska_items and not archiv_items and existing_records:
            print("Varování: Zdrojový web je momentálně nedostupný nebo vrátil 0 položek. Ponechávám stávající databázi beze změny.")
            return

        if vyveska_items:
            active_ids = {item["id"] for item in vyveska_items}
            for r in existing_records.values():
                if r["id"] in active_ids:
                    r["is_active"] = True
                else:
                    r["is_active"] = False
                    r["is_archived"] = True

        all_found = []
        for item in vyveska_items:
            item["is_active"] = True
            item["is_archived"] = False
            all_found.append(item)

        for item in archiv_items:
            item["is_active"] = False
            item["is_archived"] = True
            all_found.append(item)

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
                    "source_name": item.get("source_name", DEFAULT_SOURCE_NAME),
                    "original_title": item["original_title"],
                    "is_active": item["is_active"],
                    "is_archived": item["is_archived"],
                    "image_url": item["image_url"],
                    "local_image_path": rel_img_path,
                    "face_image_path": "",
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
                if item["is_active"]:
                    existing["is_active"] = True
                    existing["is_archived"] = False
                if item["is_archived"]:
                    existing["is_archived"] = True
                existing["last_seen"] = now_iso
                existing["local_image_path"] = rel_img_path

    # Zajistit, aby žádná neaktivní položka nezůstala mimo archiv
    for r in existing_records.values():
        if not r.get("is_active") and not r.get("is_archived"):
            r["is_archived"] = True

    # OCR, sjednocení polí, souřadnice měst a detekce portrétu obličeje
    print(f"\nKontroluji OCR rozpoznávání textu, sjednocení dat a detekci obličejů...")
    for item_id, item in existing_records.items():
        fname = item.get("filename") or os.path.basename(item.get("local_image_path", "")) or f"{item_id}.jpg"
        img_path = os.path.join(IMAGES_DIR, fname)

        raw_text = item.get("raw_text", "")
        if (not raw_text or enrich_existing_only) and os.path.exists(img_path):
            raw_text = run_ocr(img_path)

        parsed = parse_parte_text(raw_text, item.get("category", ""), item.get("name", ""))
        item["raw_text"] = parsed["raw_text"]
        item["gender"] = parsed["gender"]
        item["source_name"] = item.get("source_name") or DEFAULT_SOURCE_NAME
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

        # Doplnit GPS souřadnice obce
        coords = TOWN_COORDS.get(item.get("residence", ""))
        if coords:
            item["lat"], item["lon"] = coords[0], coords[1]
        else:
            item["lat"], item["lon"] = None, None

        # Detekce a výřez obličeje z parte
        if os.path.exists(img_path):
            item["face_image_path"] = extract_face_portrait(img_path, item_id, force=enrich_existing_only)
        else:
            item["face_image_path"] = ""

    # Odstranit duplicity vzniklé přesunem z vývěsky do archivu
    existing_records = deduplicate_records(existing_records)

    save_databases(existing_records)
    print(f"\nHotovo! Všechny záznamy ({len(existing_records)}) byly aktualizovány a uloženy.")

if __name__ == "__main__":
    run_scraper(enrich_existing_only=False)
