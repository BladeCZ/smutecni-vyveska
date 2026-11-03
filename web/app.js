// Smuteční vývěska - Klientská aplikace
let allItems = [];
let currentTab = 'active'; // Výchozí záložka: Aktuální vývěska
let currentSearch = '';
let filterGender = 'all';
let filterTown = 'all';
let filterRadiusKm = 20;
let filterSource = 'all';
let hideMemories = false;
let sortField = 'date'; // Výchozí řazení: podle data úmrtí
let sortDesc = true;    // Od nejnovějších (posledních)
let userGpsCoords = null;
let activeModalItem = null;

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
    "Zábřeh": [49.8825, 16.8722],
    "Litovel": [49.7011, 17.0761],
    "Uničov": [49.7708, 17.1214],
    "Šumperk": [49.9653, 16.9706],
    "Olomouc": [49.5938, 17.2509]
};

// DOM prvky
const searchInput = document.getElementById('searchInput');
const clearSearchBtn = document.getElementById('clearSearch');
const filterGenderEl = document.getElementById('filterGender');
const filterTownEl = document.getElementById('filterTown');
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

function getFilterCenterCoords() {
    if (filterTown === 'all') return null;
    if (filterTown === '__gps__') return userGpsCoords;
    if (TOWN_COORDS[filterTown]) return TOWN_COORDS[filterTown];
    for (const it of allItems) {
        if (it.residence === filterTown && it.lat != null && it.lon != null) {
            return [it.lat, it.lon];
        }
    }
    return null;
}

// Naplnění výběrových seznamů Město a Zdroj parte
function populateFilterDropdowns(items) {
    // 1. Města
    const currentSelectedTown = filterTownEl.value;
    const townsSet = new Set(Object.keys(TOWN_COORDS));
    items.forEach(it => {
        if (it.residence) townsSet.add(it.residence);
    });
    const sortedTowns = Array.from(townsSet).sort((a, b) => a.localeCompare(b, 'cs'));

    filterTownEl.innerHTML = `
        <option value="all">Všechna města</option>
        <option value="__gps__">📍 Moje poloha (GPS)</option>
    `;
    sortedTowns.forEach(town => {
        const count = items.filter(i => i.residence === town).length;
        const opt = document.createElement('option');
        opt.value = town;
        opt.textContent = count > 0 ? `${town} (${count})` : town;
        filterTownEl.appendChild(opt);
    });
    if (Array.from(filterTownEl.options).some(o => o.value === currentSelectedTown)) {
        filterTownEl.value = currentSelectedTown;
    }

    // 2. Zdroje parte
    const currentSelectedSource = filterSourceEl.value;
    const sourcesSet = new Set(['PS Horníček']);
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
            const src = item.source_name || 'PS Horníček';
            if (src !== filterSource) return false;
        }

        // 5. Filtr města a vzdálenosti (okolí v km)
        if (filterTown !== 'all') {
            if (filterRadiusKm === 0 && filterTown !== '__gps__') {
                if (item.residence !== filterTown) return false;
            } else if (centerCoords) {
                const itemCoords = getItemCoords(item);
                if (!itemCoords) return false;
                const dist = getDistanceKm(centerCoords[0], centerCoords[1], itemCoords[0], itemCoords[1]);
                if (dist == null || dist > filterRadiusKm) return false;
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
    filterRadiusEl.disabled = (filterTown === 'all');

    const parts = [];
    if (filterGender === 'F') parts.push('♀ Ženy');
    if (filterGender === 'M') parts.push('♂ Muži');
    if (filterTown !== 'all') {
        const townLabel = filterTown === '__gps__' ? 'Moje poloha' : filterTown;
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
        const imgSrc = item.local_image_path ? item.local_image_path : item.image_url;
        const isMemory = item.category === 'Vzpomínka';
        const badgeClass = isMemory ? 'badge-memory' : 'badge-announcement';

        const gInfo = formatGenderSymbol(item.gender);
        const ageSuffix = item.age ? ` (${item.age})` : '';
        const dateText = item.death_date ? item.death_date : '–';
        const causeText = item.death_cause ? item.death_cause : '–';
        const ceremonyText = item.ceremony ? item.ceremony : '–';
        const sourceName = item.source_name || 'PS Horníček';

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

        // Fotografie obličeje pod jménem (pouze pokud je na parte přítomna)
        const faceHtml = item.face_image_path
            ? `<img src="${escapeHtml(item.face_image_path)}" alt="Portrét ${escapeHtml(item.name)}" class="face-portrait" loading="lazy" onerror="this.style.display='none'">`
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
    
    modalImg.src = imgSrc;
    modalImg.classList.remove('zoomed');
    modalDownloadLink.href = imgSrc;
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
    if (e.key === 'Escape') closeModal();
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

filterTownEl.addEventListener('change', (e) => {
    const val = e.target.value;
    if (val === '__gps__') {
        if (!navigator.geolocation) {
            alert('Váš prohlížeč nepodporuje zjištění polohy GPS.');
            filterTownEl.value = 'all';
            filterTown = 'all';
            renderTable();
            return;
        }
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                userGpsCoords = [pos.coords.latitude, pos.coords.longitude];
                filterTown = '__gps__';
                if (filterRadiusKm === 0) {
                    filterRadiusKm = 20;
                    filterRadiusEl.value = '20';
                }
                renderTable();
            },
            () => {
                alert('Nepodařilo se získat vaši polohu. Vyberte prosím město ze seznamu.');
                filterTownEl.value = 'all';
                filterTown = 'all';
                renderTable();
            }
        );
    } else {
        filterTown = val;
        renderTable();
    }
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
    filterTownEl.value = 'all';
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
