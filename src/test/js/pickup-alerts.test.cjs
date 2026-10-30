const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('src/main/resources/static/js/pages/pickup-alerts.js', 'utf8');

test('pickup polling preserves focused pickup details and rejects login or failed responses', async () => {
    let refresh, visible, calls = 0, focused = false, redirected = false, fail = false, next = 'updated', replacement;
    const message = {textContent: ''};
    const panel = {dataset: {pickupPoll: '/driver/pickup-assistance/upcoming'},
        contains: () => focused, querySelector: () => message, replaceWith: item => {replacement = item;}};
    const document = {hidden: false, activeElement: {}, querySelector: () => panel,
        addEventListener: (name, handler) => {assert.equal(name, 'visibilitychange'); visible = handler;}};
    vm.runInNewContext(code, {document, AbortSignal,
        setInterval: (handler, ms) => {refresh = handler; assert.equal(ms, 60000);},
        DOMParser: class {parseFromString() {return {querySelector: () => next};}},
        fetch: async (url, options) => {
            calls++; assert.equal(url, panel.dataset.pickupPoll); assert.equal(options.cache, 'no-store');
            assert.equal(options.credentials, 'same-origin'); assert.ok(options.signal);
            if (fail) throw new Error('offline');
            return {ok: true, redirected, text: async () => '<section></section>'};
        }
    });
    await refresh(); assert.equal(replacement, 'updated');
    focused = true; await refresh(); assert.equal(calls, 1);
    focused = false; document.hidden = true; await refresh(); assert.equal(calls, 1);
    document.hidden = false; redirected = true; next = 'login'; await refresh();
    assert.equal(replacement, 'updated'); assert.match(message.textContent, /updates unavailable/);
    redirected = false; fail = true; await refresh(); assert.equal(replacement, 'updated');
    fail = false; next = null; await refresh(); assert.equal(replacement, 'updated');
    next = 'latest'; visible(); await new Promise(resolve => setImmediate(resolve)); assert.equal(replacement, 'latest');
});
