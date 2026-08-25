# Session Onboarding Guide

Steps Claude should follow at the start of every session to build context quickly.

---

## Step 1: Read Session Context
```
Read .claude/session-context.md
```
This file contains: current branch, recent changes, project status, active priorities, known issues, and blockers.

## Step 2: Understand the Task Scope

Before writing any code, determine which layer(s) the task touches:

| If the task involves... | Read these first |
|------------------------|------------------|
| UI / Compose | The target screen file + `core/ui/components/` + `core/util/ComposeUtils.kt` |
| ViewModel logic | The target ViewModel + its UiState/UiEvent types + repository interface |
| Data / Repository | `data/repository/` impl + DAO + mapper files |
| Schema change | `core/data/local/room/` entities + `ChefAIDataBase.kt` |
| Auth | `auth/` package + `docs/authentication.md` |
| Sync | `core/data/` sync files + `docs/sync-deep-dive.md` + [ADR-006](../adrs/adr-006-sync-protocol.md) |
| New feature | `docs/adrs/adr-0005-feature-based-package-structure.md` for package guidelines |

## Step 3: Check for Relevant Skills

Skills live in `.claude/skills/`. Use the matching skill template:

| Task | Skill |
|------|-------|
| Build a new Compose screen or component | `compose-component.md` |
| Create or modify a ViewModel | `viewmodel.md` |
| Review code (PR or ad-hoc) | `code-review.md` |
| Modify existing code | `update-code.md` |

## Step 4: Before Writing Code

- Read the files you're about to modify (never edit blind)
- Check existing patterns in nearby files — match the style
- For new files, follow the naming conventions in `docs/claude/conventions.md`
- Check `docs/claude/gotchas.md` for known pitfalls in the area you're working in

## Step 5: After Completing Work

Update `.claude/session-context.md` with:
- What was changed and why
- Current branch and its purpose
- Any new blockers, open questions, or next steps
- Updated project status if milestones changed
