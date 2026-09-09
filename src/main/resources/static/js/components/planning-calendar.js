'use strict';

import {
    addDays,
    getWeekRangeLabel,
    parseIsoDate,
    sameDay,
    startOfWeek,
    toIsoDate
} from '../shared/date-utils.js';

import {
    createPlanningStatus
} from './planning-status.js';

const fullDateFormatter = new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric'
});

const monthYearFormatter = new Intl.DateTimeFormat('en-US', {
    month: 'long',
    year: 'numeric'
});

const shortWeekdayFormatter = new Intl.DateTimeFormat('en-US', {
    weekday: 'short'
});

const shortMonthFormatter = new Intl.DateTimeFormat('en-US', {
    month: 'short'
});

function getIsoDateFromLink(link) {
    try {
        const url = new URL(link.href, window.location.origin);
        return url.searchParams.get('date');
    } catch (error) {
        return null;
    }
}

function getSelectedIsoDate(originalLinks) {
    const selectedLink = originalLinks.find(
        link => link.getAttribute('aria-current') === 'date'
    );

    if (selectedLink) {
        return getIsoDateFromLink(selectedLink);
    }

    /*
     * Preserve a manually requested date even when it is not in the
     * uploaded schedule. RoutePulse can then explain that it is unavailable.
     */
    const requestedDate = new URLSearchParams(window.location.search)
        .get('date');

    if (parseIsoDate(requestedDate)) {
        return requestedDate;
    }

    return getIsoDateFromLink(originalLinks[0]);
}

function buildAvailableDateMap(originalLinks) {
    const availableDateMap = new Map();

    originalLinks.forEach(link => {
        const isoDate = getIsoDateFromLink(link);

        if (parseIsoDate(isoDate)) {
            availableDateMap.set(
                isoDate,
                link.cloneNode(true)
            );
        }
    });

    return availableDateMap;
}

function createPlanningDay(
    date,
    selectedDate,
    availableDateMap
) {
    const isoDate = toIsoDate(date);
    const sourceLink = availableDateMap.get(isoDate);

    const day = sourceLink
        ? document.createElement('a')
        : document.createElement('span');

    day.className = 'planning-calendar__date';

    if (sourceLink) {
        day.classList.add('planning-calendar__date--available');
        day.href = sourceLink.href;
        day.setAttribute(
            'aria-label',
            `${fullDateFormatter.format(date)}. Schedule available.`
        );
    } else {
        day.classList.add('planning-calendar__date--disabled');
        day.setAttribute('aria-disabled', 'true');
        day.setAttribute(
            'aria-label',
            `${fullDateFormatter.format(date)}. No schedule available.`
        );
    }

    if (sameDay(date, selectedDate)) {
        day.setAttribute('aria-current', 'date');
    }

    const weekday = document.createElement('span');
    weekday.className = 'planning-calendar__weekday';
    weekday.textContent = shortWeekdayFormatter.format(date).toUpperCase();

    const dayNumber = document.createElement('strong');
    dayNumber.className = 'planning-calendar__day';
    dayNumber.textContent = String(date.getDate());

    const month = document.createElement('span');
    month.className = 'planning-calendar__month';
    month.textContent = shortMonthFormatter.format(date).toUpperCase();

    day.append(weekday, dayNumber, month);
    return day;
}

function getAvailableWeekStarts(availableDateMap, selectedWeekStart) {
    const availableWeekStarts = Array.from(
        new Set(
            Array.from(availableDateMap.keys())
                .map(parseIsoDate)
                .filter(Boolean)
                .map(startOfWeek)
                .map(toIsoDate)
        )
    )
        .map(parseIsoDate)
        .filter(Boolean)
        .sort((firstDate, secondDate) => firstDate - secondDate);

    /*
     * If someone manually requested a date that is not included in the
     * schedule, still display that week so the unavailable date makes sense.
     */
    const includesSelectedWeek = availableWeekStarts.some(
        weekStart => sameDay(weekStart, selectedWeekStart)
    );

    if (!includesSelectedWeek) {
        availableWeekStarts.push(selectedWeekStart);
        availableWeekStarts.sort(
            (firstDate, secondDate) => firstDate - secondDate
        );
    }

    return availableWeekStarts;
}

