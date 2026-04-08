// Existing admin dashboard logic moved from app.js
const API_BASE = '';

function showToast(message, isError = false) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = 'toast show' + (isError ? ' error' : '');
    setTimeout(() => {
        toast.className = 'toast hidden';
    }, 3000);
}

function scrollToSection(id) {
    const el = document.getElementById(id);
    if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

async function loadRooms(flashId) {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;

        const res = await fetch(`${API_BASE}/api/rooms`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) throw new Error('Failed to load rooms');
        const rooms = await res.json();

        // Stat card
        const statRooms = document.getElementById('stat-total-rooms');
        if (statRooms) statRooms.textContent = rooms.length;

        // Count badge
        const badge = document.getElementById('rooms-count-badge');
        if (badge) badge.textContent = rooms.length;

        // Inline list table
        const tbody = document.getElementById('rooms-table-body');
        if (tbody) {
            tbody.innerHTML = '';
            if (rooms.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#64748b;padding:16px;">No rooms yet — add one above</td></tr>';
            } else {
                rooms.forEach(r => {
                    const avail = r.available
                        ? '<span style="color:#10b981;font-weight:600;">✓ Yes</span>'
                        : '<span style="color:#f87171;font-weight:600;">✗ No</span>';
                    const tr = document.createElement('tr');
                    if (flashId && r.id === flashId) tr.classList.add('row-new');
                    tr.innerHTML = `
                        <td>${r.id}</td>
                        <td><strong>${r.roomNumber || '-'}</strong></td>
                        <td><span class="room-type-badge room-type-${(r.type||'').toLowerCase()}">${r.type || '-'}</span></td>
                        <td>₹${r.price != null ? Number(r.price).toLocaleString('en-IN',{maximumFractionDigits:0}) : '-'}</td>
                        <td>${avail}</td>
                        <td><button class="danger-btn-sm" onclick="deleteRoom(${r.id})">Remove</button></td>`;
                    tbody.appendChild(tr);
                });
                if (flashId) {
                    const newRow = tbody.querySelector('.row-new');
                    if (newRow) newRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }
            }
        }
    } catch (e) {
        console.error(e);
    }
}

