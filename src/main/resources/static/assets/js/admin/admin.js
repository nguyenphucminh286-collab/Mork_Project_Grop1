/**
 * SAVORA — Executive Admin Portal & Analytics Dashboard
 */
document.addEventListener('DOMContentLoaded', () => {
    const $ = (id) => document.getElementById(id);
    let overviewData = {
        kpis: {},
        orders: [],
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

    // Tab Switching
    document.querySelectorAll('#admin-tabs-nav .admin-tab-btn').forEach((btn) => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('#admin-tabs-nav .admin-tab-btn').forEach((b) => b.classList.remove('active'));
            document.querySelectorAll('.admin-tab-pane').forEach((p) => p.classList.remove('active'));
            btn.classList.add('active');
            const target = $(btn.dataset.tab);
            if (target) target.classList.add('active');
        });
    });

    const loadAdminOverview = async () => {
        try {
            const res = await SavoraApp.fetchApi('/api/admin/overview');
            overviewData = res.data || overviewData;
            renderKpis();
            renderRevenueCharts();
            renderOrderTableFilterOptions();
            renderOrdersTable();
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
        if ($('adm-kpi-orders')) $('adm-kpi-orders').textContent = `${k.paidOrdersCount || 0} Paid / ${k.totalOrdersCount || 0} Total`;
        if ($('adm-kpi-active-orders')) $('adm-kpi-active-orders').textContent = `${k.activeOrdersCount || 0} active open tables`;
        if ($('adm-kpi-bookings')) $('adm-kpi-bookings').textContent = String(k.totalReservationsCount || 0);
        if ($('adm-kpi-dishes')) $('adm-kpi-dishes').textContent = String(k.totalDishesCount || 0);
        if ($('adm-kpi-dishes-sub')) $('adm-kpi-dishes-sub').textContent = `${k.availableDishesCount || 0} available on menu`;
        if ($('adm-kpi-tables')) $('adm-kpi-tables').textContent = String(k.totalTablesCount || 0);
        if ($('adm-kpi-tables-sub')) $('adm-kpi-tables-sub').textContent = `${k.availableTablesCount || 0} available • ${k.occupiedTablesCount || 0} occupied`;
        if ($('adm-kpi-users')) $('adm-kpi-users').textContent = String(k.totalUsersCount || 0);
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
                <span class="adm-chart-pill">🧾 Avg Bill: <strong>${SavoraApp.formatCurrency(avgBill)}</strong></span>
                <span class="adm-chart-pill" style="color:#2e7d32;">📈 Peak Day: <strong>${SavoraApp.formatCurrency(Math.max(...days.map(d => d.revenue)))}</strong></span>
            `;
        }

        dailyBox.innerHTML = `
            <div class="adm-bars-grid">
                ${days.map((d) => {
                    const heightPct = d.revenue > 0 ? Math.max(10, Math.round((d.revenue / maxDayRev) * 100)) : 4;
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
                        <button type="button" class="adm-btn-xs btn-edit-table" data-id="${t.tableId}">✏️ Edit</button>
                        <button type="button" class="adm-btn-xs btn-delete-table" data-id="${t.tableId}" data-code="${t.tableCode}" style="color:#c62828;border-color:#ffcdd2;">🗑 Delete</button>
                        <a href="/qr/table/${encodeURIComponent(t.tableCode)}" target="_blank" class="adm-btn-xs" style="text-decoration:none;">📱 Table QR</a>
                    </div>
                </td>
            </tr>
        `).join('');

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

    // 5. Staff & User Accounts
    const renderUsersTable = () => {
        const tbody = $('adm-users-tbody');
        if (!tbody) return;
        tbody.innerHTML = (overviewData.users || []).map((u) => {
            const roleBadge = u.roleName === 'ADMIN' ? 'adm-badge-red'
                : (u.roleName === 'STAFF' ? 'adm-badge-blue'
                : (u.roleName === 'CHEF' ? 'adm-badge-amber' : 'adm-badge-green'));
            return `
                <tr>
                    <td>#${u.userId}</td>
                    <td><strong>${u.fullName}</strong></td>
                    <td>
                        <div>${u.email}</div>
                        <div style="font-size:0.73rem;color:var(--text-muted);">${u.phone || '—'}</div>
                    </td>
                    <td><span class="adm-badge ${roleBadge}">${u.roleName}</span></td>
                    <td><span class="adm-badge ${u.status === 'ACTIVE' ? 'adm-badge-green' : 'adm-badge-red'}">${u.status}</span></td>
                    <td>
                        <button type="button" class="adm-btn-xs btn-toggle-user" data-id="${u.userId}">
                            ${u.status === 'ACTIVE' ? '🔒 Lock' : '🔓 Unlock'}
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('.btn-toggle-user').forEach((btn) => {
            btn.onclick = async () => {
                try {
                    await SavoraApp.fetchApi(`/api/admin/users/${btn.dataset.id}/toggle-status`, { method: 'POST' });
                    showToast('Updated account status!');
                    await loadAdminOverview();
                } catch (err) {
                    showToast(err.message);
                }
            };
        });
    };

    $('adm-user-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await SavoraApp.fetchApi('/api/admin/users', {
                method: 'POST',
                body: JSON.stringify({
                    firstName: $('adm-user-fname').value,
                    lastName: $('adm-user-lname').value,
                    email: $('adm-user-email').value,
                    phone: $('adm-user-phone').value,
                    password: $('adm-user-pass').value,
                    roleName: $('adm-user-role').value
                })
            });
            showToast('Created new account successfully!');
            $('adm-user-form')?.reset();
            await loadAdminOverview();
        } catch (err) {
            showToast(err.message);
        }
    });

    $('btn-admin-refresh')?.addEventListener('click', () => {
        loadAdminOverview();
        showToast('Refreshed Admin Dashboard data!');
    });

    // 6. User Login Date/Time, Locations & Page Navigation Tracking Dashboard
    const roleBadgeClass = (role) => {
        if (role === 'ADMIN') return 'adm-badge-red';
        if (role === 'STAFF') return 'adm-badge-blue';
        if (role === 'CHEF') return 'adm-badge-amber';
        if (role === 'CUSTOMER') return 'adm-badge-green';
        return 'adm-badge-purple';
    };

    const renderTrackingTables = () => {
        const logs = overviewData.activityLogs || [];
        const loginLogs = logs.filter((l) => l.actionType === 'LOGIN');
        const visitLogs = logs.filter((l) => l.actionType === 'PAGE_VISIT');

        if ($('adm-trk-logins')) $('adm-trk-logins').textContent = String(loginLogs.length);
        if ($('adm-trk-last-login')) {
            $('adm-trk-last-login').textContent = loginLogs.length
                ? `Latest: ${loginLogs[0].userFullName} (${loginLogs[0].createdAt.slice(11, 16)})`
                : 'No logins recorded';
        }
        if ($('adm-trk-visits')) $('adm-trk-visits').textContent = String(visitLogs.length);

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

        // Section A: User Login History Table
        const loginTbody = $('adm-login-history-tbody');
        if (loginTbody) {
            loginTbody.innerHTML = loginLogs.length ? loginLogs.slice(0, 25).map((l) => `
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
        }

        // Section B: Filtered Specific Pages & Sites Visited Log
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

        visitsTbody.innerHTML = filtered.length ? filtered.map((l) => {
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
    };

    $('adm-track-search')?.addEventListener('input', renderTrackingTables);
    $('adm-track-type-filter')?.addEventListener('change', renderTrackingTables);
    $('adm-track-role-filter')?.addEventListener('change', renderTrackingTables);

    loadAdminOverview();
});
