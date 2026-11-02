// Smuteční vývěska - Klientská aplikace
let allItems = [];
let currentTab = 'active'; // VÝCHOZÍ JE VÝHRADNĚ AKTUÁLNÍ VÝVĚSKA
let currentSearch = '';
let activeModalItem = null;

// DOM prvky
const searchInput = document.getElementById('searchInput');
const clearSearchBtn = document.getElementById('clearSearch');
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
    const activeCount = allItems.filter(i => i.is_active).length;
    const archiveCount = allItems.filter(i => i.is_archived).length;
    countActive.textContent = activeCount;
    countArchive.textContent = archiveCount;
}

function getFilteredItems() {
    return allItems.filter(item => {
        // Filtr podle záložky: Aktuální vývěska vs Archiv
        if (currentTab === 'active' && !item.is_active) return false;
        if (currentTab === 'archive' && !item.is_archived) return false;

        // Filtr vyhledávání
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
}

// Vykreslení standardní tabulky roztažené na celou šířku
function renderTable() {
    const items = getFilteredItems();
    tableBody.innerHTML = '';

    const isCurrentActive = currentTab === 'active';
    sectionTitle.textContent = isCurrentActive ? 'Aktuální smuteční oznámení (Vývěska)' : 'Archiv parte';
    statsBadge.textContent = `${items.length} ${getNoun(items.length, 'parte', 'parte', 'parte')}`;

    if (items.length === 0) {
        emptyState.style.display = 'block';
        return;
    }

    emptyState.style.display = 'none';

    items.forEach(item => {
        const imgSrc = item.local_image_path ? item.local_image_path : item.image_url;
        const isMemory = item.category === 'Vzpomínka';
        const badgeClass = isMemory ? 'badge-memory' : 'badge-announcement';

        const ageText = item.age ? item.age : '–';
        const dateText = item.death_date ? item.death_date : '–';
        const causeText = item.death_cause ? item.death_cause : '–';
        const ceremonyText = item.ceremony ? item.ceremony : '–';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>
                <div class="cell-name-box">
                    <span class="cell-name">${escapeHtml(item.name)}</span>
                    <div class="cell-sub">
                        ${item.residence ? `<span class="cell-town-under-name">📍 ${escapeHtml(item.residence)}</span>` : ''}
                        <span class="badge ${badgeClass}">${escapeHtml(item.category)}</span>
                    </div>
                </div>
            </td>
            <td class="cell-age">${escapeHtml(ageText)}</td>
            <td class="cell-date">${escapeHtml(dateText)}</td>
            <td class="cell-cause">${escapeHtml(causeText)}</td>
            <td class="cell-ceremony">${escapeHtml(ceremonyText)}</td>
            <td class="cell-action">
                <button class="btn-open-parte" title="Otevřít originální obrázek parte">
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
    modalTitle.textContent = item.name;
    modalTownSubtitle.textContent = item.residence ? `📍 Bydliště: ${item.residence} (${item.category})` : item.category;
    
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
    if (item.ceremony) {
        detailsHtml += `<div class="meta-field" style="grid-column: span 2;"><strong>Poslední rozloučení:</strong> ${escapeHtml(item.ceremony)}</div>`;
    }
    detailsHtml += `<div class="meta-field" style="grid-column: span 2;"><strong>Původní odkaz:</strong> <a href="${item.image_url}" target="_blank" rel="noopener" style="color:var(--gold-light);">Otevřít originál na pshornicek.cz ↗</a></div>`;

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

navTabs.forEach(tab => {
    tab.addEventListener('click', () => {
        navTabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        currentTab = tab.dataset.tab;
        renderTable();
    });
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
