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

## Overloads and generic signatures (H10 to H16)

H10 to H15 apply the rule Yannis agreed for overloads and generic signatures (see `progress.md`), and H16 nests the parameter types in `ParameterType`. H10 to H15 add or delete no public type: the classes they add or delete (`ConditionOperators.UniqueValueCondition`, `EitherCardinalityParameterType`) are private or package-private, as is `TypeVariableParameterType`, which H15 renames `GenericParameterType`, and the test built-in `SingleOrSequenceBuiltIn` is not in the jar. H16 makes the three parameter types public by nesting them. The members below are read with `javap -public` on H14 and H16. None of the members below is on develop, so the CHANGELOG lists none of them.

| Type | Item | Change |
| --- | --- | --- |
| `model.signatures.ParameterType` | H14 | `T_OR_T_SEQUENCE` and `expressionTypeOf` removed: no construct takes a single value or a sequence as it is any more, so every parameter type converts an argument to its one expression class |
| `model.signatures.Signature` | H13 | `resultFor(List<? extends TypedExpression>)` added: the result type of a call whose arguments the signature takes, with `T` bound by them |
| `model.signatures.ParameterType.Concrete` | H16 | added: the former package-private `ConcreteParameterType`, nested; its constructor stays package-private |
| `model.signatures.ParameterType.Path` | H16 | added: the former package-private `PathParameterType`, nested; its constructor stays package-private |
| `model.signatures.ParameterType.Generic` | H16 | added: the former package-private `GenericParameterType` (`TypeVariableParameterType` before H15), nested; an enum, `SCALAR` and `SEQUENCE`, which `ParameterType.T` and `T_SEQUENCE` already gave |

## Invocable (H17)

H17 renames `model.identifiers.Parametrised` `model.identifiers.Invocable`: the same public members, its constructor included, under the new name. The class is on develop (as `model.variables.Parametrised`, renamed package in H9), so the CHANGELOG lists the rename under breaking changes. `InvalidArgumentException.argumentNumberMismatch` takes an `Invocable` in its two overloads.


## Two questions for a call (H18)

H18 removes `model.signatures.Match` and adds `model.signatures.Rejection`, which holds what a rejected `Match` held (`getParameters`, `getPosition`, `getRequired`, `getKind`) and the enum `Rejection.Kind` (`CARDINALITY`, `PRIMITIVE`, `PATH`), formerly `Match.Kind`. `Parameters.match` gives an `Optional<ParsedArguments<?>>`, and the added `Parameters.rejectionOf` gives an `Optional<Rejection>`: exactly one of the two holds a value. `Resolution` is a final class that holds only a call that fits (`getParameters`, `getArguments`); `isResolved` and `getMismatch` are removed. `Construct.resolve` gives an `Optional<Resolution>`; the added `Construct.takes(int)` tells whether a parameter list takes that many arguments, and the added `Construct.mismatchOf` gives the `Mismatch` of a call of such a number. `Mismatch.isArityMismatch` is removed, since every mismatch has a rejection; `Mismatch.getRejection` and `getRejections` give `Rejection`. `ParameterType.rejectionKindOf` gives `Rejection.Kind`. None of these classes is on develop, so the CHANGELOG is unchanged.

## Composers typed by their signatures (H19)

