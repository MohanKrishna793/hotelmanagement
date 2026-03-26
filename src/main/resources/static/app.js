const API_BASE = '';
window.STRIPE_ENABLED = false;
/** Set after a successful /api/customer/stripe-key so "Book" does not wait on a second round-trip. */
window.STRIPE_CONFIG_READY = false;
let stripeKeyFetchInFlight = null;

function normalizeJwt(token) {
    if (!token || typeof token !== 'string') return '';
    let normalized = token.trim();
    if (normalized.toLowerCase().startsWith('bearer ')) {
        normalized = normalized.slice(7).trim();
    }
    if (
        (normalized.startsWith('"') && normalized.endsWith('"')) ||
        (normalized.startsWith("'") && normalized.endsWith("'"))
    ) {
        normalized = normalized.slice(1, -1).trim();
    }
    return normalized;
}

function getStoredJwt() {
    const token = normalizeJwt(localStorage.getItem('jwt'));
    if (!token || token.split('.').length !== 3) return null;
    return token;
}

// Load Stripe config when user is logged in (test mode = no real money; no PAN/KYC)
async function loadStripeKey() {
    const token = getStoredJwt();
    if (!token) {
        window.STRIPE_CONFIG_READY = false;
        return;
    }
    if (stripeKeyFetchInFlight) return stripeKeyFetchInFlight;
    stripeKeyFetchInFlight = (async () => {
        try {
            const res = await fetch(`${API_BASE}/api/customer/stripe-key`, { headers: { 'Authorization': 'Bearer ' + token } });
            if (res.ok) {
                const data = await res.json();
                window.STRIPE_ENABLED = data.enabled === 'true' || data.enabled === true;
                window.STRIPE_CONFIG_READY = true;
            }
        } catch (e) { /* ignore */ }
        finally {
            stripeKeyFetchInFlight = null;
        }
    })();
    return stripeKeyFetchInFlight;
}

// WhatsApp floating button: pre-fill greeting; user must send "join <sandbox>" first to connect, then send the greeting to get menu.
const WHATSAPP_NUMBER = '14155238886';
const WHATSAPP_SANDBOX_JOIN = 'join watch-swept';
const WHATSAPP_GREETING = 'Hello 👋, I need help with hotel booking on Smart Hotel Management.';
let deferredInstallPrompt = null;

function initWhatsAppFloat() {
    const el = document.getElementById('whatsapp-float');
    if (!el || el.tagName !== 'A') return;
    // Pre-fill the greeting so user sends it (after joining sandbox) to get the chatbot menu.
    const text = encodeURIComponent(WHATSAPP_GREETING);
    el.href = `https://wa.me/${WHATSAPP_NUMBER}?text=${text}`;
    const tooltip = el.querySelector('.whatsapp-float-tooltip');
    if (tooltip) tooltip.textContent = 'First send: ' + WHATSAPP_SANDBOX_JOIN + ' to connect. Then send the pre-filled message to chat.';
}

function isIosDevice() {
    return /iphone|ipad|ipod/i.test(navigator.userAgent);
}

function isStandaloneMode() {
    return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
}

function showInstallButtonForIos() {
    const btn = document.getElementById('install-app-btn');
    if (!btn || isStandaloneMode()) return;
    btn.style.display = 'inline-flex';
    btn.onclick = () => {
        showToast('On iPhone: tap Share icon, then "Add to Home Screen".');
    };
}

function initInstallPrompt() {
    const btn = document.getElementById('install-app-btn');
    if (!btn) return;

    if (isIosDevice()) {
        showInstallButtonForIos();
    }

    window.addEventListener('beforeinstallprompt', (event) => {
        event.preventDefault();
        deferredInstallPrompt = event;
        btn.style.display = 'inline-flex';
    });

    btn.addEventListener('click', async () => {
        if (deferredInstallPrompt) {
            deferredInstallPrompt.prompt();
            const choice = await deferredInstallPrompt.userChoice;
            if (choice && choice.outcome === 'accepted') {
                showToast('App install started.');
            }
            deferredInstallPrompt = null;
            btn.style.display = 'none';
            return;
        }
        if (isIosDevice() && !isStandaloneMode()) {
            showToast('On iPhone: tap Share icon, then "Add to Home Screen".');
        }
    });

    window.addEventListener('appinstalled', () => {
        btn.style.display = 'none';
        showToast('SmartHotel installed successfully.');
    });
}

