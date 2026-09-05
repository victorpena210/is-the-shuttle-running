(function () {
    'use strict';

    const validViews = new Set([
        'overview',
        'schedules',
        'demand',
        'coverage'
    ]);

    function currentView() {
        const requestedView = window.location.hash
            .replace('#', '')
            .trim()
            .toLowerCase();

        return validViews.has(requestedView)
            ? requestedView
            : 'overview';
    }

    function showView(view) {
        document
            .querySelectorAll('[data-dashboard-section]')
            .forEach(function (section) {
                section.hidden =
                    section.dataset.dashboardSection !== view;
            });

        const planContainer =
            document.querySelector('[data-plan-container]');

        if (planContainer) {
            planContainer.hidden =
                view !== 'demand' && view !== 'coverage';
        }

        document
            .querySelectorAll('[data-dashboard-link]')
            .forEach(function (link) {
                if (link.dataset.dashboardLink === view) {
                    link.setAttribute('aria-current', 'page');
                } else {
                    link.removeAttribute('aria-current');
                }
            });

        document.body.dataset.dashboardView = view;
    }

    function updateView() {
        showView(currentView());
    }

    window.addEventListener('hashchange', updateView);
    updateView();
})();
