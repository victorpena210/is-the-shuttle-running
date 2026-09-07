(function () {
    'use strict';

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


    function parseIsoDate(value) {

        if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) {
            return null;
        }

        const [year, month, day] =
            value.split('-').map(Number);

        /*
         * Noon avoids timezone/DST edge cases that can happen
         * when a date-only value is interpreted at midnight.
         */
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


    function toIsoDate(date) {

        const year = date.getFullYear();

        const month = String(
            date.getMonth() + 1
        ).padStart(2, '0');

        const day = String(
            date.getDate()
        ).padStart(2, '0');

        return `${year}-${month}-${day}`;
    }


    function addDays(date, amount) {

        const result = new Date(date);

        result.setDate(
            result.getDate() + amount
        );

        return result;
    }


    function startOfWeek(date) {

        const result = new Date(date);

        const day =
            result.getDay();

        /*
         * RoutePulse planning weeks run Monday-Sunday.
         */
        const offset =
            day === 0
                ? -6
                : 1 - day;

        result.setDate(
            result.getDate() + offset
        );

        result.setHours(
            12,
            0,
            0,
            0
        );

        return result;
    }


    function sameDay(
        firstDate,
        secondDate
    ) {

        return firstDate &&
            secondDate &&
            toIsoDate(firstDate) ===
                toIsoDate(secondDate);
    }


    function getIsoDateFromLink(link) {

        try {

            const url = new URL(
                link.href,
                window.location.origin
            );

            return url.searchParams.get('date');

        } catch (error) {

            return null;
        }
    }


    function getSelectedIsoDate() {

        /*
         * Prefer the date Thymeleaf already marked
         * as the selected planning date.
         */
        const selectedLink =
            originalLinks.find(
                link =>
                    link.getAttribute(
                        'aria-current'
                    ) === 'date'
            );

        if (selectedLink) {

            return getIsoDateFromLink(
                selectedLink
            );
        }


        /*
         * If the requested date is not available,
         * it will not have an aria-current link.
         *
         * In that case preserve the requested date
         * from the query string so RoutePulse can
         * show the manager why it is unavailable.
         */
        const requestedDate =
            new URLSearchParams(
                window.location.search
            ).get('date');

        if (parseIsoDate(requestedDate)) {
            return requestedDate;
        }


        return getIsoDateFromLink(
            originalLinks[0]
        );
    }


    function getWeekRangeLabel(
        weekStart
    ) {

        const weekEnd =
            addDays(
                weekStart,
                6
            );

        const startMonth =
            weekStart.toLocaleString(
                'en-US',
                {
                    month: 'long'
                }
            );

        const endMonth =
            weekEnd.toLocaleString(
                'en-US',
                {
                    month: 'long'
                }
            );

        const sameMonth =
            weekStart.getMonth() ===
                weekEnd.getMonth() &&
            weekStart.getFullYear() ===
                weekEnd.getFullYear();

        const sameYear =
            weekStart.getFullYear() ===
                weekEnd.getFullYear();


        if (sameMonth) {

            return `${startMonth} ${weekStart.getDate()}–${weekEnd.getDate()}, ${weekEnd.getFullYear()}`;
        }


        if (sameYear) {

            return `${startMonth} ${weekStart.getDate()}–${endMonth} ${weekEnd.getDate()}, ${weekEnd.getFullYear()}`;
        }


        return `${startMonth} ${weekStart.getDate()}, ${weekStart.getFullYear()}–${endMonth} ${weekEnd.getDate()}, ${weekEnd.getFullYear()}`;
    }


    function getAlertText() {

        return Array.from(
            document.querySelectorAll(
                '.alert'
            )
        )
            .map(alert =>
                alert.textContent
                    .replace(/\s+/g, ' ')
                    .trim()
                    .toLowerCase()
            )
            .join(' ');
    }


    function getParkStatus(
        selectedDateAvailable
    ) {

        if (!selectedDateAvailable) {
            return null;
        }


        /*
         * The controller already owns park-open logic.
         *
         * We are only reading the resulting UI state here,
         * not recalculating whether the park is open.
         */
        const alertText =
            getAlertText();


        if (
            alertText.includes(
                'park closed'
            )
        ) {

            return {
                label: 'Park closed',
                tone: 'closed'
            };
        }


        if (
            alertText.includes(
                'calendar could not be verified'
            )
        ) {

            return {
                label: 'Calendar unverified',
                tone: 'warning'
            };
        }


        /*
         * The generated plan content is only rendered
         * when the existing controller says planning
         * can proceed.
         */
        const planContainer =
            document.querySelector(
                '[data-plan-container]'
            );


        if (
            planContainer &&
            planContainer.children.length > 0
        ) {

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


    function createStatusChip(
        label,
        tone
    ) {

        const chip =
            document.createElement(
                'span'
            );

        chip.className =
            `planning-status-chip planning-status-chip--${tone}`;


        const dot =
            document.createElement(
                'span'
            );

        dot.className =
            'planning-status-chip__dot';

        dot.setAttribute(
            'aria-hidden',
            'true'
        );


        const text =
            document.createElement(
                'span'
            );

        text.textContent =
            label;


        chip.append(
            dot,
            text
        );


        return chip;
    }


    function createPlanningDay(
        date,
        selectedDate,
        availableDateMap
    ) {

        const isoDate =
            toIsoDate(date);

        const sourceLink =
            availableDateMap.get(
                isoDate
            );


        /*
         * Available dates remain normal links.
         *
         * Dates without schedule data become disabled
         * visual calendar cells.
         */
        const day =
            sourceLink
                ? document.createElement('a')
                : document.createElement('span');


        day.className =
            'planning-calendar__date';


        if (sourceLink) {

            day.classList.add(
                'planning-calendar__date--available'
            );

            day.href =
                sourceLink.href;

            day.setAttribute(
                'aria-label',
                `${fullDateFormatter.format(date)}. Schedule available.`
            );

        } else {

            day.classList.add(
                'planning-calendar__date--disabled'
            );

            day.setAttribute(
                'aria-disabled',
                'true'
            );

            day.setAttribute(
                'aria-label',
                `${fullDateFormatter.format(date)}. No schedule available.`
            );
        }


        if (
            sameDay(
                date,
                selectedDate
            )
        ) {

            day.setAttribute(
                'aria-current',
                'date'
            );
        }


        const weekday =
            document.createElement(
                'span'
            );

        weekday.className =
            'planning-calendar__weekday';

        weekday.textContent =
            shortWeekdayFormatter
                .format(date)
                .toUpperCase();


        const dayNumber =
            document.createElement(
                'strong'
            );

        dayNumber.className =
            'planning-calendar__day';

        dayNumber.textContent =
            String(
                date.getDate()
            );


        const month =
            document.createElement(
                'span'
            );

        month.className =
            'planning-calendar__month';

        month.textContent =
            shortMonthFormatter
                .format(date)
                .toUpperCase();


        day.append(
            weekday,
            dayNumber,
            month
        );


        return day;
    }


    /*
     * =====================================================
     * BUILD AVAILABLE-DATE LOOKUP
     * =====================================================
     */

    const availableDateMap =
        new Map();


    originalLinks.forEach(
        link => {

            const isoDate =
                getIsoDateFromLink(
                    link
                );

            if (
                parseIsoDate(
                    isoDate
                )
            ) {

                availableDateMap.set(
                    isoDate,
                    link.cloneNode(true)
                );
            }
        }
    );


    if (
        availableDateMap.size === 0
    ) {
        return;
    }


    const selectedIsoDate =
        getSelectedIsoDate();

    const selectedDate =
        parseIsoDate(
            selectedIsoDate
        );


    if (!selectedDate) {
        return;
    }


    /*
     * =====================================================
     * FIND WEEKS THAT ACTUALLY CONTAIN SCHEDULE DATA
     * =====================================================
     *
     * This is why an old August upload and a September
     * upload no longer appear as one giant row of dates.
     *
     * Previous / next jumps between useful planning weeks.
     */

    const availableWeekStarts =
        Array.from(
            new Set(
                Array.from(
                    availableDateMap.keys()
                )
                    .map(parseIsoDate)
                    .filter(Boolean)
                    .map(startOfWeek)
                    .map(toIsoDate)
            )
        )
            .map(parseIsoDate)
            .filter(Boolean)
            .sort(
                (
                    firstDate,
                    secondDate
                ) =>
                    firstDate -
                    secondDate
            );


    const selectedWeekStart =
        startOfWeek(
            selectedDate
        );


    /*
     * If someone manually requested a date that is not
     * included in the schedule, still show that calendar
     * week so the unavailable date makes visual sense.
     */
    if (
        !availableWeekStarts.some(
            weekStart =>
                sameDay(
                    weekStart,
                    selectedWeekStart
                )
        )
    ) {

        availableWeekStarts.push(
            selectedWeekStart
        );

        availableWeekStarts.sort(
            (
                firstDate,
                secondDate
            ) =>
                firstDate -
                secondDate
        );
    }


    let visibleWeekIndex =
        availableWeekStarts.findIndex(
            weekStart =>
                sameDay(
                    weekStart,
                    selectedWeekStart
                )
        );


    if (visibleWeekIndex < 0) {
        visibleWeekIndex = 0;
    }


    /*
     * =====================================================
     * REBUILD PLANNING-DATE HEADER
     * =====================================================
     */

    const header =
        section.querySelector(
            '.planning-date__header'
        );


    if (header) {

        header.innerHTML = `
            <h2 id="planning-date-heading">
                Planning date
            </h2>

            <div
                class="planning-control__month"
                data-planning-month>
            </div>
        `;
    }


    /*
     * =====================================================
     * WEEK NAVIGATION
     * =====================================================
     */

    const weekNavigation =
        document.createElement(
            'div'
        );

    weekNavigation.className =
        'planning-week-navigation';


    const previousButton =
        document.createElement(
            'button'
        );

    previousButton.type =
        'button';

    previousButton.className =
        'planning-week-button planning-week-button--previous';

    previousButton.innerHTML = `
        <span aria-hidden="true">
            ‹
        </span>

        <span class="planning-week-button__label">
            Previous week
        </span>
    `;


    const weekRange =
        document.createElement(
            'strong'
        );

    weekRange.className =
        'planning-week-navigation__range';

    weekRange.setAttribute(
        'aria-live',
        'polite'
    );


    const nextButton =
        document.createElement(
            'button'
        );

    nextButton.type =
        'button';

    nextButton.className =
        'planning-week-button planning-week-button--next';

    nextButton.innerHTML = `
        <span class="planning-week-button__label">
            Next week
        </span>

        <span aria-hidden="true">
            ›
        </span>
    `;


    weekNavigation.append(
        previousButton,
        weekRange,
        nextButton
    );


    calendar.before(
        weekNavigation
    );


    /*
     * =====================================================
     * SELECTED-DATE STATUS ROW
     * =====================================================
     */

    const status =
        document.createElement(
            'div'
        );

    status.className =
        'planning-control__status';


    const statusDate =
        document.createElement(
            'strong'
        );

    statusDate.className =
        'planning-control__selected-date';

    statusDate.textContent =
        fullDateFormatter.format(
            selectedDate
        );


    const statusItems =
        document.createElement(
            'div'
        );

    statusItems.className =
        'planning-control__status-items';


    const selectedDateAvailable =
        availableDateMap.has(
            selectedIsoDate
        );


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


    const selectedParkStatus =
        getParkStatus(
            selectedDateAvailable
        );


    if (selectedParkStatus) {

        statusItems.append(
            createStatusChip(
                selectedParkStatus.label,
                selectedParkStatus.tone
            )
        );
    }


    status.append(
        statusDate,
        statusItems
    );


    calendar.after(
        status
    );


    section.classList.add(
        'planning-date--enhanced'
    );


    const monthHeading =
        section.querySelector(
            '[data-planning-month]'
        );


    /*
     * =====================================================
     * RENDER ONE WEEK
     * =====================================================
     */

    function renderWeek() {

        const weekStart =
            availableWeekStarts[
                visibleWeekIndex
            ];


        calendar.replaceChildren();


        for (
            let dayOffset = 0;
            dayOffset < 7;
            dayOffset += 1
        ) {

            calendar.append(
                createPlanningDay(
                    addDays(
                        weekStart,
                        dayOffset
                    ),
                    selectedDate,
                    availableDateMap
                )
            );
        }


        weekRange.textContent =
            getWeekRangeLabel(
                weekStart
            );


        if (monthHeading) {

            monthHeading.textContent =
                monthYearFormatter
                    .format(
                        weekStart
                    )
                    .toUpperCase();
        }


        previousButton.disabled =
            visibleWeekIndex === 0;


        nextButton.disabled =
            visibleWeekIndex ===
                availableWeekStarts.length - 1;
    }


    previousButton.addEventListener(
        'click',
        () => {

            if (
                visibleWeekIndex > 0
            ) {

                visibleWeekIndex -= 1;

                renderWeek();
            }
        }
    );


    nextButton.addEventListener(
        'click',
        () => {

            if (
                visibleWeekIndex <
                    availableWeekStarts.length - 1
            ) {

                visibleWeekIndex += 1;

                renderWeek();
            }
        }
    );


    renderWeek();


    /*
     * The main page title already says
     * "Transportation demand".
     *
     * Avoid immediately repeating it again below
     * the date selector.
     */
    const demandHeading =
        document.getElementById(
            'transportation-demand-heading'
        );


    if (demandHeading) {

        demandHeading.textContent =
            'Demand overview';
    }

})();