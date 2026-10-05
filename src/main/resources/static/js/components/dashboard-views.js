'use strict';

const validViews = new Set([
    'overview',
    'live-status',
    'schedules',
    'demand',
    'coverage'
]);

const viewCopy = {
    overview: {
        heading: 'Overview',
        description:
            'Review today’s transportation demand, driver coverage, and shuttle operations.',
        documentTitle: 'Overview | Is the Shuttle Running',
        showPlanningDate: true
    },

    'live-status': {
        heading: 'Live shuttle status',
        description:
            'Review what riders see right now and manage public service updates.',
        documentTitle: 'Live Shuttle Status | Is the Shuttle Running',
        showPlanningDate: false
    },

    schedules: {
        heading: 'Schedules',
        description:
            'Upload Dayforce schedules and review the imports Is the Shuttle Running uses for shuttle planning.',
        documentTitle: 'Schedules | Is the Shuttle Running',
        showPlanningDate: false
    },

    demand: {
        heading: 'Transportation demand',
        description:
            'Review arrivals, midday turnover, evening pickups, service verification, and recommended shuttle windows for the selected planning day.',
        documentTitle: 'Transportation Demand | Is the Shuttle Running',
        showPlanningDate: true
    },

    coverage: {
        heading: 'Driver coverage',
        description:
            'Assign drivers to the morning and afternoon shuttle shifts for the selected date.',
        documentTitle: 'Driver Coverage | Is the Shuttle Running',
        showPlanningDate: true
    }
};

function currentView() {
    const requestedView = window.location.hash
        .replace('#', '')
        .trim()
        .toLowerCase();

    return validViews.has(requestedView)
        ? requestedView
        : 'overview';
}

function updatePageHeader(view) {
    const copy = viewCopy[view] || viewCopy.overview;
    const pageHeader = document.querySelector('.page-header');

    if (!pageHeader) {
        return;
    }

    const heading = pageHeader.querySelector('h1');
    const description = pageHeader.querySelector('h1 + p');
    const planningDate = pageHeader.querySelector('.page-date');

    if (heading) {
        heading.textContent = copy.heading;
    }

    if (description) {
        description.textContent = copy.description;
    }

    if (planningDate) {
        planningDate.hidden = !copy.showPlanningDate;
    }

    document.title = copy.documentTitle;
}

function showView(view) {
    document
        .querySelectorAll('[data-dashboard-section]')
        .forEach(section => {
            section.hidden = section.dataset.dashboardSection !== view;
        });

    const planContainer = document.querySelector('[data-plan-container]');

    if (planContainer) {
        planContainer.hidden = view !== 'demand' && view !== 'coverage';
    }

    document
        .querySelectorAll('[data-dashboard-link]')
        .forEach(link => {
            if (link.dataset.dashboardLink === view) {
                link.setAttribute('aria-current', 'page');
            } else {
                link.removeAttribute('aria-current');
            }
        });

    document.body.dataset.dashboardView = view;
    updatePageHeader(view);
}

/**
 * Initialize dashboard hash navigation and keep the page heading,
 * section visibility, and active sidebar item in sync.
 */
export function initializeDashboardViews() {
    function updateView() {
        showView(currentView());
    }

    window.addEventListener('hashchange', updateView);
    updateView();
}
