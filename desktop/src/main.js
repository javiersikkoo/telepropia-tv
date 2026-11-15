import './style.css';
import { HostPauses, LiveAggregator, merge, shouldRetry, visibleFailure } from './core/aggregation.js';
import { Failure, message } from './core/failure.js';
import { displayName } from './core/hosts.js';
import { createResolverClient } from './core/resolver.js';
import { HostStore, PROBE_SPORT_ID, SPORTS } from './core/store.js';
import { createPlayer } from './player.js';
import { isOnline, request, setFullscreen } from './platform/http.js';

const $ = (id) => document.getElementById(id);

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key === 'dataset') Object.assign(node.dataset, value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  // Siempre texto, nunca HTML: los nombres vienen del resolver.
  for (const child of children.flat()) {
    if (child !== null && child !== undefined) node.append(child instanceof Node ? child : String(child));
  }
  return node;
}

const store = new HostStore(window.localStorage);
const client = createResolverClient(request, isOnline);
const pauses = new HostPauses();
const aggregator = new LiveAggregator((host, sport) => client.live(host, sport), pauses);
const timeFormat = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' });

// ---------------------------------------------------------------------------
// Navegación: una pila de vistas; Esc y "Volver" quitan una.

const VIEWS = ['live', 'sources', 'player', 'settings'];
const stack = [];
let current = null;
const onEnter = {};
const onLeave = {};

function showView(name) {
  for (const view of VIEWS) $(`view-${view}`).hidden = view !== name;
  current = name;
  onEnter[name]?.();
}

function go(name) {
  if (current) {
    onLeave[current]?.();
    stack.push(current);
  }
  showView(name);
}

function back() {
  if (stack.length === 0) return;
  onLeave[current]?.();
  showView(stack.pop());
}

document.querySelectorAll('.back').forEach((button) => button.addEventListener('click', back));

// ---------------------------------------------------------------------------
// Partidos en vivo

const REFRESH_INTERVAL_MS = 60_000;
// Solo etiqueta, no ordena. Vive en memoria: se pierde al cambiar de deporte y al salir de la pantalla.
const firstSeen = new Map();
let liveState = { kind: 'loading' };
let refreshSeq = 0;
let refreshTimer = null;

function renderSports() {
  const nav = $('sports');
  nav.replaceChildren(
    ...SPORTS.map((sport) =>
      el('button', {
        class: 'chip',
        'aria-pressed': String(sport.id === store.sport),
        onclick: () => {
          store.sport = sport.id;
          firstSeen.clear();
          renderSports();
          refresh(true);
        },
      }, sport.label),
    ),
  );
}

function sourcesText(event) {
  const count = event.liveSources.length;
  if (!event.hasLiveSource) return 'Sin fuentes vivas';
  return count === 1 ? '1 fuente' : `${count} fuentes`;
}

function renderLive(state) {
  liveState = state;
  $('live-loading').hidden = state.kind !== 'loading';
  $('events').hidden = state.kind !== 'content';
  $('live-message').hidden = state.kind !== 'empty' && state.kind !== 'error';
  $('live-retry').hidden = state.kind !== 'error';

  const degraded = state.degraded;
  $('degraded').hidden = !degraded;
  if (degraded) {
    $('degraded').textContent =
      `Modo degradado: ${degraded.failing} de ${degraded.active} hosts sin respuesta. ` +
      'Sus eventos se marcan y no se abren.';
  }

  if (state.kind === 'empty') {
    const sport = SPORTS.find((s) => s.id === store.sport)?.label ?? '';
    $('live-message-text').textContent = `No hay nada de ${sport} en vivo ahora mismo.\nSe vuelve a mirar cada minuto.`;
  }
  if (state.kind === 'error') {
    $('live-message-text').textContent = message(state.failure, 'No se ha podido cargar la lista');
  }
  if (state.kind !== 'content') return;

  const focusedKey = document.activeElement?.dataset?.key;
  $('events').replaceChildren(
    ...state.rows.map(({ event, detectedAt }) => {
      const time = timeFormat.format(new Date(detectedAt));
      const count = sourcesText(event);
      return el('li', {},
        el('button', {
          class: event.hasLiveSource ? 'row event' : 'row event dead',
          dataset: { key: event.key },
          'aria-disabled': event.hasLiveSource ? null : 'true',
          'aria-label': `${event.name}, ${count}, detectado a las ${time}`,
          onclick: () => event.hasLiveSource && openSources(event),
        },
        el('span', { class: 'name' }, event.name),
        el('span', { class: 'count' }, count),
        el('span', { class: 'time' }, `Detectado a las ${time}`)),
      );
    }),
  );
  if (focusedKey) $('events').querySelector(`[data-key="${CSS.escape(focusedKey)}"]`)?.focus();
}

