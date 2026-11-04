# Public API changes of step 2 (H1 to H6)

The public types of `src/main/java` that H1 to H6 removed, added or reshaped, measured with `javap -public` on the compiled classes of 487b712 (H0, the 281 types of `public-types.txt`) and of abcd603 (H6, 279 types). A type counts as public if it is public and nested only in public types; the generated parser, lexer, listener and visitor of the EFX grammars are left out. Compared with `javap -protected`, which adds what a subclass sees, the same 20 types differ and no other, so no protected member changed elsewhere; the three public translators that H5 and H6 touched keep their public and protected members. The changes to package-private classes (`sdk2.Translation`, `sdk2.Subexpression` and the visitor) are not listed: no consumer can see them.

"Develop" says whether the type exists on develop (89e0821, the base of step 1). A type that is not on develop was added in step 1, so for a consumer of 2.0.0-alpha.8 it is new in this release, however step 2 shaped it; only the types on develop need a breaking-change entry in the CHANGELOG. None of the eForms Notice Viewer (c90d83d), the eForms SDK Analyzer (7b7e8b4) and efx-cli (ec52757) uses any type below.

## Removed (7)

| Type | Develop | Item | What took its place |
| --- | --- | --- | --- |
| `exceptions.Construct` | no | H2 | the interface `model.signatures.Construct` |
| `model.signatures.Arguments` | no | H3 | `model.signatures.ParsedArguments` |
| `model.signatures.Mismatch.Rejection` | no | H4 | `Match`, which carries its parameter list |
| `model.signatures.Mismatch.Rejection.Kind` | no | H4 | `Match.Kind` |
| `model.variables.ParsedArgument` | yes | H3 | `model.signatures.ParsedArgument`, moved |
| `model.variables.ParsedArguments` | yes | H3 | `model.signatures.ParsedArguments`, moved |
| `model.variables.StrictArguments` | yes | H2 | nothing: a declaration's arguments are checked as a built-in's |

## Added (5)

| Type | Item | What it is |
| --- | --- | --- |
| `model.signatures.BuiltInParameter` | H1 | the added class `Parameter`, renamed: a name and a `ParameterType` |
| `model.signatures.Construct` | H2 | what a type error names and the one check method checks: `describe`, `roleOf`, `getParameterLists`, `resolve`, `suggestionsFor` |
| `model.signatures.Match.Kind` | H4 | `Mismatch.Rejection.Kind`, moved: `CARDINALITY`, `PRIMITIVE`, `PATH` |
| `model.signatures.ParsedArgument<P extends Parameter>` | H3 | a parameter and its value (fields `parameter`, `value`) |
| `model.signatures.ParsedArguments<P extends Parameter>` | H3 | the one list of the arguments of a call, with the typed accessors of the former `Arguments` |

## Reshaped (20)

| Type | Develop | Item | Change |
| --- | --- | --- | --- |
| `exceptions.Diagnosis` | no | H2 | its constructors take the construct's name and the role word as two strings, not an `exceptions.Construct`, and no longer take the `int` position |
| `model.signatures.BuiltIn` | no | H2 | extends `Construct`; `compose(Resolution, ScriptGenerator)` added, `resolve` inherited |
| `model.signatures.Composer` | no | H3 | `compose` takes `ParsedArguments<?>` |
| `model.signatures.Match` | no | H3, H4 | abstract; `getParameters()` added; `getKind()` gives `Match.Kind`; `getArguments()` gives `ParsedArguments<?>`; `isCountMismatch()` removed |
| `model.signatures.Mismatch` | no | H2, H4 | `diagnose()` takes no argument; `getRejection()` and `getRejections()` give `Match`; `unreadable(Construct, List, int)` added |
| `model.signatures.Overload` | no | H3 | `compose` takes `ParsedArguments<?>` |
| `model.signatures.Parameter` | no | H1 | an interface (`getName`, `getType`), no longer a class; the class is `BuiltInParameter` |
| `model.signatures.ParameterType` | no | H2, H3, H4 | `T_OR_T_SEQUENCE` and `expressionTypeOf` added; `rejectionKindOf` gives `Match.Kind` |
| `model.signatures.Parameters` | no | H1, H2 | `Parameters<P extends Parameter> extends LinkedList<P>`, no longer final; `fixed` and `variadic` factories, `roleOf`, `equals` and `hashCode` added; `parameterAt` and `getVariadic` answer in `P`; `of(ParsedParameters)` and `getFixed()` removed |
| `model.signatures.Resolution` | no | H2, H4 | abstract; holds the matched `Parameters<?>` and the arguments, or the mismatch; `compose` and `getOverload` removed |
| `model.signatures.Signature` | no | H1 | `getParameters()` gives `Parameters<BuiltInParameter>`; `signature(BuiltInParameter...)` |
| `model.signatures.Signature.Builder` | no | H1 | `variadic(BuiltInParameter)` |
| `model.templates.ContentBlock` | yes | H3 | its constructor takes `ParsedArguments<ParsedParameter>` |
| `model.templates.InvokeContentTemplate` | yes | H3 | its constructor takes `ParsedArguments<ParsedParameter>` |
| `model.variables.Function` | yes | H2 | `describe()` added |
| `model.variables.Parametrised` | yes | H2 | abstract; implements `Construct` (`roleOf`, `getParameterLists` added) |
| `model.variables.ParsedParameter` | yes | H1 | implements `Parameter` (`getName`, `getType` added) |
| `model.variables.ParsedParameters` | yes | H1, H3 | extends `Parameters<ParsedParameter>` instead of `LinkedList<ParsedParameter>`; `toArguments()` added |
| `model.variables.Template` | yes | H2 | `describe()` added |
| `model.variables.Variables` | yes | H3 | `toArguments()` added |

