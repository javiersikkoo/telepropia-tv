# TelePropia

App de Android TV para uso personal: lista los partidos que se están jugando
ahora y los reproduce. Pensada para el Fire TV Stick 4K; funciona también en
Android TV y Google TV.

La app no habla con ningún sitio. Habla con uno o varios **resolvers** que corren
en tu red local, uno por sitio, cada uno en su propio proceso y puerto. Junta lo
que dan todos, agrupa el mismo partido en un solo evento y te deja elegir la
fuente. Si un resolver cae, sus eventos salen marcados y la app sigue con los
demás.

---

## Instalación en el Fire TV Stick con Downloader

### 1. Consigue el APK

- **Versión publicada:** en GitHub, pestaña **Releases**, fichero
  `telepropia.apk`. Con el repo público, Downloader lo baja directo de
  `https://github.com/javiersikkoo/telepropia-tv/releases/latest/download/telepropia.apk`
  y puedes saltarte el paso 2.
- **Último build:** pestaña **Actions** → la ejecución más reciente en verde →
  artefacto `telepropia-release-firma-definitiva` → `app-universal-release.apk`.

Usa siempre el **universal**: es el único que Downloader instala de una vez.
Renómbralo a `telepropia.apk` para escribir menos con el mando.

### 2. Sírvelo en tu red local

En el ordenador donde corren los resolvers, en la carpeta del APK:

```
python3 -m http.server 8080
```

Apunta la IP local de ese ordenador (por ejemplo `192.168.1.10`).

### 3. Prepara el Fire Stick (solo la primera vez)

1. **Activa las opciones para desarrolladores.** Ajustes → Mi Fire TV (en
   versiones nuevas: Dispositivo y software) → Acerca de → pulsa **7 veces**
   sobre el nombre del dispositivo hasta que avise.
2. **Instala Downloader.** Inicio → Buscar → escribe `Downloader` → la app
   naranja de AFTVnews → Descargar.
3. **Deja que Downloader instale apps.** Ajustes → Mi Fire TV → Opciones para
   desarrolladores → Instalar aplicaciones desconocidas → **Downloader:
   Activado**.

### 4. Instala

1. Abre Downloader y acepta el permiso de almacenamiento.
2. En el campo de dirección escribe `http://192.168.1.10:8080/telepropia.apk`
   (con tu IP) → **Go**.
3. Cuando termine la descarga → **Instalar** → **Listo**.
4. Downloader ofrece borrar el APK: **Eliminar**, no hace falta guardarlo.

### 5. Primer arranque

TelePropia abre Ajustes con el campo vacío. Para cada resolver:

1. Escribe `IP:puerto` (por ejemplo `192.168.1.10:3001`).
2. Opcional: ponle una etiqueta (por ejemplo `Sitio A`). Es lo que verás al
   elegir fuente; sin etiqueta se muestra `IP:puerto`.
3. **Añadir.** La app lo prueba sola y muestra `OK · N ms` si responde.

Dar de alta otro resolver no requiere reinstalar nada.

### Actualizar

Mismo proceso desde el paso 2. Mientras el APK esté firmado con la misma clave,
se instala encima y conserva los hosts. Si el Fire Stick dice "La aplicación no
se ha instalado", el APK está firmado con otra clave: desinstala la anterior y
vuelve a dar de alta los hosts.

---

## Uso con el mando

- **Arriba:** fila de deportes. Pulsa uno para filtrar.
- **Abajo:** eventos en vivo. Cada uno dice cuántas fuentes tiene. OK abre el
  selector de fuentes; OK otra vez reproduce.
- Un evento marcado **Sin fuentes vivas** viene de un resolver que no responde;
  no se abre hasta que vuelva.
- **Ajustes:** el orden de la lista es la prioridad de las fuentes dentro de
  cada evento (Subir / Bajar). Pausar deja un host fuera sin borrarlo.
- **Atrás** sale del reproductor de una pulsación.

La lista se refresca sola cada minuto y al volver del reproductor.

---

## Desarrollo

Requisitos: JDK 17 y Android SDK 34.

```
./gradlew ktlintCheck testDebugUnitTest assembleRelease lintRelease
```

### Firma del release

La clave nunca entra en el repo.

- **Local:** crea `keystore.properties` en la raíz (está en `.gitignore`):

  ```
  storeFile=/ruta/a/telepropia-release.jks
  storePassword=...
  keyAlias=telepropia
  keyPassword=...
  ```

- **CI:** cuatro secretos del repositorio (Settings → Secrets and variables →
  Actions): `TELEPROPIA_KEYSTORE_BASE64`, `TELEPROPIA_KEYSTORE_PASSWORD`,
  `TELEPROPIA_KEY_ALIAS`, `TELEPROPIA_KEY_PASSWORD`. Sin ellos el CI firma con
  una clave temporal y el artefacto se llama `...-firma-temporal`: sirve para
  probar, pero no se instala encima de una versión firmada con la definitiva.

Guarda una copia de `telepropia-release.jks` y su contraseña fuera del
ordenador: sin ella, cada actualización obliga a desinstalar.

### CI

Cada push a `main` ejecuta:

1. ktlint, tests unitarios, `assembleRelease` con recuento de warnings,
   comprobación del SQL de migración de Room y lint de Android.
2. **E2E en un emulador Android TV** contra dos resolvers falsos
   (`.github/scripts/fake_resolver.py`) que cumplen el contrato y sirven un
   HLS real. Instala el release minificado y comprueba con el mando: alta de
   dos hosts, evento con dos fuentes, reproducción, Atrás en un paso, corte de
   red con "Sin respuesta del servidor", pausar el primer host, modo degradado
   y arranque en frío por debajo de 3 s. Las capturas quedan en el artefacto
   `capturas-e2e`.

Para publicar una versión: Actions → Android CI → Run workflow → `version` = `vX.Y.Z`.
Sale en Releases como `telepropia.apk` (nombre fijo) y `telepropia-vX.Y.Z.apk`.

Las reglas del proyecto están en `CLAUDE.md` y las decisiones en
`DECISIONS.md`.
