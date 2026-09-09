'use strict';

/**
 * Read server-rendered alert text.
 *
 * The Spring controller remains the source of truth for park-open logic.
 * JavaScript only reflects the state already rendered by the server.
 */
function getAlertText() {
    return Array.from(document.querySelectorAll('.alert'))
        .map(alert =>
            alert.textContent
                .replace(/\s+/g, ' ')
                .trim()
                .toLowerCase()
        )
        .join(' ');
}

function getParkStatus(selectedDateAvailable) {
    if (!selectedDateAvailable) {
        return null;
    }

    const alertText = getAlertText();

    if (alertText.includes('park closed')) {
        return {
            label: 'Park closed',
            tone: 'closed'
        };
    }

    if (alertText.includes('calendar could not be verified')) {
        return {
            label: 'Calendar unverified',
            tone: 'warning'
        };
    }

    const planContainer = document.querySelector('[data-plan-container]');

    if (planContainer && planContainer.children.length > 0) {
        return {
            label: 'Park open',
            tone: 'open'
        };
    }

    return {
        label: 'Park status unavailable',
        tone: 'neutral'
    };
}

export function createStatusChip(label, tone) {
    const chip = document.createElement('span');
    chip.className = `planning-status-chip planning-status-chip--${tone}`;

    const dot = document.createElement('span');
    dot.className = 'planning-status-chip__dot';
    dot.setAttribute('aria-hidden', 'true');

    const text = document.createElement('span');
    text.textContent = label;

    chip.append(dot, text);
    return chip;
}

/**
 * Build the status row displayed beneath the planning calendar.
 */
export function createPlanningStatus({
    selectedDate,
    selectedDateAvailable,
    fullDateFormatter
}) {
    const status = document.createElement('div');
    status.className = 'planning-control__status';

    const statusDate = document.createElement('strong');
    statusDate.className = 'planning-control__selected-date';
    statusDate.textContent = fullDateFormatter.format(selectedDate);

    const statusItems = document.createElement('div');
    statusItems.className = 'planning-control__status-items';

    statusItems.append(
        createStatusChip(
            selectedDateAvailable
                ? 'Schedule available'
                : 'No schedule available',
            selectedDateAvailable
                ? 'schedule'
                : 'neutral'
        )
    );

    const parkStatus = getParkStatus(selectedDateAvailable);

    if (parkStatus) {
        statusItems.append(
            createStatusChip(
                parkStatus.label,
                parkStatus.tone
            )
        );
    }

    status.append(statusDate, statusItems);
    return status;
}
