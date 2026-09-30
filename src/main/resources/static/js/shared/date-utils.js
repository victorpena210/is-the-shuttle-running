'use strict';

/**
 * Parse a YYYY-MM-DD value as a local date at noon.
 *
 * Noon avoids timezone/DST edge cases that can happen when
 * a date-only value is interpreted at midnight.
 */
export function parseIsoDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) {
        return null;
    }

    const [year, month, day] = value.split('-').map(Number);

    const date = new Date(
        year,
        month - 1,
        day,
        12,
        0,
        0,
        0
    );

    return Number.isNaN(date.getTime())
        ? null
        : date;
}

/**
 * Convert a Date to Is the Shuttle Running's YYYY-MM-DD planning-date format.
 */
export function toIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
}

/**
 * Return a new Date offset by the requested number of days.
 */
export function addDays(date, amount) {
    const result = new Date(date);
    result.setDate(result.getDate() + amount);
    return result;
}

/**
 * Is the Shuttle Running planning weeks run Monday-Sunday.
 */
export function startOfWeek(date) {
    const result = new Date(date);
    const day = result.getDay();
    const offset = day === 0 ? -6 : 1 - day;

    result.setDate(result.getDate() + offset);
    result.setHours(12, 0, 0, 0);

    return result;
}

/**
 * Compare two Date objects by calendar day only.
 */
export function sameDay(firstDate, secondDate) {
    return Boolean(
        firstDate
        && secondDate
        && toIsoDate(firstDate) === toIsoDate(secondDate)
    );
}

/**
 * Format a Monday-Sunday planning week for the navigation control.
 */
export function getWeekRangeLabel(weekStart) {
    const weekEnd = addDays(weekStart, 6);

    const startMonth = weekStart.toLocaleString('en-US', {
        month: 'long'
    });

    const endMonth = weekEnd.toLocaleString('en-US', {
        month: 'long'
    });

    const sameMonth =
        weekStart.getMonth() === weekEnd.getMonth()
        && weekStart.getFullYear() === weekEnd.getFullYear();

    const sameYear =
        weekStart.getFullYear() === weekEnd.getFullYear();

    if (sameMonth) {
        return `${startMonth} ${weekStart.getDate()}–${weekEnd.getDate()}, ${weekEnd.getFullYear()}`;
    }

    if (sameYear) {
        return `${startMonth} ${weekStart.getDate()}–${endMonth} ${weekEnd.getDate()}, ${weekEnd.getFullYear()}`;
    }

    return `${startMonth} ${weekStart.getDate()}, ${weekStart.getFullYear()}–${endMonth} ${weekEnd.getDate()}, ${weekEnd.getFullYear()}`;
}
