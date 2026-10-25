(() => {
    'use strict';
    const preview = document.querySelector('[data-live-rider-preview]');
    if (!preview) return;
    const card = preview.querySelector('[data-live-rider-card]');
    const note = preview.querySelector('[data-live-rider-refresh-note]');
    let loading = false;

    async function refresh() {
        if (loading || document.hidden) return;
        loading = true;
        try {
            const response = await fetch(preview.dataset.refreshUrl, {
                cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000)
            });
            if (!response.ok || response.redirected) throw new Error('Status unavailable');
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const updated = page.querySelector('[data-live-rider-card] .rider-status-card');
            if (!updated) throw new Error('Missing status card');
            // Refresh only the preview so manager notes and session-close forms stay intact.
            card.replaceChildren(updated);
            note.textContent = 'Updated ' + new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', second: '2-digit' })
                + ' · Refreshes every 20 seconds.';
        } catch (_) {
            note.textContent = 'Status may be out of date. Unable to refresh; retrying automatically.';
        } finally {
            loading = false;
        }
    }

    window.setInterval(refresh, 20000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
})();
