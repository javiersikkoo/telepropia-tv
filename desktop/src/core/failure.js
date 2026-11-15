export const Failure = Object.freeze({
  NoNetwork: 'NoNetwork',
  Timeout: 'Timeout',
  Expired: 'Expired',
  NotFound: 'NotFound',
  Upstream: 'Upstream',
  NoHost: 'NoHost',
  NoActiveHost: 'NoActiveHost',
  Other: 'Other',
});

export const ok = (value) => ({ ok: true, value });
export const err = (failure) => ({ ok: false, failure });

export function fromStatus(code) {
  if (code === 403 || code === 487) return Failure.Expired;
  if (code === 404) return Failure.NotFound;
  if (code === 502) return Failure.Upstream;
  return Failure.Other;
}

// Sin acceso a la red del sistema, el tipo de fallo solo se ve en el texto del error.
export function fromError(error, online = true) {
  if (!online) return Failure.NoNetwork;
  const name = error && error.name;
  if (name === 'TimeoutError' || name === 'AbortError') return Failure.Timeout;
  const text = String((error && error.message) || error || '').toLowerCase();
  if (/dns|lookup|resolve host|unreachable|enetunreach|no route/.test(text)) return Failure.NoNetwork;
  if (/timed? ?out|refused|failed to fetch|connect|reset|network/.test(text)) return Failure.Timeout;
  return Failure.Other;
}

const MESSAGES = {
  NoNetwork: 'Sin conexión',
  Timeout: 'Sin respuesta del servidor',
  Expired: 'El enlace ha caducado',
  NotFound: 'El partido ya no está disponible',
  Upstream: 'La fuente no responde',
  NoHost: 'Falta el host del resolver. Añádelo en Ajustes.',
  NoActiveHost: 'Todos los hosts están en pausa. Activa alguno en Ajustes.',
};

export function message(failure, other = 'No se ha podido reproducir') {
  return MESSAGES[failure] || other;
}
