import Hls from 'hls.js';
import { Failure, fromStatus } from './core/failure.js';
import { isOnline, request } from './platform/http.js';

function emptyStats() {
  return {
    aborted: false,
    loaded: 0,
    retry: 0,
    total: 0,
    chunkCount: 0,
    bwEstimate: 0,
    loading: { start: 0, first: 0, end: 0 },
    parsing: { start: 0, end: 0 },
    buffering: { start: 0, first: 0, end: 0 },
  };
}

// hls.js carga con XHR del webview, que el resolver rechazaría por CORS: playlist y segmentos pasan por request().
class ResolverLoader {
  constructor() {
    this.stats = emptyStats();
    this.context = null;
    this.aborted = false;
  }

  load(context, config, callbacks) {
    this.context = context;
    this.aborted = false;
    const stats = (this.stats = emptyStats());
    stats.loading.start = performance.now();
    const binary = context.responseType === 'arraybuffer';
    const timeoutMs = config?.loadPolicy?.maxLoadTimeMs || config?.timeout || 20000;

    request(context.url, { timeoutMs })
      .then(async (response) => {
        if (this.aborted) return;
        stats.loading.first = performance.now();
        if (response.status < 200 || response.status >= 300) {
          stats.loading.end = performance.now();
          callbacks.onError({ code: response.status, text: `HTTP ${response.status}` }, context, null, stats);
          return;
        }
        const data = binary ? await response.arrayBuffer() : await response.text();
        if (this.aborted) return;
        stats.loading.end = performance.now();
        stats.loaded = stats.total = binary ? data.byteLength : data.length;
        callbacks.onSuccess({ url: response.url, data, code: response.status }, stats, context, null);
      })
      .catch((error) => {
        if (this.aborted) return;
        stats.loading.end = performance.now();
        if (error?.name === 'TimeoutError') callbacks.onTimeout(stats, context, null);
        else callbacks.onError({ code: 0, text: String(error?.message || error) }, context, null, stats);
      });
  }

  abort() {
    this.aborted = true;
    this.stats.aborted = true;
  }

  destroy() {
    this.abort();
    this.context = null;
  }
}

const policy = (firstByteMs, totalMs) => ({
  default: {
    maxTimeToFirstByteMs: firstByteMs,
    maxLoadTimeMs: totalMs,
    timeoutRetry: { maxNumRetry: 2, retryDelayMs: 0, maxRetryDelayMs: 0 },
    errorRetry: { maxNumRetry: 2, retryDelayMs: 1000, maxRetryDelayMs: 4000 },
  },
});

/**
 * Reproductor HLS sin cabeceras propias: el proxy del resolver ya inyecta el referer.
 * `events`: onPlaying(), onBuffering(bool), onFailure(failure), onEnded().
 */
export function createPlayer(video, events) {
  let hls = null;
  let mediaRecovered = false;

  function stop() {
    if (hls) {
      hls.destroy();
      hls = null;
    }
    video.removeAttribute('src');
    video.load();
  }

  function play(url) {
    stop();
    mediaRecovered = false;
    hls = new Hls({
      loader: ResolverLoader,
      manifestLoadPolicy: policy(8000, 12000),
      playlistLoadPolicy: policy(8000, 12000),
      fragLoadPolicy: policy(8000, 20000),
      enableWorker: true,
    });
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(() => {});
    });
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (!data.fatal) return;
      if (data.type === Hls.ErrorTypes.MEDIA_ERROR && !mediaRecovered) {
        mediaRecovered = true;
        hls.recoverMediaError();
        return;
      }
      const code = data.response?.code;
      let failure = Failure.Other;
      if (code >= 400) failure = fromStatus(code);
      else if (data.type === Hls.ErrorTypes.NETWORK_ERROR) failure = isOnline() ? Failure.Timeout : Failure.NoNetwork;
      events.onFailure(failure);
    });
    hls.loadSource(url);
    hls.attachMedia(video);
  }

  video.addEventListener('playing', () => events.onPlaying());
  video.addEventListener('waiting', () => events.onBuffering(true));
  video.addEventListener('ended', () => events.onEnded());

  return {
    play,
    stop,
    togglePause() {
      if (video.paused) video.play().catch(() => {});
      else video.pause();
    },
  };
}
