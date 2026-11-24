document.addEventListener('DOMContentLoaded', () => {
    let activeCategory = 'ALL', searchKeyword = '', activeZone = 'ALL';
    let allTables = [], currentTables = [], currentDishes = [], selectedTableId = null;

    const $ = (id) => document.getElementById(id);
    const dateInput = $('pasgo-date'), timeInput = $('pasgo-time');
    const adultsSelect = $('pasgo-adults'), childrenSelect = $('pasgo-children');
    const tableSelect = $('pasgo-table-select'), voucherSelect = $('pasgo-voucher-select');
    const occasionInput = $('pasgo-occasion'), slotWindowLabel = $('slot-window-label');
    const capHintLabel = $('selected-table-cap-hint');

    const getLocalDateStr = (d = new Date()) =>
        `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

    if (dateInput) {
        dateInput.value = dateInput.min = getLocalDateStr();
    }

    const updateSlotWindowLabel = (t) => {
        if (!slotWindowLabel || !t) return;
        const [hh, mm] = t.split(':').map(Number);
        slotWindowLabel.textContent = `✓ ${t} – ${String((hh + 2) % 24).padStart(2, '0')}:${String(mm).padStart(2, '0')} (120m)`;
    };

    const syncTimeSlots = () => {
        const btns = [...document.querySelectorAll('.timeslot-btn')];
        if (!btns.length || !dateInput) return;

        const today = getLocalDateStr();
        const isToday = (dateInput.value || today) === today;
        const now = new Date();
        const curMins = now.getHours() * 60 + now.getMinutes();
        let activeValid = false;

        btns.forEach((btn) => {
            const [hh, mm] = (btn.dataset.time || '19:00').split(':').map(Number);
            const isPast = isToday && (hh * 60 + mm) <= curMins;
            btn.disabled = isPast;
            btn.classList.toggle('past-slot', isPast);
            if (btn.classList.contains('active')) {
                if (isPast) btn.classList.remove('active');
                else activeValid = true;
            }
        });

        if (!activeValid) {
            const nextBtn = btns.find((b) => !b.disabled);
            if (nextBtn) {
                nextBtn.classList.add('active');
                if (timeInput) timeInput.value = nextBtn.dataset.time;
                updateSlotWindowLabel(nextBtn.dataset.time);
            } else if (isToday) {
                const tmr = new Date();
                tmr.setDate(tmr.getDate() + 1);
                dateInput.value = getLocalDateStr(tmr);
                syncTimeSlots();
            }
        }
    };

    const updateSummaryBanner = () => {
        const el = $('table-slot-summary');
        if (!el) return;
        const a = parseInt(adultsSelect?.value || '2', 10), c = parseInt(childrenSelect?.value || '0', 10);
        const sel = allTables.find((t) => t.tableId === selectedTableId);
        el.innerHTML = `Availability for <strong>${dateInput?.value}</strong> at <strong>${timeInput?.value}</strong> • Party of <strong>${a + c}</strong> (${a} Adults, ${c} Children)` +
            (sel ? ` • Selected: <strong>${sel.tableCode} (Max ${sel.capacity} seats)</strong>` : '');
    };

    const syncChildren = (maxCap, adults) => {
        if (!childrenSelect) return;
        const maxChild = Math.max(0, maxCap - adults);
        const curChild = Math.min(parseInt(childrenSelect.value || '0', 10), maxChild);
        childrenSelect.innerHTML = Array.from({ length: maxChild + 1 }, (_, c) =>
            `<option value="${c}" ${c === curChild ? 'selected' : ''}>${c === 1 ? '1 Child' : `${c} Children`}</option>`
        ).join('');
        updateSummaryBanner();
    };

    const syncGuestCapacity = () => {
        const sel = allTables.find((t) => t.tableId === selectedTableId);
        const maxCap = sel ? sel.capacity : 16;
        if (capHintLabel) capHintLabel.textContent = sel ? `Max ${maxCap} seats (${sel.tableCode})` : `Max ${maxCap} seats`;
        if (!adultsSelect) return;

        const curAdults = Math.min(Math.max(1, parseInt(adultsSelect.value || '2', 10)), maxCap);
        adultsSelect.innerHTML = Array.from({ length: maxCap }, (_, i) => {
            const a = i + 1;
            return `<option value="${a}" ${a === curAdults ? 'selected' : ''}>${a === 1 ? '1 Adult' : `${a} Adults`}</option>`;
        }).join('');
        syncChildren(maxCap, curAdults);
    };

    const renderTables = () => {
        currentTables = activeZone === 'ALL' ? allTables : allTables.filter((t) => t.area === activeZone);
        const bookable = currentTables.filter((t) => t.bookableForSlot);
        if (!bookable.some((t) => t.tableId === selectedTableId)) {
            selectedTableId = bookable[0]?.tableId || null;
        }

        if (tableSelect) {
            tableSelect.innerHTML = bookable.length
                ? bookable.map((t) => `<option value="${t.tableId}" ${t.tableId === selectedTableId ? 'selected' : ''}>${t.tableCode} — ${t.tableName} (Max ${t.capacity} seats)</option>`).join('')
                : `<option value="">No available tables for this slot</option>`;
        }

        syncGuestCapacity();

        const grid = $('table-grid');
        if (!grid) return;
        grid.innerHTML = currentTables.map((t) => {
            const sel = t.bookableForSlot && t.tableId === selectedTableId;
            return `
                <div class="table-card ${t.bookableForSlot ? `bookable ${sel ? 'selected-table' : ''}` : 'unbookable'}" data-id="${t.tableId}">
                    <div>
                        <div class="table-card__top">
                            <span class="table-card__code">${t.tableCode}</span>
                            <span class="table-status-badge badge-status-${t.operationalStatus}">${t.operationalStatus}</span>
                        </div>
                        <h4 class="table-card__name" style="margin-top:8px;">${t.tableName}</h4>
                        <div class="table-card__meta" style="margin-top:4px;">
                            <span>📍 ${t.area}</span><span>👥 Max ${t.capacity} Seats</span>
                        </div>
                        ${t.bookableForSlot
                            ? `<p style="font-size:0.78rem;color:var(--text-muted);margin-top:6px;">${t.note || 'Ready for reservation'}</p>`
                            : `<p class="table-card__reason" style="margin-top:6px;">⚠ ${t.unavailableReason || 'Unavailable'}</p>`}
                    </div>
                    <div style="margin-top:8px;">
                        <button type="button" class="btn ${t.bookableForSlot ? (sel ? 'btn-primary' : 'btn-outline') : ''}"
                            ${t.bookableForSlot ? '' : 'disabled'} style="width:100%;padding:7px 12px;font-size:0.8rem;">
                            ${t.bookableForSlot ? (sel ? '✓ Selected for Booking' : 'Select This Table') : 'Not Eligible'}
                        </button>
                    </div>
                </div>`;
        }).join('');

        grid.querySelectorAll('.table-card.bookable').forEach((card) => {
            card.onclick = () => {
                selectedTableId = parseInt(card.dataset.id, 10);
                if (tableSelect) tableSelect.value = String(selectedTableId);
                syncGuestCapacity();
                renderTables();
            };
        });
    };

    const renderZones = () => {
        const box = document.querySelector('.zone-filter-pills');
        if (!box) return;
        const areas = [...new Set(allTables.map((t) => t.area).filter(Boolean))];
        if (activeZone !== 'ALL' && !areas.includes(activeZone)) activeZone = 'ALL';

        box.innerHTML = [
            `<button type="button" class="zone-pill ${activeZone === 'ALL' ? 'active' : ''}" data-area="ALL">All Zones (${allTables.length})</button>`,
            ...areas.map((a) => `<button type="button" class="zone-pill ${activeZone === a ? 'active' : ''}" data-area="${a}">${a}</button>`)
        ].join('');

        box.querySelectorAll('.zone-pill').forEach((pill) => {
            pill.onclick = () => {
                activeZone = pill.dataset.area || 'ALL';
                renderZones();
                renderTables();
            };
        });
    };

    const loadTables = async () => {
        const q = `/api/public/pasgo/tables?date=${encodeURIComponent(dateInput?.value || getLocalDateStr())}&time=${encodeURIComponent(timeInput?.value || '19:00')}&adults=${adultsSelect?.value || 2}&children=${childrenSelect?.value || 0}&area=ALL`;
        const res = await SavoraApp.fetchApi(q);
        allTables = res.data || [];
        renderZones();
        renderTables();
    };

    const renderMenu = () => {
        const grid = $('menu-grid');
        if (!grid) return;
        const list = currentDishes.filter((d) =>
            (activeCategory === 'ALL' || d.categoryName === activeCategory) &&
            (!searchKeyword || `${d.dishName} ${d.description || ''}`.toLowerCase().includes(searchKeyword))
        );
        grid.innerHTML = list.length ? list.map((d) => `
            <article class="menu-card">
                <div class="menu-card__media">
                    <img src="${d.thumbnailUrl}" alt="${d.dishName}" class="menu-card__img" loading="lazy">
                    ${d.isSignature ? '<span class="menu-card__badge">★ SIGNATURE</span>' : ''}
                </div>
                <div class="menu-card__body">
                    <span class="menu-card__cat">${d.categoryName}</span>
                    <h3 class="menu-card__title">${d.dishName}</h3>
                    <p class="menu-card__desc">${d.description || ''}</p>
                    <div class="menu-card__footer">
                        <div>
                            <span class="menu-card__price">${SavoraApp.formatCurrency(d.discountPrice || d.price)}</span>
                            ${d.discountPrice ? `<span class="menu-card__old-price">${SavoraApp.formatCurrency(d.price)}</span>` : ''}
                        </div>
                    </div>
                </div>
            </article>`).join('') : `<p style="color:var(--text-muted);grid-column:1/-1;text-align:center;padding:24px 0;">No dishes found.</p>`;
    };

    const loadMenu = async () => {
        const res = await SavoraApp.fetchApi('/api/public/pasgo/menu');
        currentDishes = res.data || [];
        const box = document.querySelector('.category-tabs');
        if (box && currentDishes.length) {
            const cats = [...new Set(currentDishes.map((d) => d.categoryName).filter(Boolean))];
            box.innerHTML = [`<button type="button" class="category-tab active" data-category="ALL">All</button>`,
                ...cats.map((c) => `<button type="button" class="category-tab" data-category="${c}">${c}</button>`)].join('');
            box.querySelectorAll('.category-tab').forEach((tab) => {
                tab.onclick = () => {
                    box.querySelectorAll('.category-tab').forEach((t) => t.classList.remove('active'));
                    tab.classList.add('active');
                    activeCategory = tab.dataset.category || 'ALL';
                    renderMenu();
                };
            });
        }
        renderMenu();
    };

    const loadVouchers = async () => {
        const res = await SavoraApp.fetchApi('/api/public/pasgo/vouchers');
        const vouchers = res.data || [];
        const box = $('pasgo-deals-container');
        if (box && vouchers.length) {
            box.innerHTML = vouchers.map((v, i) => `
                <div class="pasgo-deal-item ${i === 0 ? 'selected' : ''}" data-voucher-id="${v.voucherId}" data-voucher-code="${v.voucherCode}">
                    <div>
                        <span class="pasgo-deal-badge">SAVE ${v.discountPercent}%</span>
                        <div class="pasgo-deal-title">Code: ${v.voucherCode} — ${v.discountPercent}% OFF</div>
                        <div class="pasgo-deal-desc">${v.description || ''}</div>
                    </div>
                    <button type="button" class="btn ${i === 0 ? 'btn-primary' : 'btn-outline'} btn-select-deal"
                            data-voucher-id="${v.voucherId}" data-voucher-code="${v.voucherCode}"
                            style="padding:8px 16px;font-size:0.82rem;white-space:nowrap;">
                        ${i === 0 ? '✓ Selected' : 'Apply Deal'}
                    </button>
                </div>`).join('');
            if (voucherSelect) {
                voucherSelect.innerHTML = vouchers.map((v, i) =>
                    `<option value="${v.voucherId}" ${i === 0 ? 'selected' : ''}>${v.voucherCode} (-${v.discountPercent}%)</option>`
                ).join('') + `<option value="">No offer</option>`;
            }
        }
        document.querySelectorAll('.btn-select-deal').forEach((btn) => {
            btn.onclick = () => {
                document.querySelectorAll('.pasgo-deal-item').forEach((el) => el.classList.remove('selected'));
                document.querySelectorAll('.btn-select-deal').forEach((b) => {
                    b.className = 'btn btn-outline btn-select-deal';
                    b.textContent = 'Apply Deal';
                });
                btn.closest('.pasgo-deal-item')?.classList.add('selected');
                btn.className = 'btn btn-primary btn-select-deal';
                btn.textContent = '✓ Selected';
                if (voucherSelect) voucherSelect.value = btn.dataset.voucherId;
            };
        });
    };

    // Event bindings
    document.querySelectorAll('.timeslot-btn').forEach((btn) => {
        btn.onclick = () => {
            if (btn.disabled) return;
            document.querySelectorAll('.timeslot-btn').forEach((b) => b.classList.remove('active'));
            btn.classList.add('active');
            if (timeInput) timeInput.value = btn.dataset.time;
            updateSlotWindowLabel(btn.dataset.time);
            loadTables();
        };
    });

    document.querySelectorAll('.occasion-pill').forEach((pill) => {
        pill.onclick = () => {
            document.querySelectorAll('.occasion-pill').forEach((p) => p.classList.remove('active'));
            pill.classList.add('active');
            if (occasionInput) occasionInput.value = pill.dataset.occasion;
        };
    });

    dateInput?.addEventListener('change', () => { syncTimeSlots(); loadTables(); });
    adultsSelect?.addEventListener('change', () => {
        const sel = allTables.find((t) => t.tableId === selectedTableId);
        syncChildren(sel ? sel.capacity : 16, parseInt(adultsSelect.value || '1', 10));
    });
    childrenSelect?.addEventListener('change', updateSummaryBanner);
    tableSelect?.addEventListener('change', () => {
        selectedTableId = parseInt(tableSelect.value, 10) || null;
        syncGuestCapacity();
        renderTables();
    });
    $('menu-search-input')?.addEventListener('input', (e) => {
        searchKeyword = e.target.value.trim().toLowerCase();
        renderMenu();
    });

    $('pasgo-booking-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const alertBox = $('pasgo-form-alert'), submitBtn = $('btn-pasgo-submit');
        if (alertBox) alertBox.style.display = 'none';

        const payload = {
            adultCount: parseInt(adultsSelect.value, 10),
            childCount: parseInt(childrenSelect.value, 10),
            reservationDate: dateInput.value,
            reservationTime: timeInput.value,
            preferredArea: activeZone === 'ALL' ? null : activeZone,
            tableId: parseInt(tableSelect.value, 10),
            voucherId: voucherSelect.value ? parseInt(voucherSelect.value, 10) : null,
            occasion: occasionInput.value,
            customerName: $('pasgo-name').value.trim(),
            customerPhone: $('pasgo-phone').value.trim(),
            customerEmail: $('pasgo-email').value.trim(),
            specialRequest: $('pasgo-note').value.trim()
        };

        submitBtn.disabled = true;
        try {
            const res = await SavoraApp.fetchApi('/api/customer/reservations', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            const t = res.data;
            $('pasgo-modal-details').innerHTML = `
                <div class="pasgo-ticket-row"><span>Booking Code:</span><strong style="color:#d32f2f;">${t.reservationCode}</strong></div>
                <div class="pasgo-ticket-row"><span>Guest:</span><strong>${t.customerName} (${t.customerPhone})</strong></div>
                <div class="pasgo-ticket-row"><span>Date &amp; Slot:</span><strong>${t.reservationDate} | ${t.reservationTime} – ${t.endTime}</strong></div>
                <div class="pasgo-ticket-row"><span>Party Size:</span><strong>${t.guestCount} Guests (${t.adultCount} Adults, ${t.childCount} Children)</strong></div>
                <div class="pasgo-ticket-row"><span>Table:</span><strong>${t.tableCode} — ${t.tableName}</strong></div>
                <div class="pasgo-ticket-row"><span>Offer:</span><strong style="color:#2e7d32;">${t.voucherCode ? `${t.voucherCode} (-${t.discountPercent}%)` : 'Standard'}</strong></div>
                <div style="display:flex;gap:12px;margin-top:20px;">
                    <a href="/customer/reservations" class="btn btn-primary" style="flex:1;text-align:center;">View My Bookings</a>
                    <button type="button" class="btn btn-outline" id="btn-close-pasgo-modal" style="flex:1;">Done</button>
                </div>`;
            $('pasgo-success-modal').style.display = 'flex';
            $('btn-close-pasgo-modal').onclick = () => { $('pasgo-success-modal').style.display = 'none'; };
            await loadTables();
        } catch (err) {
            if (err.message && err.message.toLowerCase().includes('please sign in')) {
                const promptModal = $('signin-prompt-modal');
                if (promptModal) promptModal.style.display = 'flex';
            } else if (alertBox) {
                alertBox.textContent = err.message;
                alertBox.style.display = 'block';
            }
        } finally {
            submitBtn.disabled = false;
        }
    });

    $('btn-signin-yes')?.addEventListener('click', () => {
        window.location.href = '/login';
    });
    $('btn-signin-cancel')?.addEventListener('click', () => {
        const promptModal = $('signin-prompt-modal');
        if (promptModal) promptModal.style.display = 'none';
    });

    syncTimeSlots();
    setInterval(syncTimeSlots, 30000);
    loadVouchers();
    loadTables();
    loadMenu();
});
