"""Validates translations against the English base and writes res/values-*/strings.xml."""
import importlib.util
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "res")


def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


EN = load("keys").EN
T = {}
for f in ("t_cis", "t_eu", "t_asia"):
    T.update(load(f).T)

DIRS = {"id": "values-in"}  # Android resources use the legacy code for Indonesian
PH = re.compile(r"%\d\$[sd]")


def esc(t):
    t = (t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
         .replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n"))
    if t[:1] in "@?":
        t = "\\" + t
    return t


def write(dirname, strings):
    d = os.path.join(RES, dirname)
    os.makedirs(d, exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    if dirname == "values":
        out.append('    <string name="app_name">Osmon</string>')
    for k in EN:
        out.append('    <string name="%s">%s</string>' % (k, esc(strings[k])))
    out.append("</resources>")
    with open(os.path.join(d, "strings.xml"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


errors = []
for lang, tr in T.items():
    missing = [k for k in EN if k not in tr]
    extra = [k for k in tr if k not in EN]
    if missing:
        errors.append("%s: missing %s" % (lang, missing))
    if extra:
        errors.append("%s: unknown %s" % (lang, extra))
    for k, v in tr.items():
        if k in EN and sorted(PH.findall(v)) != sorted(PH.findall(EN[k])):
            errors.append("%s.%s: placeholders %s vs %s" % (lang, k, PH.findall(v), PH.findall(EN[k])))
        if "%" in v.replace("%1$s", "").replace("%1$d", ""):
            errors.append("%s.%s: stray %%" % (lang, k))
if errors:
    print("\n".join(errors))
    sys.exit(1)

write("values", EN)
for lang, tr in T.items():
    write(DIRS.get(lang, "values-" + lang), tr)
print("languages:", 1 + len(T), "keys:", len(EN))
