// Smuteční vývěska - Klientská aplikace
let allItems = [];
let currentTab = 'active'; // Výchozí záložka: Aktuální vývěska
let currentSearch = '';
let filterGender = 'all';
let filterTown = 'all'; // 'all', '__gps__', nebo název města / vlastní zadání
let filterRadiusKm = 20;
let filterSource = 'all';
let hideMemories = false;
let sortField = 'date'; // Výchozí řazení: podle data úmrtí
let sortDesc = true;    // Od nejnovějších (posledních)
let userGpsCoords = null;
let customTownCoords = {}; // Cache pro dohledané obce přes Nominatim
let activeModalItem = null;
let allKnownTowns = [];
let geocodeTimeout = null;

// GPS souřadnice měst a obcí v regionu pro výpočet vzdálenosti (např. Loštice a okolí 20 km)
const TOWN_COORDS = {
    "Loštice": [49.7458, 16.9278],
    "Mohelnice": [49.7769, 16.9194],
    "Moravičany": [49.7569, 16.9636],
    "Pavlov": [49.7408, 16.8803],
    "Úsov": [49.7986, 17.0108],
    "Dubicko": [49.8267, 16.9647],
    "Bohuslavice": [49.8208, 16.9400],
    "Lukavice": [49.8236, 16.9158],
    "Třeština": [49.7967, 16.9639],
    "Krchleby": [49.8117, 16.8322],
    "Podolí": [49.7664, 16.8844],
    "Pískov": [49.8058, 17.0794],
    "Veleboř": [49.8250, 17.0406],
    "Rohle": [49.8639, 17.0233],
    "Studená Loučka": [49.7611, 16.8269],
    "Mírov": [49.7928, 16.8508],
    "Palonín": [49.7367, 16.9739],
    "Zvole": [49.8414, 16.9169],
    "Stavenice": [49.7775, 16.9817],
    "Červená Lhota": [49.7136, 17.0156],
    "Bílá Lhota": [49.7103, 16.9761],
    "Slavoňov": [49.8017, 16.9061],
    "Řimice": [49.7186, 17.0144],
    "Klopina": [49.8225, 17.0308],
    "Police": [49.8147, 17.0014],
    "Medlov": [49.7844, 17.0644],
    "Mladeč": [49.7061, 17.0169],
    "Zábřeh": [49.8825, 16.8722],
    "Litovel": [49.7011, 17.0761],
    "Uničov": [49.7708, 17.1214],
    "Šumperk": [49.9653, 16.9706],
    "Olomouc": [49.5938, 17.2509],
    "Šternberk": [49.7306, 17.2989],
    "Konice": [49.5892, 16.8886],
    "Moravská Třebová": [49.7578, 16.6642],
    "prostějov": [49.4719, 17.1118],
    "Prostějov": [49.4719, 17.1118],
    "Lanškroun": [49.9122, 16.6119],
    "Náklo": [49.6542, 17.1292],
    "Chromeč": [49.9469, 16.8719],
    "Rájec": [49.8453, 16.8856],
    "Postřelmov": [49.9078, 16.9061],
    "Bludov": [49.9428, 16.9297],
    "Ruda nad Moravou": [49.9839, 16.8764],
    "Sudkov": [49.9114, 16.9425],
    "Lesnice": [49.8736, 16.9453],
    "Leština": [49.8742, 16.9233],
    "Brníčko": [49.8950, 16.9736],
    "Kolšov": [49.9039, 16.9250],
    "Nemile": [49.8808, 16.8358],
    "Červenka": [49.7219, 17.0811],
    "Senice na Hané": [49.6228, 17.0878],
    "Cholina": [49.6611, 17.0542],
    "Bílsko": [49.6472, 17.0369],
    "Haňovice": [49.6797, 17.0439],
    "Pňovice": [49.7192, 17.1636],
    "Střeň": [49.6964, 17.1528],
    "Příkazy": [49.6361, 17.1450],
    "Horka nad Moravou": [49.6400, 17.2094],
    "Štěpánov": [49.6850, 17.2208],
    "Bouzov": [49.7042, 16.8894],
    "Hvozd": [49.6317, 16.9178],
    "Luká": [49.6478, 16.9467]
};

