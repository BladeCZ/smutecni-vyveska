"""
Smuteční vývěska PS Horníček - Web Scraper & Database Builder
Stahuje parte z https://www.pshornicek.cz/c-m-s-page/smutecni-vyveska a /archiv.html
"""

import os
import re
import json
import sqlite3
import csv
import subprocess
from datetime import datetime
import requests

BASE_URL = "https://www.pshornicek.cz"
URL_VYVESKA = f"{BASE_URL}/c-m-s-page/smutecni-vyveska"
URL_ARCHIV = f"{BASE_URL}/archiv.html"

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DATA_DIR = os.path.join(ROOT_DIR, "data")
IMAGES_DIR = os.path.join(DATA_DIR, "images")
WEB_DATA_DIR = os.path.join(ROOT_DIR, "web", "data")
WEB_IMAGES_DIR = os.path.join(ROOT_DIR, "web", "images")
DB_JSON_PATH = os.path.join(DATA_DIR, "database.json")
DB_SQLITE_PATH = os.path.join(DATA_DIR, "database.sqlite")
DB_CSV_PATH = os.path.join(DATA_DIR, "database.csv")

LOCAL_TESSDATA = os.path.join(ROOT_DIR, "scraper", "tessdata")

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "cs,en;q=0.9"
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
    mapping = {
        "Loštic": "Loštice",
        "Pavlova": "Pavlov",
        "Moravičan": "Moravičany",
        "Červené Lhoty": "Červená Lhota",
        "Stavenic": "Stavenice",
        "Mirova": "Mírov",
        "Mírova": "Mírov",
        "Lukavice": "Lukavice",
        "Třeštiny": "Třeština",
        "Palonína": "Palonín",
        "Mohelnice": "Mohelnice",
        "Zvole": "Zvole",
        "Moravičanech": "Moravičany",
        "Lošticích": "Loštice"
    }
    t = town_str.strip()
    return mapping.get(t, t)

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

def parse_parte_text(text, category=""):
    info = {
        "residence": "",
        "age": "",
        "death_date": "",
        "death_cause": "",
        "ceremony": "",
        "raw_text": text.strip()
    }

    if not text:
        return info

    # Bydliště (z Mohelnice, z Loštic...)
    res_m = re.search(r'\bz\s+([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž\s\-]+?)(?=\n|$)', text)
    if res_m:
        raw_res = res_m.group(1).strip()
        if not re.search(r'\b(?:dne|ve|v|pan|paní|srdci|bolestí)\b', raw_res, re.IGNORECASE):
            info["residence"] = clean_town(raw_res)

    # Věk
    age_m = re.search(r'(?:ve\s+v[eě]ku|do[zž]il(?:a)?\s+se)\s+([^.\n]+?let)', text, re.IGNORECASE)
    if age_m:
        info["age"] = age_m.group(1).strip()

    # Kdy zemřel(a) / datum
    death_m = re.search(r'((?:v\s+[a-záčďéěíňóřšťúůýž]+\s+)?dne\s+\d{1,2}\.?\s+[a-záčďéěíňóřšťúůýž]+(?:\s+\d{4})?)', text, re.IGNORECASE)
    if death_m:
        info["death_date"] = death_m.group(1).strip()

    # Důvod / způsob úmrtí
    cause_m = re.search(r'(?:Zemřel|Zemřela|Odešel|Odešla)\s+((?:náhle|tiše|nečekaně|tragicky|po\s+[^,\n]+?nemoci)[^.\n]*)', text, re.IGNORECASE)
    if cause_m:
        cause = cause_m.group(1).strip()
        # Odstranit případné čárky
        cause = re.sub(r'[,;]+$', '', cause)
        if len(cause) > 2 and not cause.lower().startswith("dne"):
            info["death_cause"] = cause

    # Poslední rozloučení / obřad
    if category == "Vzpomínka":
        info["ceremony"] = "Vzpomínka na zesnulého"
    elif re.search(r'bez\s+smutečního\s+obřadu', text, re.IGNORECASE):
        info["ceremony"] = "Bez smutečního obřadu (na přání zesnulého)"
    elif re.search(r'(?:uctíte\s+tichou\s+vzpomínkou|tělesné\s+pozůstatky\s+budou\s+zpopelněny)', text, re.IGNORECASE) and not re.search(r'rozloučíme', text, re.IGNORECASE):
        info["ceremony"] = "Tiché rozloučení bez smutečního obřadu"
    else:
        cer_match = re.search(r'rozloučíme\s+([\s\S]+?)(?=\.\s*(?:Po\s+smutečních|Jménem|\n\n|$))', text, re.IGNORECASE)
        if cer_match:
            clean_cer = " ".join([l.strip() for l in cer_match.group(1).split() if l.strip()])
            info["ceremony"] = clean_cer

    return info

import shutil
import time
import urllib.parse

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

    # Fallback přes proxy pro případ GeoIP blokace zahraničních IP adres (např. GitHub Actions)
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
            "source": source_type
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

