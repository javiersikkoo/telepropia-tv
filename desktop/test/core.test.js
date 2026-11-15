import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import {
  HostPauses,
  LiveAggregator,
  merge,
  normalizeName,
  shouldRetry,
  visibleFailure,
} from '../src/core/aggregation.js';
import { Failure, err, fromError, fromStatus, ok } from '../src/core/failure.js';
import { displayName, endpoint, normalizeHost, rewritePlayable } from '../src/core/hosts.js';
import { createResolverClient } from '../src/core/resolver.js';
import { HostStore } from '../src/core/store.js';

const fixture = (name) => readFileSync(new URL(`../../fixtures/${name}`, import.meta.url), 'utf8');

class MemoryStorage {
  constructor() {
    this.data = new Map();
  }
  getItem(k) {
    return this.data.has(k) ? this.data.get(k) : null;
  }
  setItem(k, v) {
    this.data.set(k, String(v));
  }
}

test('normaliza el host con y sin esquema, sin barra y con puerto explícito', () => {
  assert.equal(normalizeHost('192.168.1.10:3000'), 'http://192.168.1.10:3000');
  assert.equal(normalizeHost('http://192.168.1.10:3000/'), 'http://192.168.1.10:3000');
  assert.equal(normalizeHost('  http://192.168.1.10:3000/api/  '), 'http://192.168.1.10:3000');
  assert.equal(normalizeHost('192.168.1.10'), 'http://192.168.1.10:80');
  assert.equal(normalizeHost(''), null);
  assert.equal(normalizeHost('ftp://192.168.1.10'), null);
});

test('reescribe playableUrl al host configurado sin tocar la query codificada', () => {
  const body = JSON.parse(fixture('resolve.json'));
  const rewritten = rewritePlayable(body.playableUrl, 'http://192.168.1.10:3000');
  assert.equal(
    rewritten,
    'http://192.168.1.10:3000/api/hls?url=https%3A%2F%2Fcdn.example%2Ftoken-abc%2Findex.m3u8' +
      '&referer=https%3A%2F%2Fplayer.example%2F',
  );
  assert.equal(rewritePlayable('http://localhost:3000/api/hls?url=a%26b', 'http://10.0.0.5:8080'),
    'http://10.0.0.5:8080/api/hls?url=a%26b');
});

test('endpoint compone ruta y query', () => {
  assert.equal(endpoint('http://192.168.1.10:3000', 'api/live', { sportType: 1 }),
    'http://192.168.1.10:3000/api/live?sportType=1');
});

test('códigos HTTP y errores de red', () => {
  assert.equal(fromStatus(403), Failure.Expired);
  assert.equal(fromStatus(487), Failure.Expired);
  assert.equal(fromStatus(404), Failure.NotFound);
  assert.equal(fromStatus(502), Failure.Upstream);
  assert.equal(fromStatus(400), Failure.Other);
  assert.equal(fromError(new Error('error sending request: Connection refused (os error 111)')), Failure.Timeout);
  assert.equal(fromError(new Error('dns error: failed to lookup address information')), Failure.NoNetwork);
  assert.equal(fromError({ name: 'TimeoutError', message: 'signal timed out' }), Failure.Timeout);
  assert.equal(fromError(new TypeError('Failed to fetch'), false), Failure.NoNetwork);
});

test('el cliente lee los fixtures, ignora campos de más y reescribe el host', async () => {
  const responses = {
    'http://192.168.1.10:3000/api/live?sportType=1': JSON.stringify({ ...JSON.parse(fixture('live.json')), extra: 1 }),
    'http://192.168.1.10:3000/api/resolve?matchId=4331876&sportType=1': fixture('resolve.json'),
  };
  const client = createResolverClient(async (url) => ({
    status: responses[url] ? 200 : 404,
    text: async () => responses[url] || '{}',
  }));
  const host = { id: 7, baseUrl: 'http://192.168.1.10:3000', label: null, state: 'ACTIVE' };
  const live = await client.live(host, 1);
  assert.deepEqual(live, ok([{ matchId: '4331876', sportId: 1, name: 'Team A vs Team B', sourceHostId: 7,
    sourceName: '192.168.1.10:3000' }]));
  const resolved = await client.resolve(host.baseUrl, '4331876', 1);
  assert.ok(resolved.ok && resolved.value.url.startsWith('http://192.168.1.10:3000/api/hls?url=https%3A%2F%2F'));
  assert.deepEqual(await client.resolve(host.baseUrl, 'nope', 1), err(Failure.NotFound));
});

test('la etiqueta da nombre a la fuente; vacía, host:puerto', () => {
  assert.equal(displayName({ baseUrl: 'http://192.168.1.10:3001', label: 'Sitio A' }), 'Sitio A');
  assert.equal(displayName({ baseUrl: 'http://192.168.1.10:3002', label: null }), '192.168.1.10:3002');
});

test('normaliza nombres de evento', () => {
  assert.equal(normalizeName('Atlético Madrid vs Sevilla'), 'atletico madrid sevilla');
  assert.equal(normalizeName('ATLETICO MADRID - SEVILLA'), 'atletico madrid sevilla');
  assert.equal(normalizeName('Atletico  Madrid v. Sevilla'), 'atletico madrid sevilla');
});

// --- agregación -----------------------------------------------------------------

const first = { id: 1, baseUrl: 'http://192.168.1.10:3001', label: 'Sitio A', state: 'ACTIVE' };
const second = { id: 2, baseUrl: 'http://192.168.1.10:3002', label: null, state: 'ACTIVE' };
const match = (host, id, name) => ({ matchId: id, sportId: 1, name, sourceHostId: host.id, sourceName: displayName(host) });

