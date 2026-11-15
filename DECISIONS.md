# Registro de decisiones

Decisiones con alternativa razonable, recortes y ampliaciones de alcance, y
cambios en el contrato del resolver. Se lee al empezar cada sesión.

Formato: fecha, decisión, alternativa descartada, coste asumido. Cinco líneas
como máximo por entrada. Lo que rompe el contrato o el alcance se pregunta
antes de anotarse.

---

## Vigentes

## 2026-11-15 — El alcance no se recorta por criterio de legalidad

La app soporta cualquier fuente aunque no sea estrictamente legal. La licencia
la decide quien pone la fuente; la app es un cliente y no valida nada.

Alternativa descartada: limitar el proyecto a fuentes "legales". Se descarta
porque el requisito que manda es no estar acoplado a ninguna fuente.

Coste: el README asume que la fuente puede caer.

## 2026-11-15 — Sin Compose

Vistas XML con el sistema de foco y `nextFocus*`. El foco con mando en TV es
más predecible que con Compose.

Alternativa descartada: Compose. Coste: más XML y más `findViewById`.

## 2026-11-15 — `PlaybackSpec` no se cachea nunca

Se resuelve en el momento del play. Las URLs del resolver son de sesión y
caducan; una caché solo produce 403.

Alternativa descartada: caché corta de 30 s. Coste: una petición extra por
reproducción, y un reintento con re-resolución en cada 403.

## 2026-11-15 — Objetivo primario: Fire Stick 4K

Se desarrolla y se prueba contra Fire Stick 4K. Android TV y Google TV son
secundarios.

Alternativa descartada: tele Android TV. Coste: hay que vigilar el rendimiento
en Fire Stick Lite y no usar APIs que Amazon recorte.

## 2026-11-15 — minSdk 21

Fire OS antiguos y Android TV viejos siguen en el parque instalado. Media3 lo
soporta.

Alternativa descartada: minSdk 23 para ahorrar ramas. Coste: más comprobaciones
de compatibilidad en reproducción.

## 2026-11-15 — Nada de multiventana ni grabación

Se aparcan grabación, timeshift y PiP hasta que las cuatro fases estén
cerradas.

Alternativa descartada: incluirlas en F1. Coste: la app sale sin ellas.

---

## Nuevas

## 2026-11-15 — La app es un navegador de partidos, no un cliente IPTV

Una lista de lo que se juega ahora y un botón de play. Fuera Xtream, EPG,
canales, categorías, estanterías, paginación y búsqueda.

Alternativa descartada: el cliente IPTV de la primera versión del documento.
Se descarta porque sin panel Xtream ni parrilla, la mitad de ese alcance no se
usaría nunca para ver deporte.

Coste: no hay parrilla horaria ni navegación por canal; se deroga la entrada
de EPG desde F1.

## 2026-11-15 — La fuente es un resolver externo, en la LAN

El resolver hace el trabajo sucio del sitio (firma, cifrado, referer,
segmentos) y expone un JSON. La app consume ese JSON.

Alternativa descartada: reimplementar la cadena en Kotlin. Se descarta porque
son días de trabajo y obliga a meter criptografía del sitio en el código.

Coste: hace falta una máquina encendida mientras se ve, y un dominio más de
configuración. El resolver es código ajeno y no se toca.

## 2026-11-15 — La app no conoce ningún sitio; el único dato es el host

Ninguna cadena de dominio en Kotlin. El host del resolver vive en DataStore y
se edita desde ajustes.

Alternativa descartada: `ExtractorSpec` en Room con selectores y dominios.
Se descarta porque la app ya no habla con el sitio: no hay selectores que
guardar.

Coste: sin los sitios dentro, la app no sirve sin un resolver montado. Se
deroga la entrada de "todo lo específico de una fuente vive en datos".

## 2026-11-15 — Nada de WebView en la app

Ni oculta para extraer, ni visible como reproductor de reserva. Media3 y punto.