def save_databases(records):
    os.makedirs(DATA_DIR, exist_ok=True)
    os.makedirs(WEB_DATA_DIR, exist_ok=True)
    os.makedirs(WEB_IMAGES_DIR, exist_ok=True)
    
    records_list = list(records.values())
    # Seřadit: nejprve aktivní na vývěsce, poté archiv
    records_list.sort(key=lambda x: (0 if x.get("is_active") else 1, x.get("last_seen", "")), reverse=False)
    
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
        category TEXT,
        original_title TEXT,
        is_active INTEGER,
        is_archived INTEGER,
        image_url TEXT,
        local_image_path TEXT,
        age TEXT,
        death_date TEXT,
        death_cause TEXT,
        residence TEXT,
        ceremony TEXT,
        raw_text TEXT,
        first_seen TEXT,
        last_seen TEXT
    )
    """)
    
    for r in records_list:
        cur.execute("""
        INSERT OR REPLACE INTO obituaries (
            id, name, category, original_title, is_active, is_archived,
            image_url, local_image_path, age, death_date, death_cause, residence, ceremony,
            raw_text, first_seen, last_seen
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            r.get("id"),
            r.get("name"),
            r.get("category"),
            r.get("original_title"),
            1 if r.get("is_active") else 0,
            1 if r.get("is_archived") else 0,
            r.get("image_url"),
            r.get("local_image_path"),
            r.get("age", ""),
            r.get("death_date", ""),
            r.get("death_cause", ""),
            r.get("residence", ""),
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
            writer.writerow(["ID", "Jméno", "Kategorie", "Město/Bydliště", "Věk", "Datum úmrtí", "Důvod úmrtí", "Místo a čas obřadu", "Aktivní na vývěsce", "V archivu", "Původní titulek", "URL obrázku", "Lokalní soubor", "Prvně nalezeno", "Naposledy viděno"])
            for r in records_list:
                writer.writerow([
                    r.get("id"),
                    r.get("name"),
                    r.get("category"),
                    r.get("residence", ""),
                    r.get("age", ""),
                    r.get("death_date", ""),
                    r.get("death_cause", ""),
                    r.get("ceremony", ""),
                    "ANO" if r.get("is_active") else "NE",
                    "ANO" if r.get("is_archived") else "NE",
                    r.get("original_title"),
                    r.get("image_url"),
                    r.get("local_image_path"),
                    r.get("first_seen"),
                    r.get("last_seen")
                ])
    print(f"Uložen CSV: {DB_CSV_PATH}")

def run_scraper(enrich_existing_only=False):
    os.makedirs(IMAGES_DIR, exist_ok=True)
    
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

        # Aktualizovat příznaky is_active pouze pokud se podařilo stáhnout aktuální vývěsku
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
                    "category": item["category"],
                    "original_title": item["original_title"],
                    "is_active": item["is_active"],
                    "is_archived": item["is_archived"],
                    "image_url": item["image_url"],
                    "local_image_path": rel_img_path,
                    "first_seen": now_iso,
                    "last_seen": now_iso,
                    "age": "",
                    "death_date": "",
                    "death_cause": "",
                    "residence": "",
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

    # OCR obohacení pro položky, kterým chybí detaily (nebo při explicitním přepočtu)
    print(f"\nKontroluji OCR rozpoznávání textu a údajů...")
    enriched_count = 0
    for item_id, item in existing_records.items():
        item.setdefault("death_cause", "")
        item.setdefault("residence", "")
        item.setdefault("age", "")
        item.setdefault("death_date", "")
        item.setdefault("ceremony", "")
        item.setdefault("raw_text", "")
        if item.get("raw_text") and not enrich_existing_only:
            continue
        fname = item.get("filename") or os.path.basename(item.get("local_image_path", "")) or f"{item_id}.jpg"
        img_path = os.path.join(IMAGES_DIR, fname)
        if os.path.exists(img_path):
            ocr_text = run_ocr(img_path)
            if ocr_text:
                parsed = parse_parte_text(ocr_text, item.get("category", ""))
                item["raw_text"] = parsed["raw_text"]
                if parsed["residence"]: item["residence"] = parsed["residence"]
                if parsed["age"]: item["age"] = parsed["age"]
                if parsed["death_date"]: item["death_date"] = parsed["death_date"]
                if parsed["death_cause"]: item["death_cause"] = parsed["death_cause"]
                if parsed["ceremony"]: item["ceremony"] = parsed["ceremony"]
                enriched_count += 1
                print(f"  [{enriched_count}] {item['name']}: {item['residence']}, {item['age']}, {item['death_cause']}")

    save_databases(existing_records)
    print(f"\nHotovo! Všechny záznamy ({len(existing_records)}) byly aktualizovány a uloženy.")

if __name__ == "__main__":
    run_scraper(enrich_existing_only=False)
