/*
 * Copyright 2026 European Union
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the European
 * Commission – subsequent versions of the EUPL (the "Licence"); You may not use this work except in
 * compliance with the Licence. You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence
 * is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the Licence for the specific language governing permissions and limitations under
 * the Licence.
 */
package eu.europa.ted.efx.sdk2;

import static eu.europa.ted.efx.model.signatures.BuiltInParameter.parameter;
import static eu.europa.ted.efx.model.signatures.Overload.overload;
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.PATH;
import static eu.europa.ted.efx.model.signatures.ParameterType.T;
import static eu.europa.ted.efx.model.signatures.ParameterType.T_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.iteration.IteratorListExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.ScalarPath;
import eu.europa.ted.efx.model.identifiers.Dictionary;
import eu.europa.ted.efx.model.signatures.Composable;
import eu.europa.ted.efx.model.signatures.Composer;
import eu.europa.ted.efx.model.signatures.Overload;
import eu.europa.ted.efx.model.signatures.ParameterType;
import eu.europa.ted.efx.model.types.EfxDataType;
import eu.europa.ted.efx.model.types.EfxTypeLattice;

/**
 * A construct that the grammar writes inside an expression, rather than one the catalogue holds:
 * the parentheses, the conditional and the list; an iteration and a quantifier; a predicate, an
 * index and a lookup in a dictionary. Like a built-in, it has an {@link Overload} for each way it
 * can be written: a signature, and the composer that translates what the signature takes.
 *
 * <p>The visitor translates the parts of the construct first, in the scope or the context the
 * construct sets up, such as the variables of an iteration or the element a predicate reads; the
 * overload that takes them composes the construct. An iteration and a quantifier also compose
 * their iterators, which are not arguments, and a lookup its dictionary: such a construct is made
 * for each use, with them.</p>
 *
 * <p>A construct that gives a reference, such as a predicate, says only that it gives one,
 * {@code PATH}: the reference it gives is of the class of the reference it takes.</p>
 *
 * <p>A type error calls it by its name and its arguments by the names of its parameters, numbered
 * among the items of a list.</p>
 */
class ExpressionConstruct implements Composable {

  /** An expression in parentheses: a value of any type, which the parentheses keep as it is. */
  static final ExpressionConstruct PARENTHESES = new ExpressionConstruct("Parenthesised expression",
      overload(signature(parameter("content", T)).returning(T),
          (script, content) -> inParentheses(script, content, boundClassOf(T, content))),
      overload(signature(parameter("content", T_SEQUENCE)).returning(T_SEQUENCE),
          (script, content) -> inParentheses(script, content, boundClassOf(T_SEQUENCE, content))));

  /**
   * {@code if ... then ... else ...}: the branches have one primitive. Two single values take the
   * first overload, the more specific, and give a single value; a sequence on either side takes
   * the second, the other branch promoted to a sequence of one, and gives a sequence.
   */
  static final ExpressionConstruct CONDITIONAL = new ExpressionConstruct("Conditional 'if'",
      overload(signature(parameter("condition", INDICATOR), parameter("then branch", T),
          parameter("else branch", T)).returning(T),
          (script, condition, whenTrue, whenFalse) -> conditional(script, condition, whenTrue,
              whenFalse, boundClassOf(T, whenTrue))),
      overload(signature(parameter("condition", INDICATOR), parameter("then branch", T_SEQUENCE),
          parameter("else branch", T_SEQUENCE)).returning(T_SEQUENCE),
          (script, condition, whenTrue, whenFalse) -> conditional(script, condition, whenTrue,
              whenFalse, boundClassOf(T_SEQUENCE, whenTrue))));

  /** A list in brackets: single values of the primitive of the first, which give their sequence. */
  static final ExpressionConstruct LIST = new ExpressionConstruct("List",
      overload(signature().variadic(parameter("item", T)).returning(T_SEQUENCE),
          (script, items) -> script.composeList(items, boundClassOf(T_SEQUENCE, items.get(0)))));

  /** A predicate after a field, which gives the elements of the field that satisfy it. */
  static final ExpressionConstruct PREDICATE_ON_FIELD =
      predicate(ScriptGenerator::composeFieldReferenceWithPredicate);

  /** A predicate after a node, which gives the elements of the node that satisfy it. */
  static final ExpressionConstruct PREDICATE_ON_NODE =
      predicate(ScriptGenerator::composeNodeReferenceWithPredicate);

  /**
   * An index after a field or a node: the element at a position of the whole sequence. The
   * visitor gives it the sequence in parentheses, so that it selects from the whole sequence.
   */
  static final ExpressionConstruct INDEX_IN_PATH = new ExpressionConstruct("Index",
      overload(signature(parameter("sequence", PATH), parameter("position", NUMBER))
          .returning(PATH),
          (script, sequence, position) -> script.composeIndexer(sequence.asSequence(), position,
              elementClassOf(sequence))));

  /**
   * An index after a value: the value at a position of the sequence of its values, a single value
   * being a sequence of one.
   */
  static final ExpressionConstruct INDEX_IN_VALUE = new ExpressionConstruct("Index",
      overload(signature(parameter("sequence", T_SEQUENCE), parameter("position", NUMBER))
          .returning(T),
          (script, sequence, position) -> script.composeIndexer(sequence, position,
              boundClassOf(T, sequence))));

