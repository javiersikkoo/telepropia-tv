"""E2E en emulador Android TV: recorre la app con el mando contra dos resolvers falsos.

Uso: e2e.py RUTA_APK DIR_HLS DIR_CAPTURAS
"""

import os
import re
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK, HLS_DIR, SHOTS = sys.argv[1], sys.argv[2], sys.argv[3]
PKG = "es.telepropia.tv"
HERE = os.path.dirname(os.path.abspath(__file__))
HOST_IP = "10.0.2.2"

SITE_A = [["100", "Team A vs Team B"], ["101", "Team C vs Team D"]]
SITE_B = [["9", "TEAM A - Team B"], ["10", "Team E v. Team F"]]

servers = {}
results = []
shot_index = 0


def adb(*args, check=True, timeout=120):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check, timeout=timeout).stdout


def shell(cmd, timeout=120):
    return adb("shell", cmd, timeout=timeout)


def key(name, times=1):
    for _ in range(times):
        shell("input keyevent KEYCODE_" + name)
        time.sleep(0.6)


def shot(name):
    global shot_index
    shot_index += 1
    path = os.path.join(SHOTS, "%02d-%s.png" % (shot_index, name))
    with open(path, "wb") as fh:
        fh.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, check=False).stdout)


def dump():
    for _ in range(5):
        out = shell("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml")
        start = out.find("<hierarchy")
        if start >= 0:
            try:
                return ET.fromstring(out[start:out.rindex("</hierarchy>") + len("</hierarchy>")])
            except (ET.ParseError, ValueError):
                pass
        time.sleep(1)
    raise RuntimeError("uiautomator no devuelve la jerarquía")


def nodes(root=None):
    root = root if root is not None else dump()
    return list(root.iter("node"))


def texts(root=None):
    return [n.get("text") for n in nodes(root) if n.get("text")]


def with_text(sub, root=None):
    return [n for n in nodes(root) if sub in (n.get("text") or "")]


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(node):
    x, y = center(node)
    shell("input tap %d %d" % (x, y))
    time.sleep(1)


def wait_text(sub, timeout=30):
    end = time.time() + timeout
    while time.time() < end:
        found = with_text(sub)
        if found:
            return found
        time.sleep(1)
    raise AssertionError("no aparece '%s'. En pantalla: %s" % (sub, texts()))