async function deleteRoom(roomId) {
    if (!confirm('Remove this room? This cannot be undone.')) return;
    try {
        const token = localStorage.getItem('jwt');
        if (!token) { showToast('Please log in first.', true); return; }
        const res = await fetch(`${API_BASE}/api/rooms/${roomId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to remove room');
        }
        showToast('Room removed');
        loadRooms();
        loadAvailableRoomsCount();
    } catch (e) {
        showToast(e.message, true);
    }
}

async function loadAvailableRoomsCount() {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            return;
        }
        const res = await fetch(`${API_BASE}/api/rooms/available`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const rooms = await res.json();
        document.getElementById('stat-available-rooms').textContent = rooms.length;
    } catch (e) {
        console.error(e);
        showToast('Failed to load available rooms', true);
    }
}

async function loadBookings() {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;
        const res = await fetch(`${API_BASE}/api/bookings`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const bookings = await res.json();
        const tbody = document.getElementById('bookings-table-body');
        if (!tbody) return;
        tbody.innerHTML = '';

        window._adminBookingsCache = {};
        bookings.forEach(b => {
            window._adminBookingsCache[b.id] = b;
            const tr = document.createElement('tr');
            const status = b.status || 'BOOKED';
            const total = b.totalCost != null ? `₹${b.totalCost.toFixed(2)}` : '-';
            tr.innerHTML = `
                <td>${b.id}</td>
                <td>${b.room ? `${b.room.roomNumber} (${b.room.type})` : '-'}</td>
                <td>${b.guest ? b.guest.name : '-'}</td>
                <td>${b.checkInDate}</td>
                <td>${b.checkOutDate}</td>
                <td>${status}</td>
                <td>${total}</td>
                <td class="admin-booking-actions">
                    <button type="button" class="secondary-btn generate-bill-btn" data-booking-id="${b.id}">Generate Bill</button>
                    ${status === 'BOOKED' || status === 'CHECKED_IN' ? `<button type="button" class="secondary-btn cancel-booking-btn" data-booking-id="${b.id}">Cancel</button>` : ''}
                </td>
            `;
            tbody.appendChild(tr);
        });

        tbody.querySelectorAll('.cancel-booking-btn').forEach(btn => {
            btn.addEventListener('click', () => updateBookingStatus(btn.getAttribute('data-booking-id'), 'CANCELLED'));
        });
        tbody.querySelectorAll('.generate-bill-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.getAttribute('data-booking-id');
                const b = window._adminBookingsCache && window._adminBookingsCache[id];
                if (b) showBillModalForBooking(b);
                else showToast('Booking not found', true);
            });
        });

        const statBookings = document.getElementById('stat-total-bookings');
        if (statBookings) statBookings.textContent = bookings.length;
    } catch (e) {
        console.error(e);
        showToast('Failed to load bookings', true);
    }
}

let cachedGuests = [];

async function loadGuests(flashId) {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;
        const res = await fetch(`${API_BASE}/api/guests`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            const stat = document.getElementById('stat-total-guests');
            if (stat) stat.textContent = '-';
            return;
        }
        const guests = await res.json();
        cachedGuests = guests;

        // Update stat card
        const stat = document.getElementById('stat-total-guests');
        if (stat) stat.textContent = guests.length;

        // Update count badge
        const badge = document.getElementById('guests-count-badge');
        if (badge) badge.textContent = guests.length;

        // Render inline guest list table
        const tbody = document.getElementById('guests-table-body');
        if (tbody) {
            tbody.innerHTML = '';
            if (guests.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#64748b;padding:16px;">No guests yet</td></tr>';
            } else {
                guests.forEach(g => {
                    const tr = document.createElement('tr');
                    if (flashId && g.id === flashId) tr.classList.add('row-new');
                    tr.innerHTML = `
                        <td>${g.id}</td>
                        <td>${g.name || '-'}</td>
                        <td style="word-break:break-all;">${g.email || '-'}</td>
                        <td>${g.phone || '-'}</td>
                        <td><button class="danger-btn-sm" onclick="deleteGuest(${g.id})">Remove</button></td>`;
                    tbody.appendChild(tr);
                });
            }
            // Scroll the new row into view
            if (flashId) {
                const newRow = tbody.querySelector('.row-new');
                if (newRow) newRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }

        populateBookingGuestDropdown(guests);
    } catch (e) {
        const stat = document.getElementById('stat-total-guests');
        if (stat) stat.textContent = '-';
    }
}

function loadGuestsCount() {
    loadGuests();
}

function populateBookingRoomDropdown(rooms) {
    const sel = document.getElementById('booking-room-id');
    if (!sel) return;
    const current = sel.value;
    sel.innerHTML = '<option value="">Select room</option>';
    (rooms || []).filter(r => r.available).forEach(r => {
        const opt = document.createElement('option');
        opt.value = r.id;
        opt.textContent = `${r.roomNumber} - ${r.type} (₹${r.price.toFixed(2)})`;
        sel.appendChild(opt);
    });
    if (current) sel.value = current;
}

function populateBookingGuestDropdown(guests) {
    const sel = document.getElementById('booking-guest-id');
    if (!sel) return;
    const current = sel.value;
    sel.innerHTML = '<option value="">Select guest</option>';
    (guests || []).forEach(g => {
        const opt = document.createElement('option');
        opt.value = g.id;
        opt.textContent = `${g.name} (${g.email})`;
        sel.appendChild(opt);
    });
    if (current) sel.value = current;
}

function loadDashboard() {
    loadRooms();
    loadAvailableRoomsCount();
    loadBookings();
    loadGuests();
    loadStaffMembers();
    loadDestinations();
    loadHotels();
    loadBookingStates();
    loadReports();
    loadAuditLogs();
}

// ── Chart.js instances registry ─────────────────────────────────────────────
const _chartInstances = {};

/** Inject Chart.js dynamically if not already present. Returns a promise. */
function injectChartJs() {
    return new Promise((resolve) => {
        if (typeof Chart !== 'undefined') { resolve(true); return; }
        // Try jsDelivr first
        const s = document.createElement('script');
        s.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.2/dist/chart.umd.min.js';
        s.onload  = () => { resolve(true); };
        s.onerror = () => {
            // Fallback to cdnjs
            const s2 = document.createElement('script');
            s2.src = 'https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.2/chart.umd.min.js';
            s2.onload  = () => resolve(true);
            s2.onerror = () => resolve(false);
            document.head.appendChild(s2);
        };
        document.head.appendChild(s);
    });
}

/**
 * Wait up to maxMs for Chart.js (also triggers injection if not present).
 */
function waitForChart(maxMs = 9000) {
    if (typeof Chart !== 'undefined') return Promise.resolve(true);
    // Kick off injection in parallel
    injectChartJs();
    return new Promise((resolve) => {
        const start = Date.now();
        const id = setInterval(() => {
            if (typeof Chart !== 'undefined') { clearInterval(id); resolve(true); return; }
            if (Date.now() - start > maxMs)   { clearInterval(id); resolve(false); }
        }, 150);
    });
}

/**
 * Ensure the #reports section has the full chart UI regardless of which admin.html version
 * the browser has cached.  Called before every loadReports().
 */
function ensureReportsUI() {
    const section = document.getElementById('reports');
    if (!section) return;

    // Fix title (old HTML has plain "Reports")
    const h2 = section.querySelector('.panel-header h2');
    if (h2 && !h2.innerHTML.includes('Analytics')) h2.innerHTML = 'Reports &amp; Analytics';

    // Inject months selector if missing
    if (!document.getElementById('revenue-months')) {
        const ph = section.querySelector('.panel-header');
        if (ph) {
            const controls = document.createElement('div');
            controls.style.cssText = 'display:flex;gap:8px;align-items:center;';
            controls.innerHTML = `
              <select id="revenue-months" style="padding:6px 10px;border-radius:6px;border:1px solid #444;background:#1e2535;color:#e2e8f0;font-size:13px;">
                <option value="6">Last 6 Months</option>
                <option value="12" selected>Last 12 Months</option>
                <option value="24">Last 24 Months</option>
              </select>
              <button class="secondary-btn" onclick="loadReports()">Refresh</button>`;
            // Replace whatever is in panel-header after h2
            const existingBtn = ph.querySelector('button');
            if (existingBtn) existingBtn.remove();
            ph.appendChild(controls);
        }
    }

    // Inject charts grid if missing
    if (!document.getElementById('charts-grid')) {
        // Remove old placeholder text if present
        const oldP = section.querySelector('#reports-summary, p');
        if (oldP) oldP.style.display = 'none';

        // Inject required chart card CSS inline (in case styles.css is old)
        if (!document.getElementById('_chartCardStyle')) {
            const style = document.createElement('style');
            style.id = '_chartCardStyle';
            style.textContent = `
              #charts-grid{display:none;grid-template-columns:1fr 1fr;gap:24px;margin-top:16px;}
              @media(max-width:768px){#charts-grid{grid-template-columns:1fr!important;}}
              .chart-card{background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.08);
                border-radius:12px;padding:20px 24px;}
              .chart-title{font-size:13px;font-weight:600;color:#94a3b8;text-transform:uppercase;
                letter-spacing:.5px;margin:0 0 14px 0;}
              .chart-wrap{position:relative;height:260px;width:100%;}
            `;
            document.head.appendChild(style);
        }

        const grid = document.createElement('div');
        grid.id = 'charts-grid';
        grid.innerHTML = `
          <div class="chart-card">
            <p class="chart-title">📊 Monthly Revenue (₹)</p>
            <div class="chart-wrap"><canvas id="revenueChart"></canvas></div>
          </div>
          <div class="chart-card">
            <p class="chart-title">🎯 Booking Status</p>
            <div class="chart-wrap" style="max-height:280px;"><canvas id="statusChart"></canvas></div>
          </div>
          <div class="chart-card">
            <p class="chart-title">📈 Monthly Booking Count</p>
            <div class="chart-wrap"><canvas id="countChart"></canvas></div>
          </div>
          <div class="chart-card">
            <p class="chart-title">🔀 Revenue vs Bookings</p>
            <div class="chart-wrap"><canvas id="comboChart"></canvas></div>
          </div>`;
        section.appendChild(grid);

        const loading = document.createElement('div');
        loading.id = 'charts-loading';
        loading.style.cssText = 'text-align:center;padding:40px;color:#94a3b8;display:none;';
        loading.textContent = 'Loading charts…';
        section.appendChild(loading);

        const errDiv = document.createElement('div');
        errDiv.id = 'charts-error';
        errDiv.style.cssText = 'text-align:center;padding:20px;color:#f87171;display:none;';
        section.appendChild(errDiv);
    }
}

async function loadReports() {
    const token = localStorage.getItem('jwt');
    if (!token) return;

    // Always ensure chart DOM exists — works even with old cached admin.html
    ensureReportsUI();

    const loadingEl = document.getElementById('charts-loading');
    const errorEl   = document.getElementById('charts-error');
    const gridEl    = document.getElementById('charts-grid');

    if (loadingEl) { loadingEl.style.display = 'block'; }
    if (errorEl)   { errorEl.style.display   = 'none';  errorEl.innerHTML = ''; }
    if (gridEl)    { gridEl.style.display     = 'none'; }

    try {
        const months  = (document.getElementById('revenue-months') || {}).value || 12;
        const headers = { 'Authorization': 'Bearer ' + token };

        const [dashRes, revRes, chartReady] = await Promise.all([
            fetch(`${API_BASE}/api/admin/reports/dashboard`, { headers }),
            fetch(`${API_BASE}/api/admin/reports/revenue?months=${months}`, { headers }),
            waitForChart(9000)
        ]);

        if (!dashRes.ok || !revRes.ok)
            throw new Error('Server error (' + (dashRes.ok ? revRes.status : dashRes.status) + ')');

        const dash = await dashRes.json();
        const rev  = await revRes.json();

        // ── Update stat cards ────────────────────────────────────────
        const fmt = (n) => '₹' + Number(n).toLocaleString('en-IN', { maximumFractionDigits: 0 });
        const setEl = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
        setEl('stat-revenue',         dash.revenue          != null ? fmt(dash.revenue) : '-');
        setEl('stat-occupancy',        dash.occupancyPercent != null ? dash.occupancyPercent + '%' : '-');
        setEl('stat-total-rooms',      dash.totalRooms       ?? '-');
        setEl('stat-available-rooms',  dash.availableRooms   ?? '-');
        setEl('stat-total-guests',     dash.totalGuests      ?? '-');
        setEl('stat-total-bookings',   dash.activeBookings   ?? '-');

        if (loadingEl) loadingEl.style.display = 'none';
        if (gridEl)    gridEl.style.display    = 'grid';

        if (!chartReady || typeof Chart === 'undefined') {
            _renderReportTables(dash, rev, gridEl);
            return;
        }

        // ── Chart shared config ──────────────────────────────────────
        const DARK = '#94a3b8';
        const GRID = 'rgba(255,255,255,0.07)';
        const base = {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { labels: { color: DARK, font: { size: 12 }, boxWidth: 14 } }
            }
        };
        const xAxis = { ticks: { color: DARK }, grid: { color: GRID } };
        const yAxis = { ticks: { color: DARK }, grid: { color: GRID } };

        function kill(id) {
            if (_chartInstances[id]) { _chartInstances[id].destroy(); delete _chartInstances[id]; }
        }

        // ── 1. Monthly Revenue — Gradient bar ────────────────────────
        kill('revenueChart');
        const rCtx = document.getElementById('revenueChart');
        if (rCtx) {
            const grd = rCtx.getContext('2d').createLinearGradient(0, 0, 0, 260);
            grd.addColorStop(0,   'rgba(245,158,11,0.90)');
            grd.addColorStop(1,   'rgba(245,158,11,0.20)');
            _chartInstances['revenueChart'] = new Chart(rCtx, {
                type: 'bar',
                data: {
                    labels: rev.labels || [],
                    datasets: [{
                        label: 'Revenue (₹)',
                        data: rev.revenues || [],
                        backgroundColor: grd,
                        borderColor: '#f59e0b',
                        borderWidth: 1.5,
                        borderRadius: 6,
                        borderSkipped: false
                    }]
                },
                options: {
                    ...base,
                    scales: {
                        x: xAxis,
                        y: { ...yAxis, ticks: { ...yAxis.ticks,
                            callback: v => '₹' + Number(v).toLocaleString('en-IN', {maximumFractionDigits:0}) } }
                    },
                    plugins: { ...base.plugins,
                        tooltip: { callbacks: { label: c => ' ₹' + Number(c.parsed.y).toLocaleString('en-IN',{maximumFractionDigits:0}) } }
                    }
                }
            });
        }

        // ── 2. Booking Status — Rich doughnut ────────────────────────
        kill('statusChart');
        const sCtx = document.getElementById('statusChart');
        if (sCtx && dash.statusCounts) {
            const sc = dash.statusCounts;
            _chartInstances['statusChart'] = new Chart(sCtx, {
                type: 'doughnut',
                data: {
                    labels: ['Booked', 'Checked In', 'Completed', 'Cancelled'],
                    datasets: [{
                        data: [sc.BOOKED||0, sc.CHECKED_IN||0, sc.COMPLETED||0, sc.CANCELLED||0],
                        backgroundColor: ['#3b82f6','#10b981','#8b5cf6','#ef4444'],
                        borderColor:     ['#1d4ed8','#059669','#6d28d9','#dc2626'],
                        borderWidth: 2,
                        hoverOffset: 10
                    }]
                },
                options: {
                    ...base, cutout: '62%',
                    plugins: { ...base.plugins,
                        tooltip: { callbacks: { label: c => ` ${c.label}: ${c.parsed}` } }
                    }
                }
            });
        }

        // ── 3. Monthly Bookings — Area line ──────────────────────────
        kill('countChart');
        const cCtx = document.getElementById('countChart');
        if (cCtx) {
            const grd2 = cCtx.getContext('2d').createLinearGradient(0, 0, 0, 260);
            grd2.addColorStop(0,  'rgba(99,102,241,0.45)');
            grd2.addColorStop(1,  'rgba(99,102,241,0.02)');
            _chartInstances['countChart'] = new Chart(cCtx, {
                type: 'line',
                data: {
                    labels: rev.labels || [],
                    datasets: [{
                        label: 'Bookings',
                        data: rev.counts || [],
                        borderColor: '#818cf8',
                        backgroundColor: grd2,
                        fill: true,
                        tension: 0.45,
                        pointRadius: 5,
                        pointBackgroundColor: '#818cf8',
                        pointBorderColor: '#1e1b4b',
                        pointBorderWidth: 2
                    }]
                },
                options: {
                    ...base,
                    scales: { x: xAxis, y: { ...yAxis, ticks: { ...yAxis.ticks, stepSize: 1 } } }
                }
            });
        }

        // ── 4. Revenue vs Bookings — Combo ───────────────────────────
        kill('comboChart');
        const mCtx = document.getElementById('comboChart');
        if (mCtx) {
            _chartInstances['comboChart'] = new Chart(mCtx, {
                data: {
                    labels: rev.labels || [],
                    datasets: [
                        {
                            type: 'bar', label: 'Revenue (₹)',
                            data: rev.revenues || [],
                            backgroundColor: 'rgba(245,158,11,0.70)',
                            borderColor: '#fbbf24', borderWidth: 1, borderRadius: 4,
                            yAxisID: 'yRev'
                        },
                        {
                            type: 'line', label: 'Bookings',
                            data: rev.counts || [],
                            borderColor: '#34d399',
                            backgroundColor: 'rgba(52,211,153,0.12)',
                            fill: true, tension: 0.4,
                            pointRadius: 5,
                            pointBackgroundColor: '#34d399',
                            pointBorderColor: '#064e3b', pointBorderWidth: 2,
                            yAxisID: 'yCount'
                        }
                    ]
                },
                options: {
                    ...base,
                    scales: {
                        x: xAxis,
                        yRev: {
                            type: 'linear', position: 'left',
                            ticks: { color: DARK, callback: v => '₹' + Number(v).toLocaleString('en-IN',{maximumFractionDigits:0}) },
                            grid: { color: GRID }
                        },
                        yCount: {
                            type: 'linear', position: 'right',
                            ticks: { color: DARK, stepSize: 1 },
                            grid: { display: false }
                        }
                    },
                    plugins: { ...base.plugins,
                        tooltip: { callbacks: { label: c =>
                            c.dataset.label === 'Revenue (₹)'
                                ? ' ₹' + Number(c.parsed.y).toLocaleString('en-IN',{maximumFractionDigits:0})
                                : ` ${c.parsed.y} bookings`
                        }}
                    }
                }
            });
        }

    } catch (e) {
        console.error('[loadReports]', e);
        if (loadingEl) loadingEl.style.display = 'none';
        if (gridEl)    gridEl.style.display    = 'none';
        if (errorEl) {
            errorEl.style.display = 'block';
            errorEl.innerHTML = `<p style="margin:0 0 8px 0;">&#9888; ${e.message}</p>
                <button class="secondary-btn" onclick="loadReports()">Retry</button>`;
        }
    }
}

/** Plain-table fallback when Chart.js CDN is completely blocked. */
function _renderReportTables(dash, rev, container) {
    if (!container) return;
    const sc  = dash.statusCounts || {};
    const fmt = v => '₹' + Number(v||0).toLocaleString('en-IN',{maximumFractionDigits:0});
    const rows = (rev.labels||[]).map((lbl,i)=>
        `<tr><td>${lbl}</td><td>${fmt(rev.revenues[i])}</td><td>${rev.counts[i]||0}</td></tr>`
    ).join('') || '<tr><td colspan="3" style="text-align:center;color:#64748b">No data yet</td></tr>';

    container.innerHTML = `
      <div class="chart-card" style="grid-column:1/-1">
        <p class="chart-title">Monthly Revenue &amp; Bookings</p>
        <table class="data-table" style="margin:0"><thead><tr><th>Month</th><th>Revenue</th><th>Bookings</th></tr></thead>
        <tbody>${rows}</tbody></table>
      </div>
      <div class="chart-card">
        <p class="chart-title">Booking Status</p>
        <table class="data-table" style="margin:0"><thead><tr><th>Status</th><th>Count</th></tr></thead>
        <tbody>
          <tr><td>🔵 Booked</td><td>${sc.BOOKED||0}</td></tr>
          <tr><td>🟢 Checked In</td><td>${sc.CHECKED_IN||0}</td></tr>
          <tr><td>🟣 Completed</td><td>${sc.COMPLETED||0}</td></tr>
          <tr><td>🔴 Cancelled</td><td>${sc.CANCELLED||0}</td></tr>
        </tbody></table>
      </div>`;
}

async function loadAuditLogs() {
    const token = localStorage.getItem('jwt');
    const tbody = document.getElementById('audit-log-body');
    if (!token || !tbody) return;
    try {
        const res = await fetch(`${API_BASE}/api/admin/audit-logs?limit=50`, { headers: { 'Authorization': 'Bearer ' + token } });
        if (!res.ok) return;
        const logs = await res.json();
        tbody.innerHTML = (Array.isArray(logs) ? logs : []).map(l => `
            <tr>
                <td>${l.createdAt ? new Date(l.createdAt).toLocaleString() : '-'}</td>
                <td>${l.userEmail || '-'}</td>
                <td>${l.action || '-'}</td>
                <td>${l.entityType || '-'} ${l.entityId != null ? '#' + l.entityId : ''}</td>
                <td>${l.details || '-'}</td>
            </tr>
        `).join('') || '<tr><td colspan="5">No audit entries</td></tr>';
    } catch (e) { console.error(e); }
}

async function loadBookingStates() {
    const sel = document.getElementById('booking-state');
    if (!sel) return;
    try {
        const res = await fetch(`${API_BASE}/api/public/states`);
        if (!res.ok) return;
        const states = await res.json();
        const list = Array.isArray(states) ? states : [];
        sel.innerHTML = '<option value="">Select State</option>' + list.map(s => `<option value="${s}">${s}</option>`).join('');
    } catch (e) {
        console.error(e);
    }
}

async function loadBookingHotels(state) {
    const sel = document.getElementById('booking-hotel-id');
    if (!sel || !state) {
        if (sel) sel.innerHTML = '<option value="">Select Hotel</option>';
        return;
    }
    sel.innerHTML = '<option value="">Loading…</option>';
    try {
        const res = await fetch(`${API_BASE}/api/public/hotels?state=${encodeURIComponent(state)}`);
        if (!res.ok) throw new Error('Failed to load hotels');
        const hotels = await res.json();
        const list = Array.isArray(hotels) ? hotels : [];
        sel.innerHTML = '<option value="">Select Hotel</option>' + list.map(h => `<option value="${h.id}">${h.name}</option>`).join('');
        document.getElementById('booking-room-id').innerHTML = '<option value="">Select Room</option>';
        document.getElementById('booking-total').value = '';
    } catch (e) {
        sel.innerHTML = '<option value="">Select Hotel</option>';
        showToast(e.message || 'Failed to load hotels', true);
    }
}

async function loadBookingRooms(hotelId, roomType) {
    const sel = document.getElementById('booking-room-id');
    if (!sel || !hotelId || !roomType) {
        if (sel) sel.innerHTML = '<option value="">Select Room</option>';
        document.getElementById('booking-total').value = '';
        return;
    }
    sel.innerHTML = '<option value="">Loading…</option>';
    try {
        const res = await fetch(`${API_BASE}/api/public/hotels/${hotelId}/rooms`);
        if (!res.ok) throw new Error('Failed to load rooms');
        const rooms = await res.json();
        const list = (Array.isArray(rooms) ? rooms : []).filter(r => r.type === roomType && r.available !== false);
        sel.innerHTML = '<option value="">Select Room</option>' + list.map(r =>
            `<option value="${r.id}" data-price="${r.price != null ? r.price : 0}">${r.roomNumber || ('Room ' + r.id)} - ${r.type} (₹${(r.price != null ? r.price : 0).toFixed(2)})</option>`
        ).join('');
        document.getElementById('booking-total').value = '';
        updateBookingTotal();
    } catch (e) {
        sel.innerHTML = '<option value="">Select Room</option>';
        showToast(e.message || 'Failed to load rooms', true);
    }
}

function updateBookingTotal() {
    const roomSel = document.getElementById('booking-room-id');
    const checkIn = document.getElementById('booking-checkin').value;
    const checkOut = document.getElementById('booking-checkout').value;
    const totalEl = document.getElementById('booking-total');
    if (!totalEl) return;
    const opt = roomSel && roomSel.options[roomSel.selectedIndex];
    const price = opt && opt.getAttribute('data-price') ? parseFloat(opt.getAttribute('data-price')) : NaN;
    if (!checkIn || !checkOut || isNaN(price)) {
        totalEl.value = '';
        return;
    }
    const from = new Date(checkIn);
    const to = new Date(checkOut);
    const nights = Math.max(0, Math.ceil((to - from) / (24 * 60 * 60 * 1000)));
    const total = price * nights;
    totalEl.value = '₹' + total.toFixed(2);
}

function showBillModal() {
    const guestSel = document.getElementById('booking-guest-id');
    const roomSel = document.getElementById('booking-room-id');
    const checkIn = document.getElementById('booking-checkin').value;
    const checkOut = document.getElementById('booking-checkout').value;
    const totalEl = document.getElementById('booking-total').value;
    const guestName = guestSel && guestSel.options[guestSel.selectedIndex] ? guestSel.options[guestSel.selectedIndex].textContent : '-';
    const roomText = roomSel && roomSel.options[roomSel.selectedIndex] ? roomSel.options[roomSel.selectedIndex].textContent : '-';
    const content = document.getElementById('bill-modal-content');
    if (!content) return;
    content.innerHTML = `
        <p><strong>Guest:</strong> ${guestName}</p>
        <p><strong>Room:</strong> ${roomText}</p>
        <p><strong>Check-in:</strong> ${checkIn || '-'}</p>
        <p><strong>Check-out:</strong> ${checkOut || '-'}</p>
        <p><strong>Total Amount:</strong> ${totalEl || '-'}</p>
    `;
    document.getElementById('bill-modal-backdrop').style.display = 'flex';
}

function closeBillModal() {
    const el = document.getElementById('bill-modal-backdrop');
    if (el) el.style.display = 'none';
}

function showBillModalForBooking(b) {
    const guestName = b.guest ? b.guest.name : '-';
    const roomText = b.room ? `${b.room.roomNumber || ''} (${b.room.type || '-'})` : '-';
    const hotelName = b.room && b.room.hotel ? b.room.hotel.name : '-';
    const total = b.totalCost != null ? '₹' + Number(b.totalCost).toFixed(2) : '-';
    const content = document.getElementById('bill-modal-content');
    if (!content) return;
    content.innerHTML = `
        <p><strong>Booking ID:</strong> ${b.id}</p>
        <p><strong>Guest:</strong> ${guestName}</p>
        <p><strong>Hotel:</strong> ${hotelName}</p>
        <p><strong>Room:</strong> ${roomText}</p>
        <p><strong>Check-in:</strong> ${b.checkInDate || '-'}</p>
        <p><strong>Check-out:</strong> ${b.checkOutDate || '-'}</p>
        <p><strong>Status:</strong> ${b.status || '-'}</p>
        <p><strong>Total Amount:</strong> ${total}</p>
    `;
    document.getElementById('bill-modal-backdrop').style.display = 'flex';
}

async function createRoom() {
    const roomNumber = document.getElementById('room-number').value.trim();
    const typeEl = document.getElementById('room-type');
    const type = typeEl && typeEl.tagName === 'SELECT' ? typeEl.value : typeEl.value.trim();
    const price = parseFloat(document.getElementById('room-price').value);
    const available = document.getElementById('room-available').checked;

    if (!roomNumber || !type || isNaN(price)) {
        showToast('Please fill all room fields', true);
        return;
    }

    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            showToast('Please log in first (JWT required for admin).', true);
            return;
        }
        const res = await fetch(`${API_BASE}/api/rooms`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ roomNumber, type, price, available })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to create room');
        }

        const room = await res.json();
        showToast(`Room created (ID: ${room.id})`);
        document.getElementById('room-number').value = '';
        if (typeEl && typeEl.tagName === 'SELECT') typeEl.value = 'DELUXE';
        document.getElementById('room-price').value = '';
        document.getElementById('room-available').checked = true;

        loadRooms(room.id);
        loadAvailableRoomsCount();
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    }
}

async function createGuest() {
    const name = document.getElementById('guest-name').value.trim();
    const email = document.getElementById('guest-email').value.trim();
    const phone = document.getElementById('guest-phone').value.trim();

    if (!name || !email || !phone) {
        showToast('Please fill all guest fields', true);
        return;
    }

    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            showToast('Please log in first (JWT required for admin).', true);
            return;
        }
        const res = await fetch(`${API_BASE}/api/guests`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ name, email, phone })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to create guest');
        }

        const guest = await res.json();
        showToast(`Guest created (ID: ${guest.id})`);

        document.getElementById('guest-name').value = '';
        document.getElementById('guest-email').value = '';
        document.getElementById('guest-phone').value = '';

        loadGuests(guest.id);
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    }
}

async function deleteGuest(guestId) {
    if (!confirm('Remove this guest? This cannot be undone.')) return;
    try {
        const token = localStorage.getItem('jwt');
        if (!token) { showToast('Please log in first.', true); return; }
        const res = await fetch(`${API_BASE}/api/guests/${guestId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to remove guest');
        }
        showToast('Guest removed');
        loadGuests();
    } catch (e) {
        showToast(e.message, true);
    }
}

async function loadStaffMembers() {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;
        const res = await fetch(`${API_BASE}/api/admin/staff`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            throw new Error('Failed to load staff members');
        }
        const users = await res.json();
        const tbody = document.getElementById('staff-table-body');
        if (!tbody) return;
        tbody.innerHTML = '';

        if (!Array.isArray(users) || users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6">No staff or manager accounts found.</td></tr>';
            return;
        }

        users.forEach(user => {
            const selectedStaff = user.role === 'STAFF' ? 'selected' : '';
            const selectedManager = user.role === 'MANAGER' ? 'selected' : '';
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${user.id}</td>
                <td>${user.fullName || '-'}</td>
                <td>${user.email || '-'}</td>
                <td>${user.phone || '-'}</td>
                <td>
                    <select class="form-input form-select inline-role-select" data-user-id="${user.id}">
                        <option value="STAFF" ${selectedStaff}>STAFF</option>
                        <option value="MANAGER" ${selectedManager}>MANAGER</option>
                    </select>
                </td>
                <td>
                    <button type="button" class="secondary-btn update-staff-role-btn" data-user-id="${user.id}">
                        Update Role
                    </button>
                    <button type="button" class="secondary-btn remove-staff-btn" data-user-id="${user.id}">
                        Remove
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        });

        tbody.querySelectorAll('.update-staff-role-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const userId = btn.getAttribute('data-user-id');
                const roleSelect = tbody.querySelector(`.inline-role-select[data-user-id="${userId}"]`);
                const role = roleSelect ? roleSelect.value : '';
                updateStaffMemberRole(userId, role);
            });
        });
        tbody.querySelectorAll('.remove-staff-btn').forEach(btn => {
            btn.addEventListener('click', () => removeStaffMember(btn.getAttribute('data-user-id')));
        });
    } catch (e) {
        console.error(e);
        showToast(e.message || 'Failed to load staff members', true);
    }
}

