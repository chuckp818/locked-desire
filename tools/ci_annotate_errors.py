#!/usr/bin/env python3
"""Gibt die wichtigsten Fehlerzeilen eines Gradle-Logs als GitHub-Actions-Hinweise (Annotations) aus.

So lassen sich Compilerfehler auch ohne Anmeldung in der Übersicht des Laufs lesen.
Aufruf: python3 tools/ci_annotate_errors.py build.log "Titel"
"""
import re
import sys

path, title = sys.argv[1], sys.argv[2]
lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
pattern = re.compile(r"^e: |error:|What went wrong|Execution failed|Could not |Unresolved|FAILED|Caused by|> Task .* FAILED|Exception")
def kurz(l):
    # Kompilerfehler ohne den langen Runner-Pfad ausgeben
    return re.sub(r"file:///home/runner/work/[^/]+/[^/]+/", "", l.strip())


hits = [kurz(l) for l in lines if re.match(r"^e: ", l)]  # Compilerfehler zuerst
hits += [kurz(l) for l in lines if pattern.search(l) and not re.match(r"^e: ", l)]
# gleiche Zeilen nur einmal, Reihenfolge behalten
seen, unique = set(), []
for h in hits:
    if h not in seen:
        seen.add(h)
        unique.append(h)
unique = unique[:150]
if not unique:
    unique = lines[-40:]
chunk_size, max_annotations = 15, 10
for i in range(min(max_annotations, (len(unique) + chunk_size - 1) // chunk_size)):
    chunk = unique[i * chunk_size:(i + 1) * chunk_size]
    text = "\n".join(chunk)[:3800]
    text = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title={title} {i + 1}::{text}")
