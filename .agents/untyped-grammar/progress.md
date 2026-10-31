# Progress: EFX-2 untyped grammar

Single record of the work described in `plan.md`. Update it at the end of every session: item status, gate results with their numbers, the commit ids of both repositories, decisions taken, open questions for Yannis.

**Last updated:** 2026-10-31, by the design thread, when the plan was committed.
**Current phase:** 0. **Next item:** P0.2.

## Work items

| Item | Status | Toolkit commit | SDK commit | Notes |
|---|---|---|---|---|
| P0.1 Create the branches | done 2026-10-31 | (this commit) | 0c66c10 | `feature/untyped-grammar` from `develop` (toolkit, `89e0821`) and from `efx-2` (SDK, `abd9d85`) |
| P0.2 Build environment | todo | | | |
| P0.3 Add `plan.md` and `progress.md` | done 2026-10-31 | (this commit) | 0c66c10 | |
| P0.4 Opt-in corpus test and baseline | todo | | | Baseline captured after P0.5 so that it includes `rules.efx` |
| P0.5 Fix the `rules.efx` failure (symbol resolver) and add the test | todo | | | |
| P1.1 Translation tests for untested features | todo | | | |
| P1.2 Invalid-operation table | todo | | | |
| P1.3 Record which rows are syntax errors today | todo | | | |
| P2.1 `model.signatures` package and catalogue test | todo | | | |
| P2.2 Leaf rule for references, path versus value | todo | | | |
| P2.3 Rewire the expression translator to the resolver | todo | | | |
| P2.4 Flip the promotion tests, remove `IDENTIFIER_IS_SCALAR` | todo | | | |
| P3.1 New `Efx.g4`, README, local install | todo | | | |
| P3.2 Expression visitor, composition refactor, declarations, error strategy, casts removed | todo | | | |
| P3.3 Profiler run | todo | | | |
| P3.4 Benchmark | todo | | | |
| P4.1 Tighten the invalid-operation table | todo | | | |
| P4.2 Uniform type-error messages | todo | | | |
| P5.1 Autocomplete catalogue from the signatures | todo | | | |
| P5.2 Consumers built against the branch | todo | | | |
| P5.3 Changelogs and final numbers | todo | | | |

## Gate results

| Gate | Date | Tests (count, green) | Corpus comparison | Timings | Profiler | Toolkit commit | SDK commit |
|---|---|---|---|---|---|---|---|
| 0 | | | | | | | |
| 1 | | | | | | | |
| 2 | | | | | | | |
| 3 | | | | | | | |
| 4 | | | | | | | |
| 5 | | | | | | | |

## Baseline

Location of the baseline directory captured in P0.4, the per-file digests, and the timings with the typed grammar (design-time measurement: 301 s warm, 360 s cold for the 52 templates).

## Decisions taken during implementation

None yet. Design-time decisions are in `plan.md`.

## Open questions for Yannis

None.

## Session log

- 2026-10-31, design thread: plan approved by Yannis; branches created in both forks; `plan.md` and `progress.md` committed.