H19 makes `ParameterType` generic, `ParameterType<E extends TypedExpression>`: `E` is the class `getExpressionType()` gives and `convert` returns; `bind` and `boundBy` give a `ParameterType<? extends E>`, and `of` takes a `Class<E>`. The constants are typed, as in `ParameterType<StringExpression> TEXT`, `ParameterType<ScalarExpression> T`, `ParameterType<SequenceExpression> T_SEQUENCE` and `ParameterType<PathExpression> PATH`. `ParameterType.Concrete<E>` and `ParameterType.Path<E extends PathExpression>` are generic, and `ParameterType.Generic<E>` is a final class instead of an enum, so `Generic.SCALAR` and `Generic.SEQUENCE` are removed: `T` and `T_SEQUENCE` are its two instances. `BuiltInParameter<E>` is generic; `Parameter.getType()`, `ParsedParameter.getType()`, `Rejection.getRequired()`, `Signature.getResult()` and `Signature.resultFor` give a `ParameterType<?>`. `Signature` is no longer final, and `Signature.Builder` is removed: `signature` takes no parameter up to three, instead of any number, and gives the builder of one of five added shapes, `Signature.Nullary<R>`, `Unary<A, R>`, `Binary<A, B, R>`, `Ternary<A, B, C, R>` and `Variadic<V, R>`, each with a nested `Builder` and getters of its typed parameters; `getParameters()` gives `Parameters<BuiltInParameter<?>>`. `Composer` gains the five matching shapes, `Composer.Nullary` to `Composer.Variadic`, and its four factories return them. `Overload.overload(Signature, Composer)` is replaced by five `overload` methods, one for each shape. `ParsedArguments` gains `from(int, Class<T>)`, which replaces `stringsFrom(int)`, and loses `dateAt`, `timeAt`, `durationAt`, `scalarAt` and `pathAt`. `Parameters.fixed` and `Parameters.variadic` are removed. Of these classes only `ParsedArguments` and `ParsedParameter` are on develop, and none of the members H19 changes is: the CHANGELOG entry on `ParsedArguments` names the accessors it gains without saying that the built-ins read them.

## Composables and clauses (H20)

H20 moves `Overload` and `Composer` from `model.builtins` to `model.signatures`; `Overload.negated()` is public, and the added `Overload.of(Signature, Composer)` declares the overload of a signature without a shape. It adds the interface `model.signatures.Composable extends Construct` (`getOverloads`, `getParameterLists` by default from them, `resolve`) and the class `model.signatures.Spelling` (`function`, `operator`, `describe`, `roleOf`). `Construct.resolve` is replaced by `Construct.match`, which gives an `Optional<ParsedArguments<?>>`; `Composable.resolve` gives an `Optional<Resolution>`. `Resolution` holds the overload that takes the call (`getOverload`, `getArguments`, `compose(ScriptGenerator)`); `getParameters` is removed. `BuiltIn` extends `Composable`, gains `getSpelling()`, and loses `compose(Resolution, ScriptGenerator)`. `Mismatch` gains `diagnose(Spelling)`. `Signature` gains `of(Parameters<?>, ParameterType<?>)`, and `getParameters()` gives a `Parameters<?>`; `Parameters` gains `of(List)`. `Function` implements `Composable`: its public constructor takes the class of its declared type after its parameters, a protected one also takes its composer, and its `dataType` is the declared type. `DynamicFunction` keeps its constructor. In `sdk2`, which has no public API here, `AsWritten` is removed, `GrammarConstruct` is renamed `ExpressionConstruct` and `Clause` is added. Of these classes only `Function` is on develop: the CHANGELOG entry on `Invocable` says what changes in it.

## The constructs package (H21)

H21 renames the package `model.signatures` to `model.constructs`. Measured with `javap` on 22e1270 and on H21: the same public types, of which 38 change package and nothing else, their members included; with the package name replaced, the two listings are identical. The package is not on develop, so the CHANGELOG names the new package and lists no breaking change.

