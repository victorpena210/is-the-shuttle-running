'use strict';

function initializeDrivingQrPage() {
    const printButton = document.querySelector('[data-print-driving-qr]');
    const regenerateForm = document.querySelector('[data-confirm-qr-regenerate]');

    printButton?.addEventListener('click', () => window.print());

    regenerateForm?.addEventListener('submit', event => {
        const confirmed = window.confirm(
            'Generate a new shuttle QR code? The current QR will stop working immediately.'
        );

        if (!confirmed) {
            event.preventDefault();
        }
    });
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeDrivingQrPage, { once: true });
} else {
    initializeDrivingQrPage();
}
