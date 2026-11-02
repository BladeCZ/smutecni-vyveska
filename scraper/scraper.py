"""
Smuteční vývěska PS Horníček - Web Scraper & Database Builder
Stahuje parte z https://www.pshornicek.cz/c-m-s-page/smutecni-vyveska a /archiv.html
"""

import os
import re
import json
import sqlite3
import csv
import base64
from datetime import datetime
import requests

BASE_URL = "https://www.pshornicek.cz"
URL_VYVESKA = f"{BASE_URL}/c-m-s-page/smutecni-vyveska"
URL_ARCHIV = f"{BASE_URL}/archiv.html"

# Složky
ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DATA_DIR = os.path.join(ROOT_DIR, "data")
IMAGES_DIR = os.path.join(DATA_DIR, "images")
WEB_DATA_DIR = os.path.join(ROOT_DIR, "web", "data")
DB_JSON_PATH = os.path.join(DATA_DIR, "database.json")
DB_SQLITE_PATH = os.path.join(DATA_DIR, "database.sqlite")
DB_CSV_PATH = os.path.join(DATA_DIR, "database.csv")

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

def fetch_html(url):
    print(f"Stahuji HTML: {url}")
    resp = requests.get(url, headers=HEADERS, timeout=20)
    resp.encoding = 'utf-8'
    return resp.text

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
    if os.path.exists(target_path) and os.path.getsize(target_path) > 0:
        return False
    print(f"  Stahuji nový obrázek: {os.path.basename(target_path)}")
    resp = requests.get(url, headers=HEADERS, timeout=30)
    if resp.status_code == 200:
        with open(target_path, 'wb') as f:
            f.write(resp.content)
        return True
    else:
        print(f"  Chyba při stahování {url}: HTTP {resp.status_code}")
        return False

def extract_with_gemini(image_path, api_key):
    """
    Extrakce detailů pomocí Google Gemini Flash Vision API
    """
    try:
        with open(image_path, "rb") as f:
            img_b64 = base64.b64encode(f.read()).decode("utf-8")

        prompt = """Analyzuj toto smuteční oznámení (parte) a vrať JSON se strukturovanými údaji:
{
  "name": "Celé jméno zesnulé osoby včetně titulů",
  "age": "Věk (např. 80 let) nebo prázdný řetězec",
  "death_date": "Datum úmrtí (např. 27. října 2026)",
  "residence": "Bydliště / obec (např. Mohelnice)",
  "ceremony_date": "Datum a den obřadu rozloučení (pokud je uveden)",
  "ceremony_place": "Místo konání obřadu (obřadní síň, kostel, hřbitov apod.)",
  "survivors": "Zarmoucená rodina / pozůstalí",
  "quote": "Smuteční citát / verš"
}
Vrať POUZE validní JSON bez markdownu kolem."""

        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"
        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": "image/jpeg",
                                "data": img_b64
                            }
                        }
                    ]
                }
            ]
        }
        resp = requests.post(url, json=payload, timeout=30)
        if resp.status_code == 200:
            res_json = resp.json()
            text = res_json['candidates'][0]['content']['parts'][0]['text'].strip()
            text = re.sub(r'^```json\s*', '', text)
            text = re.sub(r'\s*```$', '', text)
            data = json.loads(text)
            return {
                "datum_umrti": data.get("death_date", ""),
                "vek": data.get("age", ""),
                "misto_bydliste": data.get("residence", ""),
                "misto_obradu": data.get("ceremony_place", ""),
                "datum_obradu": data.get("ceremony_date", ""),
                "pozustali": data.get("survivors", ""),
                "citat": data.get("quote", ""),
                "raw_text": f"{data.get('quote', '')}\n{data.get('survivors', '')}".strip()
            }
    except Exception as e:
        print(f"  Gemini API extrakce selhala: {e}")
    return None

def extract_with_tesseract(image_path):
    """
    Extrakce detailů pomocí lokálního Tesseract OCR
    """
    details = {
        "datum_umrti": "",
        "vek": "",
        "misto_bydliste": "",
        "misto_obradu": "",
        "datum_obradu": "",
        "pozustali": "",
        "citat": "",
        "raw_text": ""
    }
    try:
        import pytesseract
        from PIL import Image
        img = Image.open(image_path)
        try:
            text = pytesseract.image_to_string(img, lang='ces')
        except Exception:
            text = pytesseract.image_to_string(img)
            
        details["raw_text"] = text.strip()
        
        # Věk
        age_m = re.search(r'(?:ve\s+v[eě]ku|do[zž]il(?:a)?\s+se)\s+(\d{1,3})\s+let', text, re.IGNORECASE)
        if age_m:
            details["vek"] = f"{age_m.group(1)} let"
            
        # Datum úmrtí
        death_m = re.search(r'(?:zem[rř]el(?:a)?|opustil(?:a)?(?:\s+n[aá]s)?).*?(\d{1,2}\.?\s+[a-záčďéěíňóřšťúůýž0-9\.\s]+(?:\d{4})?)', text, re.IGNORECASE)
        if death_m:
            details["datum_umrti"] = death_m.group(1).strip()
            
        # Bydliště
        res_m = re.search(r'\bz\s+([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+(?:\s+[A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][a-záčďéěíňóřšťúůýž]+)?)\b', text)
        if res_m:
            details["misto_bydliste"] = res_m.group(0).strip()
            
        # Obřad
        cer_m = re.search(r'(?:rozlou[cč][ií]me|ob[rř]ad).*?(v\s+[^.\n]+)', text, re.IGNORECASE)
        if cer_m:
            details["misto_obradu"] = cer_m.group(1).strip()[:100]

    except ImportError:
        pass
    except Exception as e:
        print(f"  Tesseract OCR selhal: {e}")
    return details

