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

    const getTodayIsoDate = () => {
        const now = new Date();
        const yyyy = now.getFullYear();
        const mm = String(now.getMonth() + 1).padStart(2, '0');
        const dd = String(now.getDate()).padStart(2, '0');
        return `${yyyy}-${mm}-${dd}`;
    };

    const filterTodayBookings = (list) => {
        if (!Array.isArray(list)) return [];
        const todayIso = getTodayIsoDate();
        return list.filter((r) => !r.reservationDate || r.reservationDate === todayIso);
    };

    const parseSlotStartMinutes = (timeSlot) => {
        if (!timeSlot) return 9999;
        const match = String(timeSlot).match(/(\d{1,2}):(\d{2})/);
        if (!match) return 9999;
        return parseInt(match[1], 10) * 60 + parseInt(match[2], 10);
    };

    const pickClosestRealTimeBooking = (list) => {
        const todayList = filterTodayBookings(list);
        const activeList = todayList.filter(
            (r) => r.status === 'SEATED' || r.status === 'CONFIRMED' || r.status === 'PENDING'
        );
        if (!activeList.length) return null;

        const now = new Date();
        const nowMins = now.getHours() * 60 + now.getMinutes();

        return activeList.slice().sort((a, b) => {
            if (a.status === 'SEATED' && b.status !== 'SEATED') return -1;
            if (b.status === 'SEATED' && a.status !== 'SEATED') return 1;
            const diffA = Math.abs(parseSlotStartMinutes(a.timeSlot) - nowMins);
            const diffB = Math.abs(parseSlotStartMinutes(b.timeSlot) - nowMins);
            return diffA - diffB;
        })[0];
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
            const closestBooking = pickClosestRealTimeBooking(t.tableReservations);
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

                        ${closestBooking ? `
                            <div class="w-res-box" style="margin-top: 8px;">
                                📅 <strong>${closestBooking.reservationCode}</strong> (${closestBooking.timeSlot})<br>
                                👤 ${closestBooking.customerName} • ${closestBooking.guestCount} guests
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

        // Linked Reservation Schedule List (Strictly Today's Bookings + Customer Note + Cancel Reason)
        const resBox = $('insp-reservation-box');
        if (resBox) {
            const bookings = filterTodayBookings(s.tableReservations);
            if (bookings.length > 0 || s.orderNote) {
                resBox.style.display = 'block';
                let html = '';

                if (bookings.length > 0) {
                    html += `<div style="font-size:0.8rem;font-weight:800;margin-bottom:8px;color:#bf360c;display:flex;justify-content:space-between;align-items:center;">
                        <span>📅 Today's Table Bookings (${bookings.length})</span>
                    </div>`;
                    html += bookings.map((r) => {
                        const isCancelled = r.status === 'CANCELLED';
                        const isCompleted = r.status === 'COMPLETED';
                        const badgeBg = isCancelled ? '#ffebee' : (r.status === 'SEATED' ? '#e3f2fd' : (isCompleted ? '#eeeeee' : '#e8f5e9'));
                        const badgeColor = isCancelled ? '#c62828' : (r.status === 'SEATED' ? '#0d47a1' : (isCompleted ? '#616161' : '#1b5e20'));
                        const cardBorder = isCancelled ? '1.5px solid #ef9a9a' : '1px solid rgba(230, 81, 0, 0.28)';
                        const cardBg = isCancelled ? '#fff5f5' : 'rgba(255, 255, 255, 0.72)';

                        return `
                            <div style="padding:8px 10px;border-radius:8px;border:${cardBorder};background:${cardBg};margin-bottom:7px;color:#3e2723;">
                                <div style="display:flex;justify-content:space-between;align-items:center;gap:6px;">
                                    <strong>⏰ ${r.timeSlot} <small style="font-weight:600;opacity:0.8;">(${r.reservationCode})</small></strong>
                                    <span style="padding:2px 8px;border-radius:999px;font-size:0.68rem;font-weight:800;background:${badgeBg};color:${badgeColor};">
                                        ${r.status}
                                    </span>
                                </div>
                                <div style="margin-top:3px;font-size:0.75rem;">
                                    👤 <strong>${r.customerName}</strong> ${r.customerPhone ? `(${r.customerPhone})` : ''} • ${r.guestCount} guests
                                    ${r.occasion ? ` • 🎉 ${r.occasion}` : ''}
                                    ${r.voucherCode ? ` • 🎁 <strong>${r.voucherCode} (-${r.discountPercent}%)</strong>` : ''}
                                </div>
                                <div style="margin-top:2px;font-size:0.74rem;">
                                    📝 Note: <strong>${r.specialRequest && r.specialRequest.trim() ? r.specialRequest : 'No special request'}</strong>
                                </div>
                                ${isCancelled ? `
                                    <div style="margin-top:5px;padding:5px 8px;border-radius:6px;background:#ffebee;border:1px dashed #e53935;color:#b71c1c;font-size:0.74rem;font-weight:700;">
                                        ❌ Cancel Reason: "${r.cancelReason && r.cancelReason.trim() ? r.cancelReason : 'Cancelled by customer'}"
                                    </div>
                                ` : ''}
                            </div>
                        `;
                    }).join('');
                } else if (s.reservationCode) {
                    html += `📅 <strong>Booked Online: ${s.reservationCode} (${s.reservedTimeSlot})</strong>` +
                        (s.reservedOccasion ? ` • 🎉 ${s.reservedOccasion}` : '') +
                        `<br>👤 Guest: <strong>${s.reservedCustomerName}</strong> (${s.reservedGuestCount} guests)` +
                        (s.reservedVoucherCode ? ` • Deal: <strong>${s.reservedVoucherCode} (-${s.reservedDiscountPercent}%)</strong>` : '') +
                        `<br>📝 Customer Note: <strong>${s.reservedSpecialRequest && s.reservedSpecialRequest.trim() ? s.reservedSpecialRequest : 'No special request'}</strong>`;
                }

                if (s.orderNote && s.orderNote.trim()) {
                    html += `<div style="margin-top:4px;padding-top:4px;border-top:1px dashed rgba(230,81,0,0.3);">🍽️ Table Order Note: <strong>${s.orderNote}</strong></div>`;
                }
                resBox.innerHTML = html;
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

        // Running Bill Summary + Generate Bill & Checkout Button
        const billBox = $('insp-bill-summary');
        if (billBox) {
            if (s.orderId && items.length > 0) {
                billBox.innerHTML = `
                    <div class="qr-bill-row"><span>Subtotal:</span><strong>${SavoraApp.formatCurrency(s.subtotalAmount)}</strong></div>
                    <div class="qr-bill-row"><span>Voucher Discount ${s.appliedVoucherCode ? `(${s.appliedVoucherCode} -${s.appliedDiscountPercent}%)` : ''}:</span><strong style="color:#2e7d32;">-${SavoraApp.formatCurrency(s.discountAmount)}</strong></div>
                    <div class="qr-bill-row qr-bill-row--total"><span>Total Table Bill:</span><span>${SavoraApp.formatCurrency(s.totalAmount)}</span></div>
                    <button type="button" class="btn-generate-bill" id="btn-open-bill-modal">
                        🧾 Generate Bill &amp; Checkout
                    </button>`;
                $('btn-open-bill-modal')?.addEventListener('click', () => openBillModal(s));
            } else {
                billBox.innerHTML = '';
            }
        }
    };

    // Open Official Bill & Checkout Modal
    const openBillModal = (s) => {
        const modal = $('waiter-bill-modal');
        if (!modal || !s) return;

        const nowStr = new Date().toLocaleString('en-GB', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });

        if ($('bill-header-table-badge')) {
            $('bill-header-table-badge').textContent = `🍽️ TABLE: ${s.tableCode} — ${s.tableName || 'Dining Table'} (${s.area || 'Main Hall'})`;
        }

        if ($('bill-modal-meta')) {
            $('bill-modal-meta').innerHTML = `
                <div><strong>Bill / Order No:</strong> ${s.orderCode}</div>
                <div><strong>Date &amp; Time:</strong> ${nowStr}</div>
                <div><strong>Table Number:</strong> ${s.tableCode} (${s.area})</div>
                <div><strong>Guest:</strong> ${s.reservedCustomerName || 'Walk-in Guest'}</div>
                ${s.reservationCode ? `<div style="grid-column:1 / -1;"><strong>Linked Booking:</strong> ${s.reservationCode} (${s.reservedTimeSlot})</div>` : ''}
            `;
        }

        const items = s.placedItems || [];
        const unservedItems = items.filter((i) => i.itemStatus !== 'SERVED');
        const warnBox = $('bill-modal-warning');
        const confirmBtn = $('btn-bill-confirm-pay');

        if (warnBox && confirmBtn) {
            if (unservedItems.length > 0) {
                warnBox.style.display = 'block';
                warnBox.textContent = `⚠️ Warning: ${unservedItems.length} dish(es) are not marked as SERVED yet (${unservedItems.map(u => `${u.dishName} [${u.itemStatus}]`).join(', ')}). Please mark all dishes SERVED or remove PENDING items before completing payment.`;
                confirmBtn.disabled = true;
                confirmBtn.style.opacity = '0.55';
            } else {
                warnBox.style.display = 'none';
                confirmBtn.disabled = false;
                confirmBtn.style.opacity = '1';
            }
        }

        if ($('bill-modal-items')) {
            $('bill-modal-items').innerHTML = items.map((i) => `
                <tr>
                    <td>
                        <strong>${i.dishName}</strong>
                        <span style="font-size:0.7rem;color:var(--text-muted);display:block;">Status: ${i.itemStatus}</span>
                    </td>
                    <td style="text-align:center;font-weight:700;">${i.quantity}</td>
                    <td style="text-align:right;">${SavoraApp.formatCurrency(i.unitPrice)}</td>
                    <td style="text-align:right;font-weight:700;">${SavoraApp.formatCurrency(i.lineTotal)}</td>
                </tr>
            `).join('');
        }

        if ($('bill-modal-totals')) {
            $('bill-modal-totals').innerHTML = `
                <div class="qr-bill-row"><span>Subtotal:</span><strong>${SavoraApp.formatCurrency(s.subtotalAmount)}</strong></div>
                <div class="qr-bill-row"><span>Voucher Discount ${s.appliedVoucherCode ? `(${s.appliedVoucherCode} -${s.appliedDiscountPercent}%)` : ''}:</span><strong style="color:#2e7d32;">-${SavoraApp.formatCurrency(s.discountAmount)}</strong></div>
                <div class="qr-bill-row qr-bill-row--total" style="font-size:1.05rem;margin-top:6px;padding-top:8px;border-top:1.5px solid var(--border-color);">
                    <span>TOTAL AMOUNT DUE:</span>
                    <span style="color:#d32f2f;font-weight:800;">${SavoraApp.formatCurrency(s.totalAmount)}</span>
                </div>
            `;
        }

        // Populate Dynamic VietQR (MBBank - 0332280605 - NGUYENMINH) with amount & bill code
        const rawAmount = Math.max(0, Math.round(Number(s.totalAmount || 0)));
        const billCode = (s.orderCode || 'SAVORA').trim();
        const vietQrUrl = `https://img.vietqr.io/image/MB-0332280605-qr_only.png?amount=${rawAmount}&addInfo=${encodeURIComponent(billCode)}&accountName=${encodeURIComponent('NGUYENMINH')}`;

        if ($('bill-vietqr-img')) {
            $('bill-vietqr-img').src = vietQrUrl;
        }
        if ($('bill-vietqr-amount')) {
            $('bill-vietqr-amount').textContent = SavoraApp.formatCurrency(rawAmount);
        }
        if ($('bill-vietqr-addinfo')) {
            $('bill-vietqr-addinfo').textContent = billCode;
        }
        if ($('bill-cash-amount')) {
            $('bill-cash-amount').textContent = SavoraApp.formatCurrency(rawAmount);
        }

        syncPaymentMethodView();
        modal.style.display = 'flex';
    };

    const syncPaymentMethodView = () => {
        const selectedMethod = document.querySelector('input[name="waiterPaymentMethod"]:checked')?.value || 'BANK_TRANSFER';
        const qrBox = $('bill-vietqr-box');
        const cashBox = $('bill-cash-box');
        if (selectedMethod === 'BANK_TRANSFER') {
            if (qrBox) qrBox.style.display = 'block';
            if (cashBox) cashBox.style.display = 'none';
        } else {
            if (qrBox) qrBox.style.display = 'none';
            if (cashBox) cashBox.style.display = 'block';
        }
    };

    // Payment method radio highlighting & toggling between VietQR and Cash
    document.querySelectorAll('#bill-method-options input[name="waiterPaymentMethod"]').forEach((radio) => {
        radio.addEventListener('change', () => {
            document.querySelectorAll('#bill-method-options .bill-method-option').forEach((lbl) => lbl.classList.remove('active'));
            radio.closest('.bill-method-option')?.classList.add('active');
            syncPaymentMethodView();
        });
    });

    $('btn-bill-close')?.addEventListener('click', () => {
        if ($('waiter-bill-modal')) $('waiter-bill-modal').style.display = 'none';
    });

    $('btn-bill-print')?.addEventListener('click', () => {
        const card = $('printable-receipt-area');
        if (card) card.scrollTop = 0;
        window.print();
    });

    $('btn-bill-confirm-pay')?.addEventListener('click', async () => {
        if (!currentSession || !currentSession.tableCode) return;
        const selectedMethod = document.querySelector('input[name="waiterPaymentMethod"]:checked')?.value || 'CASH';
        const btn = $('btn-bill-confirm-pay');
        btn.disabled = true;
        const origText = btn.textContent;
        btn.textContent = 'Processing Payment...';

        try {
            const res = await SavoraApp.fetchApi(`/api/staff/tables/${encodeURIComponent(currentSession.tableCode)}/checkout`, {
                method: 'POST',
                body: JSON.stringify({ paymentMethod: selectedMethod })
            });
            currentSession = res.data;
            if ($('waiter-bill-modal')) $('waiter-bill-modal').style.display = 'none';
            showToast(`✅ Bill paid (${selectedMethod})! Table ${currentSession.tableCode} is now set to CLEANING.`);
            await loadFloorTables();
        } catch (err) {
            const warnBox = $('bill-modal-warning');
            if (warnBox) {
                warnBox.style.display = 'block';
                warnBox.textContent = err.message;
            }
        } finally {
            btn.disabled = false;
            btn.textContent = origText;
        }
    });

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
    if (window.SavoraApp && SavoraApp.onRealtimeSync) {
        SavoraApp.onRealtimeSync(loadFloorTables);
    }
    setInterval(loadFloorTables, 3000);
});
