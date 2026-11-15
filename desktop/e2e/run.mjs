// Recorrido de escritorio en Chromium contra dos resolvers falsos que cumplen §5 y sirven HLS real.
// Uso: node e2e/run.mjs DIR_HLS_VP9 [DIR_CAPTURAS]
import { spawn } from 'node:child_process';
import { createReadStream, existsSync, mkdirSync, statSync } from 'node:fs';
import { appendFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const HERE = fileURLToPath(new URL('.', import.meta.url));
const DIST = resolve(HERE, '../dist');
const FAKE = resolve(HERE, '../../.github/scripts/fake_resolver.py');
const HLS = process.argv[2];
const SHOTS = process.argv[3] || resolve(HERE, '../e2e-capturas');
const APP_PORT = 4173;
const SITE_A = [['100', 'Team A vs Team B'], ['101', 'Team C vs Team D']];
const SITE_B = [['9', 'TEAM A - Team B'], ['10', 'Team E v. Team F']];

const servers = new Map();
const results = [];
let shot = 0;

function startServer(port, matches) {
  stopServer(port);
  const proc = spawn('python3', [FAKE, String(port), HLS, JSON.stringify(matches)], { stdio: 'ignore' });
  servers.set(port, proc);
  return new Promise((r) => setTimeout(r, 800));
}

function stopServer(port) {
  servers.get(port)?.kill();
  servers.delete(port);
}

function serveDist() {
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png' };
  const server = createServer((req, res) => {
    let path = join(DIST, decodeURIComponent(new URL(req.url, 'http://x').pathname));
    if (!existsSync(path) || statSync(path).isDirectory()) path = join(DIST, 'index.html');
    res.writeHead(200, { 'Content-Type': types[extname(path)] || 'application/octet-stream' });
    createReadStream(path).pipe(res);
  });
  return new Promise((r) => server.listen(APP_PORT, () => r(server)));
}

const expect = (cond, msg) => {
  if (!cond) throw new Error(msg);
};

async function main() {
  mkdirSync(SHOTS, { recursive: true });
  const app = await serveDist();
  await startServer(3101, SITE_A);
  await startServer(3102, SITE_B);
  const browser = await chromium.launch({
    executablePath: process.env.E2E_CHROMIUM || undefined,
    args: ['--autoplay-policy=no-user-gesture-required'],
  });
  const page = await browser.newPage({ viewport: { width: 1280, height: 760 }, locale: 'es-ES' });
  page.setDefaultTimeout(30_000);
  const row = (text) => page.locator('button.event', { hasText: text });

  async function step(name, fn) {
    const started = Date.now();
    try {
      await fn();
      results.push(['OK', name, Date.now() - started]);
      console.log(`✓ ${name} (${Math.round((Date.now() - started) / 1000)} s)`);
      await page.screenshot({ path: join(SHOTS, `${String(++shot).padStart(2, '0')}.png`) });
    } catch (error) {
      results.push(['FALLO', name, Date.now() - started]);
      console.log(`✗ ${name}\n    ${error.message.split('\n')[0]}`);
      await page.screenshot({ path: join(SHOTS, `${String(++shot).padStart(2, '0')}-FALLO.png`) });
      throw error;
    }
  }

  async function addHost(address, label) {
    await page.fill('#host-input', address);
    await page.fill('#label-input', label);
    await page.press('#label-input', 'Enter');
  }

  async function videoAdvances() {
    await page.waitForFunction(() => document.querySelector('#video').currentTime > 2, null, { timeout: 40_000 });
    expect(await page.locator('#player-error').isHidden(), 'hay un error en pantalla');
  }

  try {
    await step('primer arranque abre ajustes', async () => {
      await page.goto(`http://localhost:${APP_PORT}/`);
      await page.getByText('Hace falta el host del resolver').waitFor();
    });

    await step('alta de dos hosts sin recompilar, con probe OK', async () => {
      await addHost('127.0.0.1:3101', 'Sitio A');
      await page.getByText('Host añadido').waitFor();
      await addHost('127.0.0.1:3102/', '');
      await page.locator('.probe', { hasText: 'OK ·' }).nth(1).waitFor();
      await page.locator('.host .label', { hasText: 'Sitio A' }).waitFor();
      await page.locator('.host .label', { hasText: '127.0.0.1:3102' }).waitFor();
    });

    await step('cierre F2: un evento con dos fuentes', async () => {
      await page.keyboard.press('Escape');
      await row('Team A vs Team B').waitFor();
      expect((await row('Team A vs Team B').innerText()).includes('2 fuentes'), 'Team A debería tener 2 fuentes');
      expect((await row('Team C vs Team D').innerText()).includes('1 fuente'), 'Team C solo está en el sitio A');
      expect((await row('Team E').innerText()).includes('1 fuente'), 'Team E solo está en el sitio B');
    });

    await step('teclado: Tab llega a los eventos y Enter abre las fuentes', async () => {
      await row('Team A vs Team B').focus();
      await page.keyboard.press('Enter');
      await page.getByText('Elige fuente').waitFor();
      await page.locator('button.source', { hasText: 'Fuente 1 · Sitio A' }).waitFor();
      await page.locator('button.source', { hasText: 'Fuente 2 · 127.0.0.1:3102' }).waitFor();
      expect(await page.evaluate(() => document.activeElement?.textContent?.includes('Fuente 1')), 'foco en la fuente 1');
    });

    await step('reproduce el HLS de playableUrl reescrito', async () => {
      await page.keyboard.press('Enter');
      await videoAdvances();
    });

    await step('Esc sale del reproductor en un solo paso', async () => {
      await page.keyboard.press('Escape');
      await page.getByText('Elige fuente').waitFor();
      expect(await page.locator('#view-player').isHidden(), 'el reproductor sigue visible');
    });

    await step('corte del resolver en reproducción: Sin respuesta del servidor', async () => {
      await page.locator('button.source', { hasText: 'Sitio A' }).click();
      await videoAdvances();
      stopServer(3101);
      stopServer(3102);
      await page.getByText('Sin respuesta del servidor').waitFor({ timeout: 120_000 });
      await startServer(3101, SITE_A);
      await startServer(3102, SITE_B);
    });

    await step('cierre F2: pausar el primero deja el evento con la segunda', async () => {
      await page.keyboard.press('Escape');
      await page.keyboard.press('Escape');
      await page.click('#open-settings');
      await page.locator('.host', { hasText: 'Sitio A' }).getByRole('button', { name: /^Pausar/ }).click();
      await page.locator('.host', { hasText: 'Sitio A' }).getByText('En pausa').waitFor();
      await page.keyboard.press('Escape');
      await row('TEAM A - Team B').waitFor();
      expect((await row('TEAM A - Team B').innerText()).includes('1 fuente'), 'Team A debería quedar con 1 fuente');
      expect((await row('Team C').count()) === 0, 'Team C solo está en el host pausado');
      await row('TEAM A - Team B').click();
      await page.locator('button.source', { hasText: 'Fuente 1 · 127.0.0.1:3102' }).waitFor();
      expect((await page.locator('button.source').count()) === 1, 'solo debe quedar una fuente');
      await page.keyboard.press('Escape');
      await page.click('#open-settings');
      await page.locator('.host', { hasText: 'Sitio A' }).getByRole('button', { name: /^Activar/ }).click();
      await page.locator('.host', { hasText: 'Sitio A' }).getByText('Activo').waitFor();
      await page.keyboard.press('Escape');
    });

    await step('ajustes: subir, bajar y etiqueta', async () => {
      await page.click('#open-settings');
      await page.locator('.host').nth(1).getByRole('button', { name: /^Subir/ }).click();
      expect((await page.locator('.host').first().innerText()).includes('127.0.0.1:3102'), 'el segundo debería subir');
      await page.locator('.host').first().getByRole('button', { name: /^Cambiar etiqueta/ }).click();
      await page.locator('.host .edit input').fill('Sitio B');
      await page.locator('.host .edit input').press('Enter');
      await page.locator('.host .label', { hasText: 'Sitio B' }).waitFor();
      await page.keyboard.press('Escape');
      await row('TEAM A - Team B').click();
      await page.locator('button.source').first().filter({ hasText: 'Fuente 1 · Sitio B' }).waitFor();
      await page.keyboard.press('Escape');
    });

    await step('modo degradado: host caído marca sus eventos', async () => {
      stopServer(3101);
      await page.getByRole('button', { name: 'Fútbol', exact: true }).click();
      await page.getByText('Modo degradado').waitFor();
      const dead = row('Team C vs Team D');
      expect((await dead.innerText()).includes('Sin fuentes vivas'), 'Team C debería salir sin fuentes vivas');
      await dead.click({ force: true });
      expect(await page.locator('#view-live').isVisible(), 'un evento sin fuentes vivas no debe abrirse');
      await startServer(3101, SITE_A);
    });
  } finally {
    await browser.close();
    for (const port of [...servers.keys()]) stopServer(port);
    app.close();
    const summary = process.env.GITHUB_STEP_SUMMARY;
    if (summary) {
      appendFileSync(summary, '\n## E2E de escritorio (Chromium)\n\n| | Paso | s |\n|---|---|---|\n' +
        results.map(([s, n, ms]) => `| ${s === 'OK' ? '✅' : '❌'} | ${n} | ${Math.round(ms / 1000)} |`).join('\n') + '\n');
    }
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
