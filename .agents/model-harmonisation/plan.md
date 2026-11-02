# Step 2: harmonise the untyped-grammar branch with the toolkit's model

2026-11-02 · Ioannis Rousochatzakis

Copied from the Claude Doc https://claude.ai/artifact/VkJ79cXCcTCTurSDyHMdJt (revision 5), approved by Yannis on 2026-11-02. If Yannis overturns a decision of section 6, the change enters this plan there.

## 1. Goal and the one rule

Step 1 (branch `feature/efx2-untyped-grammar`: toolkit fcaedc8, SDK efc9a21) moved type checking from the grammar into the toolkit. It is accepted and frozen. It added 36 types to the toolkit, and several of them say again what a class of the pre-existing model already said.

Step 2 removes those redundancies. When it is done, the toolkit has one class per idea, and every class and name added by step 1 is justified.

**The one rule.** A class added by step 1 stays only if it carries knowledge or behaviour that no pre-existing class can carry. Otherwise the pre-existing class is extended and the added class goes. This plan applies the rule itself: section 4 fixes, for every item, which classes exist afterwards, their names and what each one knows; section 6 gives the reasoning behind each choice. The implementing thread's design doc per item shows the code for that shape, nothing more. Yannis approves or overturns each choice in section 6.

**What does not change.** The output (all tests green, every corpus translation identical), the language, the grammar, the SDK. Public API changes are allowed, listed, and each approved by Yannis before it lands.

**Not in step 2 (parked, unchanged).** The five type vocabularies of the toolkit (ParameterType, model.types.EfxDataType, autocomplete.EfxDataType, EfxTypeTokenLookup, FieldTypes), the multilingual-field question, context overrides, the `[?filter]` idea.

## 2. Branches and process

1. Step 1 stays at toolkit fcaedc8 and SDK efc9a21. No more commits on it, and it is not merged. The review PRs rousso/efx-toolkit-java#1 and rousso/eForms-SDK#1 stay open as review surfaces.
2. Step 2 is one new toolkit branch off fcaedc8 in rousso/efx-toolkit-java, named `feature/efx2-model-harmonisation`. The SDK needs no step 2 branch, because nothing in this plan touches the grammar or the corpus. An item that would need the SDK stops and asks.
3. The implementing thread executes the items in order, in its own thread: a short design doc per item showing the code for the shape fixed here, Yannis's card to commit, one commit per item, pushed to the fork.
4. The design thread reviews each landed commit through the fork and records the verdict.
5. After the gate (section 7): step 2 merges into step 1, then step 1 into develop, each merge on Yannis's word. Then the deferred pre-merge list: re-archive and delete `.agents`, fork merges, upstream PRs (SDK first), Jira.

## 3. Rules of engagement

Carried over from step 1, unchanged:

- One commit per item. Pushed history is never rewritten.
- Output is sacred. Tests green and the corpus sha256 list identical at every commit. A change to intended output stops the item and goes to Yannis. A regression the branch itself introduced is fixed and reported, never put on a card.
- No language or grammar change. Nothing changes in the SDK.
- Stop and ask before any public API change, any rename of a public type, anything outside the toolkit.
- Commits are authored by Ioannis Rousochatzakis, with no model names and no co-author trailers. Only the forks under `rousso/`, never OP-TED.
- Clean object-oriented code in the toolkit's own patterns: no helper or utility classes, no hacks, no long methods, no fully qualified names in code as a fix.

**Words.** The design docs use the vocabulary agreed on 2026-11-02. A program is one expression. An expression is a valid combination of constructs. The eleven construct types are operators, built-in functions, the conditional, loops, quantifiers, lists, filters and indexes, literals, references, field properties, and grouping. The word "operand" is used only when speaking of operators. A term is defined before it is used, from terms defined earlier.

## 4. The items

Each item is one design doc and one commit. The classes named below are the outcome; section 6 gives the reasoning. "Pre-existing" means on develop before step 1; "added" means added by step 1.

**H0. Branch and baseline.** Create `feature/efx2-model-harmonisation` off fcaedc8. Record the baseline: the test count, the sha256 of every corpus translation, and the list of public types on the branch. Add the step 2 plan file where step 1 kept its plan.

