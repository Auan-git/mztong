// Run with: node --test tools/test-campus-map.cjs
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');

const html = readFileSync(join(__dirname, '../app/src/main/assets/campus_map.html'), 'utf8');
const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map(match => match[1]);

function page({ height = 600, hasSdk = true, initError = false } = {}) {
    const messages = [];
    const events = {};
    const windowEvents = {};
    const requests = [];
    const container = { clientWidth: 360, clientHeight: height };
    let sdk, pluginReady, map;
    class Map {
        constructor() {
            if (initError) throw new Error('initialization failed');
            map = this;
        }
        on(name, callback) { events[name] = callback; }
        add() {}
        remove() {}
        setZoomAndCenter(zoom, position) { this.position = Array.from(position); }
    }
    const context = {
        AndroidBridge: { onJsMessage: message => messages.push(message) },
        document: {
            getElementById: () => container,
            createElement: () => ({}),
            head: { appendChild: element => { sdk = element; } },
        },
        addEventListener: (name, callback) => { windowEvents[name] = callback; },
    };
    if (hasSdk) context.AMap = {
        Map,
        Marker: class {},
        plugin: (_, callback) => { pluginReady = callback; },
        Geocoder: class {
            getLocation(address, callback) { requests.push({ address, callback }); }
        },
    };
    context.window = context;
    vm.createContext(context);
    scripts.forEach(script => vm.runInContext(script, context));
    return { context, messages, events, windowEvents, container, requests,
        sdk, get map() { return map; }, loadGeocoder: () => pluginReady() };
}

test('SDK initialization does not hide loading until map tiles complete', () => {
    const p = page();
    p.sdk.onload();
    assert.deepEqual(p.messages, ['initialized']);
    p.events.complete();
    p.events.complete();
    assert.equal(p.messages.filter(message => message === 'ready').length, 1);
});

test('failed SDK request reports failure without claiming readiness', () => {
    const p = page({ hasSdk: false });
    p.sdk.onerror();
    assert.match(p.messages[0], /加载失败/);
    assert.ok(!p.messages.includes('ready'));
});

test('missing SDK global and constructor errors never report readiness', () => {
    for (const options of [{ hasSdk: false }, { initError: true }]) {
        const p = page(options);
        p.sdk.onload();
        assert.ok(p.messages.some(message => message.includes('失败')));
        assert.ok(!p.messages.includes('ready'));
    }
});

test('zero-height viewport defers map creation until layout is usable', () => {
    const p = page({ height: 0 });
    p.sdk.onload();
    assert.equal(p.map, undefined);
    assert.deepEqual(p.messages, []);
    p.container.clientHeight = 600;
    p.windowEvents.resize();
    assert.ok(p.map);
    assert.deepEqual(p.messages, ['initialized']);
});

test('late geocoding responses cannot switch back to an old campus', () => {
    const p = page();
    p.sdk.onload();
    p.loadGeocoder();
    p.context.setCampus('大兴校区', '大兴地址', 116.349, 39.7545);
    p.context.setCampus('燕郊校区', '燕郊地址', 116.7902, 39.9524);
    assert.deepEqual(p.map.position, [116.7902, 39.9524]);
    const result = (lng, lat) => ({ geocodes: [{ location: { lng, lat } }] });
    p.requests[1].callback('complete', result(116.8, 39.95));
    p.requests[0].callback('complete', result(116.3, 39.75));
    assert.deepEqual(p.map.position, [116.8, 39.95]);
    assert.equal(p.messages.at(-1), '已定位 燕郊校区');
});

test('geocoding failure keeps the selected campus usable with fallback coordinates', () => {
    const p = page();
    p.sdk.onload();
    p.loadGeocoder();
    p.context.setCampus('大兴校区', '大兴地址', 116.349, 39.7545);
    p.requests[0].callback('error', null);
    assert.deepEqual(p.map.position, [116.349, 39.7545]);
    assert.match(p.messages.at(-1), /使用预设坐标/);
    p.context.fitCampus();
    assert.equal(p.requests.at(-1).address, '大兴地址');
});

test('verified campus coordinates survive plugin loading and return-to-campus', () => {
    const p = page();
    p.sdk.onload();
    const campus = [116.796135, 39.954483];
    p.context.setCampus('民政职业大学燕郊校区', '河北省三河市燕郊开发区燕灵路454号', ...campus, false);
    p.loadGeocoder();
    assert.equal(p.requests.length, 0);
    assert.deepEqual(p.map.position, campus);
    p.map.setZoomAndCenter(14, [116.814361, 39.953335]);
    p.context.fitCampus();
    assert.deepEqual(p.map.position, campus);
    assert.equal(p.requests.length, 0);
    assert.equal(p.messages.at(-1), '已定位 民政职业大学燕郊校区');
});

test('an old address lookup cannot replace a verified campus after switching', () => {
    const p = page();
    p.sdk.onload();
    p.loadGeocoder();
    p.context.setCampus('大兴校区', '大兴地址', 116.349, 39.7545);
    p.context.setCampus('民政职业大学燕郊校区', '燕灵路454号', 116.796135, 39.954483, false);
    p.requests[0].callback('complete', { geocodes: [{ location: { lng: 116.375642, lat: 39.75213 } }] });
    assert.deepEqual(p.map.position, [116.796135, 39.954483]);
    assert.equal(p.messages.at(-1), '已定位 民政职业大学燕郊校区');
});