async function createStaffMember() {
    const fullName = document.getElementById('staff-full-name').value.trim();
    const email = document.getElementById('staff-email').value.trim();
    const phone = document.getElementById('staff-phone').value.trim();
    const password = document.getElementById('staff-password').value;
    const role = document.getElementById('staff-role').value;

    if (!fullName || !email || !password || !role) {
        showToast('Please fill all required staff fields', true);
        return;
    }

    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            showToast('Please log in first.', true);
            return;
        }
        const res = await fetch(`${API_BASE}/api/admin/staff`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ fullName, email, phone, password, role })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to create staff account');
        }

        showToast(`${role} account created`);
        document.getElementById('staff-full-name').value = '';
        document.getElementById('staff-email').value = '';
        document.getElementById('staff-phone').value = '';
        document.getElementById('staff-password').value = '';
        document.getElementById('staff-role').value = 'STAFF';
        loadStaffMembers();
    } catch (e) {
        console.error(e);
        showToast(e.message || 'Failed to create staff account', true);
    }
}

async function removeStaffMember(userId) {
    if (!userId) return;
    if (!window.confirm('Remove this account?')) {
        return;
    }

    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            showToast('Please log in first.', true);
            return;
        }
        const res = await fetch(`${API_BASE}/api/admin/staff/${userId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to remove account');
        }
        showToast('Account removed');
        loadStaffMembers();
    } catch (e) {
        console.error(e);
        showToast(e.message || 'Failed to remove account', true);
    }
}

async function updateStaffMemberRole(userId, role) {
    if (!userId || !role) return;

    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            showToast('Please log in first.', true);
            return;
        }
        const res = await fetch(`${API_BASE}/api/admin/staff/${userId}/role`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ role })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to update role');
        }
        showToast(`Role updated to ${role}`);
        loadStaffMembers();
    } catch (e) {
        console.error(e);
        showToast(e.message || 'Failed to update role', true);
    }
}

async function createBooking() {
    const roomIdEl = document.getElementById('booking-room-id');
    const guestIdEl = document.getElementById('booking-guest-id');
    const roomId = roomIdEl ? parseInt(roomIdEl.value, 10) : 0;
    const guestId = guestIdEl ? parseInt(guestIdEl.value, 10) : 0;
    const checkInDate = document.getElementById('booking-checkin').value;
    const checkOutDate = document.getElementById('booking-checkout').value;

    if (!roomId || !guestId || !checkInDate || !checkOutDate) {
        showToast('Please select room, guest and dates', true);
        return;
    }

    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            showToast('Please log in first (JWT required for admin).', true);
            return;
        }
        const res = await fetch(`${API_BASE}/api/bookings`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                checkInDate,
                checkOutDate,
                room: { id: roomId },
                guest: { id: guestId }
            })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to create booking');
        }

        showToast('Booking created');
        const roomSel = document.getElementById('booking-room-id');
        const guestSel = document.getElementById('booking-guest-id');
        if (roomSel) roomSel.value = '';
        if (guestSel) guestSel.value = '';
        document.getElementById('booking-checkin').value = '';
        document.getElementById('booking-checkout').value = '';

        loadDashboard();
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    }
}

async function loadDestinations(flashId) {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;
        const res = await fetch(`${API_BASE}/api/admin/destinations`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) return;
        const list = await res.json();

        // Update count badge
        const badge = document.getElementById('destinations-count-badge');
        if (badge) badge.textContent = list.length;

        // Render inline destinations list
        const tbody = document.getElementById('destinations-table-body');
        if (tbody) {
            tbody.innerHTML = '';
            if (list.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#64748b;padding:16px;">No destinations yet</td></tr>';
            } else {
                list.forEach(d => {
                    const tr = document.createElement('tr');
                    if (flashId && d.id === flashId) tr.classList.add('row-new');
                    tr.innerHTML = `
                        <td>${d.id}</td>
                        <td>${d.name || '-'}</td>
                        <td>${d.city || '-'}</td>
                        <td>${d.country || '-'}</td>
                        <td><button class="danger-btn-sm" onclick="deleteDestination(${d.id})">Remove</button></td>`;
                    tbody.appendChild(tr);
                });
                if (flashId) {
                    const newRow = tbody.querySelector('.row-new');
                    if (newRow) newRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }
            }
        }

        // Also keep the hotel-destination dropdown in sync
        const destSelect = document.getElementById('hotel-destination-id');
        if (destSelect) {
            const cur = destSelect.value;
            destSelect.innerHTML = '<option value="">No destination</option>';
            list.forEach(d => {
                const opt = document.createElement('option');
                opt.value = d.id;
                opt.textContent = `${d.name} (${d.city || d.country || ''})`;
                destSelect.appendChild(opt);
            });
            if (cur) destSelect.value = cur;
        }
    } catch (e) {
        console.error(e);
    }
}

async function createDestination() {
    const name = document.getElementById('dest-name').value.trim();
    const city = document.getElementById('dest-city').value.trim();
    const country = document.getElementById('dest-country').value.trim();
    const description = document.getElementById('dest-description').value.trim();
    if (!name || !city || !country) {
        showToast('Name, city and country are required', true);
        return;
    }
    try {
        const token = localStorage.getItem('jwt');
        if (!token) { showToast('Please log in first.', true); return; }
        const res = await fetch(`${API_BASE}/api/admin/destinations`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ name, city, country, description })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to create destination');
        }
        const dest = await res.json();
        showToast(`Destination created (ID: ${dest.id})`);
        document.getElementById('dest-name').value = '';
        document.getElementById('dest-city').value = '';
        document.getElementById('dest-country').value = '';
        document.getElementById('dest-description').value = '';
        loadDestinations(dest.id);
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    }
}

async function deleteDestination(destId) {
    if (!confirm('Remove this destination? Hotels linked to it will be unlinked.')) return;
    try {
        const token = localStorage.getItem('jwt');
        if (!token) { showToast('Please log in first.', true); return; }
        const res = await fetch(`${API_BASE}/api/admin/destinations/${destId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to remove destination');
        }
        showToast('Destination removed');
        loadDestinations();
    } catch (e) {
        showToast(e.message, true);
    }
}