function harness() {
  const state = { now: 0, responses: new Map(), requested: [] };
  const aggregator = new LiveAggregator(async (host) => {
    state.requested.push(host.id);
    return state.responses.get(host.id);
  }, new HostPauses(1000), () => state.now);
  const snapshot = async (...hosts) => {
    const fetched = await aggregator.fetch(hosts, 1);
    assert.ok(fetched.ok, JSON.stringify(fetched));
    return merge(fetched.value);
  };
  return { state, aggregator, snapshot };
}

test('cierre F2: dos hosts, un evento con dos fuentes; pausar el primero deja la segunda', async () => {
  const { state, snapshot } = harness();
  state.responses.set(1, ok([match(first, '100', 'Real Madrid vs Barcelona')]));
  state.responses.set(2, ok([match(second, '9', 'Real Madrid - Barcelona')]));

  const both = await snapshot(first, second);
  assert.equal(both.events.length, 1);
  assert.deepEqual(both.events[0].liveSources.map((m) => m.sourceHostId), [1, 2]);

  state.requested.length = 0;
  const paused = await snapshot({ ...first, state: 'PAUSED' }, second);
  assert.deepEqual(state.requested, [2]);
  assert.deepEqual(paused.events[0].liveSources.map((m) => m.sourceHostId), [2]);
  assert.equal(paused.degraded, false);
});

test('el orden de ajustes manda dentro del evento', async () => {
  const { state, snapshot } = harness();
  state.responses.set(1, ok([match(first, '100', 'X vs Y')]));
  state.responses.set(2, ok([match(second, '9', 'X - Y')]));
  const reordered = await snapshot(second, first);
  assert.deepEqual(reordered.events[0].liveSources.map((m) => m.sourceHostId), [2, 1]);
});

test('host caído: pausa en memoria, eventos marcados, espera y vuelta', async () => {
  const { state, snapshot } = harness();
  state.responses.set(1, ok([match(first, '1', 'A vs B'), match(first, '2', 'C vs D')]));
  state.responses.set(2, ok([match(second, '5', 'A v B')]));
  await snapshot(first, second);

  state.responses.set(1, err(Failure.Timeout));
  const degraded = await snapshot(first, second);
  assert.equal(degraded.degraded, true);
  const ab = degraded.events.find((e) => normalizeName(e.name) === 'a b');
  assert.deepEqual(ab.liveSources.map((m) => m.sourceHostId), [2]);
  const cd = degraded.events.find((e) => normalizeName(e.name) === 'c d');
  assert.equal(cd.hasLiveSource, false);

  state.requested.length = 0;
  await snapshot(first, second);
  assert.deepEqual(state.requested, [2]);

  state.now += 1000;
  state.requested.length = 0;
  state.responses.set(1, ok([match(first, '1', 'A vs B')]));
  const back = await snapshot(first, second);
  assert.deepEqual(state.requested, [1, 2]);
  assert.equal(back.degraded, false);
});

test('todos caídos y sin lista previa es error con el fallo real; sin activos', async () => {
  const { state, aggregator } = harness();
  state.responses.set(1, err(Failure.Upstream));
  assert.deepEqual(await aggregator.fetch([first], 1), err(Failure.Upstream));
  assert.deepEqual(await aggregator.fetch([{ ...first, state: 'PAUSED' }], 1), err(Failure.NoActiveHost));
  assert.deepEqual(await aggregator.fetch([], 1), err(Failure.NoHost));
});

test('reintento una vez para caducado, 404, 502 y timeout; red cortada se pinta como timeout', () => {
  for (const f of [Failure.Expired, Failure.NotFound, Failure.Upstream, Failure.Timeout]) {
    assert.equal(shouldRetry(f, false), true);
    assert.equal(shouldRetry(f, true), false);
  }
  assert.equal(shouldRetry(Failure.Other, false), false);
  assert.equal(shouldRetry(Failure.NoNetwork, false), false);
  assert.equal(visibleFailure(Failure.Timeout, Failure.NoNetwork), Failure.Timeout);
  assert.equal(visibleFailure(Failure.Expired, Failure.Upstream), Failure.Upstream);
});

test('almacén: alta, duplicado, inválido, orden, etiqueta y estado', () => {
  const store = new HostStore(new MemoryStorage());
  const a = store.add('192.168.1.10:3001', ' Sitio A ');
  assert.equal(a.result, 'added');
  assert.equal(a.host.label, 'Sitio A');
  assert.equal(store.add('http://192.168.1.10:3001/', '').result, 'duplicate');
  assert.equal(store.add('ftp://x', '').result, 'invalid');
  const b = store.add('192.168.1.10:3002/', '');
  assert.equal(b.host.label, null);
  store.move(b.host.id, -1);
  assert.deepEqual(store.list().map((h) => h.id), [b.host.id, a.host.id]);
  store.move(b.host.id, -1);
  assert.deepEqual(store.list().map((h) => h.id), [b.host.id, a.host.id]);
  store.setLabel(b.host.id, '  ');
  assert.equal(store.byId(b.host.id).label, null);
  store.setState(a.host.id, 'PAUSED');
  assert.equal(store.byId(a.host.id).state, 'PAUSED');
  store.remove(a.host.id);
  assert.deepEqual(store.list().map((h) => h.id), [b.host.id]);
  assert.equal(store.sport, 1);
  store.sport = 3;
  assert.equal(store.sport, 3);
});
