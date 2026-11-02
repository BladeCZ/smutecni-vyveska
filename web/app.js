// Smuteční vývěska - Web & PWA Client
let allItems = [];
let currentTab = 'active'; // VÝCHOZÍ JE POUZE AKTUÁLNÍ VÝVĚSKA
let currentSearch = '';
let activeModalItem = null;

// DOM elementy
const searchInput = document.getElementById('searchInput');
const clearSearchBtn = document.getElementById('clearSearch');
const navTabs = document.querySelectorAll('.nav-tab');
const tableBody = document.getElementById('tableBody');
const mobileCards = document.getElementById('mobileCards');
const sectionTitle = document.getElementById('sectionTitle');
const statsBadge = document.getElementById('statsBadge');
const emptyState = document.getElementById('emptyState');
const refreshBtn = document.getElementById('refreshBtn');

// Počítadla
const countActive = document.getElementById('countActive');
const countArchive = document.getElementById('countArchive');

// Modal elementy
const modal = document.getElementById('imageModal');
const modalTitle = document.getElementById('modalTitle');
const modalTownSubtitle = document.getElementById('modalTownSubtitle');
const modalCategory = document.getElementById('modalCategory');
const modalImg = document.getElementById('modalImg');
const modalDetails = document.getElementById('modalDetails');
const modalCloseBtn = document.getElementById('modalCloseBtn');
const modalBackdrop = document.querySelector('.modal-backdrop');
const modalShareBtn = document.getElementById('modalShareBtn');
const modalDownloadLink = document.getElementById('modalDownloadLink');

