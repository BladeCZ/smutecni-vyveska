// Smuteční vývěska - Web & PWA Client
let allItems = [];
let currentFilter = 'all';
let currentSearch = '';
let activeModalItem = null;

// DOM elementy
const searchInput = document.getElementById('searchInput');
const clearSearchBtn = document.getElementById('clearSearch');
const filterChips = document.querySelectorAll('.chip');
const cardsGrid = document.getElementById('cardsGrid');
const statsBar = document.getElementById('statsBar');
const emptyState = document.getElementById('emptyState');
const refreshBtn = document.getElementById('refreshBtn');

// Modal elementy
const modal = document.getElementById('imageModal');
const modalTitle = document.getElementById('modalTitle');
const modalCategory = document.getElementById('modalCategory');
const modalImg = document.getElementById('modalImg');
const modalDetails = document.getElementById('modalDetails');
const modalCloseBtn = document.getElementById('modalCloseBtn');
const modalBackdrop = document.querySelector('.modal-backdrop');
const modalShareBtn = document.getElementById('modalShareBtn');
const modalDownloadLink = document.getElementById('modalDownloadLink');

// Počítadla
const countAll = document.getElementById('countAll');
const countActive = document.getElementById('countActive');
const countAnnounce = document.getElementById('countAnnounce');
const countMemory = document.getElementById('countMemory');
const countArchive = document.getElementById('countArchive');

// Zjištění správné cesty k datům (GitHub Pages vs lokální otevření)
async function fetchDatabase() {
    statsBar.textContent = 'Načítám aktuální data...';
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
        } catch (e) {
            // pokračovat na další URL
        }
    }

    if (loadedData) {
        allItems = loadedData;
        updateCounts();
        render();
        statsBar.textContent = `Zobrazeno ${getFilteredItems().length} z celkem ${allItems.length} parte.`;
    } else {
        statsBar.textContent = 'Chyba při načítání databáze. Zkuste to prosím znovu.';
    }
}

function updateCounts() {
    countAll.textContent = allItems.length;
    countActive.textContent = allItems.filter(i => i.is_active).length;
    countAnnounce.textContent = allItems.filter(i => i.category === 'Smuteční oznámení').length;
    countMemory.textContent = allItems.filter(i => i.category === 'Vzpomínka').length;
    countArchive.textContent = allItems.filter(i => i.is_archived).length;
}

function getFilteredItems() {
    return allItems.filter(item => {
        // Filtr podle kategorie / statusu
        if (currentFilter === 'active' && !item.is_active) return false;
        if (currentFilter === 'archive' && !item.is_archived) return false;
        if (currentFilter === 'announcement' && item.category !== 'Smuteční oznámení') return false;
        if (currentFilter === 'memory' && item.category !== 'Vzpomínka') return false;

        // Filtr podle vyhledávacího textu
        if (currentSearch) {
            const q = currentSearch.toLowerCase();
            const matchName = (item.name || '').toLowerCase().includes(q);
            const matchResidence = (item.residence || '').toLowerCase().includes(q);
            const matchDeath = (item.death_date || '').toLowerCase().includes(q);
            const matchTitle = (item.original_title || '').toLowerCase().includes(q);
            const matchRaw = (item.raw_text || '').toLowerCase().includes(q);
            if (!matchName && !matchResidence && !matchDeath && !matchTitle && !matchRaw) {
                return false;
            }
        }
        return true;
    });
}

