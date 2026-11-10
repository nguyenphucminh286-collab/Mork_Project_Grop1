/**
 * SAVORA FINE DINING - HOME PAGE CONTROLLER (assets/js/public/home.js)
 */

const SAVORA_MENU_DATA = [
    {
        id: 1,
        category: 'Starters & Salads',
        name: 'Pan-Seared Norwegian Salmon Salad',
        description: 'Pan-seared Norwegian salmon fillet served over organic Da Lat greens with tangy passion fruit vinaigrette.',
        price: 165000,
        discountPrice: 149000,
        prepTime: 10,
        isSignature: true,
        image: 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=800&q=80'
    },
    {
        id: 2,
        category: 'Starters & Salads',
        name: 'Roasted Kabocha Pumpkin & Black Truffle Soup',
        description: 'Velvety Japanese pumpkin bisque finished with French heavy cream, toasted pumpkin seeds, and black truffle oil.',
        price: 95000,
        discountPrice: null,
        prepTime: 10,
        isSignature: false,
        image: 'https://images.unsplash.com/photo-1547592166-23ac45744acd?auto=format&fit=crop&w=800&q=80'
    },
    {
        id: 3,
        category: 'Prime Steaks & Seafood',
        name: ' Lava-Stone Grilled Australian Tenderloin (250g)',
        description: 'Prime grass-fed Australian beef tenderloin grilled medium-rare on volcanic stone, served with truffle mash and Kampot peppercorn jus.',
        price: 420000,
        discountPrice: 389000,
        prepTime: 20,
        isSignature: true,
        image: 'https://images.unsplash.com/photo-1558030006-450675393462?auto=format&fit=crop&w=800&q=80'
    },
    {
        id: 4,
        category: 'Prime Steaks & Seafood',
        name: 'Oven-Baked Spiny Lobster with Mozzarella',
        description: 'Whole fresh Nha Trang spiny lobster baked with aged Parmigiano-Reggiano, melted mozzarella, and garlic herb butter.',
        price: 690000,
        discountPrice: 650000,
        prepTime: 25,
        isSignature: true,
        image: 'https://images.unsplash.com/photo-1559737558-2f5a35f4523b?auto=format&fit=crop&w=800&q=80'
    },
    {
        id: 5,
        category: 'Hotpot & Grill',
        name: 'Savora Royal Seafood Hotpot Set',
        description: 'Aromatic lemongrass & galangal broth accompanied by tiger prawns, squid, octopus, Manila clams, and seasonal wild mushrooms.',
        price: 450000,
        discountPrice: null,
        prepTime: 15,
        isSignature: true,
        image: 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=800&q=80'
    },
    {
        id: 6,
        category: 'Artisan Desserts',
        name: 'Belgian Molten Chocolate Lava Cake & Gelato',
        description: 'Warm 70% dark Belgian chocolate fondant with a liquid ganache center, paired with Madagascar vanilla bean gelato.',
        price: 89000,
        discountPrice: null,
        prepTime: 12,
        isSignature: false,
        image: 'https://images.unsplash.com/photo-1606313564200-e75d5e30476c?auto=format&fit=crop&w=800&q=80'
    },
    {
        id: 7,
        category: 'Wines & Cocktails',
        name: 'Savora Signature Peach, Orange & Lemongrass Brew',
        description: 'Cold-brewed artisanal black tea infused with crisp white peach slices, Valencia orange zest, and fresh lemongrass.',
        price: 65000,
        discountPrice: null,
        prepTime: 5,
        isSignature: false,
        image: 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=800&q=80'
    }
];

