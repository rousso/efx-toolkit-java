# Progress: EFX-2 untyped grammar

Single record of the work described in `plan.md`. Update it at the end of every session: item status, gate results with their numbers, the commit ids of both repositories, decisions taken, open questions for Yannis.

**Last updated:** 2026-10-31, by the implementing thread (session 1).
**Current phase:** 0, gate passed; phase 1 starts after Yannis has seen the gate report. **Next item:** P1.1.

## Work items

| Item | Status | Toolkit commit | SDK commit | Notes |
|---|---|---|---|---|
| P0.1 Create the branches | done 2026-10-31 | d094164 | 0c66c10 | `feature/untyped-grammar` from `develop` (toolkit, `89e0821`) and from `efx-2` (SDK, `abd9d85`) |
| P0.2 Build environment | done 2026-10-31 | 2db4e16 | | Environment `rousso`: Java 21 compiling for 11, Maven 3.9.11. SDK branch installed locally as 2.0.0-SNAPSHOT; `eforms-core-java` 1.10.0-SNAPSHOT and `eforms-sdk` 1.16.0-SNAPSHOT resolved from central.sonatype.com. `mvn -nsu test`: 1,550 tests green, the unpacked `Efx.g4` identical to the SDK branch. `-nsu` keeps Maven from replacing the local 2.0.0-SNAPSHOT with the published one |
| P0.3 Add `plan.md` and `progress.md` | done 2026-10-31 | d094164 | 0c66c10 | |
| P0.4 Opt-in corpus test and baseline | done 2026-10-31 | 5136df4, (this commit) | | `SdkCorpus` (unpacks the eforms-sdk jar on the test classpath with eforms-core's `ArchiveUtils.unzip`, the way eforms-core unpacks a downloaded SDK), `SdkCorpusDependencyFactory` (real `SdkSymbolResolver`, XPath script generator, Schematron generator, `MarkupGeneratorMock`), `SdkCorpusTranslationTest` (tag `corpus`, one dynamic test per corpus file, outputs under `target/corpus/output`, previous outputs deleted first). pom.xml: property `excludedGroups=corpus` and a test-scope `eforms-sdk` 2.0 dependency, approved by Yannis on 31 October. Default run: 1,554 tests green, the corpus test excluded. Corpus run (`mvn -nsu test -Dgroups=corpus -DexcludedGroups=`): 56 green, outputs identical to a standalone run. Baseline captured from 5136df4, see the section Baseline |
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
| 0 | 2026-10-31 | 1,554 green (default run, corpus excluded); 56 green (corpus run) | Baseline captured: 52 templates and 4 rules files, 790 output files, identical to the run before the commit and, for the templates and the 3 stage files, to `develop` | Templates 388.7 s, rules 69.6 s (`rules.efx` 69.4 s), 461.0 s in all; 411 to 461 s over 4 runs | Not yet (phase 3) | 5136df4 (test), this commit (record) | 0c66c10 |
| 1 | | | | | | | |
| 2 | | | | | | | |
| 3 | | | | | | | |
| 4 | | | | | | | |
| 5 | | | | | | | |

## Baseline

Captured 2026-10-31 from toolkit commit 5136df4 (typed grammar, P0.5 fix included) and SDK commit 0c66c10, in a separate worktree, with `mvn -nsu test -Dgroups=corpus -DexcludedGroups=`.

- **Outputs:** project files `untyped-grammar/corpus-baseline-5136df4/output/` (27 MB, 790 files: 52 template outputs and 738 Schematron files and indexes). The output is deterministic: four runs gave the same bytes.
- **Manifest:** `corpus-baseline.sha256` beside this file, one SHA-256 per output file, so the comparison needs nothing outside the repository. Its own digest starts `4f648c8c7ad05398`.
- **How to compare** after a corpus run: `cd target/corpus/output && sha256sum --quiet -c ../../../.agents/untyped-grammar/corpus-baseline.sha256 && find . -type f | wc -l` must print 790 and nothing else. Phase 3 expects exactly two template files to differ, `view-templates/28.efx.txt` and `30.efx.txt`, in the 3 indexed lines the plan names.
- **Timings** (4-core cloud container, one surefire JVM, `rules.efx` translated first, then the stage files, then the templates; the toolkit's DEBUG test logging stays on): 52 templates 388.7 s, `rules.efx` 69.4 s, stage files 0.2 s together, 461.0 s for the whole test class. Four runs on 31 October gave 411 to 461 s for the class and 352 to 389 s for the templates, so differences under about 10% are noise on this machine. The design-time figures (301 s warm, 360 s cold) came from a standalone runner and are not comparable.

Digest per corpus file: the first 16 hex digits of the SHA-256 of a template's output, or, for a rules file, of the sorted manifest of its output directory.

<details><summary>56 digests</summary>

| Corpus file | Digest | Outputs |
|---|---|---|
| `view-templates/1.efx` | `e94d8ff1a1d8c078` | 1 file |
| `view-templates/2.efx` | `69ff0f837dd17ab5` | 1 file |
| `view-templates/3.efx` | `b35d6e776bdc703e` | 1 file |
| `view-templates/4.efx` | `949c04e2d8eec6b8` | 1 file |
| `view-templates/5.efx` | `f4460cc4d901477c` | 1 file |
| `view-templates/6.efx` | `5482bb96862f3e6a` | 1 file |
| `view-templates/7.efx` | `3098612cd8bc4cec` | 1 file |
| `view-templates/8.efx` | `d0049649e8a8ffa3` | 1 file |
| `view-templates/9.efx` | `6b5dc8cb312de029` | 1 file |
| `view-templates/10.efx` | `1153ddd5a7d70687` | 1 file |
| `view-templates/11.efx` | `bfe0a53ef4326960` | 1 file |
| `view-templates/12.efx` | `2364c94822f46936` | 1 file |
| `view-templates/13.efx` | `ac2d68b84294610c` | 1 file |
| `view-templates/14.efx` | `6e78308ae3534642` | 1 file |
| `view-templates/15.efx` | `e3b48aab47718fab` | 1 file |
| `view-templates/16.efx` | `38a6f89f04fc4363` | 1 file |
| `view-templates/17.efx` | `a0b92feb16a8076f` | 1 file |
| `view-templates/18.efx` | `c41faff5fdd19b24` | 1 file |
| `view-templates/19.efx` | `5a07a0f0394a15ca` | 1 file |
| `view-templates/20.efx` | `2ab42e779a1d0d51` | 1 file |
| `view-templates/21.efx` | `237e84a7415240a3` | 1 file |
| `view-templates/22.efx` | `1e0cd37e66496537` | 1 file |
| `view-templates/23.efx` | `4b0c7ace258ea07b` | 1 file |
| `view-templates/24.efx` | `f31320b3748ed8be` | 1 file |
| `view-templates/25.efx` | `c6a8d00b0c4763c1` | 1 file |
| `view-templates/26.efx` | `3138f97fc80a9dcf` | 1 file |
| `view-templates/27.efx` | `c89cfc0bad54725f` | 1 file |
| `view-templates/28.efx` | `1f26fea8e93b8567` | 1 file |
| `view-templates/29.efx` | `18da8bb31e4af161` | 1 file |
| `view-templates/30.efx` | `cdb64b5bcc264d79` | 1 file |
| `view-templates/31.efx` | `29ed2ff3cd78c923` | 1 file |
| `view-templates/32.efx` | `941267beb95b8979` | 1 file |
| `view-templates/33.efx` | `bdef3e5e2ba0a167` | 1 file |
| `view-templates/34.efx` | `6666b15580707aa4` | 1 file |
| `view-templates/35.efx` | `ed49b213ca5d0217` | 1 file |
| `view-templates/36.efx` | `4b04d323a4726f69` | 1 file |
| `view-templates/37.efx` | `966ba661f32115dc` | 1 file |
| `view-templates/38.efx` | `0566d986913062a0` | 1 file |
| `view-templates/39.efx` | `0566d986913062a0` | 1 file |
| `view-templates/40.efx` | `5060983331283f22` | 1 file |
| `view-templates/CEI.efx` | `2547db39d977a2de` | 1 file |
| `view-templates/E1.efx` | `a90710c1445aef4d` | 1 file |
| `view-templates/E2.efx` | `87e01ee847650d64` | 1 file |
| `view-templates/E3.efx` | `0d72a49413369706` | 1 file |
| `view-templates/E4.efx` | `63217e2a9bbc91d4` | 1 file |
| `view-templates/E5.efx` | `760e2d04e6c028b3` | 1 file |
| `view-templates/E6.efx` | `8aec1940289dc1f5` | 1 file |
| `view-templates/T01.efx` | `e60af0734fe3117b` | 1 file |
| `view-templates/T02.efx` | `341fe30c50fc682e` | 1 file |
| `view-templates/X01.efx` | `c836f3ee375ec991` | 1 file |
| `view-templates/X02.efx` | `b070fe38240de714` | 1 file |
| `view-templates/summary.efx` | `e3b0c44298fc1c14` | 1 file |
| `validation/rules.efx` | `ec9b6a7dc653aade` | 575 files |
| `validation/stage-1a.efx` | `a38fbde4f49a2bfb` | 103 files |
| `validation/stage-4m.efx` | `99924333db54af9b` | 31 files |
| `validation/stage-5m.efx` | `b308c958b75e8548` | 29 files |

</details>

## Decisions taken during implementation

- 2026-10-31: P0.5 before P0.4, so that the baseline includes `rules.efx` from the start.
- 2026-10-31: Yannis approved two pom.xml additions for P0.4 beyond the plan's listed lines: `<excludedGroups>corpus</excludedGroups>` and a test-scope `eforms-sdk` dependency at `${version.eforms-sdk-2}`.
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

None. (Answered 2026-10-31: Yannis approved both pom.xml additions for P0.4.)

## Session log

- 2026-10-31, design thread: plan approved by Yannis; branches created in both forks; `plan.md` and `progress.md` committed.
- 2026-10-31, implementing thread, session 1: P0.2 and P0.5 done and pushed; phase 0 review applied; Yannis approved the two pom.xml additions; P0.4 committed; baseline captured; gate 0 passed. Scratch comparison with `develop` (same harness, real SDK): outputs identical, except that `rules.efx` now translates.