// DOM prvky
const searchInput = document.getElementById('searchInput');
const clearSearchBtn = document.getElementById('clearSearch');
const filterGenderEl = document.getElementById('filterGender');
const filterTownInput = document.getElementById('filterTownInput');
const clearTownBtn = document.getElementById('clearTownBtn');
const gpsTownBtn = document.getElementById('gpsTownBtn');
const townSuggestionsEl = document.getElementById('townSuggestions');
const townComboboxWrap = document.getElementById('townComboboxWrap');
const filterRadiusEl = document.getElementById('filterRadius');
const filterSourceEl = document.getElementById('filterSource');
const hideMemoriesToggle = document.getElementById('hideMemoriesToggle');
const resetFiltersBtn = document.getElementById('resetFiltersBtn');
const activeFilterSummary = document.getElementById('activeFilterSummary');

const sortByNameTh = document.getElementById('sortByName');
const sortByDateTh = document.getElementById('sortByDate');
const sortNameIcon = document.getElementById('sortNameIcon');
const sortDateIcon = document.getElementById('sortDateIcon');

const navTabs = document.querySelectorAll('.nav-tab');
const tableBody = document.getElementById('tableBody');
const sectionTitle = document.getElementById('sectionTitle');
const statsBadge = document.getElementById('statsBadge');
const emptyState = document.getElementById('emptyState');
const refreshBtn = document.getElementById('refreshBtn');
const lastUpdatedEl = document.getElementById('lastUpdated');

// Počítadla
const countActive = document.getElementById('countActive');
const countArchive = document.getElementById('countArchive');

// Modal prvky
const modal = document.getElementById('imageModal');
const modalTitle = document.getElementById('modalTitle');
const modalTownSubtitle = document.getElementById('modalTownSubtitle');
const modalImg = document.getElementById('modalImg');
const modalDetails = document.getElementById('modalDetails');
const modalCloseBtn = document.getElementById('modalCloseBtn');
const modalBackdrop = document.getElementById('modalBackdrop');
const modalShareBtn = document.getElementById('modalShareBtn');
const modalDownloadLink = document.getElementById('modalDownloadLink');

function normalizeStr(s) {
    return (s || '')
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .trim();
}