`TranslatorConfigurationException.ambiguousOverloads`, which the plan names, keeps its signature, so it is not in the tables: step 1 added it, and the CHANGELOG already lists its error code. H2 renamed its first parameter from `builtInName` to `construct` and dropped the quotes around that name in the `AMBIGUOUS_OVERLOADS` message, since the construct's own name now brings them ("Function '...'"). No test raises this message.

## After H7: the built-ins package (H8)

H8 moves the built-in catalogue out of `model.signatures` into the new package `model.builtins`. Measured with `javap` on 77b941d and on H8: the same 279 public types, of which 19 change package and nothing else, their members included. None is on develop, so none needs a breaking-change entry; the CHANGELOG's type-checking entry names both packages.

| Was | Is |
| --- | --- |
| `model.signatures.ArithmeticOperators` | `model.builtins.ArithmeticOperators` |
| `model.signatures.BuiltIn` | `model.builtins.BuiltIn` |
| `model.signatures.BuiltIns` | `model.builtins.BuiltIns` |
| `model.signatures.ComparisonOperators` | `model.builtins.ComparisonOperators` |
| `model.signatures.Composer` | `model.builtins.Composer` |
| `model.signatures.Composer.SequenceFunction` | `model.builtins.Composer.SequenceFunction` |
| `model.signatures.Composer.SequencePairFunction` | `model.builtins.Composer.SequencePairFunction` |
| `model.signatures.Composer.SubsequenceFunction` | `model.builtins.Composer.SubsequenceFunction` |
| `model.signatures.Composer.SubsequenceWithLengthFunction` | `model.builtins.Composer.SubsequenceWithLengthFunction` |
| `model.signatures.ConditionOperators` | `model.builtins.ConditionOperators` |
| `model.signatures.ConversionFunctions` | `model.builtins.ConversionFunctions` |
| `model.signatures.DateTimeFunctions` | `model.builtins.DateTimeFunctions` |
| `model.signatures.LogicalOperators` | `model.builtins.LogicalOperators` |
| `model.signatures.NumericFunctions` | `model.builtins.NumericFunctions` |
| `model.signatures.Overload` | `model.builtins.Overload` |
| `model.signatures.PatternMatch` | `model.builtins.PatternMatch` |
| `model.signatures.SequenceFunctions` | `model.builtins.SequenceFunctions` |
| `model.signatures.StringFunctions` | `model.builtins.StringFunctions` |
| `model.signatures.TemplateFunctions` | `model.builtins.TemplateFunctions` |

## The identifiers package (H9)

H9 renames the package `model.variables` to `model.identifiers`. Measured with `javap` on cc8e4a9 and on H9: the same 279 public types, of which 14 change package and nothing else, their members included. The package is on develop, so the CHANGELOG lists the rename under breaking changes.

| Was | Is |
| --- | --- |
| `model.variables.Dictionary` | `model.identifiers.Dictionary` |
| `model.variables.DynamicFunction` | `model.identifiers.DynamicFunction` |
| `model.variables.DynamicVariable` | `model.identifiers.DynamicVariable` |
| `model.variables.DynamicVariable.AutoGenerated` | `model.identifiers.DynamicVariable.AutoGenerated` |
| `model.variables.Function` | `model.identifiers.Function` |
| `model.variables.Functions` | `model.identifiers.Functions` |
| `model.variables.Identifier` | `model.identifiers.Identifier` |
| `model.variables.Identifiers` | `model.identifiers.Identifiers` |
| `model.variables.Parametrised` | `model.identifiers.Parametrised` |
| `model.variables.ParsedParameter` | `model.identifiers.ParsedParameter` |
| `model.variables.ParsedParameters` | `model.identifiers.ParsedParameters` |
| `model.variables.Template` | `model.identifiers.Template` |
| `model.variables.Variable` | `model.identifiers.Variable` |
| `model.variables.Variables` | `model.identifiers.Variables` |

## Overloads and generic signatures (H10 to H15)

H10 to H15 apply the rule Yannis agreed for overloads and generic signatures (see `progress.md`). They add or delete no public type: the classes they add or delete (`ConditionOperators.UniqueValueCondition`, `EitherCardinalityParameterType`) are private or package-private, as is `TypeVariableParameterType`, which H15 renames `GenericParameterType`, and the test built-in `SingleOrSequenceBuiltIn` is not in the jar. The members below are read with `javap -public` on H14. None of the members below is on develop, so the CHANGELOG lists none of them.

| Type | Item | Change |
| --- | --- | --- |
| `model.signatures.ParameterType` | H14 | `T_OR_T_SEQUENCE` and `expressionTypeOf` removed: no construct takes a single value or a sequence as it is any more, so every parameter type converts an argument to its one expression class |
| `model.signatures.Signature` | H13 | `resultFor(List<? extends TypedExpression>)` added: the result type of a call whose arguments the signature takes, with `T` bound by them |