const SAVORA_TABLES_DATA = [
    { code: 'T1-B01', name: 'Table 01 — Window Side', area: '1st Floor', capacity: 2, status: 'AVAILABLE', note: 'Romantic window-side table for two' },
    { code: 'T1-B02', name: 'Table 02 — Main Hall', area: '1st Floor', capacity: 4, status: 'AVAILABLE', note: 'Standard family booth (4 seats)' },
    { code: 'T1-B03', name: 'Table 03 — Main Hall', area: '1st Floor', capacity: 4, status: 'OCCUPIED', note: 'Active dine-in order in progress' },
    { code: 'T1-B04', name: 'Table 04 — Main Hall', area: '1st Floor', capacity: 4, status: 'CLEANING', note: 'Guests departed — staff sanitizing table' },
    { code: 'T2-B01', name: 'Table 01 — Garden Terrace', area: '2nd Floor', capacity: 6, status: 'RESERVED', note: 'Reserved for 19:00 – 21:00 slot (120 mins)' },
    { code: 'T2-B02', name: 'Table 02 — Garden Terrace', area: '2nd Floor', capacity: 8, status: 'AVAILABLE', note: 'Spacious group dining table overlooking terrace' },
    { code: 'VIP-01', name: 'VIP Lotus Private Suite', area: 'VIP Zone', capacity: 12, status: 'AVAILABLE', note: 'Private suite with projector & sound system' },
    { code: 'VIP-02', name: 'VIP Royal Banquet Room', area: 'VIP Zone', capacity: 16, status: 'MAINTENANCE', note: 'Scheduled air-conditioning maintenance' }
];