function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(() => {
            // Ignore service worker registration errors.
        });
    }
}

function showToast(message, isError = false) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = message;
    toast.className = 'toast show' + (isError ? ' error' : '');
    setTimeout(() => {
        toast.className = 'toast hidden';
    }, 3000);
}

function showPaymentProcessingOverlay(message = 'Processing payment... Please wait.') {
    let overlay = document.getElementById('payment-processing-overlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'payment-processing-overlay';
        overlay.className = 'payment-processing-overlay';
        overlay.innerHTML = `
            <div class="payment-processing-card">
                <span class="spinner" aria-hidden="true"></span>
                <p id="payment-processing-text"></p>
            </div>
        `;
        document.body.appendChild(overlay);
    }
    const text = document.getElementById('payment-processing-text');
    if (text) text.textContent = message;
    overlay.style.display = 'flex';
}

function hidePaymentProcessingOverlay() {
    const overlay = document.getElementById('payment-processing-overlay');
    if (overlay) overlay.style.display = 'none';
}

function showActionLoader(message) {
    showPaymentProcessingOverlay(message || 'Please wait...');
}

function hideActionLoader() {
    hidePaymentProcessingOverlay();
}

function buildIdempotencyKey(roomId, checkInDate, checkOutDate) {
    const token = getStoredJwt() || 'anon';
    return `bk-${roomId}-${checkInDate}-${checkOutDate}-${token.substring(0, 10)}`;
}

function parseJwtPayload(token) {
    try {
        const parts = (token || '').split('.');
        if (parts.length < 2) return null;
        const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
        const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
        return JSON.parse(atob(padded));
    } catch (e) {
        return null;
    }
}

function isJwtExpired(token) {
    const payload = parseJwtPayload(token);
    if (!payload || typeof payload.exp !== 'number') return false;
    return Date.now() >= payload.exp * 1000;
}

function handleAuthExpired(message) {
    localStorage.removeItem('jwt');
    updateHeaderForAuth();
    showToast(message || 'Session expired. Please log in again.', true);
    scrollToTopAndOpenLogin();
}

