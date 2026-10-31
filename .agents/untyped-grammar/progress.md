# Progress: EFX-2 untyped grammar

Single record of the work described in `plan.md`. Update it at the end of every session: item status, gate results with their numbers, the commit ids of both repositories, decisions taken, open questions for Yannis.

**Last updated:** 2026-10-31, by the implementing thread (session 1).
**Current phase:** 0. **Next item:** P0.4, waiting for Yannis's answer on the pom.xml question below.

## Work items

| Item | Status | Toolkit commit | SDK commit | Notes |
|---|---|---|---|---|
| P0.1 Create the branches | done 2026-10-31 | d094164 | 0c66c10 | `feature/untyped-grammar` from `develop` (toolkit, `89e0821`) and from `efx-2` (SDK, `abd9d85`) |
| P0.2 Build environment | done 2026-10-31 | 2db4e16 | | Environment `rousso`: Java 21 compiling for 11, Maven 3.9.11. SDK branch installed locally as 2.0.0-SNAPSHOT; `eforms-core-java` 1.10.0-SNAPSHOT and `eforms-sdk` 1.16.0-SNAPSHOT resolved from central.sonatype.com. `mvn -nsu test`: 1,550 tests green, the unpacked `Efx.g4` identical to the SDK branch. `-nsu` keeps Maven from replacing the local 2.0.0-SNAPSHOT with the published one |
| P0.3 Add `plan.md` and `progress.md` | done 2026-10-31 | d094164 | 0c66c10 | |
| P0.4 Opt-in corpus test and baseline | in progress | | | Drafted and run locally, not committed: `SdkCorpus` (unpacks the eforms-sdk jar with eforms-core's `ArchiveUtils.unzip`), `SdkCorpusDependencyFactory`, `SdkCorpusTranslationTest` (tag `corpus`, one dynamic test per corpus file, outputs under `target/corpus/output`). 56 files translated in 411 s, outputs identical to a standalone run. Needs two pom.xml additions, put to Yannis (open question 1). Copy of the drafts: project files `untyped-grammar/p04-draft/`. Baseline captured after P0.5 so that it includes `rules.efx` |
| P0.5 Fix the `rules.efx` failure (symbol resolver) and add the test | done 2026-10-31 | 8f73f51, bef9092 | | `XPathSymbolResolver.isFieldRepeatableFromContext(SdkField, SdkField)` now answers "single value" first when the field is the context, before the repeatable and multilingual checks. Two tests: `testMultilingualTextFieldReference_FromOwnContext_InScalarContext` (translation) and `fieldToField_multilingualFieldItself_shouldReturnScalarPath` (resolver contract, runs in both resolver test classes); both failed before the fix. 1,553 tests green. Real SDK, compared with `develop`: the 52 templates and the 3 stage files byte-identical, `rules.efx` now translates (575 outputs) where `develop` fails at line 6522:9. Second commit from the phase 0 review: the field-context overload now delegates to the node-context overload (it had become a line-for-line copy, which is how the two drifted apart), plus a third test, a multilingual field in its own predicate (`testMultilingualTextFieldReference_InOwnPredicate`, fails on `develop`); 1,554 tests green, corpus unchanged |
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

- 2026-10-31: P0.5 before P0.4, so that the baseline includes `rules.efx` from the start.
- 2026-10-31: the phase 0 review (`simplify`, `code-review`) was applied to the P0.5 code in a second P0.5 commit, since pushed history is not rewritten.
- 2026-10-31: the corpus test translates the 3 stage files on their own as well as through `rules.efx`, which includes them: 0.2 s, and the plan counts 4 rules files.

## Corrections to facts in plan.md

- The SDK's rules files are `stage-1a.efx`, `stage-4m.efx`, `stage-5m.efx` and `rules.efx` (the plan says `stage-1b` and `stage-2a`). `rules.efx` includes the three stage files at its lines 1, 15778 and 17176. The line total, 32,209, is right.

## Observations for later phases

From the phase 0 review, left for P2.2 (the leaf rule implemented in one place):
- `XPathSymbolResolver.isFieldRepeatableFromContext(SdkField, SdkNode)` walks the node ancestry itself, repeating `isNodeRepeatableFromContext`.
- `EfxExpressionTranslatorV2.fieldMayReturnMultipleValues` repeats the resolver's field-as-context rule by comparing raw identifiers, while the resolver compares resolved fields; an alias on one side and an id on the other would still disagree. The public `SymbolResolver.isFieldRepeatableFromContext(String, String)` takes only a node id, and its Javadoc mentions neither multilingual fields nor the field-as-context rule.
- The `-Language` attribute field of a multilingual field (for example `BT-99-Lot-Language`) is typed as a scalar although it yields one value per language; `rules.efx` relies on that (R-DCS-FM2, `... satisfies BT-99-Lot-Language == $lg`), so it must not change silently.

## Open questions for Yannis

1. (asked 2026-10-31 in the implementing thread) P0.4 needs two pom.xml additions beyond the plan's `<visitor>true</visitor>`: the property `<excludedGroups>corpus</excludedGroups>`, so that `mvn test` skips the corpus test and `mvn test -Dgroups=corpus -DexcludedGroups=` runs it, and a test-scope dependency on `eu.europa.ted.eforms:eforms-sdk:${version.eforms-sdk-2}` (managed in `dependencyManagement`, as the pom does for every dependency), so that the jar the grammar comes from is on the test classpath. Recommended: both.

## Session log

- 2026-10-31, design thread: plan approved by Yannis; branches created in both forks; `plan.md` and `progress.md` committed.
- 2026-10-31, implementing thread, session 1: P0.2 and P0.5 done and pushed; P0.4 drafted and run locally, waiting for the pom.xml answer; phase 0 review applied. Scratch comparison with `develop` (same harness, real SDK): outputs identical, except that `rules.efx` now translates.
