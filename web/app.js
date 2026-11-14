// Smuteční vývěska - Klientská aplikace
let allItems = [];
let currentTab = 'active'; // Výchozí záložka: Aktuální vývěska
let currentSearch = '';
let filterGender = 'all';
let filterTown = 'all'; // 'all', '__gps__', nebo název města / vlastní zadání
let filterRadiusKm = 20;
let filterSource = 'all';
let hideMemories = true; // Výchozí: vzpomínky skryté (zaškrtnutím se zobrazí)
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

// Počítadla a pohledy
const countActive = document.getElementById('countActive');
const countArchive = document.getElementById('countArchive');
const countStats = document.getElementById('countStats');
const tableContainer = document.getElementById('tableContainer');
const statsView = document.getElementById('statsView');
const statsKpiGrid = document.getElementById('statsKpiGrid');
const statsTownTableBody = document.getElementById('statsTownTableBody');
const statsScopeInfo = document.getElementById('statsScopeInfo');
let statsTimeGranularity = 'week'; // 'week' | 'day' | 'month'
let activeCharts = {};

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

// Pravidlo archivace: parte je aktuální po dobu 1 týdne (7 dní) od data úmrtí,
// případně od data prvního stažení (pokud datum úmrtí na parte chybí).
function applySevenDayArchivingRule(items) {
    const now = new Date();
    const refTodayUtc = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());

    items.forEach(item => {
        let refIso = (item.death_date_iso || '').trim();
        if (!refIso && item.first_seen && item.first_seen.length >= 10) {
            refIso = item.first_seen.slice(0, 10);
        }
        if (/^\d{4}-\d{2}-\d{2}$/.test(refIso)) {
            const [y, m, d] = refIso.split('-').map(Number);
            const itemUtc = Date.UTC(y, m - 1, d);
            const diffDays = Math.floor((refTodayUtc - itemUtc) / (1000 * 60 * 60 * 24));
            // Parte je aktuální 7 dní od úmrtí / stažení
            const isRecent = diffDays <= 7;
            item.is_active = isRecent;
            item.is_archived = !isRecent;
        }
    });
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
        try {
            const ovResp = await fetch('https://res.cloudinary.com/pf9pdwlp/raw/upload/smutecni-vyveska/overrides.json?nocache=' + Date.now());
            if (ovResp.ok) {
                const overrides = await ovResp.json();
                if (overrides && typeof overrides === 'object') {
                    loadedData.forEach(item => {
                        const patch = overrides[item.id];
                        if (patch && typeof patch === 'object') {
                            ['name', 'gender', 'category', 'age', 'death_date', 'death_date_iso', 'death_cause', 'residence', 'lat', 'lon', 'ceremony', 'manual_edit'].forEach(k => {
                                if (k in patch) {
                                    item[k] = patch[k];
                                }
                            });
                        }
                    });
                }
            }
        } catch (e) {}
        allItems = loadedData;
        applySevenDayArchivingRule(allItems);
        populateFilterDropdowns(allItems);
        updateCounts();
        updateLastSeen(allItems);
        renderTable();
        checkNewObituariesAndNotify(allItems);
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
    const statsCount = allItems.filter(i => i.category !== 'Vzpomínka').length;
    countActive.textContent = activeCount;
    countArchive.textContent = archiveCount;
    if (countStats) countStats.textContent = statsCount;
}

function formatGenderSymbol(gender) {
    if (gender === 'F') return { symbol: '♀', cls: 'gender-f', label: 'Žena' };
    if (gender === 'M') return { symbol: '♂', cls: 'gender-m', label: 'Muž' };
    if (gender === 'MF') return { symbol: '♀♂', cls: 'gender-f', label: 'Manželé' };
    return { symbol: '', cls: '', label: '' };
}

