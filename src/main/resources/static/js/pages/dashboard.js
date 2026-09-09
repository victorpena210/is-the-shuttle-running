'use strict';

import {
    initializeAttentionCenter
} from '../components/attention-center.js';

import {
    initializeDashboardViews
} from '../components/dashboard-views.js';

import {
    initializeDriverCoverage
} from '../components/driver-coverage.js';

import {
    initializePlanningCalendar
} from '../components/planning-calendar.js';

function initializeDashboard() {
    initializeDashboardViews();
    initializeAttentionCenter();
    initializePlanningCalendar();
    initializeDriverCoverage();
}

if (document.readyState === 'loading') {
    document.addEventListener(
        'DOMContentLoaded',
        initializeDashboard,
        { once: true }
    );
} else {
    initializeDashboard();
}