// Výpočet vzdušné vzdálenosti dvou GPS bodů (Haversine v km)
function getDistanceKm(lat1, lon1, lat2, lon2) {
    if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return null;
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

function getItemCoords(item) {
    if (item.lat != null && item.lon != null) {
        return [item.lat, item.lon];
    }
    if (item.residence && TOWN_COORDS[item.residence]) {
        return TOWN_COORDS[item.residence];
    }
    return null;
}

function findMatchingKnownTown(query) {
    if (!query || query === 'all' || query === '__gps__') return null;
    const normQ = normalizeStr(query);
    if (!normQ) return null;

    // 1. Přesná shoda bez diakritiky
    for (const town of allKnownTowns) {
        if (normalizeStr(town) === normQ) return town;
    }
    // 2. Začíná na zadaný text (např. "lost" -> "Loštice")
    for (const town of allKnownTowns) {
        if (normalizeStr(town).startsWith(normQ)) return town;
    }
    return null;
}

function getFilterCenterCoords() {
    if (filterTown === 'all' || !filterTown) return null;
    if (filterTown === '__gps__') return userGpsCoords;
    if (TOWN_COORDS[filterTown]) return TOWN_COORDS[filterTown];
    if (customTownCoords[filterTown]) return customTownCoords[filterTown];

    const matchedTown = findMatchingKnownTown(filterTown);
    if (matchedTown) {
        if (TOWN_COORDS[matchedTown]) return TOWN_COORDS[matchedTown];
        for (const it of allItems) {
            if (it.residence === matchedTown && it.lat != null && it.lon != null) {
                return [it.lat, it.lon];
            }
        }
    }

    for (const it of allItems) {
        if (it.residence === filterTown && it.lat != null && it.lon != null) {
            return [it.lat, it.lon];
        }
    }
    return null;
}

// Naplnění seznamu měst pro našeptávač a seznamu zdrojů
function populateFilterDropdowns(items) {
    const townsSet = new Set(Object.keys(TOWN_COORDS).filter(t => t[0] === t[0].toUpperCase()));
    items.forEach(it => {
        if (it.residence) townsSet.add(it.residence);
    });
    allKnownTowns = Array.from(townsSet).sort((a, b) => a.localeCompare(b, 'cs'));

    // Zdroje parte
    const currentSelectedSource = filterSourceEl.value;
    const sourcesSet = new Set(['Horníček Moravičany', 'Hrandop Litovel', 'Drozd Zábřeh']);
    items.forEach(it => {
        if (it.source_name) sourcesSet.add(it.source_name);
    });
    filterSourceEl.innerHTML = `<option value="all">Všechny zdroje</option>`;
    Array.from(sourcesSet).forEach(src => {
        const opt = document.createElement('option');
        opt.value = src;
        opt.textContent = src;
        filterSourceEl.appendChild(opt);
    });
    if (Array.from(filterSourceEl.options).some(o => o.value === currentSelectedSource)) {
        filterSourceEl.value = currentSelectedSource;
    }
}

// Vykreslení rozbalovacího našeptávače měst
function renderTownSuggestions(queryText, extraRemoteItems = []) {
    const normQ = normalizeStr(queryText);
    const matchingLocal = allKnownTowns.filter(t => !normQ || normalizeStr(t).includes(normQ));

    let html = '';
    if (!normQ) {
        html += `
            <div class="town-suggestion-item" data-value="all">
                <span>🌍 Všechna města</span>
                <span class="town-suggestion-badge">Zrušit filtr</span>
            </div>
            <div class="town-suggestion-item" data-value="__gps__">
                <span>📍 Moje aktuální poloha (GPS)</span>
                <span class="town-suggestion-badge">GPS</span>
            </div>
        `;
    }

    matchingLocal.slice(0, 18).forEach(town => {
        const count = allItems.filter(i => i.residence === town).length;
        html += `
            <div class="town-suggestion-item" data-value="${escapeHtml(town)}">
                <span>📍 ${escapeHtml(town)}</span>
                ${count > 0 ? `<span class="town-suggestion-badge">${count} parte</span>` : `<span class="town-suggestion-badge">mapa</span>`}
            </div>
        `;
    });

    extraRemoteItems.forEach(rem => {
        if (!matchingLocal.some(t => normalizeStr(t) === normalizeStr(rem.name))) {
            html += `
                <div class="town-suggestion-item" data-value="${escapeHtml(rem.name)}" data-lat="${rem.lat}" data-lon="${rem.lon}">
                    <span>🔎 ${escapeHtml(rem.label)}</span>
                    <span class="town-suggestion-badge">ČR GPS</span>
                </div>
            `;
        }
    });

    if (!html) {
        html = `<div class="town-suggestion-item" style="pointer-events:none;color:var(--text-muted);">Hledám obec „${escapeHtml(queryText)}“...</div>`;
    }

    townSuggestionsEl.innerHTML = html;
    townSuggestionsEl.style.display = 'block';

    townSuggestionsEl.querySelectorAll('.town-suggestion-item[data-value]').forEach(el => {
        el.addEventListener('mousedown', (e) => {
            e.preventDefault(); // Zabránit blur před kliknutím
            const val = el.getAttribute('data-value');
            const lat = el.getAttribute('data-lat');
            const lon = el.getAttribute('data-lon');
            if (lat && lon) {
                customTownCoords[val] = [parseFloat(lat), parseFloat(lon)];
            }
            selectTownFilter(val);
        });
    });
}

function selectTownFilter(val) {
    townSuggestionsEl.style.display = 'none';
    if (val === 'all' || !val) {
        filterTown = 'all';
        filterTownInput.value = '';
        clearTownBtn.classList.remove('visible');
        renderTable();
        return;
    }

    if (val === '__gps__') {
        triggerGpsLocation();
        return;
    }

    filterTown = val;
    filterTownInput.value = val;
    clearTownBtn.classList.add('visible');
    renderTable();
}

function triggerGpsLocation() {
    if (!navigator.geolocation) {
        alert('Váš prohlížeč nepodporuje zjištění polohy GPS.');
        return;
    }
    filterTownInput.value = '📍 Zjišťuji polohu...';
    navigator.geolocation.getCurrentPosition(
        (pos) => {
            userGpsCoords = [pos.coords.latitude, pos.coords.longitude];
            filterTown = '__gps__';
            filterTownInput.value = '📍 Moje poloha (GPS)';
            clearTownBtn.classList.add('visible');
            if (filterRadiusKm === 0) {
                filterRadiusKm = 20;
                filterRadiusEl.value = '20';
            }
            townSuggestionsEl.style.display = 'none';
            renderTable();
        },
        () => {
            alert('Nepodařilo se získat vaši polohu. Napište prosím město do pole.');
            filterTown = 'all';
            filterTownInput.value = '';
            clearTownBtn.classList.remove('visible');
            renderTable();
        }
    );
}

// Načtení databáze
async function fetchDatabase() {
    statsBadge.textContent = 'Načítám data...';
    const candidateUrls = [
        'data/database.json',
        './data/database.json',
        '../data/database.json'
    ];

    let loadedData = null;
    for (const url of candidateUrls) {
        try {
            const resp = await fetch(url + '?nocache=' + Date.now());
            if (resp.ok) {
                loadedData = await resp.json();
                break;
            }
        } catch (e) {}
    }

    if (loadedData) {
        allItems = loadedData;
        populateFilterDropdowns(allItems);
        updateCounts();
        updateLastSeen(allItems);
        renderTable();
    } else {
        statsBadge.textContent = 'Chyba při načítání databáze';
    }
}

// Zobrazení času poslední aktualizace
function updateLastSeen(items) {
    let latest = null;
    for (const it of items) {
        const timeStr = it.last_seen || it.first_seen;
        if (timeStr) {
            const d = new Date(timeStr.replace(' ', 'T'));
            if (!latest || d > latest) latest = d;
        }
    }
    if (latest && !isNaN(latest.getTime())) {
        const dateFormatted = latest.toLocaleDateString('cs-CZ', {
            day: 'numeric',
            month: 'numeric',
            year: 'numeric'
        });
        const timeFormatted = latest.toLocaleTimeString('cs-CZ', {
            hour: '2-digit',
            minute: '2-digit'
        });
        if (lastUpdatedEl) {
            lastUpdatedEl.innerHTML = `🕒 Aktualizováno: <strong>${dateFormatted} v ${timeFormatted}</strong>`;
        }
    }
}

function updateCounts() {
    const activeCount = allItems.filter(i => i.is_active && (!hideMemories || i.category !== 'Vzpomínka')).length;
    const archiveCount = allItems.filter(i => i.is_archived && (!hideMemories || i.category !== 'Vzpomínka')).length;
    countActive.textContent = activeCount;
    countArchive.textContent = archiveCount;
}

function formatGenderSymbol(gender) {
    if (gender === 'F') return { symbol: '♀', cls: 'gender-f', label: 'Žena' };
    if (gender === 'M') return { symbol: '♂', cls: 'gender-m', label: 'Muž' };
    if (gender === 'MF') return { symbol: '♀♂', cls: 'gender-f', label: 'Manželé' };
    return { symbol: '', cls: '', label: '' };
}

function getFilteredAndSortedItems() {
    const centerCoords = getFilterCenterCoords();

    const filtered = allItems.filter(item => {
        // 1. Záložka: Aktuální vývěska vs Archiv
        if (currentTab === 'active' && !item.is_active) return false;
        if (currentTab === 'archive' && !item.is_archived) return false;

        // 2. Skrýt vzpomínky
        if (hideMemories && item.category === 'Vzpomínka') return false;

        // 3. Filtr pohlaví
        if (filterGender !== 'all') {
            const g = item.gender || '';
            if (g !== filterGender && g !== 'MF') return false;
        }

        // 4. Filtr zdroje parte
        if (filterSource !== 'all') {
            const src = item.source_name || 'Horníček Moravičany';
            if (src !== filterSource) return false;
        }

        // 5. Filtr města a vzdálenosti (okolí v km)
        if (filterTown !== 'all' && filterTown) {
            if (filterRadiusKm === 0 && filterTown !== '__gps__') {
                const normItemTown = normalizeStr(item.residence);
                const normFilterTown = normalizeStr(filterTown);
                if (!normItemTown.includes(normFilterTown)) return false;
            } else if (centerCoords) {
                const itemCoords = getItemCoords(item);
                if (!itemCoords) return false;
                const dist = getDistanceKm(centerCoords[0], centerCoords[1], itemCoords[0], itemCoords[1]);
                if (dist == null || dist > filterRadiusKm) return false;
            } else {
                // Pokud uživatel píše město, pro které ještě nejsou souřadnice, filtruj podle názvu obce
                const normItemTown = normalizeStr(item.residence);
                const normFilterTown = normalizeStr(filterTown);
                if (!normItemTown.includes(normFilterTown)) return false;
            }
        }

        // 6. Textové vyhledávání
        if (currentSearch) {
            const q = currentSearch.toLowerCase();
            const matchName = (item.name || '').toLowerCase().includes(q);
            const matchResidence = (item.residence || '').toLowerCase().includes(q);
            const matchAge = (item.age || '').toLowerCase().includes(q);
            const matchDeath = (item.death_date || '').toLowerCase().includes(q);
            const matchCause = (item.death_cause || '').toLowerCase().includes(q);
            const matchCeremony = (item.ceremony || '').toLowerCase().includes(q);
            if (!matchName && !matchResidence && !matchAge && !matchDeath && !matchCause && !matchCeremony) {
                return false;
            }
        }

        return true;
    });

    // Řazení (výchozí podle data úmrtí od posledních / nejnovějších)
    filtered.sort((a, b) => {
        if (sortField === 'name') {
            const cmp = (a.name || '').localeCompare(b.name || '', 'cs');
            return sortDesc ? -cmp : cmp;
        } else {
            const da = a.death_date_iso || '0000-00-00';
            const db = b.death_date_iso || '0000-00-00';
            if (da !== db) {
                return sortDesc ? db.localeCompare(da) : da.localeCompare(db);
            }
            return (a.name || '').localeCompare(b.name || '', 'cs');
        }
    });

    return filtered;
}

function updateFilterUiState() {
    const anyFilterActive =
        filterGender !== 'all' ||
        filterTown !== 'all' ||
        filterSource !== 'all' ||
        hideMemories ||
        currentSearch.length > 0;

    resetFiltersBtn.style.display = anyFilterActive ? 'inline-flex' : 'none';
    filterRadiusEl.disabled = (filterTown === 'all' || !filterTown);

    const parts = [];
    if (filterGender === 'F') parts.push('♀ Ženy');
    if (filterGender === 'M') parts.push('♂ Muži');
    if (filterTown !== 'all' && filterTown) {
        const matched = findMatchingKnownTown(filterTown);
        const townLabel = filterTown === '__gps__' ? 'Moje poloha' : (matched || filterTown);
        parts.push(filterRadiusKm > 0 ? `${townLabel} + okolí ${filterRadiusKm} km` : townLabel);
    }
    if (filterSource !== 'all') parts.push(filterSource);
    if (hideMemories) parts.push('bez vzpomínek');

    activeFilterSummary.textContent = parts.length > 0 ? `Aktivní filtr: ${parts.join(' • ')}` : '';
}

// Vykreslení tabulky
function renderTable() {
    updateCounts();
    updateFilterUiState();

    const items = getFilteredAndSortedItems();
    const centerCoords = getFilterCenterCoords();
    tableBody.innerHTML = '';

    const isCurrentActive = currentTab === 'active';
    sectionTitle.textContent = isCurrentActive ? 'Aktuální smuteční oznámení (Vývěska)' : 'Archiv parte';
    statsBadge.textContent = `${items.length} ${getNoun(items.length, 'záznam', 'záznamy', 'záznamů')}`;

    if (items.length === 0) {
        emptyState.style.display = 'block';
        return;
    }

    emptyState.style.display = 'none';

    items.forEach(item => {
        const imgSrc = item.local_image_path || item.release_image_url || item.image_url;
        const isMemory = item.category === 'Vzpomínka';
        const badgeClass = isMemory ? 'badge-memory' : 'badge-announcement';

        const gInfo = formatGenderSymbol(item.gender);
        const ageSuffix = item.age ? ` (${item.age})` : '';
        const dateText = item.death_date ? item.death_date : '–';
        const causeText = item.death_cause ? item.death_cause : '–';
        const ceremonyText = item.ceremony ? item.ceremony : '–';
        const sourceName = item.source_name || 'Horníček Moravičany';

        // Výpočet vzdálenosti od zvoleného města/polohy
        let distBadgeHtml = '';
        if (filterTown !== 'all' && centerCoords) {
            const itemCoords = getItemCoords(item);
            if (itemCoords) {
                const dist = getDistanceKm(centerCoords[0], centerCoords[1], itemCoords[0], itemCoords[1]);
                if (dist != null) {
                    const rounded = dist < 1 ? '0 km' : `${Math.round(dist)} km`;
                    distBadgeHtml = `<span class="town-dist">(${rounded})</span>`;
                }
            }
        }

        // Fotografie obličeje pod jménem (lokální + záloha v GitHub Releases)
        const facePrimary = item.face_image_path || item.release_face_url;
        const faceFallback = (item.face_image_path && item.release_face_url) ? item.release_face_url : '';
        const faceHtml = facePrimary
            ? `<img src="${escapeHtml(facePrimary)}" data-fallback="${escapeHtml(faceFallback)}" alt="Portrét ${escapeHtml(item.name)}" class="face-portrait" loading="lazy" onerror="if(this.dataset.fallback && this.src !== this.dataset.fallback){this.src=this.dataset.fallback;}else{this.style.display='none';}">`
            : '';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>
                <div class="cell-name-box">
                    <div class="cell-name-line">
                        ${gInfo.symbol ? `<span class="gender-symbol ${gInfo.cls}" title="${gInfo.label}">${gInfo.symbol}</span>` : ''}
                        <span class="cell-name">${escapeHtml(item.name)}</span>
                        ${ageSuffix ? `<span class="name-age">${escapeHtml(ageSuffix)}</span>` : ''}
                    </div>
                    <div class="cell-under-name">
                        ${faceHtml}
                        <div class="cell-sub">
                            ${item.residence ? `<span class="cell-town-under-name">📍 ${escapeHtml(item.residence)} ${distBadgeHtml}</span>` : ''}
                            <span class="badge ${badgeClass}">${escapeHtml(item.category)}</span>
                            <span class="badge badge-source" title="Zdroj parte">${escapeHtml(sourceName)}</span>
                        </div>
                    </div>
                </div>
            </td>
            <td class="cell-date">${escapeHtml(dateText)}</td>
            <td class="cell-cause">${escapeHtml(causeText)}</td>
            <td class="cell-ceremony">${escapeHtml(ceremonyText)}</td>
            <td class="cell-action">
                <button class="btn-open-parte" title="Otevřít celé parte">
                    <span>📄 Detail</span>
                </button>
            </td>
        `;

        tr.addEventListener('click', () => openModal(item, imgSrc));
        tableBody.appendChild(tr);
    });
}

function openModal(item, imgSrc) {
    activeModalItem = item;
    const gInfo = formatGenderSymbol(item.gender);
    const ageSuffix = item.age ? ` (${item.age})` : '';
    modalTitle.textContent = `${gInfo.symbol ? gInfo.symbol + ' ' : ''}${item.name}${ageSuffix}`;
    modalTownSubtitle.textContent = item.residence ? `📍 Bydliště: ${item.residence} • ${item.category}` : item.category;
    
    modalImg.onerror = () => {
        if (item.release_image_url && modalImg.src !== item.release_image_url) {
            modalImg.src = item.release_image_url;
        } else if (item.image_url && modalImg.src !== item.image_url) {
            modalImg.src = item.image_url;
        }
    };
    modalImg.src = imgSrc;
    modalImg.classList.remove('zoomed');
    modalDownloadLink.href = item.release_image_url || imgSrc;
    modalDownloadLink.download = `parte_${sanitizeFilename(item.name)}.jpg`;

    let detailsHtml = '';
    if (item.residence) {
        detailsHtml += `<div class="meta-field"><strong>Město / Bydliště:</strong> ${escapeHtml(item.residence)}</div>`;
    }
    if (item.age) {
        detailsHtml += `<div class="meta-field"><strong>Věk:</strong> ${escapeHtml(item.age)}</div>`;
    }
    if (item.death_date) {
        detailsHtml += `<div class="meta-field"><strong>Datum úmrtí:</strong> ${escapeHtml(item.death_date)}</div>`;
    }
    if (item.death_cause) {
        detailsHtml += `<div class="meta-field"><strong>Důvod / Okolnost:</strong> ${escapeHtml(item.death_cause)}</div>`;
    }
    if (item.source_name) {
        detailsHtml += `<div class="meta-field"><strong>Zdroj parte:</strong> ${escapeHtml(item.source_name)}</div>`;
    }
    if (item.ceremony) {
        detailsHtml += `<div class="meta-field" style="grid-column: span 2;"><strong>Poslední rozloučení:</strong> ${escapeHtml(item.ceremony)}</div>`;
    }
    detailsHtml += `<div class="meta-field" style="grid-column: span 2;"><strong>Původní odkaz:</strong> <a href="${item.image_url}" target="_blank" rel="noopener" style="color:var(--gold-light);">Otevřít originál na webu pohřební služby ↗</a></div>`;

    modalDetails.innerHTML = detailsHtml;
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeModal() {
    modal.classList.remove('active');
    document.body.style.overflow = '';
    modalImg.classList.remove('zoomed');
    activeModalItem = null;
}

modalImg.addEventListener('click', () => {
    modalImg.classList.toggle('zoomed');
});

modalCloseBtn.addEventListener('click', closeModal);
modalBackdrop.addEventListener('click', closeModal);
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closeModal();
        townSuggestionsEl.style.display = 'none';
    }
});

modalShareBtn.addEventListener('click', async () => {
    if (!activeModalItem) return;
    const shareData = {
        title: `Parte - ${activeModalItem.name}`,
        text: `Parte: ${activeModalItem.name} (${activeModalItem.residence || ''})\nDatum úmrtí: ${activeModalItem.death_date || ''}\nPohřeb: ${activeModalItem.ceremony || ''}`,
        url: window.location.href
    };

    if (navigator.share) {
        try {
            await navigator.share(shareData);
        } catch (err) {}
    } else {
        navigator.clipboard.writeText(window.location.href);
        alert('Odkaz na parte byl zkopírován do schránky.');
    }
});

// Přepínání záložek Aktuální vývěska vs Archiv parte
navTabs.forEach(tab => {
    tab.addEventListener('click', () => {
        navTabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        currentTab = tab.dataset.tab;
        renderTable();
    });
});

// Události filtrů
filterGenderEl.addEventListener('change', (e) => {
    filterGender = e.target.value;
    renderTable();
});

// Našeptávač a psaní do kolonky Město
filterTownInput.addEventListener('focus', () => {
    const q = filterTown === '__gps__' ? '' : filterTownInput.value.trim();
    renderTownSuggestions(q);
});

filterTownInput.addEventListener('input', (e) => {
    const val = e.target.value.trim();
    clearTownBtn.classList.toggle('visible', val.length > 0);

    if (!val) {
        filterTown = 'all';
        renderTownSuggestions('');
        renderTable();
        return;
    }

    filterTown = val;
    renderTownSuggestions(val);
    renderTable();

    // Pokud obec není v lokálním seznamu a má aspoň 3 znaky, dohledáme její GPS v ČR přes OpenStreetMap Nominatim
    if (geocodeTimeout) clearTimeout(geocodeTimeout);
    if (val.length >= 3 && !findMatchingKnownTown(val)) {
        geocodeTimeout = setTimeout(async () => {
            try {
                const url = `https://nominatim.openstreetmap.org/search?format=json&countrycodes=cz&limit=4&q=${encodeURIComponent(val)}`;
                const resp = await fetch(url, { headers: { 'Accept-Language': 'cs' } });
                if (resp.ok) {
                    const data = await resp.json();
                    const remoteItems = data.map(d => {
                        const shortName = (d.display_name || '').split(',')[0].trim();
                        return {
                            name: shortName,
                            label: (d.display_name || '').split(',').slice(0, 2).join(', '),
                            lat: parseFloat(d.lat),
                            lon: parseFloat(d.lon)
                        };
                    });
                    if (remoteItems.length > 0 && !customTownCoords[val]) {
                        customTownCoords[val] = [remoteItems[0].lat, remoteItems[0].lon];
                        renderTable();
                    }
                    if (document.activeElement === filterTownInput) {
                        renderTownSuggestions(val, remoteItems);
                    }
                }
            } catch (err) {}
        }, 320);
    }
});

filterTownInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        e.preventDefault();
        const firstItem = townSuggestionsEl.querySelector('.town-suggestion-item[data-value]');
        if (firstItem && townSuggestionsEl.style.display !== 'none') {
            const val = firstItem.getAttribute('data-value');
            const lat = firstItem.getAttribute('data-lat');
            const lon = firstItem.getAttribute('data-lon');
            if (lat && lon) {
                customTownCoords[val] = [parseFloat(lat), parseFloat(lon)];
            }
            selectTownFilter(val);
        } else {
            townSuggestionsEl.style.display = 'none';
        }
        filterTownInput.blur();
    }
});

document.addEventListener('click', (e) => {
    if (townComboboxWrap && !townComboboxWrap.contains(e.target)) {
        townSuggestionsEl.style.display = 'none';
    }
});

clearTownBtn.addEventListener('click', () => {
    selectTownFilter('all');
    filterTownInput.focus();
});

gpsTownBtn.addEventListener('click', () => {
    triggerGpsLocation();
});

filterRadiusEl.addEventListener('change', (e) => {
    filterRadiusKm = parseInt(e.target.value, 10) || 0;
    renderTable();
});

filterSourceEl.addEventListener('change', (e) => {
    filterSource = e.target.value;
    renderTable();
});

hideMemoriesToggle.addEventListener('change', (e) => {
    hideMemories = e.target.checked;
    renderTable();
});

