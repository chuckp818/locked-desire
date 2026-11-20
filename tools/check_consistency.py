#!/usr/bin/env python3
"""Statische Konsistenzprüfung des KopfFrei-Projekts (KEIN Ersatz für eine Kompilierung).

Aufruf im Projektordner: python3 tools/check_consistency.py

Prüft:
 1. Jeder `import de.kopffrei...` zeigt auf eine vorhandene Deklaration.
 2. Das importierende Modul darf das Zielmodul sehen (Gradle-Abhängigkeit, `api` zählt transitiv).
 3. Importe von androidx/compose/hilt/... sind durch eine deklarierte Bibliothek gedeckt.
 4. R-Referenzen (R.drawable.x, R.string.x, ...) existieren im richtigen Modul (nonTransitiveRClass).
 5. Manifest: Klassen existieren; @-Ressourcen in XML existieren.
 6. Alle XML-Dateien sind wohlgeformt.
"""
import re, sys, os, glob
import xml.dom.minidom as minidom

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors, warnings = [], []

def err(m): errors.append(m)
def warn(m): warnings.append(m)

# ---------------------------------------------------------------- Module & Gradle
modules = {}  # ":core:common" -> dir
for dirpath, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in ("build", ".git", ".gradle", "node_modules")]
    if "build.gradle.kts" in files and dirpath != ROOT:
        rel = os.path.relpath(dirpath, ROOT)
        modules[":" + rel.replace(os.sep, ":")] = dirpath

settings = open(os.path.join(ROOT, "settings.gradle.kts")).read()
included = set(re.findall(r'":([a-z:]+)"', settings))
for m in modules:
    if m[1:] not in included:
        err(f"Modul {m} hat build.gradle.kts, steht aber nicht in settings.gradle.kts")
for inc in included:
    if ":" + inc not in modules:
        err(f"settings.gradle.kts bindet :{inc} ein, Ordner/Build-Datei fehlt")

deps = {}  # module -> {"api": set(proj), "impl": set(proj), "libs_api": set, "libs_impl": set, "libs_test": set, "plugins": set}
for m, d in modules.items():
    text = open(os.path.join(d, "build.gradle.kts")).read()
    info = {"api": set(), "impl": set(), "libs_api": set(), "libs_impl": set(), "libs_test": set(), "plugins": set(), "ns": None}
    for kind, proj in re.findall(r'\b(api|implementation)\(project\("(:[a-z:]+)"\)\)', text):
        info["api" if kind == "api" else "impl"].add(proj)
    for kind, lib in re.findall(r'\b(api|implementation|ksp|testImplementation|androidTestImplementation|debugImplementation)\((?:platform\()?libs\.([A-Za-z0-9.]+)\)?\)', text):
        if kind == "api": info["libs_api"].add(lib)
        elif kind == "implementation": info["libs_impl"].add(lib)
        elif kind in ("testImplementation", "androidTestImplementation"): info["libs_test"].add(lib)
    for p in re.findall(r'alias\(libs\.plugins\.([A-Za-z0-9.]+)\)', text):
        info["plugins"].add(p)
    ns = re.search(r'namespace\s*=\s*"([^"]+)"', text)
    info["ns"] = ns.group(1) if ns else None
    deps[m] = info
    for proj in info["api"] | info["impl"]:
        if proj not in modules:
            err(f"{m}: Abhängigkeit auf unbekanntes Modul {proj}")

def visible_projects(m):
    """Module, deren Klassen m sehen darf: direkte Abhängigkeiten + deren api-Ketten."""
    seen, stack = set(), list(deps[m]["api"] | deps[m]["impl"])
    while stack:
        p = stack.pop()
        if p in seen or p not in deps: continue
        seen.add(p)
        stack.extend(deps[p]["api"])
    return seen

def visible_libs(m):
    """Katalog-Bibliotheken, die m sehen darf (eigene + api-Bibliotheken sichtbarer Module)."""
    libs = set(deps[m]["libs_api"] | deps[m]["libs_impl"])
    for p in visible_projects(m):
        libs |= deps[p]["libs_api"]
    return libs

# ---------------------------------------------------------------- Kotlin-Dateien einlesen
def module_of(path):
    best = None
    for m, d in modules.items():
        if path.startswith(d + os.sep) and (best is None or len(d) > len(modules[best])):
            best = m
    return best

kt_files = []
for dirpath, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in ("build", ".git", ".gradle")]
    for f in files:
        if f.endswith(".kt"):
            kt_files.append(os.path.join(dirpath, f))

decl_re = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|internal|private|protected|data|enum|sealed|abstract|open|inline|value|annotation|const|tailrec|suspend|operator|infix|external|actual|expect|fun)\s+)*'
    r'(class|interface|object|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[A-Za-z0-9_.<>?, ]+\.)?([A-Za-z_][A-Za-z0-9_]*)',
    re.M)