**H1. One parameter model.** Afterwards, `Parameter` is an interface in `model.signatures`: a name and a type, the type being a `ParameterType`. Two classes implement it. The pre-existing `ParsedParameter` (`model.variables`) is a parameter declared in EFX: it stays an `Identifier`, keeps its reference expression, and answers its type from that expression's class. `BuiltInParameter` (`model.signatures`) is a parameter declared in the catalogue: a type pattern that can be bound (today the added class `Parameter`). `Parameters<P extends Parameter>`, in `model.signatures`, is the one list with the matching: a `LinkedList<P>` with the optional variadic parameter, `takes`, `match` and `bind` of the added list; the catalogue holds a `Parameters<BuiltInParameter>`. The pre-existing `ParsedParameters` stays, as `ParsedParameters extends Parameters<ParsedParameter> implements ParsedEntity` in `model.variables`: the list a declaration holds and the call stack carries by its class, with its constructors (the one from `Variables` included) and `toMap` as they are. `Parametrised`, `Function`, `Template`, `DynamicFunction`, `TemplateDefinition`, `ContentBlock` and `CallStack.getFunctionParameters` keep their `ParsedParameters`. `Parameters.of(ParsedParameters)` goes: the declaration's list is the matching list. `model.signatures` imports nothing from `model.variables`. The signature builder (`signature(parameter(...)).returning(...)`) keeps its spelling. The SDK 1 translators are not touched: they extend EfxBaseListener and do not use these classes. Goes: the added `Parameter` class (now `BuiltInParameter`); the added `Parameters` is reshaped into the generic matching list that `ParsedParameters` extends.

**H2. One way to check every construct** (proposal 1). Afterwards, the added interface `BuiltIn` is renamed `Construct` and absorbs the added class `exceptions.Construct`: a `Construct` is the description of one kind of construct, with its catalogue name, the name and role words a message uses for it and its positions, its overloads, `resolve`, and the two catalogue flags with defaults. The ten catalogue enums and `PatternMatch` implement it. The pre-existing `Parametrised` implements it too, with one overload built from its `Parameters` and its result type, so a call of a declared function or template is checked exactly as a built-in call is. `BuiltIns` keeps its name: the catalogue of the built-in ones. Every construct the visitor and the two listeners check goes through `resolve`: lists as `(item: T...) -> T*`; the conditional as `(INDICATOR, T, T) -> T` and `(INDICATOR, T*, T*) -> T*`; each clause (WHEN, ASSERT, REPORT, OTHERWISE ASSERT and OTHERWISE REPORT, COMPUTE, `when`, `by`, hyperlink, the five label parts, the expression block) as a one-parameter `Construct` constant; the role words are computed from position and count as today, with the same words. `Diagnosis` takes the `Construct`. Goes: `BuiltIn` (renamed), `exceptions.Construct` (absorbed), `sdk2.Operands` with its 27 uses, the pre-existing `StrictArguments` (public), the hand-built `Diagnosis` in `argumentsOf`. Known risk: an expression with two faults may be blamed at a different position than today; each such test-message change goes to Yannis before it lands, wording unchanged.

**H3. One argument model.** Afterwards, the pre-existing `ParsedArguments` is the one list of the arguments of a call, for built-ins and declarations alike: for each position the parameter answered and the converted value, plus the typed accessors of the added `Arguments` (`stringAt`, `sequenceTypeAt` and the rest), which the composers use. `Parameters.match` produces it; `Overload.compose`, `Composer`, `Match` and `Resolution` take it. `ParsedArgument` holds a `Parameter` and its value, instead of being a `ParsedParameter`, and `ParsedArguments` is typed by the kind of parameter it pairs, so the template model (`ContentBlock`, `TemplateDefinition`, `InvokeContentTemplate`) keeps reading the name, type and reference expression of what it passes down, without a cast. Goes: `signatures.Arguments`.

**H4. The answers slimmed** (proposal 2). Afterwards, `Parameters.takes(count)` is asked before matching, so `Match` has two states only, matched (with the `ParsedArguments`) or rejected (position, required type, the argument), with one factory each and no field read in the wrong state. `Mismatch.Rejection` goes: its two facts, the overload and its `Match`, live in `Mismatch`'s list; the reporting order and `diagnose` stay as they are. `Resolution` keeps its two outcomes behind `isResolved()`, built by two factories, so no field is null by construction. Java 11, so no sealed types; the design doc shows the three classes before and after. No message changes.

**H5. One holder of the translation state** (proposal 3). Afterwards, the added `sdk2.TranslationContext` is named `Translation` (package-private): the symbols, the script generator, the call stack and the EFX context stack of one translation. `TranslatorListener` and `EfxExpressionVisitor` hold the `Translation` and read the four through it; their own four fields and accessors go (about 450 reference edits, mechanical, no behaviour change). The pre-existing public `interfaces.TranslatorContext`, the template section handed to the `MarkupGenerator`, is not touched.

