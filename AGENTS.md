# weather-agent

Java 25 / Spring Boot 3.5 (Maven) backend, package `no.weatheragent`; React/Vite frontend in `frontend/`. Status per area lives in `HANDOFF.md`.

## Delegating to cheap sub-agents

Use the Haiku-pinned agents in `.Codex/agents/` for routine work so the main session's tokens go to the hard parts:

- `cheap-explorer`: finding files, symbols, usages; summarizing a module.
- `cheap-test-runner`: running Maven/npm builds, tests, lint; reporting failures only.
- `cheap-editor`: small mechanical edits that are fully specified (exact files and changes).

Keep in the main session: architecture and design, new features, logic changes, debugging, security-sensitive code, code review, and anything needing judgment.

**At most 5 sub-agents running at once.** Prefer one well-briefed agent over several narrow ones; don't spawn an agent for a single known-path read or grep.

## Long-term memory

Record ideas, faster ways of doing things, and optimizations as they come up (Hindsight memory once installed; otherwise the Codex auto-memory files).
The user explicitly requested on 2026-10-11 that important findings, decisions,
test results, and useful project context be saved to the connected Hindsight
automatically, without asking for confirmation each time. This includes the
training QA findings approved that day. Save concise notes rather than secrets
or credentials; higher-priority tool and approval requirements still apply.

## Communication preference

Use the installed Caveman skill (`/Users/49kotosk/.codex/skills/caveman/SKILL.md`)
by default, as requested on 2026-10-11, until the user asks for normal mode.
Keep responses concise and in the user's language. Preserve technical facts,
negations, numbers, commands, paths, and code. Use full sentences when clarity,
safety, questions, or step order requires them. Persisted documents and memory
notes retain normal prose. Follow required progress updates and higher-priority
instructions.
