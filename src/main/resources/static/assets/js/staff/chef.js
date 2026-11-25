/**
 * SAVORA — Chef Kitchen Display System (KDS)
 * Workflow: PENDING -> COOKING -> READY (then Waiter marks SERVED)
 */
document.addEventListener('DOMContentLoaded', () => {
  let allKitchenItems = [];
  let selectedTableFilter = 'ALL';

  const colPendingList = document.getElementById('colPendingList');
  const colCookingList = document.getElementById('colCookingList');
  const colReadyList   = document.getElementById('colReadyList');

  const kpiChefPending = document.getElementById('kpiChefPending');
  const kpiChefCooking = document.getElementById('kpiChefCooking');
  const kpiChefReady   = document.getElementById('kpiChefReady');
  const kpiChefServed  = document.getElementById('kpiChefServed');

  const badgePendingCount = document.getElementById('badgePendingCount');
  const badgeCookingCount = document.getElementById('badgeCookingCount');
  const badgeReadyCount   = document.getElementById('badgeReadyCount');

  const chefTableFilterPills = document.getElementById('chefTableFilterPills');
  const chefActionAlert      = document.getElementById('chefActionAlert');

  function showAlert(msg, type = 'success') {
    if (!chefActionAlert) return;
    chefActionAlert.className = `alert alert-${type}`;
    chefActionAlert.textContent = msg;
    chefActionAlert.style.display = 'block';
    setTimeout(() => {
      chefActionAlert.style.display = 'none';
    }, 3500);
  }

  async function loadKitchenQueue() {
    try {
      const res = await SavoraApp.fetchApi('/api/staff/kitchen/items');
      allKitchenItems = res?.data || [];
      updateKpisAndTablePills();
      renderKanbanColumns();
    } catch (err) {
      if (colPendingList) {
        colPendingList.innerHTML = `<div class="chef-empty-col">${err.message || 'Unable to load kitchen queue.'}</div>`;
      }
    }
  }

  function updateKpisAndTablePills() {
    let pendingQty = 0;
    let cookingQty = 0;
    let readyQty   = 0;
    let servedQty  = 0;

    const activeTables = new Set();

    allKitchenItems.forEach(item => {
      const qty = Number(item.quantity || 1);
      if (item.itemStatus === 'PENDING') pendingQty += qty;
      else if (item.itemStatus === 'COOKING') cookingQty += qty;
      else if (item.itemStatus === 'READY') readyQty += qty;
      else if (item.itemStatus === 'SERVED') servedQty += qty;

      if (item.itemStatus !== 'SERVED' && item.tableCode) {
        activeTables.add(item.tableCode);
      }
    });

    if (kpiChefPending) kpiChefPending.textContent = pendingQty;
    if (kpiChefCooking) kpiChefCooking.textContent = cookingQty;
    if (kpiChefReady)   kpiChefReady.textContent   = readyQty;
    if (kpiChefServed)  kpiChefServed.textContent  = servedQty;

    if (chefTableFilterPills) {
      const tables = Array.from(activeTables).sort();
      if (selectedTableFilter !== 'ALL' && !activeTables.has(selectedTableFilter)) {
        selectedTableFilter = 'ALL';
      }
      chefTableFilterPills.innerHTML = [
        `<button type="button" class="chef-pill ${selectedTableFilter === 'ALL' ? 'active' : ''}" data-table="ALL">All Active Tables (${tables.length})</button>`,
        ...tables.map(code => `<button type="button" class="chef-pill ${selectedTableFilter === code ? 'active' : ''}" data-table="${code}">Table ${code}</button>`)
      ].join('');

      chefTableFilterPills.querySelectorAll('.chef-pill').forEach(btn => {
        btn.addEventListener('click', () => {
          selectedTableFilter = btn.dataset.table;
          updateKpisAndTablePills();
          renderKanbanColumns();
        });
      });
    }
  }

  function renderTicketCard(item) {
    const status = item.itemStatus;
    const noteText = item.note || item.orderNote || '';
    const noteHtml = noteText
      ? `<div class="chef-note-box">📝 Chef Note: ${noteText}</div>`
      : '';

    let actionHtml = '';
    if (status === 'PENDING') {
      actionHtml = `
        <div class="chef-ticket-actions">
          <button type="button" class="btn-chef-cook" data-id="${item.orderItemId}" data-next="COOKING">
            🔥 Start Cooking
          </button>
        </div>`;
    } else if (status === 'COOKING') {
      actionHtml = `
        <div class="chef-ticket-actions">
          <button type="button" class="btn-chef-ready" data-id="${item.orderItemId}" data-next="READY">
            🔔 Mark Ready for Waiter
          </button>
        </div>`;
    } else if (status === 'READY') {
      actionHtml = `
        <div class="chef-ticket-actions">
          <div class="chef-waiting-waiter">
            🔔 Waiting for Waiter to serve Table ${item.tableCode}
          </div>
        </div>`;
    }

    return `
      <article class="chef-ticket-card ticket-${status.toLowerCase()}">
        <div class="chef-ticket-top">
          <span class="chef-table-chip">🍽️ ${item.tableCode} • ${item.area || 'Main Hall'}</span>
          <span class="chef-time-meta">🕒 ${item.orderedAt || 'Just now'} • ~${item.prepTimeMinutes || 15}m</span>
        </div>
        <div class="chef-dish-row">
          <span class="chef-qty-badge">${item.quantity}x</span>
          <div>
            <div class="chef-dish-title">${item.dishName}</div>
            <div class="chef-dish-cat">${item.categoryName || 'Main Course'} • Order ${item.orderCode}</div>
          </div>
        </div>
        ${noteHtml}
        ${actionHtml}
      </article>
    `;
  }

  function renderKanbanColumns() {
    const filtered = allKitchenItems.filter(i =>
      selectedTableFilter === 'ALL' || i.tableCode === selectedTableFilter
    );

    const pendingItems = filtered.filter(i => i.itemStatus === 'PENDING');
    const cookingItems = filtered.filter(i => i.itemStatus === 'COOKING');
    const readyItems   = filtered.filter(i => i.itemStatus === 'READY');

    if (badgePendingCount) badgePendingCount.textContent = pendingItems.length;
    if (badgeCookingCount) badgeCookingCount.textContent = cookingItems.length;
    if (badgeReadyCount)   badgeReadyCount.textContent   = readyItems.length;

    if (colPendingList) {
      colPendingList.innerHTML = pendingItems.length
        ? pendingItems.map(renderTicketCard).join('')
        : `<div class="chef-empty-col">No new dishes waiting to cook.</div>`;
    }

    if (colCookingList) {
      colCookingList.innerHTML = cookingItems.length
        ? cookingItems.map(renderTicketCard).join('')
        : `<div class="chef-empty-col">No dishes currently on the stove.</div>`;
    }

    if (colReadyList) {
      colReadyList.innerHTML = readyItems.length
        ? readyItems.map(renderTicketCard).join('')
        : `<div class="chef-empty-col">No dishes waiting for Waiter pickup.</div>`;
    }

    document.querySelectorAll('.btn-chef-cook, .btn-chef-ready').forEach(btn => {
      btn.addEventListener('click', () => updateDishStatus(Number(btn.dataset.id), btn.dataset.next, btn));
    });
  }

  async function updateDishStatus(orderItemId, targetStatus, btnEl) {
    const originalText = btnEl.textContent;
    btnEl.disabled = true;
    btnEl.textContent = 'Updating...';
    try {
      const res = await SavoraApp.fetchApi(`/api/staff/kitchen/items/${orderItemId}/status`, {
        method: 'POST',
        body: JSON.stringify({ status: targetStatus })
      });
      showAlert(res.message || `Dish updated to ${targetStatus}.`, 'success');
      await loadKitchenQueue();
    } catch (err) {
      showAlert(err.message || 'Failed to update dish status.', 'error');
      btnEl.disabled = false;
      btnEl.textContent = originalText;
    }
  }

  document.getElementById('btnRefreshKitchen')?.addEventListener('click', loadKitchenQueue);

  loadKitchenQueue();
  if (window.SavoraApp && SavoraApp.onRealtimeSync) {
    SavoraApp.onRealtimeSync(loadKitchenQueue);
  }
  setInterval(loadKitchenQueue, 2500);
});
