(() => {
    'use strict';
    const alert = document.querySelector('[data-session-review-alert]');
    if (!alert) return;
    let loading = false;
    async function refresh() {
        if (loading || document.hidden) return;
        loading = true;
        try {
            const response = await fetch(alert.dataset.countUrl, {
                credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' }
            });
            if (!response.ok || response.redirected) return;
            const { count } = await response.json();
            if (!Number.isSafeInteger(count) || count < 0) return;
            const message = `${count} ${count === 1 ? 'session has' : 'sessions have'} reached the four-hour limit.`;
            const text = alert.querySelector('[data-review-message]');
            if (text.textContent !== message) text.textContent = message;
            alert.hidden = count === 0;
        } catch (_) {
            // Keep any existing warning visible during a temporary network failure.
        } finally { loading = false; }
    }
    refresh();
    window.setInterval(refresh, 30000);
    document.addEventListener('visibilitychange', refresh);
})();
