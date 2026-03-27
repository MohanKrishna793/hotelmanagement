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

async function loadRooms() {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) {
            showToast('Please log in first (JWT required for admin).', true);
            return;
        }
        const res = await fetch(`${API_BASE}/api/rooms`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const rooms = await res.json();
        const tbody = document.getElementById('rooms-table-body');
        if (tbody) tbody.innerHTML = '';

        rooms.forEach(r => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${r.id}</td>
                <td>${r.roomNumber}</td>
                <td>${r.type}</td>
                <td>₹${r.price.toFixed(2)}</td>
                <td>${r.available ? 'Yes' : 'No'}</td>
            `;
            tbody.appendChild(tr);
        });

        const statRooms = document.getElementById('stat-total-rooms');
        if (statRooms) statRooms.textContent = rooms.length;
    } catch (e) {
        console.error(e);
        showToast('Failed to load rooms', true);
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

async function loadGuests() {
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
        const stat = document.getElementById('stat-total-guests');
        if (stat) stat.textContent = guests.length;
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

async function loadReports() {
    const token = localStorage.getItem('jwt');
    if (!token) return;
    try {
        const res = await fetch(`${API_BASE}/api/admin/reports/dashboard`, { headers: { 'Authorization': 'Bearer ' + token } });
        if (!res.ok) return;
        const data = await res.json();
        const revEl = document.getElementById('stat-revenue');
        const occEl = document.getElementById('stat-occupancy');
        if (revEl) revEl.textContent = (data.revenue != null ? Number(data.revenue).toFixed(0) : '-');
        if (occEl) occEl.textContent = (data.occupancyPercent != null ? data.occupancyPercent + '%' : '-');
    } catch (e) { console.error(e); }
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

        showToast('Room created');
        document.getElementById('room-number').value = '';
        if (typeEl && typeEl.tagName === 'SELECT') typeEl.value = 'DELUXE';
        document.getElementById('room-price').value = '';
        document.getElementById('room-available').checked = true;

        loadDashboard();
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

        loadGuestsCount();
    } catch (e) {
        console.error(e);
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

async function loadDestinations() {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;
        const res = await fetch(`${API_BASE}/api/admin/destinations`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) return;
        const list = await res.json();
        const tbody = document.getElementById('destinations-table-body');
        if (!tbody) return;
        tbody.innerHTML = '';
        list.forEach(d => {
            const tr = document.createElement('tr');
            tr.innerHTML = `<td>${d.id}</td><td>${d.name}</td><td>${d.city}</td><td>${d.country}</td>`;
            tbody.appendChild(tr);
        });
        const destSelect = document.getElementById('hotel-destination-id');
        if (destSelect) {
            const cur = destSelect.value;
            destSelect.innerHTML = '<option value="">No destination</option>';
            list.forEach(d => {
                const opt = document.createElement('option');
                opt.value = d.id;
                opt.textContent = `${d.name} (${d.city})`;
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
        showToast('Destination created');
        document.getElementById('dest-name').value = '';
        document.getElementById('dest-city').value = '';
        document.getElementById('dest-country').value = '';
        document.getElementById('dest-description').value = '';
        loadDestinations();
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    }
}

async function loadHotels() {
    try {
        const token = localStorage.getItem('jwt');
        if (!token) return;
        const res = await fetch(`${API_BASE}/api/admin/hotels`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) return;
        const list = await res.json();
        const tbody = document.getElementById('hotels-table-body');
        if (!tbody) return;
        tbody.innerHTML = '';
        list.forEach(h => {
            const tr = document.createElement('tr');
            tr.innerHTML = `<td>${h.id}</td><td>${h.name}</td><td>${h.city}</td><td>${h.country}</td>`;
            tbody.appendChild(tr);
        });
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
        showToast('Hotel created');
        document.getElementById('hotel-name').value = '';
        document.getElementById('hotel-address').value = '';
        document.getElementById('hotel-city').value = '';
        document.getElementById('hotel-country').value = '';
        if (destIdEl) destIdEl.value = '';
        if (basePriceEl) basePriceEl.value = '';
        loadHotels();
    } catch (e) {
        console.error(e);
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
});

