# EFX Toolkit 2.0.0-alpha.9 Release Notes

_The EFX Toolkit for Java developers is a library that enables the transpilation of [EFX](https://docs.ted.europa.eu/eforms/latest/efx) expressions and templates to different target languages. It also includes an implementation of an EFX-to-XPath transpiler._

---

## In this release

This is an incremental update over 2.0.0-alpha.8.

### Changes since 2.0.0-alpha.8

- **Symbol resolution**: applications can now use their own metadata-loading mechanism while reusing the toolkit’s XPath symbol-resolution logic. This logic has been extracted into the abstract `XPathSymbolResolver` class. The existing `SdkSymbolResolver` extends it and continues to load metadata from SDK files using the eForms Core Library. The `SymbolResolver` javadoc now states what the translators already required: every class of the paths a resolver returns must have a public constructor that takes the path's script alone.
- **EFX-2 type checking in the translator**: the EFX-2 grammar no longer types expressions (see the SDK changelog). The EFX-2 translators infer the type of each part of an expression from its literals, from the field metadata and from the declarations in scope, and reject an expression whose types do not fit. Every operator and built-in function is modelled, with its overloads, in the new package `eu.europa.ted.efx.model.builtins`. The new package `eu.europa.ted.efx.model.constructs` holds what every construct is checked through: the interface `Construct`, the `Signature` of each overload, and one model of parameters and arguments that built-ins and declared functions and templates share (`Parameter`, `Parameters`, `Argument`, `Arguments`). `EfxTypeTokenLookup` gains `toJavaType(String)` and `nameOf(Class)`. Expressions are translated by a visitor over the expression tree, which the three EFX-2 translators share. The generated XPath is unchanged except where the changes below say. EFX-1 translation is unchanged.
- **EFX-2 language changes**, as listed in the SDK changelog: casts are removed; a single value is accepted wherever a sequence is; an index is written `[at n]`, selects the nth element of the whole sequence in every position and reads its position once, where the expression stands, while `[...]` is a predicate only; `count` counts nodes; a node read through a context override is a node; a selector no longer reads an attribute through a context override; the expression after `else`, `return` or `satisfies` extends as far to the right as it can; a conditional whose branches differ in number of values is a sequence; names that start with `__` are reserved for the variables the translator generates, and declaring one raises `IDENTIFIER_IS_RESERVED`. The call stack checks it, for the prefix given to its new constructor `CallStack(TypeChecker, String)`.
- **Uniform type-error messages**: every type error of the EFX-2 translators names the construct, what it was given and what the rejected position takes, and may suggest a fix, as in "Type mismatch. Predicate cannot take 'date*' and 'number'. Its condition must be 'indicator'. To select by position, write [at n]." The new public class `Diagnosis` in `eu.europa.ted.efx.exceptions` carries what a message says about the `Construct` it names. A call of a declared function or template with the wrong number of arguments, and a call outside a template of a built-in only templates can call, name the call the same way, with its position: "line 2:10 Argument number mismatch. Function '?greeting' takes 1 argument but got 0."
- **Template translator**: in a nested template line, an index in the context counts within the element of the line above, and a position that reads a field reads it there; an attribute field in an indexed line reads that line's element. The variable that the translator's own loops bind, for formatted numbers, durations, dates and times and for the asset ids of a label, is now `$__item`, so it no longer hides a template variable named `$item`.
- **Rules translator**: a `null` error listener keeps ANTLR's own listeners, as in the other translators, where it raised a `NullPointerException`.
- **Context variables**: a field context variable used as a value gives the value of its field, read from the element it holds, as `$c::BT-00-Number` does. In `for context:$c in BT-00-Number return [1, 2, 3][at $c]`, the index now reads the number; it read the element, which kept every value.
- **Privacy properties**: `:publicationDate`, `:justificationCode` and `:justificationDescription` on a field that cannot be withheld raise `InvalidUsageException` with `FIELD_NOT_WITHHOLDABLE`, where they failed with "Unknown symbol 'null'".
- **Autocomplete**: the function items of `EfxAutocomplete` are derived from the signatures of the built-in functions, through the new `FunctionCompletions.itemsOf(BuiltIn)`, `BuiltIn.isFunction()` and `Signature.concreteForms()`. EFX-2 lists 140 function items where it listed 113. Added (27): `current-date()`, `current-time()`, `count-duplicates` and `get-duplicates` for each value type, the conversions `text`, `number`, `indicator`, `date` and `time`, and `format-short`, `format-medium` and `format-long` of a time alone. Changed (4): the format of `format-number` is no longer optional; `concat(values...)` replaces `concat(first, rest...)`; `preferred-language` and `preferred-language-text` take a `TEXT_SEQUENCE`. `count` of a node is not listed. The items follow the order of the signatures that give them.
- **Performance**: in our measurements, translating the 52 view templates and the validation rules of the SDK takes about a fifth of the time it took with the typed grammar (85 to 101 s, where it took 411 to 461 s).

The following sections describe the features of the 2.0.0 line. For the changes introduced in the preceding release, see the [2.0.0-alpha.8 release notes](https://github.com/OP-TED/efx-toolkit-java/releases/tag/2.0.0-alpha.8).

### EFX-2 language support

- Implemented all new EFX-2 language features introduced in SDK 2.0.0-alpha.2. See the [SDK changelog](https://github.com/OP-TED/eForms-SDK/blob/release/2.0.0-alpha.2/CHANGELOG.md) for the full list.

### EFX Rules translator

- Added a new EFX Rules to Schematron translator, enabling business rules authored in EFX to be transpiled into Schematron validation schemas.
- Rules that apply to all notice subtypes are deduplicated into a shared Schematron pattern.
- New `ValidatorGenerator` interface for Schematron output generation.

### Dependency analysis

- Added dependency extractors for EFX rules, producing dependency graphs that track field, codelist, and variable references.

### Template translator

- Dictionary lookups are now translated using XSL keys for better performance.
- Profiling and timing support for template translation via new `TranslatorOptions` methods.

### EFX autocomplete API

- Added an autocomplete API for code editors, providing completion items for EFX keywords, built-in functions, and field properties.

### New interfaces and classes

- `ValidatorGenerator`: Interface for generating validation output (Schematron).
- `TranslatorContext`: Interface providing translation context to `MarkupGenerator` methods.
- `TypeChecker`: Interface for SDK-specific type validation.
- `IncludedFileResolver`: Interface for resolving `#include` directives in EFX files.
- New typed exception hierarchy: `EfxCompilationException`, `InvalidArgumentException`, `InvalidIdentifierException`, `InvalidIndentationException`, `InvalidUsageException`, `SdkInconsistencyException`, `SymbolResolutionException`, `TranslatorConfigurationException`, `TypeMismatchException`.

### Bug fixes

- Fixed invalid XPath in typed uniqueness conditions.
- Fixed quote escaping in string literals.

## EFX-1 Support

Although this is a pre-release version of the EFX Toolkit, it provides production-level support for EFX-1 transpilation. EFX-1 is the current version of EFX released with SDK 1.

NOTE: Transpilation of EFX-1 to XPath and XSL in this version of the EFX Toolkit is **better than** what is provided by **EFX Toolkit 1.3.0**.

## Breaking changes

Since 2.0.0-alpha.8:

- `XPathContextualizer.join` takes a third argument, which says how far the joined path may be shortened; the two-argument form has been removed.
- This version needs the EFX-2 grammar of the SDK pre-release that follows 2.0.0-alpha.4, which no longer types expressions; it does not build against the grammar of 2.0.0-alpha.4.
- EFX-2 written with a numeric index `[n]` must be written `[at n]`: `[n]` is now a type error whose message suggests the new form. In the applications we built against this version, two test lines of the eForms Notice Viewer needed it.
- The generated parser classes of EFX-2 (`EfxParser`, `EfxListener`, `EfxBaseListener`) follow the new grammar: the rules and labels of the typed grammar are gone. `EfxVisitor` and `EfxBaseVisitor` are now generated for both grammars.
- `EfxTemplateTranslatorV2` and `EfxRulesTranslatorV2` no longer extend `EfxExpressionTranslatorV2`; the three translators extend the package-private `TranslatorListener`. The public listener methods of the typed grammar are gone, and so are the protected members of `EfxExpressionTranslatorV2`: the fields `symbols`, `script`, `errorListener`, `stack` and `efxContext`, and the methods `getFieldId`, `getNodeId`, `getLexerSymbol`, `resolveScalarType`, `composeFieldValueReference`, `resolveAndPushVariableReference` and `exitParameterDeclaration(ParserRuleContext, String, Class)`. The public constructors are unchanged, so a subclass that only calls them, as the eForms Notice Viewer's do, is unaffected.
- The public enum `EfxBuiltInFunction`, the hand-written list of function items, is removed; `EfxAutocomplete.getEfxCompletions` keeps its signature, and `FunctionCompletions.itemsOf` gives the items of one built-in.
- `TypeMismatchException`: `IDENTIFIER_IS_SCALAR` and `identifierIsScalar` are removed, since a single value is now accepted where a sequence is; the new factories `cannotConvert(ParserRuleContext, Diagnosis)` and `incompatibleOperands(ParserRuleContext, Diagnosis)` replace `incompatibleOperands(ParserRuleContext, String, Expression, Expression)`; `fieldMayRepeat`, `fieldIsMultilingual`, `nodeContextUsedAsValue`, `identifierIsSequence` and `dictionaryIsSequence` take a `Diagnosis` after the context. `InvalidArgumentException.argumentTypeMismatch(ParserRuleContext, Diagnosis)` replaces `argumentTypeMismatch(int, Parametrised, Class, Class)`. `InvalidArgumentException.argumentNumberMismatch(ParserRuleContext, Invocable, int)` replaces its two overloads, `(Parametrised, int, int)` and `(Parametrised, int)`. `InvalidUsageException.templateOnlyFunction(ParserRuleContext, String)` takes the call as an error names it, such as "Function 'format-short'", where it took the function's name. New error codes: `InvalidIdentifierException.ErrorCode.IDENTIFIER_IS_RESERVED` and `TranslatorConfigurationException.ErrorCode.AMBIGUOUS_OVERLOADS`.
- The package `eu.europa.ted.efx.model.variables` is renamed `eu.europa.ted.efx.model.identifiers`: what it holds are the identifiers that EFX source declares (`Identifier` and its subclasses `Variable`, `Function`, `Template`, `Dictionary`, `ParsedParameter` and the others), and not all of them are variables. Its classes keep their names. None of the eForms Notice Viewer, the eForms SDK Analyzer and efx-cli uses it.
- `ParsedArgument` and `ParsedArguments` are renamed `Argument` and `Arguments` and move from `eu.europa.ted.efx.model.variables` to `eu.europa.ted.efx.model.constructs`, where built-ins and declared functions and templates share them. `Argument<P extends Parameter>` holds a parameter and its value, and is no longer a `ParsedParameter`: its name and type are those of its new field `parameter`. `Arguments<P>` gains typed accessors (`at`, `from`, `stringAt`, `typeAt` and the others), loses `toSet()`, and is no longer a `ParsedEntity`. The constructors from a `Variable`, from `Variables` and from `ParsedParameters` are replaced by `Variables.toArguments()` and `ParsedParameters.toArguments()`, and a new constructor of `Arguments` takes the parameters and their values. `ContentBlock` and `InvokeContentTemplate` take an `Arguments<ParsedParameter>`; `ContentBlock.getOwnArguments()` gives it, and `ContentBlock.getAllArguments()` is removed (`getAllParameters()` gives the parameters a block passes on). `StrictArguments` is removed.
- `ParsedParameter` implements the new interface `Parameter` (`getName`, `getType`), and loses `getParameterType()`, the class of its reference expression, which `getType()` replaces; and `ParsedParameters` extends `Parameters<ParsedParameter>`, the parameter list that matches arguments, which is itself a `LinkedList`. `Parametrised` is renamed `Invocable`, after what a function and a template share, an invocation with arguments; it is abstract and implements `Construct`, so a call of a declared function or template is checked as a built-in call is; `Function` and `Template` give how a call writes them (`getSpelling()`), by which a message names them and their arguments. `Function` takes the class of its declared type after its parameters, and a call of it has that type rather than the type of its body. None of the eForms Notice Viewer, the eForms SDK Analyzer and efx-cli uses the classes that this entry and the one above name.
- `XPathSymbolResolver` answers whether a field repeats from a context through the field's parent node, and treats a multilingual field in its own context as one value, so `getRelativePathOfField` can type a path differently.

For the breaking changes introduced earlier in the 2.0.0 line, see the [2.0.0-alpha.6 release notes](https://github.com/OP-TED/efx-toolkit-java/releases/tag/2.0.0-alpha.6).

## Future development

Further alpha and beta releases of SDK 2 and EFX Toolkit 2 will be issued. While in "alpha" development stage, further breaking changes may be introduced.

---

You can download the latest EFX Toolkit from Maven Central.
[![Maven Central](https://img.shields.io/maven-central/v/eu.europa.ted.eforms/efx-toolkit-java?label=Download%20&style=flat-square)](https://central.sonatype.com/artifact/eu.europa.ted.eforms/efx-toolkit-java)

Documentation for the EFX Toolkit is available at: <https://docs.ted.europa.eu/eforms/latest/efx-toolkit>

---

This version of the EFX Toolkit has a compile-time dependency on the following eForms SDK versions and uses the EFX grammar that each version provides:

- eForms SDK 1.16.0-beta.3
- eForms SDK 2.0.0-alpha.4

It also depends on the [eForms Core Java library](https://github.com/OP-TED/eforms-core-java) version 1.9.1.
