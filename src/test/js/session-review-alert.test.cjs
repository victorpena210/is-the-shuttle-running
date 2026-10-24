const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('src/main/resources/static/js/components/session-review-alert.js', 'utf8');

test('manager warning refreshes, clears after review, and survives failed requests', async () => {
    const message = { textContent: '' };
    const alert = { hidden: true, dataset: { countUrl: '/driving-records/reviews/count' }, querySelector: () => message };
    let refresh, result = { count: 2 }, fail = false;
    const document = { hidden: false, querySelector: () => alert, addEventListener: () => {} };
    vm.runInNewContext(code, { document, window: { setInterval: (fn, ms) => { refresh = fn; assert.equal(ms, 30000); } },
        fetch: async (url, options) => {
            assert.equal(url, alert.dataset.countUrl); assert.equal(options.cache, 'no-store');
            if (fail) throw new Error('offline');
            return { ok: true, redirected: false, json: async () => result };
        }});
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(alert.hidden, false); assert.match(message.textContent, /2 sessions/);
    fail = true; await refresh(); assert.equal(alert.hidden, false);
    fail = false; result = { count: 0 }; await refresh(); assert.equal(alert.hidden, true);
    result = { count: 1 }; await refresh(); assert.equal(alert.hidden, false); assert.match(message.textContent, /1 session has/);
    result = { count: '<script>' }; await refresh(); assert.match(message.textContent, /1 session has/);
});
