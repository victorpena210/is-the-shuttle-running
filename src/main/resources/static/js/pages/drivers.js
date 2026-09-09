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
            'RoutePulse could not check that employee ID. Please try again.'
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

if (document.readyState === 'loading') {
    document.addEventListener(
        'DOMContentLoaded',
        initializeDriverLookup,
        { once: true }
    );
} else {
    initializeDriverLookup();
}