  private final String name;
  private final List<Overload> overloads;

  private ExpressionConstruct(final String name, final Overload... overloads) {
    this.name = name;
    this.overloads = List.of(overloads);
  }

  /**
   * Gives the body of an iteration, which gives values for each iteration: a single value is a
   * sequence of one, and the iteration gives the sequences joined.
   *
   * @param iterators the iterators of the iteration, whose variables the body reads
   * @return the iteration
   */
  static ExpressionConstruct iteration(final IteratorListExpression iterators) {
    return new ExpressionConstruct("Iteration 'for'",
        overload(signature(parameter("body", T_SEQUENCE)).returning(T_SEQUENCE),
            (script, body) -> script.composeForExpression(iterators, body,
                boundClassOf(T_SEQUENCE, body))));
  }

  /**
   * Gives the condition of {@code every}, which gives whether every iteration satisfies it.
   *
   * @param token the quantifier as written
   * @param iterators the iterators of the quantifier, whose variables the condition reads
   * @return the quantifier, whose result is an indicator
   */
  static ExpressionConstruct every(final String token, final IteratorListExpression iterators) {
    return quantifier(token, (script, condition) -> script.composeAllSatisfy(iterators, condition));
  }

  /**
   * Gives the condition of {@code some}, which gives whether some iteration satisfies it.
   *
   * @param token the quantifier as written
   * @param iterators the iterators of the quantifier, whose variables the condition reads
   * @return the quantifier, whose result is an indicator
   */
  static ExpressionConstruct some(final String token, final IteratorListExpression iterators) {
    return quantifier(token,
        (script, condition) -> script.composeAnySatisfies(iterators, condition));
  }

  /**
   * Gives a key in brackets after the name of a dictionary, which gives the values the dictionary
   * indexes by the key.
   *
   * @param reference the name of the dictionary, as written
   * @param dictionary the dictionary
   * @return the lookup, whose result is of the type of the dictionary's values
   */
  static ExpressionConstruct lookup(final String reference, final Dictionary dictionary) {
    return lookup(reference, dictionary.name, dictionary.type);
  }

  private static <E extends TypedExpression> ExpressionConstruct lookup(final String reference,
      final String dictionary, final Class<E> type) {
    return new ExpressionConstruct("Dictionary '" + reference + "'",
        overload(signature(parameter("key", TEXT)).returning(ParameterType.of(type)),
            (script, key) -> script.composeDictionaryLookup(dictionary, key, type)));
  }

  /**
   * Gives a predicate. A number for its condition is a position written as a predicate, so its
   * errors suggest the index.
   *
   * @param composer the composer of the predicate on what it follows, a field or a node
   * @return the predicate
   */
  private static ExpressionConstruct predicate(
      final Composer.Binary<PathExpression, BooleanExpression, PathExpression> composer) {
    return new ExpressionConstruct("Predicate",
        overload(signature(parameter("sequence", PATH), parameter("condition", INDICATOR))
            .returning(PATH), composer)) {

      @Override
      public List<String> suggestionsFor(final List<? extends TypedExpression> arguments) {
        return arguments.get(1).is(EfxDataType.Number.class)
            ? List.of("To select by position, write [at n].")
            : List.of();
      }
    };
  }

  private static ExpressionConstruct quantifier(final String token,
      final Composer.Unary<BooleanExpression, BooleanExpression> composer) {
    return new ExpressionConstruct("Quantifier '" + token + "'",
        overload(signature(parameter("condition", INDICATOR)).returning(INDICATOR), composer));
  }

  @Override
  public String describe() {
    return this.name;
  }

  /**
   * @return the name of the parameter at the position, numbered among the items of a list; the
   *         overloads of a construct name their parameters alike
   */
  @Override
  public String roleOf(final int position, final int count) {
    return this.getParameterLists().get(0).roleOf(position, count);
  }

  @Override
  public List<Overload> getOverloads() {
    return this.overloads;
  }

  /**
   * Gives the class a type variable is bound to by an argument that the overload of a construct
   * has taken, which binds it.
   */
  private static <E extends TypedExpression> Class<? extends E> boundClassOf(
      final ParameterType<E> typeVariable, final TypedExpression argument) {
    return typeVariable.boundBy(argument).orElseThrow().getExpressionType();
  }

  /**
   * Gives the class of an element of the sequence a path selects: a path of a single value of its
   * primitive.
   */
  private static Class<? extends ScalarPath> elementClassOf(final PathExpression sequence) {
    return ScalarPath.fromEfxDataType.get(EfxTypeLattice.toPrimitive(sequence.getDataType()));
  }

  private static <E extends TypedExpression> E inParentheses(final ScriptGenerator script,
      final TypedExpression content, final Class<E> type) {
    return script.composeParenthesizedExpression(TypedExpression.from(content, type), type);
  }

  private static <E extends TypedExpression> E conditional(final ScriptGenerator script,
      final BooleanExpression condition, final TypedExpression whenTrue,
      final TypedExpression whenFalse, final Class<E> type) {
    return script.composeConditionalExpression(condition, type.cast(whenTrue),
        type.cast(whenFalse), type);
  }
}
