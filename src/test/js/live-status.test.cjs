const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('src/main/resources/static/js/pages/live-status.js', 'utf8');

test('live status refreshes only the card, reports failures, and recovers without clearing forms', async () => {
    const note = { textContent: '', hidden: true };
    const card = { current: 'stopped', replaceChildren(updated) { this.current = updated; } };
    const preview = {
        dataset: { refreshUrl: '/app/live-status' },
        querySelector: selector => selector === '[data-live-rider-card]' ? card : note
    };
    let refresh, onVisible, calls = 0, fail = false, redirected = false, updated = 'running: Alex', release;
    const document = { hidden: false, querySelector: () => preview,
        addEventListener: (event, fn) => { assert.equal(event, 'visibilitychange'); onVisible = fn; } };
    vm.runInNewContext(code, { document, Date, AbortSignal,
        window: { setInterval: (fn, ms) => { refresh = fn; assert.equal(ms, 20000); } },
        DOMParser: class { parseFromString() { return { querySelector: selector => {
            assert.equal(selector, '[data-live-rider-card] .rider-status-card'); return updated;
        }}; } },
        fetch: async (url, options) => {
            calls++;
            assert.equal(url, '/app/live-status');
            assert.equal(options.cache, 'no-store');
            assert.equal(options.credentials, 'same-origin');
            assert.ok(options.signal);
            if (release) await release;
            if (fail) throw new Error('offline');
            return { ok: true, redirected, text: async () => '<html>manager forms and status</html>' };
        }
    });
    await refresh();
    assert.equal(card.current, 'running: Alex');
    assert.equal(note.textContent, ''); assert.equal(note.hidden, true);
    fail = true; await refresh();
    assert.equal(card.current, 'running: Alex');
    assert.match(note.textContent, /out of date/);
    assert.equal(note.hidden, false);
    fail = false; redirected = true; updated = 'login'; await refresh();
    assert.equal(card.current, 'running: Alex');
    redirected = false; updated = null; await refresh();
    assert.equal(card.current, 'running: Alex');
    updated = 'stopped'; await refresh();
    assert.equal(card.current, 'stopped');
    assert.equal(note.textContent, ''); assert.equal(note.hidden, true);
    document.hidden = true;
    const before = calls; await refresh(); assert.equal(calls, before);
    document.hidden = false; updated = 'running: Blair'; onVisible();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(card.current, 'running: Blair');
    let resolveRequest; release = new Promise(resolve => { resolveRequest = resolve; });
    const pending = refresh(); await refresh(); assert.equal(calls, before + 2);
    resolveRequest(); await pending;
});