function rebuildPlanningHeader(section) {
    const header = section.querySelector('.planning-date__header');

    if (!header) {
        return null;
    }

    header.innerHTML = `
        <h2 id="planning-date-heading">
            Planning date
        </h2>

        <div
            class="planning-control__month"
            data-planning-month>
        </div>
    `;

    return header.querySelector('[data-planning-month]');
}

function createWeekNavigation() {
    const container = document.createElement('div');
    container.className = 'planning-week-navigation';

    const previousButton = document.createElement('button');
    previousButton.type = 'button';
    previousButton.className =
        'planning-week-button planning-week-button--previous';
    previousButton.innerHTML = `
        <span aria-hidden="true">‹</span>
        <span class="planning-week-button__label">Previous week</span>
    `;

    const weekRange = document.createElement('strong');
    weekRange.className = 'planning-week-navigation__range';
    weekRange.setAttribute('aria-live', 'polite');

    const nextButton = document.createElement('button');
    nextButton.type = 'button';
    nextButton.className =
        'planning-week-button planning-week-button--next';
    nextButton.innerHTML = `
        <span class="planning-week-button__label">Next week</span>
        <span aria-hidden="true">›</span>
    `;

    container.append(previousButton, weekRange, nextButton);

    return {
        container,
        previousButton,
        weekRange,
        nextButton
    };
}

function renameDemandHeading() {
    const demandHeading = document.getElementById(
        'transportation-demand-heading'
    );

    if (demandHeading) {
        demandHeading.textContent = 'Demand overview';
    }
}

/**
 * Enhance the server-rendered planning date list into the interactive
 * RoutePulse Monday-Sunday planning calendar.
 */
export function initializePlanningCalendar() {
    const section = document.querySelector(
        '.planning-date[data-dashboard-section="demand"]'
    );

    if (!section) {
        return;
    }

    const calendar = section.querySelector('.planning-calendar');

    if (!calendar) {
        return;
    }

    const originalLinks = Array.from(
        calendar.querySelectorAll('.planning-calendar__date[href]')
    );

    if (originalLinks.length === 0) {
        return;
    }

    const availableDateMap = buildAvailableDateMap(originalLinks);

    if (availableDateMap.size === 0) {
        return;
    }

    const selectedIsoDate = getSelectedIsoDate(originalLinks);
    const selectedDate = parseIsoDate(selectedIsoDate);

    if (!selectedDate) {
        return;
    }

    const selectedWeekStart = startOfWeek(selectedDate);
    const availableWeekStarts = getAvailableWeekStarts(
        availableDateMap,
        selectedWeekStart
    );

    let visibleWeekIndex = availableWeekStarts.findIndex(
        weekStart => sameDay(weekStart, selectedWeekStart)
    );

    if (visibleWeekIndex < 0) {
        visibleWeekIndex = 0;
    }

    const monthHeading = rebuildPlanningHeader(section);

    const {
        container: weekNavigation,
        previousButton,
        weekRange,
        nextButton
    } = createWeekNavigation();

    calendar.before(weekNavigation);

    const selectedDateAvailable = availableDateMap.has(selectedIsoDate);

    const status = createPlanningStatus({
        selectedDate,
        selectedDateAvailable,
        fullDateFormatter
    });

    calendar.after(status);
    section.classList.add('planning-date--enhanced');

    function renderWeek() {
        const weekStart = availableWeekStarts[visibleWeekIndex];

        calendar.replaceChildren();

        for (let dayOffset = 0; dayOffset < 7; dayOffset += 1) {
            calendar.append(
                createPlanningDay(
                    addDays(weekStart, dayOffset),
                    selectedDate,
                    availableDateMap
                )
            );
        }

        weekRange.textContent = getWeekRangeLabel(weekStart);

        if (monthHeading) {
            monthHeading.textContent = monthYearFormatter
                .format(weekStart)
                .toUpperCase();
        }

        previousButton.disabled = visibleWeekIndex === 0;
        nextButton.disabled =
            visibleWeekIndex === availableWeekStarts.length - 1;
    }

    previousButton.addEventListener('click', () => {
        if (visibleWeekIndex > 0) {
            visibleWeekIndex -= 1;
            renderWeek();
        }
    });

    nextButton.addEventListener('click', () => {
        if (visibleWeekIndex < availableWeekStarts.length - 1) {
            visibleWeekIndex += 1;
            renderWeek();
        }
    });

    renderWeek();
    renameDemandHeading();
}
