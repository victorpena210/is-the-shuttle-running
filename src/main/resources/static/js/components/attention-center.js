'use strict';

const REFRESH_INTERVAL_MS = 30_000;

function attentionEndpoint() {
    const currentUrl = new URL(window.location.href);
    const endpoint = new URL('/app/attention', window.location.origin);

    const date = currentUrl.searchParams.get('date');
    if (date) {
        endpoint.searchParams.set('date', date);
    }

    return endpoint;
}

async function refreshAttentionCenter() {
    if (document.visibilityState !== 'visible') {
        return;
    }

    const currentCenter = document.querySelector('.attention-center');

    if (!currentCenter) {
        return;
    }

    try {
        const response = await fetch(attentionEndpoint(), {
            method: 'GET',
            cache: 'no-store',
            headers: {
                'X-Requested-With': 'XMLHttpRequest'
            }
        });

        if (!response.ok) {
            return;
        }

        const html = await response.text();
        const template = document.createElement('template');
        template.innerHTML = html.trim();

        const updatedCenter = template.content.querySelector('.attention-center');

        if (!updatedCenter) {
            return;
        }

        const activeView = document.body.dataset.dashboardView || 'overview';
        updatedCenter.hidden =
            updatedCenter.dataset.dashboardSection !== activeView;

        currentCenter.replaceWith(updatedCenter);
    } catch (error) {
        // A live refresh failure should never interrupt the manager dashboard.
        console.debug('Is the Shuttle Running attention refresh skipped.', error);
    }
}

/**
 * Keep time-sensitive manager exceptions current while the dashboard is open.
 * A normal page load still remains the source of truth if polling is unavailable.
 */
export function initializeAttentionCenter() {
    if (!document.querySelector('.attention-center')) {
        return;
    }

    window.setInterval(refreshAttentionCenter, REFRESH_INTERVAL_MS);

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            refreshAttentionCenter();
        }
    });
}