Alternativa descartada: WebView a pantalla completa. Funciona, pero en un
dispositivo sin puntero los anuncios y los controles de la página se vuelven
inmanejables con D-pad.

Coste: si el resolver falla, no hay plan B dentro de la app.

## 2026-11-15 — Sin caché de catálogo; refresco cada 60 s

La lista de partidos se pide viva al resolver mientras la pantalla está en
primer plano y al volver del reproductor.

Alternativa descartada: caché de 10 min en Room. Se descarta porque los
partidos entran y salen en minutos y una lista de hace diez es peor que nada.

Coste: una petición por minuto contra la red local.

## 2026-11-15 — `playableUrl` se reescribe al host configurado

El resolver devuelve `playableUrl` con `localhost` aunque corra en otra
máquina. Antes de reproducir se sustituye el host por el configurado.

Alternativa descartada: confiar en el valor tal cual. Se descarta porque en el
Fire Stick `localhost` es el propio stick y no se ve nada.

Coste: una manipulación de URL en el cliente, y hay que respetar la query
completa al reescribir.

## 2026-11-15 — Se codifica contra fixtures, no contra la web

`fixtures/live.json` y `fixtures/resolve.json` con respuestas reales son la
verdad de la forma de los datos. No se navega ni se investiga.

Alternativa descartada: dejar que el agente deduzca el contrato del sitio.
Se descarta porque cada sesión acababa en debate sobre legalidad y sobre
endpoints en vez de en código.

Coste: los fixtures se quedan viejos si el resolver cambia y hay que
regenerarlos a mano.

## 2026-11-15 — Sin Leanback por defecto

Una sola pantalla de lista y un reproductor. Vistas normales con foco del
sistema; Leanback solo si algún día hay filas de verdad.

Alternativa descartada: `BrowseFragment` y compañía. Se descarta porque añade
dependencia y jerarquía de fragments para pintar una lista plana.

Coste: si el catálogo crece a filas, hay que rehacer la pantalla.

## 2026-11-15 — El reintento con re-resolución entra en F1

§10 lo ponía en F2 pero §8 pide ya el mapeo de 403, que sin re-resolución no
tiene salida. F1 lo implementa para 403 y 487; F2 conserva el resto de fallos.

Alternativa descartada: dejar el 403 mapeado a un botón que reintenta la misma
URL caducada. Coste: F2 arranca con menos margen.

## 2026-11-15 — Hora de detección en memoria del ViewModel

`Match` no trae hora. `Map<matchId, hora>` en el ViewModel; se limpia al cambiar
de deporte y al salir de la pantalla; si un partido falta en una respuesta y
vuelve, cuenta como nuevo. Etiqueta, no ordena. Alternativa descartada: Room.
Coste: volver del reproductor reinicia las horas.

## 2026-11-15 — "Sin conexión" se deduce de la excepción

§2 solo permite `INTERNET`, sin `ACCESS_NETWORK_STATE`. DNS, sin ruta o red
inalcanzable → "Sin conexión"; conexión rechazada o timeout → "Sin respuesta
del servidor". Alternativa descartada: pedir el permiso. Coste: algún caso
límite cae en el mensaje vecino.

## 2026-11-15 — Versiones fijadas por compileSdk 34

core-ktx 1.13.1 y Media3 1.4.1; las siguientes piden compileSdk 35.
Alternativa descartada: subir compileSdk, que contradice §1.
Coste: no se usan las últimas versiones hasta que cambie §1.

## 2026-11-15 — `keepScreenOn` en la vista, no en el manifest

No existe como atributo de actividad. Va en la vista raíz del reproductor y como
`FLAG_KEEP_SCREEN_ON`. Alternativa descartada: ninguna válida.
Coste: ninguno.

## 2026-11-15 — El reproductor se cierra al pasar a segundo plano

`onStop` cierra la actividad; al volver se ve la lista y el siguiente play
re-resuelve. Alternativa descartada: pausar y reanudar con la misma URL, que
puede haber caducado. Coste: Home no retoma el partido.

