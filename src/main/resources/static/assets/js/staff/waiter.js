document.addEventListener('DOMContentLoaded', () => {
    const $ = (id) => document.getElementById(id);
    let allTables = [], allDishes = [], activeZone = 'ALL', activeStatus = 'ALL';
    let selectedTableCode = null, currentSession = null;

    const showToast = (msg) => {
        const toast = $('home-toast');
        if (!toast) return;
        toast.textContent = msg;
        toast.style.display = 'block';
        clearTimeout(window.__waiterToastTimer);
        window.__waiterToastTimer = setTimeout(() => { toast.style.display = 'none'; }, 3200);
    };

    const showAlert = (msg) => {
        const box = $('waiter-alert');
        if (!box) return;
        if (!msg) {
            box.style.display = 'none';
            return;
        }
        box.textContent = msg;
        box.style.display = 'block';
    };

    const loadMenuDishes = async () => {
        try {
            const res = await SavoraApp.fetchApi('/api/public/pasgo/menu');
            allDishes = res.data || [];
            const sel = $('waiter-dish-select');
            if (sel) {
                sel.innerHTML = allDishes.map((d) => {
                    const price = d.discountPrice || d.price;
                    return `<option value="${d.dishId}">${d.dishName} — ${SavoraApp.formatCurrency(price)}</option>`;
                }).join('');
            }
        } catch (_) {}
    };

    const loadFloorTables = async () => {
        try {
            const res = await SavoraApp.fetchApi('/api/staff/tables');
            allTables = res.data || [];
            updateKpis();
            renderZonePills();
            renderTableGrid();

            if (!selectedTableCode && allTables.length) {
                const firstOccupiedOrReserved = allTables.find((t) => t.status === 'OCCUPIED' || t.status === 'RESERVED');
                selectedTableCode = (firstOccupiedOrReserved || allTables[0]).tableCode;
            }
            if (selectedTableCode) {
                await loadSelectedTableSession(selectedTableCode);
            }
        } catch (err) {
            showAlert(err.message);
        }
    };

    const updateKpis = () => {
        const countBy = (st) => allTables.filter((t) => t.status === st).length;
        const readyTotal = allTables.reduce((sum, t) => sum + (t.readyItemsCount || 0), 0);
        if ($('kpi-available')) $('kpi-available').textContent = String(countBy('AVAILABLE'));
        if ($('kpi-reserved')) $('kpi-reserved').textContent = String(countBy('RESERVED'));
        if ($('kpi-occupied')) $('kpi-occupied').textContent = String(countBy('OCCUPIED'));
        if ($('kpi-cleaning')) $('kpi-cleaning').textContent = String(countBy('CLEANING'));
        if ($('kpi-ready-dishes')) $('kpi-ready-dishes').textContent = String(readyTotal);
    };

    const renderZonePills = () => {
        const box = $('waiter-zone-pills');
        if (!box) return;
        const zones = [...new Set(allTables.map((t) => t.area).filter(Boolean))];
        if (activeZone !== 'ALL' && !zones.includes(activeZone)) activeZone = 'ALL';

        box.innerHTML = [
            `<button type="button" class="zone-pill ${activeZone === 'ALL' ? 'active' : ''}" data-zone="ALL">All Zones (${allTables.length})</button>`,
            ...zones.map((z) => `<button type="button" class="zone-pill ${activeZone === z ? 'active' : ''}" data-zone="${z}">${z}</button>`)
        ].join('');

        box.querySelectorAll('.zone-pill').forEach((pill) => {
            pill.onclick = () => {
                activeZone = pill.dataset.zone || 'ALL';
                renderZonePills();
                renderTableGrid();
            };
        });
    };

    const renderTableGrid = () => {
        const grid = $('waiter-table-grid');
        if (!grid) return;

        const filtered = allTables.filter((t) =>
            (activeZone === 'ALL' || t.area === activeZone) &&
            (activeStatus === 'ALL' || t.status === activeStatus)
        );

        grid.innerHTML = filtered.map((t) => {
            const isSel = t.tableCode === selectedTableCode;
            return `
                <div class="w-table-card st-${t.status} ${isSel ? 'active-table' : ''}" data-code="${t.tableCode}">
                    <div>
                        <div class="table-card__top">
                            <span class="table-card__code">${t.tableCode}</span>
                            <span class="table-status-badge badge-status-${t.status}">${t.status}</span>
                        </div>
                        <h4 class="table-card__name" style="margin-top: 8px;">${t.tableName}</h4>
                        <div class="table-card__meta" style="margin-top: 4px;">
                            <span>📍 ${t.area}</span>
                            <span>👥 Max ${t.capacity} Seats</span>
                        </div>

                        ${t.reservationCode ? `
                            <div class="w-res-box" style="margin-top: 8px;">
                                📅 <strong>${t.reservationCode}</strong> (${t.reservedTimeSlot})<br>
                                👤 ${t.reservedCustomerName} • ${t.reservedGuestCount} guests
                            </div>` : ''}

                        ${t.orderCode ? `
                            <div class="w-order-box" style="margin-top: 8px;">
                                <div style="display:flex;justify-content:space-between;font-weight:700;">
                                    <span>🧾 ${t.orderCode}</span>
                                    <span style="color:var(--primary-color);">${SavoraApp.formatCurrency(t.totalAmount)}</span>
                                </div>
                                <div style="font-size:0.73rem;color:var(--text-muted);margin-top:3px;">
                                    ⏳ ${t.pendingItemsCount} Pending • 🔥 ${t.cookingItemsCount} Cooking • ✓ ${t.servedItemsCount} Served
                                </div>
                                ${t.readyItemsCount > 0 ? `<span class="w-ready-alert">🔔 ${t.readyItemsCount} READY TO SERVE!</span>` : ''}
                            </div>` : ''}
                    </div>
                </div>`;
        }).join('');

        grid.querySelectorAll('.w-table-card').forEach((card) => {
            card.onclick = () => {
                selectedTableCode = card.dataset.code;
                renderTableGrid();
                loadSelectedTableSession(selectedTableCode);
            };
        });
    };

    const loadSelectedTableSession = async (tableCode) => {
        showAlert(null);
        try {
            const res = await SavoraApp.fetchApi(`/api/staff/tables/${encodeURIComponent(tableCode)}/session`);
            currentSession = res.data;
            renderInspector();
        } catch (err) {
            showAlert(err.message);
        }
    };

    const renderInspector = () => {
        if (!currentSession) return;
        const s = currentSession;
        if ($('insp-table-title')) $('insp-table-title').textContent = `${s.tableCode} — ${s.tableName}`;
        if ($('insp-table-meta')) $('insp-table-meta').textContent = `📍 ${s.area} • 👥 Max ${s.capacity} Seats`;

        const badge = $('insp-table-status');
        if (badge) {
            badge.className = `table-status-badge badge-status-${s.tableStatus}`;
            badge.textContent = s.tableStatus;
        }

        // Render Operational Table Action Buttons
        const actionBox = $('insp-status-actions');
        if (actionBox) {
            let btns = '';
            if (s.tableStatus === 'AVAILABLE' || s.tableStatus === 'RESERVED') {
                btns += `<button type="button" class="btn btn-primary btn-table-action" data-action="SEAT_GUEST" style="padding:7px 12px;font-size:0.78rem;flex:1;">🪑 Seat Guests (Set Occupied)</button>`;
            }
            if (s.tableStatus === 'OCCUPIED') {
                btns += `<button type="button" class="btn btn-outline btn-table-action" data-action="CLOSE_TO_CLEANING" style="padding:7px 12px;font-size:0.78rem;flex:1;">🧹 Close Table (Set Cleaning)</button>`;
            }
            if (s.tableStatus === 'CLEANING') {
                btns += `<button type="button" class="btn btn-primary btn-table-action" data-action="SET_AVAILABLE" style="padding:7px 12px;font-size:0.78rem;flex:1;background:#2e7d32;">✓ Complete Cleaning (Set Available)</button>`;
            }
            actionBox.innerHTML = btns;
            actionBox.querySelectorAll('.btn-table-action').forEach((b) => {
                b.onclick = async () => {
                    b.disabled = true;
                    try {
                        await SavoraApp.fetchApi(`/api/staff/tables/${encodeURIComponent(s.tableCode)}/status`, {
                            method: 'POST',
                            body: JSON.stringify({ action: b.dataset.action })
                        });
                        showToast(`Updated Table ${s.tableCode} status!`);
                        await loadFloorTables();
                    } catch (err) {
                        showAlert(err.message);
                    } finally {
                        b.disabled = false;
                    }
                };
            });
        }

        // Linked Reservation Info
        const resBox = $('insp-reservation-box');
        if (resBox) {
            if (s.reservationCode) {
                resBox.style.display = 'block';
                resBox.innerHTML = `📅 <strong>Booked Online: ${s.reservationCode}</strong> (${s.reservedTimeSlot})<br>👤 Guest: <strong>${s.reservedCustomerName}</strong> (${s.reservedGuestCount} guests)` +
                    (s.reservedVoucherCode ? ` • Deal: <strong>${s.reservedVoucherCode} (-${s.reservedDiscountPercent}%)</strong>` : '');
            } else {
                resBox.style.display = 'none';
            }
        }

        // Ordered Dishes List
        const items = s.placedItems || [];
        if ($('insp-item-count')) $('insp-item-count').textContent = String(items.reduce((sum, i) => sum + i.quantity, 0));
        if ($('insp-order-code')) $('insp-order-code').textContent = s.orderCode ? `Order: ${s.orderCode}` : 'No active order';

        const listBox = $('insp-items-list');
        if (listBox) {
            listBox.innerHTML = items.length ? items.map((i) => `
                <div class="waiter-item-card">
                    <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
                        <strong style="font-size:0.88rem;">${i.quantity}x ${i.dishName}</strong>
                        <span class="kitchen-badge k-status-${i.itemStatus}">${i.itemStatus}</span>
                    </div>
                    <div style="display:flex;justify-content:space-between;font-size:0.78rem;color:var(--text-muted);margin-top:4px;">
                        <span>${i.note ? `📝 ${i.note}` : 'Standard prep'} ${i.orderedAt ? `• ${i.orderedAt}` : ''}</span>
                        <strong>${SavoraApp.formatCurrency(i.lineTotal)}</strong>
                    </div>
                    <div class="waiter-item-actions">
                        ${i.itemStatus === 'PENDING' ? `
                            <button type="button" class="btn-remove-pending" data-item-id="${i.orderItemId}" data-dish-name="${i.dishName}">
                                🗑 Remove (Pending)
                            </button>` : ''}
                        ${i.itemStatus === 'READY' ? `
                            <button type="button" class="btn-serve-item" data-item-id="${i.orderItemId}">
                                ✓ Mark Served
                            </button>` : ''}
                    </div>
                </div>`).join('')
                : `<p style="text-align:center;color:var(--text-muted);font-size:0.84rem;padding:20px 0;">No dishes ordered for this table yet.</p>`;

            // Remove PENDING dish button handler
            listBox.querySelectorAll('.btn-remove-pending').forEach((btn) => {
                btn.onclick = async () => {
                    const itemId = btn.dataset.itemId;
                    const dishName = btn.dataset.dishName;
                    btn.disabled = true;
                    try {
                        const res = await SavoraApp.fetchApi(`/api/staff/order-items/${itemId}`, { method: 'DELETE' });
                        currentSession = res.data;
                        showToast(`Removed pending dish "${dishName}" from Table ${s.tableCode}!`);
                        await loadFloorTables();
                    } catch (err) {
                        showAlert(err.message);
                        btn.disabled = false;
                    }
                };
            });

            // Mark dish as SERVED button handler
            listBox.querySelectorAll('.btn-serve-item').forEach((btn) => {
                btn.onclick = async () => {
                    const itemId = btn.dataset.itemId;
                    btn.disabled = true;
                    try {
                        const res = await SavoraApp.fetchApi(`/api/staff/order-items/${itemId}/serve`, { method: 'POST' });
                        currentSession = res.data;
                        showToast(`Dish marked as SERVED at Table ${s.tableCode}!`);
                        await loadFloorTables();
                    } catch (err) {
                        showAlert(err.message);
                        btn.disabled = false;
                    }
                };
            });
        }

        // Running Bill Summary
        const billBox = $('insp-bill-summary');
        if (billBox) {
            if (s.orderId) {
                billBox.innerHTML = `
                    <div class="qr-bill-row"><span>Subtotal:</span><strong>${SavoraApp.formatCurrency(s.subtotalAmount)}</strong></div>
                    <div class="qr-bill-row"><span>Voucher Discount ${s.appliedVoucherCode ? `(${s.appliedVoucherCode} -${s.appliedDiscountPercent}%)` : ''}:</span><strong style="color:#2e7d32;">-${SavoraApp.formatCurrency(s.discountAmount)}</strong></div>
                    <div class="qr-bill-row qr-bill-row--total"><span>Total Table Bill:</span><span>${SavoraApp.formatCurrency(s.totalAmount)}</span></div>`;
            } else {
                billBox.innerHTML = '';
            }
        }
    };

    // Waiter Adds Dish to Selected Table
    $('btn-waiter-add-dish')?.addEventListener('click', async () => {
        if (!selectedTableCode) {
            showAlert('Please select a dining table first.');
            return;
        }
        const dishId = parseInt($('waiter-dish-select')?.value || '0', 10);
        const quantity = Math.max(1, parseInt($('waiter-dish-qty')?.value || '1', 10));
        const note = $('waiter-dish-note')?.value.trim() || null;

        if (!dishId) return;

        const btn = $('btn-waiter-add-dish');
        btn.disabled = true;
        showAlert(null);

        try {
            const res = await SavoraApp.fetchApi('/api/staff/tables/order', {
                method: 'POST',
                body: JSON.stringify({
                    tableCode: selectedTableCode,
                    items: [{ dishId, quantity, note }]
                })
            });
            currentSession = res.data;
            if ($('waiter-dish-qty')) $('waiter-dish-qty').value = '1';
            if ($('waiter-dish-note')) $('waiter-dish-note').value = '';
            showToast(`Added dish to Table ${selectedTableCode}!`);
            await loadFloorTables();
        } catch (err) {
            showAlert(err.message);
        } finally {
            btn.disabled = false;
        }
    });

    $('waiter-status-filter')?.addEventListener('change', (e) => {
        activeStatus = e.target.value || 'ALL';
        renderTableGrid();
    });

    $('btn-waiter-refresh')?.addEventListener('click', () => {
        loadFloorTables();
        showToast('Refreshed floor & kitchen status!');
    });

    loadMenuDishes();
    loadFloorTables();
    setInterval(loadFloorTables, 12000);
});