resetFiltersBtn.addEventListener('click', () => {
    filterGender = 'all';
    filterTown = 'all';
    filterRadiusKm = 20;
    filterSource = 'all';
    hideMemories = false;
    currentSearch = '';

    filterGenderEl.value = 'all';
    filterTownInput.value = '';
    clearTownBtn.classList.remove('visible');
    townSuggestionsEl.style.display = 'none';
    filterRadiusEl.value = '20';
    filterSourceEl.value = 'all';
    hideMemoriesToggle.checked = false;
    searchInput.value = '';
    clearSearchBtn.classList.remove('visible');
    renderTable();
});

// Řazení kliknutím na záhlaví sloupce
function updateSortHeaders() {
    sortByNameTh.classList.toggle('active-sort', sortField === 'name');
    sortByDateTh.classList.toggle('active-sort', sortField === 'date');
    sortNameIcon.textContent = sortField === 'name' ? (sortDesc ? '↓' : '↑') : '';
    sortDateIcon.textContent = sortField === 'date' ? (sortDesc ? '↓' : '↑') : '';
}

sortByDateTh.addEventListener('click', () => {
    if (sortField === 'date') {
        sortDesc = !sortDesc;
    } else {
        sortField = 'date';
        sortDesc = true;
    }
    updateSortHeaders();
    renderTable();
});

