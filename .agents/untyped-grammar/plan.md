# EFX-2 untyped grammar: implementation plan

**Status:** approved by Yannis Rousochatzakis (GitHub `rousso`) on 31 October 2026, in the project thread "Move EFX-2 type checking into transpiler". Nothing in this file may be changed without his word; `progress.md` beside it is the single record of progress.

**Sources:** the design doc "EFX-2 untyped grammar: design and plan" (https://claude.ai/artifact/6jm2neDsnvngVAC39a5juB) and its companion analysis "EFX-2 type safety today" (https://claude.ai/artifact/LQLxogWs3r2iprsXoTJMyh). This file is the self-contained copy of the design doc, with work-item ids added; the docs hold the same text plus two diagrams and the review comments.

**Repositories:** grammar in `rousso/eForms-SDK`, branch `feature/untyped-grammar` (from `efx-2`); toolkit in `rousso/efx-toolkit-java`, branch `feature/untyped-grammar` (from `develop`). The OP-TED repositories are never touched; `rousso/eforms-core-java` is built and read only.

## How to work with this plan

- Start every session by reading this file and `progress.md`; end every session by updating `progress.md`.
- Work items carry ids `P<phase>.<n>`. One commit per work item, the message starting with the id. The full test suite runs before every commit.
- A phase closes only when its gate passes; the gate results, with their numbers, go into `progress.md`.
- The section "Rules of engagement" is binding. When in doubt, stop and ask Yannis in the thread.

## Summary

The work replaces the typed EFX-2 grammar with an untyped one and moves type safety into the toolkit. Today `Efx.g4` has 229 rules and 783 alternatives, and 595 of those alternatives exist only to encode the type and cardinality of operands: six typed expression rules, six typed sequence rules, six typed function groups, and 290 late-bound variants that let a field or variable reference appear in a typed position. The new grammar has one `expression` rule with standard operator precedence, one list rule, one iterator rule, one declaration rule per declaration kind, and one alternative per built-in function. It should land at about 300 alternatives.

The toolkit takes over what the grammar did. It infers the type of every sub-expression bottom-up: literals carry their type, field references take theirs from the SDK metadata (type and repeatability from the current context), variables, parameters, functions and dictionaries take theirs from their declarations. Every operator and built-in is resolved against a signature catalogue that holds the overloads the typed grammar used to spell out. A scalar is promoted to a sequence wherever a sequence is expected, and everything else is rejected with a type error that names the operator and both operand types.

What stays the same: the EFX language as written in templates and rules, except the cast syntax that goes and the reach of `else`, `return` and `satisfies`, which now extends as far right as it can, as in XPath (seventh language change, Yannis, 31 October: `if c then 1 else 2 == 3` is read as `if c then 1 else (2 == 3)` where the typed grammar stopped the branch at `2` because its type could hold no comparison; a test pins the new reading), and the index, which is spelled `[at n]` from P4.3 (ninth language change, Yannis, 1 November: `[...]` is a filter and nothing else, so 59 corpus lines change their EFX text and none its XPath), and names starting with `__`, which are reserved for generated variables (tenth); the generated XPath, byte for byte except the 3 indexed lines named in the grammar section, which the corpus check enforces; `ScriptGenerator` and `XPathScriptGenerator`, whose 122 typed compose methods remain the targets of translation; the model packages (`expressions`, `types`, `variables`, `CallStack`); the structure of the template and rules translators; the `sdk1` package; the public `EfxTranslator` API.

What shrinks:

|  | Today | After |
|---|---|---|
| Grammar alternatives | 783 | about 300 |
| Alternatives per new built-in | up to 7 | 1 |
| `EfxExpressionTranslatorV2` exit methods | 464 | about 120 |
| Cardinality side stack and dispatch sites | 1 stack, 2 if-chain dispatchers, about 40 `resolveScalarType` calls | none |
| `EfxErrorStrategy` rule-name entries | 95 | about 45 |
| Translating the 52 SDK templates | 301 s (warm JVM) | to be measured; the parser should stop being the bottleneck |

Why it is worth doing: parsing speed, which is the reason Yannis raised; grammar maintainability, since a new built-in today costs up to seven grammar alternatives and seven listener methods; and error messages that can finally say what is wrong instead of reporting a syntax error.

## The untyped grammar

One `expression` rule carries every operator with standard precedence, and types and cardinality leave the grammar entirely. Rule names change only where typing forced a rule to exist, so the template and rules sections, the references, the literals and the lexer keep their shape. `EfxLexer.g4` does not change at all: the type keywords `text`, `number`, `date` and the rest keep doubling as declaration types and conversion functions.

### The expression rule

```
expression
    : OpenParenthesis expression CloseParenthesis                      # parenthesizedExpression
    | expression OpenBracket expression CloseBracket                   # filteredExpression
    | expression OpenBracket At expression CloseBracket                # indexedExpression
    | expression Slash AtPrefix attributeName=Identifier               # attributeExpression
    | expression ColonColon linkedFieldReference                       # fieldFromContextExpression
    | expression ColonColon simpleNodeReference                        # nodeFromContextExpression
    | expression Colon RawValue                                        # rawValueExpression
    | expression Colon PreferredLanguage                               # preferredLanguageExpression
    | expression Colon PreferredLanguageText                           # preferredLanguageTextExpression
    | expression operator=(Star | Slash | Percent) expression          # multiplicativeExpression
    | expression operator=(Plus | Minus) expression                    # additiveExpression
    | expression operator=Comparison expression                        # comparisonExpression
    | expression modifier=Not? In expression                           # inListCondition
    | expression Is modifier=Not? Unique In expression                 # uniqueValueCondition
    | expression Is modifier=Not? Empty                                # emptinessCondition
    | expression Has modifier=No? Duplicates                           # distinctCondition
    | expression modifier=Not? Like pattern=StringLiteral              # likePatternCondition
    | expression Is modifier=Not? Present                              # presenceCondition
    | expression And expression                                        # logicalAndCondition
    | expression Or expression                                         # logicalOrCondition
    | If expression Then expression Else expression                    # conditionalExpression
    | (Every | Some) iteratorList Satisfies expression                 # quantifiedExpression
    | For iteratorList Return Distinct? expression                     # sequenceFromIteration
    | OpenBracket expression (Comma expression)* CloseBracket          # list
    | literal                                                          # literalExpression
    | reference                                                        # referenceExpression
    | fieldProperty                                                    # fieldPropertyExpression
    | codelistReference                                                # codelistExpression
    | builtInFunction                                                  # functionExpression
    ;
```

ANTLR gives earlier alternatives higher precedence, so the order above is the precedence table Yannis approved: the bracket (filter and index), then `* / %`, then `+ -`, then the comparison family (`==` and friends, `in`, `like`, `is empty`, `has duplicates`, `is unique in`, `is present`), then `and`, then `or`. The three prefix forms come last so that their trailing expression extends as far right as it can, exactly as `if ... then ... else` does today. `not(...)` stays a function.

The late-bound rules go: every reference is simply a `reference`, and the toolkit learns its type when it resolves it. The six typed expression rules, the six typed sequence rules and the `lateBoundExpression`, `lateBoundScalar` and `lateBoundSequence` rules (290 alternatives together) collapse into the rule above and the `list` alternative. The cast rules and their twelve `*TypeCast` helpers are deleted.

The steps that may follow a reference (`/@attribute`, `::` to a field or a node, `:rawValue`, `:preferredLanguage`, `:preferredLanguageText`) are postfix alternatives of `expression`, placed right after the bracket, so that the parser never reads past a `]` to tell a context's predicate from an index (P3.1 design, 31 October; measured by the implementing thread: a lookahead of 5 tokens at most against an unbounded one, and the corpus parses in about 2.3 s against 255 s with the typed grammar). The toolkit rejects a step in the wrong place, such as `'a':rawValue`, as a type error. Consequences: a node read through an override, `ND-1::ND-2` or `$n::ND-Root`, is a node reference like any other, countable and testable with `is present` (a fifth language change); the selector takes a field, a node or a field's attribute, each with its predicates and no override, so `&{ND-1::BT-2/@a}`, which no corpus file or test uses, leaves the language (a sixth); a context (`fieldContext`, `nodeContext`) keeps its predicates, any number of them. Dictionary lookups have no rule of their own: `$d['key']` is the bracket form, and the toolkit tells a lookup from a filter by the symbol.

### References, brackets and contexts

A field, attribute or node reference is a reference and nothing more: the `(OpenBracket predicate CloseBracket)?` tail moves out of the reference rules into two postfix forms of `expression`, the filter `x[condition]` and the index `x[at n]`, so the parser never has to choose between "predicate of the reference" and "index of the expression" at a `[`: it decides on the token after it, `At` being the keyword the lexer already has for `ENDPOINT .. AT`, which no name can take. Yannis decided the two meanings on 31 October and gave them separate spellings on 1 November, in the design thread, after the P4.3 design showed that telling them apart by the type of the content costs a second translation of the content and a limit (a context-reading index would also have had to type-check from the element): the common form stays unmarked (7,603 filters in the corpus against 59 indexes), the form that turns a sequence into one value carries the word, and `[1]` no longer looks like XPath's per-parent `[1]` while meaning something else; the JSONPath spelling `[?filter]` was rejected because `?` is already the template-function prefix inside filters (`BT-500-Organization-Company[?exists-common-value(...)]`, 156 occurrences in the corpus). A filter is evaluated for each element of the sequence with that element as its context, as `enterPredicate` does today, and keeps the elements for which it holds, `path[filter]`; its content must be boolean, and a number there, today's `[1]`, is a type error whose message names the `at` form. An index is evaluated once, where the expression stands, like any other expression, and selects the nth element of the sequence the reference denotes from its context, the same in every position, so an index yields at most one value (EFX has no sequence of sequences); its content must be a number and may be anything numeric, a field, a function or an API call included (Yannis, 31 October, "reading 1": the number read once where the expression stands, not the XPath notion of a position compared inside each element, which can select several). In XPath that is a position on the whole sequence, `(path)[n]`, not the bare step predicate `path[n]`, which picks the nth element under each parent and gives one value per repeating ancestor; the value step follows: `BT-00-Repeatable-Text[at 2]` becomes `(PathNode/RepeatableTextField)[2]/normalize-space(text())`, and a filter alone stays `path[filter]/value`, as today. Content that XPath would read the same inside each element as where the expression stands (a literal, a variable, arithmetic on those, an absolute path after a field or a node, a context variable's field `$c::F`, a dictionary lookup) stands in the position as it is; content that reads the context, or that reads the context or the root after a value (an atomic item has no root to start from), is bound before the position with the `for` the toolkit writes for iterators, `(for $__index in <number> return (path)[$__index])`, because XPath would otherwise read it inside each element (P4.3; the name is Yannis's, 1 November). The reads the visitor has counted since phase 3 tell the two apart, so every index that translates today keeps its XPath and the binding appears exactly where today's translation is rejected. Today's toolkit writes `(path/value)[n]` in a value position but the per-parent `path[n]` before a step (`:rawValue`, `::`, `:preferredLanguage`) and in a context; the rule ends that split (found by the implementing thread in P3.2, 31 October: `BT-191-Tender` sits under the repeating `efac:Origin`, so `path[filter][1]/value` would have given one value per origin where today's output gives one). A bracket after anything that is not a reference, such as a list, a variable or a parenthesised expression, takes the same two forms: `('a','b','c')[at 1]` is an index on that sequence, and a filter on a value sequence stays a type error, as today. Dictionary lookups keep the bare bracket, `$d['key']`, told apart by the symbol. A context (`fieldContext`, `nodeContext`) takes both forms after its reference. Phase 3 had one form, told apart by the type of its content, translated the content in the element's context first and rejected an index that had read it (`INDEX_READS_CONTEXT`); P4.3 adds the `At` alternative to the SDK grammar and removes that error, its factory and its tests.

Two consequences, confirmed by Yannis on 31 October and 1 November. For authors: the index is written `[at n]`; literal and variable indexes were the only ones that ever worked, and a number in `[...]` is a type error from P4.3 that names the `at` form; an index that reads a field, rejected at translation time in phase 3, translates from P4.3, where today it fails at run time. The corpus has 7,603 boolean predicates on references and 59 numeric indexes, all `[1]`: 3 on a field, `BT-191-Tender[OPT-321-Tender == $tenderid][1]` in templates 28 and 30, and 56 on a variable, `$tpo_names[1]` in 19 templates; all 59 change their EFX text to `[at 1]` in the SDK commit of P4.3. For the output: the 3 lines on a field change from `(path[filter]/value)[1]` to `(path[filter])[1]/value`, the same values at run time, the 56 on a variable keep their XPath, and the 34 tests that index a field change their expected XPath text the same way: 7 in value positions, and 27 before a step or in a context, where today's per-parent `path[n]` becomes `(path)[n]`; their values are the same wherever no node between the context and the field repeats, which is every case the tests and the corpus have. Every test whose EFX holds a numeric index respells it `[at n]` in P4.3; the expected XPath of every index that translates today is unchanged (the implementing thread checked on 1 November: 60 tests hold a numeric index, 51 of which change nothing else, one error column moves by the three characters of `at `, and the 8 rejections become translations; one filter message names 'indicator' alone; see the table). These are the only permitted output changes of the whole work.

### Iterators, declarations and parameters

Declared types stay in the language; they are declarations, not expressions, and the toolkit checks the initializer against them. Names that start with `__` are reserved for the variables the toolkit generates (`$__index`, `__apiResultN`): a declaration of one is rejected (tenth language change, Yannis, 1 November; `_` is a letter in the identifier rule, so such a name was legal and could clash with a generated one; no test or corpus file declares one). The thirteen per-type rules for each declaration kind become one rule with a type token:

```
typeDeclaration: primitiveType Star?;
primitiveType: Text | Indicator | Number | Date | Time | Duration;

variableInitializer
    : typeDeclaration Colon VariablePrefix variableName=Identifier Assignment expression   # typedVariableInitializer
    | Dynamic Colon VariablePrefix variableName=Identifier Assignment dynamicFunctionInvocation # dynamicVariableInitializer
    ;

parameterDeclaration: typeDeclaration Colon VariablePrefix parameterName=Identifier;

iteratorExpression
    : primitiveType Colon VariablePrefix variableName=Identifier In expression     # valueIterator
    | ContextType Colon VariablePrefix variableName=Identifier In (fieldContext | nodeContext) # contextIterator
    ;

functionDeclaration
    : Let typeDeclaration Colon FunctionPrefix functionName=Identifier
      OpenParenthesis parameterList? CloseParenthesis Assignment expression Semicolon
    ;
```

`EfxTypeTokenLookup` already maps these tokens to `EfxDataType`, so the toolkit reads the declared type from `primitiveType` and converts the initializer to it, promoting a scalar to a sequence where the declaration asks for one and rejecting anything else. An iterator variable holds one item, so it takes a `primitiveType` without `*`, as today. The `contextVariableInitializer`, the `dictionaryDeclaration` and the template definitions are unchanged.

### Built-in functions

One alternative per built-in, grouped as today by what they return, with `expression` arguments. The function name stays a keyword, so a misspelt name or a wrong arity is still a syntax error with a precise position, and prediction stays cheap because every alternative starts with its own token. Three examples show the collapse:

```
numericFunction
    : CountFunction OpenParenthesis expression CloseParenthesis                      # countFunction
    | Number OpenParenthesis expression CloseParenthesis                             # numberFunction
    | IndexOfFunction OpenParenthesis expression Comma expression CloseParenthesis  # indexOfFunction
    ...
    ;
```

`count` had 7 alternatives, `number` 3, `index-of` 18, `sequence-equal` 18, `text` 6, `format-short` 4. The six typed sequence-function groups (85 alternatives) become one `sequenceFunction` group of 11. The 67 function tokens of the lexer stay as they are.

### Everything that stays

`singleExpression`, `expressionBlock`, the selectors, the template file, template lines, labels, the rules file, stages, rule sets and clauses keep their rules. Positions that read `(booleanExpression | lateBoundScalar)` today, such as `whenClause`, `assertClause`, `linkBlock`, `dictionaryKeyClause` and `whenDisplayTemplate`, read `expression`, and the toolkit pops a `BooleanExpression` there as it already does. `parameterValue` keeps its typed literal rules, because parameter values are literals and the type is their only content.

### Size and shape

The grammar lands at roughly 300 alternatives: about 22 in `expression`, about 85 built-in functions, about 15 reference rules, 10 declaration rules, and the template and rules sections unchanged at about 150. Every primary alternative starts with a distinct token (a literal, `[`, `(`, `if`, `for`, `every`, `some`, a function keyword, a field or node identifier, `$`, `?`, `/`, `#`), so the parser should stay in SLL mode throughout. The ANTLR profiler report that `EfxTemplateTranslatorV2` can already produce is the gate for that claim in phase 3.

## Type inference in the toolkit

Types are inferred bottom-up, at the exit of every parse-tree node, and every operator and built-in is resolved against a catalogue of signatures. The toolkit already has most of the ingredients: every `TypedExpression` carries its `EfxDataType`, `EfxTypeLattice` relates primitives to their scalar and sequence forms, and `CallStack.pop(Class)` already converts on pop through `TypeCheckerV2`. Two things are new: a signature catalogue with a resolver, and one leaf rule for references that replaces the cardinality side stack.

Every operator and built-in takes the same path, and the catalogue is the only place that knows which operand types are legal.

### Leaves

- **Literals** keep their literal classes (`StringLiteral`, `NumericLiteral` and the rest). Nothing changes.
- **Field and attribute references** become a path typed by the field's SDK type (`ScalarPath.fromFieldType`, `SequencePath.fromFieldType`) and by one cardinality rule: a sequence when the field is repeatable from the current context or multilingual, unless the field is itself the context; a scalar otherwise. This is today's `RESOLVE_EITHER` branch of `resolveAndPushFieldReference`, applied everywhere. The `RESOLVE_SCALAR` and `RESOLVE_SEQUENCE` branches are not needed: a sequence path in a scalar position is rejected by the operator, and a scalar path in a sequence position is promoted by it.
- **Node references** become a `NodePath`, scalar or sequence by repeatability. Only path consumers accept them: `is present`, a bracket filter, a context position, and `count`, which counts the nodes the reference selects (added to the language by Yannis on 31 October; see the catalogue below).
- **Variables and parameters** take the type of their declaration, as `CallStack.pushIdentifierReference` already pushes it. A context variable bound by `for context:$c in ...` is a scalar path of its field; a node context variable is a node reference: `count` counts the node it holds, as `count(ND-SubNode)` does (Yannis, 1 November, P4.2), and where a value is required it stays an error (`NODE_CONTEXT_AS_VALUE`).
- **Function invocations** `?f(args)` take `Function.dataType` as declared. `StrictArguments` collects the arguments; from P2.3 the catalogue's parameter matching checks them against the function's declared parameters, as it checks a built-in's overloads, so there is one type check, not two. A template invocation `invoke t(args)` is checked the same way against the template's parameters: a template has parameters and no result, so it is a parameter list, not a built-in.
- **Dictionary lookups** are a sequence of the indexed field's type, as today.

### Paths and values

A reference is pushed as a path, and the two positions that consume a path as such take it: the left side of a bracket and `is present` (context positions use `fieldContext` and `nodeContext` and are not expressions). Everywhere else the reference is dereferenced into its value, which is what `composeFieldValueReference(fieldId, path)` does today in `exitScalarFromFieldReference`. In the listener this is decided in `exitReferenceExpression` from the parent node, the way `adjustForGrammarAmbiguity` reads its parent today; in a visitor the consumer asks for a path or a value explicitly. The next section weighs the two. A node reference has no value form: it stays a path wherever it stands, and a consumer that is not a bracket, `is present` or `count` rejects it ("a node has no value"). `preferred-language` and `preferred-language-text` take an `expression` from P4.2 (Yannis, 1 November), as every other built-in does, so that `preferred-language('a')` is a type error and not a syntax error; the visitor reads the argument as a field, as the postfix `:preferredLanguage` does, and the catalogue's multilingual-field parameter rejects anything else. The context iterator keeps `fieldContext | nodeContext`: an `expression` there would admit forms such as `for context:$c in $d` and overrides, which is a language decision, not a message fix.

### Composite nodes

- **List** `[a, b, c]`: all elements must share a primitive; the result is the sequence of that primitive. Mixed primitives are a type error naming the first offending element.
- **Iteration** `for ... return body`: a scalar body gives a sequence of its primitive, a sequence body gives the same sequence type (concatenation), matching the two `composeForExpression` overloads.
- **Conditional** `if c then a else b`: `c` must be boolean; `a` and `b` must share a primitive; the result is their join under promotion (both scalar gives a scalar, otherwise a sequence), the two branches treated alike (confirmed by Yannis on 31 October, on Codex's gate 3 review; phase 3 kept the typed grammar's single values, P4.4 implements the join).
- **Bracket** `x[c]` and `x[at i]`: `x` a path and `c` boolean gives the filtered path (same type), `c` read in `x`'s context; `x` a path and `i` numeric gives the scalar path of `x`'s primitive, the position taken once on the whole sequence the path selects, `(path)[i]`, before the value step, `i` read where the expression stands and bound before the position when XPath would read it differently inside each element: when it reads the context after a field or a node, or the context or the root after a value (P4.3); `x` a value sequence and `i` numeric gives the scalar of its primitive; anything else is a type error, a number in `[...]` and a boolean in `[at ...]` included.
- **Field properties** (`:isWithheld` and friends) are boolean, `:privacyCode`, `:preferredLanguage` and `:rawValue` are strings, as today.
- **Operators and built-ins** go through the catalogue below.

### The signature catalogue

A new package `eu.europa.ted.efx.model.signatures` holds the operator and function signatures that the typed grammar used to spell out as alternatives, and `ScriptGenerator` as typed overloads. It has four concepts:

- `ParameterType`: a pattern an argument must match. Either a concrete type (`NumberScalar`), a primitive in any cardinality (`Number`), a type variable in a cardinality (`T`, `T*`, with `T` ranging over the value primitives, never `Node`), or `Path`. Nodes reach one built-in through a concrete type, not through `T`: `count` has a second overload, `count(NodeSequence)` beside `count(T*)`, composed with the same `composeCountOperation`, so `count(ND-RepeatableNode)` gives `count(RepeatableNode)` and `count(ND-RepeatableNode[...])` counts the nodes the predicate keeps; a non-repeatable node counts 0 or 1 by promotion (Yannis, 31 October).
- `Signature`: an ordered list of parameters, with an optional or variadic tail, and a result type written in the same vocabulary (`Boolean`, `T`, `T*`).
- `BuiltIn`: a name, which is the grammar token or the operator symbol, and its overloads. Each overload pairs a `Signature` with a `Composer`, a function of the `ScriptGenerator` and the bound arguments that calls the typed compose method, for example `(script, args) -> script.composeToStringConversion(args.numeric(0))`. Operators (`+`, `==`, `and`, `in`, `is empty`, ...) are built-ins with symbolic names.
- `Resolution`: given a built-in and the actual operand types, picks the overload. Exactly one overload must accept the arguments, each unchanged, widened (`MultilingualString` to `String`, `Dynamic` to `Boolean`) or promoted (a single value to a sequence of one); none is a type error; more than one, whether or not one of them matches exactly, is a catalogue defect: overloads are disjoint, there is no ranking by specificity, ambiguity raises `TranslatorConfigurationException` and a unit test checks every built-in. Binding passes an argument unchanged when it already has the parameter's class and otherwise converts it with `TypedExpression.from`, as `pop(Class)` does today (a `StringLiteral` keeps its literal marker, which `composeNumberFormatting` reads), calls the composer and pushes the result.

The catalogue is data in one place: about 15 operators and 70 functions with about 190 signatures, against the 595 grammar alternatives that encode them today (the left and right late-bound variants collapse). User-defined functions and templates already have a parameter model (`Parametrised`, `ParsedParameters`); the matching part of a `Signature` is its parameter list (`Parameters`, the signature without its result), which is built from a declared parameter list too, so `?f(args)` and `invoke t(args)` are checked by the same code that matches a built-in's overloads, and a template needs no result type and no composer. In the last phase the autocomplete catalogue `EfxBuiltInFunction` (113 string entries) is generated from the signatures, which is what unifying the type vocabularies means.

### Errors

A failed resolution carries the operator, one rejected position, the expected type with `T` bound, the kind of failure (primitive, cardinality, or a path where a value is required or the reverse) and the argument expressions; when several overloads take the arguments, an overload whose primitives all fit is reported before any that fails on a primitive, and among those the one rejected at the furthest position, so that the diagnostic does not depend on the order of the overloads (a unit test reorders them); every primitive of an overload is checked before any cardinality, so that an operand wrong in both reports the primitive whichever side of a symmetric operator the sequence is on, `BT-00-Repeatable-Text == 1` and `1 == BT-00-Repeatable-Text` both giving `INCOMPATIBLE_OPERANDS`, while a sequence of dates minus a date still reports that the field may repeat, because the `date - date` overload fits but for its cardinality (Yannis, 1 November, on the phase 2 observation deferred to P4.2; the rule of 31 October reported the furthest position first, which made the pair depend on its side). The translator turns it into today's exceptions and keeps today's error codes, choosing by the operand's origin, which it reads from the parse-tree child at the failed position, looking through parentheses (field id, variable or dictionary name), not from the type; an operand that is not a reference, such as a list or an arithmetic result, has no origin and gets the generic code: a repeatable field in a scalar slot gives `FIELD_MAY_REPEAT` with the context symbol, a multilingual field gives `FIELD_IS_MULTILINGUAL`, a sequence variable or function gives `IDENTIFIER_IS_SEQUENCE`, a dictionary gives `DICTIONARY_IS_SEQUENCE`, a node context variable gives `NODE_CONTEXT_AS_VALUE`, a mismatched primitive gives `INCOMPATIBLE_OPERANDS` for operators and `CANNOT_CONVERT` for single arguments, whichever grammar alternative matched (two tests that expect `CANNOT_CONVERT` under `==` and `+` change; Yannis, 31 October). When both the primitive and the cardinality are wrong, the primitive mismatch is reported; the origin codes apply only when the primitive fits and the cardinality fails. The message names the operator and both operand types instead of today's "Expected StringScalar instead of NumberScalar".

Yannis settled the wording on 30 October: one uniform format for every type error, whichever part of the toolkit rejects the expression, so that the same kind of mistake no longer surfaces as a type mismatch in one place and as a syntax error in another, as it does today. Nothing downstream parses the message text, by design. Construct names follow the grammar: `[...]` is "Predicate" with the role "condition" (Yannis, 1 November: his team's word and the grammar's rule name; the P4.2 name "Bracket" belonged to the bracket with two meanings), `[at ...]` is "Index" with "sequence" and "position". A suggestion may follow the reason as the last sentence, carried by the `Diagnosis` as such: a number in a predicate ends with "To select by position, write [at n]." (P4.3).

Uniform promotion flips three existing tests, all in `LateBoundTypeResolutionTest`, which today expect `IDENTIFIER_IS_SCALAR`: `count($x)` with a scalar variable, `count(?f())` with a scalar function, and `'test' in $f` with a context variable. Under the rule Yannis chose these become valid (a singleton sequence), the tests invert in phase 1, and the `IDENTIFIER_IS_SCALAR` code and its factory are removed. No implicit conversion runs the other way: a sequence where a scalar is required stays an error, so `BT-00-Repeatable-Text == 'test'` is still rejected.

### What this replaces

The cardinality side stack and its 6 consumers, the two if-chain dispatchers, the roughly 40 `resolveScalarType` and `resolveSequenceType` call sites, and the 328 per-type and late-bound exit methods. What replaces them is the new `EfxExpressionVisitor` with one visit method per grammar label, most of them one line: visit the operands, resolve the built-in, return the result. `CallStack.pop(Class)` with its conversion stays for the template and rules translators, which keep popping a `BooleanExpression` for a condition or a `TypedExpression` for a display block, and which call the visitor for every expression they contain.

## Listener or visitor for expressions

The choice does not change what the toolkit can check. The only type information that flowed downward in the old design was the expected cardinality, and it disappears with the leaf rule above. The choice changes how plainly the code says what it does, and when the switch costs least. Yannis chose option B on 31 October: a visitor for the expression subtree, introduced in phase 3 when every expression method is rewritten anyway, composed into the template and rules translators, which stay listeners. The table records the comparison that led to it.

|  | A: listener everywhere | B: visitor for expressions, composed |
|---|---|---|
| Shape | `EfxExpressionTranslatorV2 extends EfxBaseListener`; template and rules translators extend it, as today | `EfxExpressionVisitor extends EfxBaseVisitor<Operand>`, an `Operand` being the translated expression with its origin (the reference it is written as, if any), so that the bracket, `::` and the error codes do not read the parse tree again (P3.2 design, 31 October); template and rules translators stay listeners and hold a visitor |
| Data flow | Operands are popped from `CallStack`, results pushed; push and pop pairs must match across methods | Each `visitX` returns the typed expression of its node; no stack for expressions |
| Paths versus values | `exitReferenceExpression` reads its parent node to decide | The consumer asks for a path or a value: the two bracket visits ask for their left child as a path |
| Context switch inside brackets | An `enterIndexer` hook that reads its left sibling in the parse tree | The filter pushes the context between visiting its two children; the index visits its content in the enclosing context (P4.3) |
| What else moves | Nothing | Symbol resolver, script generator, `CallStack` and `ContextStack` move from inherited fields into a translation-context object shared by the three translators; about 50 consumption sites in the template and rules translators call `visit(ctx.expression())` instead of popping; a `ParseTreeWalker` subclass stops at `expression` nodes (about 15 lines); `<visitor>true</visitor>` in the ANTLR plugin configuration |
| Performance | Unchanged | Unchanged: the same tree is walked once, expression subtrees by the visitor instead of the walker |
| Cost in phase 3 | Lowest: about 120 exit methods | About 120 visit methods plus the composition refactor, roughly two to three days of work on top |
| What it buys | Nothing new | Explicit data flow, an expression translator that stands alone and is testable without templates, and the first stage of the full visitor migration Yannis is open to |

Phase 2 stays a listener, because the grammar is untouched there and the work is to rewire existing exit methods to the resolver. The switch happens in phase 3, where the grammar labels change and every expression method is rewritten regardless; making it later would mean writing those methods twice. Option C, a visitor everywhere, is out of scope for this work: it rewrites the 2,800 lines of template and rules translators in one go, which Yannis ruled out.

## Everything else that changes

Eleven files in the toolkit depend on the generated parser; the test tree does not, because every test is written in EFX. The table lists each coupled part and what the change means for it.

| Part | Today | Change |
|---|---|---|
| `EfxErrorStrategy` | A map of 95 `RULE_*` constants to display names, used to word syntax errors | Rewritten for the new rule set, about 45 entries. Syntax errors no longer mention types ("expected a numeric expression") because the parser no longer knows them |
| `EfxComputeDependencyExtractor`, `EfxValidationDependencyExtractor` | Hook `simpleFieldReference`, `fieldMention`, `simpleNodeReference`, `codelistReference` and the rules structure | Those rules keep their names; recompile and verify, no edit expected |
| `EfxProfilerReportGenerator`, `EfxDebugLexer`, `EfxTypeTokenLookup` | Read rule names and tokens generically | No change; the profiler report is the phase-3 gate |
| `EfxTemplateTranslatorV2` (116 methods, 44 typed pops) | Thirteen exit methods per declaration kind (variables, parameters, functions), conditions popped as `BooleanExpression` | One exit method per declaration kind reading `typeDeclaration`; conditions popped as before. About 50 sites call the expression visitor instead of popping |
| `EfxRulesTranslatorV2` (60 methods, 7 typed pops) | Same pattern for `whenClause`, `assertClause`, `reportClause`, the `otherwise` clauses and `dynamicFunctionDeclaration` | Same change as the template translator |
| `CallStack`, `ContextStack`, `TypeCheckerV2`, `TypeChecker.V1` | Scopes, contexts, pop-time conversion | API unchanged. The three translators share one instance of each through a translation-context object |
| `TypeMismatchException` | Nine factories and eight error codes | `identifierIsScalar` and `IDENTIFIER_IS_SCALAR` go; messages reworded to name the operator and both operand types |
| `autocomplete` package | `EfxBuiltInFunction` (113 entries), its own `EfxDataType` and `EfxFunctionParameter`, no parser coupling | Derived from the signature catalogue in phase 5; `EfxKeyword` and `EfxLinkedProperty` unchanged |
| Tests (1,550) | Written in EFX against expected XPath | All survive the grammar change. Expectations change for the three promotion tests, the one cast test, the 34 tests that index a field (and in P4.3 every numeric index is respelled `[at n]`, expected XPath unchanged, one error column apart), and in phase 4 the 13 tests that assert a bare `ParseCancellationException` for what becomes a type error |
| `pom.xml` | ANTLR plugin generates listeners for both SDK grammars | Add `<visitor>true</visitor>`. Nothing else |
| `sdk1` package | EFX-1 translators on the SDK 1.16 grammar | Untouched |

### The two repositories are coupled

The toolkit builds against the `eforms-sdk` Maven artifact and unpacks `Efx.g4` from it, so a toolkit commit requires the matching grammar. During the work the implementing thread installs the SDK fork's branch locally as `2.0.0-SNAPSHOT`; the toolkit fork's CI (`build.yml` runs `mvn clean install` with the published snapshot) will fail on the branch until the new grammar is published, which is expected and must be said in the pull request. Upstream, the SDK pull request merges first and publishes its snapshot, then the toolkit pull request can build. `plan.md` records the pair of commits that belong together at every gate.

### Consumers and documentation

`efx-cli`, `eforms-sdk-analyzer` and `eforms-notice-viewer` use `EfxTranslator`, whose API does not change; phase 5 builds them against the branch from read-only clones and runs their tests. The `efx-grammar/README.md` in the SDK and both `CHANGELOG.md` files get entries. Two language changes, the removal of casts and the promotion rule, also belong in the EFX documentation site, which lives outside these repositories; that is a note for Yannis, not a task for the implementing thread.

## Phases and gates

Six phases, each closed by a gate that the implementing thread must pass before it starts the next, and each ending with every test green and the corpus output unchanged. Phases 1, 2, 4 and 5 touch only the toolkit; phase 3 touches both repositories.

Phases 1, 2, 4 and 5 touch only the toolkit; phase 3 changes the grammar in the SDK and the toolkit together, and phase 0 creates the branch in each.

### Phase 0: set up

- **P0.1** Create `feature/untyped-grammar` from `efx-2` in rousso/eForms-SDK and from `develop` in rousso/efx-toolkit-java.
- **P0.2** Build environment: install the SDK fork branch into the local Maven repository (`mvn install -DskipTests`, 2.0.0-SNAPSHOT), since the modified grammar is not published. `eforms-core-java` 1.10.0-SNAPSHOT and `eforms-sdk` 1.16.0-SNAPSHOT resolve from `central.sonatype.com`, which Yannis's cloud environment named rousso allows since 31 October; the implementing thread must run in that environment. If the host is unreachable, install the eforms-core fork and a worktree of the SDK's `release/1.16.0` locally and run the tests with `-Dversion.eforms-sdk-1=1.16.0`.
- **P0.3** Add `.agents/untyped-grammar/plan.md` and `progress.md` to the toolkit branch, and a short pointer to them in the SDK branch.
- **P0.4** Add the opt-in corpus test (JUnit tag `corpus`, excluded by surefire by default): it reads the view templates, the rules files and `fields.json` from the eforms-sdk jar on the classpath, translates them with the mock markup generator and the Schematron generator, and writes the outputs under `target/corpus/`. Capture the baseline from `develop` outside the working tree and record the per-file digests and the timings in `progress.md`.
- **P0.5** Fix the `rules.efx` failure (Yannis decided on 31 October: fix the toolkit, keep the rules). Cause: `XPathSymbolResolver.isFieldRepeatableFromContext(SdkField, SdkField)` types a multilingual field as a sequence even when the field is the context, so the 75 maximum-length rules of the shape `WITH <multilingual field> ASSERT string-length(<field>) <= N` fail, the first at line 6498 (reported as 6522 after include expansion). The translator's `fieldMayReturnMultipleValues` already has the right rule; the fix applies the same rule in the resolver and adds the missing test, a multilingual field used inside its own `WITH`. The baseline is captured after the fix, so it includes `rules.efx`.

**Gate 0.** All tests green including the new multilingual test, baseline captured for the 52 templates and the 4 rules files, timings recorded.

### Phase 1: tests first (toolkit, grammar untouched)

- **P1.1** Translation tests for every language feature the coverage analysis found untested: date minus date, every form of duration arithmetic, `number(...)` conversions, indexers per type, `count`, `count-duplicates`, `get-duplicates`, `is unique in` and `subsequence` per type, `format-short`, `format-medium` and `format-long` for times and date-times, and the rest of the list in the analysis document. Each asserts the generated XPath.
- **P1.2** The invalid-operation table: one parameterised test with one row per rejected expression, written by hand with a comment naming the rule each row checks. Rows cover every operator with each wrong operand primitive, a sequence where a scalar is required, wrong arity, a non-reference before `is present`, a node used as a value, a mixed list, mismatched branches of a conditional, and a mismatched iterator declaration. Each row asserts `ParseCancellationException`, the common base of syntax and compilation errors, so the rows survive the move from parser to toolkit.
- **P1.3** Record in `progress.md` which rows are syntax errors today; they become type errors in phase 3 and are tightened in phase 4.

**Gate 1.** All green. JaCoCo shows every expression exit method that corresponds to a language feature executed by at least one test. Yannis reviews the invalid-operation table: it is the net that catches anything the typed grammar rejected silently.

### Phase 2: type inference with the grammar untouched (toolkit)

- **P2.1** The `model.signatures` package: `ParameterType`, `Signature`, `BuiltIn`, `Resolution`, and the catalogue binding every operator and built-in to its `ScriptGenerator` compose method, with the unit test that proves the catalogue unambiguous.
- **P2.2** The leaf rule for references and the path-versus-value handling of the type-inference section above, implemented in one place, so that `XPathSymbolResolver` and the translator can no longer disagree as they do today for a multilingual field inside its own context.
- **P2.3** Rewire every exit method of `EfxExpressionTranslatorV2` to the resolver; delete the cardinality stack, the two dispatchers and the `resolveScalarType` and `resolveSequenceType` methods; match `?f(args)` and `invoke t(args)` against their declared parameters with the catalogue's parameter matching (`Parameters.match`), with `StrictArguments` only collecting the arguments. Dynamic functions keep their handling in `EfxRulesTranslatorV2` (the auto-generated rule variable and its success check); the matching checks only their arguments. The shared call method enforces `BuiltIn.isTemplateOnly()` through one protected predicate the template translator overrides, replacing the per-function `templateOnlyFunction` exit methods and their overrides; `replace-regex` keeps its literal-pattern validation as the prologue of its own exit method. Integration tests assert code, named identifier and location for direct and parenthesised references, context overrides, sequence functions and dictionary lookups; a form that starts translating in P2.3 gets its translation test then.
- **P2.4** Flip the three promotion tests, remove `IDENTIFIER_IS_SCALAR` and its factory.

**Gate 2.** All green, corpus identical to the baseline, catalogue test green.

### Phase 3: the untyped grammar (SDK) and the rewiring (toolkit)

- **P3.1** Write the new `Efx.g4` described in the grammar section above; `EfxLexer.g4` unchanged; update `efx-grammar/README.md`; install the SDK branch locally.
- **P3.2** Toolkit: one method per new grammar label, as a visitor (option B, chosen by Yannis on 31 October), with the composition refactor: a translation-context object shared by the three translators, a walker that stops at expression nodes, and the template and rules translators calling the visitor at their expression sites; one exit method per declaration kind in the template and rules translators; the `EfxErrorStrategy` table; removal of the cast handling and the cast test; translation tests for what the typed grammar rejects and the decisions of 31 October allow: `count` of a node reference (repeatable, with a predicate, and non-repeatable), `date + duration`, `duration + date`, `date - duration`, `ND-SubNode[1] is present`, and a node through an override (`ND-1::ND-2`, `$n::ND-Root`) counted and tested with `is present`.
- **P3.3** Profiler run over the corpus with `EfxProfilerReportGenerator`: record decisions with full-context fallbacks and ambiguities; the target is none inside expressions.
- **P3.4** Benchmark: corpus timing against the phase-0 numbers.

**Gate 3.** All green; corpus identical except the 3 indexed lines in templates 28 and 30 named in the grammar section; profiler and timing recorded in `progress.md`, the pair of commits (SDK, toolkit) noted.

### Phase 4: type checking proven (toolkit)

- **P4.1** Tighten every row of the invalid-operation table to its specific exception (`TypeMismatchException`, `InvalidArgumentException`, or a syntax error) and assert that type errors name the operator and both operand types.
- **P4.2** One uniform message format for every type error, naming the operator and both operand types, whichever part of the toolkit rejects the expression; no type mistake may surface as a syntax error.
- **P4.3** The index gets its own spelling, `[at n]`, and `[...]` is a filter and nothing else (Yannis, 1 November, in the design thread, replacing the 31 October design of a second visit in the enclosing context, which cost a double translation of the content and a limit). SDK: the `expression OpenBracket At expression CloseBracket` alternative beside the filter, in `fieldContext` and `nodeContext` too; the 59 corpus lines (`[1]` to `[at 1]`); README and CHANGELOG; in one commit, and the corpus output does not change beyond the 3 lines already allowed. Toolkit: a filter's content is translated in the element context and must be boolean, a number there being a type error in the P4.2 format whose message names the `at` form; an index's content is translated once where the expression stands, like any expression, and must be a number, a field, a function or an API call included; `(path)[n]` when the content reads the same inside each element as where the expression stands, otherwise `(for $__index in <number> return (path)[$__index])` then the value step: after a field or a node when the content reads the context, after a value when it reads the context or the root, told by the reads counted since phase 3, so the binding appears exactly where today rejects; `INDEX_READS_CONTEXT`, its factory and its tests go, replaced by translation tests for an index that is a field, an arithmetic expression over a field and a function over fields, on a field, on a node and on a list, their expected XPath checked with Saxon; the 60 tests with a numeric index respell it, 51 with nothing else changing and `LateBoundTypeResolutionTest.testOperandOrigin_Indexer`'s error column moving by the three characters of `at `; `testTypeErrorMessage_Bracket`, a filter with text content, gets "Its condition must be 'indicator'." as its second sentence. Names: the construct is "Predicate" with the role "condition", the index "Index" with "sequence" and "position" (Yannis, 1 November); the 9 invalid-operation rows whose first sentence names "Bracket" change it. A declared name that starts with `__` is rejected, with a test: the prefix is reserved for generated variables (Yannis, 1 November). The hint "To select by position, write [at n]." follows the reason when a predicate's content is a number, carried by the `Diagnosis` as a suggestion. No expected XPath of an index that translates today changes: every index that reads a field fails at run time today.
- **P4.4** The conditional takes the join of its branches, as the typing rule says: `then` and `else` must share a primitive, both named in the error otherwise; a sequence if either branch is one, the other promoted to a sequence of one; a single value if both are. Replaces the `then` branch deciding the class and `else` converting to it. Tests: a single value with a sequence in either order, two sequences, mismatched primitives, expected XPath checked with Saxon. No existing translation changes: every newly valid conditional was rejected before. Decided by Yannis on 31 October ("I agree to promote too").

**Gate 4.** All green.

### Phase 5: unification and verification (toolkit)

- **P5.1** Derive the autocomplete catalogue from the signatures; `EfxAutocomplete` keeps its API.
- **P5.2** Build `efx-cli`, `eforms-sdk-analyzer` and `eforms-notice-viewer` against the branch from read-only clones and run their tests.
- **P5.3** `CHANGELOG.md` entries in both repositories; final numbers in `progress.md`.

**Gate 5.** All green, consumers build and pass. Yannis reviews the branches, merges them into the forks, and opens the upstream pull requests himself, SDK first.

### Size

Rough deltas: phase 1 adds about 1,500 lines of tests; phase 2 adds about 800 lines of model and removes about 1,500 from the expression translator; phase 3 removes about 1,100 lines of grammar and about 2,500 lines of translator while adding about 900; phases 4 and 5 are small. Phases 2 and 3 are the long ones and will span several sessions of the implementing thread; `progress.md` carries the state between sessions.

## Rules of engagement for the implementing thread

These rules are Yannis's standing conditions, not suggestions. They bind every session that works on this plan.

1. **Where to work.** Only on `feature/untyped-grammar` in `rousso/eForms-SDK` and `rousso/efx-toolkit-java`. Never on `efx-2`, `develop`, any OP-TED repository, or `rousso/eforms-core-java`, which is built and read only. Pushing to those two branches is approved by this plan; any other branch, pull request, merge, tag or force-push needs Yannis's word in the thread, every time.
2. **Order.** Phases in order; a phase closes only when its gate passes. Every session starts by reading `plan.md` and `progress.md` and ends by updating `progress.md`.
3. **Commits.** One commit per work item, the message starting with the item id (`P2.3`), the full test suite run before each commit. Pushed history is never rewritten.
4. **Output is sacred.** Any difference in the corpus output, or any test whose expectation would have to change beyond those listed in this plan, stops the work on that item; it is written up in `progress.md` and put to Yannis before anything else is done.
5. **Java 11.** The toolkit compiles with source and target 11: no records, switch expressions, text blocks, pattern matching or sealed types. `var` is fine.
6. **Code quality.** Follow the toolkit's existing patterns and package layout and extend them; do not rewrite what works. Model concepts as classes: a signature is an object, not a switch statement. No utility or helper grab bags, no long methods, no reflection tricks, no suppressed warnings, no commented-out code, no TODO left behind. Javadoc on every new public type and method in the house style. New code is covered by tests. The public API does not change. Run the `simplify` and `code-review` skills before closing a phase and apply what they find.
7. **When to stop and ask.** A gate fails for a reason this plan does not cover; a decision about the language is needed; a change would touch `sdk1`, the public API, or `pom.xml` beyond the listed lines; anything that cannot be undone.
8. **Progress.** `progress.md` is the single record: per work item its status, the gate results with their numbers (test count, corpus comparison, timings, profiler summary), the commit ids of both repositories at each gate, decisions taken, and open questions for Yannis.

### Build recipe

The toolkit must build against the modified grammar, so every session installs the SDK branch locally (`mvn install -DskipTests`, 2.0.0-SNAPSHOT) and then builds and tests the toolkit with `mvn test`. The other dependencies, `eforms-core-java` 1.10.0-SNAPSHOT and `eforms-sdk` 1.16.0-SNAPSHOT, come from `central.sonatype.com`, which Yannis's cloud environment named rousso allows (added 31 October; the implementing thread must run in that environment). Fallback if the host is unreachable: install `rousso/eforms-core-java` at `develop` and a worktree of the SDK's `release/1.16.0` (1.16.0) locally, and run `mvn test -Dversion.eforms-sdk-1=1.16.0`. The corpus test runs with the `corpus` tag enabled and compares `target/corpus/` with the baseline directory named in `progress.md`. The profiler report comes from `EfxTranslatorOptions` with the profiler enabled and an output path.

## Risks and the questions that were answered

Yannis answered all four questions on 30 and 31 October. The last one is recorded below; the other three are recorded in the sections they concern: the grammar section for the numeric index, the errors section for the messages, phase 0 for `rules.efx`. The rest of this section is the risks the plan already contains.

### Questions answered

1. **Listener or visitor** for expressions in phase 3. Option A keeps the listener everywhere, as today. Option B adds a visitor for the expression subtree, composed into the template and rules translators, which stay listeners. The table under the heading of that name compares them. Decided on 31 October: option B, the visitor.

### Risks

- **Silent acceptance.** The typed grammar rejected some expressions that no test and no corpus line exercises. The invalid-operation table of phase 1 is the net, and gate 1 asks Yannis to review it for that reason.
- **Repository coupling.** The toolkit branch does not build in CI until the SDK grammar is published; the pull requests must say so, and upstream the SDK merges first. `progress.md` names the matching commits at every gate.
- **Performance.** The parser is the suspected bottleneck, not the proven one. The phase-0 baseline (301 s for the 52 templates) and the phase-3 benchmark decide; if the gain is small, the profiler report shows where the time goes, and the cleaner grammar stands on its own.
- **Multilingual fields.** `MultilingualString` widens to `String` in the resolver; outputs that depend on the distinction, such as `preferred-language-text`, are covered by the corpus and by the 5 tests on `FIELD_IS_MULTILINGUAL`.
- **Behaviour changes by design.** Casts go, three promotion cases become valid, an index means the nth element of the whole sequence in every position and a filter keeps its meaning, `count` accepts a node reference, a node read through an override (`ND-1::ND-2`) is a node reference, the selector's override form `&{ND-1::BT-2/@a}` goes, the branch after `else`, `return` or `satisfies` extends as far right as it can, and a conditional with one sequence branch is a sequence (both on Codex's gate 3 review, Yannis, 31 October), the index is spelled `[at n]`, and names starting with `__` are reserved for generated variables (both Yannis, 1 November). All ten are language changes and belong in the EFX documentation site, which is outside these repositories and is Yannis's.
- **Several sessions.** Phases 2 and 3 are too long for one session of the implementing thread. The plan and progress files are the only state that survives between sessions, which is why rule 8 above is strict.

## Allowed changes to output and test expectations

These are the only output differences and test-expectation changes this plan permits. Anything else stops the work on that item (rule 4).

| What | Where | Why |
|---|---|---|
| 3 corpus lines change from `(path[filter]/value)[1]` to `(path[filter])[1]/value` | `view-templates/28.efx` line 175; `view-templates/30.efx` lines 213 and 249 (`BT-191-Tender[OPT-321-Tender == $tenderid][1]`) | A bracket after a reference becomes an XPath predicate on the node path (grammar section). Phase 3 |
| 34 tests that index a field change their expected XPath text | 7 in value positions, among them `EfxExpressionTranslatorV2Test`: `testIndexer_WithNonRepeatableField`, `testIndexer_WithNonRepeatableFieldAndPredicate`, `testIndexer_WithRepeatableField`, `testIndexer_WithRepeatableFieldAndPredicate`, from `(path[filter]/value)[n]` to `(path[filter])[n]/value`; 27 before a step or in a context, from the per-parent `path[n]` to `(path)[n]`; the full list goes in progress.md | An index is the nth element of the sequence the reference denotes, everywhere (Yannis, 31 October); today's output takes the nth under each parent in those 27 positions. Same values wherever no node between the context and the field repeats. Phase 3 |
| 59 corpus lines change their EFX text from `[1]` to `[at 1]`, their XPath unchanged beyond the 3 lines above | `view-templates/28.efx` line 175 and `30.efx` lines 213 and 249 (`BT-191-Tender[OPT-321-Tender == $tenderid][1]`); `$tpo_names[1]` in 19 view templates, 56 occurrences | The index is spelled `[at n]` (Yannis, 1 November). Phase 4 (P4.3), SDK commit |
| 60 tests with a numeric index respell it `[at n]`, expected XPath unchanged: 51 change nothing else, 1 error column moves by 3 (`LateBoundTypeResolutionTest.testOperandOrigin_Indexer`, 1:23), and the 8 `INDEX_READS_CONTEXT` tests become translation tests; 1 filter message changes its second sentence to "Its condition must be 'indicator'." (`testTypeErrorMessage_Bracket`, `BT-00-Repeatable-Text[BT-00-Text]`); the 4 invalid-operation rows with a bracket after a value keep their first sentence | `EfxExpressionTranslatorV2Test`, `EfxTemplateTranslatorV2Test`, `LateBoundTypeResolutionTest`; the list goes in progress.md | Same change; `[...]` takes an indicator alone, and `at ` is three characters. Phase 4 (P4.3) |
| 9 invalid-operation rows whose first sentence names "Bracket" change it to "Predicate" (11 occurrences in all tests) | `EfxInvalidOperationV2Test` and neighbours | The construct is named Predicate, with the role condition (Yannis, 1 November). Phase 4 (P4.3) |
| 3 promotion tests invert: the expressions become valid | `LateBoundTypeResolutionTest`: `count($x)` with a scalar variable, `count(?f())` with a scalar function, `'test' in $f` with a context variable; today they expect `IDENTIFIER_IS_SCALAR` | Uniform scalar-to-sequence promotion. Phase 1 (inverted) and phase 2 (code removed) |
| 1 cast test is removed, and 2 tests lose a cast from their EFX and keep their expected XPath | `EfxTemplateTranslatorV2Test.testExpressionBlock_RepeatableFieldWithExplicitCast` (`(text*)BT-00-Repeatable-Text`) is removed; `testNegativeDuration_ViaMultiplicationWithField` (`2 * (duration)BT-00-Duration * -3`) and `testDurationMultiplication_LateBoundDurationTimesCastNumber` (`BT-00-Duration * (number)BT-00-Number`, renamed `..._LateBoundDurationTimesLateBoundNumber`) lose the cast | Casts leave the language; both parse without the cast to the tree the cast gave (P3.1 check, 31 October), and P3.2's run proves the XPath. Phase 3 |
| 13 tests that assert a bare `ParseCancellationException` keep passing in phase 3 and are tightened to the specific exception | `EfxExpressionTranslatorV2Test` and neighbours | Rejections move from the parser to the toolkit. Phase 4 |
| 2 rows leave the invalid-operation table: `count(ND-SubNode)` and `count(ND-RepeatableNode)` become valid | `EfxInvalidOperationV2Test`, rule "A node has no value" | Node counting added to the language (Yannis, 31 October). Table in phase 2, positive tests in phase 3 |
| 1 test inverts: `count($n)` of a node context variable becomes valid | `EfxExpressionTranslatorV2Test.testSequenceFromNodeContextVariable_ThrowsNodeCannotBeValue`, renamed `testCountOfNodeContextVariable_CountsItsNode` (`for context:$n in ND-SubNode return count($n)`) | A node context variable is a node reference, which `count` takes as it takes `count(ND-SubNode)` (Yannis, 1 November, on the implementing thread's decision card). Phase 4 (P4.2) |
| 1 test changes its expected exception: `BT-00-Text:publicationDate is present` on a field that cannot be withheld gives `InvalidUsageException` `FIELD_NOT_WITHHOLDABLE` | `EfxExpressionTranslatorV2Test.testLinkedFieldProperty_onNonWithholdableField`; today a `SymbolResolutionException`, "Unknown symbol 'null'", because the linked field is looked up before the field is checked | A defect fix: the three linked field properties check that the field can be withheld, as the privacy properties do (Yannis, 1 November, in the P4.1/P4.2 design doc). Phase 4 (P4.1) |
| 1 test corrects its EFX: the time literal `00:01:00` of `testFieldValueComparison_TypeMismatch` gets its zone, `00:01:00Z`, and the test expects `INCOMPATIBLE_OPERANDS` | `EfxExpressionTranslatorV2Test`; today the literal is a syntax error, so the test did not check the type mismatch its name says | A test correction (Yannis, 1 November, in the P4.1/P4.2 design doc). Phase 4 (P4.1) |
| 2 tests change their expected code from `CANNOT_CONVERT` to `INCOMPATIBLE_OPERANDS` | `LateBoundTypeResolutionTest.testLateBoundComparison_MismatchedFieldTypes_Throws` (`BT-00-Text == BT-00-Number`) and `EfxRulesTranslatorV2Test.testApiCall_TypeMismatch_InArithmeticExpression` (`1 + ?check(BT-00-Identifier)` in a rule) | A mismatched primitive under an operator gives `INCOMPATIBLE_OPERANDS` (Errors section); today the code depends on which grammar alternative matched. Yannis chose this over keeping today's codes until phase 3 (31 October). Phase 2 (P2.3) |
| `rules.efx` translates after the resolver fix | `validation/rules.efx`, 75 maximum-length rules on multilingual fields | Toolkit defect fixed in P0.5; the baseline is captured after the fix |

## Facts established during the design, for the implementing sessions

**Repositories and versions.** Toolkit `develop` was at `89e0821`, SDK `efx-2` at `abd9d85` when the branches were created. Toolkit `pom.xml`: version `2.0.0-SNAPSHOT`, `version.eforms-sdk-1` = `1.16.0-SNAPSHOT`, `version.eforms-sdk-2` = `2.0.0-SNAPSHOT`, snapshot repository `https://central.sonatype.com/repository/maven-snapshots`, Java source and target 11. The toolkit unpacks `Efx.g4` and `EfxLexer.g4` from the eforms-sdk jars (`unpack-sdk-grammars` in the pom). CI (`.github/workflows/build.yml`) runs `mvn clean install` on every push and will be red on the toolkit branch until the SDK grammar is published; say so in the pull request.

**Corpus.** The SDK branch holds 52 view templates (`view-templates/*.efx`) and 4 rules files (`validation/stage-1a.efx`, `stage-1b.efx`, `stage-2a.efx`, `rules.efx`; `rules.efx` is 17,177 of the corpus's 32,209 lines and starts with `#include "stage-1a.efx"`, which shifts the parser's line numbers by 24). The eforms-sdk 2.0.0-SNAPSHOT jar bundles `eforms-sdk/view-templates/*.efx`, `eforms-sdk/validation/*.efx`, `eforms-sdk/fields/fields.json`, the codelists and the notice types. Measured on 30 October 2026 with the typed grammar: the 52 templates take 301 s with a warm JVM and 360 s cold; the three stage files take under a second; the output is deterministic across runs and about 6.9 MB. The corpus uses 7,603 boolean predicates on references and 59 numeric indexes, all the literal `[1]`: 3 on fields (listed above) and 56 on `$tpo_names`; the 82 `)[` once counted as indexes are regular-expression text inside `like` literals (recounted by the design thread, 1 November).

**Translating the corpus from Java.** `new eu.europa.ted.eforms.sdk.SdkSymbolResolver("eforms-sdk-2.0", root)` expects `root/2.0/fields/fields.json`, so point `root` at a directory whose `2.0` entry is the SDK checkout (a symlink works). A `TranslatorDependencyFactory` for the corpus returns that resolver, `ComponentFactory.getScriptGenerator(sdkVersion, qualifier, options)`, the test tree's `MarkupGeneratorMock`, `ComponentFactory.getValidatorGenerator(sdkVersion, qualifier, options)` and `ThrowingErrorListener.INSTANCE`. Then `EfxTranslator.translateTemplate(factory, "eforms-sdk-2.0", path, new EfxTranslatorOptions("udf", DecimalFormat.EFX_DEFAULT))` gives the template output and `EfxTranslator.translateRules(factory, "eforms-sdk-2.0", path, options)` gives a `Map<String, String>` of outputs per rules file. Use `-Xmx3g` or more.

**Single expressions in tests.** `EfxTestsBase.translateExpressionWithContext(context, expression)` wraps the expression as `{context} ${expression}` (EFX-1 style); the EFX-2 form is `WITH context COMPUTE expression`. The mock SDK (`src/test/resources/json/sdk2-fields.json`, loaded by `DependencyFactoryMock.INSTANCE` from the working directory `efx-toolkit-java`) defines `ND-Root`, `BT-00-Text`, `BT-00-Number`, `BT-00-Text-Multilingual`, `BT-00-Repeatable-Text`, `BT-00-Text-In-Repeatable-Node` and the rest of the `BT-00-*` family.

**The rules.efx defect (P0.5).** `XPathSymbolResolver.isFieldRepeatableFromContext(SdkField, SdkField context)` returns true for every multilingual field before it looks at the context, so `getRelativePathOfField` hands the translator a `MultilingualStringSequencePath` for the path `.`; `EfxExpressionTranslatorV2.fieldMayReturnMultipleValues` has the correct rule (a field is scalar inside its own context) but receives a sequence path already. Reproduce with `WITH BT-01(d)-Procedure COMPUTE string-length(BT-01(d)-Procedure) <= 400` against the real SDK, or with `BT-00-Text-Multilingual` as its own context in the mock. Outside its own context the field is rightly rejected with `FIELD_IS_MULTILINGUAL`.

**Why promotion cannot live in the typed grammar.** Adding `numericExpression` as an alternative of `numericSequence` makes the two rules mutually left-recursive (`numericExpression` already has `numericSequence '[' indexer ']'`), which ANTLR 4.13 rejects with error 119. The restriction was a structural limit of the typed design, not a language decision.

**Today's toolkit, in numbers.** 1,550 tests green on `develop`. `EfxExpressionTranslatorV2` has 464 exit methods, 328 of them per-type or late-bound; 162 of the 522 expression listener methods are never executed by a test. Error-code assertions in the tests: `FIELD_MAY_REPEAT` 12, `FIELD_IS_MULTILINGUAL` 5, `IDENTIFIER_IS_SCALAR` 3, `IDENTIFIER_IS_SEQUENCE` 2, `NODE_CONTEXT_AS_VALUE` 3, `INCOMPATIBLE_OPERANDS` 3, `CANNOT_CONVERT` 2, `DICTIONARY_IS_SEQUENCE` 1. The eleven files coupled to the generated parser: `model/types/EfxTypeTokenLookup`, `sdk1/EfxExpressionTranslatorV1`, `sdk1/EfxTemplateTranslatorV1`, `sdk2/EfxComputeDependencyExtractor`, `sdk2/EfxDebugLexer`, `sdk2/EfxErrorStrategy`, `sdk2/EfxExpressionTranslatorV2`, `sdk2/EfxRulesTranslatorV2`, `sdk2/EfxTemplateTranslatorV2`, `sdk2/EfxValidationDependencyExtractor`, `util/EfxProfilerReportGenerator`.

## Notes for Yannis, outside this plan

- Ten language changes belong in the EFX documentation site: casts are removed, scalar-to-sequence promotion is uniform, an index means the nth element of the whole sequence in every position (a filter is unchanged; from P4.3 the index is spelled `[at n]`, `[...]` is a filter only, and the index's content may be any number, read once where the expression stands), names starting with `__` are reserved for the toolkit's generated variables, `count` accepts a node reference, a node read through an override (`ND-1::ND-2`, `$n::ND-Root`) is a node reference, the selector's override form `&{ND-1::BT-2/@a}` is removed, and the expression after `else`, `return` or `satisfies` extends as far right as it can, so `if c then 1 else 2 == 3` needs parentheses to compare the conditional, and a conditional whose branches differ in number of values is a sequence, the single value promoted (all decided 31 October, the spelling 1 November; no public interface of the toolkit changes). One precedence note for authors: the comparison family binds in the order written (`a == b in c` is `(a == b) in c`).
- Upstream pull requests to OP-TED are opened by Yannis himself, SDK first, after he has reviewed and merged the branches into his forks.
