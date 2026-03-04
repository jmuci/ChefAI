# Session Context — ChefAI
<!-- This file is read at the start of every Claude session and updated after significant work. -->
<!-- Keep it concise: max ~150 lines. Focus on actionable state, not history. -->

## Last Updated
2026-03-04

## Current Branch
`add-image-logging` — Added comprehensive logging for image loading pipeline (Coil 3 debug)

## Recent Changes
- Added image loading interceptor and debug logging across RecipeListCard, RecipeDetailsScreen, LargeCard, UserProfileMenu, NetworkDomainMap
- Consolidated all AI context files (AI_CONTEXT.md, AGENTS.md, prompts/) into CLAUDE.md

## Project Status
| Area                          | Status                         |
|-------------------------------|--------------------------------|
| Room DB (11 entities, v2)     | Done                           |
| DAOs + Paging 3               | Done                           |
| Auth (login/register/refresh) | Done (real API calls)          |
| Secure token storage          | Done                           |
| Feature-based packages        | Done                           |
| Test infrastructure           | Good (JVM fakes, instrumented) |
| Anonymous-first usage         | Done                           |
| Sync (push/pull)              | Done                           |
| Real user wiring              | In Progress                    |
| HomeScreen Server Driven UI   | NOT started                    |
| Meal Plans                    | Stub only                      |

## Active Priorities
1. Wire real user from SessionManager into RecipesViewModel/Repository
2. Milestone 4: 	Interview Ready - Monstro Demo
3. 
## Known Issues
- RecipesViewModel and DefaultRecipeRepository use hardcoded test UUID
- HomeScreen uses static placeholder data
- loadSession() creates User with placeholder displayName="User"

## Blockers
None currently.

## Notes for Next Session
- The `prompts/` folder still exists with code generation templates (referenced from old AGENTS.md)
- Two old worktrees exist under `.claude/worktrees/` (upbeat-clarke, hopeful-elbakyan)
