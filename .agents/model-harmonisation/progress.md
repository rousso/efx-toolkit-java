# Progress: step 2, the model harmonisation

Single record of the work described in `plan.md` beside this file. Update it with every item: its status, its commit, its design doc, the numbers at that commit, and anything Yannis or the design thread decided.

**Last updated:** 2026-11-02, by the implementing thread.
**Next item:** H1, one parameter model.

## Work items

| Item | Status | Commit | Design doc | Notes |
|---|---|---|---|---|
| H0 Branch and baseline | done 2026-11-02 | the commit that adds this file | the plan, section 4 | `feature/efx2-model-harmonisation` off fcaedc8, the step 1 head, in rousso/efx-toolkit-java. The baseline below; `plan.md` is the approved Claude Doc, copied. No code change. |
| H1 One parameter model | not started | | | |
| H2 One way to check every construct | not started | | | |
| H3 One argument model | not started | | | |
| H4 The answers slimmed | not started | | | |
| H5 One holder of the translation state | not started | | | |
| H6 Names and the Function clash | not started | | | |
| H7 Public API and CHANGELOG | not started | | | |

## Baseline (H0)

Captured 2026-11-02 on fcaedc8, with the SDK of efc9a21 installed locally as 2.0.0-SNAPSHOT.

- **Tests:** `mvn -nsu -B clean test`: 2,709 tests, all green (corpus excluded).
- **Corpus:** `mvn -nsu -B test -Dgroups=corpus -DexcludedGroups=`: 56 tests green, 790 output files. `corpus-baseline.sha256` beside this file holds one SHA-256 per output file; its own digest starts `ac9eb04a7f8e95b5`. It replaces step 1's manifest (`.agents/untyped-grammar/corpus-baseline.sha256`), which is that of the typed grammar at 5136df4 and was never refreshed after step 1 changed output on purpose, in phase 3 (the 3 indexed lines) and in P5.3 (the `$__item` prefix). The two manifests differ in 51 files: the 51 view templates whose label loops now bind `$__item`, two of which, `view-templates/28.efx.txt` and `30.efx.txt`, also hold the 3 indexed lines. With `$__item` read back as `$item`, those two are the only files that differ from step 1's baseline output, in those 3 lines, as at gate 5.
- **How to compare** after a corpus run: `cd target/corpus/output && sha256sum --quiet -c ../../../.agents/model-harmonisation/corpus-baseline.sha256 && find . -type f | wc -l` must print 790 and nothing else. Every item must pass it unchanged (plan, section 3).
- **Public types:** `public-types.txt` beside this file lists the 281 types of `src/main/java` that are public to a consumer (public, and nested only in public types), with their kind, read from the compiled classes with `javap -public`. The generated parser, lexer, listener and visitor of the EFX grammars are left out: they are built from the SDK's grammar, which step 2 does not change. H7 lists the public types removed, renamed or reshaped against this list.

## Decisions

## Open questions
