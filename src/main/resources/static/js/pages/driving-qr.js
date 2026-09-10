'use strict';

function initializeDrivingQrPage() {
    const printButton = document.querySelector('[data-print-driving-qr]');
    const copyNfcButton = document.querySelector('[data-copy-nfc-url]');
    const nfcUrl = document.querySelector('[data-nfc-url]');
    const regenerateForm = document.querySelector('[data-confirm-qr-regenerate]');

    printButton?.addEventListener('click', () => window.print());

    copyNfcButton?.addEventListener('click', async () => {
        const value = nfcUrl?.textContent?.trim();
        if (!value) {
            return;
        }

        const originalText = copyNfcButton.textContent.trim();

        try {
            await navigator.clipboard.writeText(value);
            copyNfcButton.textContent = 'Copied NFC URL';
        } catch (_error) {
            const textarea = document.createElement('textarea');
            textarea.value = value;
            textarea.setAttribute('readonly', '');
            textarea.style.position = 'fixed';
            textarea.style.opacity = '0';
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            textarea.remove();
            copyNfcButton.textContent = 'Copied NFC URL';
        }

        window.setTimeout(() => {
            copyNfcButton.textContent = originalText;
        }, 1800);
    });

    regenerateForm?.addEventListener('submit', event => {
        const confirmed = window.confirm(
            'Generate a new shuttle checkpoint code? The current QR code and NFC URL will stop working immediately.'
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
