(() => {
    'use strict';

    const READY_EVENT = 'DOMContentLoaded';

    function parseTime(value) {
        if (!value || !/^\d{1,2}:\d{2}/.test(value)) {
            return null;
        }

        const [hours, minutes] = value
            .slice(0, 5)
            .split(':')
            .map(Number);

        if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
            return null;
        }

        return (hours * 60) + minutes;
    }

    function normalizeEnd(startMinutes, endMinutes) {
        if (startMinutes == null || endMinutes == null) {
            return null;
        }

        return endMinutes <= startMinutes
            ? endMinutes + (24 * 60)
            : endMinutes;
    }

    function normalizeTimeIntoWorkWindow(timeMinutes, workStart, workEnd) {
        if (timeMinutes == null) {
            return null;
        }

        let normalized = timeMinutes;

        if (workEnd > (24 * 60)
                && normalized < workStart) {
            normalized += 24 * 60;
        }

        return normalized;
    }

    function clamp(value, minimum, maximum) {
        return Math.min(
            Math.max(value, minimum),
            maximum
        );
    }

    function initializeTimeline(timeline) {
        const workStart = parseTime(timeline.dataset.workStart);
        const rawWorkEnd = parseTime(timeline.dataset.workEnd);
        const rawDriveStart = parseTime(timeline.dataset.driveStart);
        const rawDriveEnd = parseTime(timeline.dataset.driveEnd);
        const fill = timeline.querySelector('[data-driving-fill]');

        if (workStart == null
                || rawWorkEnd == null
                || rawDriveStart == null
                || rawDriveEnd == null
                || !fill) {
            return;
        }

        const workEnd = normalizeEnd(workStart, rawWorkEnd);
        const driveStart = normalizeTimeIntoWorkWindow(
            rawDriveStart,
            workStart,
            workEnd
        );

        let driveEnd = normalizeEnd(
            driveStart,
            normalizeTimeIntoWorkWindow(
                rawDriveEnd,
                workStart,
                workEnd
            )
        );

        if (driveEnd == null || workEnd <= workStart) {
            return;
        }

        const visibleStart = clamp(
            driveStart,
            workStart,
            workEnd
        );

        const visibleEnd = clamp(
            driveEnd,
            workStart,
            workEnd
        );

        const workDuration = workEnd - workStart;

        if (visibleEnd <= visibleStart || workDuration <= 0) {
            fill.style.left = '0%';
            fill.style.width = '0%';
            return;
        }

        const leftPercent =
            ((visibleStart - workStart) / workDuration) * 100;

        const widthPercent =
            ((visibleEnd - visibleStart) / workDuration) * 100;

        fill.style.left = `${leftPercent}%`;
        fill.style.width = `${widthPercent}%`;
    }

    function selectedOption(select) {
        return select.options[select.selectedIndex] || null;
    }

    function updateDriverForm(form) {
        const select = form.querySelector('[data-coverage-driver-select]');
        const warning = form.querySelector('[data-unscheduled-warning]');
        const submitButton = form.querySelector('[data-driver-submit]');

        if (!select || !submitButton) {
            return;
        }

        if (!submitButton.dataset.defaultLabel) {
            submitButton.dataset.defaultLabel =
                submitButton.textContent.trim();
        }

        const option = selectedOption(select);
        const hasSelection = Boolean(select.value);
        const unscheduled =
            hasSelection
            && option
            && option.dataset.scheduled === 'false';

        if (warning) {
            warning.hidden = !unscheduled;
        }

        const originalDriver = form.dataset.originalDriver;
        const unchanged =
            Boolean(originalDriver)
            && String(select.value) === String(originalDriver);

        submitButton.disabled =
            !hasSelection
            || unchanged;

        if (unscheduled && !unchanged) {
            submitButton.textContent = originalDriver
                ? 'Save override anyway'
                : 'Assign anyway';
        } else {
            submitButton.textContent =
                submitButton.dataset.defaultLabel;
        }
    }

    function initializeDriverForm(form) {
        const select = form.querySelector('[data-coverage-driver-select]');

        if (!select) {
            return;
        }

        select.addEventListener('change', () => {
            updateDriverForm(form);
        });

        updateDriverForm(form);
    }

    function openCoverageDetails(selector) {
        if (!selector) {
            return;
        }

        const details = document.querySelector(selector);

        if (!(details instanceof HTMLDetailsElement)) {
            return;
        }

        details.open = true;
        details.scrollIntoView({
            behavior: 'smooth',
            block: 'center'
        });

        window.setTimeout(() => {
            const firstField = details.querySelector(
                'select, input:not([type="hidden"]), textarea, button'
            );

            if (firstField instanceof HTMLElement) {
                firstField.focus({ preventScroll: true });
            }
        }, 250);
    }

    function initializeOpenButtons() {
        document
            .querySelectorAll('[data-open-coverage-details]')
            .forEach(button => {
                button.addEventListener('click', () => {
                    openCoverageDetails(
                        button.dataset.openCoverageDetails
                    );
                });
            });
    }

    function initializeCloseButtons() {
        document
            .querySelectorAll('[data-close-coverage-details]')
            .forEach(button => {
                button.addEventListener('click', () => {
                    const details = button.closest('details');

                    if (details instanceof HTMLDetailsElement) {
                        details.open = false;

                        const summary = details.querySelector('summary');
                        if (summary instanceof HTMLElement) {
                            summary.focus();
                        }
                    }
                });
            });
    }

    function initializeCoverage() {
        document
            .querySelectorAll('[data-coverage-timeline]')
            .forEach(initializeTimeline);

        document
            .querySelectorAll('.coverage-driver-form')
            .forEach(initializeDriverForm);

        initializeOpenButtons();
        initializeCloseButtons();
    }

    if (document.readyState === 'loading') {
        document.addEventListener(
            READY_EVENT,
            initializeCoverage,
            { once: true }
        );
    } else {
        initializeCoverage();
    }
})();