## 2026-11-15 — Los hosts dejan de conmutar y pasan a agregar

Se piden todos los hosts activos en paralelo y se fusiona por nombre y deporte
normalizados; el que falla se pausa en memoria y se excluye, sin borrarse.
`resolve()` va al host de la fuente elegida. Sustituye el §9 de conmutación.

Alternativa descartada: conmutación al primario caído. Coste: una petición por
host en cada refresco, y el mismo evento aparece con calidades distintas.

## 2026-11-15 — Alta de sitios por script, un proceso y un puerto por sitio

Cada sitio es un resolver propio, levantado por script fuera de la app, con su
proceso y su puerto. La app solo ve hosts.

Alternativa descartada: alta de sitios dentro de la app. Coste: los resolvers
viven fuera y hay que mantenerlos aparte.

## 2026-11-15 — `sourceName` sale del host:puerto

§5 no da nombre de fuente ni `SourceHost` lo tiene. Se pinta `host:puerto`.
Alternativa descartada: inventar un campo de etiqueta sin pedirlo.
Coste: en pantalla se ve una IP. Resuelto con la entrada de la etiqueta.

## 2026-11-15 — Host caído: excluido, pero sus eventos se marcan

Casa §9 ("se excluye") con §7 ("evento sin fuentes vivas se marca"). La última
lista buena del host caído se guarda en memoria; sus eventos salen marcados y
sin abrir, y nunca se resuelve contra él. Espera de 5 min antes de volver a
pedirle; "Probar" con OK o reactivarlo lo devuelve antes. Coste: un host
intermitente entra y sale de la lista.

## 2026-11-15 — Modo degradado y reintento F2

Con algún host activo caído, banner "N de M hosts sin respuesta". El reintento
re-resuelve una vez también para 404, 502 y timeout; sin red no se reintenta.
Solo `/api/live` pausa hosts; un fallo de `resolve()` es del partido, no del
host. Alternativa descartada: pausar también en `resolve()`. Coste: un host que
lista pero no resuelve sigue en la lista.

## 2026-11-15 — DataStore deja de guardar host activo

§6 lo pide, pero con agregación no hay un host activo: hay estado por host en
Room. DataStore guarda solo el deporte. Alternativa descartada: mantener la
clave sin uso. Coste: §6 queda desfasado en esa línea hasta que se parchee.

## 2026-11-15 — Room v2 con migración

`state` entra como columna `TEXT NOT NULL DEFAULT 'ACTIVE'`; los hosts de F1 se
conservan. Alternativa descartada: `fallbackToDestructiveMigration`, que borra
los hosts. Coste: una migración que mantener.

## 2026-11-15 — `SourceHost` lleva etiqueta editable

Campo `label` opcional, visible en el selector de fuentes; vacío se pinta
`host:puerto`.

Alternativa descartada: elegir fuente por IP. Coste: un campo más en alta y
ajustes.

## 2026-11-15 — Un host que falla la lista queda fuera 5 minutos

Backoff fijo. `probe()` con OK o reactivarlo a mano lo devuelve antes. Solo
pausa el fallo de la lista, nunca el de `resolve()`.

Alternativa descartada: pausar también por fallo de resolución. Se descarta
porque un partido roto tiraría el sitio entero.

Coste: hasta 5 minutos sin ese host aunque se arregle solo.

## 2026-11-15 — "Marcar primario" pasa a subir y bajar

Sin `isPrimary`, §7 se queda sin forma de fijar la prioridad. Ajustes ordena
con Subir y Bajar; Room guarda `position`. Alternativa descartada: ordenar por
fecha de alta sin poder cambiarla. Coste: dos botones más por host.

## 2026-11-15 — Room v3 recrea la tabla

Quitar `isPrimary` y añadir `label` y `position` exige recrear la tabla:
SQLite no borra columnas antes de Android 14. La migración conserva los hosts y
pone el antiguo primario el primero. Coste: SQL a mano que debe calcar el que
genera Room; el CI lo compara.

