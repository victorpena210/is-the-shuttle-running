(() => {
    let updating = false;
    async function refresh() {
        const panel = document.querySelector('[data-pickup-poll]');
        if (!panel || updating || document.hidden || panel.contains(document.activeElement)) return;
        updating = true;
        try {
            const response = await fetch(panel.dataset.pickupPoll, {credentials: 'same-origin', cache: 'no-store'});
            if (!response.ok || response.redirected) throw new Error('Unavailable');
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const next = page.querySelector('#upcoming-pickups');
            if (!next) throw new Error('Unavailable');
            if (!panel.contains(document.activeElement)) panel.replaceWith(next);
        } catch (_) {
            const message = panel.querySelector('[data-pickup-connection]');
            if (message) message.textContent = 'Pickup updates unavailable. Refresh the page to check the latest list.';
        } finally { updating = false; }
    }
    setInterval(refresh, 60000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
})();