document.addEventListener('DOMContentLoaded', async () => {
    let activeCategory = 'ALL';
    let searchKeyword = '';

    // 1. Check current authenticated user session
    await initSessionBanner();

    // 2. Initialize default reservation date to today
    const dateInput = document.getElementById('filter-date');
    if (dateInput) {
        dateInput.value = new Date().toISOString().split('T')[0];
    }

    // 3. Render Signature Menu & Category Tabs
    renderMenu();
    setupMenuFilters();

    // 4. Render Live Dining Tables & Filters
    renderTables();
    setupTableFilters();

    // 5. Setup Voucher Copy Buttons
    setupVoucherButtons();

    async function initSessionBanner() {
        const banner = document.getElementById('session-banner');
        const content = document.getElementById('session-banner-content');
        if (!banner || !content) return;

        try {
            const res = await SavoraApp.fetchApi('/api/auth/me');
            const u = res.data;
            if (!u) return;

            banner.style.display = 'block';
            content.innerHTML = `
                <div class="session-user">
                    <img src="${u.avatarUrl || 'https://res.cloudinary.com/dej5mxdrt/image/upload/v1780061324/OIP_dbbjuo.jpg'}"
                         alt="${u.fullName}" class="session-user__avatar">
                    <div class="session-user__info">
                        <strong>Welcome back, ${u.fullName}!</strong>
                        <span>Signed in as ${u.email} (User ID: #${u.userId})</span>
                    </div>
                </div>
                <div class="session-badges">
                    <span class="role-pill">ROLE: ${u.roleName}</span>
                    <a href="#reservation" class="btn btn-primary" style="padding: 8px 16px; font-size: 0.82rem;">Book a Table</a>
                </div>
            `;
        } catch (_) {
            banner.style.display = 'none';
        }
    }

    function renderMenu() {
        const grid = document.getElementById('menu-grid');
        if (!grid) return;

        const filtered = SAVORA_MENU_DATA.filter((dish) => {
            const matchesCat = activeCategory === 'ALL' || dish.category === activeCategory;
            const matchesSearch = !searchKeyword
                || dish.name.toLowerCase().includes(searchKeyword)
                || dish.description.toLowerCase().includes(searchKeyword)
                || dish.category.toLowerCase().includes(searchKeyword);
            return matchesCat && matchesSearch;
        });

        if (filtered.length === 0) {
            grid.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 48px 20px; background: #fff; border-radius: 16px; border: 1px solid var(--border-color);">
                    <h3 style="font-family: var(--font-heading); margin-bottom: 8px;">No dishes match your filter</h3>
                    <p style="color: var(--text-muted);">Try clearing your search keyword or selecting another culinary category.</p>
                </div>
            `;
            return;
        }

        grid.innerHTML = filtered.map((dish) => {
            const finalPrice = dish.discountPrice || dish.price;
            return `
                <article class="menu-card">
                    <div class="menu-card__media">
                        <img src="${dish.image}" alt="${dish.name}" class="menu-card__img" loading="lazy">
                        ${dish.isSignature ? '<span class="menu-card__badge">★ CHEF’S SIGNATURE</span>' : ''}
                        <span class="menu-card__time">⏱ ${dish.prepTime} mins</span>
                    </div>
                    <div class="menu-card__body">
                        <span class="menu-card__cat">${dish.category}</span>
                        <h3 class="menu-card__title">${dish.name}</h3>
                        <p class="menu-card__desc">${dish.description}</p>
                        <div class="menu-card__footer">
                            <div class="menu-card__price-group">
                                <span class="menu-card__price">${SavoraApp.formatCurrency(finalPrice)}</span>
                                ${dish.discountPrice ? `<span class="menu-card__old-price">${SavoraApp.formatCurrency(dish.price)}</span>` : ''}
                            </div>
                            <a href="#reservation" class="btn btn-outline" style="padding: 7px 14px; font-size: 0.82rem;">
                                Reserve to Order
                            </a>
                        </div>
                    </div>
                </article>
            `;
        }).join('');
    }

    function setupMenuFilters() {
        const tabs = document.querySelectorAll('.category-tab');
        tabs.forEach((tab) => {
            tab.addEventListener('click', () => {
                tabs.forEach((t) => t.classList.remove('active'));
                tab.classList.add('active');
                activeCategory = tab.dataset.category || 'ALL';
                renderMenu();
            });
        });

        const searchInput = document.getElementById('menu-search-input');
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                searchKeyword = e.target.value.trim().toLowerCase();
                renderMenu();
            });
        }
    }

    function renderTables() {
        const grid = document.getElementById('table-grid');
        if (!grid) return;

        const areaFilter = document.getElementById('filter-area')?.value || 'ALL';
        const guestsFilter = parseInt(document.getElementById('filter-guests')?.value || '0', 10);
        const onlyAvailable = document.getElementById('filter-only-available')?.value === 'AVAILABLE';

        const filtered = SAVORA_TABLES_DATA.filter((t) => {
            const matchArea = areaFilter === 'ALL' || t.area === areaFilter;
            const matchCapacity = t.capacity >= guestsFilter;
            const matchStatus = !onlyAvailable || t.status === 'AVAILABLE';
            return matchArea && matchCapacity && matchStatus;
        });

        grid.innerHTML = filtered.map((t) => {
            const isBookable = t.status === 'AVAILABLE';
            return `
                <div class="table-card status-${t.status}">
                    <div>
                        <div class="table-card__top">
                            <span class="table-card__code">${t.code}</span>
                            <span class="table-status-badge badge-status-${t.status}">${t.status}</span>
                        </div>
                        <h4 class="table-card__name" style="margin-top: 12px;">${t.name}</h4>
                        <div class="table-card__meta" style="margin-top: 6px;">
                            <span>📍 ${t.area}</span>
                            <span>👥 Up to ${t.capacity} Guests</span>
                        </div>
                        <p class="table-card__note" style="margin-top: 8px;">${t.note}</p>
                    </div>
                    <div style="margin-top: 10px;">
                        ${isBookable
                            ? `<button type="button" class="btn btn-primary btn-book-table" data-code="${t.code}" data-name="${t.name}" style="width: 100%; padding: 9px 14px; font-size: 0.84rem;">Select Table</button>`
                            : `<button type="button" class="btn" disabled style="width: 100%; padding: 9px 14px; font-size: 0.82rem; background: #ece8e2; color: #8c8278; cursor: not-allowed;">Unavailable (${t.status})</button>`
                        }
                    </div>
                </div>
            `;
        }).join('');

        document.querySelectorAll('.btn-book-table').forEach((btn) => {
            btn.addEventListener('click', () => {
                showToast(`Selected ${btn.dataset.code} (${btn.dataset.name}). Default reservation duration: 120 minutes.`);
            });
        });
    }

    function setupTableFilters() {
        ['filter-area', 'filter-guests', 'filter-only-available', 'filter-date'].forEach((id) => {
            const el = document.getElementById(id);
            if (el) {
                el.addEventListener('change', renderTables);
            }
        });
    }

    function setupVoucherButtons() {
        document.querySelectorAll('.btn-copy-voucher').forEach((btn) => {
            btn.addEventListener('click', () => {
                const code = btn.dataset.code;
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(code);
                }
                showToast(`Copied voucher code "${code}" to clipboard!`);
            });
        });
    }

    function showToast(message) {
        const toast = document.getElementById('home-toast');
        if (!toast) return;
        toast.textContent = message;
        toast.style.display = 'block';
        clearTimeout(window.__savoraToastTimer);
        window.__savoraToastTimer = setTimeout(() => {
            toast.style.display = 'none';
        }, 3500);
    }
});
