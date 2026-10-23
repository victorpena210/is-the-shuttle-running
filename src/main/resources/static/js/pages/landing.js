/** Illustrative status switcher only; never represents live organization data. */
const preview = document.querySelector('.status-preview');
const controls = document.querySelector('[data-preview-controls]');
const states = {
    running: {
        label: 'Service running',
        heading: 'Yes.\nWe’re moving.',
        detail: 'The shuttle is running. Check your organization’s pickup locations before heading out.'
    },
    paused: {
        label: 'Temporarily paused',
        heading: 'A short\nbreather.',
        detail: 'Service is temporarily paused. Check your organization’s status page for operational updates.'
    },
    stopped: {
        label: 'Not running',
        heading: 'Off duty.\nStay in the loop.',
        detail: 'The shuttle is not running right now. Check back for an update or contact your shuttle coordinator.'
    }
};
if (preview && controls) {
    controls.hidden = false;
    controls.addEventListener('click', event => {
        const button = event.target.closest('button[data-preview]');
        if (!button || !controls.contains(button)) return;
        const state = states[button.dataset.preview];
        if (!state) return;
        preview.dataset.previewState = button.dataset.preview;
        preview.querySelector('[data-preview-label]').textContent = state.label;
        preview.querySelector('[data-preview-heading]').textContent = state.heading;
        preview.querySelector('[data-preview-detail]').textContent = state.detail;
        controls.querySelectorAll('button').forEach(item => {
            item.setAttribute('aria-pressed', String(item === button));
        });
    });
}