## 2026-11-15 — Banner del launcher en PNG 320×180

El vector daba aviso de tamaño y en Fire OS el banner sale recortado o con
bandas. PNG de 320×180 px en `drawable-xhdpi`, con el nombre de la app.
Alternativa descartada: vector. Coste: un binario en el repo.

## 2026-11-15 — F3 y F4 se cierran antes de probar F1 en el aparato

Petición expresa: acabar la app entera y probar después. El riesgo de depurar
varias capas a la vez se acota con el E2E en emulador del CI.
Alternativa descartada: F1 en el Fire Stick antes de seguir. Coste: si algo
falla en el aparato, puede venir de cualquier fase.

## 2026-11-15 — E2E en emulador Android TV con resolvers falsos

El CI instala el release minificado en un emulador TV (API 30) y recorre la app
con el mando contra dos resolvers falsos que cumplen §5 y sirven HLS real.
Alternativa descartada: fiarlo todo a la prueba manual. Coste: un emulador no
es un Fire Stick; §11 sigue pidiendo la prueba en el aparato.

## 2026-11-15 — Arranque en frío medido en el emulador

`reportFullyDrawn()` al pintar el primer estado de la lista; el CI toma la
mediana de 5 arranques con `am start -W` y falla por encima de 3 s.
Alternativa descartada: medir solo a mano. Coste: el emulador x86 no tiene el
rendimiento del stick; la cifra del Fire Stick queda pendiente.

## 2026-11-15 — Splits por ABI sin código nativo

§10 los pide, pero ninguna dependencia trae `.so`: los APK por ABI salen
iguales que el universal. Se generan igualmente y Downloader usa el universal.
Alternativa descartada: no dividir. Coste: artefactos repetidos.

## 2026-11-15 — Firma por variables de entorno; temporal si faltan

La clave vive en `keystore.properties` (local) o en secretos de GitHub (CI).
Sin secretos, el CI firma con una clave temporal y lo dice en el nombre del
artefacto. Alternativa descartada: clave en el repo. Coste: dar de alta cuatro
secretos a mano; desde esta sesión no se puede.

## 2026-11-15 — Instalación servida desde el PC de los resolvers

El repo es privado: Downloader no puede bajar de GitHub. El README sirve el APK
con `python3 -m http.server` en la LAN. Alternativa descartada: alojar el APK
en público. Coste: un paso más en cada actualización.

---

## Derogadas

## 2026-11-15 — Derogada: `isPrimary`

Con agregación sobra. El orden de la lista en ajustes es la prioridad dentro
de cada evento.

Alternativa descartada: conservarlo como desempate. Coste: ninguna, el orden
de lista ya desempata.

## 2026-11-15 — Derogada: semilla M3U de TDTChannels

Ya no hay lista semilla. Al primer arranque, sin hosts, se abre ajustes con el
campo vacío y una línea de texto explicando que hace falta el host del resolver.

Alternativa descartada: mantener TDTChannels como relleno. Se descarta porque
la TDT no tiene deporte que el resolver no dé, y arrancar con una lista que no
es la que quieres ver solo confunde.

Coste: la app no hace nada útil hasta que se configura un host.

## 2026-11-15 — Derogada: EPG desde F1

Se cae con el recorte a navegador de partidos. Un partido en vivo no tiene
parrilla horaria.

Alternativa descartada: mantener `getEpg()` en el contrato. Se descarta porque
el resolver tampoco lo da.

Coste: si algún día se añaden canales 24 h, hay que diseñarlo de cero.

## 2026-11-15 — Derogada: todo lo específico de una fuente vive en datos

Sustituida por "la app no conoce ningún sitio". `ExtractorSpec` y
`MediaRef.extras` desaparecen; lo específico se queda en el resolver.

Alternativa descartada: conservar la tabla de specs para F3. Se descarta porque
sin WebView no hay nada que extraer en el cliente.

Coste: la app queda atada a un resolver concreto y a su contrato.