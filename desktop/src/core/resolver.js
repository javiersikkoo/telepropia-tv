import { Failure, err, fromError, fromStatus, ok } from './failure.js';
import { displayName, endpoint, rewritePlayable } from './hosts.js';

/**
 * Cliente del contrato de §5. `http(url)` devuelve `{ status, text() }` o lanza;
 * en Tauri va por su cliente HTTP, en el navegador por fetch.
 */
export function createResolverClient(http, isOnline = () => true) {
  async function get(url, parse) {
    let response;
    try {
      response = await http(url);
    } catch (error) {
      return err(fromError(error, isOnline()));
    }
    if (response.status < 200 || response.status >= 300) return err(fromStatus(response.status));
    try {
      return ok(parse(JSON.parse(await response.text())));
    } catch {
      return err(Failure.Other);
    }
  }

  return {
    live(host, sportId) {
      const sourceName = displayName(host);
      return get(endpoint(host.baseUrl, 'api/live', { sportType: sportId }), (body) =>
        (Array.isArray(body.matches) ? body.matches : []).map((m) => ({
          matchId: String(m.matchId),
          sportId: Number(m.sportType),
          name: String(m.name),
          sourceHostId: host.id,
          sourceName,
        })),
      );
    },

    resolve(baseUrl, matchId, sportId) {
      return get(endpoint(baseUrl, 'api/resolve', { matchId, sportType: sportId }), (body) => {
        const url = rewritePlayable(body.playableUrl, baseUrl);
        if (!url) throw new Error('playableUrl');
        return { url, referer: body.referer ?? null };
      });
    },
  };
}