async function fetchDatabase() {
    statsBadge.textContent = 'Načítám...';
    const candidateUrls = [
        'data/database.json',
        '../data/database.json',
        './database.json'
    ];

    let loadedData = null;
    for (const url of candidateUrls) {
        try {
            const resp = await fetch(url + '?t=' + Date.now());
            if (resp.ok) {
                loadedData = await resp.json();
                break;
            }
        } catch (e) {}
    }

    if (loadedData) {
        allItems = loadedData;
        updateCounts();
        render();
    } else {
        statsBadge.textContent = 'Chyba při načítání databáze';
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

function render() {
    const items = getFilteredItems();
    tableBody.innerHTML = '';
    mobileCards.innerHTML = '';

    const isCurrentActive = currentTab === 'active';
    sectionTitle.textContent = isCurrentActive ? 'Aktuální smuteční oznámení (Vývěska)' : 'Archiv smutečních oznámení';
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

        const residenceText = item.residence ? item.residence : '-';
        const ageText = item.age ? item.age : '-';
        const dateText = item.death_date ? item.death_date : '-';
        const causeText = item.death_cause ? item.death_cause : '-';
        const ceremonyText = item.ceremony ? item.ceremony : '-';

        // 1. ŘÁDEK TABULKY (Desktop / Tablet)
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="td-name">
                <div class="person-name">${escapeHtml(item.name)}</div>
                <div class="person-sub">
                    <span class="badge ${badgeClass}">${escapeHtml(item.category)}</span>
                    ${item.residence ? `<span class="town-inline">📍 ${escapeHtml(item.residence)}</span>` : ''}
                </div>
            </td>
            <td class="td-residence">${escapeHtml(residenceText)}</td>
            <td class="td-age">${escapeHtml(ageText)}</td>
            <td class="td-date">${escapeHtml(dateText)}</td>
            <td class="td-cause">${escapeHtml(causeText)}</td>
            <td class="td-ceremony">${escapeHtml(ceremonyText)}</td>
            <td class="text-right">
                <button class="btn-detail" title="Zobrazit celé parte">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                        <circle cx="12" cy="12" r="3"></circle>
                    </svg>
                    <span>Detail</span>
                </button>
            </td>
        `;

        tr.addEventListener('click', () => openModal(item, imgSrc));
        tableBody.appendChild(tr);

        // 2. KARTA PRO MOBIL (Mobile View)
        const card = document.createElement('div');
        card.className = 'mobile-card';
        card.innerHTML = `
            <div class="mobile-card-top">
                <div>
                    <div class="mobile-card-name">${escapeHtml(item.name)}</div>
                    ${item.residence ? `<div class="mobile-card-town">📍 ${escapeHtml(item.residence)}</div>` : ''}
                </div>
                <span class="badge ${badgeClass}">${escapeHtml(item.category)}</span>
            </div>

            <div class="mobile-card-info-grid">
                <div class="mobile-card-info-item">
                    <span class="info-label">Věk</span>
                    <span class="info-val">${escapeHtml(ageText)}</span>
                </div>
                <div class="mobile-card-info-item">
                    <span class="info-label">Datum úmrtí</span>
                    <span class="info-val">${escapeHtml(dateText)}</span>
                </div>
                <div class="mobile-card-info-item" style="grid-column: span 2;">
                    <span class="info-label">Důvod / Okolnost</span>
                    <span class="info-val">${escapeHtml(causeText)}</span>
                </div>
            </div>

            <div class="mobile-card-ceremony">
                <strong>Poslední rozloučení / Pohřeb:</strong>
                <span>${escapeHtml(ceremonyText)}</span>
            </div>

            <button class="btn-detail-mobile">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                    <circle cx="12" cy="12" r="3"></circle>
                </svg>
                <span>Otevřít obrázek parte</span>
            </button>
        `;

        card.addEventListener('click', () => openModal(item, imgSrc));
        mobileCards.appendChild(card);
    });
}

function openModal(item, imgSrc) {
    activeModalItem = item;
    modalTitle.textContent = item.name;
    modalTownSubtitle.textContent = item.residence ? `📍 ${item.residence}` : '';
    modalCategory.textContent = item.category;
    modalCategory.className = `badge ${item.category === 'Vzpomínka' ? 'badge-memory' : 'badge-announcement'}`;
    
    modalImg.src = imgSrc;
    modalImg.classList.remove('zoomed');
    modalDownloadLink.href = imgSrc;
    modalDownloadLink.download = `parte_${sanitizeFilename(item.name)}.jpg`;

    // Sestavení detailů
    let detailsHtml = '';
    if (item.residence) {
        detailsHtml += `<div class="detail-row"><strong>Město / Bydliště:</strong> ${escapeHtml(item.residence)}</div>`;
    }
    if (item.age) {
        detailsHtml += `<div class="detail-row"><strong>Věk:</strong> ${escapeHtml(item.age)}</div>`;
    }
    if (item.death_date) {
        detailsHtml += `<div class="detail-row"><strong>Datum úmrtí:</strong> ${escapeHtml(item.death_date)}</div>`;
    }
    if (item.death_cause) {
        detailsHtml += `<div class="detail-row"><strong>Důvod úmrtí:</strong> ${escapeHtml(item.death_cause)}</div>`;
    }
    if (item.ceremony) {
        detailsHtml += `<div class="detail-row" style="grid-column: span 2;"><strong>Poslední rozloučení:</strong> ${escapeHtml(item.ceremony)}</div>`;
    }
    detailsHtml += `<div class="detail-row" style="grid-column: span 2;"><strong>Odkaz:</strong> <a href="${item.image_url}" target="_blank" rel="noopener" style="color:var(--accent);">Otevřít původní parte na webu PS Horníček ↗</a></div>`;

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

// Zvětšení obrázku kliknutím v modalu
modalImg.addEventListener('click', () => {
    modalImg.classList.toggle('zoomed');
});

// Zavření modalu
modalCloseBtn.addEventListener('click', closeModal);
modalBackdrop.addEventListener('click', closeModal);
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeModal();
});

// Sdílení
modalShareBtn.addEventListener('click', async () => {
    if (!activeModalItem) return;
    const shareData = {
        title: `Parte - ${activeModalItem.name}`,
        text: `Parte: ${activeModalItem.name} (${activeModalItem.residence || ''})\nDatum: ${activeModalItem.death_date || ''}\nPohřeb: ${activeModalItem.ceremony || ''}`,
        url: window.location.href
    };

    if (navigator.share) {
        try {
            await navigator.share(shareData);
        } catch (err) {
            if (err.name !== 'AbortError') console.error('Share failed:', err);
        }
    } else {
        navigator.clipboard.writeText(window.location.href);
        alert('Odkaz na parte byl zkopírován do schránky.');
    }
});

// Přepínání záložek
navTabs.forEach(tab => {
    tab.addEventListener('click', () => {
        navTabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        currentTab = tab.dataset.tab;
        render();
    });
});

// Vyhledávání
searchInput.addEventListener('input', (e) => {
    currentSearch = e.target.value.trim();
    clearSearchBtn.classList.toggle('visible', currentSearch.length > 0);
    render();
});

clearSearchBtn.addEventListener('click', () => {
    searchInput.value = '';
    currentSearch = '';
    clearSearchBtn.classList.remove('visible');
    render();
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

if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('sw.js').catch(err => {
            console.log('SW registration failed:', err);
        });
    });
}

fetchDatabase();