def wait_gone(resource_ids, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        present = [n for n in nodes() if n.get("resource-id") in resource_ids]
        if not present:
            return
        time.sleep(1)
    raise AssertionError("siguen en pantalla: %s" % resource_ids)


def resumed():
    out = shell("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -1")
    return out.strip()


def wait_activity(name, timeout=20):
    end = time.time() + timeout
    while time.time() < end:
        if name in resumed():
            return
        time.sleep(1)
    raise AssertionError("se esperaba %s y está %s" % (name, resumed()))


def focused():
    return [n for n in nodes() if n.get("focused") == "true"]


def focused_texts():
    out = []
    for n in focused():
        out += [d.get("text") for d in n.iter("node") if d.get("text")]
    return out


def start_server(port, matches):
    import json
    stop_server(port)
    servers[port] = subprocess.Popen(
        [sys.executable, os.path.join(HERE, "fake_resolver.py"), str(port), HLS_DIR, json.dumps(matches)]
    )
    time.sleep(1)


def stop_server(port):
    proc = servers.pop(port, None)
    if proc:
        proc.terminate()
        proc.wait(timeout=10)


def step(name, fn):
    started = time.time()
    try:
        fn()
        results.append(("OK", name, time.time() - started))
        print("✓ %s (%.0f s)" % (name, time.time() - started), flush=True)
        shot(name.split(":")[0].replace(" ", "_")[:40])
    except Exception as exc:
        results.append(("FALLO", name, time.time() - started))
        print("✗ %s\n    %s" % (name, exc), flush=True)
        shot("FALLO-" + name.split(":")[0].replace(" ", "_")[:30])
        raise


# ---------------------------------------------------------------------------


def install():
    adb("install", "-r", "-g", APK, timeout=300)
    shell("pm clear " + PKG)


def first_run_opens_settings():
    shell("am start -n %s/.ui.live.LiveActivity" % PKG)
    wait_activity("SettingsActivity")
    wait_text("Hace falta el host del resolver")


def hide_ime():
    # En TV el teclado de pantalla tapa la mitad de ajustes; el texto se inyecta igual al campo con foco.
    if "mInputShown=true" in shell("dumpsys input_method"):
        key("BACK")


def by_id(name):
    return next(n for n in nodes() if n.get("resource-id") == "%s:id/%s" % (PKG, name))


def add_host(address, label):
    before = len(with_text("Probar"))
    tap(by_id("host_input"))
    hide_ime()
    shell("input text " + address)
    if label:
        tap(by_id("label_input"))
        hide_ime()
        shell("input text " + label.replace(" ", "%s"))
    hide_ime()
    tap(by_id("add_button"))
    end = time.time() + 15
    while time.time() < end and len(with_text("Probar")) <= before:
        time.sleep(1)
    assert len(with_text("Probar")) == before + 1, "no se ha dado de alta %s: %s" % (address, texts())


def add_two_hosts_without_recompiling():
    add_host("%s:3001" % HOST_IP, "Sitio A")
    add_host("%s:3002/" % HOST_IP, "")
    end = time.time() + 30
    while time.time() < end and len(with_text("OK ·")) < 2:
        time.sleep(1)
    assert len(with_text("OK ·")) == 2, "probe de los dos hosts: %s" % texts()
    assert with_text("Sitio A") and with_text("%s:3002" % HOST_IP), texts()


def back_to_list():
    for _ in range(3):
        if "LiveActivity" in resumed():
            break
        key("BACK")
    wait_activity("LiveActivity")


def event_with_two_sources():
    back_to_list()
    wait_text("Team A vs Team B", 40)
    row = [n for n in nodes() if n.get("focusable") == "true" and any(
        "Team A vs Team B" in (d.get("text") or "") for d in n.iter("node"))]
    assert row, "no hay fila para Team A vs Team B"
    row_texts = [d.get("text") for d in row[0].iter("node") if d.get("text")]
    assert "2 fuentes" in row_texts, "Team A debería tener 2 fuentes: %s" % row_texts
    assert with_text("1 fuente"), "Team C y Team E deberían tener 1 fuente: %s" % texts()


def dpad_focus():
    # Los taps dejan el sistema en modo táctil; dos UP lo sacan y dejan el foco en la fila de deportes.
    key("DPAD_UP", 2)
    key("DPAD_DOWN")
    assert "Team A vs Team B" in focused_texts(), "DPAD_DOWN no entra en la lista: %s" % focused_texts()
    key("DPAD_UP")
    assert "Fútbol" in focused_texts(), "DPAD_UP no vuelve al deporte activo: %s" % focused_texts()
    key("DPAD_DOWN")


def open_sources():
    key("DPAD_CENTER")
    wait_activity("SourcesActivity")
    wait_text("Elige fuente")
    wait_text("Fuente 1 · Sitio A")
    wait_text("Fuente 2 · %s:3002" % HOST_IP)


def play():
    key("DPAD_CENTER")
    wait_activity("PlayerActivity")
    wait_gone({PKG + ":id/buffering", PKG + ":id/status_text"}, 60)
    time.sleep(5)
    assert not with_text("No se ha podido") and not with_text("caducado"), texts()
    assert "PlayerActivity" in resumed()


def back_once_leaves_player():
    key("BACK")
    wait_activity("SourcesActivity", 5)


def network_cut_shows_timeout():
    play()
    stop_server(3001)
    stop_server(3002)
    wait_text("Sin respuesta del servidor", 240)
    assert "PlayerActivity" in resumed(), "la app se ha cerrado: %s" % resumed()
    start_server(3001, SITE_A)
    start_server(3002, SITE_B)


def pause_first_keeps_event_with_second():
    back_to_list()
    tap(with_text("Ajustes")[0])
    wait_activity("SettingsActivity")
    pause = sorted(with_text("Pausar"), key=lambda n: center(n)[1])[0]
    tap(pause)
    wait_text("En pausa")
    back_to_list()
    tap(with_text("Fútbol")[0])
    wait_text("Team A vs Team B", 40)
    row = [n for n in nodes() if n.get("focusable") == "true" and any(
        "Team A" in (d.get("text") or "") for d in n.iter("node"))][0]
    row_texts = [d.get("text") for d in row.iter("node") if d.get("text")]
    assert "1 fuente" in row_texts, row_texts
    assert not with_text("Team C vs Team D"), "Team C solo está en el host pausado"
    tap(row)
    wait_activity("SourcesActivity")
    wait_text("Fuente 1 · %s:3002" % HOST_IP)
    assert not with_text("Sitio A"), texts()
    back_to_list()
    tap(with_text("Ajustes")[0])
    tap(with_text("Activar")[0])
    end = time.time() + 10
    while time.time() < end and with_text("En pausa"):
        time.sleep(1)
    assert not with_text("En pausa"), texts()
    back_to_list()


def degraded_mode():
    stop_server(3002)
    tap(with_text("Fútbol")[0])
    wait_text("Modo degradado", 40)
    wait_text("Sin fuentes vivas")
    dead = [n for n in nodes() if n.get("focusable") == "true" and any(
        "Team E" in (d.get("text") or "") for d in n.iter("node"))][0]
    tap(dead)
    time.sleep(2)
    assert "LiveActivity" in resumed(), "un evento sin fuentes vivas no debe abrirse"
    start_server(3002, SITE_B)


def cold_start():
    totals, drawn = [], []
    for _ in range(5):
        shell("am force-stop " + PKG)
        time.sleep(2)
        shell("logcat -c")
        out = shell("am start -W -n %s/.ui.live.LiveActivity" % PKG)
        match = re.search(r"TotalTime:\s*(\d+)", out)
        if match:
            totals.append(int(match.group(1)))
        time.sleep(6)
        log = shell("logcat -d -s ActivityTaskManager:I")
        full = re.search(r"Fully drawn %s/\.ui\.live\.LiveActivity: \+(?:(\d+)s)?(\d+)ms" % re.escape(PKG), log)
        if full:
            drawn.append(int(full.group(1) or 0) * 1000 + int(full.group(2)))
    total = statistics.median(totals) if totals else None
    full = statistics.median(drawn) if drawn else None
    line = "Arranque en frío (mediana de 5, emulador): primer frame %s ms, lista pintada %s ms" % (total, full)
    print(line, flush=True)
    with open(os.environ.get("GITHUB_STEP_SUMMARY", "/dev/null"), "a") as fh:
        fh.write("\n### %s\n" % line)
    assert total is not None and total < 3000, "el arranque supera 3 s en el emulador"


def main():
    os.makedirs(SHOTS, exist_ok=True)
    start_server(3001, SITE_A)
    start_server(3002, SITE_B)
    try:
        step("instalar el release minificado", install)
        step("primer arranque abre ajustes", first_run_opens_settings)
        step("alta de dos hosts sin recompilar, con probe OK", add_two_hosts_without_recompiling)
        step("cierre F2: un evento con dos fuentes", event_with_two_sources)
        step("foco con el mando: lista y vuelta al deporte activo", dpad_focus)
        step("selector de fuentes con etiqueta y host:puerto", open_sources)
        step("reproduce el HLS de playableUrl reescrito", play)
        step("BACK sale del reproductor en un solo paso", back_once_leaves_player)
        step("corte de red en reproducción: Sin respuesta del servidor", network_cut_shows_timeout)
        step("cierre F2: pausar el primero deja el evento con la segunda", pause_first_keeps_event_with_second)
        step("modo degradado: host caído marca sus eventos", degraded_mode)
        step("arranque en frío por debajo de 3 s", cold_start)
    finally:
        for port in list(servers):
            stop_server(port)
        with open(os.environ.get("GITHUB_STEP_SUMMARY", "/dev/null"), "a") as fh:
            fh.write("\n## E2E en emulador Android TV\n\n| | Paso | s |\n|---|---|---|\n")
            for status, name, secs in results:
                fh.write("| %s | %s | %.0f |\n" % ("✅" if status == "OK" else "❌", name, secs))


if __name__ == "__main__":
    main()
