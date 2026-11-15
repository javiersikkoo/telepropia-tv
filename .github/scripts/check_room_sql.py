import pathlib, re, sys

kt = pathlib.Path("app/src/main/java/es/telepropia/tv/data/local/AppDatabase.kt").read_text()
block = re.search(r'CREATE_V3 = (.*?)\n\n', kt, re.S).group(1)
migration = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', block)).replace("`source_host_v3`", "`source_host`")

generated = None
for f in pathlib.Path("app/build/generated").rglob("AppDatabase_Impl.*"):
    m = re.search(r'CREATE TABLE IF NOT EXISTS `source_host` \([^"]*\)', f.read_text())
    if m:
        generated = m.group(0)
        break

print("migración:", migration)
print("Room:     ", generated)
if generated is None or generated != migration:
    print("El SQL de MIGRATION_2_3 no coincide con el que genera Room")
    sys.exit(1)
print("OK: el SQL de la migración coincide con Room")