async function refresh(showLoading = false) {
  const seq = ++refreshSeq;
  if (showLoading || liveState.kind === 'error') renderLive({ kind: 'loading' });
  $('refreshing').hidden = liveState.kind === 'loading';

  const fetched = await aggregator.fetch(store.list(), store.sport);
  if (seq !== refreshSeq) return;
  $('refreshing').hidden = true;

  if (!fetched.ok) {
    renderLive({ kind: 'error', failure: fetched.failure });
    return;
  }
  const snapshot = merge(fetched.value);
  const degraded = snapshot.degraded
    ? { failing: snapshot.failingHosts.length, active: snapshot.activeHostCount }
    : null;
  if (snapshot.events.length === 0) {
    firstSeen.clear();
    renderLive({ kind: 'empty', degraded });
    return;
  }
  const now = Date.now();
  // Un evento que desaparece y vuelve cuenta como nuevo.
  const keys = new Set(snapshot.events.map((e) => e.key));
  for (const key of [...firstSeen.keys()]) if (!keys.has(key)) firstSeen.delete(key);
  const rows = snapshot.events.map((event) => {
    if (!firstSeen.has(event.key)) firstSeen.set(event.key, now);
    return { event, detectedAt: firstSeen.get(event.key) };
  });
  renderLive({ kind: 'content', rows, degraded });
}

onEnter.live = () => {
  renderSports();
  refresh(liveState.kind !== 'content');
  clearInterval(refreshTimer);
  refreshTimer = setInterval(() => {
    if (document.visibilityState === 'visible') refresh();
  }, REFRESH_INTERVAL_MS);
};

onLeave.live = () => {
  clearInterval(refreshTimer);
  refreshSeq++;
  firstSeen.clear();
};

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && current === 'live') refresh();
});

$('live-retry').addEventListener('click', () => refresh(true));
$('open-settings').addEventListener('click', () => go('settings'));

// ---------------------------------------------------------------------------
// Fuentes de un evento: solo las vivas; contra un host caído nunca se resuelve.

function openSources(event) {
  $('sources-title').textContent = event.name;
  $('sources').replaceChildren(
    ...event.liveSources.map((match, index) =>
      el('li', {},
        el('button', {
          class: 'row source',
          'aria-label': `Fuente ${index + 1}, ${match.sourceName}. Pulsa para reproducir.`,
          onclick: () => openPlayer(event, match),
        },
        el('span', { class: 'name' }, `Fuente ${index + 1} · ${match.sourceName}`)),
      ),
    ),
  );
  go('sources');
  $('sources').querySelector('button')?.focus();
}

// ---------------------------------------------------------------------------
// Reproductor

const video = $('video');
let playerToken = 0;
let retried = false;
let playing = null;
let idleTimer = null;

const player = createPlayer(video, {
  onPlaying: () => setStatus(null),
  onBuffering: (busy) => busy && $('player-error').hidden && setStatus('Cargando…'),
  onFailure: (failure) => {
    if (shouldRetry(failure, retried)) {
      retried = true;
      player.stop();
      loadSource(failure);
    } else {
      showPlayerError(failure);
    }
  },
  onEnded: () => showPlayerError(Failure.NotFound),
});

function setStatus(text) {
  $('player-status').hidden = !text;
  $('player-status-text').textContent = text || '';
}