**H6. Names and the Function clash.** (a) `Operand<E>` is renamed `Subexpression<E>`: the visitor's result for one construct, its translation and its origin. (b) `Origin` and its eight kinds keep their names. (c) The `Function` clash in the visitor (`model.variables.Function` against `java.util.function.Function`, a fully qualified name today) goes: whatever H2 leaves of the two lambda parameters of `builtIn(...)` becomes a nested enum of the two readings, value and path, with the error it implies; no `java.util.function` import remains in the visitor. (d) `EfxExpressionVisitor` keeps its name and its package.

**H7. Public API and CHANGELOG.** List every public type removed, renamed or reshaped by H1 to H6 (at least `StrictArguments`, `ParsedParameters`, `signatures.Parameters`, `Parameter`, `Parametrised`, `BuiltIn`, `exceptions.Construct`, `signatures.Arguments`, `Match`, `Mismatch.Rejection`, `Resolution`) and write the toolkit CHANGELOG entry. The SDK CHANGELOG is not touched.

## 5. Order and dependencies

H0, H1, H2, H3, H4, H5, H6, H7, in that order.

- H2 needs H1: a declaration implements `Construct` with one overload only once it holds the one `Parameters` list the matching uses.
- H3 follows H2: once `Operands` is gone, only `Arguments` and `ParsedArguments` are left to merge.
- H4 follows H3: the answer classes carry the argument list.
- H5 does not depend on H1 to H4, but it changes about 450 lines, so it comes after the structural items to keep each diff readable.
- H6 comes last among the code items: renames are cheapest once the set of classes is final.
- H7 closes.

## 6. The decisions, with the analysis

Each decision is taken here, from the code, with its reason. Yannis approves or overturns each one with a line; the implementing thread executes what stands and puts nothing of this on a card again.

**1. The branch name.** `feature/efx2-model-harmonisation`: it says what step 2 does, and the step 1 branch keeps `feature/efx2-untyped-grammar`.

**2. One parameter model (H1).** Facts: the pre-existing `ParsedParameter` is an `Identifier` (name, data type) with the reference expression that reads it; `CallStack.getParameter` finds it in the identifier registry, so it must stay an `Identifier`. The added `Parameter` is a name and a `ParameterType`: a pattern (a concrete type, a type variable, or a path kind) that can be bound; no pre-existing class can carry a pattern. The two lists: `ParsedParameters`, a LinkedList and a `ParsedEntity` collected on the call stack while a declaration is parsed (the stack finds its entries by class), and the added `Parameters` (fixed and variadic, `takes`, `match`, `bind`), built from the former at every call by `Parameters.of`. Decision: `Parameter` becomes the interface both classes implement, because `ParsedParameter` cannot extend the added class (it already extends `Identifier`); the added class is named `BuiltInParameter`, which says where its parameters are declared. `Parameters<P extends Parameter>` is the one matching list, and `ParsedParameters` stays as its specialisation for EFX-declared parameters: a generic type has no class of its own at run time, so a named subclass gives the stack a class to find without unchecked conversions or casts, keeps the pre-existing constructors and `toMap`, and changes the model the least; `Parameters.of` goes because a `ParsedParameters` is a `Parameters`. The word "Parsed" stays where it is true: parsed from EFX. Home: `model.signatures` for `Parameter`, `BuiltInParameter` and `Parameters`, `model.variables` for `ParsedParameter` and `ParsedParameters`, so that `model.variables` depends on `model.signatures` in one direction only and the matching's package-private internals (the `Match` factories, `TypeVariableParameterType.bindingOf`) stay hidden; the first version of this plan said `model.variables` for everything and dropped `ParsedParameters`, and the implementing thread's H0 findings changed both. `interfaces.Parameter` (the markup side) stays: after H1 no file needs both names, so this clash is accepted where the `Argument` one was not. Public API: `ParsedParameters` changes its superclass; `Parametrised.parameters` keeps its type.

**3. One argument model (H3).** Facts: the pre-existing `ParsedArgument` is a `ParsedParameter` with the value given for it, and `ParsedArguments` is their list, read by the template model for the pairs and by the expression side only for the values. The added `Arguments` holds the converted values and the bound type of each position, with typed accessors the composers use; only `Parameters.match` makes one. The bound type of a position is the bound parameter's type, so the pairing already gives it. Decision: `ParsedArguments` survives, because every argument is parsed from EFX, so the word is true for built-ins too, and because `interfaces.Argument` already exists, so an `Argument` in the model would be a second clash like `Function`. It gains the typed accessors; `Arguments` goes. `ParsedArgument` holds a `Parameter` and a value instead of being a `ParsedParameter`, because the parameter may now be a catalogue parameter; the list is typed by the kind of parameter it pairs, so the template model keeps reading `ParsedParameter`s without a cast.