async function loadHotels(flashId) {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;
        const res = await fetch(`${API_BASE}/api/admin/hotels`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) return;
        const list = await res.json();

        // Update count badge
        const badge = document.getElementById('hotels-count-badge');
        if (badge) badge.textContent = list.length;

        const tbody = document.getElementById('hotels-table-body');
        if (!tbody) return;
        tbody.innerHTML = '';

        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#64748b;padding:16px;">No hotels yet</td></tr>';
            return;
        }

        list.forEach(h => {
            const tr = document.createElement('tr');
            if (flashId && h.id === flashId) tr.classList.add('row-new');
            tr.innerHTML = `
                <td>${h.id}</td>
                <td>${h.name || '-'}</td>
                <td>${h.city || '-'}</td>
                <td>${h.country || '-'}</td>
                <td><button class="danger-btn-sm" onclick="deleteHotel(${h.id})">Remove</button></td>`;
            tbody.appendChild(tr);
        });

        // Scroll the new row into view
        if (flashId) {
            const newRow = tbody.querySelector('.row-new');
            if (newRow) newRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    } catch (e) {
        console.error(e);
    }
}

async function createHotel() {
    const name = document.getElementById('hotel-name').value.trim();
    const address = document.getElementById('hotel-address').value.trim();
    const city = document.getElementById('hotel-city').value.trim();
    const country = document.getElementById('hotel-country').value.trim();
    const destIdEl = document.getElementById('hotel-destination-id');
    const destinationId = destIdEl && destIdEl.value ? parseInt(destIdEl.value, 10) : null;
    const basePriceEl = document.getElementById('hotel-base-price');
    const basePricePerNight = basePriceEl && basePriceEl.value ? parseFloat(basePriceEl.value) : null;
    if (!name || !city || !country) {
        showToast('Name, city and country are required', true);
        return;
    }
    try {
        const token = localStorage.getItem('jwt');
        if (!token) { showToast('Please log in first.', true); return; }
        const res = await fetch(`${API_BASE}/api/admin/hotels`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ name, address, city, country, destinationId, basePricePerNight })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to create hotel');
        }
        const hotel = await res.json();
        showToast(`Hotel created (ID: ${hotel.id})`);
        document.getElementById('hotel-name').value = '';
        document.getElementById('hotel-address').value = '';
        document.getElementById('hotel-city').value = '';
        document.getElementById('hotel-country').value = '';
        if (destIdEl) destIdEl.value = '';
        if (basePriceEl) basePriceEl.value = '';
        loadHotels(hotel.id);
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    }
}