sortByNameTh.addEventListener('click', () => {
    if (sortField === 'name') {
        sortDesc = !sortDesc;
    } else {
        sortField = 'name';
        sortDesc = false;
    }
    updateSortHeaders();
    renderTable();
});

searchInput.addEventListener('input', (e) => {
    currentSearch = e.target.value.trim();
    clearSearchBtn.classList.toggle('visible', currentSearch.length > 0);
    renderTable();
});

clearSearchBtn.addEventListener('click', () => {
    searchInput.value = '';
    currentSearch = '';
    clearSearchBtn.classList.remove('visible');
    renderTable();
});

refreshBtn.addEventListener('click', fetchDatabase);

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#039;');
}

function sanitizeFilename(name) {
    return (name || 'parte').replace(/[^a-zA-Z0-9_\u00C0-\u017F]/g, '_');
}

function getNoun(number, one, two, five) {
    let n = Math.abs(number);
    n %= 100;
    if (n >= 5 && n <= 20) return five;
    n %= 10;
    if (n === 1) return one;
    if (n >= 2 && n <= 4) return two;
    return five;
}

// Zrušení starých service workerů a vyčištění staré cache prohlížeče
if ('serviceWorker' in navigator) {
    navigator.serviceWorker.getRegistrations().then(regs => {
        for (let r of regs) r.unregister();
    });
    if ('caches' in window) {
        caches.keys().then(keys => {
            keys.forEach(k => caches.delete(k));
        });
    }
}

fetchDatabase();
