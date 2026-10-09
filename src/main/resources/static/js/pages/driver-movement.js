'use strict';
(() => {
    const panel = document.getElementById('movement-monitor');
    if (!panel) return;
    const get = id => document.getElementById(id);
    const endpoint = '/driver/movement/' + encodeURIComponent(panel.dataset.sessionId);
    const consentKey = 'shuttle-location-' + panel.dataset.sessionId;
    const enable = get('movement-enable'), stop = get('movement-stop');
    let sharing = false, ended = false, locating = false, posting = false, polling = false, responding = false;
    let lastLost = false, pollTimer, locationTimer;
    const time = value => value ? new Date(value).toLocaleTimeString([], {hour:'numeric', minute:'2-digit'}) : 'not yet recorded';
    const storage = (value) => { try { if (value == null) return sessionStorage.getItem(consentKey); sessionStorage.setItem(consentKey,value); } catch (_) { return null; } };
    let requestChain = Promise.resolve();
    function request(path = '', body) {
        // Serialize reads and writes so a delayed poll cannot overwrite a newer driver response.
        const operation = requestChain.catch(() => {}).then(() => performRequest(path, body));
        requestChain = operation;
        return operation;
    }
    async function performRequest(path, body) {
        const controller = new AbortController();
        const deadline = setTimeout(() => controller.abort(), 12000);
        try {
            const options = {credentials:'same-origin', cache:'no-store', signal:controller.signal, headers:{Accept:'application/json'}};
            if (body !== undefined) {
                options.method = 'POST'; options.headers['Content-Type'] = 'application/json';
                const token = document.querySelector('meta[name="_csrf"]');
                const header = document.querySelector('meta[name="_csrf_header"]');
                if (token && header) options.headers[header.content] = token.content;
                options.body = JSON.stringify(body);
            }
            const res = await fetch(endpoint + path, options);
            if (res.redirected || !res.headers.get('content-type')?.includes('application/json'))
                throw new Error('Sign in again or refresh this page to reconnect.');
            const data = await res.json();
            if (!res.ok) throw new Error(data.message || 'Could not update this session. Refresh to check its status.');
            return data;
        } finally { clearTimeout(deadline); }
    }
    function render(data) {
        if (ended && data.active) return;
        const messages = {
            DISABLED: 'Service is active. Location sharing is optional; no check-ins are required.',
            LOCATION_UNAVAILABLE: 'Service is active. Location updates are unavailable; no action is required.',
            WAITING: 'Waiting at pickup — service remains active.',
            MONITORING: 'Receiving reliable location updates. Service stays active without check-ins.',
            ENDED: data.endReason === 'INACTIVITY_TIMEOUT' ? 'This session was closed by the previous inactivity policy and needs review. Start a new session to drive.'
                : data.endReason === 'PAUSE' ? 'On break — service is paused. Use the NFC/QR checkpoint to resume.' : 'This session has ended.'
        };
        get('movement-status').textContent = messages[data.state] || 'Checking status…';
        get('movement-confirmed').textContent = 'Last service confirmation: ' + time(data.lastConfirmedAt)
            + (data.movementConfirmedAt ? '. Last route movement: ' + time(data.movementConfirmedAt) : '.');
        get('movement-route-note').hidden = data.routeConfigured || !data.active;
        get('movement-responses').hidden = !data.active;
        if (!data.active) {
            ended = true; sharing = false; storage('off');
            clearInterval(pollTimer); clearInterval(locationTimer);
            if (!get('movement-refresh')) {
                const link = document.createElement('a'); link.id = 'movement-refresh'; link.href = '/driver';
                link.textContent = 'Refresh assignments and driving records'; panel.append(link);
            }
        }
        enable.hidden = sharing || ended; stop.hidden = !sharing || ended;
        enable.textContent = data.enabled ? 'Resume sharing location' : 'Share location for this session';
    }
    function error(ex) { get('movement-connection').textContent = ex.message || 'Unable to connect. Your last recorded session status has not changed.'; }
    async function lost(message) {
        get('movement-connection').textContent = message;
        if (!lastLost && !ended) {
            lastLost = true;
            try { render(await request('/signal-lost',{})); } catch (ex) { error(ex); }
        }
    }
    function locate() {
        if (!sharing || ended || locating || posting || responding) return;
        locating = true;
        navigator.geolocation.getCurrentPosition(async position => {
            locating = false;
            if (!sharing || ended || responding) return;
            // Never queue positions for later delivery or reuse the last fix as a heartbeat.
            if (Date.now() - position.timestamp > 45000) {
                await lost('The device returned an old location. Waiting for a current fix.'); return;
            }
            posting = true;
            try {
                const c = position.coords;
                render(await request('/location', {latitude:c.latitude, longitude:c.longitude,
                    accuracy:c.accuracy, speed:c.speed, capturedAt:new Date(position.timestamp).toISOString()}));
                lastLost = false; get('movement-connection').textContent = '';
            } catch (ex) { error(ex); } finally { posting = false; }
        }, async ex => {
            locating = false;
            if (!sharing || ended) return;
            if (ex.code === 1) { sharing = false; storage('off'); enable.hidden = false; stop.hidden = true; }
            await lost(ex.code === 1 ? 'Location access was denied. Allow it in browser settings, then resume sharing.'
                : 'A current location is unavailable. Missing updates will not end your session.');
        }, {enableHighAccuracy:true, maximumAge:0, timeout:12000});
    }
    async function begin() {
        if (!navigator.geolocation || !window.isSecureContext) {
            error(new Error('Location monitoring requires HTTPS and a browser with location access.')); return;
        }
        enable.disabled = true;
        try {
            const data = await request('/enable',{});
            sharing = true; storage('on'); lastLost = false; render(data);
            get('movement-connection').textContent = 'Waiting for location permission and a current fix…'; locate();
        } catch (ex) { error(ex); } finally { enable.disabled = false; }
    }
    enable.addEventListener('click',begin);
    stop.addEventListener('click', async () => {
        sharing = false; storage('off'); enable.hidden = false; stop.hidden = true;
        await lost('Location sharing stopped. Your session remains active.');
    });
    panel.querySelectorAll('[data-movement-response]').forEach(button => button.addEventListener('click', async () => {
        if (responding || ended) return;
        responding = true;
        panel.querySelectorAll('[data-movement-response]').forEach(b => b.disabled = true);
        try { render(await request('/response',{response:button.dataset.movementResponse})); get('movement-connection').textContent = ''; }
        catch (ex) { error(ex); }
        finally { responding = false; panel.querySelectorAll('[data-movement-response]').forEach(b => b.disabled = ended); }
    }));
    async function poll() {
        if (polling || ended || responding || posting) return;
        polling = true;
        try { render(await request()); } catch (ex) { error(ex); } finally { polling = false; }
    }
    pollTimer = setInterval(poll,15000);
    locationTimer = setInterval(locate,15000);
    poll().then(() => { if (!ended && storage() === 'on') begin(); });
    document.addEventListener('visibilitychange',() => { if (!document.hidden) { poll(); locate(); } });
})();