**4. The interface every construct implements (H2).** Facts: the added `BuiltIn` is a name, overloads, `resolve` and two catalogue flags; ten enums and `PatternMatch` implement it. The added `exceptions.Construct` is the same construct seen from a message: its display name and the role word of each position ("left operand", "2nd argument", "then branch"), built at 37 call sites, one next to each `BuiltIn` use. The pre-existing `Parametrised` is "an identifier that accepts parameters": the same idea for declared functions and templates, with one overload. An enum cannot extend a class, so the common idea must be an interface, and a declared function is not built in, so `BuiltIn` cannot be its name. Decision: the interface is `Construct`, the agreed word for a kind of building block: it describes one kind of construct, how it is named in the catalogue and in messages, what it takes (the overloads) and what it gives. It absorbs `exceptions.Construct`, so a construct is described once. `Parametrised` stays the class for declarations and implements it; the enums and `PatternMatch` implement it; `BuiltIns` keeps its name as the catalogue of the built-in ones. Public API: `BuiltIn` renamed, `exceptions.Construct` gone.

**5. Which context class is renamed (H5).** Facts: `interfaces.TranslatorContext` is public and pre-existing: the template section handed to the `MarkupGenerator`, an interface consumers implement (the notice viewer builds against it). `sdk2.TranslationContext` is package-private and added. Decision: the added one is renamed, to `Translation`: it is the state of one translation (symbols, script generator, call stack, EFX context stack), the name is free in the toolkit, and `translation.script()` reads as what it is. The public one is not touched.

**6. The name of `Operand<E>` (H6a).** Facts: it is the visitor's result for every construct it visits: the translation, a `TypedExpression`, and the origin, what the sub-expression was written as (a field, a node, a variable, a value), which the containing construct uses to read it as a value, a path or a context and to word errors. "Operand" is reserved for operators. `Translated` was rejected; `TranslatedExpression` would sit next to `TypedExpression`, which already means a translated expression. Decision: `Subexpression<E>`, the agreed word for a construct inside an expression: the visitor returns, for each construct, the sub-expression it stands for, with its translation and its origin. Package-private, so no API change.

**7. The Origin kinds (H6b).** Facts: `Origin` has eight kinds, all package-private: None, ValueOrigin, FieldOrigin and NodeOrigin (paths), VariableOrigin and ContextVariableOrigin (identifiers), DictionaryNameOrigin and DictionaryOrigin. `FieldOrigin` builds a `Context.FieldContext` on demand, which is where the two names meet. Decision: keep them. Each name says what the sub-expression was written as, no pre-existing class carries that knowledge, and a `FieldOrigin` is not a `FieldContext`: one is how a sub-expression was written, the other is the context a line or rule runs in. Renaming for distance alone would be churn.

**8. The visitor's name and package (H6d).** Facts: `EfxExpressionVisitor` is package-private in `sdk2`, extends the generated `EfxBaseVisitor`, and is subclassed privately by the rules and template translators; the public `EfxExpressionTranslatorV2` is the single-expression entry point that drives it. Moving it to its own package widens the visibility of its constructor, visit entry points, subclass hooks and `Subexpression.value()`. Decision: keep the name and the package. The toolkit names classes after what they are (`TranslatorListener`, `ExpressionSkippingWalker`, `IncludeProcessor`), and this one is an ANTLR visitor over expressions; "translator" in the toolkit names a public entry point, so an `ExpressionTranslator` beside `EfxExpressionTranslatorV2` would be the kind of near-duplicate name this step removes. Package-private is a feature here, not a cost.

**9. The words for an operator's parts.** Facts: the catalogue describes an operator with `parameter(...)`, as it does a function; the messages call an operator's parts "operand", "left operand" and "right operand", and a function's "1st argument" (`exceptions.Construct` today, the `Construct` interface after H2). Decision: in the code, "parameter" and "argument" for every construct, so one model serves all; in messages, "operand" for operators, as today. The rule is about the words people read, and those already follow it.

If Yannis overturns a decision, the change enters this plan here, and the implementing thread works from the plan, never from a card of its own.

## 7. The gate

Step 2 is done when all of these hold:

- Every item H0 to H7 has landed as one commit on the step 2 branch, and the design thread has reviewed it.
- Tests are green and the corpus sha256 list equals the H0 baseline.
- Every decision of section 6 is applied as it stands after Yannis's review.
- No class added by step 1 remains without its one-sentence justification.
- Every public API change is in the CHANGELOG.
- Yannis has reviewed the branch.

Then the merges of section 2, each on Yannis's word.