| Was | Is |
| --- | --- |
| `model.signatures.BuiltInParameter` | `model.constructs.BuiltInParameter` |
| `model.signatures.Composable` | `model.constructs.Composable` |
| `model.signatures.Composer` | `model.constructs.Composer` |
| `model.signatures.Composer.Binary` | `model.constructs.Composer.Binary` |
| `model.signatures.Composer.Nullary` | `model.constructs.Composer.Nullary` |
| `model.signatures.Composer.SequenceFunction` | `model.constructs.Composer.SequenceFunction` |
| `model.signatures.Composer.SequencePairFunction` | `model.constructs.Composer.SequencePairFunction` |
| `model.signatures.Composer.SubsequenceFunction` | `model.constructs.Composer.SubsequenceFunction` |
| `model.signatures.Composer.SubsequenceWithLengthFunction` | `model.constructs.Composer.SubsequenceWithLengthFunction` |
| `model.signatures.Composer.Ternary` | `model.constructs.Composer.Ternary` |
| `model.signatures.Composer.Unary` | `model.constructs.Composer.Unary` |
| `model.signatures.Composer.Variadic` | `model.constructs.Composer.Variadic` |
| `model.signatures.Construct` | `model.constructs.Construct` |
| `model.signatures.Mismatch` | `model.constructs.Mismatch` |
| `model.signatures.Overload` | `model.constructs.Overload` |
| `model.signatures.Parameter` | `model.constructs.Parameter` |
| `model.signatures.ParameterType` | `model.constructs.ParameterType` |
| `model.signatures.ParameterType.Concrete` | `model.constructs.ParameterType.Concrete` |
| `model.signatures.ParameterType.Generic` | `model.constructs.ParameterType.Generic` |
| `model.signatures.ParameterType.Path` | `model.constructs.ParameterType.Path` |
| `model.signatures.Parameters` | `model.constructs.Parameters` |
| `model.signatures.ParsedArgument` | `model.constructs.ParsedArgument` |
| `model.signatures.ParsedArguments` | `model.constructs.ParsedArguments` |
| `model.signatures.Rejection` | `model.constructs.Rejection` |
| `model.signatures.Rejection.Kind` | `model.constructs.Rejection.Kind` |
| `model.signatures.Resolution` | `model.constructs.Resolution` |
| `model.signatures.Signature` | `model.constructs.Signature` |
| `model.signatures.Signature.Binary` | `model.constructs.Signature.Binary` |
| `model.signatures.Signature.Binary.Builder` | `model.constructs.Signature.Binary.Builder` |
| `model.signatures.Signature.Nullary` | `model.constructs.Signature.Nullary` |
| `model.signatures.Signature.Nullary.Builder` | `model.constructs.Signature.Nullary.Builder` |
| `model.signatures.Signature.Ternary` | `model.constructs.Signature.Ternary` |
| `model.signatures.Signature.Ternary.Builder` | `model.constructs.Signature.Ternary.Builder` |
| `model.signatures.Signature.Unary` | `model.constructs.Signature.Unary` |
| `model.signatures.Signature.Unary.Builder` | `model.constructs.Signature.Unary.Builder` |
| `model.signatures.Signature.Variadic` | `model.constructs.Signature.Variadic` |
| `model.signatures.Signature.Variadic.Builder` | `model.constructs.Signature.Variadic.Builder` |
| `model.signatures.Spelling` | `model.constructs.Spelling` |

## `Parameter.Impl` (H24)

H24 renames `model.constructs.BuiltInParameter<E>` to `model.constructs.Parameter.Impl<E>`, nested in the interface it implements; its members are unchanged (`parameter(String, ParameterType<E>)`, `getName`, `getType`, `toString`). The class is not on develop, so the CHANGELOG does not list it.

## `Argument` and `Arguments` (H25)

H25 renames `model.constructs.ParsedArgument<P>` to `model.constructs.Argument<P>` and `model.constructs.ParsedArguments<P>` to `model.constructs.Arguments<P>`. `Arguments` loses `toSet()`, whose only caller was `ContentBlock`; the other members are unchanged. Both classes were on develop, in `model.variables`, so the CHANGELOG's breaking-change line for them gives the new names. `interfaces.Argument`, the markup generator's, keeps its name; no file imports both. Of `ContentBlock`'s protected members, which `javap -public` does not list, `getOwnArguments()` gives `Arguments<ParsedParameter>` in place of `Set<ParsedArgument<ParsedParameter>>` and `getAllArguments()` is removed; the CHANGELOG says so.
