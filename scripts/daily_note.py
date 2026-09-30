#!/usr/bin/env python3
"""Daglige arbeidsnotater i Hindsight (ett dokument per arbeidsdag: daily/ÅÅÅÅ-MM-DD.md).

  python3 scripts/daily_note.py draft [ÅÅÅÅ-MM-DD]        skriver ut commits fra dagen (utgangspunkt for notatet)
  python3 scripts/daily_note.py save ÅÅÅÅ-MM-DD notat.md   lagrer notatet i Hindsight (samme dato erstatter forrige versjon)
  python3 scripts/daily_note.py index                      bygger daily/INDEX.md på nytt fra alle daglige notater

Notatets første linje bør være «# Daglig notat ÅÅÅÅ-MM-DD (ukedag): tittel». Indeksen bruker den linjen.
Bruker HINDSIGHT_API_URL og HINDSIGHT_BANK_ID (satt i skymiljøet). Kjør `git fetch --unshallow` først hvis klonen er grunn.
Se «Daglige notater» i CLAUDE.md for hva et notat skal inneholde.
"""
import datetime, json, os, subprocess, sys, urllib.request, uuid

WEEKDAYS = ["mandag", "tirsdag", "onsdag", "torsdag", "fredag", "lørdag", "søndag"]


def api(path, payload=None, method=None):
    base = f"{os.environ['HINDSIGHT_API_URL']}/v1/default/banks/{os.environ['HINDSIGHT_BANK_ID']}"
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(base + path, data=data, method=method or ("POST" if data else "GET"),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode())


def retain(item):
    return api("/memories", {"items": [item], "async": True, "operation_id": str(uuid.uuid4())})


def draft(date):
    out = subprocess.check_output(
        ["git", "log", "--all", "--reverse", f"--since={date} 00:00", f"--until={date} 23:59:59",
         "--date=format:%H:%M", "--pretty=%n=== %ad %h (%an) %s%n%b"], text=True)
    lines = [l for l in out.splitlines() if not l.startswith(("Co-Authored-By", "Claude-Session", "https://claude.ai"))]
    weekday = WEEKDAYS[datetime.date.fromisoformat(date).weekday()]
    print(f"# Daglig notat {date} ({weekday}): <tittel>\n\nCommits fra {date} (klokkeslett som i git; skyøkter står i UTC, +2 t norsk tid):")
    print("\n".join(lines)[:12000])


def save(date, path):
    text = open(path, encoding="utf-8").read().strip()
    weekday = WEEKDAYS[datetime.date.fromisoformat(date).weekday()]
    if not text.startswith("# Daglig notat"):
        text = f"# Daglig notat {date} ({weekday}): <tittel>\n" + text
    print(retain({
        "content": text + "\n", "document_id": f"daily/{date}.md",
        "context": f"Daglig arbeidsnotat for weather-agent, {date} ({weekday}): hva vi jobbet med den dagen",
        "timestamp": f"{date}T12:00:00+02:00", "tags": ["daily-note", date[:7], "weather-agent"],
        "metadata": {"date": date, "weekday": weekday, "kind": "daily-note"}}))


def index():
    docs = api("/documents?limit=200").get("items", [])
    days = sorted(d["id"] for d in docs if d["id"].startswith("daily/2"))
    lines = []
    for doc_id in days:
        first = (api(f"/documents/{doc_id}").get("original_text") or "").strip().splitlines()[0]
        lines.append("- " + first.replace("# Daglig notat ", "**", 1).replace(" (", " (", 1).replace("):", ")**:", 1))
    dates = [datetime.date.fromisoformat(d[6:16]) for d in days]
    gaps = [f"{x + datetime.timedelta(days=1)} til {y - datetime.timedelta(days=1)} ({(y - x).days - 1} dager)"
            for x, y in zip(dates, dates[1:]) if (y - x).days > 4]
    gap_note = ("Ingen notater (ingen arbeid) i: " + "; ".join(gaps) + ".\n\n") if gaps else ""
    body = ("# Indeks over daglige notater\n**Arbeidsdager i weather-agent, fra git-historikken og senere notater. "
            "Bruk dato for å slå opp en dag: dokumentene heter `daily/ÅÅÅÅ-MM-DD.md`.**\n" + gap_note + "\n".join(lines) + "\n")
    print(retain({"content": body, "document_id": "daily/INDEX.md",
                  "context": "Indeks over alle daglige arbeidsnotater for weather-agent (en linje per dag)",
                  "tags": ["daily-note", "index", "weather-agent"], "metadata": {"kind": "daily-index"}}))
    print(f"{len(lines)} dager i indeksen")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "draft":
        draft(sys.argv[2] if len(sys.argv) > 2 else datetime.date.today().isoformat())
    elif cmd == "save" and len(sys.argv) == 4:
        save(sys.argv[2], sys.argv[3])
    elif cmd == "index":
        index()
    else:
        print(__doc__)
        sys.exit(1)