function choosePaymentMethod(stripeEnabled) {
    return new Promise((resolve) => {
        const overlay = document.createElement('div');
        overlay.className = 'payment-choice-overlay';
        const stripeDisabledNote = stripeEnabled
            ? ''
            : '<p class="payment-choice-note">Stripe is currently unavailable on this server. You can still continue with Pay at hotel.</p>';
        overlay.innerHTML = `
            <div class="payment-choice-card">
                <h3>Choose payment method</h3>
                <p>Complete payment securely with Stripe now, or pay at the hotel.</p>
                ${stripeDisabledNote}
                <div class="payment-choice-actions">
                    <button type="button" class="primary-btn" id="pay-now-btn" ${stripeEnabled ? '' : 'disabled title="Stripe is not configured"'}>Pay now (Stripe)</button>
                    <button type="button" class="secondary-btn" id="pay-later-btn">Pay at hotel</button>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);
        const cleanup = (method) => {
            overlay.remove();
            resolve(method);
        };
        overlay.querySelector('#pay-now-btn').addEventListener('click', () => {
            if (!stripeEnabled) {
                showToast('Stripe is not configured yet. Ask admin to set Stripe keys.', true);
                return;
            }
            cleanup('STRIPE');
        });
        overlay.querySelector('#pay-later-btn').addEventListener('click', () => cleanup('PAY_AT_HOTEL'));
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) cleanup('PAY_AT_HOTEL');
        });
    });
}

function scrollToSection(id) {
    const el = document.getElementById(id);
    if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

function openLoginModal() {
    const backdrop = document.getElementById('login-modal-backdrop');
    if (backdrop) {
        backdrop.style.display = 'flex';
        backdrop.setAttribute('aria-hidden', 'false');
    }
}

function closeLoginModal() {
    const backdrop = document.getElementById('login-modal-backdrop');
    if (backdrop) {
        backdrop.style.display = 'none';
        backdrop.setAttribute('aria-hidden', 'true');
    }
}

function scrollToTopAndOpenLogin() {
    window.scrollTo({ top: 0, behavior: 'smooth' });
    setTimeout(openLoginModal, 300);
}

/** Show/hide My Bookings link and Login/Register. My Bookings page is separate; link visible when logged in. */
function updateHeaderForAuth() {
    const token = getStoredJwt();
    const navMyBookings = document.getElementById('nav-my-bookings');
    const loginBtn = document.getElementById('header-login-btn');
    const registerBtn = document.getElementById('header-register-btn');
    if (navMyBookings) {
        navMyBookings.style.display = token ? 'inline' : 'none';
    }
    if (loginBtn) {
        loginBtn.textContent = token ? 'Logout' : 'Login';
    }
    if (registerBtn) {
        registerBtn.style.display = token ? 'none' : 'inline';
    }
}

async function loadStates() {
    const select = document.getElementById('search-state');
    if (!select) return;
    try {
        const res = await fetch(`${API_BASE}/api/public/states`);
        if (!res.ok) return;
        const states = await res.json();
        select.innerHTML = '<option value="">Select State</option>' +
            (Array.isArray(states) ? states : []).map(s => `<option value="${s}">${s}</option>`).join('');
    } catch (e) {
        console.error(e);
        select.innerHTML = '<option value="">Select State (load failed)</option>';
    }
}

async function onStateChangeForCity() {
    const stateEl = document.getElementById('search-state');
    const cityEl = document.getElementById('search-city');
    if (!cityEl || !stateEl) return;
    const state = stateEl.value ? stateEl.value.trim() : '';
    cityEl.innerHTML = '<option value="">Any city</option>';
    if (!state) return;
    try {
        const res = await fetch(`${API_BASE}/api/public/cities?state=${encodeURIComponent(state)}`);
        if (!res.ok) return;
        const cities = await res.json();
        const list = Array.isArray(cities) ? cities : [];
        list.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c;
            opt.textContent = c;
            cityEl.appendChild(opt);
        });
    } catch (e) {
        console.error(e);
    }
}

async function loadMainPageHotels() {
    const container = document.getElementById('hotel-cards');
    if (!container) return;
    const stateEl = document.getElementById('search-state');
    const cityEl = document.getElementById('search-city');
    const state = stateEl && stateEl.value ? stateEl.value.trim() : null;
    if (!state) {
        container.innerHTML = '<p class="empty-state">Select a state above and click Search to see hotels.</p>';
        return;
    }
    container.innerHTML = '<p class="loading-state"><span class="spinner"></span> Loading hotels…</p>';
    try {
        const params = new URLSearchParams({ state });
        const city = cityEl && cityEl.value ? cityEl.value.trim() : '';
        if (city) params.set('city', city);
        const checkInEl = document.getElementById('search-checkin');
        const checkOutEl = document.getElementById('search-checkout');
        if (checkInEl && checkInEl.value) params.set('checkIn', checkInEl.value);
        if (checkOutEl && checkOutEl.value) params.set('checkOut', checkOutEl.value);
        const classEl = document.getElementById('search-class');
        const hotelTypeEl = document.getElementById('search-hotel-type');
        const minPriceEl = document.getElementById('search-min-price');
        const maxPriceEl = document.getElementById('search-max-price');
        if (classEl && classEl.value) params.set('hotelClass', classEl.value);
        if (hotelTypeEl && hotelTypeEl.value) params.set('hotelType', hotelTypeEl.value);
        if (minPriceEl && minPriceEl.value && !isNaN(Number(minPriceEl.value))) params.set('minPrice', minPriceEl.value);
        if (maxPriceEl && maxPriceEl.value && !isNaN(Number(maxPriceEl.value))) params.set('maxPrice', maxPriceEl.value);
        const res = await fetch(`${API_BASE}/api/public/hotels?` + params.toString());
        if (!res.ok) throw new Error('Server returned ' + res.status);
        const data = await res.json();
        const hotels = Array.isArray(data) ? data : [];
        renderHotelCards(hotels);
    } catch (e) {
        console.error(e);
        container.innerHTML = '<p class="empty-state">Unable to load hotels. Select a state and try again.</p>';
    }
}

const DEFAULT_HOTEL_IMAGE = 'https://images.pexels.com/photos/258154/pexels-photo-258154.jpeg?auto=compress&cs=tinysrgb&w=800';

function renderHotelCards(hotels) {
    const container = document.getElementById('hotel-cards');
    if (!container) return;
    container.innerHTML = '';
    const list = Array.isArray(hotels) ? hotels : [];
    if (!list.length) {
        container.innerHTML = '<p class="empty-state">No hotels found for selected criteria. Try a different state or city.</p>';
        return;
    }
    list.forEach(h => {
        const card = document.createElement('div');
        card.className = 'hotel-card panel';
        card.dataset.hotelId = h.id;
        const imgUrl = h.imageUrl || DEFAULT_HOTEL_IMAGE;
        const amenitiesList = (h.amenities || '').split(',').map(a => a.trim()).filter(Boolean);
        const amenitiesHtml = amenitiesList.length
            ? '<ul class="hotel-amenities">' + amenitiesList.map(a => `<li>${a}</li>`).join('') + '</ul>'
            : '<p class="hotel-amenities-none">No amenities listed</p>';
        card.innerHTML = `
            <div class="hotel-card-image" style="background-image:url('${imgUrl}')"></div>
            <div class="hotel-card-body">
                <h3>${h.name}</h3>
                <p class="hotel-location">${h.city}, ${h.country}</p>
                <p class="hotel-price">Starting from ₹${(h.startingPrice != null ? h.startingPrice : 0).toFixed(2)}</p>
                <div class="hotel-amenities-wrap">
                    <strong>Amenities</strong>
                    ${amenitiesHtml}
                </div>
                <div class="hotel-nearby-wrap" data-hotel-id="${h.id}">
                    <strong>Nearby tourist places</strong>
                    <p class="nearby-loading">Loading…</p>
                </div>
                <button type="button" class="primary-btn view-rooms-btn" data-hotel-id="${h.id}"><span class="btn-icon-book"></span> View Rooms & Book</button>
            </div>
        `;
        container.appendChild(card);
        loadNearbyPlaces(h.id);
    });
    container.querySelectorAll('.view-rooms-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = btn.getAttribute('data-hotel-id');
            loadRoomsForHotel(id);
        });
    });
}

async function loadNearbyPlaces(hotelId) {
    const wrap = document.querySelector(`.hotel-nearby-wrap[data-hotel-id="${hotelId}"]`);
    if (!wrap) return;
    try {
        const res = await fetch(`${API_BASE}/api/public/hotels/${hotelId}/recommendations`);
        const places = await res.json();
        const loading = wrap.querySelector('.nearby-loading');
        if (loading) loading.remove();
        if (!places.length) {
            wrap.innerHTML = '<strong>Nearby tourist places</strong><p class="nearby-none">No nearby tourist places listed</p>';
        } else {
            const list = '<ul class="nearby-list">' + places.map(p => `<li>${p.name}${p.type ? ` (${p.type})` : ''}</li>`).join('') + '</ul>';
            wrap.innerHTML = '<strong>Nearby tourist places</strong>' + list;
        }
    } catch (e) {
        const loading = wrap.querySelector('.nearby-loading');
        if (loading) loading.textContent = 'Unable to load';
    }
}

async function searchHotels(lat, lng) {
    const stateEl = document.getElementById('search-state');
    const cityEl = document.getElementById('search-city');
    const state = stateEl && stateEl.value ? stateEl.value.trim() : '';
    if (!state) {
        showToast('Please select a state to search hotels.', true);
        if (stateEl) stateEl.focus();
        return;
    }
    const hotelClass = document.getElementById('search-class').value.trim();
    const city = cityEl && cityEl.value ? cityEl.value.trim() : '';
    const hotelType = document.getElementById('search-hotel-type').value.trim();
    const minPrice = document.getElementById('search-min-price').value.trim();
    const maxPrice = document.getElementById('search-max-price').value.trim();

    const params = new URLSearchParams({ state });
    if (city) params.append('city', city);
    if (lat != null && lng != null) {
        params.append('lat', lat);
        params.append('lng', lng);
        params.append('radiusKm', '10');
    }
    if (hotelClass) params.append('hotelClass', hotelClass);
    if (hotelType) params.append('hotelType', hotelType);
    if (minPrice) params.append('minPrice', minPrice);
    if (maxPrice) params.append('maxPrice', maxPrice);
    const checkInEl = document.getElementById('search-checkin');
    const checkOutEl = document.getElementById('search-checkout');
    if (checkInEl && checkInEl.value) params.append('checkIn', checkInEl.value);
    if (checkOutEl && checkOutEl.value) params.append('checkOut', checkOutEl.value);

    try {
        const container = document.getElementById('hotel-cards');
        if (container) container.innerHTML = '<p style="opacity:0.8;">Searching…</p>';
        const res = await fetch(`${API_BASE}/api/public/hotels?` + params.toString());
        if (!res.ok) throw new Error('Search failed: ' + res.status);
        const data = await res.json();
        const hotels = Array.isArray(data) ? data : [];
        renderHotelCards(hotels);
        scrollToSection('hotels');
        if (!hotels.length) {
            showToast('No hotels found for this state or room type. Try another state.', true);
        }
    } catch (e) {
        console.error(e);
        if (document.getElementById('hotel-cards')) {
            document.getElementById('hotel-cards').innerHTML = '<p style="opacity:0.8;">Search failed. Make sure the server is running.</p>';
        }
        showToast(e.message || 'Failed to search hotels', true);
    }
}

async function loadRoomsForHotel(hotelId) {
    const token = localStorage.getItem('jwt');
    const checkInEl = document.getElementById('search-checkin');
    const checkOutEl = document.getElementById('search-checkout');
    const searchCheckIn = checkInEl && checkInEl.value ? checkInEl.value : '';
    const searchCheckOut = checkOutEl && checkOutEl.value ? checkOutEl.value : '';
    let url = `${API_BASE}/api/public/hotels/${hotelId}/rooms`;
    if (searchCheckIn && searchCheckOut) url += `?checkIn=${encodeURIComponent(searchCheckIn)}&checkOut=${encodeURIComponent(searchCheckOut)}`;
    try {
        const res = await fetch(url);
        const rooms = await res.json();
        const container = document.querySelector(`.hotel-card[data-hotel-id="${hotelId}"]`);
        if (!container) return;

        const existing = container.querySelector('.rooms-panel');
        if (existing) {
            existing.remove();
            return;
        }

        const detail = document.createElement('div');
        detail.className = 'rooms-panel';
        const loginHint = token ? '' : `<p class="rooms-login-hint">You must <button type="button" class="link-btn" onclick="scrollToTopAndOpenLogin()">Login</button> to book a room. Booking is not available without an account.</p>`;
        detail.innerHTML = loginHint + `
            <h4>Available Rooms</h4>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Type</th>
                        <th>Price</th>
                        <th>Check-In</th>
                        <th>Check-Out</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody id="rooms-tbody-hotel-${hotelId}"></tbody>
            </table>
        `;
        container.querySelector('.hotel-card-body').appendChild(detail);

        const tbody = detail.querySelector('tbody');
        (Array.isArray(rooms) ? rooms : []).forEach(r => {
            const tr = document.createElement('tr');
            const rid = String(r.id);
            const tdType = document.createElement('td');
            tdType.textContent = r.type || '';
            const tdPrice = document.createElement('td');
            tdPrice.textContent = '₹' + (r.price != null ? Number(r.price).toFixed(2) : '0.00');
            const tdIn = document.createElement('td');
            const tdOut = document.createElement('td');
            const ci = document.createElement('input');
            ci.type = 'date';
            ci.className = 'checkin-input form-input';
            ci.id = 'room-' + rid + '-checkin';
            ci.name = 'checkin_room_' + rid;
            // Unique "section" per room so the browser does not mirror one row's dates into every row (Chrome autofill).
            ci.setAttribute('autocomplete', 'section-room-' + rid + ' checkin');
            ci.setAttribute('data-room-id', rid);
            if (searchCheckIn) ci.value = searchCheckIn;
            const co = document.createElement('input');
            co.type = 'date';
            co.className = 'checkout-input form-input';
            co.id = 'room-' + rid + '-checkout';
            co.name = 'checkout_room_' + rid;
            co.setAttribute('autocomplete', 'section-room-' + rid + ' checkout');
            co.setAttribute('data-room-id', rid);
            if (searchCheckOut) co.value = searchCheckOut;
            tdIn.appendChild(ci);
            tdOut.appendChild(co);
            const tdBook = document.createElement('td');
            const bookBtn = document.createElement('button');
            bookBtn.type = 'button';
            bookBtn.className = 'primary-btn';
            bookBtn.setAttribute('data-room-id', rid);
            bookBtn.textContent = 'Book';
            tdBook.appendChild(bookBtn);
            tr.appendChild(tdType);
            tr.appendChild(tdPrice);
            tr.appendChild(tdIn);
            tr.appendChild(tdOut);
            tr.appendChild(tdBook);
            tbody.appendChild(tr);
        });

        detail.querySelectorAll('button[data-room-id]').forEach(btn => {
            btn.addEventListener('click', () => {
                if (!localStorage.getItem('jwt')) {
                    showToast('Please log in first to book a room.', true);
                    scrollToTopAndOpenLogin();
                    return;
                }
                const row = btn.closest('tr');
                const roomId = btn.getAttribute('data-room-id');
                const checkInDate = row.querySelector('.checkin-input').value;
                const checkOutDate = row.querySelector('.checkout-input').value;
                if (!checkInDate || !checkOutDate) {
                    showToast('Please select check-in and check-out dates', true);
                    return;
                }
                if (new Date(checkOutDate) <= new Date(checkInDate)) {
                    showToast('Check-out date must be after check-in date', true);
                    return;
                }
                createCustomerBooking(roomId, checkInDate, checkOutDate, hotelId);
            });
        });
    } catch (e) {
        console.error(e);
        showToast('Failed to load rooms', true);
    }
}

async function createCustomerBooking(roomId, checkInDate, checkOutDate, hotelId) {
    const token = getStoredJwt();
    if (!token) {
        showToast('Please log in first to book a room.', true);
        scrollToTopAndOpenLogin();
        return;
    }
    if (isJwtExpired(token)) {
        handleAuthExpired('Session expired. Please log in again to book.');
        return;
    }
    // Stripe config is preloaded on page load + after login. Only block if never loaded (first ms after hard refresh).
    if (!window.STRIPE_CONFIG_READY) {
        await loadStripeKey();
    } else {
        loadStripeKey(); // refresh in background; do not delay the user
    }
    const paymentMethod = await choosePaymentMethod(window.STRIPE_ENABLED);
    const discountCode = ''; // optional: prompt('Discount code (optional):') || '';
    const specialRequests = ''; // optional: prompt('Special requests (optional):') || '';
    const idempotencyKey = buildIdempotencyKey(roomId, checkInDate, checkOutDate);

    const controller = new AbortController();
    const bookingTimeoutMs = 60000;
    const timeoutId = setTimeout(() => controller.abort(), bookingTimeoutMs);
    try {
        const loadingMsg = paymentMethod === 'STRIPE'
            ? 'Preparing secure checkout…'
            : 'Confirming your booking…';
        showActionLoader(loadingMsg);
        const res = await fetch(`${API_BASE}/api/customer/bookings`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify({
                roomId: Number(roomId),
                checkInDate,
                checkOutDate,
                paymentMethod,
                specialRequests: specialRequests || null,
                discountCode: discountCode || null
            }),
            signal: controller.signal
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            if (res.status === 401 || res.status === 403) {
                handleAuthExpired(err.message || 'Please log in again to continue booking.');
                return;
            }
            throw new Error(err.message || err.error || 'Failed to create booking');
        }

        const data = await res.json();
        if (paymentMethod === 'STRIPE' && data.stripeSessionUrl) {
            window.location.href = data.stripeSessionUrl;
            return;
        }

        showToast('Booking confirmed successfully');
        if (hotelId) loadRoomsForHotel(hotelId);
        updateHeaderForAuth();
        window.location.href = '/my-bookings.html';
    } catch (e) {
        console.error(e);
        if (e && e.name === 'AbortError') {
            showToast(
                'Booking timed out: the app or database did not respond in time (not your Wi-Fi). Check Spring Boot is running and your database is reachable, then try again.',
                true
            );
        } else {
            showToast(e.message || 'Booking failed. Please log in and try again.', true);
        }
    } finally {
        clearTimeout(timeoutId);
        hideActionLoader();
    }
}

/** After Stripe redirect: show message if user cancelled. */
function handleStripePaymentCancelled() {
    const hash = (window.location.hash || '').replace('#', '');
    if (hash === 'payment-cancelled') showToast('Payment was cancelled. You can pay at the hotel.');
}

/** After Stripe redirect: verify session and go to My Bookings. Call on page load when URL has session_id and #payment-success. */
async function handleStripePaymentReturn() {
    const params = new URLSearchParams(window.location.search);
    const sessionId = params.get('session_id');
    const hash = (window.location.hash || '').replace('#', '');
    if (!sessionId || hash !== 'payment-success') return;
    const token = localStorage.getItem('jwt');
    if (!token) {
        showToast('Please log in to complete payment verification.', true);
        return;
    }
    showPaymentProcessingOverlay('Processing Stripe payment... Please wait.');
    try {
        const res = await fetch(`${API_BASE}/api/customer/bookings/verify-stripe`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ session_id: sessionId })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Payment verification failed');
        }
        showToast('Payment successful. Booking confirmed.');
        window.location.replace('/my-bookings.html');
    } catch (e) {
        console.error(e);
        showToast(e.message || 'Payment verification failed', true);
        if (confirm('Payment verification failed. Retry now?')) {
            await handleStripePaymentReturn();
        }
    } finally {
        hidePaymentProcessingOverlay();
    }
}

/** Used only to check if user has bookings (e.g. for nav). My Bookings table is on /my-bookings.html. */
async function loadMyBookings() {
    const token = localStorage.getItem('jwt');
    if (!token) return false;
    try {
        const res = await fetch(`${API_BASE}/api/customer/bookings`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) return false;
        const bookings = await res.json();
        return Array.isArray(bookings) && bookings.length > 0;
    } catch (e) {
        return false;
    }
}

function openRegisterModal() {
    const backdrop = document.getElementById('register-modal-backdrop');
    if (backdrop) {
        backdrop.style.display = 'flex';
        backdrop.setAttribute('aria-hidden', 'false');
    }
}

function closeRegisterModal() {
    const backdrop = document.getElementById('register-modal-backdrop');
    if (backdrop) {
        backdrop.style.display = 'none';
        backdrop.setAttribute('aria-hidden', 'true');
    }
}

/** Clear login form so it is not auto-filled with admin or other saved values (e.g. after registration). */
function clearLoginForm() {
    const emailEl = document.getElementById('login-email');
    const passwordEl = document.getElementById('login-password');
    if (emailEl) {
        emailEl.value = '';
        emailEl.removeAttribute('value');
    }
    if (passwordEl) {
        passwordEl.value = '';
        passwordEl.removeAttribute('value');
    }
}

function validatePhone(phone) {
    if (!phone || !phone.trim()) return true;
    const digits = phone.replace(/\D/g, '');
    return digits.length >= 10 && digits.length <= 15;
}

async function customerRegister() {
    const submitBtn = document.querySelector('#register-modal button[type="submit"]');
    const fullName = document.getElementById('register-fullName').value.trim();
    const email = document.getElementById('register-email').value.trim();
    const password = document.getElementById('register-password').value;
    const confirmPassword = document.getElementById('register-confirmPassword').value;
    const phone = document.getElementById('register-phone').value.trim();
    if (!fullName || !email || !password) {
        showToast('Full name, email and password are required.', true);
        return;
    }
    if (password !== confirmPassword) {
        showToast('Password and Confirm Password do not match.', true);
        return;
    }
    if (phone && !validatePhone(phone)) {
        showToast('Phone must be 10–15 digits (e.g. 9876543210 or +919876543210). Remove spaces or symbols.', true);
        return;
    }
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Registering…';
    }
    try {
        showActionLoader('Creating your account...');
        const res = await fetch((API_BASE || '') + '/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, email, password, phone: phone || null })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            const msg = data.message || (data.errors ? 'Validation: ' + data.errors : '') || 'Registration failed';
            throw new Error(typeof msg === 'string' ? msg : 'Registration failed');
        }
        showToast(data.message || 'Registration successful. Check your email and WhatsApp for confirmation.');
        closeRegisterModal();
        document.getElementById('register-fullName').value = '';
        document.getElementById('register-email').value = '';
        document.getElementById('register-password').value = '';
        const confirmEl = document.getElementById('register-confirmPassword');
        if (confirmEl) confirmEl.value = '';
        document.getElementById('register-phone').value = '';
        clearLoginForm();
        openLoginModal();
        setTimeout(clearLoginForm, 50);
    } catch (e) {
        console.error(e);
        showToast(e.message || 'Registration failed. Check console for details.', true);
    } finally {
        hideActionLoader();
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Register';
        }
    }
}

// Keep login across refresh so Stripe return verification can complete.
function initAuthState() {
    updateHeaderForAuth();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initAuthState);
} else {
    initAuthState();
}

window.addEventListener('load', () => {
    registerServiceWorker();
    initInstallPrompt();
    initWhatsAppFloat();
    loadStates();
    loadStripeKey();
    handleStripePaymentCancelled();
    handleStripePaymentReturn();
    const searchState = document.getElementById('search-state');
    if (searchState) searchState.addEventListener('change', onStateChangeForCity);
    updateHeaderForAuth();
    loadMainPageHotels();

    const registerBtn = document.getElementById('header-register-btn');
    if (registerBtn) {
        registerBtn.addEventListener('click', () => openRegisterModal());
    }

    document.getElementById('header-login-btn').addEventListener('click', () => {
        const token = localStorage.getItem('jwt');
        if (token) {
            localStorage.removeItem('jwt');
            window.STRIPE_CONFIG_READY = false;
            window.STRIPE_ENABLED = false;
            updateHeaderForAuth();
            showToast('Logged out');
            return;
        }
        openLoginModal();
    });

    const loginBackdrop = document.getElementById('login-modal-backdrop');
    if (loginBackdrop) {
        loginBackdrop.addEventListener('click', (e) => {
            if (e.target === loginBackdrop) closeLoginModal();
        });
    }

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            const loginBackdrop = document.getElementById('login-modal-backdrop');
            const registerBackdrop = document.getElementById('register-modal-backdrop');
            if (registerBackdrop && registerBackdrop.style.display === 'flex') closeRegisterModal();
            else if (loginBackdrop && loginBackdrop.style.display === 'flex') closeLoginModal();
        }
    });

    const registerBackdrop = document.getElementById('register-modal-backdrop');
    if (registerBackdrop) {
        registerBackdrop.addEventListener('click', (e) => {
            if (e.target === registerBackdrop) closeRegisterModal();
        });
    }
});

async function customerLogin() {
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value.trim();
    if (!email || !password) {
        showToast('Email and password are required', true);
        return;
    }
    try {
        showActionLoader('Signing you in...');
        const res = await fetch(`${API_BASE}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || 'Login failed');
        }
        const data = await res.json();
        const token = normalizeJwt(data.accessToken || data.access_token || data.token);
        if (!token) {
            throw new Error('Login response missing token');
        }
        localStorage.setItem('jwt', token);
        await loadStripeKey();
        showToast('Login successful');
        closeLoginModal();
        document.getElementById('login-email').value = '';
        document.getElementById('login-password').value = '';
        updateHeaderForAuth();
    } catch (e) {
        console.error(e);
        showToast(e.message, true);
    } finally {
        hideActionLoader();
    }
}