async function deleteHotel(hotelId) {
    if (!confirm('Remove this hotel? All its rooms and links will also be removed.')) return;
    try {
        const token = localStorage.getItem('jwt');
        if (!token) { showToast('Please log in first.', true); return; }
        const res = await fetch(`${API_BASE}/api/admin/hotels/${hotelId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to remove hotel');
        }
        showToast('Hotel removed');
        loadHotels();
    } catch (e) {
        showToast(e.message, true);
    }
}

async function updateBookingStatus(bookingId, status) {
    if (!bookingId) return;
    try {
        const token = localStorage.getItem('jwt');
        if (!token) { showToast('Please log in first.', true); return; }
        const res = await fetch(`${API_BASE}/api/bookings/${bookingId}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ status })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Failed to update booking');
        }
        showToast('Booking ' + status.toLowerCase());
        loadDashboard();
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    }
}

async function checkAdminAccess() {
    const gate = document.getElementById('admin-gate');
    const main = document.getElementById('admin-main');
    const navLinks = document.querySelectorAll('.admin-nav-link');
    const logoutBtn = document.getElementById('admin-logout-btn');
    const token = localStorage.getItem('jwt');

    if (!token) {
        if (gate) gate.style.display = 'block';
        if (main) main.style.display = 'none';
        navLinks.forEach(el => { el.style.display = 'none'; });
        if (logoutBtn) logoutBtn.style.display = 'none';
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/api/auth/me`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) {
            localStorage.removeItem('jwt');
            if (gate) gate.style.display = 'block';
            if (main) main.style.display = 'none';
            navLinks.forEach(el => { el.style.display = 'none'; });
            if (logoutBtn) logoutBtn.style.display = 'none';
            return;
        }
        const me = await res.json();
        const roles = me.roles || [];
        if (!roles.includes('ROLE_ADMIN')) {
            if (gate) gate.style.display = 'block';
            if (main) main.style.display = 'none';
            navLinks.forEach(el => { el.style.display = 'none'; });
            if (logoutBtn) logoutBtn.style.display = 'none';
            return;
        }
        if (gate) gate.style.display = 'none';
        if (main) main.style.display = 'block';
        navLinks.forEach(el => { el.style.display = el.tagName === 'A' ? 'inline' : 'inline-block'; });
        if (logoutBtn) {
            logoutBtn.style.display = 'inline-block';
            logoutBtn.onclick = () => {
                localStorage.removeItem('jwt');
                window.location.href = '/index.html';
            };
        }
        loadDashboard();
    } catch (e) {
        console.error(e);
        if (gate) gate.style.display = 'block';
        if (main) main.style.display = 'none';
    }
}

function bindCreateBookingForm() {
    const stateEl = document.getElementById('booking-state');
    const hotelEl = document.getElementById('booking-hotel-id');
    const roomTypeEl = document.getElementById('booking-room-type');
    const roomEl = document.getElementById('booking-room-id');
    const checkInEl = document.getElementById('booking-checkin');
    const checkOutEl = document.getElementById('booking-checkout');
    if (stateEl) stateEl.addEventListener('change', () => loadBookingHotels(stateEl.value.trim()));
    if (hotelEl) hotelEl.addEventListener('change', () => {
        const type = (document.getElementById('booking-room-type') || {}).value;
        if (hotelEl.value && type) loadBookingRooms(parseInt(hotelEl.value, 10), type);
    });
    if (roomTypeEl) roomTypeEl.addEventListener('change', () => {
        const hid = (document.getElementById('booking-hotel-id') || {}).value;
        if (hid && roomTypeEl.value) loadBookingRooms(parseInt(hid, 10), roomTypeEl.value);
    });
    if (roomEl) roomEl.addEventListener('change', updateBookingTotal);
    if (checkInEl) checkInEl.addEventListener('change', updateBookingTotal);
    if (checkOutEl) checkOutEl.addEventListener('change', updateBookingTotal);
}

window.addEventListener('load', () => {
    bindCreateBookingForm();
    checkAdminAccess();

    // Reload charts when months selector changes
    const monthsSel = document.getElementById('revenue-months');
    if (monthsSel) monthsSel.addEventListener('change', loadReports);
});

