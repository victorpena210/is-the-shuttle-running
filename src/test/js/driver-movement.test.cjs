const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/pages/driver-movement.js'), 'utf8');
const flush = async () => { for (let n = 0; n < 12; n++) await new Promise(resolve => setImmediate(resolve)); };

// Browser API fakes exercise the production script without a network, browser, or location permission.
async function setup() {
    const elements = new Map(), intervals = [], posts = [];
    function element(id) {
        if (!elements.has(id)) elements.set(id, {id, hidden: false, disabled: false, textContent: '', dataset: {}, handlers: {},
            addEventListener(event, handler) { this.handlers[event] = handler; },
            closest() { return element('confirmation-section'); },
            append(child) { elements.set(child.id, child); }});
        return elements.get(id);
    }
    const responses = ['WAITING', 'BREAK', 'END'].map(response => {
        const button = element(response); button.dataset.movementResponse = response; return button;
    });
    const panel = element('movement-monitor'); panel.dataset.sessionId = '10';
    panel.querySelectorAll = () => responses;
    const state = {sessionId:10, active:true, enabled:false, state:'DISABLED', routeConfigured:true,
        lastConfirmedAt:new Date().toISOString()};
    const storage = new Map();
    let mode = 'success';
    const context = {
        console, Date, Promise, AbortController, setTimeout, clearTimeout,
        setInterval(fn) { intervals.push(fn); return intervals.length; },
        clearInterval(id) { intervals[id - 1] = () => {}; },
        sessionStorage: {getItem:key => storage.get(key), setItem:(key,value) => storage.set(key,value)},
        document: {hidden:false, getElementById:id => elements.get(id) || (id === 'movement-refresh' ? null : element(id)),
            querySelector:selector => ({content:selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'test-token'}),
            createElement:() => element('new-link'), addEventListener() {}},
        navigator: {geolocation: {getCurrentPosition(success, error) {
            if (mode === 'denied') error({code:1});
            else success({timestamp:Date.now(), coords:{latitude:29.405, longitude:-98.5, accuracy:8, speed:0}});
        }}},
        async fetch(url, options) {
            if (options.method === 'POST') {
                assert.equal(options.headers['X-CSRF-TOKEN'], 'test-token');
                const body = JSON.parse(options.body); posts.push({url,body});
                if (url.endsWith('/enable')) Object.assign(state, {enabled:true, state:'UNCONFIRMED'});
                if (url.endsWith('/location')) state.state = 'MONITORING';
                if (url.endsWith('/signal-lost')) state.state = 'UNCONFIRMED';
                if (url.endsWith('/response')) {
                    if (body.response === 'WAITING') state.state = 'WAITING';
                    else Object.assign(state, {active:false, state:'ENDED', endReason:body.response === 'BREAK' ? 'PAUSE' : 'COMPLETED'});
                }
            }
            const data = {...state};
            return {ok:true, redirected:false, headers:{get:() => 'application/json'}, json:async () => data};
        }
    };
    context.window = {isSecureContext:true};
    vm.runInNewContext(source, context); await flush();
    return {state, posts, element, intervals, setMode:value => mode = value,
        async click(id) { await element(id).handlers.click(); await flush(); }};
}

test('enable sends a fresh GPS fix and CSRF; waiting keeps controls active', async () => {
    const f = await setup(); await f.click('movement-enable');
    const fix = f.posts.find(p => p.url.endsWith('/location')).body;
    assert.equal(fix.latitude,29.405); assert.equal(fix.accuracy,8); assert(fix.capturedAt);
    assert.match(f.element('movement-status').textContent,/Receiving reliable/);
    await f.click('WAITING'); assert.match(f.element('movement-status').textContent,/Waiting at pickup/);
    assert.equal(f.element('movement-responses').hidden,false);
});
test('prompt shows deadline; break closes UI and stops later telemetry', async () => {
    const f = await setup(); await f.click('movement-enable');
    Object.assign(f.state,{state:'PROMPT',timeoutAt:new Date(Date.now()+600000).toISOString()});
    await f.intervals[0](); assert.equal(f.element('movement-prompt').hidden,false);
    assert.match(f.element('movement-deadline').textContent,/close for review/);
    await f.click('BREAK'); assert.match(f.element('movement-status').textContent,/On break/);
    assert.equal(f.element('movement-responses').hidden,true);
    const count=f.posts.length; await f.intervals[1](); await flush(); assert.equal(f.posts.length,count);
});
test('denied permission is reported; resume and stop sharing work', async () => {
    const f=await setup(); f.setMode('denied'); await f.click('movement-enable');
    assert.match(f.element('movement-connection').textContent,/denied/);
    assert(f.posts.some(p=>p.url.endsWith('/signal-lost')));
    f.setMode('success'); await f.click('movement-enable');
    assert.match(f.element('movement-status').textContent,/Receiving reliable/);
    await f.click('movement-stop'); assert.match(f.element('movement-status').textContent,/Status unconfirmed/);
    assert.equal(f.element('movement-enable').hidden,false);
});
test('end and automatic timeout both remove active controls', async () => {
    const f=await setup(); await f.click('movement-enable'); await f.click('END');
    assert.equal(f.state.endReason,'COMPLETED'); assert.equal(f.element('movement-enable').hidden,true);
    const timeout=await setup(); Object.assign(timeout.state,{active:false,state:'ENDED',endReason:'INACTIVITY_TIMEOUT'});
    await timeout.intervals[0](); assert.match(timeout.element('movement-status').textContent,/needs review/);
    assert.equal(timeout.element('movement-enable').hidden,true);
});