function showPlayerError(failure) {
  player.stop();
  setStatus(null);
  $('player-error-text').textContent = message(failure);
  $('player-error').hidden = false;
  $('player-retry').focus();
}

// PlaybackSpec no se guarda en ningún sitio: cada play resuelve, y siempre contra el host de la fuente.
async function loadSource(trigger) {
  const token = ++playerToken;
  $('player-error').hidden = true;
  setStatus(trigger ? 'Reintentando con un enlace nuevo…' : 'Buscando el enlace…');
  const { match } = playing;
  const host = store.byId(match.sourceHostId);
  const outcome = host
    ? await client.resolve(host.baseUrl, match.matchId, match.sportId)
    : { ok: false, failure: Failure.NoHost };
  if (token !== playerToken || current !== 'player') return;
  if (!outcome.ok) {
    showPlayerError(visibleFailure(trigger, outcome.failure));
    return;
  }
  player.play(outcome.value.url);
}

function openPlayer(event, match) {
  playing = { event, match };
  retried = false;
  $('player-title').textContent = event.name;
  video.setAttribute('aria-label', `Reproduciendo ${event.name}`);
  go('player');
  loadSource(null);
}

onLeave.player = () => {
  playerToken++;
  player.stop();
  setFullscreen($('view-player'), false).catch(() => {});
  $('view-player').classList.remove('idle');
};

$('player-retry').addEventListener('click', () => {
  retried = false;
  loadSource(null);
});

let fullscreen = false;
async function toggleFullscreen() {
  fullscreen = !fullscreen;
  await setFullscreen($('view-player'), fullscreen).catch(() => {
    fullscreen = false;
  });
}

video.addEventListener('dblclick', toggleFullscreen);
document.addEventListener('fullscreenchange', () => {
  fullscreen = Boolean(document.fullscreenElement);
});

$('view-player').addEventListener('mousemove', () => {
  $('view-player').classList.remove('idle');
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => $('view-player').classList.add('idle'), 3000);
});

// ---------------------------------------------------------------------------
// Ajustes

const probes = new Map();
let editing = null;

function stateText(host) {
  if (host.state !== 'ACTIVE') return ['En pausa', true];
  if (pauses.failureOf(host.id)) return ['Sin respuesta · fuera de la lista', true];
  return ['Activo', false];
}

function probeText(id) {
  const probe = probes.get(id);
  if (!probe) return '';
  if (probe.running) return 'Probando…';
  return probe.ok ? `OK · ${probe.latencyMs} ms` : message(probe.failure, 'No se ha podido cargar la lista');
}

async function probe(host) {
  if (probes.get(host.id)?.running) return;
  probes.set(host.id, { running: true });
  renderHosts();
  const started = performance.now();
  const outcome = await client.live(host, PROBE_SPORT_ID);
  const latencyMs = Math.round(performance.now() - started);
  if (outcome.ok) pauses.resume(host.id);
  else if (store.byId(host.id)?.state === 'ACTIVE') pauses.pause(host.id, outcome.failure, Date.now());
  probes.set(host.id, { running: false, ok: outcome.ok, failure: outcome.failure, latencyMs });
  renderHosts();
}

