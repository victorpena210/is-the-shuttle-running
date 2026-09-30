'use strict';

const LOOKUP_DELAY_MS = 350;

function resetLookupResult(resultElement) {
    resultElement.hidden = true;
    resultElement.textContent = '';
    resultElement.className = 'alert';
}

function showLookupResult(resultElement, tone, message) {
    resultElement.hidden = false;
    resultElement.className = `alert alert--${tone}`;
    resultElement.textContent = message;
}

async function lookupEmployee(employeeIdInput, employeeLookupResult) {
    const employeeId = employeeIdInput.value.trim();

    if (!employeeId) {
        resetLookupResult(employeeLookupResult);
        return;
    }

    try {
        const response = await fetch(
            '/drivers/lookup?employeeId=' + encodeURIComponent(employeeId),
            {
                headers: {
                    Accept: 'application/json'
                }
            }
        );

        if (!response.ok) {
            throw new Error('Lookup request failed.');
        }

        const result = await response.json();

        if (result.found) {
            showLookupResult(
                employeeLookupResult,
                'success',
                'Employee found: ' + result.name
            );
        } else {
            showLookupResult(
                employeeLookupResult,
                'error',
                result.message
            );
        }
    } catch (error) {
        showLookupResult(
            employeeLookupResult,
            'error',
            'Is the Shuttle Running could not check that employee ID. Please try again.'
        );
    }
}

function initializeDriverLookup() {
    const employeeIdInput = document.getElementById('employeeId');
    const employeeLookupResult = document.getElementById(
        'employeeLookupResult'
    );

    if (!employeeIdInput || !employeeLookupResult) {
        return;
    }

    let lookupTimer;

    employeeIdInput.addEventListener('input', () => {
        window.clearTimeout(lookupTimer);

        const employeeId = employeeIdInput.value.trim();

        if (!employeeId) {
            resetLookupResult(employeeLookupResult);
            return;
        }

        lookupTimer = window.setTimeout(
            () => lookupEmployee(employeeIdInput, employeeLookupResult),
            LOOKUP_DELAY_MS
        );
    });
}

function initializeEmailDialogs() {
    document.querySelectorAll('[data-email-dialog-open]').forEach((button) => {
        button.addEventListener('click', () => {
            const dialog = document.getElementById(
                button.dataset.emailDialogOpen
            );

            if (!dialog || typeof dialog.showModal !== 'function') {
                return;
            }

            dialog.showModal();

            const emailInput = dialog.querySelector('input[name="email"]');
            if (emailInput) {
                emailInput.focus();
                emailInput.select();
            }
        });
    });

    document.querySelectorAll('[data-email-dialog-close]').forEach((button) => {
        button.addEventListener('click', () => {
            const dialog = button.closest('dialog');
            if (dialog) {
                dialog.close();
            }
        });
    });

    document.querySelectorAll('.driver-email-dialog').forEach((dialog) => {
        dialog.addEventListener('click', (event) => {
            if (event.target !== dialog) {
                return;
            }

            const bounds = dialog.getBoundingClientRect();
            const insideDialog =
                event.clientX >= bounds.left
                && event.clientX <= bounds.right
                && event.clientY >= bounds.top
                && event.clientY <= bounds.bottom;

            if (!insideDialog) {
                dialog.close();
            }
        });
    });
}

function initializeDriversPage() {
    initializeDriverLookup();
    initializeEmailDialogs();
}

if (document.readyState === 'loading') {
    document.addEventListener(
        'DOMContentLoaded',
        initializeDriversPage,
        { once: true }
    );
} else {
    initializeDriversPage();
}
