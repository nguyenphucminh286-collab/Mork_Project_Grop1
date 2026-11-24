document.addEventListener('DOMContentLoaded', () => {
    const $ = (id) => document.getElementById(id);
    const currentTableCode = document.body.dataset.initialTable || 'T1-B01';
    let currentSession = null, allDishes = [], allVouchers = [], activeCategory = 'ALL', searchKeyword = '';
    let cart = [];

    const showToast = (msg) => {
        const toast = $('home-toast');
        if (!toast) return;
        toast.textContent = msg;
        toast.style.display = 'block';
        clearTimeout(window.__qrToastTimer);
        window.__qrToastTimer = setTimeout(() => { toast.style.display = 'none'; }, 3000);
    };

    const switchPanelTab = (tabName) => {
        const isCart = tabName === 'cart';
        $('tab-btn-cart')?.classList.toggle('active', isCart);
        $('tab-btn-kitchen')?.classList.toggle('active', !isCart);
        if ($('tab-content-cart')) $('tab-content-cart').style.display = isCart ? 'block' : 'none';
        if ($('tab-content-kitchen')) $('tab-content-kitchen').style.display = isCart ? 'none' : 'block';
    };

    $('tab-btn-cart')?.addEventListener('click', () => switchPanelTab('cart'));
    $('tab-btn-kitchen')?.addEventListener('click', () => switchPanelTab('kitchen'));

    const loadVouchers = async () => {
        try {
            const res = await SavoraApp.fetchApi('/api/public/pasgo/vouchers');
            allVouchers = res.data || [];
            const sel = $('qr-voucher-select');
            if (sel) {
                sel.innerHTML = `<option value="" data-pct="0">No promotional voucher</option>` +
                    allVouchers.map((v) =>
                        `<option value="${v.voucherCode}" data-pct="${v.discountPercent || 0}">${v.voucherCode} (-${v.discountPercent}%)</option>`
                    ).join('');
                sel.onchange = () => renderCart();
            }
        } catch (_) {}
        renderCart();
    };

    const loadTableSession = async () => {
        try {
            const res = await SavoraApp.fetchApi(`/api/public/qr/session?table=${encodeURIComponent(currentTableCode)}`);
            currentSession = res.data;
            renderSessionBanner();
            renderKitchenTracker();
        } catch (err) {
            showToast(err.message);
        }
    };

    const renderSessionBanner = () => {
        if (!currentSession) return;
        const s = currentSession;
        if ($('qr-breadcrumb-table')) $('qr-breadcrumb-table').textContent = s.tableCode;
        if ($('qr-badge-code')) $('qr-badge-code').textContent = s.tableCode;
        if ($('qr-table-name')) $('qr-table-name').textContent = `${s.tableCode} — ${s.tableName}`;
        if ($('qr-table-area')) $('qr-table-area').textContent = `📍 ${s.area}`;
        if ($('qr-table-capacity')) $('qr-table-capacity').textContent = `👥 Max ${s.capacity} Seats`;
        if ($('qr-table-note')) $('qr-table-note').textContent = s.tableNote || 'Scan QR at table to order directly to the kitchen';

        const statusBadge = $('qr-table-status');
        if (statusBadge) {
            statusBadge.className = `table-status-badge badge-status-${s.tableStatus}`;
            statusBadge.textContent = s.tableStatus;
        }

        const resPill = $('qr-linked-reservation');
        if (resPill) {
            if (s.reservationCode) {
                resPill.style.display = 'inline-flex';
                resPill.innerHTML = `✓ Linked Booking: <strong>${s.reservationCode}</strong> • Guest: <strong>${s.reservedCustomerName}</strong> (${s.reservedTimeSlot})` +
                    (s.reservedVoucherCode ? ` • Deal: <strong>${s.reservedVoucherCode} (-${s.reservedDiscountPercent}%)</strong>` : '');
            } else {
                resPill.style.display = 'none';
            }
        }

        const voucherSelect = $('qr-voucher-select');
        const activeVoucher = s.appliedVoucherCode || s.reservedVoucherCode;
        if (voucherSelect && activeVoucher && !voucherSelect.value) {
            voucherSelect.value = activeVoucher;
            renderCart();
        }
    };

    const renderKitchenTracker = () => {
        if (!currentSession) return;
        const items = currentSession.placedItems || [];
        if ($('qr-placed-count')) $('qr-placed-count').textContent = String(items.reduce((sum, i) => sum + i.quantity, 0));
        if ($('qr-active-order-code')) {
            $('qr-active-order-code').textContent = currentSession.orderCode
                ? `Order: ${currentSession.orderCode} (${currentSession.orderStatus})`
                : 'No active order yet';
        }

        const listBox = $('qr-kitchen-items-list');
        if (listBox) {
            listBox.innerHTML = items.length ? items.map((i) => `
                <div class="kitchen-item">
                    <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
                        <strong style="font-size:0.88rem;">${i.quantity}x ${i.dishName}</strong>
                        <span class="kitchen-badge k-status-${i.itemStatus}">${i.itemStatus}</span>
                    </div>
                    <div style="display:flex;justify-content:space-between;font-size:0.78rem;color:var(--text-muted);margin-top:4px;">
                        <span>${i.note ? `📝 ${i.note}` : 'Standard prep'} ${i.orderedAt ? `• ${i.orderedAt}` : ''}</span>
                        <strong>${SavoraApp.formatCurrency(i.lineTotal)}</strong>
                    </div>
                </div>`).join('')
                : `<p style="text-align:center;color:var(--text-muted);font-size:0.85rem;padding:24px 0;">No dishes sent to the kitchen yet.</p>`;
        }

        const billBox = $('qr-running-bill');
        if (billBox) {
            if (currentSession.orderId) {
                billBox.innerHTML = `
                    <div class="qr-bill-row"><span>Subtotal:</span><strong>${SavoraApp.formatCurrency(currentSession.subtotalAmount)}</strong></div>
                    <div class="qr-bill-row"><span>Voucher Discount ${currentSession.appliedVoucherCode ? `(${currentSession.appliedVoucherCode} -${currentSession.appliedDiscountPercent}%)` : ''}:</span><strong style="color:#2e7d32;">-${SavoraApp.formatCurrency(currentSession.discountAmount)}</strong></div>
                    <div class="qr-bill-row qr-bill-row--total"><span>Total Table Bill:</span><span>${SavoraApp.formatCurrency(currentSession.totalAmount)}</span></div>`;
            } else {
                billBox.innerHTML = '';
            }
        }
    };

    const getSelectedVoucherDiscountPct = () => {
        const sel = $('qr-voucher-select');
        if (!sel || !sel.value) return { code: null, pct: 0 };
        const opt = sel.options[sel.selectedIndex];
        const pctFromAttr = opt?.dataset?.pct ? parseInt(opt.dataset.pct, 10) : 0;
        if (pctFromAttr > 0) return { code: sel.value, pct: pctFromAttr };
        const matched = allVouchers.find((v) => v.voucherCode === sel.value);
        return { code: sel.value, pct: matched?.discountPercent || 0 };
    };

    const renderCart = () => {
        const totalQty = cart.reduce((sum, i) => sum + i.quantity, 0);
        if ($('qr-cart-count')) $('qr-cart-count').textContent = String(totalQty);

        const box = $('qr-cart-items');
        if (!box) return;

        const subtotal = cart.reduce((sum, i) => sum + i.price * i.quantity, 0);
        const { code, pct } = getSelectedVoucherDiscountPct();
        const discountAmt = pct > 0 ? Math.round((subtotal * pct) / 100) : 0;
        const finalTotal = Math.max(0, subtotal - discountAmt);

        if ($('qr-cart-subtotal')) $('qr-cart-subtotal').textContent = SavoraApp.formatCurrency(subtotal);
        const discRow = $('qr-cart-discount-row');
        if (discRow) {
            if (pct > 0 && subtotal > 0) {
                discRow.style.display = 'flex';
                if ($('qr-cart-discount-label')) $('qr-cart-discount-label').textContent = `Voucher (${code} -${pct}%):`;
                if ($('qr-cart-discount-amount')) $('qr-cart-discount-amount').textContent = `-${SavoraApp.formatCurrency(discountAmt)}`;
            } else {
                discRow.style.display = 'none';
            }
        }
        if ($('qr-cart-final-total')) $('qr-cart-final-total').textContent = SavoraApp.formatCurrency(finalTotal);

        if (!cart.length) {
            box.innerHTML = `<p style="text-align:center;color:var(--text-muted);font-size:0.85rem;padding:22px 0;">Your selection is empty. Pick dishes from the menu on the left.</p>`;
            return;
        }

        box.innerHTML = cart.map((item, idx) => `
            <div class="qr-cart-item">
                <div class="qr-cart-item__top">
                    <span class="qr-cart-item__name">${item.dishName}</span>
                    <div class="qr-qty-stepper">
                        <button type="button" class="qr-qty-btn" data-idx="${idx}" data-delta="-1">−</button>
                        <strong style="font-size:0.85rem;min-width:18px;text-align:center;">${item.quantity}</strong>
                        <button type="button" class="qr-qty-btn" data-idx="${idx}" data-delta="1">+</button>
                    </div>
                </div>
                <div style="display:flex;justify-content:space-between;align-items:center;font-size:0.78rem;color:var(--text-muted);">
                    <span>${item.note ? `📝 ${item.note}` : 'No special note'}</span>
                    <strong style="color:var(--primary-color);">${SavoraApp.formatCurrency(item.price * item.quantity)}</strong>
                </div>
            </div>`).join('');

        box.querySelectorAll('.qr-qty-btn').forEach((btn) => {
            btn.onclick = () => {
                const idx = parseInt(btn.dataset.idx, 10);
                const delta = parseInt(btn.dataset.delta, 10);
                if (!cart[idx]) return;
                cart[idx].quantity += delta;
                if (cart[idx].quantity <= 0) cart.splice(idx, 1);
                renderCart();
            };
        });
    };

    const renderMenu = () => {
        const grid = $('qr-dish-grid');
        if (!grid) return;

        const filtered = allDishes.filter((d) =>
            (activeCategory === 'ALL' || d.categoryName === activeCategory) &&
            (!searchKeyword || `${d.dishName} ${d.description || ''}`.toLowerCase().includes(searchKeyword))
        );

        grid.innerHTML = filtered.length ? filtered.map((d) => {
            const unitPrice = d.discountPrice || d.price;
            return `
                <article class="qr-dish-card">
                    <div class="qr-dish-card__media">
                        <img src="${d.thumbnailUrl}" alt="${d.dishName}" loading="lazy">
                        ${d.isSignature ? '<span class="menu-card__badge">★ SIGNATURE</span>' : ''}
                    </div>
                    <div class="qr-dish-card__body">
                        <span class="menu-card__cat">${d.categoryName}</span>
                        <h3 class="menu-card__title">${d.dishName}</h3>
                        <p class="menu-card__desc">${d.description || ''}</p>
                        <input type="text" class="qr-dish-note-input" id="dish-note-${d.dishId}" placeholder="Note for Chef (e.g. less spicy)...">
                        <div class="qr-dish-actions">
                            <div>
                                <span class="menu-card__price">${SavoraApp.formatCurrency(unitPrice)}</span>
                                ${d.discountPrice ? `<span class="menu-card__old-price">${SavoraApp.formatCurrency(d.price)}</span>` : ''}
                            </div>
                            <button type="button" class="btn btn-primary btn-add-qr-dish"
                                    data-id="${d.dishId}" data-name="${d.dishName}" data-price="${unitPrice}"
                                    style="padding: 7px 12px; font-size: 0.78rem;">
                                + Add
                            </button>
                        </div>
                    </div>
                </article>`;
        }).join('') : `<p style="color:var(--text-muted);grid-column:1/-1;text-align:center;padding:24px 0;">No dishes match your filter.</p>`;

        grid.querySelectorAll('.btn-add-qr-dish').forEach((btn) => {
            btn.onclick = () => {
                const dishId = parseInt(btn.dataset.id, 10);
                const dishName = btn.dataset.name;
                const price = parseFloat(btn.dataset.price);
                const noteInput = $(`dish-note-${dishId}`);
                const note = noteInput ? noteInput.value.trim() : '';

                const existing = cart.find((i) => i.dishId === dishId && i.note === note);
                if (existing) {
                    existing.quantity += 1;
                } else {
                    cart.push({ dishId, dishName, price, quantity: 1, note });
                }
                if (noteInput) noteInput.value = '';
                renderCart();
                switchPanelTab('cart');
                showToast(`Added ${dishName} to Table ${currentTableCode} order!`);
            };
        });
    };

    const loadMenu = async () => {
        const res = await SavoraApp.fetchApi('/api/public/pasgo/menu');
        allDishes = res.data || [];
        const tabsBox = $('qr-category-tabs');
        if (tabsBox && allDishes.length) {
            const cats = [...new Set(allDishes.map((d) => d.categoryName).filter(Boolean))];
            tabsBox.innerHTML = [`<button type="button" class="category-tab active" data-category="ALL">All Dishes</button>`,
                ...cats.map((c) => `<button type="button" class="category-tab" data-category="${c}">${c}</button>`)].join('');
            tabsBox.querySelectorAll('.category-tab').forEach((tab) => {
                tab.onclick = () => {
                    tabsBox.querySelectorAll('.category-tab').forEach((t) => t.classList.remove('active'));
                    tab.classList.add('active');
                    activeCategory = tab.dataset.category || 'ALL';
                    renderMenu();
                };
            });
        }
        renderMenu();
    };

    $('qr-menu-search')?.addEventListener('input', (e) => {
        searchKeyword = e.target.value.trim().toLowerCase();
        renderMenu();
    });

    $('qr-voucher-select')?.addEventListener('change', () => renderCart());

    $('btn-submit-qr-order')?.addEventListener('click', async () => {
        const alertBox = $('qr-order-alert');
        if (alertBox) alertBox.style.display = 'none';

        if (!cart.length) {
            if (alertBox) {
                alertBox.textContent = 'Please add at least 1 dish before sending to the kitchen.';
                alertBox.style.display = 'block';
            }
            return;
        }

        const btn = $('btn-submit-qr-order');
        btn.disabled = true;
        btn.textContent = 'Sending to Kitchen...';

        try {
            const res = await SavoraApp.fetchApi('/api/public/qr/order', {
                method: 'POST',
                body: JSON.stringify({
                    tableCode: currentTableCode,
                    voucherCode: $('qr-voucher-select')?.value || null,
                    orderNote: $('qr-general-note')?.value.trim() || null,
                    items: cart.map((i) => ({ dishId: i.dishId, quantity: i.quantity, note: i.note }))
                })
            });
            cart = [];
            if ($('qr-general-note')) $('qr-general-note').value = '';
            renderCart();
            currentSession = res.data;
            renderSessionBanner();
            renderKitchenTracker();
            switchPanelTab('kitchen');
            showToast(`Order sent to Chef for Table ${currentTableCode}!`);
        } catch (err) {
            if (alertBox) {
                alertBox.textContent = err.message;
                alertBox.style.display = 'block';
            }
        } finally {
            btn.disabled = false;
            btn.textContent = '🔥 Send Order to Kitchen';
        }
    });

    $('btn-refresh-kitchen')?.addEventListener('click', () => {
        loadTableSession();
        showToast('Refreshed live kitchen status!');
    });

    $('btn-show-qr-modal')?.addEventListener('click', () => {
        const qrUrl = `${window.location.origin}/qr/table/${encodeURIComponent(currentTableCode)}`;
        if ($('qr-modal-title')) $('qr-modal-title').textContent = `QR Code — Table ${currentTableCode}`;
        if ($('qr-modal-url')) $('qr-modal-url').textContent = qrUrl;
        if ($('qr-modal-img')) {
            $('qr-modal-img').src = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(qrUrl)}`;
        }
        if ($('qr-preview-modal')) $('qr-preview-modal').style.display = 'flex';
    });

    $('btn-close-qr-modal')?.addEventListener('click', () => {
        if ($('qr-preview-modal')) $('qr-preview-modal').style.display = 'none';
    });

    renderCart();
    loadVouchers();
    loadTableSession();
    loadMenu();
    setInterval(loadTableSession, 15000);
});
