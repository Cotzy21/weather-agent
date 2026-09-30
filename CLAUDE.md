# weather-agent

Java 25 / Spring Boot 3.5 (Maven) backend, package `no.weatheragent`; React/Vite frontend in `frontend/`. Status per area lives in `HANDOFF.md`.

## Delegating to cheap sub-agents

Use the Haiku-pinned agents in `.claude/agents/` for routine work so the main session's tokens go to the hard parts:

- `cheap-explorer`: finding files, symbols, usages; summarizing a module.
- `cheap-test-runner`: running Maven/npm builds, tests, lint; reporting failures only.
- `cheap-editor`: small mechanical edits that are fully specified (exact files and changes).

Keep in the main session: architecture and design, new features, logic changes, debugging, security-sensitive code, code review, and anything needing judgment.

**At most 5 sub-agents running at once.** Prefer one well-briefed agent over several narrow ones; don't spawn an agent for a single known-path read or grep.

## Long-term memory

Record ideas, faster ways of doing things, and optimizations as they come up (Hindsight memory once installed; otherwise the Claude Code auto-memory files).

## Daily notes

The owner wants a note for **every day we work on the project**, so they can later refer to a specific day ("the day we did X") and be understood. One Hindsight document per day: `daily/YYYY-MM-DD.md`, written in Norwegian, with a dated `timestamp` and the tag `daily-note`; `daily/INDEX.md` lists one line per day. Days 2026-06-22 to 2026-09-30 were written from the git history and are the template to follow (read `daily/2026-09-30.md`).

- Write or update the day's note **before a session ends** (the same document id replaces the previous version, so update it as the day goes on). Keep `daily/INDEX.md` current.
- Use `scripts/daily_note.py`: `draft [date]` prints that day's commits as a starting point, `save DATE file.md` stores the note, `index` rebuilds the index. It needs `HINDSIGHT_API_URL` and `HINDSIGHT_BANK_ID` (set in cloud sessions). If the clone is shallow, run `git fetch --unshallow` first.
- A note has: a one-line summary, what was worked on (with feature names as the owner says them), decisions and why, the state at the end of the day, open points, commit hashes, and search keywords. Times are Norwegian time; commits from cloud sessions are in UTC in git (+2 h).
- Days with no work get no note. Do not invent detail: the notes are built from commits, docs and what actually happened in the session.
