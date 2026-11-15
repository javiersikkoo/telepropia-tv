# CLAUDE.md

App Android de TV para uso personal: lista los partidos que se están jugando
ahora y los reproduce. No es un cliente IPTV. No hay canales, ni parrilla, ni
EPG, ni panel de proveedor.

La app nunca habla con el sitio de deportes. Habla con un resolver que corre en
la red local y que ya existe. El resolver es código ajeno: se usa, no se toca.

---

## §0 Reglas de la fuente

- La fuente no es legal ni deja de serlo. No se valida y no se discute. No
  escribas avisos sobre licencias en el código ni en los comentarios.
- **No investigues.** No navegues, no compruebes dominios, no busques en la web,
  no opines sobre si el sitio va a caer. El contrato está en §5 y las respuestas
  reales en `fixtures/`.
- Si falta un dato, pídelo en una frase y sigue. No lo inventes.
- Si algo del documento no te cuadra, dilo en una frase y sigue. No reescribas
  el alcance por tu cuenta.

---

## §1 Requisitos técnicos

- Kotlin, Gradle Kotlin DSL, version catalog.
- `es.telepropia.tv`. minSdk 21, compileSdk 34, targetSdk 34.
- Vistas XML. **Cero Compose.** Foco con el sistema (`nextFocus*`).
  Leanback solo si aporta en la lista; por defecto no.
- ktlint configurado y pasando.
- Cero telemetría, cero analytics, cero crash reporting.
- Comentarios solo para el "por qué", nunca para el "qué".
- Cero cadenas de dominio en Kotlin. El único host es el del resolver, y vive
  en DataStore, editable desde ajustes.

## §2 Manifest

- Intent `LEANBACK_LAUNCHER` además de `LAUNCHER`.
- `android:banner`.
- `uses-feature android:name="android.software.leanback"` requerido **false**.
- `uses-feature android:name="android.hardware.touchscreen"` requerido **false**.
- Actividad de reproductor en `landscape`, `configChanges` para orientación y
  teclado, `keepScreenOn`.
- Permiso `INTERNET`. Nada más.

## §3 Arquitectura

Cinco capas en paquetes separados:

- `data/model` — modelos de §4.
- `data/remote` — cliente HTTP del resolver.
- `data/local` — Room y DataStore.
- `domain` — casos de uso: listar, resolver, probar fuente.
- `ui` — pantallas y adaptadores.

`SourceRepository` es la única puerta a los datos. La UI no conoce el cliente
HTTP ni Room.

## §4 Modelos

```
Sport(id: Int, label: String)
Match(matchId: String, sportId: Int, name: String, sourceHostId: Long, sourceName: String)
PlaybackSpec(url: String, referer: String?)
SourceHost(id: Long, baseUrl: String, label: String?, state: ACTIVE|PAUSED)
SourceHealth(ok: Boolean, latencyMs: Long, failure: Failure?)
```

El orden de los hosts es el de la lista en ajustes; no hay primario.

`PlaybackSpec` **no se cachea nunca**. Se resuelve en el momento del play. Las
URLs caducan y una caché solo produce 403.

## §5 Contrato del resolver

Base: el host configurado, sin barra final. Timeout de 8 s en todas las
llamadas. Respuestas en JSON.

`GET /api/live?sportType={n}`

```
{ "sportType": 1, "matches": [ { "matchId": "4331876", "sportType": 1, "name": "Team A vs Team B" } ] }
```

Lista vacía significa que no hay nada en vivo de ese deporte. No es un fallo.

`GET /api/resolve?matchId={id}&sportType={n}`

```
{ "name": "...", "matchId": "...", "sportType": 1,
  "streamUrl": "https://cdn…/index.m3u8",
  "referer": "https://player…/",
  "playableUrl": "http://localhost:3000/api/hls?url=…&referer=…" }
```

`400` URL inválida, `502` fallo aguas arriba.

**Ojo:** `playableUrl` viene con `localhost` aunque el resolver esté en otra
máquina. Hay que reescribir el host de `playableUrl` al host configurado antes
de reproducir. Sin esto no se ve nada en el Fire Stick.

Se reproduce `playableUrl`, no `streamUrl`. El proxy del resolver ya inyecta el
referer, así que Media3 no necesita cabeceras propias.

`GET /api/live?sportType=1` sirve además como `probe()`.

Deportes: 1 fútbol, 2 baloncesto, 3 tenis, 4 béisbol, 6 críquet, 7 motor,
8 rugby, 9 fútbol americano, 11 hockey, 90 otros.

## §6 Persistencia

- Room: `SourceHost` (host del resolver, etiqueta, estado y orden). Sin caché
  de catálogo (la lista se pide viva).
