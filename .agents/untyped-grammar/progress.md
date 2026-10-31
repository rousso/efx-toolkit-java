# Progress: EFX-2 untyped grammar

Single record of the work described in `plan.md`. Update it at the end of every session: item status, gate results with their numbers, the commit ids of both repositories, decisions taken, open questions for Yannis.

**Last updated:** 2026-10-31, by the implementing thread (session 1).
**Current phase:** 2. **Next item:** P2.1.

## Work items

| Item | Status | Toolkit commit | SDK commit | Notes |
|---|---|---|---|---|
| P0.1 Create the branches | done 2026-10-31 | d094164 | 0c66c10 | `feature/untyped-grammar` from `develop` (toolkit, `89e0821`) and from `efx-2` (SDK, `abd9d85`) |
| P0.2 Build environment | done 2026-10-31 | 2db4e16 | | Environment `rousso`: Java 21 compiling for 11, Maven 3.9.11. SDK branch installed locally as 2.0.0-SNAPSHOT; `eforms-core-java` 1.10.0-SNAPSHOT and `eforms-sdk` 1.16.0-SNAPSHOT resolved from central.sonatype.com. `mvn -nsu test`: 1,550 tests green, the unpacked `Efx.g4` identical to the SDK branch. `-nsu` keeps Maven from replacing the local 2.0.0-SNAPSHOT with the published one |
| P0.3 Add `plan.md` and `progress.md` | done 2026-10-31 | d094164 | 0c66c10 | |
| P0.4 Opt-in corpus test and baseline | done 2026-10-31 | 5136df4, a317994 | | `SdkCorpus` (unpacks the eforms-sdk jar on the test classpath with eforms-core's `ArchiveUtils.unzip`, the way eforms-core unpacks a downloaded SDK), `SdkCorpusDependencyFactory` (real `SdkSymbolResolver`, XPath script generator, Schematron generator, `MarkupGeneratorMock`), `SdkCorpusTranslationTest` (tag `corpus`, one dynamic test per corpus file, outputs under `target/corpus/output`, previous outputs deleted first). pom.xml: property `excludedGroups=corpus` and a test-scope `eforms-sdk` 2.0 dependency, approved by Yannis on 31 October. Default run: 1,554 tests green, the corpus test excluded. Corpus run (`mvn -nsu test -Dgroups=corpus -DexcludedGroups=`): 56 green, outputs identical to a standalone run. Baseline captured from 5136df4, see the section Baseline |
| P0.5 Fix the `rules.efx` failure (symbol resolver) and add the test | done 2026-10-31 | 8f73f51, bef9092 | | `XPathSymbolResolver.isFieldRepeatableFromContext(SdkField, SdkField)` now answers "single value" first when the field is the context, before the repeatable and multilingual checks. Two tests: `testMultilingualTextFieldReference_FromOwnContext_InScalarContext` (translation) and `fieldToField_multilingualFieldItself_shouldReturnScalarPath` (resolver contract, runs in both resolver test classes); both failed before the fix. 1,553 tests green. Real SDK, compared with `develop`: the 52 templates and the 3 stage files byte-identical, `rules.efx` now translates (575 outputs) where `develop` fails at line 6522:9. Second commit from the phase 0 review: the field-context overload now delegates to the node-context overload (it had become a line-for-line copy, which is how the two drifted apart), plus a third test, a multilingual field in its own predicate (`testMultilingualTextFieldReference_InOwnPredicate`, fails on `develop`); 1,554 tests green, corpus unchanged |
| P1.1 Translation tests for untested features | done 2026-10-31 | 14af494 | | 152 new tests, each placed in the region of its feature. `EfxExpressionTranslatorV2Test`, 144: `is unique in` and `in` per type with a reference on either side, comparisons with the reference on the right, duration addition and subtraction, date minus date, duration multiplication with a late-bound operand, conditionals per type (typed and with a reference in either branch), concatenated iterations per type, multiplication of references, a list of references only, an attribute reference as a sequence, `count` per type, `number('…')`, `subtract-duration`, indexers per type, `count-duplicates` and `get-duplicates` per type, `value-union`, `value-intersect`, `value-except`, `index-of` and `sequence-equal` with a reference on either side per type, `subsequence` per type, and `format-short`, `format-medium` and `format-long` of a time, a date and time, and a reference, rejected outside templates. `EfxTemplateTranslatorV2Test`, 4: number, date, time and duration function declarations. `EfxRulesTranslatorV2Test`, 4, with fixtures: number, date, time and duration global variables. Each expected XPath was read, and the doubtful ones evaluated with Saxon, before it was written down; the fixtures' expected files were generated, checked, and reproduce an existing fixture byte for byte. 1,706 tests green. JaCoCo: every enter/exit method of the three V2 translators runs except 9 that are not language features: the six `exit*FunctionInvocation` handlers of the expression translator and `exitBooleanFunctionInvocation` of the rules translator (a cast on a function call, removed in phase 3), `exitIncludeDirective` (a guard: both translators expand includes before parsing), and `exitShorthandFieldValueReferenceFromContextField` of the template translator (unreachable, see Observations) |
| P1.2 Invalid-operation table | done 2026-10-31 | 8b9174c, phase 1 review commit | | `EfxInvalidOperationV2Test`: one parameterised test over 236 expressions, in 22 groups each headed by a comment naming the rule its rows break: comparison, `+`, `-`, `*`, `/` and `%`, `and`/`or`/`not()`, `like`, `in`, `is unique in`, the condition and the branches of `if`, the body of `every`/`some`, mixed lists, iterator declarations, a sequence where a single value is required, a node used as a value, `is present` on a non-reference, predicates and indexes, raw values, built-in argument types, arity and result types. Every operator meets each wrong operand primitive at least once, binary rows come in both orders and with literals as well as references. Each row asserts `ParseCancellationException`; all 236 pass today. Left out on purpose: `date + duration` and `date - duration` (open question), and anything scalar-to-sequence promotion makes valid (`sort(1)`, `1 is empty`). A search for invalid expressions accepted today found none: the pop-time checks catch every reference case tried. 1,942 tests green. Phase 1 review commit: a row must now fail with a syntax error (a bare `ParseCancellationException`) or a type error (`TypeMismatchException` or `InvalidArgumentException`), so that no row passes because a name does not resolve; five rows added, `+` between a literal and a reference (4) and a numeric predicate on a node (1); the predicate rule now reads "A predicate is a boolean, and an index on a sequence is a number", since a node takes only a boolean predicate. 241 rows. Gate 1 follow-up, from a review by Codex that Yannis passed on: the numeric node predicate is removed again and the rule's first wording restored, because the plan makes `ND-SubNode[1]` valid (a number on a reference selects a position); 60 rows added, so that `and`, `or`, `not()`, the `if` condition and the `every`/`some` body each meet every wrong primitive, the cardinality rows come in both orders (`1 + BT-00-Repeatable-Number`, `1 + [2]`), `if` branches differ in cardinality, a node reaches `count`, `is empty` and `has duplicates`, and the typed parameters of the functions are challenged one at a time, with the other arguments right (including the third argument of `substring` and of `replace`, and a third `concat` argument). 300 rows. Second follow-up, from Codex's check of that commit: each binary operator (`+`, `-`, `*`, `/`, `%`, `and`, `or`) now meets every wrong primitive in each operand position with a valid other operand, and every pair of individually valid operands that no overload accepts (`2020-01-01Z + 1`, `1 - P1D`), checked by script; `text`, `distinct-values`, `count-duplicates`, `get-duplicates`, `sort` and `reverse` get rows; and a second parameterised test translates the template-only functions inside a template, one row per parameter and three arity rows, since outside a template they are rejected whatever their arguments. 336 expression rows and 21 template rows; after this, every built-in function has a row for each of its parameters |
| P1.3 Record which rows are syntax errors today | done 2026-10-31 | 4d1685f | | 147 of the 236 rows are syntax errors today, 89 type mismatches; after the phase 1 review, 148 of 241 and 93; after the gate 1 follow-up, 203 of 300 and 97; after the second follow-up, 252 of 357 and 105; see the section Invalid-operation table |
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
| 0 | 2026-10-31 | 1,554 green (default run, corpus excluded); 56 green (corpus run) | Baseline captured: 52 templates and 4 rules files, 790 output files, identical to the run before the commit and, for the templates and the 3 stage files, to `develop` | Templates 388.7 s, rules 69.6 s (`rules.efx` 69.4 s), 461.0 s in all; 411 to 461 s over 4 runs | Not yet (phase 3) | 5136df4 (test), a317994 (record) | 0c66c10 |
| 1 | 2026-10-31 | 1,947 green (default run, corpus excluded); every V2 translator handler of a language feature runs; Yannis reviewed the invalid-operation table and passed the gate ("looks quite complete"); follow-ups from Codex's two reviews of the table: 357 rows, 2,063 tests green | Not run: phase 1 changes tests only | Not measured, for the same reason | Not yet (phase 3) | 33ec1e4 (review), 3762ff7 and d6bb3fe (decisions) | 0c66c10 |
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

## Invalid-operation table: how each row fails today

Recorded for P1.3 on 2026-10-31 at the P1.2 commit, by translating every row on its own, and brought up to date after the phase 1 review and the gate 1 follow-up. Of the 357 rows (336 expressions and 21 template-only function calls translated in a template), 252 are syntax errors today (`ParseCancellationException` itself, raised by the parser) and 105 are compilation errors (all `TypeMismatchException`). In phase 3 the syntax errors whose mistake is a type become type errors. The 16 arity rows stay syntax errors, because the plan keeps a misspelt name or a wrong arity a syntax error, and so may rows that the new grammar still rejects by their shape, such as `'a':rawValue`; phase 3 records which. In phase 4 every row is tightened to the error it should raise.

The split follows the kind of operand, not the rule: a row is a syntax error when the operand in the wrong place has a type the grammar can see (a literal, a list, a function call or an operator expression), and a type error when it is a field reference or a variable, which the grammar leaves late-bound and the translator checks when it pops it. Nodes, `is present`, raw values and arity are syntax errors in every row.

<details><summary>Syntax-error rows per rule</summary>

| Rule | Rows | Syntax errors today | Type errors today |
|---|---|---|---|
| A comparison needs two operands of the same type | 22 | `'a' == 1`, `1 == 'a'`, `'a' < TRUE`, `'a' <= 2020-01-01Z`, `'a' >= P1D`, `1 == TRUE`, `10:00:00Z > 1`, `TRUE == 2020-01-01Z`, `2020-01-01Z == 10:00:00Z`, `P1D > 10:00:00Z` | 12 |
| + adds two numbers, two durations, or a duration to a date | 18 | `'a' + 'b'`, `'a' + 1`, `1 + 'a'`, `TRUE + 1`, `2020-01-01Z + 2020-01-02Z`, `10:00:00Z + P1D`, `1 + P1D`, `1 + 10:00:00Z`, `2020-01-01Z + 1` | 9 |
| - subtracts two numbers, two durations, two dates, or a duration from a date | 17 | `'a' - 'b'`, `TRUE - FALSE`, `1 - 2020-01-01Z`, `2020-01-01Z - 1`, `11:00:00Z - 10:00:00Z`, `P1D - 2020-01-01Z`, `TRUE - 1`, `10:00:00Z - 1`, `1 - 'a'`, `1 - 10:00:00Z`, `1 - P1D` | 6 |
| * multiplies two numbers, or a number and a duration | 11 | `'a' * 2`, `TRUE * 2`, `2020-01-01Z * 2`, `10:00:00Z * 2`, `P1D * P2D`, `2 * TRUE`, `2 * 10:00:00Z` | 4 |
| / and % divide numbers only | 21 | `'a' / 2`, `TRUE / 2`, `2 / 2020-01-01Z`, `P2D / 2`, `2 / P1D`, `5 % 'a'`, `TRUE % 2`, `2020-01-01Z % 2`, `2020-01-01Z / 2`, `2 / TRUE`, `2 / 10:00:00Z`, `'a' % 2`, `10:00:00Z % 2`, `2 % TRUE`, `2 % 2020-01-01Z`, `2 % P1D` | 5 |
| and, or and not() take booleans | 27 | `1 and TRUE`, `TRUE or 'a'`, `2020-01-01Z or TRUE`, `P1D and BT-00-StartTime`, `not(1)`, `TRUE and 2020-01-01Z`, `10:00:00Z or TRUE`, `TRUE or P1D`, `not(2020-01-01Z)`, `not(10:00:00Z)`, `2020-01-01Z and TRUE`, `10:00:00Z and TRUE`, `P1D and TRUE`, `TRUE and 1`, `TRUE and 'a'`, `TRUE and P1D`, `1 or TRUE`, `'a' or TRUE`, `P1D or TRUE`, `TRUE or 2020-01-01Z`, `TRUE or 10:00:00Z` | 6 |
| like matches text | 6 | `1 like '[0-9]'`, `TRUE like 'true'`, `10:00:00Z like '10'` | 3 |
| in looks for a value in a sequence of the same type | 9 | `1 in ['a', 'b']`, `'a' in [1, 2]`, `TRUE in [1, 2]`, `2020-01-01Z not in [10:00:00Z]`, `P1D in [1, 2]` | 4 |
| is unique in looks for a value in a sequence of the same type | 6 | `'a' is unique in [1, 2]`, `2020-01-01Z is unique in [P1D]`, `TRUE is not unique in [10:00:00Z]` | 3 |
| The condition of if ... then ... else is a boolean | 7 | `if 1 then 'a' else 'b'`, `if 'a' then 1 else 2`, `if 2020-01-01Z then TRUE else FALSE`, `if 10:00:00Z then 1 else 2`, `if P1D then 1 else 2` | 2 |
| The two branches of if ... then ... else have the same type | 8 | `if TRUE then 1 else 'a'`, `if TRUE then 2020-01-01Z else P1D`, `if TRUE then [1] else ['a']`, `if TRUE then 1 else ['a']` | 4 |
| The body of every ... satisfies and some ... satisfies is a boolean | 6 | `some number:$n in [1, 2] satisfies $n + 1`, `every text:$x in ['a'] satisfies 'a'` | 4 |
| The values of a list have one type | 8 | `[1, 'a']`, `['a', TRUE]`, `[2020-01-01Z, 10:00:00Z]`, `[P1D, 1]`, `count([1, 'a'])` | 3 |
| An iterator variable has the type of the sequence it iterates over | 7 | `for number:$n in ['a'] return $n`, `for text:$t in [1, 2] return $t`, `for indicator:$i in [1] return $i`, `every time:$t in [2020-01-01Z] satisfies TRUE` | 3 |
| A sequence cannot stand where a single value is required | 18 | `['a', 'b'] == 'a'`, `['a', 'b'] like 'a'`, `[1, 2] in [1, 2, 3]`, `upper-case(['a'])`, `[1, [2, 3]]`, `1 + [2]` | 12 |
| A node has no value | 14 | `ND-SubNode == 'a'`, `'a' == ND-Root`, `ND-SubNode + 1`, `ND-SubNode in ['a']`, `string-length(ND-SubNode)`, `count(ND-SubNode)`, `count(ND-RepeatableNode)`, `ND-SubNode is empty`, `ND-SubNode has duplicates`, `distinct-values(ND-RepeatableNode)`, `count-duplicates(ND-RepeatableNode)`, `get-duplicates(ND-RepeatableNode)`, `sort(ND-RepeatableNode)`, `reverse(ND-RepeatableNode)` | 0 |
| is present applies to a field or node reference | 6 | `1 is present`, `'a' is present`, `(1 + 1) is present`, `upper-case(BT-00-Text) is present`, `some text:$x in ['a'] satisfies $x is present`, `for text:$x in ['a'] return $x is present` | 0 |
| A predicate is a boolean or a number, and an index on a sequence is a number | 9 | `BT-00-Text['x']`, `BT-00-Repeatable-Text[2020-01-01Z]`, `BT-00-Repeatable-Text[P1D]`, `ND-SubNode['x'] is present`, `['a', 'b']['x']`, `['a', 'b'][TRUE]`, `[1, 2][2020-01-01Z]` | 2 |
| A raw value is read from a field | 2 | `ND-SubNode:rawValue`, `'a':rawValue` | 0 |
| Each argument of a built-in function has the type its parameter declares | 95 | `string-length(1)`, `upper-case(TRUE)`, `substring(1, 2)`, `substring('abc', 'x')`, `contains(1, 2)`, `starts-with('a', 1)`, `concat('a', 1)`, `string-join([1, 2], ',')`, `string-join(['a'], 1)`, `replace('a', 1, 'b')`, `pad-left('a', 'b', 'c')`, `split(1, ',')`, `empty(1)`, `sum(['a', 'b'])`, `min([2020-01-01Z])`, `absolute('a')`, `round(TRUE)`, `year('2020')`, `day(10:00:00Z)`, `hours(2020-01-01Z)`, `years(1)`, `date(1)`, `time(TRUE)`, `day-time-duration(1)`, `indicator('x')`, `number(2020-01-01Z)`, `number(P1D)`, `add-duration(10:00:00Z, P1D)`, `add-duration(2020-01-01Z, 1)`, `subtract-duration(P1D, 2020-01-01Z)`, `index-of(['a', 'b'], 1)`, `sequence-equal(['a'], [1])`, `value-union(['a'], [1])`, `value-intersect([1], [2020-01-01Z])`, `subsequence(['a', 'b'], 'x')`, `subsequence(['a', 'b'], 1, 'x')`, `contains(1, 'a')`, `starts-with(1, 'a')`, `ends-with(1, 'a')`, `ends-with('a', 1)`, `max(['a'])`, `index-of-substring(1, 'a')`, `index-of-substring('a', 1)`, `round-down('a')`, `round-up(TRUE)`, `month(10:00:00Z)`, `seconds(2020-01-01Z)`, `months('P1M')`, `substring('abc', 1, 'x')`, `substring-before(1, 'a')`, `substring-before('a', 1)`, `substring-after(1, 'a')`, `substring-after('a', 1)`, `concat(1, 'a')`, `concat('a', 'b', 1)`, `lower-case(1)`, `normalize-space(1)`, `trim(1)`, `trim-left(1)`, `trim-right(1)`, `pad-left(1, 2, ' ')`, `pad-left('a', 2, 1)`, `pad-right(1, 2, ' ')`, `pad-right('a', 'b', ' ')`, `pad-right('a', 2, 1)`, `replace(1, 'a', 'b')`, `replace('a', 'b', 1)`, `replace-regex(1, 'a', 'b')`, `replace-regex('a', 1, 'b')`, `replace-regex('a', 'b', 1)`, `repeat(1, 2)`, `repeat('a', 'b')`, `url-encode(1)`, `capitalize-first(1)`, `split('a', 1)`, `subtract-duration(1, P1D)`, `subtract-duration(2020-01-01Z, 1)`, `text([1])` | 17 |
| A built-in function takes the number of arguments it declares | 13 | `upper-case()`, `concat()`, `count()`, `count([1], [2])`, `string-length('a', 'b')`, `contains('a')`, `substring('a')`, `current-date(1)`, `add-duration(2020-01-01Z)`, `not(TRUE, FALSE)`, `value-union(['a'])`, `sequence-equal(['a'], ['b'], ['c'])`, `current-time(1)` | 0 |
| The result of a built-in function has the type the function declares | 6 | `count(['a']) like 'a'`, `string-length('a') and TRUE`, `upper-case('a') + 1`, `current-date() + 1`, `year(current-date()) == 'a'`, `contains('a', 'b') + 1` | 0 |
| Each argument of a template-only function has the type its parameter declares (in a template) | 18 | `format-number('x', '0.00')`, `format-number(1, 2)`, `format-short(1)`, `format-short('a')`, `format-short(1, 10:00:00Z)`, `format-short(2020-01-01Z, 1)`, `format-medium(TRUE)`, `format-medium(P1D, 10:00:00Z)`, `format-medium(2020-01-01Z, 'a')`, `format-long(10:00:00Z, 10:00:00Z)`, `preferred-language('a')`, `preferred-language-text(1)` | 6 |
| A template-only function takes the number of arguments it declares (in a template) | 3 | `format-number(1)`, `format-short()`, `preferred-language()` | 0 |
| All rules | 357 | 252 | 105 |

</details>

## Decisions taken during implementation

- 2026-10-31: P0.5 before P0.4, so that the baseline includes `rules.efx` from the start.
- 2026-10-31: Yannis approved two pom.xml additions for P0.4 beyond the plan's listed lines: `<excludedGroups>corpus</excludedGroups>` and a test-scope `eforms-sdk` dependency at `${version.eforms-sdk-2}`.
- 2026-10-31: the phase 0 review (`simplify`, `code-review`) was applied to the P0.5 code in a second P0.5 commit, since pushed history is not rewritten.
- 2026-10-31: the corpus test translates the 3 stage files on their own as well as through `rules.efx`, which includes them: 0.2 s, and the plan counts 4 rules files.
- 2026-10-31, Yannis: `date + duration` and `date - duration` are operators of the language, for any date and duration operands, as in XPath and as two field references already translate today; `add-duration` and `subtract-duration` stay. Asked the same day, Yannis: `+` takes a duration and a date in either order, as XPath does; `duration - date` stays an error. P2.1 gives the operators their signatures, and phase 3 adds translation tests for the forms the typed grammar rejects (`2020-01-01Z + P1D`, `BT-00-StartDate + P1D`, `2020-01-01Z - P1D`, `P1D + 2020-01-01Z`, `BT-00-Duration + BT-00-StartDate`). The `+` and `-` rules of the invalid-operation table now name them; their rows were already outside the table.
- 2026-10-31, gate 1 follow-up: Yannis passed on a review of the table by Codex, and all four points were taken (see P1.2). `ND-SubNode[1] is present` leaves the table: the plan's bracket rule makes a number on a node select a position, so phase 3 adds it as a translation test instead. `count`, `is empty` and `has duplicates` on a node are errors, as the plan says that only `is present`, a bracket and a context take a node; Yannis was told, in case he wants node counting in the language.
- 2026-10-31: phase 1 review (`simplify`, `code-review`), applied in one P1.2 commit. Kept on purpose: the table's `@MethodSource` (P4.1 gives each row its expected exception, which `@ValueSource` cannot carry); late-bound booleans taken from iterator variables (see the indicator observation); the duration-field XPath written out in each test, as the file already does; one `@Test` per case, the convention of every V2 test class. Gate 1 coverage: every enter/exit method of the three V2 translators runs except the same 9 recorded for P1.1.

## Corrections to facts in plan.md

- The SDK's rules files are `stage-1a.efx`, `stage-4m.efx`, `stage-5m.efx` and `rules.efx` (the plan says `stage-1b` and `stage-2a`). `rules.efx` includes the three stage files at its lines 1, 15778 and 17176. The line total, 32,209, is right.

## Observations for later phases

From the phase 0 review, left for P2.2 (the leaf rule implemented in one place):
- `XPathSymbolResolver.isFieldRepeatableFromContext(SdkField, SdkNode)` walks the node ancestry itself, repeating `isNodeRepeatableFromContext`.
- `EfxExpressionTranslatorV2.fieldMayReturnMultipleValues` repeats the resolver's field-as-context rule by comparing raw identifiers, while the resolver compares resolved fields; an alias on one side and an id on the other would still disagree. The public `SymbolResolver.isFieldRepeatableFromContext(String, String)` takes only a node id, and its Javadoc mentions neither multilingual fields nor the field-as-context rule.
- The `-Language` attribute field of a multilingual field (for example `BT-99-Lot-Language`) is typed as a scalar although it yields one value per language; `rules.efx` relies on that (R-DCS-FM2, `... satisfies BT-99-Lot-Language == $lg`), so it must not change silently.

From P1.1:
- Indicator field values translate to the bare node (`PathNode/IndicatorField`), with no conversion to a boolean. Comparisons and `in` work, because a general comparison casts the node, but whatever takes the effective boolean value or compares items with `eq` does not: `if BT-00-Indicator then …` holds when the field says `false`, and `sequence-equal`, `index-of`, `value-union`, `value-intersect` and `value-except` treat the field's values as strings, so `sequence-equal(BT-00-Repeatable-Indicator, [TRUE, FALSE])` is always false and `index-of(BT-00-Repeatable-Indicator, TRUE)` finds nothing (both checked with Saxon). The same on `develop` (`XPathScriptGenerator.composeFieldValueReference`). The new tests take late-bound booleans from iterator variables rather than lock this in. Outside the plan's scope: generated output is sacred.
- A duration field multiplied by a number is rejected although `P1D * 2` and `2 * P1D` translate: `BT-00-Duration * 2` is a syntax error or a type mismatch in every position, and `2 * BT-00-Duration` is a type mismatch on its own (ANTLR picks the numeric late-bound alternative) but translates inside a duration comparison (`2 * BT-00-Duration > P1D`, which is what the new test uses). A defect of the typed grammar that phase 3 removes; phase 3 should add both forms as tests.
- `+` and `-` between a date and a duration translate when both operands are references (`BT-00-StartDate + BT-00-Duration`, through the late-bound dispatcher) but are rejected otherwise (`2020-01-01Z + P1D` is a syntax error, `BT-00-StartDate + P1D` a type mismatch); the typed grammar offers only `add-duration` and `subtract-duration`. The corpus uses neither the operators nor the functions. Yannis decided the operators are part of the language (see Decisions).
- EFX's duration is one type, but it translates to two XPath types that do not mix: `xs:dayTimeDuration` for days and weeks, `xs:yearMonthDuration` for months and years. Two consequences, pinned as they are by the P1.1 tests and the same on `develop`: `BT-00-Duration + P1D` (and `-`) raises a dynamic error when the field's unit is a year or a month, and `sequence-equal` on durations, which sorts both sequences, raises one as soon as a sequence holds both kinds (`Cannot compare xs:yearMonthDuration with xs:dayTimeDuration`, checked with Saxon). A question of the language's duration model, outside the plan's scope: generated output is sacred.
- The template translator's `exitShorthandFieldValueReferenceFromContextField` cannot run. The lexer emits `$value` only in TEMPLATE mode, where it parses as `contextFieldExpressionBlock` (tested); the grammar's other `$value` alternative, in `expressionBlock`, is reachable only from label blocks, whose LABEL mode never emits the token. Dead since `formattedExpressionBlock` took over `$value`; phase 3 can drop the alternative and its handler.

## Open questions for Yannis

None.

Answered: 2026-10-31, Yannis approved both pom.xml additions for P0.4. 2026-10-31, `date + duration` and `date - duration` are operators, and `duration + date` too (see Decisions).

## Session log

- 2026-10-31, design thread: plan approved by Yannis; branches created in both forks; `plan.md` and `progress.md` committed.
- 2026-10-31, implementing thread, session 1: P0.2 and P0.5 done and pushed; phase 0 review applied; Yannis approved the two pom.xml additions; P0.4 committed; baseline captured; gate 0 passed. Scratch comparison with `develop` (same harness, real SDK): outputs identical, except that `rules.efx` now translates. Phase 1 started: P1.1, P1.2 and P1.3 done and pushed. Phase 1 review applied; gate 1 checks green (1,947 tests, coverage as above); Yannis reviewed the table and passed gate 1; phase 2 started.
