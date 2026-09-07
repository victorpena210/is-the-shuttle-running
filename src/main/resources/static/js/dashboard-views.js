(function () {
    'use strict';

    const validViews = new Set([
        'overview',
        'schedules',
        'demand',
        'coverage'
    ]);


    /*
     * Copy shown in the main page header for each dashboard section.
     *
     * The HTML keeps "Overview" as the no-JavaScript fallback.
     * When a dashboard section becomes active, RoutePulse updates
     * the heading and description to match what the manager is viewing.
     */
    const viewCopy = {

        overview: {
            heading: 'Overview',
            description:
                'Review today’s transportation demand, driver coverage, and shuttle operations.',
            documentTitle: 'Overview | RoutePulse',
            showPlanningDate: true
        },

        schedules: {
            heading: 'Schedules',
            description:
                'Upload Dayforce schedules and review the imports RoutePulse uses for shuttle planning.',
            documentTitle: 'Schedules | RoutePulse',
            showPlanningDate: false
        },

        demand: {
            heading: 'Transportation demand',
            description:
                'Review arrivals, midday turnover, evening pickups, and recommended shuttle windows for the selected planning day.',
            documentTitle: 'Transportation Demand | RoutePulse',
            showPlanningDate: true
        },

        coverage: {
            heading: 'Driver coverage',
            description:
                'Assign drivers to the morning and afternoon shuttle shifts for the selected date.',
            documentTitle: 'Driver Coverage | RoutePulse',
            showPlanningDate: true
        }

    };


    /*
     * Determine which dashboard view should be shown
     * from the URL hash.
     *
     * Examples:
     *
     * /app#overview
     * /app#schedules
     * /app#demand
     * /app#coverage
     */
    function currentView() {

        const requestedView = window.location.hash
            .replace('#', '')
            .trim()
            .toLowerCase();

        return validViews.has(requestedView)
            ? requestedView
            : 'overview';
    }


    /*
     * Update the large page heading and description
     * so they match the selected dashboard section.
     */
    function updatePageHeader(view) {

        const copy =
            viewCopy[view] || viewCopy.overview;

        const pageHeader =
            document.querySelector('.page-header');

        if (!pageHeader) {
            return;
        }


        const heading =
            pageHeader.querySelector('h1');

        const description =
            pageHeader.querySelector('h1 + p');

        const planningDate =
            pageHeader.querySelector('.page-date');


        if (heading) {
            heading.textContent = copy.heading;
        }


        if (description) {
            description.textContent = copy.description;
        }


        /*
         * The schedule-import screen is not tied to one
         * planning date, so hiding "Planning for..." there
         * makes the page clearer.
         *
         * Transportation demand, driver coverage, and overview
         * can continue showing the selected date.
         */
        if (planningDate) {
            planningDate.hidden =
                !copy.showPlanningDate;
        }


        /*
         * Keep the browser tab title consistent
         * with the dashboard section.
         */
        document.title =
            copy.documentTitle;
    }


    /*
     * Display only the dashboard sections that belong
     * to the selected view.
     */
    function showView(view) {

        document
            .querySelectorAll('[data-dashboard-section]')
            .forEach(function (section) {

                section.hidden =
                    section.dataset.dashboardSection !== view;

            });


        /*
         * Transportation demand and driver coverage both use
         * information inside the generated planning area.
         */
        const planContainer =
            document.querySelector('[data-plan-container]');

        if (planContainer) {

            planContainer.hidden =
                view !== 'demand' &&
                view !== 'coverage';

        }


        /*
         * Update the active state in the sidebar navigation.
         */
        document
            .querySelectorAll('[data-dashboard-link]')
            .forEach(function (link) {

                if (link.dataset.dashboardLink === view) {

                    link.setAttribute(
                        'aria-current',
                        'page'
                    );

                } else {

                    link.removeAttribute(
                        'aria-current'
                    );

                }

            });


        /*
         * Store the current dashboard view on the body.
         * Existing CSS can continue using this value.
         */
        document.body.dataset.dashboardView =
            view;


        /*
         * Make the page heading match the selected section.
         */
        updatePageHeader(view);
    }


    /*
     * Recalculate the dashboard whenever the hash changes.
     */
    function updateView() {

        showView(
            currentView()
        );

    }


    window.addEventListener(
        'hashchange',
        updateView
    );


    /*
     * Set the correct view immediately when the page loads.
     */
    updateView();

})();