function getFilteredAndSortedItems(ignoreTab = false) {
    const centerCoords = getFilterCenterCoords();

    const filtered = allItems.filter(item => {
        // 1. Záložka: Aktuální vývěska vs Archiv (ve statistice bereme všechna období dohromady)
        if (!ignoreTab && currentTab !== 'stats') {
            if (currentTab === 'active' && !item.is_active) return false;
            if (currentTab === 'archive' && !item.is_archived) return false;
        }

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
            const da = a.death_date_iso || (a.first_seen ? a.first_seen.slice(0, 10) : '0000-00-00');
            const db = b.death_date_iso || (b.first_seen ? b.first_seen.slice(0, 10) : '0000-00-00');
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
        !hideMemories ||
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
    if (!hideMemories) parts.push('včetně vzpomínek');

    activeFilterSummary.textContent = parts.length > 0 ? `Aktivní filtr: ${parts.join(' • ')}` : '';

    const mobileFilterActiveBadge = document.getElementById('mobileFilterActiveBadge');
    if (mobileFilterActiveBadge) {
        mobileFilterActiveBadge.style.display = anyFilterActive ? 'inline-block' : 'none';
        mobileFilterActiveBadge.textContent = parts.length > 0 ? `${parts.length} aktivní` : 'Aktivní';
    }
}

// Vykreslení tabulky nebo statistického přehledu
function renderTable() {
    updateCounts();
    updateFilterUiState();

    if (currentTab === 'stats') {
        if (tableContainer) tableContainer.style.display = 'none';
        emptyState.style.display = 'none';
        if (statsView) statsView.style.display = 'flex';
        sectionTitle.textContent = 'Statistika a grafický přehled v čase';
        renderStatisticsView();
        return;
    }

    if (statsView) statsView.style.display = 'none';
    if (tableContainer) tableContainer.style.display = 'block';

    const items = getFilteredAndSortedItems();
    const centerCoords = getFilterCenterCoords();
    tableBody.innerHTML = '';

    const isCurrentActive = currentTab === 'active';
    sectionTitle.textContent = isCurrentActive
        ? 'Aktuální smuteční oznámení (Vývěska) – aktuální po dobu 1 týdne od úmrtí'
        : 'Archiv parte (starší než 1 týden od úmrtí)';
    statsBadge.textContent = `${items.length} ${getNoun(items.length, 'záznam', 'záznamy', 'záznamů')}`;

    if (items.length === 0) {
        emptyState.style.display = 'block';
        return;
    }

    emptyState.style.display = 'none';

    items.forEach(item => {
        const isCloudImg = item.release_image_url && item.release_image_url.startsWith('https://res.cloudinary.com/');
        const imgSrc = isCloudImg ? item.release_image_url : (item.local_image_path || item.release_image_url || item.image_url);
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

        // Fotografie obličeje pod jménem (primárně Cloudinary + lokální záloha)
        const isCloudFace = item.release_face_url && item.release_face_url.startsWith('https://res.cloudinary.com/');
        const facePrimary = isCloudFace ? item.release_face_url : (item.face_image_path || item.release_face_url);
        const faceFallback = isCloudFace ? (item.face_image_path || '') : (item.release_face_url || '');
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
            <td class="cell-date" data-label="Datum úmrtí:">${escapeHtml(dateText)}</td>
            <td class="cell-cause${causeText === '–' ? ' cell-empty-mobile' : ''}" data-label="Důvod / Okolnost:">${escapeHtml(causeText)}</td>
            <td class="cell-ceremony${ceremonyText === '–' ? ' cell-empty-mobile' : ''}" data-label="Pohřeb:">${escapeHtml(ceremonyText)}</td>
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
    hideMemories = !e.target.checked;
    renderTable();
});

resetFiltersBtn.addEventListener('click', () => {
    filterGender = 'all';
    filterTown = 'all';
    filterRadiusKm = 20;
    filterSource = 'all';
    hideMemories = true;
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

// ==================== STATISTIKA A GRAFY V ČASE ====================

const CZECH_MONTHS_MAP = {
    'ledna': 1, 'unora': 2, 'února': 2, 'brezna': 3, 'března': 3,
    'dubna': 4, 'kvetna': 5, 'května': 5, 'cervna': 6, 'června': 6,
    'cervence': 7, 'července': 7, 'srpna': 8, 'zari': 9, 'září': 9,
    'rijna': 10, 'října': 10, 'listopadu': 11, 'prosince': 12
};

function parseAgeNumber(item) {
    if (!item || !item.age) return null;
    const m = String(item.age).match(/(\d{1,3})/);
    if (!m) return null;
    const val = parseInt(m[1], 10);
    return (val >= 0 && val <= 115) ? val : null;
}

function calcAvg(arr) {
    if (!arr || arr.length === 0) return null;
    return arr.reduce((a, b) => a + b, 0) / arr.length;
}

function calcMedian(arr) {
    if (!arr || arr.length === 0) return null;
    const s = [...arr].sort((a, b) => a - b);
    const mid = Math.floor(s.length / 2);
    return s.length % 2 !== 0 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
}

function classifyDeathCause(item) {
    const c = normalizeStr((item.death_cause || '') + ' ' + (item.raw_text || ''));
    if (c.includes('tragick')) return 'Tragicky';
    if (c.includes('kratk') && c.includes('nemoc')) return 'Po krátké nemoci';
    if ((c.includes('dlouh') || c.includes('delsi') || c.includes('tezke')) && c.includes('nemoc')) return 'Po dlouhé / těžké nemoci';
    if (c.includes('nahle') || c.includes('necekane') || c.includes('neocekavane')) return 'Náhle a neočekávaně';
    if (c.includes('tise') || c.includes('spanek') || c.includes('spanku')) return 'Tiše / ve spánku';
    return 'Neuvedeno na parte';
}

function classifyCeremonyPlace(item) {
    const c = normalizeStr((item.ceremony || '') + ' ' + (item.raw_text || ''));
    if (c.includes('rodinnem kruhu') || c.includes('bez smutecniho obradu') || c.includes('tichou vzpominkou') || c.includes('tiche rozlouceni')) {
        return 'Bez obřadu (v rodinném kruhu)';
    }
    if (c.includes('kostel') || c.includes('chram')) return 'Kostel / chrám Páně';
    if (c.includes('mohelnic')) return 'Obřadní síň Mohelnice';
    if (c.includes('zabreh') || c.includes('zabreze')) return 'Obřadní síň Zábřeh';
    if (c.includes('litovl') || c.includes('litovel')) return 'Obřadní síň Litovel';
    if (c.includes('sumperk') || c.includes('olomouc') || c.includes('krematori')) return 'Krematorium Šumperk / Olomouc';
    return 'Jiné / upřesněno rodinou';
}

function parseFuneralDelayDays(item) {
    const deathIso = (item.death_date_iso || '').trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(deathIso)) return null;
    const [dy, dm, dd] = deathIso.split('-').map(Number);
    const deathUtc = Date.UTC(dy, dm - 1, dd);

    const cerText = (item.ceremony || '') + ' ' + (item.raw_text || '');
    const normCer = normalizeStr(cerText);
    if (normCer.includes('rodinnem kruhu') || normCer.includes('bez smutecniho obradu') || normCer.includes('tiche rozlouceni')) {
        return 'no_ceremony';
    }

    // Hledání data v pasáži o posledním rozloučení
    const rozlSplit = cerText.split(/rozlou[čc][ií]me|rozlou[čc]en[ií]|poh[řr]eb/i);
    const searchZone = rozlSplit.length > 1 ? rozlSplit.slice(1).join(' ') : (item.ceremony || '');

    let cDay = null, cMonth = null, cYear = dy;
    const mNum = searchZone.match(/(\d{1,2})\.\s*(\d{1,2})\.(?:\s*(\d{4}))?/);
    if (mNum) {
        cDay = parseInt(mNum[1], 10);
        cMonth = parseInt(mNum[2], 10);
        if (mNum[3]) cYear = parseInt(mNum[3], 10);
    } else {
        const mWord = searchZone.match(/(\d{1,2})\.\s*([a-záčďéěíňóřšťúůýž]+)(?:\s*(\d{4}))?/i);
        if (mWord) {
            const w = mWord[2].toLowerCase();
            const wNorm = normalizeStr(w);
            if (CZECH_MONTHS_MAP[w] || CZECH_MONTHS_MAP[wNorm]) {
                cDay = parseInt(mWord[1], 10);
                cMonth = CZECH_MONTHS_MAP[w] || CZECH_MONTHS_MAP[wNorm];
                if (mWord[3]) cYear = parseInt(mWord[3], 10);
            }
        }
    }

    if (cDay && cMonth) {
        const cerUtc = Date.UTC(cYear, cMonth - 1, cDay);
        const diff = Math.round((cerUtc - deathUtc) / (1000 * 60 * 60 * 24));
        if (diff >= 1 && diff <= 21) return diff;
    }
    return null;
}

function getTimeBucketKeyAndLabel(isoDate, granularity) {
    const [y, m, d] = isoDate.split('-').map(Number);
    if (granularity === 'day') {
        return {
            key: isoDate,
            label: `${String(d).padStart(2, '0')}. ${String(m).padStart(2, '0')}.`
        };
    }
    if (granularity === 'month') {
        const monthNames = ['', 'Leden', 'Únor', 'Březen', 'Duben', 'Květen', 'Červen', 'Červenec', 'Srpen', 'Září', 'Říjen', 'Listopad', 'Prosinec'];
        return {
            key: `${y}-${String(m).padStart(2, '0')}`,
            label: `${monthNames[m]} ${y}`
        };
    }
    // Výchozí: Po týdnech (pondělí daného týdne)
    const dt = new Date(Date.UTC(y, m - 1, d));
    const dayOfWeek = (dt.getUTCDay() + 6) % 7; // 0 = Pondělí .. 6 = Neděle
    const monday = new Date(dt.getTime() - dayOfWeek * 86400000);
    const sunday = new Date(monday.getTime() + 6 * 86400000);
    const wy = monday.getUTCFullYear();
    const wm = monday.getUTCMonth() + 1;
    const wd = monday.getUTCDate();
    const sm = sunday.getUTCMonth() + 1;
    const sd = sunday.getUTCDate();
    return {
        key: `${wy}-${String(wm).padStart(2, '0')}-${String(wd).padStart(2, '0')}`,
        label: `${wd}.${wm}.–${sd}.${sm}.`
    };
}

function destroyChart(id) {
    if (activeCharts[id]) {
        activeCharts[id].destroy();
        delete activeCharts[id];
    }
}

function renderStatisticsView() {
    if (typeof Chart === 'undefined') return;

    const items = getFilteredAndSortedItems(true).filter(i => i.category !== 'Vzpomínka');
    statsBadge.textContent = `${items.length} ${getNoun(items.length, 'parte', 'parte', 'parte')} ve statistice`;

    // Nastavení výchozích barev Chart.js pro tmavý režim
    Chart.defaults.color = '#94a3b8';
    Chart.defaults.borderColor = 'rgba(255, 255, 255, 0.07)';
    Chart.defaults.font.family = "'Inter', system-ui, sans-serif";

    const datedItems = items
        .filter(i => /^\d{4}-\d{2}-\d{2}$/.test(i.death_date_iso || ''))
        .sort((a, b) => a.death_date_iso.localeCompare(b.death_date_iso));

    const firstDateText = datedItems.length > 0 ? datedItems[0].death_date : '–';
    const lastDateText = datedItems.length > 0 ? datedItems[datedItems.length - 1].death_date : '–';

    if (statsScopeInfo) {
        statsScopeInfo.innerHTML = `Analyzováno <strong>${items.length} smutečních oznámení</strong> v období od <strong>${escapeHtml(firstDateText)}</strong> do <strong>${escapeHtml(lastDateText)}</strong> (bez výročních vzpomínek).`;
    }

    // 1. Výpočet věkových a demografických metrik
    const allAges = [];
    const femaleAges = [];
    const maleAges = [];
    let femaleCount = 0;
    let maleCount = 0;
    let youngestItem = null;
    let oldestItem = null;

    items.forEach(it => {
        const g = it.gender;
        if (g === 'F') femaleCount++;
        else if (g === 'M') maleCount++;
        else if (g === 'MF') { femaleCount++; maleCount++; }

        const age = parseAgeNumber(it);
        if (age != null) {
            allAges.push(age);
            if (g === 'F') femaleAges.push(age);
            if (g === 'M') maleAges.push(age);
            if (!youngestItem || age < youngestItem.ageNum) youngestItem = { item: it, ageNum: age };
            if (!oldestItem || age > oldestItem.ageNum) oldestItem = { item: it, ageNum: age };
        }
    });

    const avgAll = calcAvg(allAges);
    const medAll = calcMedian(allAges);
    const avgF = calcAvg(femaleAges);
    const avgM = calcAvg(maleAges);
    const genderDiff = (avgF != null && avgM != null) ? (avgF - avgM) : null;

    // Odstup pohřbu od úmrtí
    const delays = [];
    let noCeremonyCount = 0;
    items.forEach(it => {
        const d = parseFuneralDelayDays(it);
        if (d === 'no_ceremony') noCeremonyCount++;
        else if (typeof d === 'number') delays.push(d);
    });
    const avgDelay = calcAvg(delays);

    // Nejčastější obec a den v týdnu
    const townCounts = {};
    items.forEach(it => {
        if (it.residence) {
            townCounts[it.residence] = (townCounts[it.residence] || 0) + 1;
        }
    });
    const sortedTownEntries = Object.entries(townCounts).sort((a, b) => b[1] - a[1]);
    const topTown = sortedTownEntries.length > 0 ? sortedTownEntries[0] : ['–', 0];

    const dayNames = ['Pondělí', 'Úterý', 'Středa', 'Čtvrtek', 'Pátek', 'Sobota', 'Neděle'];
    const dayCountsF = [0, 0, 0, 0, 0, 0, 0];
    const dayCountsM = [0, 0, 0, 0, 0, 0, 0];
    datedItems.forEach(it => {
        const [y, m, d] = it.death_date_iso.split('-').map(Number);
        const dt = new Date(Date.UTC(y, m - 1, d));
        const dow = (dt.getUTCDay() + 6) % 7;
        if (it.gender === 'F') dayCountsF[dow]++;
        else dayCountsM[dow]++;
    });
    const dayTotals = dayCountsF.map((f, idx) => f + dayCountsM[idx]);
    let maxDowIdx = 0;
    dayTotals.forEach((cnt, idx) => { if (cnt > dayTotals[maxDowIdx]) maxDowIdx = idx; });

    const activeLast7Count = items.filter(i => i.is_active).length;
    const totalGenderCount = Math.max(1, femaleCount + maleCount);
    const fPct = Math.round((femaleCount / totalGenderCount) * 100);
    const mPct = 100 - fPct;

    // Vykreslení KPI karet
    if (statsKpiGrid) {
        statsKpiGrid.innerHTML = `
            <div class="kpi-card">
                <span class="kpi-label">Průměrný věk dožití</span>
                <div class="kpi-value">${avgAll != null ? avgAll.toFixed(1) : '–'} <span class="kpi-unit">let (medián ${medAll != null ? medAll.toFixed(0) : '–'} let)</span></div>
                <div class="kpi-sub">
                    <span class="f-tag">♀ Ženy: ${avgF != null ? avgF.toFixed(1) + ' let' : '–'}</span> •
                    <span class="m-tag">♂ Muži: ${avgM != null ? avgM.toFixed(1) + ' let' : '–'}</span>
                    ${genderDiff != null ? `<br>Ženy se dožívají v průměru o <strong>${Math.abs(genderDiff).toFixed(1)} let ${genderDiff >= 0 ? 'více' : 'méně'}</strong>` : ''}
                </div>
            </div>
            <div class="kpi-card">
                <span class="kpi-label">Poměr pohlaví (♀ vs. ♂)</span>
                <div class="kpi-value">${femaleCount} <span class="kpi-unit">žen (${fPct} %)</span> : ${maleCount} <span class="kpi-unit">mužů (${mPct} %)</span></div>
                <div class="kpi-sub">
                    Celkem <strong>${items.length}</strong> oznámení • Za posledních 7 dní od úmrtí: <strong>${activeLast7Count}</strong> aktuálních
                </div>
            </div>
            <div class="kpi-card">
                <span class="kpi-label">Nejvyšší a nejnižší věk</span>
                <div class="kpi-value">${oldestItem ? oldestItem.ageNum : '–'} <span class="kpi-unit">let max</span> / ${youngestItem ? youngestItem.ageNum : '–'} <span class="kpi-unit">let min</span></div>
                <div class="kpi-sub">
                    Nejstarší: <strong>${oldestItem ? escapeHtml(oldestItem.item.name) : '–'}</strong> (${oldestItem && oldestItem.item.residence ? escapeHtml(oldestItem.item.residence) : '–'})<br>
                    Nejmladší: <strong>${youngestItem ? escapeHtml(youngestItem.item.name) : '–'}</strong> (${youngestItem && youngestItem.item.residence ? escapeHtml(youngestItem.item.residence) : '–'})
                </div>
            </div>
            <div class="kpi-card">
                <span class="kpi-label">Odstup pohřbu a obřady</span>
                <div class="kpi-value">${avgDelay != null ? avgDelay.toFixed(1) : '–'} <span class="kpi-unit">dne od úmrtí do pohřbu</span></div>
                <div class="kpi-sub">
                    Bez obřadu (v rodinném kruhu): <strong>${noCeremonyCount}× (${Math.round((noCeremonyCount / Math.max(1, items.length)) * 100)} %)</strong><br>
                    Nejčastější den úmrtí: <strong>${dayNames[maxDowIdx]} (${dayTotals[maxDowIdx]}×)</strong>
                </div>
            </div>
            <div class="kpi-card">
                <span class="kpi-label">Nejčastější město / obec</span>
                <div class="kpi-value">${escapeHtml(topTown[0])} <span class="kpi-unit">(${topTown[1]} parte)</span></div>
                <div class="kpi-sub">
                    Celkem zastoupeno <strong>${sortedTownEntries.length} různých měst a obcí</strong> v regionu
                </div>
            </div>
        `;
    }

    // 2. Seskupení dat v čase (Po týdnech / Po dnech / Po měsících)
    const timeBucketsMap = new Map();
    datedItems.forEach(it => {
        const { key, label } = getTimeBucketKeyAndLabel(it.death_date_iso, statsTimeGranularity);
        if (!timeBucketsMap.has(key)) {
            timeBucketsMap.set(key, { key, label, fCount: 0, mCount: 0, fAges: [], mAges: [], allAges: [] });
        }
        const b = timeBucketsMap.get(key);
        if (it.gender === 'F') b.fCount++;
        else b.mCount++;

        const age = parseAgeNumber(it);
        if (age != null) {
            b.allAges.push(age);
            if (it.gender === 'F') b.fAges.push(age);
            else if (it.gender === 'M') b.mAges.push(age);
        }
    });

    const timeBuckets = Array.from(timeBucketsMap.values()).sort((a, b) => a.key.localeCompare(b.key));
    const timeLabels = timeBuckets.map(b => b.label);
    const timeFCounts = timeBuckets.map(b => b.fCount);
    const timeMCounts = timeBuckets.map(b => b.mCount);
    let runningTotal = 0;
    const timeCumulative = timeBuckets.map(b => {
        runningTotal += (b.fCount + b.mCount);
        return runningTotal;
    });

    // GRAF 1: Vývoj počtu úmrtí v čase (Ženy vs. Muži + kumulativní křivka)
    destroyChart('chartDeathsOverTime');
    const ctx1 = document.getElementById('chartDeathsOverTime');
    if (ctx1) {
        activeCharts['chartDeathsOverTime'] = new Chart(ctx1, {
            type: 'bar',
            data: {
                labels: timeLabels,
                datasets: [
                    {
                        label: '♀ Ženy',
                        data: timeFCounts,
                        backgroundColor: 'rgba(244, 114, 182, 0.78)',
                        borderColor: '#f472b6',
                        borderWidth: 1,
                        borderRadius: 4,
                        stack: 'genderStack',
                        yAxisID: 'y'
                    },
                    {
                        label: '♂ Muži',
                        data: timeMCounts,
                        backgroundColor: 'rgba(96, 165, 250, 0.78)',
                        borderColor: '#60a5fa',
                        borderWidth: 1,
                        borderRadius: 4,
                        stack: 'genderStack',
                        yAxisID: 'y'
                    },
                    {
                        type: 'line',
                        label: 'Kumulativní počet úmrtí v čase',
                        data: timeCumulative,
                        borderColor: '#fbbf24',
                        backgroundColor: 'rgba(251, 191, 36, 0.15)',
                        borderWidth: 2.5,
                        pointRadius: 3.5,
                        tension: 0.25,
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    x: { stacked: true },
                    y: {
                        stacked: true,
                        beginAtZero: true,
                        ticks: { stepSize: 1 },
                        title: { display: true, text: 'Počet úmrtí za období' }
                    },
                    y1: {
                        position: 'right',
                        beginAtZero: true,
                        grid: { drawOnChartArea: false },
                        title: { display: true, text: 'Kumulativně celkem' }
                    }
                }
            }
        });
    }

    // GRAF 2: Vývoj průměrného věku úmrtí v čase (♀ Ženy vs. ♂ Muži vs. Celkem)
    destroyChart('chartAgeOverTime');
    const ctx2 = document.getElementById('chartAgeOverTime');
    if (ctx2) {
        const avgAgeFSeries = timeBuckets.map(b => b.fAges.length ? Number(calcAvg(b.fAges).toFixed(1)) : null);
        const avgAgeMSeries = timeBuckets.map(b => b.mAges.length ? Number(calcAvg(b.mAges).toFixed(1)) : null);
        const avgAgeAllSeries = timeBuckets.map(b => b.allAges.length ? Number(calcAvg(b.allAges).toFixed(1)) : null);

        activeCharts['chartAgeOverTime'] = new Chart(ctx2, {
            type: 'line',
            data: {
                labels: timeLabels,
                datasets: [
                    {
                        label: '♀ Průměrný věk Ženy (let)',
                        data: avgAgeFSeries,
                        borderColor: '#f472b6',
                        backgroundColor: 'rgba(244, 114, 182, 0.2)',
                        borderWidth: 2.5,
                        pointRadius: 4,
                        spanGaps: true,
                        tension: 0.25
                    },
                    {
                        label: '♂ Průměrný věk Muži (let)',
                        data: avgAgeMSeries,
                        borderColor: '#60a5fa',
                        backgroundColor: 'rgba(96, 165, 250, 0.2)',
                        borderWidth: 2.5,
                        pointRadius: 4,
                        spanGaps: true,
                        tension: 0.25
                    },
                    {
                        label: 'Celkový průměrný věk (let)',
                        data: avgAgeAllSeries,
                        borderColor: '#fbbf24',
                        borderDash: [5, 5],
                        borderWidth: 2,
                        pointRadius: 3,
                        spanGaps: true,
                        tension: 0.25
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    y: {
                        suggestedMin: 50,
                        suggestedMax: 98,
                        title: { display: true, text: 'Věk při úmrtí (roky)' }
                    }
                }
            }
        });
    }

    // GRAF 3: Bodový graf jednotlivých úmrtí v čase (Datum úmrtí vs. Věk vs. Pohlaví)
    destroyChart('chartScatterTimeAge');
    const ctx3 = document.getElementById('chartScatterTimeAge');
    if (ctx3) {
        const uniqueDates = Array.from(new Set(datedItems.map(i => i.death_date_iso))).sort();
        const dateLabels = uniqueDates.map(iso => {
            const [y, m, d] = iso.split('-');
            return `${d}. ${m}. ${y}`;
        });

        const scatterF = [];
        const scatterM = [];
        datedItems.forEach(it => {
            const age = parseAgeNumber(it);
            if (age == null) return;
            const xIdx = uniqueDates.indexOf(it.death_date_iso);
            const pointObj = {
                x: xIdx,
                y: age,
                item: it
            };
            if (it.gender === 'F') scatterF.push(pointObj);
            else scatterM.push(pointObj);
        });

        activeCharts['chartScatterTimeAge'] = new Chart(ctx3, {
            type: 'scatter',
            data: {
                datasets: [
                    {
                        label: '♀ Ženy (jednotlivá úmrtí v čase)',
                        data: scatterF,
                        backgroundColor: 'rgba(244, 114, 182, 0.88)',
                        borderColor: '#f472b6',
                        pointRadius: 6,
                        pointHoverRadius: 9
                    },
                    {
                        label: '♂ Muži (jednotlivá úmrtí v čase)',
                        data: scatterM,
                        backgroundColor: 'rgba(96, 165, 250, 0.88)',
                        borderColor: '#60a5fa',
                        pointRadius: 6,
                        pointHoverRadius: 9
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                onClick: (evt, elements) => {
                    if (elements && elements.length > 0) {
                        const el = elements[0];
                        const ds = activeCharts['chartScatterTimeAge'].data.datasets[el.datasetIndex];
                        const pt = ds.data[el.index];
                        if (pt && pt.item) {
                            const imgSrc = pt.item.local_image_path || pt.item.release_image_url || pt.item.image_url;
                            openModal(pt.item, imgSrc);
                        }
                    }
                },
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: (ctx) => {
                                const pt = ctx.raw;
                                if (!pt || !pt.item) return '';
                                const it = pt.item;
                                const town = it.residence ? ` • 📍 ${it.residence}` : '';
                                const cause = it.death_cause ? ` • ${it.death_cause}` : '';
                                return `${it.name} (${pt.y} let)${town} • Úmrtí: ${it.death_date}${cause}`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        type: 'linear',
                        min: -0.5,
                        max: Math.max(0, uniqueDates.length - 0.5),
                        ticks: {
                            stepSize: 1,
                            callback: (val) => {
                                const idx = Math.round(val);
                                return dateLabels[idx] || '';
                            }
                        },
                        title: { display: true, text: 'Datum úmrtí v čase (kliknutím na bod otevřete parte)' }
                    },
                    y: {
                        suggestedMin: 40,
                        suggestedMax: 100,
                        title: { display: true, text: 'Věk při úmrtí (let)' }
                    }
                }
            }
        });
    }

    // GRAF 4: Pohlaví vs. Věk úmrtí (Věkové skupiny)
    destroyChart('chartGenderAgeBuckets');
    const ctx4 = document.getElementById('chartGenderAgeBuckets');
    if (ctx4) {
        const brackets = [
            { label: 'do 49 let', min: 0, max: 49 },
            { label: '50–59 let', min: 50, max: 59 },
            { label: '60–69 let', min: 60, max: 69 },
            { label: '70–79 let', min: 70, max: 79 },
            { label: '80–89 let', min: 80, max: 89 },
            { label: '90 a více let', min: 90, max: 200 }
        ];
        const fBucketCounts = brackets.map(() => 0);
        const mBucketCounts = brackets.map(() => 0);

        items.forEach(it => {
            const age = parseAgeNumber(it);
            if (age == null) return;
            const bIdx = brackets.findIndex(b => age >= b.min && age <= b.max);
            if (bIdx !== -1) {
                if (it.gender === 'F') fBucketCounts[bIdx]++;
                else mBucketCounts[bIdx]++;
            }
        });

        activeCharts['chartGenderAgeBuckets'] = new Chart(ctx4, {
            type: 'bar',
            data: {
                labels: brackets.map(b => b.label),
                datasets: [
                    {
                        label: '♀ Ženy',
                        data: fBucketCounts,
                        backgroundColor: 'rgba(244, 114, 182, 0.8)',
                        borderColor: '#f472b6',
                        borderWidth: 1,
                        borderRadius: 5
                    },
                    {
                        label: '♂ Muži',
                        data: mBucketCounts,
                        backgroundColor: 'rgba(96, 165, 250, 0.8)',
                        borderColor: '#60a5fa',
                        borderWidth: 1,
                        borderRadius: 5
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: { beginAtZero: true, ticks: { stepSize: 1 }, title: { display: true, text: 'Počet osob' } }
                }
            }
        });
    }

    // GRAF 5: Statistika podle měst a obcí (Počet ♀/♂ + Průměrný věk v obci) + Tabulka obcí
    const townMap = new Map();
    items.forEach(it => {
        const town = it.residence || 'Neuvedeno';
        if (!townMap.has(town)) {
            townMap.set(town, { town, total: 0, fCount: 0, mCount: 0, fAges: [], mAges: [], allAges: [] });
        }
        const st = townMap.get(town);
        st.total++;
        if (it.gender === 'F') st.fCount++;
        else st.mCount++;
        const age = parseAgeNumber(it);
        if (age != null) {
            st.allAges.push(age);
            if (it.gender === 'F') st.fAges.push(age);
            else st.mAges.push(age);
        }
    });

    const townStatsArr = Array.from(townMap.values())
        .filter(t => t.town !== 'Neuvedeno')
        .sort((a, b) => b.total - a.total || a.town.localeCompare(b.town, 'cs'));

    destroyChart('chartTownsStats');
    const ctx5 = document.getElementById('chartTownsStats');
    if (ctx5) {
        const topTowns = townStatsArr.slice(0, 14);
        activeCharts['chartTownsStats'] = new Chart(ctx5, {
            type: 'bar',
            data: {
                labels: topTowns.map(t => t.town),
                datasets: [
                    {
                        label: '♀ Ženy',
                        data: topTowns.map(t => t.fCount),
                        backgroundColor: 'rgba(244, 114, 182, 0.8)',
                        stack: 'townStack',
                        yAxisID: 'y'
                    },
                    {
                        label: '♂ Muži',
                        data: topTowns.map(t => t.mCount),
                        backgroundColor: 'rgba(96, 165, 250, 0.8)',
                        stack: 'townStack',
                        yAxisID: 'y'
                    },
                    {
                        type: 'line',
                        label: 'Průměrný věk v obci (let)',
                        data: topTowns.map(t => t.allAges.length ? Number(calcAvg(t.allAges).toFixed(1)) : null),
                        borderColor: '#fbbf24',
                        backgroundColor: '#fbbf24',
                        borderWidth: 2,
                        pointRadius: 4,
                        spanGaps: true,
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    x: { stacked: true },
                    y: { stacked: true, beginAtZero: true, ticks: { stepSize: 1 }, title: { display: true, text: 'Počet úmrtí' } },
                    y1: { position: 'right', suggestedMin: 55, suggestedMax: 95, grid: { drawOnChartArea: false }, title: { display: true, text: 'Průměrný věk (let)' } }
                }
            }
        });
    }

    // Naplnění podrobné tabulky obcí
    if (statsTownTableBody) {
        statsTownTableBody.innerHTML = townStatsArr.map(t => {
            const avgT = calcAvg(t.allAges);
            const medT = calcMedian(t.allAges);
            const avgTF = calcAvg(t.fAges);
            const avgTM = calcAvg(t.mAges);
            const minA = t.allAges.length ? Math.min(...t.allAges) : null;
            const maxA = t.allAges.length ? Math.max(...t.allAges) : null;
            return `
                <tr>
                    <td><strong>📍 ${escapeHtml(t.town)}</strong></td>
                    <td><strong>${t.total}</strong></td>
                    <td><span style="color:#f472b6;font-weight:600;">${t.fCount}×</span> ${avgTF != null ? `(${avgTF.toFixed(1)} let)` : ''}</td>
                    <td><span style="color:#60a5fa;font-weight:600;">${t.mCount}×</span> ${avgTM != null ? `(${avgTM.toFixed(1)} let)` : ''}</td>
                    <td><strong>${avgT != null ? avgT.toFixed(1) + ' let' : '–'}</strong></td>
                    <td>${medT != null ? medT.toFixed(0) + ' let' : '–'}</td>
                    <td>${minA != null ? `${minA} – ${maxA} let` : '–'}</td>
                </tr>
            `;
        }).join('');
    }

    // GRAF 6: Okolnosti a důvody úmrtí vs. Průměrný věk
    destroyChart('chartCausesStats');
    const ctx6 = document.getElementById('chartCausesStats');
    if (ctx6) {
        const causeMap = new Map();
        items.forEach(it => {
            const cat = classifyDeathCause(it);
            if (!causeMap.has(cat)) causeMap.set(cat, { cat, count: 0, ages: [] });
            const obj = causeMap.get(cat);
            obj.count++;
            const age = parseAgeNumber(it);
            if (age != null) obj.ages.push(age);
        });
        const causeArr = Array.from(causeMap.values()).sort((a, b) => b.count - a.count);

        activeCharts['chartCausesStats'] = new Chart(ctx6, {
            type: 'bar',
            data: {
                labels: causeArr.map(c => c.cat),
                datasets: [
                    {
                        label: 'Počet úmrtí',
                        data: causeArr.map(c => c.count),
                        backgroundColor: 'rgba(217, 119, 6, 0.72)',
                        borderColor: '#f59e0b',
                        borderWidth: 1,
                        borderRadius: 5,
                        yAxisID: 'y'
                    },
                    {
                        type: 'line',
                        label: 'Průměrný věk (let)',
                        data: causeArr.map(c => c.ages.length ? Number(calcAvg(c.ages).toFixed(1)) : null),
                        borderColor: '#38bdf8',
                        backgroundColor: '#38bdf8',
                        borderWidth: 2.5,
                        pointRadius: 4,
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    y: { beginAtZero: true, ticks: { stepSize: 1 }, title: { display: true, text: 'Počet osob' } },
                    y1: { position: 'right', suggestedMin: 50, suggestedMax: 95, grid: { drawOnChartArea: false }, title: { display: true, text: 'Průměrný věk (let)' } }
                }
            }
        });
    }

    // GRAF 7: Úmrtí podle dne v týdnu (Pondělí – Neděle)
    destroyChart('chartDayOfWeek');
    const ctx7 = document.getElementById('chartDayOfWeek');
    if (ctx7) {
        activeCharts['chartDayOfWeek'] = new Chart(ctx7, {
            type: 'bar',
            data: {
                labels: dayNames,
                datasets: [
                    {
                        label: '♀ Ženy',
                        data: dayCountsF,
                        backgroundColor: 'rgba(244, 114, 182, 0.8)',
                        stack: 'dowStack',
                        borderRadius: 4
                    },
                    {
                        label: '♂ Muži',
                        data: dayCountsM,
                        backgroundColor: 'rgba(96, 165, 250, 0.8)',
                        stack: 'dowStack',
                        borderRadius: 4
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { stacked: true },
                    y: { stacked: true, beginAtZero: true, ticks: { stepSize: 1 }, title: { display: true, text: 'Počet úmrtí' } }
                }
            }
        });
    }

    // GRAF 8: Odstup pohřbu od úmrtí (počet dní)
    destroyChart('chartFuneralDelay');
    const ctx8 = document.getElementById('chartFuneralDelay');
    if (ctx8) {
        const delayLabels = ['2–3 dny', '4 dny', '5 dní', '6 dní', '7 dní', '8 a více dní', 'Bez obřadu'];
        const delayCounts = [0, 0, 0, 0, 0, 0, 0];
        items.forEach(it => {
            const d = parseFuneralDelayDays(it);
            if (d === 'no_ceremony') delayCounts[6]++;
            else if (typeof d === 'number') {
                if (d <= 3) delayCounts[0]++;
                else if (d === 4) delayCounts[1]++;
                else if (d === 5) delayCounts[2]++;
                else if (d === 6) delayCounts[3]++;
                else if (d === 7) delayCounts[4]++;
                else delayCounts[5]++;
            }
        });

        activeCharts['chartFuneralDelay'] = new Chart(ctx8, {
            type: 'bar',
            data: {
                labels: delayLabels,
                datasets: [
                    {
                        label: 'Počet pohřbů / rozloučení',
                        data: delayCounts,
                        backgroundColor: [
                            'rgba(96, 165, 250, 0.75)',
                            'rgba(96, 165, 250, 0.82)',
                            'rgba(251, 191, 36, 0.85)',
                            'rgba(245, 158, 11, 0.85)',
                            'rgba(244, 114, 182, 0.8)',
                            'rgba(167, 139, 250, 0.8)',
                            'rgba(148, 163, 184, 0.65)'
                        ],
                        borderRadius: 5
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: { beginAtZero: true, ticks: { stepSize: 1 }, title: { display: true, text: 'Počet parte' } }
                }
            }
        });
    }

    // GRAF 9: Místa posledního rozloučení
    destroyChart('chartCeremonyPlaces');
    const ctx9 = document.getElementById('chartCeremonyPlaces');
    if (ctx9) {
        const placeCounts = {};
        items.forEach(it => {
            const p = classifyCeremonyPlace(it);
            placeCounts[p] = (placeCounts[p] || 0) + 1;
        });
        const placeEntries = Object.entries(placeCounts).sort((a, b) => b[1] - a[1]);

        activeCharts['chartCeremonyPlaces'] = new Chart(ctx9, {
            type: 'doughnut',
            data: {
                labels: placeEntries.map(e => e[0]),
                datasets: [
                    {
                        data: placeEntries.map(e => e[1]),
                        backgroundColor: [
                            '#f59e0b',
                            '#60a5fa',
                            '#f472b6',
                            '#34d399',
                            '#a78bfa',
                            '#94a3b8'
                        ],
                        borderColor: '#161a24',
                        borderWidth: 2
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'right' }
                }
            }
        });
    }
}

// Přepínače časové osy ve statistice (Po týdnech / Po dnech / Po měsících)
document.querySelectorAll('.time-granularity-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.time-granularity-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        statsTimeGranularity = btn.getAttribute('data-granularity') || 'week';
        renderStatisticsView();
    });
});

// ==================== MOBILNÍ SBALITELNÝ FILTR ====================
const mobileFilterToggleBtn = document.getElementById('mobileFilterToggleBtn');
const filtersBarWrap = document.getElementById('filtersBarWrap');
const mobileFilterChevron = document.getElementById('mobileFilterChevron');

if (mobileFilterToggleBtn && filtersBarWrap) {
    mobileFilterToggleBtn.addEventListener('click', () => {
        const isOpen = filtersBarWrap.classList.toggle('mobile-open');
        if (mobileFilterChevron) {
            mobileFilterChevron.textContent = isOpen ? '▲ Skrýt' : '▼ Rozbalit';
        }
    });
}

// ==================== OZNÁMENÍ V PROHLÍŽEČI (NOTIFIKACE NA NOVÁ PARTE + NASTAVENÍ) ====================
const NOTIFY_ENABLED_KEY = 'vyveska_notify_enabled';
const NOTIFY_KNOWN_IDS_KEY = 'vyveska_known_ids';
const NOTIFY_SOURCES_KEY = 'vyveska_notify_sources';
const NOTIFY_CATEGORY_KEY = 'vyveska_notify_category_mode';

const notifyBtn = document.getElementById('notifyBtn');
const notifyBtnIcon = document.getElementById('notifyBtnIcon');
const notifyBtnLabel = document.getElementById('notifyBtnLabel');

const notifyModal = document.getElementById('notifyModal');
const notifyModalBackdrop = document.getElementById('notifyModalBackdrop');
const notifyModalCloseBtn = document.getElementById('notifyModalCloseBtn');
const notifyMasterCheckbox = document.getElementById('notifyMasterCheckbox');
const notifySrcHornicek = document.getElementById('notifySrcHornicek');
const notifySrcHrandop = document.getElementById('notifySrcHrandop');
const notifySrcDrozd = document.getElementById('notifySrcDrozd');
const notifyOnlyObituaries = document.getElementById('notifyOnlyObituaries');
const notifyIncludeMemories = document.getElementById('notifyIncludeMemories');
const notifyTestBtn = document.getElementById('notifyTestBtn');
const notifySaveBtn = document.getElementById('notifySaveBtn');

function getNotifySettings() {
    let sources = ['Horníček Moravičany', 'Hrandop Litovel', 'Drozd Zábřeh'];
    try {
        const raw = localStorage.getItem(NOTIFY_SOURCES_KEY);
        if (raw) {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) sources = parsed;
        }
    } catch (e) {}

    const categoryMode = localStorage.getItem(NOTIFY_CATEGORY_KEY) || 'obituaries_only';
    const enabled = (
        'Notification' in window &&
        Notification.permission === 'granted' &&
        localStorage.getItem(NOTIFY_ENABLED_KEY) === '1'
    );
    return { enabled, sources, categoryMode };
}

function isBrowserNotifyEnabled() {
    return getNotifySettings().enabled;
}

function updateNotifyButtonUi() {
    if (!notifyBtn) return;
    if (!('Notification' in window)) {
        notifyBtn.style.display = 'none';
        return;
    }
    const { enabled } = getNotifySettings();
    notifyBtn.classList.toggle('active', enabled);
    if (notifyBtnIcon) notifyBtnIcon.textContent = enabled ? '🔔' : '🔕';
    if (notifyBtnLabel) notifyBtnLabel.textContent = enabled ? 'Upozornění zapnuto' : 'Upozornění';
}

function openNotifyModal() {
    if (!notifyModal) return;
    const { enabled, sources, categoryMode } = getNotifySettings();
    if (notifyMasterCheckbox) notifyMasterCheckbox.checked = enabled;
    if (notifySrcHornicek) notifySrcHornicek.checked = sources.includes('Horníček Moravičany');
    if (notifySrcHrandop) notifySrcHrandop.checked = sources.includes('Hrandop Litovel');
    if (notifySrcDrozd) notifySrcDrozd.checked = sources.includes('Drozd Zábřeh');
    if (notifyOnlyObituaries) notifyOnlyObituaries.checked = (categoryMode !== 'include_memories');
    if (notifyIncludeMemories) notifyIncludeMemories.checked = (categoryMode === 'include_memories');
    notifyModal.classList.add('active');
}

function closeNotifyModal() {
    if (!notifyModal) return;
    notifyModal.classList.remove('active');
}

function showObituaryNotification(item) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const gInfo = formatGenderSymbol(item.gender);
    const agePart = item.age ? ` (${item.age})` : '';
    const prefix = item.category === 'Vzpomínka' ? '🕯️ Vzpomínka' : '✝ Nové parte';
    const title = `${prefix}: ${gInfo.symbol ? gInfo.symbol + ' ' : ''}${item.name}${agePart}`;
    const bodyParts = [];
    if (item.residence) bodyParts.push(`📍 ${item.residence}`);
    if (item.death_date) bodyParts.push(`Úmrtí: ${item.death_date}`);
    if (item.source_name) bodyParts.push(item.source_name);

    try {
        const notif = new Notification(title, {
            body: bodyParts.join(' • '),
            icon: item.face_image_path || item.release_face_url || 'favicon.svg',
            tag: 'parte-' + item.id
        });
        notif.onclick = () => {
            window.focus();
            const imgSrc = item.local_image_path || item.release_image_url || item.image_url;
            openModal(item, imgSrc);
            notif.close();
        };
    } catch (e) {}
}

function checkNewObituariesAndNotify(items) {
    updateNotifyButtonUi();
    if (!items || items.length === 0) return;

    const currentIds = items.map(i => i.id);
    let savedIds = null;
    try {
        const raw = localStorage.getItem(NOTIFY_KNOWN_IDS_KEY);
        if (raw) savedIds = new Set(JSON.parse(raw));
    } catch (e) {}

    const { enabled, sources, categoryMode } = getNotifySettings();
    if (savedIds && savedIds.size > 0 && enabled) {
        const newMatchingItems = items.filter(i => {
            if (!i.is_active || savedIds.has(i.id)) return false;
            const src = i.source_name || 'Horníček Moravičany';
            if (!sources.includes(src)) return false;
            if (categoryMode === 'obituaries_only' && i.category === 'Vzpomínka') return false;
            return true;
        });
        newMatchingItems.slice(0, 3).forEach(item => {
            showObituaryNotification(item);
        });
    }

    try {
        localStorage.setItem(NOTIFY_KNOWN_IDS_KEY, JSON.stringify(currentIds));
    } catch (e) {}
}

if (notifyBtn) {
    notifyBtn.addEventListener('click', () => {
        openNotifyModal();
    });
}

if (notifyModalCloseBtn) notifyModalCloseBtn.addEventListener('click', closeNotifyModal);
if (notifyModalBackdrop) notifyModalBackdrop.addEventListener('click', closeNotifyModal);

if (notifySaveBtn) {
    notifySaveBtn.addEventListener('click', async () => {
        const wantEnabled = notifyMasterCheckbox ? notifyMasterCheckbox.checked : false;
        const selectedSources = [];
        if (notifySrcHornicek && notifySrcHornicek.checked) selectedSources.push('Horníček Moravičany');
        if (notifySrcHrandop && notifySrcHrandop.checked) selectedSources.push('Hrandop Litovel');
        if (notifySrcDrozd && notifySrcDrozd.checked) selectedSources.push('Drozd Zábřeh');

        if (wantEnabled && selectedSources.length === 0) {
            alert('Vyberte prosím alespoň jednu pohřební službu.');
            return;
        }

        const catMode = (notifyIncludeMemories && notifyIncludeMemories.checked) ? 'include_memories' : 'obituaries_only';
        localStorage.setItem(NOTIFY_SOURCES_KEY, JSON.stringify(selectedSources));
        localStorage.setItem(NOTIFY_CATEGORY_KEY, catMode);

        if (wantEnabled) {
            let perm = Notification.permission;
            if (perm !== 'granted') {
                perm = await Notification.requestPermission();
            }
            if (perm === 'granted') {
                localStorage.setItem(NOTIFY_ENABLED_KEY, '1');
                if (allItems.length > 0) {
                    localStorage.setItem(NOTIFY_KNOWN_IDS_KEY, JSON.stringify(allItems.map(i => i.id)));
                }
            } else {
                localStorage.setItem(NOTIFY_ENABLED_KEY, '0');
                alert('Oznámení jsou v prohlížeči zablokována. Povolte je prosím u ikony zámku v adresním řádku.');
            }
        } else {
            localStorage.setItem(NOTIFY_ENABLED_KEY, '0');
        }

        updateNotifyButtonUi();
        closeNotifyModal();
    });
}

if (notifyTestBtn) {
    notifyTestBtn.addEventListener('click', async () => {
        if (!('Notification' in window)) {
            alert('Váš prohlížeč nepodporuje webová oznámení.');
            return;
        }
        let perm = Notification.permission;
        if (perm !== 'granted') {
            perm = await Notification.requestPermission();
        }
        if (perm !== 'granted') {
            alert('Pro vyzkoušení oznámení je potřeba povolit notifikace v prohlížeči.');
            return;
        }
        const sample = allItems.find(i => i.is_active) || allItems[0];
        if (sample) {
            showObituaryNotification(sample);
        } else {
            new Notification('🔔 Testovací oznámení – Smuteční vývěska', {
                body: 'Notifikace v prohlížeči fungují správně.',
                icon: 'favicon.svg'
            });
        }
    });
}

// Automatická kontrola nových parte na pozadí každých 15 minut
setInterval(() => {
    fetchDatabase();
}, 15 * 60 * 1000);

updateNotifyButtonUi();
fetchDatabase();