pkg_decls = {}   # package -> set(names)
file_pkg = {}
for f in kt_files:
    src = open(f, encoding="utf-8").read()
    pm = re.search(r'^package\s+([\w.]+)', src, re.M)
    if not pm:
        err(f"{os.path.relpath(f, ROOT)}: keine package-Zeile"); continue
    pkg = pm.group(1)
    file_pkg[f] = pkg
    names = pkg_decls.setdefault(pkg, set())
    for line in src.split("\n"):
        if line.startswith((" ", "\t", "}", ")", "/", "*")): continue
        m = decl_re.match(line)
        if m: names.add(m.group(2))
    # Paket muss zum Ordner passen
    rel = os.path.relpath(os.path.dirname(f), ROOT).replace(os.sep, "/")
    mm = re.search(r'/(?:kotlin|java)/(.+)$', rel)
    if mm and mm.group(1).replace("/", ".") != pkg:
        err(f"{os.path.relpath(f, ROOT)}: package {pkg} passt nicht zum Ordner")

def pkg_to_module(pkg):
    for m, info in deps.items():
        if info["ns"] and (pkg == info["ns"] or pkg.startswith(info["ns"] + ".")):
            return m
    return None

# Paket -> Modul über Dateien (genauer als Namespace)
pkg_module = {}
for f, pkg in file_pkg.items():
    mod = module_of(f)
    pkg_module.setdefault(pkg, set()).add(mod)

# Bibliotheks-Präfixe
LIB_PREFIX = [
    ("androidx.compose.material.icons", {"compose.material.icons.extended"}),
    ("androidx.compose.material3", {"compose.material3"}),
    ("androidx.compose.foundation", {"compose.foundation", "compose.material3", "compose.ui"}),
    ("androidx.compose.animation", {"compose.foundation", "compose.material3", "compose.ui"}),
    ("androidx.compose.runtime", {"compose.ui", "compose.foundation", "compose.material3"}),
    ("androidx.compose.ui", {"compose.ui", "compose.foundation", "compose.material3"}),
    ("androidx.lifecycle.compose", {"androidx.lifecycle.runtime.compose"}),
    ("androidx.lifecycle", {"androidx.lifecycle.viewmodel.compose", "androidx.lifecycle.runtime.compose", "androidx.activity.compose", "androidx.core.ktx"}),
    ("androidx.hilt.navigation.compose", {"hilt.navigation.compose"}),
    ("androidx.navigation", {"androidx.navigation.compose"}),
    ("androidx.activity", {"androidx.activity.compose"}),
    ("androidx.core", {"androidx.core.ktx", "androidx.activity.compose", "androidx.biometric", "androidx.fragment"}),
    ("androidx.fragment", {"androidx.fragment"}),
    ("androidx.biometric", {"androidx.biometric"}),
    ("androidx.room", {"room.runtime", "room.ktx"}),
    ("androidx.datastore", {"datastore.preferences"}),
    ("androidx.glance", {"glance.appwidget"}),
    ("dagger.hilt", {"hilt.android"}),
    ("dagger.", {"hilt.android"}),
    ("javax.inject", {"hilt.android"}),
    ("kotlinx.coroutines", {"kotlinx.coroutines.android"}),
]
TEST_PREFIX = [
    ("org.junit", {"junit"}),
    ("androidx.test.ext", {"androidx.test.ext.junit"}),
    ("androidx.test.platform", {"androidx.test.runner", "androidx.test.ext.junit"}),
    ("androidx.test", {"androidx.test.runner", "androidx.test.ext.junit"}),
    ("kotlinx.coroutines.test", {"kotlinx.coroutines.test"}),
    ("androidx.room", {"room.testing", "room.runtime"}),
]
PLATFORM = ("android.", "java.", "javax.", "kotlin.", "kotlinx.", "org.w3c", "org.xmlpull")

