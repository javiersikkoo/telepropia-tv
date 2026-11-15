import { normalizeHost } from './hosts.js';

const HOSTS_KEY = 'telepropia.hosts';
const SPORT_KEY = 'telepropia.sport';

/** Hosts en el orden de ajustes, que es la prioridad. `storage` es localStorage o un doble en los tests. */
export class HostStore {
  constructor(storage) {
    this.storage = storage;
    this.listeners = new Set();
  }

  list() {
    try {
      const raw = JSON.parse(this.storage.getItem(HOSTS_KEY) || '[]');
      return Array.isArray(raw) ? raw : [];
    } catch {
      return [];
    }
  }

  byId(id) {
    return this.list().find((h) => h.id === id) || null;
  }

  add(raw, label) {
    const baseUrl = normalizeHost(raw);
    if (!baseUrl) return { result: 'invalid' };
    const hosts = this.list();
    if (hosts.some((h) => h.baseUrl === baseUrl)) return { result: 'duplicate' };
    const host = {
      id: hosts.reduce((max, h) => Math.max(max, h.id), 0) + 1,
      baseUrl,
      label: cleanLabel(label),
      state: 'ACTIVE',
    };
    this.save([...hosts, host]);
    return { result: 'added', host };
  }

  setLabel(id, label) {
    this.update(id, { label: cleanLabel(label) });
  }

  setState(id, state) {
    this.update(id, { state });
  }

  move(id, delta) {
    const hosts = this.list();
    const from = hosts.findIndex((h) => h.id === id);
    const to = from + delta;
    if (from < 0 || to < 0 || to >= hosts.length) return;
    hosts.splice(to, 0, hosts.splice(from, 1)[0]);
    this.save(hosts);
  }

  remove(id) {
    this.save(this.list().filter((h) => h.id !== id));
  }

  get sport() {
    return Number(this.storage.getItem(SPORT_KEY)) || 1;
  }

  set sport(id) {
    this.storage.setItem(SPORT_KEY, String(id));
  }

  onChange(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  update(id, patch) {
    this.save(this.list().map((h) => (h.id === id ? { ...h, ...patch } : h)));
  }

  save(hosts) {
    this.storage.setItem(HOSTS_KEY, JSON.stringify(hosts));
    this.listeners.forEach((fn) => fn());
  }
}

function cleanLabel(raw) {
  const text = String(raw || '').trim();
  return text || null;
}

export const SPORTS = [
  { id: 1, label: 'Fútbol' },
  { id: 2, label: 'Baloncesto' },
  { id: 3, label: 'Tenis' },
  { id: 4, label: 'Béisbol' },
  { id: 6, label: 'Críquet' },
  { id: 7, label: 'Motor' },
  { id: 8, label: 'Rugby' },
  { id: 9, label: 'Fútbol americano' },
  { id: 11, label: 'Hockey' },
  { id: 90, label: 'Otros' },
];

export const PROBE_SPORT_ID = 1;
