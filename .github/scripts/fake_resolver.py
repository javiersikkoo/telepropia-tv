"""Resolver falso para el E2E: cumple el contrato de §5 y sirve un HLS local.

Uso: fake_resolver.py PUERTO DIR_HLS '[["id", "nombre"], ...]'
"""

import json
import os
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1])
HLS_DIR = sys.argv[2]
MATCHES = dict(json.loads(sys.argv[3]))
STREAM = "https://cdn.example/token-abc/index.m3u8"
REFERER = "https://player.example/"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _json(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _file(self, path, ctype):
        if not os.path.isfile(path):
            return self._json(404, {"error": "no existe"})
        with open(path, "rb") as fh:
            data = fh.read()
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(url.query)

        if url.path == "/api/live":
            sport = int(query.get("sportType", ["1"])[0])
            matches = [{"matchId": k, "sportType": 1, "name": v} for k, v in MATCHES.items()] if sport == 1 else []
            # Campo fuera de §5 a propósito: la app tiene que ignorarlo.
            return self._json(200, {"sportType": sport, "matches": matches, "servidor": "falso"})

        if url.path == "/api/resolve":
            match_id = query.get("matchId", [""])[0]
            if match_id not in MATCHES:
                return self._json(404, {"error": "no encontrado"})
            playable = "http://localhost:%d/api/hls?url=%s&referer=%s" % (
                PORT,
                urllib.parse.quote(STREAM, safe=""),
                urllib.parse.quote(REFERER, safe=""),
            )
            return self._json(200, {
                "name": MATCHES[match_id], "matchId": match_id, "sportType": 1,
                "streamUrl": STREAM, "referer": REFERER, "playableUrl": playable,
            })

        if url.path == "/api/hls":
            # Si la app hubiera decodificado la query al reescribir el host, el url interno llegaría roto.
            if query.get("url", [""])[0] != STREAM or query.get("referer", [""])[0] != REFERER:
                return self._json(400, {"error": "url invalida"})
            return self._file(os.path.join(HLS_DIR, "index.m3u8"), "application/vnd.apple.mpegurl")

        name = os.path.basename(url.path)
        if name.endswith(".ts"):
            return self._file(os.path.join(HLS_DIR, name), "video/mp2t")
        return self._json(404, {"error": "ruta desconocida"})


ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