for f in kt_files:
    mod = module_of(f)
    if mod is None: continue
    rel = os.path.relpath(f, ROOT)
    is_test = "/src/test/" in f or "/src/androidTest/" in f
    src = open(f, encoding="utf-8").read()
    vis_p, vis_l = visible_projects(mod), visible_libs(mod)
    test_libs = deps[mod]["libs_test"]
    my_pkg = file_pkg.get(f)
    imported_names = {}
    for imp in re.findall(r'^import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$', src, re.M):
        last = imp.split(".")[-1]
        imported_names[last] = imp
        if imp.startswith("de.kopffrei."):
            pkg, _, name = imp.rpartition(".")
            # Mitglieder von Objekten/Companions: Paket schrittweise kürzen
            cand, nm = pkg, name
            found = False
            while cand.startswith("de.kopffrei."):
                if nm in pkg_decls.get(cand, set()) or (cand in pkg_decls and nm == "*"):
                    found = True; break
                cand, _, nm2 = cand.rpartition(".")
                nm = nm2
                if cand not in pkg_decls and "." not in cand: break
            if not found and name != "*":
                err(f"{rel}: Import {imp} nicht gefunden")
                continue
            target_mods = pkg_module.get(cand, set())
            if target_mods and mod not in target_mods:
                if not (target_mods & (vis_p | {mod})):
                    err(f"{rel}: Import {imp} – {mod} hängt nicht von {sorted(m for m in target_mods if m)} ab")
        elif imp.startswith(PLATFORM) and not imp.startswith("kotlinx.coroutines") and not imp.startswith("javax.inject"):
            continue
        else:
            table = TEST_PREFIX + LIB_PREFIX if is_test else LIB_PREFIX
            ok, matched = False, False
            for prefix, libs in table:
                if imp.startswith(prefix):
                    matched = True
                    if libs & (vis_l | (test_libs if is_test else set())):
                        ok = True; break
                    # Nächstspezifischeres Präfix hat nicht gepasst → weitersuchen
            if matched and not ok:
                err(f"{rel}: Import {imp} – keine passende Bibliothek in {mod} sichtbar")
            elif not matched:
                warn(f"{rel}: Import {imp} nicht zuordenbar")

# ---------------------------------------------------------------- R-Referenzen
res_names = {}  # module -> {type: set(names)}
for m, d in modules.items():
    r = {}
    for resdir in glob.glob(os.path.join(d, "src", "*", "res")):
        for sub in os.listdir(resdir):
            typ = sub.split("-")[0]
            for fn in os.listdir(os.path.join(resdir, sub)):
                full = os.path.join(resdir, sub, fn)
                if fn.endswith(".xml") and typ == "values":
                    try:
                        dom = minidom.parse(full)
                        for tag, rtype in (("string", "string"), ("color", "color"), ("style", "style"), ("dimen", "dimen")):
                            for e in dom.getElementsByTagName(tag):
                                r.setdefault(rtype, set()).add(e.getAttribute("name").replace(".", "_"))
                    except Exception as ex:
                        err(f"{full}: {ex}")
                else:
                    r.setdefault(typ, set()).add(fn.rsplit(".", 1)[0])
    res_names[m] = r

for f in kt_files:
    mod = module_of(f)
    src = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, ROOT)
    for typ, name in re.findall(r'\bR\.(\w+)\.(\w+)', src):
        # welches R? Standard = eigenes Modul, sonst per Import
        rimp = re.findall(r'^import\s+([\w.]+)\.R\s*$', src, re.M)
        targets = [mod] if not rimp else [pkg_to_module(p) or mod for p in rimp]
        if not any(name in res_names.get(t, {}).get(typ, set()) for t in targets):
            err(f"{rel}: R.{typ}.{name} nicht in {targets} gefunden")

# ---------------------------------------------------------------- XML / Manifeste
xml_files = []
for dirpath, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in ("build", ".git", ".gradle")]
    for f in files:
        if f.endswith(".xml"):
            xml_files.append(os.path.join(dirpath, f))
for f in xml_files:
    try:
        minidom.parse(f)
    except Exception as ex:
        err(f"{os.path.relpath(f, ROOT)}: XML nicht wohlgeformt: {ex}")

all_res = {}
for m, r in res_names.items():
    for t, names in r.items():
        all_res.setdefault(t, set()).update(names)
builtin = ("android:", "+id", "null")
for f in xml_files:
    src = open(f, encoding="utf-8").read()
    for typ, name in re.findall(r'@(?!android:)(\w+)/([\w.]+)', src):
        if typ in ("+id",): continue
        if typ == "layout" and name == "glance_default_loading_layout":
            continue  # kommt aus der Bibliothek glance-appwidget
        if name.replace(".", "_") not in all_res.get(typ, set()):
            err(f"{os.path.relpath(f, ROOT)}: @{typ}/{name} existiert nicht")

for mod, d in modules.items():
    mf = os.path.join(d, "src", "main", "AndroidManifest.xml")
    if not os.path.exists(mf): continue
    dom = minidom.parse(mf)
    ns = deps[mod]["ns"]
    for tag in ("activity", "service", "receiver", "provider", "application"):
        for e in dom.getElementsByTagName(tag):
            name = e.getAttribute("android:name")
            if not name or name.startswith("androidx."): continue
            full = (ns + name) if name.startswith(".") else name
            pkg, _, cls = full.rpartition(".")
            if cls not in pkg_decls.get(pkg, set()):
                err(f"{os.path.relpath(mf, ROOT)}: Klasse {full} ({tag}) nicht gefunden")

# ---------------------------------------------------------------- Ausgabe
for w in warnings: print("WARNUNG:", w)
for e in errors: print("FEHLER:", e)
print(f"\n{len(kt_files)} Kotlin-Dateien, {len(xml_files)} XML-Dateien, {len(modules)} Module geprüft: "
      f"{len(errors)} Fehler, {len(warnings)} Warnungen")
sys.exit(1 if errors else 0)
