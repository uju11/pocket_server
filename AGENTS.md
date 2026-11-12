# Agent rules (read at every session start)

## Session start
1. Read .ai/STATE.md only, then the PLAN.md section for the current task. Read ARCH.md only to locate code. Open nothing else until a task needs it.
2. If STATE.md says IN PROGRESS: run `git status --short` and `git diff --stat`, then finish or revert that task first.

## Work loop
1. Take the first unchecked task in PLAN.md (or STATE.md "Next").
2. Write "IN PROGRESS: <id>, files: ..." to STATE.md.
3. Implement the smallest change that meets the task's "done when".
4. Verify cheaply (see Verification).
5. Commit: one task = one commit, message "<id>: <summary>".
6. Update STATE.md (Last done, Next, Notes <=10 lines), tick PLAN.md, add a 1-3 line DECISIONS.md entry if you chose between options. Update ARCH.md only if structure changed.
7. Continue to the next task unless I said "one task".
Checkpoint after EVERY task. The session can be cut at any moment. Never leave more than one task uncommitted.

## Token discipline
- Search (grep/ripgrep/symbol search) before opening files; read only the needed line ranges. Don't re-read files you already know.
- Edit with targeted patches. Never rewrite whole files. Never paste unchanged code into chat.
- No explanations, plan recaps, or pleasantries. After each task reply with <=5 lines: task id, files changed, verification result, next task.
- No docs, READMEs, or extra tests beyond what the task requires.
- Task size: <=~150 changed lines and <=4 files. If bigger, split it into sub-tasks in PLAN.md and do the first one.
- Ask a question only if blocked: max one, with the default you'll use if I don't answer.
- No unrelated refactors or reformatting. No new dependency without a DECISIONS.md line.
- Prefer stubs with TODO(<id>) over speculative implementation of future tasks.

## Verification (keep output tiny)
- Compile only the touched module: ./gradlew :<module>:compileDebugKotlin -q 2>&1 | grep -E "^e: |FAILED|error" | head -20
- Run only relevant tests: ./gradlew :<module>:testDebugUnitTest --tests "<Class>" -q 2>&1 | tail -30
- Never print full build logs. On failure, fix the first error only.

## Safety / product rules
- Nothing internet-reachable without auth. No secrets in the repo.
- Default visibility private. Torrent seeding off by default.
- Keep Jellyfin / qBittorrent / DLNA / WebDAV as thin adapters over the core API; don't break their compatibility.
