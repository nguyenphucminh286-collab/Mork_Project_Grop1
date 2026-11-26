/**
 * SAVORA — Executive Admin Portal & Analytics Dashboard
 */
document.addEventListener('DOMContentLoaded', () => {
    const $ = (id) => document.getElementById(id);
    let overviewData = {
        kpis: {},
        orders: [],
        reservations: [],
        dishes: [],
        categories: [],
        tables: [],
        vouchers: [],
        users: [],
        activityLogs: []
    };

    const showToast = (msg) => {
        const toast = $('home-toast');
        if (!toast) return;
        toast.textContent = msg;
        toast.style.display = 'block';
        clearTimeout(window.__admToastTimer);
        window.__admToastTimer = setTimeout(() => { toast.style.display = 'none'; }, 3400);
    };

    const sectionLinks = [...document.querySelectorAll('#admin-tabs-nav [data-tab]')];
    const menuToggle = $('admin-nav-toggle');
    let activeSection;
    function openSection(id, focusHeading = false) {
        const link = sectionLinks.find(item => item.dataset.tab === id) || sectionLinks[0];
        activeSection = link.dataset.tab;
        sectionLinks.forEach(item => {
            item.classList.toggle('active', item === link);
            if (item === link) item.setAttribute('aria-current', 'page');
            else item.removeAttribute('aria-current');
        });
        document.querySelectorAll('.admin-tab-pane').forEach(pane => {
            pane.classList.toggle('active', pane.id === activeSection);
        });
        const title = link.firstElementChild.textContent;
        $('admin-section-group').textContent = link.closest('.admin-nav-group').querySelector('h2').textContent;
        $('admin-page-title').textContent = title;
        document.title = title + ' — Savora Admin';
        menuToggle.setAttribute('aria-expanded', 'false');
        if (focusHeading) {
            $('admin-page-title').focus({preventScroll: true});
            window.scrollTo(0, 0);
        }
        if (activeSection === 'tab-feedback') loadFeedback();
        if (activeSection === 'tab-ai') loadAiSettings();
    }
    sectionLinks.forEach(link => link.addEventListener('click', event => {
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        if (location.hash !== link.hash) history.pushState(null, '', link.hash);
        openSection(link.dataset.tab, true);
    }));
    menuToggle.addEventListener('click', () => {
        menuToggle.setAttribute('aria-expanded', String(menuToggle.getAttribute('aria-expanded') !== 'true'));
    });
    window.addEventListener('popstate', () => openSection(location.hash.slice(1), true));

    const loadAdminOverview = async () => {
        try {
            const res = await SavoraApp.fetchApi('/api/admin/overview');
            overviewData = res.data || overviewData;
            renderKpis();
            renderRevenueCharts();
            renderOrderTableFilterOptions();
            renderOrdersTable();
            renderReservationTableFilterOptions();
            renderReservationsTable();
            renderCategoryOptions();
            renderDishesTable();
            renderTablesTable();
            renderVouchersTable();
            renderUsersTable();
            renderTrackingTables();
        } catch (err) {
            showToast(err.message || 'Unable to load admin overview.');
        }
    };

    const renderKpis = () => {
        const k = overviewData.kpis || {};
        if ($('adm-kpi-revenue')) $('adm-kpi-revenue').textContent = SavoraApp.formatCurrency(k.totalRevenue || 0);
        if ($('adm-kpi-today-revenue')) $('adm-kpi-today-revenue').textContent = `Today: ${SavoraApp.formatCurrency(k.todayRevenue || 0)}`;
        if ($('adm-kpi-orders')) $('adm-kpi-orders').textContent = String(k.paidOrdersCount || 0);
        if ($('adm-kpi-active-orders')) $('adm-kpi-active-orders').textContent = `${k.totalOrdersCount || 0} total · ${k.activeOrdersCount || 0} active`;
        if ($('adm-kpi-bookings')) $('adm-kpi-bookings').textContent = String(k.totalReservationsCount || 0);
        if ($('adm-kpi-dishes-sub')) $('adm-kpi-dishes-sub').textContent = `${k.availableDishesCount || 0} available on menu`;
        if ($('adm-kpi-tables-sub')) $('adm-kpi-tables-sub').textContent = `${k.availableTablesCount || 0} available • ${k.occupiedTablesCount || 0} occupied`;
    };

    // 0. Interactive Daily Revenue & Payment Channel Charts
    const renderRevenueCharts = () => {
        const dailyBox = $('adm-daily-revenue-chart');
        const breakdownBox = $('adm-payment-breakdown-chart');
        const chartKpis = $('adm-chart-kpis');
        if (!dailyBox || !breakdownBox) return;

        const paidOrders = (overviewData.orders || []).filter(
            (o) => o.status === 'PAID' || o.status === 'CLOSED'
        );

        // Build last 7 days array ending today
        const days = [];
        const now = new Date();
        for (let i = 6; i >= 0; i--) {
            const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
            const yyyy = d.getFullYear();
            const mm = String(d.getMonth() + 1).padStart(2, '0');
            const dd = String(d.getDate()).padStart(2, '0');
            const key = `${yyyy}-${mm}-${dd}`;
            const label = d.toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit' });
            const weekday = d.toLocaleDateString('en-US', { weekday: 'short' });
            days.push({ key, label: `${weekday} ${label}`, revenue: 0, bills: 0, isToday: i === 0 });
        }

        let bankRevenue = 0;
        let cashRevenue = 0;
        const dishRevMap = {};

        paidOrders.forEach((o) => {
            const amt = Number(o.totalAmount || 0);
            const rawDate = (o.closedAt || o.createdAt || '').slice(0, 10);
            const daySlot = days.find((d) => d.key === rawDate);
            if (daySlot) {
                daySlot.revenue += amt;
                daySlot.bills += 1;
            } else if (days.length) {
                // Group older orders into earliest visible bucket if needed
                days[0].revenue += amt;
                days[0].bills += 1;
            }

            if (o.paymentMethod === 'BANK_TRANSFER' || o.paymentMethod === 'VN_PAY') {
                bankRevenue += amt;
            } else {
                cashRevenue += amt;
            }

            (o.items || []).forEach((it) => {
                const name = it.dishName || 'Dish';
                if (!dishRevMap[name]) dishRevMap[name] = { qty: 0, rev: 0 };
                dishRevMap[name].qty += Number(it.quantity || 1);
                dishRevMap[name].rev += Number(it.lineTotal || 0);
            });
        });

        const totalRev = Math.max(1, bankRevenue + cashRevenue);
        const maxDayRev = Math.max(1, ...days.map((d) => d.revenue));
        const avgBill = paidOrders.length ? Math.round((bankRevenue + cashRevenue) / paidOrders.length) : 0;

        if (chartKpis) {
            chartKpis.innerHTML = `
                <span class="adm-chart-pill">Average invoice: <strong>${SavoraApp.formatCurrency(avgBill)}</strong></span>
                <span class="adm-chart-pill">Peak day: <strong>${SavoraApp.formatCurrency(Math.max(...days.map(d => d.revenue)))}</strong></span>
            `;
        }

        dailyBox.innerHTML = `
            <div class="adm-bars-grid">
                ${days.map((d) => {
                    const heightPct = (d.revenue / maxDayRev) * 100;
                    const shortAmt = d.revenue >= 1000000
                        ? `${(d.revenue / 1000000).toFixed(2)}M`
                        : (d.revenue > 0 ? `${Math.round(d.revenue / 1000)}k` : '0');
                    return `
                        <div class="adm-bar-col" title="${d.label}: ${SavoraApp.formatCurrency(d.revenue)} (${d.bills} paid bill(s))">
                            <span class="adm-bar-amount">${shortAmt}</span>
                            <div class="adm-bar-track">
                                <div class="adm-bar-fill ${d.isToday ? 'is-today' : ''}" style="height: ${heightPct}%;"></div>
                            </div>
                        </div>
                    `;
                }).join('')}
            </div>
            <div class="adm-bar-labels">
                ${days.map((d) => `
                    <div>
                        <div class="adm-bar-day">${d.isToday ? 'Today' : d.label}</div>
                        <div class="adm-bar-bills">${d.bills} bill${d.bills === 1 ? '' : 's'}</div>
                    </div>
                `).join('')}
            </div>
        `;

        const bankPct = paidOrders.length ? Math.round((bankRevenue / totalRev) * 100) : 0;
        const cashPct = paidOrders.length ? (100 - bankPct) : 0;

        const topDishes = Object.entries(dishRevMap)
            .sort((a, b) => b[1].rev - a[1].rev)
            .slice(0, 3);

        breakdownBox.innerHTML = `
            <div class="adm-breakdown-section">
                <div class="adm-breakdown-item">
                    <div class="adm-breakdown-head">
                        <span>🏦 VietQR Bank Transfer</span>
                        <span style="color:#1565c0;">${SavoraApp.formatCurrency(bankRevenue)} (${bankPct}%)</span>
                    </div>
                    <div class="adm-progress-track">
                        <div class="adm-progress-fill" style="width:${bankPct}%;background:linear-gradient(90deg,#1976d2,#42a5f5);"></div>
                    </div>
                </div>

                <div class="adm-breakdown-item">
                    <div class="adm-breakdown-head">
                        <span>💵 Cash Payment</span>
                        <span style="color:#2e7d32;">${SavoraApp.formatCurrency(cashRevenue)} (${cashPct}%)</span>
                    </div>
                    <div class="adm-progress-track">
                        <div class="adm-progress-fill" style="width:${cashPct}%;background:linear-gradient(90deg,#2e7d32,#66bb6a);"></div>
                    </div>
                </div>
            </div>

            <div class="adm-top-dishes-box">
                <div style="font-size:0.74rem;font-weight:800;color:var(--text-muted);text-transform:uppercase;margin-bottom:6px;">
                    🏆 Top Revenue Dishes
                </div>
                ${topDishes.length ? topDishes.map(([name, info], idx) => `
                    <div class="adm-top-dish-row">
                        <span style="font-weight:700;">${idx + 1}. ${name} <small style="color:var(--text-muted);">(${info.qty}x)</small></span>
                        <strong style="color:var(--primary-color);">${SavoraApp.formatCurrency(info.rev)}</strong>
                    </div>
                `).join('') : `<div style="font-size:0.78rem;color:var(--text-muted);">No paid dishes recorded yet.</div>`}
            </div>
        `;
    };

    // 1. Orders & Revenue Invoices (with Filter by Table, Status, and Search)
    const renderOrderTableFilterOptions = () => {
        const sel = $('adm-order-table-filter');
        if (!sel) return;
        const currentVal = sel.value || 'ALL';

        const tableMap = new Map();
        (overviewData.tables || []).forEach((t) => {
            if (t.tableCode) {
                tableMap.set(t.tableCode, `${t.tableCode} — ${t.tableName || ''} (${t.area || ''})`);
            }
        });
        (overviewData.orders || []).forEach((o) => {
            if (o.tableCode && !tableMap.has(o.tableCode)) {
                tableMap.set(o.tableCode, o.tableCode);
            }
        });

        const countByTable = {};
        (overviewData.orders || []).forEach((o) => {
            const c = o.tableCode || 'TAKEAWAY';
            countByTable[c] = (countByTable[c] || 0) + 1;
        });

        const options = [`<option value="ALL">🪑 All Tables (${(overviewData.orders || []).length} bills)</option>`];
        Array.from(tableMap.entries()).forEach(([code, label]) => {
            const cnt = countByTable[code] || 0;
            options.push(`<option value="${code}">🍽️ ${label} (${cnt} bill${cnt === 1 ? '' : 's'})</option>`);
        });

        sel.innerHTML = options.join('');
        if ([...tableMap.keys(), 'ALL'].includes(currentVal)) {
            sel.value = currentVal;
        }
    };

    const renderOrdersTable = () => {
        const tbody = $('adm-orders-tbody');
        if (!tbody) return;
        const q = ($('adm-order-search')?.value || '').trim().toLowerCase();
        const st = $('adm-order-status-filter')?.value || 'ALL';
        const tb = $('adm-order-table-filter')?.value || 'ALL';

        const filtered = (overviewData.orders || []).filter((o) => {
            const matchSt = st === 'ALL' || o.status === st;
            const matchTb = tb === 'ALL' || o.tableCode === tb;
            const matchQ = !q || [o.orderCode, o.tableCode, o.guestName, o.reservationCode].filter(Boolean).join(' ').toLowerCase().includes(q);
            return matchSt && matchTb && matchQ;
        });

        const sumFiltered = filtered.reduce((acc, o) => acc + Number(o.totalAmount || 0), 0);
        if ($('adm-order-filter-summary')) {
            $('adm-order-filter-summary').innerHTML = `${tb === 'ALL' ? 'All Tables' : `Table <strong>${tb}</strong>`}: <strong>${filtered.length}</strong> bill(s) • Total: <strong>${SavoraApp.formatCurrency(sumFiltered)}</strong>`;
        }

        if (!filtered.length) {
            tbody.innerHTML = `<tr><td colspan="11" style="text-align:center;padding:24px;color:var(--text-muted);">No invoices found for ${tb === 'ALL' ? 'the current filter' : `Table ${tb}`}.</td></tr>`;
            return;
        }

        tbody.innerHTML = filtered.map((o) => {
            const stBadge = o.status === 'PAID' ? 'adm-badge-green'
                : (o.status === 'OPEN' || o.status === 'PREPARING' ? 'adm-badge-amber' : 'adm-badge-gray');
            const itemsSummary = (o.items || []).map((i) => `${i.quantity}x ${i.dishName}`).join(', ') || 'No items';
            return `
                <tr>
                    <td><strong>${o.orderCode}</strong></td>
                    <td><span class="adm-badge adm-badge-blue">${o.tableCode}</span></td>
                    <td>
                        <strong>${o.guestName}</strong>
                        ${o.reservationCode ? `<div style="font-size:0.72rem;color:var(--text-muted);">Booking: ${o.reservationCode}</div>` : ''}
                    </td>
                    <td style="max-width:240px;">
                        <div style="font-weight:700;">${o.totalQty} dish(es)</div>
                        <div style="font-size:0.73rem;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="${itemsSummary}">${itemsSummary}</div>
                    </td>
                    <td>${SavoraApp.formatCurrency(o.subtotalAmount)}</td>
                    <td style="color:#2e7d32;">-${SavoraApp.formatCurrency(o.discountAmount)}</td>
                    <td><strong style="color:var(--primary-color);">${SavoraApp.formatCurrency(o.totalAmount)}</strong></td>
                    <td>
                        <span class="adm-badge adm-badge-purple">${o.paymentMethod}</span>
                        ${o.transactionRef ? `<div style="font-size:0.68rem;color:var(--text-muted);">${o.transactionRef}</div>` : ''}
                    </td>
                    <td><span class="adm-badge ${stBadge}">${o.status}</span></td>
                    <td style="font-size:0.74rem;color:var(--text-muted);">${o.closedAt || o.createdAt}</td>
                    <td>
                        <button type="button" class="adm-btn-xs btn-view-invoice" data-id="${o.orderId}">🧾 View Bill</button>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-view-invoice').forEach((btn) => {
            btn.onclick = () => {
                const order = (overviewData.orders || []).find((x) => String(x.orderId) === String(btn.dataset.id));
                if (order) openAdminInvoiceModal(order);
            };
        });
    };

    const openAdminInvoiceModal = (o) => {
        const modal = $('admin-bill-modal');
        if (!modal) return;
        if ($('adm-bill-table-badge')) {
            $('adm-bill-table-badge').textContent = `🍽️ TABLE: ${o.tableCode} — ${o.tableName || ''} (${o.area || 'Main Hall'})`;
        }
        if ($('adm-bill-meta')) {
            $('adm-bill-meta').innerHTML = `
                <div><strong>Bill / Order No:</strong> ${o.orderCode}</div>
                <div><strong>Status:</strong> ${o.status} (${o.paymentMethod})</div>
                <div><strong>Guest:</strong> ${o.guestName}</div>
                <div><strong>Timestamp:</strong> ${o.closedAt || o.createdAt}</div>
                ${o.transactionRef ? `<div style="grid-column:1 / -1;"><strong>Transaction Ref:</strong> ${o.transactionRef}</div>` : ''}
            `;
        }
        if ($('adm-bill-items')) {
            $('adm-bill-items').innerHTML = (o.items || []).map((i) => `
                <tr>
                    <td><strong>${i.dishName}</strong></td>
                    <td style="text-align:center;font-weight:700;">${i.quantity}</td>
                    <td style="text-align:right;">${SavoraApp.formatCurrency(i.unitPrice)}</td>
                    <td style="text-align:right;font-weight:700;">${SavoraApp.formatCurrency(i.lineTotal)}</td>
                </tr>
            `).join('');
        }
        if ($('adm-bill-totals')) {
            $('adm-bill-totals').innerHTML = `
                <div class="qr-bill-row"><span>Subtotal:</span><strong>${SavoraApp.formatCurrency(o.subtotalAmount)}</strong></div>
                <div class="qr-bill-row"><span>Voucher Discount ${o.voucherCode ? `(${o.voucherCode} -${o.discountPercent}%)` : ''}:</span><strong style="color:#2e7d32;">-${SavoraApp.formatCurrency(o.discountAmount)}</strong></div>
                <div class="qr-bill-row qr-bill-row--total" style="font-size:1.05rem;margin-top:6px;padding-top:8px;border-top:1.5px solid var(--border-color);">
                    <span>TOTAL BILL AMOUNT:</span>
                    <span style="color:#d32f2f;font-weight:800;">${SavoraApp.formatCurrency(o.totalAmount)}</span>
                </div>
            `;
        }
        modal.style.display = 'flex';
    };

    $('btn-adm-bill-close')?.addEventListener('click', () => {
        if ($('admin-bill-modal')) $('admin-bill-modal').style.display = 'none';
    });
    $('btn-adm-bill-print')?.addEventListener('click', () => window.print());
    $('adm-order-search')?.addEventListener('input', renderOrdersTable);
    $('adm-order-table-filter')?.addEventListener('change', renderOrdersTable);
    $('adm-order-status-filter')?.addEventListener('change', renderOrdersTable);

    // 1B. Online Table Reservations Filtered by Dining Table (with Pagination)
    let resCurrentPage = 1;
    const RES_PAGE_SIZE = 8;

    const renderReservationTableFilterOptions = () => {
        const sel = $('adm-res-table-filter');
        if (!sel) return;
        const currentVal = sel.value || 'ALL';

        const tableMap = new Map();
        (overviewData.tables || []).forEach((t) => {
            if (t.tableCode) {
                tableMap.set(t.tableCode, `${t.tableCode} — ${t.tableName || ''} (${t.area || ''})`);
            }
        });
        (overviewData.reservations || []).forEach((r) => {
            if (r.tableCode && !tableMap.has(r.tableCode)) {
                tableMap.set(r.tableCode, r.tableCode);
            }
        });

        const countByTable = {};
        (overviewData.reservations || []).forEach((r) => {
            const c = r.tableCode || 'TBD';
            countByTable[c] = (countByTable[c] || 0) + 1;
        });

        const options = [`<option value="ALL">🪑 All Dining Tables (${(overviewData.reservations || []).length} bookings)</option>`];
        Array.from(tableMap.entries()).forEach(([code, label]) => {
            const cnt = countByTable[code] || 0;
            options.push(`<option value="${code}">🍽️ ${label} (${cnt} booking${cnt === 1 ? '' : 's'})</option>`);
        });

        sel.innerHTML = options.join('');
        if ([...tableMap.keys(), 'ALL'].includes(currentVal)) {
            sel.value = currentVal;
        }
    };

    const renderReservationsTable = () => {
        const tbody = $('adm-reservations-tbody');
        if (!tbody) return;

        const q = ($('adm-res-search')?.value || '').trim().toLowerCase();
        const tb = $('adm-res-table-filter')?.value || 'ALL';
        const st = $('adm-res-status-filter')?.value || 'ALL';

        const filtered = (overviewData.reservations || []).filter((r) => {
            const matchTb = tb === 'ALL' || r.tableCode === tb;
            const matchSt = st === 'ALL' || r.status === st;
            const matchQ = !q || [r.reservationCode, r.tableCode, r.customerName, r.customerPhone, r.specialRequest, r.cancelReason]
                .filter(Boolean).join(' ').toLowerCase().includes(q);
            return matchTb && matchSt && matchQ;
        });

        if ($('adm-res-filter-summary')) {
            const activeCnt = filtered.filter((r) => r.status === 'CONFIRMED' || r.status === 'SEATED' || r.status === 'PENDING').length;
            const cancelCnt = filtered.filter((r) => r.status === 'CANCELLED').length;
            $('adm-res-filter-summary').innerHTML = `${tb === 'ALL' ? 'All Tables' : `Table <strong>${tb}</strong>`}: <strong>${filtered.length}</strong> booking(s) • <strong>${activeCnt}</strong> Active • <strong>${cancelCnt}</strong> Cancelled`;
        }

        const totalPages = Math.max(1, Math.ceil(filtered.length / RES_PAGE_SIZE));
        if (resCurrentPage > totalPages) resCurrentPage = totalPages;
        const slice = filtered.slice((resCurrentPage - 1) * RES_PAGE_SIZE, resCurrentPage * RES_PAGE_SIZE);

        if (!slice.length) {
            tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:24px;color:var(--text-muted);">No table reservations found for ${tb === 'ALL' ? 'the selected filter' : `Table ${tb}`}.</td></tr>`;
            if ($('adm-reservations-pagination')) $('adm-reservations-pagination').innerHTML = '';
            return;
        }

        tbody.innerHTML = slice.map((r) => {
            const stBadge = r.status === 'CONFIRMED' ? 'adm-badge-green'
                : (r.status === 'SEATED' ? 'adm-badge-blue'
                : (r.status === 'CANCELLED' ? 'adm-badge-red'
                : (r.status === 'PENDING' ? 'adm-badge-amber' : 'adm-badge-gray')));

            return `
                <tr>
                    <td><strong>${r.reservationCode}</strong></td>
                    <td>
                        <span class="adm-badge adm-badge-blue">${r.tableCode}</span>
                        <div style="font-size:0.71rem;color:var(--text-muted);margin-top:2px;">${r.area || ''}</div>
                    </td>
                    <td style="white-space:nowrap;">
                        <strong>📅 ${r.reservationDate}</strong>
                        <div style="font-size:0.75rem;color:var(--primary-color);font-weight:700;">⏰ ${r.timeSlot}</div>
                    </td>
                    <td>
                        <strong>${r.customerName}</strong>
                        <div style="font-size:0.73rem;color:var(--text-muted);">📞 ${r.customerPhone || '—'}</div>
                    </td>
                    <td>
                        <strong>👥 ${r.guestCount} Guests</strong> <small style="color:var(--text-muted);">(${r.adultCount || r.guestCount}A, ${r.childCount || 0}C)</small>
                        ${r.occasion ? `<div style="font-size:0.72rem;color:var(--text-muted);">🎉 ${r.occasion}</div>` : ''}
                    </td>
                    <td>
                        ${r.voucherCode ? `<span class="adm-badge adm-badge-amber">🎁 ${r.voucherCode} (-${r.discountPercent}%)</span>` : `<span style="color:var(--text-muted);font-size:0.75rem;">None</span>`}
                    </td>
                    <td style="max-width:250px;">
                        <div style="font-size:0.76rem;">📝 ${r.specialRequest && r.specialRequest.trim() ? r.specialRequest : '<span style="color:var(--text-muted);">No special note</span>'}</div>
                        ${r.status === 'CANCELLED' ? `<div style="margin-top:4px;padding:3px 7px;border-radius:6px;background:#ffebee;color:#b71c1c;font-size:0.72rem;font-weight:700;">❌ Cancel Reason: "${r.cancelReason || 'Cancelled by customer'}"</div>` : ''}
                    </td>
                    <td><span class="adm-badge ${stBadge}">${r.status}</span></td>
                </tr>
            `;
        }).join('');

        buildPaginationHtml('adm-reservations-pagination', filtered.length, resCurrentPage, RES_PAGE_SIZE, 'bookings', (newPage) => {
            resCurrentPage = newPage;
            renderReservationsTable();
        });
    };

    $('adm-res-search')?.addEventListener('input', () => {
        resCurrentPage = 1;
        renderReservationsTable();
    });
    $('adm-res-table-filter')?.addEventListener('change', () => {
        resCurrentPage = 1;
        renderReservationsTable();
    });
    $('adm-res-status-filter')?.addEventListener('change', () => {
        resCurrentPage = 1;
        renderReservationsTable();
    });

    // 2. Menu & Dishes
    const renderCategoryOptions = () => {
        const sel = $('adm-dish-category');
        if (!sel) return;
        sel.innerHTML = (overviewData.categories || []).map((c) =>
            `<option value="${c.categoryId}">${c.categoryName}</option>`
        ).join('');
    };

    const renderDishesTable = () => {
        const tbody = $('adm-dishes-tbody');
        if (!tbody) return;
        tbody.innerHTML = (overviewData.dishes || []).map((d) => {
            const stClass = d.status === 'AVAILABLE' ? 'adm-badge-green' : 'adm-badge-red';
            return `
                <tr>
                    <td>
                        <div style="display:flex;align-items:center;gap:10px;">
                            <img src="${d.thumbnailUrl}" alt="${d.dishName}" class="admin-dish-thumb">
                            <div>
                                <strong>${d.dishName}</strong>
                                ${d.isSignature ? `<span class="adm-badge adm-badge-amber" style="margin-left:4px;">★ Signature</span>` : ''}
                            </div>
                        </div>
                    </td>
                    <td>${d.categoryName}</td>
                    <td>
                        <strong>${SavoraApp.formatCurrency(d.discountPrice || d.price)}</strong>
                        ${d.discountPrice ? `<div style="font-size:0.72rem;text-decoration:line-through;color:var(--text-muted);">${SavoraApp.formatCurrency(d.price)}</div>` : ''}
                    </td>
                    <td>${d.prepTime || 15}m</td>
                    <td><span class="adm-badge ${stClass}">${d.status}</span></td>
                    <td>
                        <div style="display:flex;gap:6px;flex-wrap:wrap;">
                            <button type="button" class="adm-btn-xs btn-edit-dish" data-id="${d.dishId}">✏️ Edit</button>
                            <button type="button" class="adm-btn-xs btn-toggle-dish" data-id="${d.dishId}">
                                ${d.status === 'AVAILABLE' ? '🚫 Set Out' : '✅ Set Available'}
                            </button>
                            <button type="button" class="adm-btn-xs btn-delete-dish" data-id="${d.dishId}" data-name="${d.dishName}" style="color:#c62828;border-color:#ffcdd2;">
                                🗑 Delete
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-edit-dish').forEach((btn) => {
            btn.onclick = () => {
                const d = (overviewData.dishes || []).find((x) => String(x.dishId) === String(btn.dataset.id));
                if (!d) return;
                $('adm-dish-id').value = d.dishId;
                $('adm-dish-name').value = d.dishName || '';
                $('adm-dish-category').value = d.categoryId || '';
                $('adm-dish-prep').value = d.prepTime || 15;
                $('adm-dish-price').value = d.price || '';
                $('adm-dish-discount').value = d.discountPrice || '';
                $('adm-dish-status').value = d.status || 'AVAILABLE';
                $('adm-dish-signature').value = String(Boolean(d.isSignature));
                $('adm-dish-thumb').value = d.thumbnailUrl || '';
                if ($('adm-dish-preview') && d.thumbnailUrl) {
                    $('adm-dish-preview').src = d.thumbnailUrl;
                }
                $('adm-dish-desc').value = d.description || '';
                $('adm-dish-form-title').textContent = `✏️ Edit Dish #${d.dishId}`;
            };
        });

        tbody.querySelectorAll('.btn-toggle-dish').forEach((btn) => {
            btn.onclick = async () => {
                try {
                    await SavoraApp.fetchApi(`/api/admin/dishes/${btn.dataset.id}/toggle`, { method: 'POST' });
                    showToast('Updated dish availability!');
                    await loadAdminOverview();
                } catch (err) {
                    showToast(err.message);
                }
            };
        });

        tbody.querySelectorAll('.btn-delete-dish').forEach((btn) => {
            btn.onclick = async () => {
                const dishName = btn.dataset.name || 'this dish';
                if (!window.confirm(`Are you sure you want to delete dish "${dishName}"?`)) return;
                try {
                    await SavoraApp.fetchApi(`/api/admin/dishes/${btn.dataset.id}`, { method: 'DELETE' });
                    showToast(`Deleted dish "${dishName}"!`);
                    await loadAdminOverview();
                } catch (err) {
                    showToast(err.message);
                }
            };
        });
    };

    // Cloudinary Automatic Image File Upload Handler
    $('adm-dish-file')?.addEventListener('change', async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;
        const statusEl = $('adm-upload-status');
        const saveBtn = $('btn-adm-dish-save');
        if (statusEl) {
            statusEl.style.display = 'block';
            statusEl.textContent = '☁️ Uploading image to Cloudinary (dej5mxdrt)...';
        }
        if (saveBtn) saveBtn.disabled = true;

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('folder', 'savora/dishes');

            const resp = await fetch('/api/admin/upload-image', {
                method: 'POST',
                credentials: 'same-origin',
                body: formData
            });
            const json = await resp.json();
            if (!resp.ok || !json.success) {
                throw new Error(json.message || 'Cloudinary upload failed.');
            }
            const imageUrl = json.data?.imageUrl;
            if (imageUrl) {
                $('adm-dish-thumb').value = imageUrl;
                if ($('adm-dish-preview')) $('adm-dish-preview').src = imageUrl;
                if (statusEl) statusEl.textContent = '✅ Uploaded to Cloudinary successfully!';
                showToast('☁️ Image uploaded to Cloudinary!');
            }
        } catch (err) {
            if (statusEl) statusEl.textContent = `❌ ${err.message}`;
            showToast(err.message);
        } finally {
            if (saveBtn) saveBtn.disabled = false;
        }
    });

    $('adm-dish-thumb')?.addEventListener('input', (e) => {
        const val = e.target.value.trim();
        if (val && $('adm-dish-preview')) {
            $('adm-dish-preview').src = val;
        }
    });

    $('btn-adm-dish-reset')?.addEventListener('click', () => {
        $('adm-dish-form')?.reset();
        $('adm-dish-id').value = '';
        if ($('adm-upload-status')) $('adm-upload-status').style.display = 'none';
        $('adm-dish-form-title').textContent = '➕ Add / Edit Menu Dish';
    });

    $('adm-dish-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await SavoraApp.fetchApi('/api/admin/dishes', {
                method: 'POST',
                body: JSON.stringify({
                    dishId: $('adm-dish-id').value || null,
                    dishName: $('adm-dish-name').value,
                    categoryId: $('adm-dish-category').value,
                    prepTime: $('adm-dish-prep').value,
                    price: $('adm-dish-price').value,
                    discountPrice: $('adm-dish-discount').value,
                    status: $('adm-dish-status').value,
                    isSignature: $('adm-dish-signature').value,
                    thumbnailUrl: $('adm-dish-thumb').value,
                    description: $('adm-dish-desc').value
                })
            });
            showToast('Saved dish successfully!');
            $('btn-adm-dish-reset')?.click();
            await loadAdminOverview();
        } catch (err) {
            showToast(err.message);
        }
    });

    // 3. Dining Tables
    const renderTablesTable = () => {
        const tbody = $('adm-tables-tbody');
        if (!tbody) return;
        tbody.innerHTML = (overviewData.tables || []).map((t) => `
            <tr>
                <td><strong>${t.tableCode}</strong></td>
                <td>${t.tableName}</td>
                <td>${t.area}</td>
                <td>👥 ${t.capacity} Seats</td>
                <td><span class="table-status-badge badge-status-${t.status}">${t.status}</span></td>
                <td>
                    <div style="display:flex;gap:6px;flex-wrap:wrap;">
                        <button type="button" class="adm-btn-xs btn-view-table-bookings" data-code="${t.tableCode}" style="color:#1565c0;border-color:#bbdefb;">📅 Bookings</button>
                        <button type="button" class="adm-btn-xs btn-edit-table" data-id="${t.tableId}">✏️ Edit</button>
                        <button type="button" class="adm-btn-xs btn-delete-table" data-id="${t.tableId}" data-code="${t.tableCode}" style="color:#c62828;border-color:#ffcdd2;">🗑 Delete</button>
                        <a href="/qr/table/${encodeURIComponent(t.tableCode)}" target="_blank" class="adm-btn-xs" style="text-decoration:none;">📱 Table QR</a>
                    </div>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.btn-view-table-bookings').forEach((btn) => {
            btn.onclick = () => {
                const code = btn.dataset.code;
                const tabBtn = document.querySelector('#admin-tabs-nav .admin-tab-btn[data-tab="tab-reservations"]');
                if (tabBtn) tabBtn.click();
                const sel = $('adm-res-table-filter');
                if (sel && code) {
                    sel.value = code;
                    resCurrentPage = 1;
                    renderReservationsTable();
                }
            };
        });

        tbody.querySelectorAll('.btn-edit-table').forEach((btn) => {
            btn.onclick = () => {
                const t = (overviewData.tables || []).find((x) => String(x.tableId) === String(btn.dataset.id));
                if (!t) return;
                $('adm-table-id').value = t.tableId;
                $('adm-table-code').value = t.tableCode;
                $('adm-table-name').value = t.tableName;
                $('adm-table-area').value = t.area;
                $('adm-table-capacity').value = t.capacity;
                $('adm-table-status').value = t.status;
                $('adm-table-note').value = t.note || '';
                $('adm-table-form-title').textContent = `✏️ Edit Table ${t.tableCode}`;
            };
        });

        tbody.querySelectorAll('.btn-delete-table').forEach((btn) => {
            btn.onclick = async () => {
                const code = btn.dataset.code || 'this table';
                if (!window.confirm(`Are you sure you want to delete Dining Table "${code}"?`)) return;
                try {
                    await SavoraApp.fetchApi(`/api/admin/tables/${btn.dataset.id}`, { method: 'DELETE' });
                    showToast(`Deleted Dining Table "${code}"!`);
                    await loadAdminOverview();
                } catch (err) {
                    showToast(err.message);
                }
            };
        });
    };

    $('btn-adm-table-reset')?.addEventListener('click', () => {
        $('adm-table-form')?.reset();
        $('adm-table-id').value = '';
        $('adm-table-form-title').textContent = '➕ Add / Edit Dining Table';
    });

    $('adm-table-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await SavoraApp.fetchApi('/api/admin/tables', {
                method: 'POST',
                body: JSON.stringify({
                    tableId: $('adm-table-id').value || null,
                    tableCode: $('adm-table-code').value,
                    tableName: $('adm-table-name').value,
                    area: $('adm-table-area').value,
                    capacity: $('adm-table-capacity').value,
                    status: $('adm-table-status').value,
                    note: $('adm-table-note').value
                })
            });
            showToast('Saved dining table successfully!');
            $('btn-adm-table-reset')?.click();
            await loadAdminOverview();
        } catch (err) {
            showToast(err.message);
        }
    });

    // 4. Promotional Vouchers
    const renderVouchersTable = () => {
        const tbody = $('adm-vouchers-tbody');
        if (!tbody) return;
        tbody.innerHTML = (overviewData.vouchers || []).map((v) => `
            <tr>
                <td><strong>${v.voucherCode}</strong></td>
                <td><span class="adm-badge adm-badge-amber">-${v.discountPercent}%</span></td>
                <td>${v.description || ''}</td>
                <td style="font-size:0.75rem;color:var(--text-muted);">${v.startDate} → ${v.endDate}</td>
                <td><span class="adm-badge ${v.status === 'ACTIVE' ? 'adm-badge-green' : 'adm-badge-gray'}">${v.status}</span></td>
                <td>
                    <div style="display:flex;gap:6px;">
                        <button type="button" class="adm-btn-xs btn-edit-voucher" data-id="${v.voucherId}">✏️ Edit</button>
                        <button type="button" class="adm-btn-xs btn-toggle-voucher" data-id="${v.voucherId}">
                            ${v.status === 'ACTIVE' ? 'Disable' : 'Activate'}
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.btn-edit-voucher').forEach((btn) => {
            btn.onclick = () => {
                const v = (overviewData.vouchers || []).find((x) => String(x.voucherId) === String(btn.dataset.id));
                if (!v) return;
                $('adm-voucher-id').value = v.voucherId;
                $('adm-voucher-code').value = v.voucherCode;
                $('adm-voucher-pct').value = v.discountPercent;
                $('adm-voucher-max').value = v.maxDiscountAmount || 300000;
                $('adm-voucher-min').value = v.minOrderValue || 0;
                $('adm-voucher-desc').value = v.description || '';
            };
        });

        tbody.querySelectorAll('.btn-toggle-voucher').forEach((btn) => {
            btn.onclick = async () => {
                try {
                    await SavoraApp.fetchApi(`/api/admin/vouchers/${btn.dataset.id}/toggle`, { method: 'POST' });
                    showToast('Updated voucher status!');
                    await loadAdminOverview();
                } catch (err) {
                    showToast(err.message);
                }
            };
        });
    };

    $('btn-adm-voucher-reset')?.addEventListener('click', () => {
        $('adm-voucher-form')?.reset();
        $('adm-voucher-id').value = '';
    });

    $('adm-voucher-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await SavoraApp.fetchApi('/api/admin/vouchers', {
                method: 'POST',
                body: JSON.stringify({
                    voucherId: $('adm-voucher-id').value || null,
                    voucherCode: $('adm-voucher-code').value,
                    discountPercent: $('adm-voucher-pct').value,
                    maxDiscountAmount: $('adm-voucher-max').value,
                    minOrderValue: $('adm-voucher-min').value,
                    description: $('adm-voucher-desc').value
                })
            });
            showToast('Saved promotional voucher!');
            $('btn-adm-voucher-reset')?.click();
            await loadAdminOverview();
        } catch (err) {
            showToast(err.message);
        }
    });

    // 5. Staff & User Accounts (Role Summary Counts + Filter by Role / Search / Status)
    let activeUserRoleFilter = 'ALL';

    const syncUserRoleFilterUi = () => {
        document.querySelectorAll('#adm-user-role-cards .user-role-kpi').forEach((card) => {
            const match = card.dataset.role === activeUserRoleFilter;
            card.classList.toggle('active', match);
            card.setAttribute('aria-pressed', String(match));
        });
        document.querySelectorAll('#adm-user-role-tabs .fb-filter-pill').forEach((tab) => {
            const match = tab.dataset.role === activeUserRoleFilter;
            tab.classList.toggle('active', match);
            tab.setAttribute('aria-selected', String(match));
        });
    };

    const renderUsersTable = () => {
        const tbody = $('adm-users-tbody');
        if (!tbody) return;

        const allUsers = overviewData.users || [];
        const counts = {
            ALL: allUsers.length,
            CUSTOMER: 0,
            STAFF: 0,
            CHEF: 0,
            ADMIN: 0
        };
        let activeCount = 0;
        let lockedCount = 0;

        allUsers.forEach((u) => {
            const role = String(u.roleName || 'CUSTOMER').toUpperCase();
            if (Object.prototype.hasOwnProperty.call(counts, role)) {
                counts[role] += 1;
            }
            if (u.status === 'ACTIVE') activeCount += 1;
            else if (u.status === 'LOCKED') lockedCount += 1;
        });

        if ($('adm-usr-kpi-total')) $('adm-usr-kpi-total').textContent = String(counts.ALL);
        if ($('adm-usr-kpi-total-sub')) {
            $('adm-usr-kpi-total-sub').textContent = `${activeCount} active · ${lockedCount} locked`;
        }
        if ($('adm-usr-kpi-customer')) $('adm-usr-kpi-customer').textContent = String(counts.CUSTOMER);
        if ($('adm-usr-kpi-staff')) $('adm-usr-kpi-staff').textContent = String(counts.STAFF);
        if ($('adm-usr-kpi-chef')) $('adm-usr-kpi-chef').textContent = String(counts.CHEF);
        if ($('adm-usr-kpi-admin')) $('adm-usr-kpi-admin').textContent = String(counts.ADMIN);

        if ($('adm-tab-cnt-all')) $('adm-tab-cnt-all').textContent = String(counts.ALL);
        if ($('adm-tab-cnt-customer')) $('adm-tab-cnt-customer').textContent = String(counts.CUSTOMER);
        if ($('adm-tab-cnt-staff')) $('adm-tab-cnt-staff').textContent = String(counts.STAFF);
        if ($('adm-tab-cnt-chef')) $('adm-tab-cnt-chef').textContent = String(counts.CHEF);
        if ($('adm-tab-cnt-admin')) $('adm-tab-cnt-admin').textContent = String(counts.ADMIN);

        syncUserRoleFilterUi();

        const searchQ = ($('adm-user-search')?.value || '').trim().toLowerCase();
        const statusFilter = $('adm-user-status-filter')?.value || 'ALL';

        const filtered = allUsers.filter((u) => {
            const role = String(u.roleName || 'CUSTOMER').toUpperCase();
            const matchRole = activeUserRoleFilter === 'ALL' || role === activeUserRoleFilter;
            const matchStatus = statusFilter === 'ALL' || u.status === statusFilter;
            const matchQ = !searchQ || [u.fullName, u.email, u.phone, role, `#${u.userId}`]
                .filter(Boolean).join(' ').toLowerCase().includes(searchQ);
            return matchRole && matchStatus && matchQ;
        });

        if ($('adm-user-filter-summary')) {
            const roleLabel = activeUserRoleFilter === 'ALL' ? 'All Roles' : activeUserRoleFilter;
            $('adm-user-filter-summary').innerHTML = `${roleLabel}: <strong>${filtered.length}</strong> account(s)`;
        }

        if (!filtered.length) {
            const emptyRow = document.createElement('tr');
            const emptyCell = document.createElement('td');
            emptyCell.colSpan = 6;
            emptyCell.style.textAlign = 'center';
            emptyCell.style.padding = '24px';
            emptyCell.style.color = 'var(--text-muted)';
            emptyCell.textContent = `No accounts found for ${activeUserRoleFilter === 'ALL' ? 'the current filter' : `${activeUserRoleFilter} role`}.`;
            emptyRow.appendChild(emptyCell);
            tbody.replaceChildren(emptyRow);
            return;
        }

        const rows = filtered.map((u) => {
            const role = String(u.roleName || 'CUSTOMER').toUpperCase();
            const isAdmin = role === 'ADMIN' || Number(u.roleId) === 1;
            const roleBadge = role === 'ADMIN' ? 'adm-badge-red'
                : (role === 'STAFF' ? 'adm-badge-blue'
                : (role === 'CHEF' ? 'adm-badge-amber' : 'adm-badge-green'));

            const tr = document.createElement('tr');

            const tdId = textNode('td', `#${u.userId}`);

            const tdName = document.createElement('td');
            tdName.appendChild(textNode('strong', u.fullName || 'User'));

            const tdContact = document.createElement('td');
            tdContact.appendChild(textNode('div', u.email || ''));
            const phoneDiv = textNode('div', u.phone || '—');
            phoneDiv.style.fontSize = '0.73rem';
            phoneDiv.style.color = 'var(--text-muted)';
            tdContact.appendChild(phoneDiv);

            const tdRole = document.createElement('td');
            tdRole.appendChild(textNode('span', role, `adm-badge ${roleBadge}`));

            const tdStatus = document.createElement('td');
            const statusClass = u.status === 'ACTIVE' ? 'adm-badge-green'
                : (u.status === 'PENDING' ? 'adm-badge-amber' : 'adm-badge-red');
            tdStatus.appendChild(textNode('span', u.status || 'ACTIVE', `adm-badge ${statusClass}`));

            const tdAction = document.createElement('td');
            if (isAdmin) {
                tdAction.appendChild(textNode('span', '🛡 Protected', 'adm-badge adm-badge-gray'));
            } else {
                const btn = textNode(
                    'button',
                    u.status === 'ACTIVE' ? '🔒 Lock' : '🔓 Unlock',
                    'adm-btn-xs btn-toggle-user'
                );
                btn.type = 'button';
                btn.dataset.id = u.userId;
                btn.addEventListener('click', async () => {
                    btn.disabled = true;
                    try {
                        await SavoraApp.fetchApi(`/api/admin/users/${u.userId}/toggle-status`, { method: 'POST' });
                        showToast('Updated account status!');
                        await loadAdminOverview();
                    } catch (err) {
                        showToast(err.message);
                        btn.disabled = false;
                    }
                });
                tdAction.appendChild(btn);
            }

            tr.append(tdId, tdName, tdContact, tdRole, tdStatus, tdAction);
            return tr;
        });

        tbody.replaceChildren(...rows);
    };

    document.querySelectorAll('#adm-user-role-cards .user-role-kpi, #adm-user-role-tabs .fb-filter-pill').forEach((el) => {
        el.addEventListener('click', () => {
            activeUserRoleFilter = el.dataset.role || 'ALL';
            renderUsersTable();
        });
    });
    $('adm-user-search')?.addEventListener('input', renderUsersTable);
    $('adm-user-status-filter')?.addEventListener('change', renderUsersTable);

    $('adm-user-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            const createdRole = $('adm-user-role').value;
            await SavoraApp.fetchApi('/api/admin/users', {
                method: 'POST',
                body: JSON.stringify({
                    firstName: $('adm-user-fname').value,
                    lastName: $('adm-user-lname').value,
                    email: $('adm-user-email').value,
                    phone: $('adm-user-phone').value,
                    password: $('adm-user-pass').value,
                    roleName: createdRole
                })
            });
            showToast('Created new account successfully!');
            $('adm-user-form')?.reset();
            if (createdRole) activeUserRoleFilter = createdRole;
            await loadAdminOverview();
        } catch (err) {
            showToast(err.message);
        }
    });

    $('btn-admin-refresh')?.addEventListener('click', async event => {
        const button = event.currentTarget;
        button.disabled = true;
        button.textContent = 'Refreshing…';
        try {
            if (activeSection === 'tab-feedback') await loadFeedback();
            else if (activeSection === 'tab-ai') await loadAiSettings();
            else await loadAdminOverview();
        } finally {
            button.disabled = false;
            button.textContent = 'Refresh';
        }
    });

    // 6. User Login Date/Time, Locations & Page Navigation Tracking Dashboard (with Pagination)
    let loginCurrentPage = 1;
    let visitsCurrentPage = 1;
    let activeTrackingPart = 'logins';

    const switchTrackingPart = (part) => {
        activeTrackingPart = part === 'visits' ? 'visits' : 'logins';
        const isLogins = activeTrackingPart === 'logins';
        if ($('adm-trk-pane-logins')) $('adm-trk-pane-logins').hidden = !isLogins;
        if ($('adm-trk-pane-visits')) $('adm-trk-pane-visits').hidden = isLogins;

        document.querySelectorAll('#adm-tracking-part-tabs .fb-filter-pill').forEach((tab) => {
            const match = tab.dataset.trkPart === activeTrackingPart;
            tab.classList.toggle('active', match);
            tab.setAttribute('aria-selected', String(match));
        });
        document.querySelectorAll('#tab-tracking .tracking-kpi-btn').forEach((btn) => {
            const match = btn.dataset.trkPart === activeTrackingPart;
            btn.classList.toggle('active', match);
            btn.setAttribute('aria-pressed', String(match));
        });
    };

    document.querySelectorAll('#adm-tracking-part-tabs .fb-filter-pill, #tab-tracking .tracking-kpi-btn').forEach((el) => {
        el.addEventListener('click', () => switchTrackingPart(el.dataset.trkPart));
    });

    const roleBadgeClass = (role) => {
        if (role === 'ADMIN') return 'adm-badge-red';
        if (role === 'STAFF') return 'adm-badge-blue';
        if (role === 'CHEF') return 'adm-badge-amber';
        if (role === 'CUSTOMER') return 'adm-badge-green';
        return 'adm-badge-purple';
    };

    const buildPaginationHtml = (containerId, totalItems, currentPage, pageSize, labelNoun, onPageSelect) => {
        const box = $(containerId);
        if (!box) return;
        if (totalItems === 0) {
            box.innerHTML = '';
            return;
        }
        const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
        const safePage = Math.min(totalPages, Math.max(1, currentPage));
        const startIdx = (safePage - 1) * pageSize + 1;
        const endIdx = Math.min(totalItems, safePage * pageSize);

        const pageButtons = [];
        pageButtons.push(`<button type="button" class="adm-page-btn" data-page="${safePage - 1}" ${safePage <= 1 ? 'disabled' : ''}>« Prev</button>`);

        const maxVisible = 5;
        let startPage = Math.max(1, safePage - 2);
        let endPage = Math.min(totalPages, startPage + maxVisible - 1);
        if (endPage - startPage + 1 < maxVisible) {
            startPage = Math.max(1, endPage - maxVisible + 1);
        }

        for (let p = startPage; p <= endPage; p++) {
            pageButtons.push(`<button type="button" class="adm-page-btn ${p === safePage ? 'active' : ''}" data-page="${p}">${p}</button>`);
        }

        pageButtons.push(`<button type="button" class="adm-page-btn" data-page="${safePage + 1}" ${safePage >= totalPages ? 'disabled' : ''}>Next »</button>`);

        box.innerHTML = `
            <div class="adm-page-info">
                Showing <strong>${startIdx}–${endIdx}</strong> of <strong>${totalItems}</strong> ${labelNoun} (Page ${safePage}/${totalPages})
            </div>
            <div class="adm-page-controls">
                ${pageButtons.join('')}
            </div>
        `;

        box.querySelectorAll('.adm-page-btn').forEach((btn) => {
            btn.onclick = () => {
                if (btn.disabled) return;
                const targetPage = parseInt(btn.dataset.page, 10);
                if (targetPage >= 1 && targetPage <= totalPages) {
                    onPageSelect(targetPage);
                }
            };
        });
    };

    const renderTrackingTables = () => {
        const logs = overviewData.activityLogs || [];
        const loginLogs = logs.filter((l) => l.actionType === 'LOGIN');
        const visitLogs = logs.filter((l) => l.actionType === 'PAGE_VISIT');

        if ($('adm-trk-logins')) $('adm-trk-logins').textContent = String(loginLogs.length);
        if ($('adm-trk-tab-logins')) $('adm-trk-tab-logins').textContent = String(loginLogs.length);
        if ($('adm-trk-last-login')) {
            $('adm-trk-last-login').textContent = loginLogs.length
                ? `Latest: ${loginLogs[0].userFullName} (${loginLogs[0].createdAt.slice(11, 16)})`
                : 'No logins recorded';
        }
        if ($('adm-trk-visits')) $('adm-trk-visits').textContent = String(visitLogs.length);
        if ($('adm-trk-tab-visits')) $('adm-trk-tab-visits').textContent = String(visitLogs.length);

        const uniqueLocations = new Set(logs.map((l) => l.location).filter(Boolean));
        if ($('adm-trk-locations')) $('adm-trk-locations').textContent = String(uniqueLocations.size);

        const pageCounts = {};
        visitLogs.forEach((l) => {
            const p = l.pagePath || '/home';
            pageCounts[p] = (pageCounts[p] || 0) + 1;
        });
        const topPageEntry = Object.entries(pageCounts).sort((a, b) => b[1] - a[1])[0];
        if ($('adm-trk-top-page')) $('adm-trk-top-page').textContent = topPageEntry ? topPageEntry[0] : '/home';
        if ($('adm-trk-top-page-sub')) {
            $('adm-trk-top-page-sub').textContent = topPageEntry ? `${topPageEntry[1]} recorded visit(s)` : 'Top route';
        }

        // Section A: Paginated User Login History Table
        const loginTbody = $('adm-login-history-tbody');
        const loginPageSize = Math.max(1, parseInt($('adm-login-page-size')?.value || '5', 10));
        const loginTotalPages = Math.max(1, Math.ceil(loginLogs.length / loginPageSize));
        if (loginCurrentPage > loginTotalPages) loginCurrentPage = loginTotalPages;
        const loginSlice = loginLogs.slice((loginCurrentPage - 1) * loginPageSize, loginCurrentPage * loginPageSize);

        if (loginTbody) {
            loginTbody.innerHTML = loginSlice.length ? loginSlice.map((l) => `
                <tr>
                    <td><strong>🕒 ${l.createdAt}</strong></td>
                    <td>
                        <strong>${l.userFullName}</strong>
                        <div style="font-size:0.73rem;color:var(--text-muted);">${l.userEmail}</div>
                    </td>
                    <td><span class="adm-badge ${roleBadgeClass(l.userRole)}">${l.userRole}</span></td>
                    <td>
                        <div style="font-weight:700;">📍 ${l.location}</div>
                        <div style="font-size:0.72rem;color:var(--text-muted);">IP: ${l.ipAddress}</div>
                    </td>
                    <td>💻 ${l.deviceInfo}</td>
                    <td>
                        <span class="adm-badge adm-badge-blue">${l.pagePath}</span>
                        <div style="font-size:0.72rem;color:var(--text-muted);margin-top:2px;">${l.pageTitle || ''}</div>
                    </td>
                </tr>
            `).join('') : `<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--text-muted);">No login sessions recorded yet.</td></tr>`;

            buildPaginationHtml('adm-login-pagination', loginLogs.length, loginCurrentPage, loginPageSize, 'login records', (newPage) => {
                loginCurrentPage = newPage;
                renderTrackingTables();
            });
        }

        // Section B: Paginated & Filtered Specific Pages & Sites Visited Log
        const visitsTbody = $('adm-page-visits-tbody');
        if (!visitsTbody) return;

        const q = ($('adm-track-search')?.value || '').trim().toLowerCase();
        const typeFilter = $('adm-track-type-filter')?.value || 'ALL';
        const roleFilter = $('adm-track-role-filter')?.value || 'ALL';

        const filtered = logs.filter((l) => {
            const matchType = typeFilter === 'ALL' || l.actionType === typeFilter;
            const matchRole = roleFilter === 'ALL' || l.userRole === roleFilter;
            const matchQ = !q || [l.userFullName, l.userEmail, l.location, l.ipAddress, l.pagePath, l.pageTitle, l.deviceInfo]
                .filter(Boolean).join(' ').toLowerCase().includes(q);
            return matchType && matchRole && matchQ;
        });

        const visitsPageSize = Math.max(1, parseInt($('adm-visits-page-size')?.value || '8', 10));
        const visitsTotalPages = Math.max(1, Math.ceil(filtered.length / visitsPageSize));
        if (visitsCurrentPage > visitsTotalPages) visitsCurrentPage = visitsTotalPages;
        const visitsSlice = filtered.slice((visitsCurrentPage - 1) * visitsPageSize, visitsCurrentPage * visitsPageSize);

        visitsTbody.innerHTML = visitsSlice.length ? visitsSlice.map((l) => {
            const isLogin = l.actionType === 'LOGIN';
            return `
                <tr>
                    <td style="white-space:nowrap;"><strong>${l.createdAt}</strong></td>
                    <td>
                        <span class="adm-badge ${isLogin ? 'adm-badge-amber' : 'adm-badge-green'}">
                            ${isLogin ? '🔐 LOGIN' : '🧭 PAGE VISIT'}
                        </span>
                    </td>
                    <td>
                        <strong>${l.userFullName}</strong>
                        <div style="font-size:0.72rem;color:var(--text-muted);">${l.userEmail}</div>
                    </td>
                    <td><span class="adm-badge ${roleBadgeClass(l.userRole)}">${l.userRole}</span></td>
                    <td>
                        <div style="font-weight:600;">📍 ${l.location}</div>
                        <div style="font-size:0.71rem;color:var(--text-muted);">IP: ${l.ipAddress}</div>
                    </td>
                    <td>
                        <span class="adm-badge adm-badge-blue" style="font-family:monospace;font-size:0.75rem;">${l.pagePath}</span>
                        <div style="font-size:0.74rem;font-weight:700;color:var(--text-main);margin-top:3px;">${l.pageTitle || ''}</div>
                    </td>
                    <td style="font-size:0.76rem;color:var(--text-muted);">${l.deviceInfo}</td>
                </tr>
            `;
        }).join('') : `<tr><td colspan="7" style="text-align:center;padding:22px;color:var(--text-muted);">No activity logs match your filter.</td></tr>`;

        buildPaginationHtml('adm-visits-pagination', filtered.length, visitsCurrentPage, visitsPageSize, 'activity logs', (newPage) => {
            visitsCurrentPage = newPage;
            renderTrackingTables();
        });
    };

    $('adm-login-page-size')?.addEventListener('change', () => {
        loginCurrentPage = 1;
        renderTrackingTables();
    });
    $('adm-visits-page-size')?.addEventListener('change', () => {
        visitsCurrentPage = 1;
        renderTrackingTables();
    });
    $('adm-track-search')?.addEventListener('input', () => {
        visitsCurrentPage = 1;
        renderTrackingTables();
    });
    $('adm-track-type-filter')?.addEventListener('change', () => {
        visitsCurrentPage = 1;
        renderTrackingTables();
    });
    $('adm-track-role-filter')?.addEventListener('change', () => {
        visitsCurrentPage = 1;
        renderTrackingTables();
    });

    let feedbackPage = 0;
    let feedbackStatusFilter = 'ALL';
    let feedbackRequest = 0;
    let searchTimer;
    let feedbackItems = [];
    let selectedFeedbackId = null;
    let renderedDetailFeedbackId = null;
    const feedbackPages = document.querySelector('.feedback-pages');

    function textNode(tag, value, className) {
        const element = document.createElement(tag);
        element.textContent = value;
        if (className) element.className = className;
        return element;
    }

    function formatFeedbackDate(isoStr) {
        if (!isoStr) return '';
        const date = new Date(isoStr);
        if (Number.isNaN(date.getTime())) return String(isoStr);
        return date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
    }

    function getSelectedFeedback() {
        return feedbackItems.find((item) => String(item.id) === String(selectedFeedbackId)) || null;
    }

    function renderFeedbackDetail(item) {
        const emptyBox = $('fb-detail-empty');
        const detailBox = $('fb-detail-content');
        const replyAlert = $('fb-reply-alert');
        const isSameItem = item && String(item.id) === String(renderedDetailFeedbackId);
        if (replyAlert && !isSameItem) replyAlert.hidden = true;

        if (!item) {
            renderedDetailFeedbackId = null;
            if (emptyBox) emptyBox.hidden = false;
            if (detailBox) detailBox.hidden = true;
            return;
        }
        if (emptyBox) emptyBox.hidden = true;
        if (detailBox) detailBox.hidden = false;

        const isReplied = item.status === 'REPLIED';
        const initials = (item.name || 'G').trim().split(/\s+/).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || 'G';

        if ($('fb-detail-avatar')) $('fb-detail-avatar').textContent = initials;
        if ($('fb-detail-name')) $('fb-detail-name').textContent = item.name || 'Guest';
        if ($('fb-detail-code')) $('fb-detail-code').textContent = item.feedbackCode || `FB-${item.id}`;

        const stBadge = $('fb-detail-status-badge');
        if (stBadge) {
            stBadge.className = `adm-badge ${isReplied ? 'adm-badge-green' : 'adm-badge-amber'}`;
            stBadge.textContent = isReplied ? '✅ REPLIED' : '⏳ PENDING REPLY';
        }

        const emailLink = $('fb-detail-email');
        if (emailLink) {
            emailLink.textContent = item.email || '';
            emailLink.href = item.email ? `mailto:${item.email}` : '#';
        }
        if ($('fb-detail-time')) $('fb-detail-time').textContent = formatFeedbackDate(item.submittedAt);
        if ($('fb-guest-subtime')) $('fb-guest-subtime').textContent = formatFeedbackDate(item.submittedAt);
        if ($('fb-detail-message')) $('fb-detail-message').textContent = item.message || '';

        const savedCard = $('fb-saved-reply-card');
        if (savedCard) {
            if (isReplied && item.adminReply) {
                savedCard.hidden = false;
                if ($('fb-saved-reply-body')) $('fb-saved-reply-body').textContent = item.adminReply;
                if ($('fb-saved-reply-meta')) {
                    $('fb-saved-reply-meta').textContent =
                        `Replied by ${item.repliedBy || 'Savora Admin'} • ${formatFeedbackDate(item.repliedAt)}`;
                }
                const mailBadge = $('fb-reply-email-badge');
                if (mailBadge) {
                    mailBadge.className = `adm-badge ${item.emailSent ? 'adm-badge-green' : 'adm-badge-amber'}`;
                    mailBadge.textContent = item.emailSent ? '✉️ Sent to Guest Gmail' : '⚠️ Gmail Delivery Pending/Failed';
                }
            } else {
                savedCard.hidden = true;
            }
        }

        if ($('fb-reply-heading')) {
            $('fb-reply-heading').textContent = isReplied ? 'Update & Resend Reply via Gmail' : 'Reply via Gmail';
        }
        if ($('fb-recipient-pill')) {
            $('fb-recipient-pill').textContent = `To: ${item.email || 'guest'}`;
        }
        if ($('fb-reply-input') && !isSameItem) {
            $('fb-reply-input').value = isReplied && item.adminReply ? item.adminReply : '';
        }
        if ($('btn-fb-send-reply')) {
            $('btn-fb-send-reply').textContent = isReplied ? '✉️ Resend Reply to Guest Gmail' : '✉️ Send Reply to Guest Gmail';
        }
        renderedDetailFeedbackId = item.id;
    }

    function renderFeedbackList() {
        const list = $('feedback-messages');
        if (!list) return;
        if (!feedbackItems.length) {
            list.replaceChildren(textNode('div', 'No feedback messages match this view.', 'fb-thread-empty'));
            selectedFeedbackId = null;
            renderFeedbackDetail(null);
            return;
        }
        if (!feedbackItems.some((it) => String(it.id) === String(selectedFeedbackId))) {
            selectedFeedbackId = feedbackItems[0].id;
        }

        const nodes = feedbackItems.map((item) => {
            const isSelected = String(item.id) === String(selectedFeedbackId);
            const isReplied = item.status === 'REPLIED';

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = `fb-thread-item ${isSelected ? 'is-selected' : ''}`;
            btn.dataset.id = item.id;

            const top = document.createElement('div');
            top.className = 'fb-thread-item__top';
            top.append(
                textNode('span', item.name || 'Guest', 'fb-thread-item__name'),
                textNode('span', isReplied ? '✅ Replied' : '⏳ Pending', `adm-badge ${isReplied ? 'adm-badge-green' : 'adm-badge-amber'}`)
            );

            const meta = document.createElement('div');
            meta.className = 'fb-thread-item__meta';
            meta.append(
                textNode('span', `${item.feedbackCode || `FB-${item.id}`} • ${item.email || ''}`, 'fb-thread-item__email'),
                textNode('time', formatFeedbackDate(item.submittedAt))
            );

            const preview = textNode('div', item.message || '', 'fb-thread-item__preview');
            btn.append(top, meta, preview);

            if (isReplied && item.adminReply) {
                btn.append(textNode('div', `↪ Reply: ${item.adminReply}`, 'fb-thread-item__reply-tag'));
            }

            btn.addEventListener('click', () => {
                selectedFeedbackId = item.id;
                renderFeedbackList();
                renderFeedbackDetail(item);
            });
            return btn;
        });

        list.replaceChildren(...nodes);
        renderFeedbackDetail(getSelectedFeedback());
    }

    async function loadFeedback() {
        const request = ++feedbackRequest;
        const search = $('feedback-search')?.value.trim() || '';
        const parameters = new URLSearchParams({
            search,
            status: feedbackStatusFilter,
            page: feedbackPage
        });
        const status = $('feedback-status');
        const list = $('feedback-messages');
        if (status) status.textContent = 'Loading messages…';
        if (feedbackPages) feedbackPages.hidden = true;
        try {
            const { data } = await SavoraApp.fetchApi('/api/admin/feedback?' + parameters);
            if (request !== feedbackRequest) return;

            feedbackItems = data.items || [];
            const totalAll = data.totalAll ?? data.total ?? 0;
            const pendingCount = data.pendingCount ?? 0;
            const repliedCount = data.repliedCount ?? 0;

            if ($('fb-kpi-total')) $('fb-kpi-total').textContent = String(totalAll);
            if ($('fb-kpi-pending')) $('fb-kpi-pending').textContent = String(pendingCount);
            if ($('fb-kpi-replied')) $('fb-kpi-replied').textContent = String(repliedCount);
            const fbBadge = $('adm-kpi-feedback');
            if (fbBadge) {
                const unreadCount = Number(pendingCount || 0);
                if (unreadCount > 0) {
                    fbBadge.textContent = String(unreadCount);
                    fbBadge.hidden = false;
                } else {
                    fbBadge.textContent = '';
                    fbBadge.hidden = true;
                }
            }

            if (!data.total) {
                if (status) {
                    status.textContent = search || feedbackStatusFilter !== 'ALL'
                        ? 'No messages match your filter.'
                        : 'No feedback yet. Messages from the Contact form will appear here.';
                }
                if (list) renderFeedbackList();
                return;
            }
            if (status) {
                status.textContent = `${data.total} ${data.total === 1 ? 'message' : 'messages'} · Newest first`;
            }
            renderFeedbackList();

            if (feedbackPages) {
                feedbackPages.hidden = data.pages <= 1;
                if ($('feedback-page')) $('feedback-page').textContent = `Page ${data.page + 1} of ${data.pages}`;
                if ($('feedback-prev')) $('feedback-prev').disabled = data.page === 0;
                if ($('feedback-next')) $('feedback-next').disabled = data.page + 1 >= data.pages;
            }
        } catch (err) {
            if (request === feedbackRequest && status) {
                status.textContent = err.message || 'Unable to load feedback. Try Refresh.';
            }
        }
    }

    $('feedback-status-pills')?.querySelectorAll('.fb-filter-pill').forEach((pill) => {
        pill.addEventListener('click', () => {
            $('feedback-status-pills').querySelectorAll('.fb-filter-pill').forEach((p) => p.classList.remove('active'));
            pill.classList.add('active');
            feedbackStatusFilter = pill.dataset.status || 'ALL';
            feedbackPage = 0;
            loadFeedback();
        });
    });

    $('feedback-search')?.addEventListener('input', () => {
        feedbackPage = 0;
        ++feedbackRequest;
        clearTimeout(searchTimer);
        searchTimer = setTimeout(loadFeedback, 200);
    });
    $('feedback-prev')?.addEventListener('click', () => { feedbackPage--; loadFeedback(); });
    $('feedback-next')?.addEventListener('click', () => { feedbackPage++; loadFeedback(); });

    document.querySelectorAll('.fb-template-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
            const item = getSelectedFeedback();
            const guestName = item?.name || 'Valued Guest';
            const templates = {
                thank: `Dear ${guestName},\n\nThank you very much for taking the time to share your valuable feedback with Savora Fine Dining & Lounge. We truly appreciate your kind words and look forward to welcoming you again soon!`,
                apology: `Dear ${guestName},\n\nThank you for bringing this to our attention. We sincerely apologize for any inconvenience you experienced during your visit. Our management team is reviewing this carefully to ensure a better experience on your next visit.`,
                invite: `Dear ${guestName},\n\nThank you for reaching out to Savora Fine Dining & Lounge! We would be delighted to welcome you back soon—please let us know if we can assist with reserving your preferred table.`
            };
            const input = $('fb-reply-input');
            if (input) {
                input.value = templates[chip.dataset.tpl] || '';
                input.focus();
            }
        });
    });

    $('fb-reply-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const item = getSelectedFeedback();
        if (!item) return;

        const replyInput = $('fb-reply-input');
        const btn = $('btn-fb-send-reply');
        const alertBox = $('fb-reply-alert');
        const replyMessage = (replyInput?.value || '').trim();
        if (!replyMessage) return;

        if (alertBox) alertBox.hidden = true;
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Sending to Gmail…';
        }

        try {
            const res = await SavoraApp.fetchApi(`/api/admin/feedback/${item.id}/reply`, {
                method: 'POST',
                body: JSON.stringify({ replyMessage })
            });
            const updated = res.data?.feedback;
            const emailSent = res.data?.emailSent;
            if (updated) {
                const idx = feedbackItems.findIndex((x) => String(x.id) === String(updated.id));
                if (idx >= 0) feedbackItems[idx] = updated;
                selectedFeedbackId = updated.id;
            }
            if (alertBox) {
                alertBox.hidden = false;
                alertBox.className = `fb-reply-alert ${emailSent ? 'fb-reply-alert--success' : 'fb-reply-alert--warning'}`;
                alertBox.textContent = res.message || 'Reply saved!';
            }
            showToast(res.message || 'Reply sent!');
            await loadFeedback();
        } catch (err) {
            if (alertBox) {
                alertBox.hidden = false;
                alertBox.className = 'fb-reply-alert fb-reply-alert--error';
                alertBox.textContent = err.message || 'Failed to send reply.';
            }
            showToast(err.message || 'Failed to send reply.');
        } finally {
            if (btn) {
                btn.disabled = false;
                const current = getSelectedFeedback();
                btn.textContent = current?.status === 'REPLIED' ? '✉️ Resend Reply to Guest Gmail' : '✉️ Send Reply to Guest Gmail';
            }
        }
    });

    $('btn-fb-delete')?.addEventListener('click', async () => {
        const item = getSelectedFeedback();
        if (!item) return;
        if (!window.confirm(`Delete feedback ${item.feedbackCode || `#${item.id}`} from ${item.name}?`)) return;
        try {
            await SavoraApp.fetchApi(`/api/admin/feedback/${item.id}`, { method: 'DELETE' });
            showToast('Deleted guest feedback.');
            selectedFeedbackId = null;
            await loadFeedback();
        } catch (err) {
            showToast(err.message || 'Unable to delete feedback.');
        }
    });

    // 8. Gemini AI Assistant & API Token Configuration
    const escapeHtml = (str) => String(str ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');

    const renderAiSettings = (data) => {
        if (!data) return;
        const summary = data.usageSummary || {};

        const statusVal = $('adm-ai-status-val');
        const statusSub = $('adm-ai-status-sub');
        const maskedPill = $('adm-ai-masked-pill');

        const sourceLabel = data.tokenSource === 'ADMIN_DB'
            ? 'Admin DB Token'
            : (data.tokenSource === 'ENV_CONFIG' ? 'Environment / Config' : 'Not Configured');

        if (statusVal) {
            statusVal.textContent = data.hasToken
                ? (data.aiEnabled ? 'Active (Gemini)' : 'Paused (Local)')
                : 'Local Fallback';
            statusVal.style.color = data.hasToken && data.aiEnabled ? '#2e7d32' : '#ed6c02';
        }
        if (statusSub) {
            statusSub.textContent = `${sourceLabel} · ${data.modelName || 'gemini-3.8-flash'}`;
        }
        if (maskedPill) {
            maskedPill.className = `adm-badge ${data.hasToken ? 'adm-badge-green' : 'adm-badge-amber'}`;
            maskedPill.textContent = data.hasToken ? `${sourceLabel}: ${data.maskedToken}` : 'No token saved';
        }

        if ($('adm-ai-total-tokens')) {
            $('adm-ai-total-tokens').textContent = Number(summary.totalTokens || 0).toLocaleString('en-US');
        }
        if ($('adm-ai-token-breakdown')) {
            $('adm-ai-token-breakdown').textContent =
                `${Number(summary.totalPromptTokens || 0).toLocaleString('en-US')} prompt · ${Number(summary.totalOutputTokens || 0).toLocaleString('en-US')} output`;
        }
        if ($('adm-ai-today-tokens')) {
            $('adm-ai-today-tokens').textContent = Number(summary.todayTokens || 0).toLocaleString('en-US');
        }
        if ($('adm-ai-total-requests')) {
            $('adm-ai-total-requests').textContent = Number(summary.totalRequests || 0).toLocaleString('en-US');
        }
        if ($('adm-ai-req-breakdown')) {
            $('adm-ai-req-breakdown').textContent =
                `${summary.successRequests || 0} Gemini · ${summary.blockedRequests || 0} Blocked · ${summary.fallbackRequests || 0} Fallback · ${summary.errorRequests || 0} Error`;
        }

        const modelSelect = $('adm-ai-model-select');
        const modelCustom = $('adm-ai-model-custom');
        const savedModel = data.modelName || 'gemini-3.8-flash';
        if (modelSelect) {
            const preset = [...modelSelect.options].some((o) => o.value === savedModel && o.value !== 'CUSTOM');
            if (preset) {
                modelSelect.value = savedModel;
                if (modelCustom) {
                    modelCustom.style.display = 'none';
                    modelCustom.value = '';
                }
            } else {
                modelSelect.value = 'CUSTOM';
                if (modelCustom) {
                    modelCustom.style.display = 'block';
                    modelCustom.value = savedModel;
                }
            }
        }

        if ($('adm-ai-temp')) $('adm-ai-temp').value = String(data.temperature ?? 0.7);
        if ($('adm-ai-max-tokens')) $('adm-ai-max-tokens').value = String(data.maxOutputTokens ?? 2048);
        if ($('adm-ai-enabled')) $('adm-ai-enabled').checked = data.aiEnabled !== false;
        if ($('adm-ai-system-prompt')) $('adm-ai-system-prompt').value = data.customSystemPrompt || '';

        const clearBtn = $('btn-adm-ai-clear-token');
        if (clearBtn) {
            clearBtn.disabled = !data.hasDbToken;
            clearBtn.style.opacity = data.hasDbToken ? '1' : '0.55';
        }

        const tbody = $('adm-ai-logs-tbody');
        if (tbody) {
            const logs = data.recentLogs || [];
            if (!logs.length) {
                tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:24px;color:var(--text-muted);">No AI chatbox requests logged yet.</td></tr>`;
            } else {
                tbody.innerHTML = logs.map((l) => {
                    const badgeCls = l.status === 'SUCCESS'
                        ? 'adm-badge-green'
                        : (l.status === 'FALLBACK' ? 'adm-badge-amber' : (l.status === 'BLOCKED' ? 'adm-badge-gray' : 'adm-badge-red'));
                    const note = l.errorMessage ? `${l.userMessagePreview || ''} — ⚠ ${l.errorMessage}` : (l.userMessagePreview || '');
                    return `
                        <tr>
                            <td style="font-size:0.74rem;color:var(--text-muted);white-space:nowrap;">${escapeHtml(l.createdAt)}</td>
                            <td><span class="adm-badge ${badgeCls}">${escapeHtml(l.status)}</span></td>
                            <td style="font-size:0.76rem;">${escapeHtml(l.modelName || l.provider)}</td>
                            <td>${Number(l.promptTokens || 0).toLocaleString('en-US')}</td>
                            <td>${Number(l.outputTokens || 0).toLocaleString('en-US')}</td>
                            <td><strong>${Number(l.totalTokens || 0).toLocaleString('en-US')}</strong></td>
                            <td style="font-size:0.76rem;">${l.latencyMs != null ? `${l.latencyMs} ms` : '–'}</td>
                            <td style="max-width:260px;font-size:0.76rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="${escapeHtml(note)}">${escapeHtml(note)}</td>
                        </tr>
                    `;
                }).join('');
            }
        }
    };

    const loadAiSettings = async () => {
        try {
            const res = await SavoraApp.fetchApi('/api/admin/ai-settings');
            renderAiSettings(res.data);
        } catch (err) {
            showToast(err.message || 'Unable to load AI settings.');
        }
    };

    const resolveSelectedAiModel = () => {
        const sel = $('adm-ai-model-select')?.value || 'gemini-3.8-flash';
        if (sel === 'CUSTOM') {
            return ($('adm-ai-model-custom')?.value || '').trim() || 'gemini-3.8-flash';
        }
        return sel;
    };

    $('adm-ai-model-select')?.addEventListener('change', (e) => {
        const custom = $('adm-ai-model-custom');
        if (custom) {
            custom.style.display = e.target.value === 'CUSTOM' ? 'block' : 'none';
            if (e.target.value === 'CUSTOM') custom.focus();
        }
    });

    $('btn-adm-ai-toggle-vis')?.addEventListener('click', () => {
        const inp = $('adm-ai-token');
        if (!inp) return;
        inp.type = inp.type === 'password' ? 'text' : 'password';
    });

    $('adm-ai-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const saveBtn = $('btn-adm-ai-save');
        if (saveBtn) saveBtn.disabled = true;
        try {
            const payload = {
                apiToken: ($('adm-ai-token')?.value || '').trim(),
                modelName: resolveSelectedAiModel(),
                temperature: parseFloat($('adm-ai-temp')?.value || '0.7'),
                maxOutputTokens: parseInt($('adm-ai-max-tokens')?.value || '1024', 10),
                aiEnabled: Boolean($('adm-ai-enabled')?.checked),
                customSystemPrompt: ($('adm-ai-system-prompt')?.value || '').trim()
            };
            const res = await SavoraApp.fetchApi('/api/admin/ai-settings', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            if ($('adm-ai-token')) $('adm-ai-token').value = '';
            renderAiSettings(res.data);
            showToast(res.message || 'Saved Gemini AI settings!');
        } catch (err) {
            showToast(err.message || 'Failed to save AI settings.');
        } finally {
            if (saveBtn) saveBtn.disabled = false;
        }
    });

    $('btn-adm-ai-clear-token')?.addEventListener('click', async () => {
        if (!window.confirm('Remove the saved Gemini API token from database?')) return;
        try {
            const res = await SavoraApp.fetchApi('/api/admin/ai-settings', {
                method: 'POST',
                body: JSON.stringify({ clearToken: true })
            });
            if ($('adm-ai-token')) $('adm-ai-token').value = '';
            renderAiSettings(res.data);
            showToast('Cleared saved DB token.');
        } catch (err) {
            showToast(err.message || 'Unable to clear DB token.');
        }
    });

    $('btn-adm-ai-test')?.addEventListener('click', async () => {
        const btn = $('btn-adm-ai-test');
        const box = $('adm-ai-test-result');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Testing…';
        }
        if (box) box.hidden = true;
        try {
            const res = await SavoraApp.fetchApi('/api/admin/ai-settings/test', {
                method: 'POST',
                body: JSON.stringify({
                    apiToken: ($('adm-ai-token')?.value || '').trim(),
                    modelName: resolveSelectedAiModel()
                })
            });
            const d = res.data || {};
            if (box) {
                box.hidden = false;
                box.className = `ai-test-result ${d.connected ? 'ok' : 'err'}`;
                box.innerHTML = d.connected
                    ? `<strong>✓ Connected (${escapeHtml(d.model)})</strong> · ${d.latencyMs} ms · ${d.totalTokens} tokens (${d.promptTokens} in / ${d.outputTokens} out)<br><em>"${escapeHtml(d.reply)}"</em>`
                    : `<strong>✕ Connection Failed (${escapeHtml(d.model)})</strong><br>${escapeHtml(d.errorMessage || 'Unknown error')}`;
            }
            await loadAiSettings();
        } catch (err) {
            if (box) {
                box.hidden = false;
                box.className = 'ai-test-result err';
                box.textContent = err.message || 'Test failed.';
            }
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.textContent = '⚡ Test Token';
            }
        }
    });

    $('btn-adm-ai-clear-logs')?.addEventListener('click', async () => {
        if (!window.confirm('Clear all AI token usage logs?')) return;
        try {
            await SavoraApp.fetchApi('/api/admin/ai-settings/logs', { method: 'DELETE' });
            await loadAiSettings();
            showToast('Cleared AI token usage logs.');
        } catch (err) {
            showToast(err.message || 'Unable to clear AI logs.');
        }
    });

    openSection(location.hash.slice(1));
    loadAdminOverview();
    if (activeSection !== 'tab-feedback') loadFeedback();
    if (activeSection === 'tab-ai') loadAiSettings();
    if (typeof SavoraApp !== 'undefined' && SavoraApp.onRealtimeSync) {
        SavoraApp.onRealtimeSync(() => loadFeedback());
    }
});
