// En la app de escritorio todo va por el cliente HTTP de Tauri: el resolver no manda CORS.
// En un navegador normal (tests contra resolvers falsos con CORS) se usa fetch.
const inTauri = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

let tauriFetch = null;
async function getFetch() {
  if (!inTauri) return window.fetch.bind(window);
  if (!tauriFetch) tauriFetch = (await import('@tauri-apps/plugin-http')).fetch;
  return tauriFetch;
}

const CONNECT_TIMEOUT_MS = 8000;
const CALL_TIMEOUT_MS = 12000;

export async function request(url, { timeoutMs = CALL_TIMEOUT_MS } = {}) {
  const doFetch = await getFetch();
  const init = { method: 'GET', signal: AbortSignal.timeout(timeoutMs) };
  if (inTauri) init.connectTimeout = CONNECT_TIMEOUT_MS;
  const response = await doFetch(url, init);
  return {
    status: response.status,
    url: response.url || url,
    text: () => response.text(),
    arrayBuffer: () => response.arrayBuffer(),
  };
}

export const isOnline = () => typeof navigator === 'undefined' || navigator.onLine !== false;

export async function setFullscreen(element, on) {
  if (inTauri) {
    const { getCurrentWindow } = await import('@tauri-apps/api/window');
    await getCurrentWindow().setFullscreen(on);
    return;
  }
  if (on && !document.fullscreenElement) await element.requestFullscreen?.();
  if (!on && document.fullscreenElement) await document.exitFullscreen?.();
}
