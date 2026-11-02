# Smuteční vývěska PS Horníček - Scraper, Databáze a Android Aplikace ✝

Kompletní řešení pro stahování, archivaci, rozpoznávání údajů a prohlížení smutečních oznámení (parte) a vzpomínek z webu **[Pohřební služba Horníček](https://www.pshornicek.cz/c-m-s-page/smutecni-vyveska)** (Moravičany, Mohelnice a okolí).

---

## 🚀 Co projekt obsahuje

1. **Automatický Python Scraper (`scraper/scraper.py`)**:
   - Prochází aktuální vývěsku i archiv parte.
   - Stahuje a trvale uchovává originální obrázky parte ve složce `data/images/` (i když je provozovatel z webu smaže).
   - Čistí jména a rozlišuje typ (Smuteční oznámení vs. Vzpomínka).
   - Podporuje **OCR (Tesseract)** i **Google Gemini AI Vision** pro automatické rozpoznání data úmrtí, věku, bydliště a místa posledního rozloučení přímo z obrázků.
   - Generuje databázi ve **3 formátech**:
     - `data/database.json` – pro Android aplikaci a web
     - `data/database.sqlite` – SQLite databáze pro SQL dotazy
     - `data/database.csv` – pro otevření v Excelu

2. **Bezplatný 24/7 hosting na GitHubu (`.github/workflows/`)**:
   - **`scraper.yml`**: Běží automaticky každých 6 hodin v cloudu GitHubu (zcela zdarma), stáhne nová parte, aktualizuje databázi a publikuje ji na GitHub Pages.
   - **`build_apk.yml`**: Automaticky sestaví Android aplikaci do formátu `.apk` a vytvoří odkaz ke stažení.

3. **Nativní Android aplikace (`android/`)**:
   - Moderní Android aplikace v **Kotlinu** s **Jetpack Compose** a Material 3 designem.
   - Okamžité vyhledávání podle jména, obce či roku.
   - Filtrování (Vše, Vývěska, Oznámení, Vzpomínky, Archiv).
   - Prohlížeč parte s možností přiblížení (pinch-to-zoom).
   - Sdílení parte (WhatsApp, SMS, e-mail).
   - **Offline podpora**: data se ukládají lokálně v telefonu, takže aplikace funguje i bez připojení k internetu.

4. **Webová aplikace / PWA pro GitHub Pages (`web/`)**:
   - Responzivní webový prohlížeč pro mobil i počítač.
   - Podpora PWA (Progressive Web App): v prohlížeči v mobilu stačí kliknout na **„Přidat na plochu“** / **„Instalovat aplikaci“** a funguje jako plnohodnotná aplikace.

---

## 📁 Struktura projektu

```text
├── .github/
│   └── workflows/
│       ├── scraper.yml             # Běží každých 6h, stahuje nová parte a publikuje web
│       └── build_apk.yml           # Automaticky sestaví instalační Android .apk
├── scraper/
│   ├── scraper.py                  # Hlavní scraper a extraktor údajů
│   └── requirements.txt            # Závislosti (requests, pillow, pytesseract...)
├── data/
│   ├── database.json               # JSON databáze (pro Android a web)
│   ├── database.sqlite             # SQLite databáze
│   ├── database.csv                # CSV tabulka pro Excel
│   └── images/                     # Stažené obrázky parte v plné kvalitě
├── web/
│   ├── index.html                  # Webová aplikace / PWA
│   ├── styles.css                  # Tmavý důstojný design
│   ├── app.js                      # Vyhledávání, filtry, modal zoom, sdílení
│   ├── manifest.json               # PWA konfigurace
│   └── sw.js                       # Service Worker pro offline režim
├── android/                        # Kompletní projekt nativní Android aplikace (Kotlin)
│   ├── app/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── README.md
```

---

## 🛠️ Návod: Jak vše nahrát na GitHub a spustit

### 1. Vytvoření repozitáře na GitHubu
1. Přihlaste se na [github.com](https://github.com) a klikněte na **New repository** (např. `smutecni-vyveska`).
2. Ponechte repozitář jako **Public** (aby fungoval GitHub Pages zdarma).

### 2. Nahrání souborů
V této složce spusťte v příkazové řádce:
```bash
git init
git add .
git commit -m "Inicializace: scraper, databáze a Android aplikace"
git branch -M main
git remote add origin https://github.com/VASE_JMENO/smutecni-vyveska.git
git push -u origin main
```

### 3. Zapnutí GitHub Pages (Hosting webu a databáze)
1. V repozitáři na GitHubu otevřete **Settings** -> **Pages**.
2. V sekci **Build and deployment** -> **Source** vyberte **GitHub Actions**.
3. Nyní se po každém spuštění scraperu web a JSON databáze automaticky publikují na:
   `https://VASE_JMENO.github.io/smutecni-vyveska/`

### 4. (Volitelné) Automatické AI rozpoznávání textu přes Gemini
Scraper standardně používá OCR (Tesseract). Pokud chcete 100% přesnou extrakci detailů (datum, věk, obec, obřad) pomocí umělé inteligence:
1. Získejte bezplatný klíč na [Google AI Studio](https://aistudio.google.com/app/apikey).
2. V repozitáři na GitHubu přejděte do **Settings** -> **Secrets and variables** -> **Actions**.
3. Přidejte secret s názvem `GEMINI_API_KEY` a vložte váš klíč.

---

## 📱 Jak používat na Android telefonu

Máte **2 jednoduché možnosti**:

### Možnost A: Okamžitá instalace přes Web / PWA (Bez kompilace)
1. Otevřete v prohlížeči Chrome v mobilu adresu vašeho GitHub Pages:
   `https://VASE_JMENO.github.io/smutecni-vyveska/`
2. Zobrazí se výzva **„Přidat na domovskou obrazovku“** nebo v menu Chrome (tři tečky vpravo nahoře) zvolte **„Nainstalovat aplikaci“**.
3. Aplikace se nainstaluje na plochu vašeho mobilu s vlastní ikonou a funguje jako plnohodnotná aplikace.

### Možnost B: Nativní Android APK aplikace
1. V GitHub repozitáři přejděte do záložky **Actions** -> zvolte workflow **Sestavení Android aplikace (APK)** -> klikněte na **Run workflow**.
2. Po dokončení (cca 2–3 minuty) si v sekci **Artifacts** stáhněte soubor `SmutecniVyveska-APK.zip`.
3. Rozbalte soubor, přeneste `.apk` do telefonu a nainstalujte.
4. V aplikaci můžete v nastavení (ikona ozubeného kola) zkontrolovat URL databáze:
   `https://VASE_JMENO.github.io/smutecni-vyveska/data/database.json`
