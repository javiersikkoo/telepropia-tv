import { Failure, err, ok } from './failure.js';

export const DEFAULT_COOLDOWN_MS = 5 * 60_000;

/** Pausa en memoria de los hosts que fallan la lista. Nunca toca lo guardado. */
export class HostPauses {
  constructor(cooldownMs = DEFAULT_COOLDOWN_MS) {
    this.cooldownMs = cooldownMs;
    this.pauses = new Map();
    this.listeners = new Set();
  }

  pause(hostId, failure, nowMs) {
    this.pauses.set(hostId, { untilMs: nowMs + this.cooldownMs, failure });
    this.listeners.forEach((fn) => fn());
  }

  resume(hostId) {
    if (this.pauses.delete(hostId)) this.listeners.forEach((fn) => fn());
  }

  isPaused(hostId) {
    return this.pauses.has(hostId);
  }

  failureOf(hostId) {
    return this.pauses.get(hostId)?.failure ?? null;
  }

  // Sin la espera, un host caído no volvería nunca a la lista sin pasar por ajustes.
  dueForRequest(host, nowMs) {
    if (host.state !== 'ACTIVE') return false;
    const pause = this.pauses.get(host.id);
    return !pause || nowMs >= pause.untilMs;
  }

  onChange(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }
}

export class LiveAggregator {
  constructor(fetchHost, pauses, clock = () => Date.now()) {
    this.fetchHost = fetchHost;
    this.pauses = pauses;
    this.clock = clock;
    // Última lista buena de cada host, solo en memoria: con ella se marcan los eventos de un host caído.
    this.lastLive = new Map();
  }

  /** `hosts` llega en el orden de ajustes, que es la prioridad de las fuentes. */
  async fetch(hosts, sportId) {
    if (hosts.length === 0) return err(Failure.NoHost);
    const active = hosts.filter((h) => h.state === 'ACTIVE');
    if (active.length === 0) return err(Failure.NoActiveHost);

    const now = this.clock();
    const due = active.filter((h) => this.pauses.dueForRequest(h, now));
    const results = await Promise.all(due.map(async (host) => [host, await this.fetchHost(host, sportId)]));

    const live = [];
    for (const [host, outcome] of results) {
      if (outcome.ok) {
        this.pauses.resume(host.id);
        this.lastLive.set(`${host.id}|${sportId}`, outcome.value);
        live.push(...outcome.value);
      } else {
        this.pauses.pause(host.id, outcome.failure, now);
      }
    }

    const failing = active.filter((h) => this.pauses.isPaused(h.id));
    const stale = failing.flatMap((h) => this.lastLive.get(`${h.id}|${sportId}`) || []);
    if (failing.length === active.length && stale.length === 0) {
      return err(this.pauses.failureOf(failing[0].id) || Failure.Timeout);
    }
    return ok({ activeHosts: active, live, stale, failingHosts: failing });
  }

  forget(hostId) {
    for (const key of [...this.lastLive.keys()]) {
      if (key.startsWith(`${hostId}|`)) this.lastLive.delete(key);
    }
  }
}

// Cada resolver escribe el cruce a su manera ("vs", "v", "-", tildes); sin quitar eso no se juntan nunca.
const CONNECTORS = new Set(['vs', 'v', 'versus', 'contra']);

export function normalizeName(name) {
  return String(name)
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .split(' ')
    .filter((w) => w && !CONNECTORS.has(w))
    .join(' ');
}

export const eventKey = (match) => `${match.sportId}|${normalizeName(match.name)}`;

export function merge(fetched) {
  const rank = new Map(fetched.activeHosts.map((h, i) => [h.id, i]));
  const sources = [
    ...fetched.live.map((match) => ({ match, live: true })),
    ...fetched.stale.map((match) => ({ match, live: false })),
  ];
  // sort es estable: dentro de un host se conserva el orden que da el resolver.
  sources.sort((a, b) => (rank.get(a.match.sourceHostId) ?? 1e9) - (rank.get(b.match.sourceHostId) ?? 1e9));

  const groups = new Map();
  for (const source of sources) {
    const key = eventKey(source.match);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(source);
  }

  const events = [...groups].map(([key, group]) => {
    const ordered = [...group.filter((s) => s.live), ...group.filter((s) => !s.live)];
    return {
      key,
      name: ordered[0].match.name,
      sportId: ordered[0].match.sportId,
      sources: ordered,
      liveSources: ordered.filter((s) => s.live).map((s) => s.match),
      hasLiveSource: ordered.some((s) => s.live),
    };
  });

  return {
    events,
    failingHosts: fetched.failingHosts,
    activeHostCount: fetched.activeHosts.length,
    degraded: fetched.failingHosts.length > 0,
  };
}

const RETRYABLE = new Set([Failure.Expired, Failure.NotFound, Failure.Upstream, Failure.Timeout]);

export const shouldRetry = (failure, alreadyRetried) => !alreadyRetried && RETRYABLE.has(failure);

// Con la red cortada en mitad del partido el re-resolve también falla por red,
// y §8 exige "Sin respuesta del servidor".
export function visibleFailure(trigger, resolveFailure) {
  if (trigger === Failure.Timeout && (resolveFailure === Failure.NoNetwork || resolveFailure === Failure.Timeout)) {
    return Failure.Timeout;
  }
  return resolveFailure;
}
