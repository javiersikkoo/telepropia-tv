// Puerto siempre explícito para que el mismo host escrito de dos formas no entre dos veces.
export function normalizeHost(raw) {
  let text = String(raw || '').trim().replace(/\/+$/, '');
  if (!text) return null;
  if (!text.includes('://')) text = 'http://' + text;
  let url;
  try {
    url = new URL(text);
  } catch {
    return null;
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
  if (!url.hostname) return null;
  const port = url.port || (url.protocol === 'https:' ? '443' : '80');
  return `${url.protocol}//${url.hostname}:${port}`;
}

// §5 no da nombre de fuente; host:puerto es lo único que distingue un resolver de otro.
export function hostLabel(baseUrl) {
  return String(baseUrl).split('://')[1] || String(baseUrl);
}

export function displayName(host) {
  return (host.label && host.label.trim()) || hostLabel(host.baseUrl);
}

// El resolver firma playableUrl con localhost aunque corra en otra máquina.
// Se copian ruta y query tal cual: decodificar la query partiría el url interno en el primer `&`.
export function rewritePlayable(playableUrl, baseUrl) {
  let playable;
  let base;
  try {
    playable = new URL(playableUrl);
    base = new URL(baseUrl);
  } catch {
    return null;
  }
  const rawPathAndQuery = playableUrl.slice(playableUrl.indexOf(playable.host) + playable.host.length);
  return `${base.protocol}//${base.host}${rawPathAndQuery || '/'}`;
}

export function endpoint(baseUrl, path, params) {
  const query = new URLSearchParams(params).toString();
  return `${baseUrl.replace(/\/+$/, '')}/${path}${query ? '?' + query : ''}`;
}