def extract_details(image_path):
    gemini_key = os.environ.get("GEMINI_API_KEY")
    if gemini_key:
        res = extract_with_gemini(image_path, gemini_key)
        if res:
            return res
    return extract_with_tesseract(image_path)

def save_databases(records):
    os.makedirs(DATA_DIR, exist_ok=True)
    os.makedirs(WEB_DATA_DIR, exist_ok=True)
    
    records_list = list(records.values())
    records_list.sort(key=lambda x: (0 if x.get("is_active") else 1, x.get("last_seen", "")), reverse=False)
    
    # 1. JSON databáze v data/ i web/data/
    for path in [DB_JSON_PATH, os.path.join(WEB_DATA_DIR, "database.json")]:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(records_list, f, ensure_ascii=False, indent=2)
    print(f"Uložen JSON ({len(records_list)} záznamů): {DB_JSON_PATH}")
    
    # 2. SQLite databáze
    conn = sqlite3.connect(DB_SQLITE_PATH)
    cur = conn.cursor()
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
            image_url, local_image_path, age, death_date, residence, ceremony,
            raw_text, first_seen, last_seen
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
    with open(DB_CSV_PATH, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["ID", "Jméno", "Kategorie", "Aktivní na vývěsce", "V archivu", "Věk", "Datum úmrtí", "Bydliště", "Místo obřadu", "Původní titulek", "URL obrázku", "Lokalní soubor", "Prvně nalezeno", "Naposledy viděno"])
        for r in records_list:
            writer.writerow([
                r.get("id"),
                r.get("name"),
                r.get("category"),
                "ANO" if r.get("is_active") else "NE",
                "ANO" if r.get("is_archived") else "NE",
                r.get("age", ""),
                r.get("death_date", ""),
                r.get("residence", ""),
                r.get("ceremony", ""),
                r.get("original_title"),
                r.get("image_url"),
                r.get("local_image_path"),
                r.get("first_seen"),
                r.get("last_seen")
            ])
    print(f"Uložen CSV: {DB_CSV_PATH}")

def run_scraper():
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

    for r in existing_records.values():
        r["is_active"] = False

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

    all_found = []
    for item in vyveska_items:
        item["is_active"] = True
        item["is_archived"] = False
        all_found.append(item)

    for item in archiv_items:
        item["is_active"] = False
        item["is_archived"] = True
        all_found.append(item)

    new_downloads_count = 0

    for item in all_found:
        item_id = item["id"]
        img_filename = item["filename"]
        local_img_path = os.path.join(IMAGES_DIR, img_filename)
        rel_img_path = f"images/{img_filename}"
        
        downloaded = download_image(item["image_url"], local_img_path)
        if downloaded:
            new_downloads_count += 1
            
        if item_id not in existing_records:
            rec = {
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
                "residence": "",
                "ceremony": "",
                "raw_text": ""
            }
            if os.path.exists(local_img_path):
                details = extract_details(local_img_path)
                rec.update({
                    "age": details.get("vek", ""),
                    "death_date": details.get("datum_umrti", ""),
                    "residence": details.get("misto_bydliste", ""),
                    "ceremony": details.get("misto_obradu", ""),
                    "raw_text": details.get("raw_text", "")
                })
            existing_records[item_id] = rec
        else:
            existing = existing_records[item_id]
            if item["is_active"]:
                existing["is_active"] = True
            if item["is_archived"]:
                existing["is_archived"] = True
            existing["last_seen"] = now_iso
            existing["local_image_path"] = rel_img_path
            
            if not existing.get("death_date") and os.path.exists(local_img_path):
                details = extract_details(local_img_path)
                if details.get("vek"): existing["age"] = details["vek"]
                if details.get("datum_umrti"): existing["death_date"] = details["datum_umrti"]
                if details.get("misto_bydliste"): existing["residence"] = details["misto_bydliste"]
                if details.get("misto_obradu"): existing["ceremony"] = details["misto_obradu"]
                if details.get("raw_text"): existing["raw_text"] = details["raw_text"]

    save_databases(existing_records)
    print(f"\nHotovo! Nově stažených obrázků: {new_downloads_count}. Celkem v databázi: {len(existing_records)}")

if __name__ == "__main__":
    run_scraper()