function hostRow(host, index, count) {
  const name = displayName(host);
  const [state, warn] = stateText(host);
  const action = (label, fn, extra = {}) =>
    el('button', { class: 'btn', ...extra, onclick: fn }, label);

  const who = editing === host.id
    ? el('form', {
      class: 'edit',
      onsubmit: (e) => {
        e.preventDefault();
        store.setLabel(host.id, e.target.elements.label.value);
        editing = null;
        renderHosts();
      },
    },
    el('input', { name: 'label', value: host.label || '', placeholder: name, 'aria-label': `Etiqueta de ${name}` }),
    el('button', { class: 'btn primary', type: 'submit' }, 'Guardar'),
    el('button', { class: 'btn', type: 'button', onclick: () => { editing = null; renderHosts(); } }, 'Cancelar'))
    : el('div', {},
      el('div', { class: 'label' }, name),
      host.label ? el('div', { class: 'url' }, host.baseUrl) : null);

  return el('li', { class: 'host', dataset: { id: String(host.id) } },
    el('div', { class: 'top' },
      el('span', { class: 'pos' }, `${index + 1}.`),
      el('div', { class: 'who' }, who),
      el('span', { class: warn ? 'state warn' : 'state' }, state),
      el('span', { class: 'probe' }, probeText(host.id))),
    el('div', { class: 'actions' },
      action('Probar', () => probe(host), { 'aria-label': `Probar ${name}` }),
      action(host.state === 'ACTIVE' ? 'Pausar' : 'Activar', () => {
        const next = host.state === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
        store.setState(host.id, next);
        if (next === 'ACTIVE') pauses.resume(host.id);
        probes.delete(host.id);
      }, { 'aria-label': `${host.state === 'ACTIVE' ? 'Pausar' : 'Activar'} ${name}` }),
      action('Etiqueta', () => {
        editing = host.id;
        renderHosts();
        document.querySelector(`[data-id="${host.id}"] .edit input`)?.focus();
      }, { 'aria-label': `Cambiar etiqueta de ${name}` }),
      action('Subir', () => store.move(host.id, -1),
        { 'aria-label': `Subir ${name} en la lista`, 'aria-disabled': index === 0 ? 'true' : null }),
      action('Bajar', () => store.move(host.id, +1),
        { 'aria-label': `Bajar ${name} en la lista`, 'aria-disabled': index === count - 1 ? 'true' : null }),
      action('Borrar', () => {
        store.remove(host.id);
        pauses.resume(host.id);
        aggregator.forget(host.id);
        probes.delete(host.id);
      }, { 'aria-label': `Borrar ${name}` })));
}

function renderHosts() {
  if (current !== 'settings') return;
  const hosts = store.list();
  const focused = document.activeElement;
  const focusedId = focused?.closest?.('.host')?.dataset.id;
  const focusedLabel = focused?.textContent;
  $('hosts').replaceChildren(...hosts.map((h, i) => hostRow(h, i, hosts.length)));
  $('no-hosts').hidden = hosts.length > 0;
  $('first-run').hidden = hosts.length > 0;
  // Subir y Bajar redibujan la lista: el foco sigue al host, no se queda en la posición.
  if (focusedId && editing === null) {
    const buttons = [...$('hosts').querySelectorAll(`[data-id="${focusedId}"] button`)];
    buttons.find((b) => b.textContent === focusedLabel)?.focus();
  }
}

$('add-form').addEventListener('submit', (e) => {
  e.preventDefault();
  const added = store.add($('host-input').value, $('label-input').value);
  $('settings-status').textContent = {
    added: 'Host añadido',
    duplicate: 'Ese host ya está en la lista',
    invalid: 'Dirección no válida',
  }[added.result];
  if (added.result === 'added') {
    $('host-input').value = '';
    $('label-input').value = '';
    probe(added.host);
  }
});

store.onChange(renderHosts);
pauses.onChange(renderHosts);

onEnter.settings = () => {
  $('first-run').hidden = store.list().length > 0;
  renderHosts();
  if (store.list().length === 0) $('host-input').focus();
};

// ---------------------------------------------------------------------------
// Teclado

document.addEventListener('keydown', (e) => {
  const typing = ['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName);
  if (e.key === 'Escape') {
    if (editing !== null) {
      editing = null;
      renderHosts();
      return;
    }
    if (current === 'player' && fullscreen) {
      toggleFullscreen();
      return;
    }
    if (typing) document.activeElement.blur();
    back();
    return;
  }
  if (current !== 'player' || typing) return;
  if (e.key === ' ' && document.activeElement?.tagName !== 'BUTTON') {
    e.preventDefault();
    player.togglePause();
  }
  if (e.key === 'f' || e.key === 'F') toggleFullscreen();
});

// ---------------------------------------------------------------------------

showView('live');
if (store.list().length === 0) go('settings');