- DataStore: deporte activo.
- Al primer arranque, si no hay hosts, se abre ajustes con el campo vacío y un
  texto de una línea explicando que hace falta el host del resolver.
- La lista se refresca cada 60 s mientras está en primer plano, y al volver del
  reproductor.

## §7 Pantallas

1. **Partidos en vivo.** Filtro por deporte arriba, lista debajo, agrupada por
   evento. Nombre y hora de detección. Cada evento abre sus fuentes y se elige
   fuente. Un evento sin fuentes vivas se marca y no se puede abrir. Refresco
   automático. Estado vacío propio.
2. **Reproductor.**
3. **Ajustes.** Lista de hosts, alta con etiqueta opcional, editar etiqueta,
   borrado, subir y bajar en la lista, activar o pausar, botón probar.

Navegación completa con D-pad, foco visible en todo, BACK en un solo paso en
todas.

## §8 Reproductor y errores

Media3, `HlsMediaSource`, sin cabeceras propias.

Un fallo de reproducción reintenta una vez re-resolviendo el partido. Si vuelve
a fallar, se pinta el error con botón de reintentar.

Mapeo obligatorio, en español, sin códigos ni trazas en pantalla:

- sin red → "Sin conexión"
- timeout → "Sin respuesta del servidor"
- 403/487 → "El enlace ha caducado" + reintento que re-resuelve
- 404 → "El partido ya no está disponible"
- 502 → "La fuente no responde"
- cualquier otro → "No se ha podido reproducir"

Cortar la red en mitad de la reproducción tiene que mostrar "Sin respuesta del
servidor", no un diálogo del sistema ni un cierre.

## §9 Fuentes múltiples: agregación

Se pueden dar de alta varios hosts, cada uno `ACTIVE` o `PAUSED`. La lista se
pide a todos los hosts activos en paralelo y se fusiona por nombre y deporte
normalizados en eventos. No se deduplica a una fila: cada host que da el evento
aparece como fuente propia dentro de él.

El host que falla se pausa en memoria y se excluye, sin borrarse. `resolve()`
va siempre al host de la fuente elegida.

Dar de alta un segundo host no puede requerir recompilar.

## §10 Fases

**F1** — Proyecto, manifest, modelos, cliente del resolver, Room, DataStore,
pantalla de lista, reproductor con Media3 y errores, ajustes con probe.

**F2** — Fuentes múltiples y conmutación de §9, reintento con re-resolución,
modo degradado, refresco y estados de carga.

**F3** — Estados vacíos, accesibilidad de foco, descripciones para TalkBack,
arranque en frío por debajo de 3 s medido.

**F4** — Release firmado, splits por ABI, reglas de minify, README con el guion
de instalación por Downloader.

## §11 Cierre de fase

Ninguna fase se cierra sin:

- `assembleRelease` compila sin warnings nuevos
- se instala y arranca en el Fire Stick 4K
- toda la pantalla se recorre solo con el mando
- BACK sale del reproductor en un solo paso
- `DECISIONS.md` tiene entrada por cada desvío

Y en F1, además: reproducir un partido en vivo real.

Los tres puntos que separan "funciona" de "está hecho": el error de red mapeado,
el segundo host dado de alta sin recompilar, y `DECISIONS.md` actualizado. Si
falla uno de los tres, la fase no está cerrada aunque reproduzca.

## §12 Fuera de alcance

Xtream, EPG, canales, categorías, estanterías, paginación, búsqueda, WebView,
grabación, timeshift, PiP, multiventana, Compose, telemetría.

## §13 Escritorio (Windows y Mac)

Cliente de escritorio en `desktop/`, en este mismo repo. Hace exactamente lo
mismo que la app de TV: hosts con etiqueta, estado y orden; agregación de §9;
elección de fuente; mapeo de errores y reintento de §8; modo degradado; refresco
de §6. Se maneja con teclado y ratón. Uso personal.

- Tauri 2 con HTML y JS sin framework; hls.js para reproducir. Sin telemetría.
- El contrato es el de §5, sin cambios. Cero cadenas de dominio en el código.
- Las peticiones al resolver y la carga de HLS van por el cliente HTTP de Tauri:
  el resolver es código ajeno y no manda cabeceras CORS.
- Hosts y deporte se guardan en el almacenamiento local de la app. No se
  sincronizan con la TV.
- Instaladores sin firma de pago: `.exe` para Windows y `.dmg` universal para
  Mac con firma ad hoc. Los avisos de instalación se aceptan.
- Esc vuelve un paso en todas las vistas. En el reproductor, espacio pausa y F
  pone pantalla completa.

Cierre: tests del núcleo, recorrido E2E en navegador contra resolvers falsos,
instaladores generados por el CI, y prueba manual en cada sistema.