function render() {
    const items = getFilteredItems();
    cardsGrid.innerHTML = '';

    if (items.length === 0) {
        emptyState.style.display = 'block';
        statsBar.textContent = 'Nenalezeny žádné záznamy.';
        return;
    }

    emptyState.style.display = 'none';
    statsBar.textContent = `Nalezeno ${items.length} parte.`;

    items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'card';

        // Určení URL obrázku (přednost má lokální stažený v repozitáři, fallback na originální URL)
        const imgSrc = item.local_image_path ? item.local_image_path : item.image_url;

        const isMemory = item.category === 'Vzpomínka';
        const badgeClass = isMemory ? 'badge-memory' : 'badge-announcement';
        const activeBadge = item.is_active ? '<span class="badge badge-active">Vývěska</span>' : '';

        card.innerHTML = `
            <div class="card-img-wrap">
                <img src="${imgSrc}" alt="${escapeHtml(item.name)}" loading="lazy" onerror="this.src='${item.image_url}'">
                <div class="card-badges">
                    <span class="badge ${badgeClass}">${escapeHtml(item.category)}</span>
                    ${activeBadge}
                </div>
            </div>
            <div class="card-body">
                <h3 class="card-title">${escapeHtml(item.name)}</h3>
                <div class="card-meta">
                    ${item.residence ? `<div class="meta-item">📍 ${escapeHtml(item.residence)}</div>` : ''}
                    ${item.death_date ? `<div class="meta-item">🕊️ ${escapeHtml(item.death_date)}</div>` : ''}
                    ${item.age ? `<div class="meta-item">⏳ Věk: ${escapeHtml(item.age)}</div>` : ''}
                </div>
                <div class="card-footer">
                    <span>${item.is_active ? 'Aktuální parte' : 'Archiv'}</span>
                    <span class="view-btn">Zobrazit detail →</span>
                </div>
            </div>
        `;

        card.addEventListener('click', () => openModal(item, imgSrc));
        cardsGrid.appendChild(card);
    });
}

function openModal(item, imgSrc) {
    activeModalItem = item;
    modalTitle.textContent = item.name;
    modalCategory.textContent = item.category;
    modalCategory.className = `badge ${item.category === 'Vzpomínka' ? 'badge-memory' : 'badge-announcement'}`;
    
    modalImg.src = imgSrc;
    modalImg.classList.remove('zoomed');
    modalDownloadLink.href = imgSrc;
    modalDownloadLink.download = `parte_${sanitizeFilename(item.name)}.jpg`;

    // Sestavení detailů
    let detailsHtml = '';
    if (item.death_date) {
        detailsHtml += `<div class="detail-row"><strong>Datum úmrtí:</strong> ${escapeHtml(item.death_date)}</div>`;
    }
    if (item.age) {
        detailsHtml += `<div class="detail-row"><strong>Věk:</strong> ${escapeHtml(item.age)}</div>`;
    }
    if (item.residence) {
        detailsHtml += `<div class="detail-row"><strong>Bydliště:</strong> ${escapeHtml(item.residence)}</div>`;
    }
    if (item.ceremony) {
        detailsHtml += `<div class="detail-row"><strong>Poslední rozloučení:</strong> ${escapeHtml(item.ceremony)}</div>`;
    }
    detailsHtml += `<div class="detail-row"><strong>Původní odkaz:</strong> <a href="${item.image_url}" target="_blank" rel="noopener" style="color:var(--accent);">Otevřít originál</a></div>`;

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

// Sdílení (Web Share API)
modalShareBtn.addEventListener('click', async () => {
    if (!activeModalItem) return;
    const shareData = {
        title: `Smuteční oznámení - ${activeModalItem.name}`,
        text: `Parte: ${activeModalItem.name} (${activeModalItem.category})`,
        url: window.location.href
    };

    if (navigator.share) {
        try {
            await navigator.share(shareData);
        } catch (err) {
            if (err.name !== 'AbortError') console.error('Share failed:', err);
        }
    } else {
        // Fallback zkopírování odkazu do schránky
        navigator.clipboard.writeText(window.location.href);
        alert('Odkaz byl zkopírován do schránky.');
    }
});

// Filtrování
filterChips.forEach(chip => {
    chip.addEventListener('click', () => {
        filterChips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        currentFilter = chip.dataset.filter;
        render();
    });
});

// Hledání
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

// Helpery
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

// Registrace Service Workeru pro PWA (offline použití)
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('sw.js').catch(err => {
            console.log('ServiceWorker registration failed: ', err);
        });
    });
}

// Spustit načtení
fetchDatabase();
