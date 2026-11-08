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
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.PATH;
import static eu.europa.ted.efx.model.signatures.ParameterType.T;
import static eu.europa.ted.efx.model.signatures.ParameterType.T_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.signatures.BuiltInParameter;
import eu.europa.ted.efx.model.signatures.Construct;
import eu.europa.ted.efx.model.signatures.Parameters;
import eu.europa.ted.efx.model.signatures.Resolution;
import eu.europa.ted.efx.model.signatures.Signature;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * A construct that the grammar writes and the catalogue does not hold: a composite such as the
 * conditional, a predicate or an index; the clause of a rule or a template that takes an
 * expression, such as {@code WHEN}; the value of a declaration; or a step that only a field has.
 * Like a built-in, it has a {@link Signature} for each way it can be written: its parameters, and
 * what it gives. It is checked against their parameters, and translated where it is written, with
 * the result of the signature that takes its arguments.
 *
 * <p>A construct that gives a reference, such as a predicate, says only that it gives one,
 * {@code PATH}: which reference it gives depends on the reference it takes, and is worked out where
 * it is translated.</p>
 *
 * <p>A type error calls it by its name and its arguments by the names of its parameters, numbered
 * among the items of a list. The fixed ones are constants, here or where they are written; those
 * named after what a declaration or a call writes are made for each one.</p>
 */
class GrammarConstruct implements Construct {

  /** An expression in parentheses: a value of any type, which the parentheses keep as it is. */
  static final GrammarConstruct PARENTHESES =
      GrammarConstruct.ofValueAsGiven("Parenthesised expression", "content");

  /**
   * {@code if ... then ... else ...}: the branches have one primitive. Two single values take the
   * first signature, the more specific, and give a single value; a sequence on either side takes
   * the second, the other branch promoted to a sequence of one, and gives a sequence.
   */
  static final GrammarConstruct CONDITIONAL = new GrammarConstruct("Conditional 'if'",
      signature(parameter("condition", INDICATOR), parameter("then branch", T),
          parameter("else branch", T)).returning(T),
      signature(parameter("condition", INDICATOR), parameter("then branch", T_SEQUENCE),
          parameter("else branch", T_SEQUENCE)).returning(T_SEQUENCE));

  /** A list in brackets: single values of the primitive of the first, which give their sequence. */
  static final GrammarConstruct LIST = new GrammarConstruct("List",
      signature().variadic(parameter("item", T)).returning(T_SEQUENCE));

  /**
   * The body of an iteration, which gives values for each iteration: a single value is a sequence
   * of one, and the iteration gives the sequences joined.
   */
  static final GrammarConstruct ITERATION = new GrammarConstruct("Iteration 'for'",
      signature(parameter("body", T_SEQUENCE)).returning(T_SEQUENCE));

  /**
   * A predicate after a field or a node, which gives the elements that satisfy it. A number for its
   * condition is a position written as a predicate, so its errors suggest the index.
   */
  static final GrammarConstruct PREDICATE = new GrammarConstruct("Predicate",
      signature(parameter("sequence", PATH), parameter("condition", INDICATOR)).returning(PATH)) {

    @Override
    public List<String> suggestionsFor(final List<? extends TypedExpression> arguments) {
      return arguments.get(1).is(EfxDataType.Number.class)
          ? List.of("To select by position, write [at n].")
          : List.of();
    }
  };

  /** An index after a field or a node: the element at a position of the whole sequence. */
  static final GrammarConstruct INDEX_IN_PATH = new GrammarConstruct("Index",
      signature(parameter("sequence", PATH), parameter("position", NUMBER)).returning(PATH));

  /**
   * An index after a value: the value at a position of the sequence of its values, a single value
   * being a sequence of one.
   */
  static final GrammarConstruct INDEX_IN_VALUE = new GrammarConstruct("Index",
      signature(parameter("sequence", T_SEQUENCE), parameter("position", NUMBER)).returning(T));

  /**
   * A field or a node read in the context its left side names. The right side is the reference
   * itself, not an argument, so the left side keeps the word of the left operand of an operator.
   */
  static final GrammarConstruct CONTEXT_OVERRIDE = new GrammarConstruct(
      Construct.operatorName("::"), signature(parameter("left operand", PATH)).returning(PATH));

  private final String name;
  private final List<Signature> signatures;

  private GrammarConstruct(final String name, final Signature... signatures) {
    this.name = name;
    this.signatures = List.of(signatures);
  }

  /**
   * Gives a construct that takes one value and gives it on, converted to its parameter: a clause,
   * such as {@code WHEN}, which gives its rule its condition, or the value of a declaration, which
   * becomes the value of what it declares.
   *
   * @param name the construct as a type error names it, such as "'WHEN'" or
   *        "Variable 'text:$x'"
   * @param parameter its parameter, such as {@code condition: INDICATOR}
   * @return the construct, whose result is the type of its parameter
   */
  static GrammarConstruct of(final String name, final BuiltInParameter<?> parameter) {
    return new GrammarConstruct(name, signature(parameter).returning(parameter.getType()));
  }

  /**
   * @param name the construct as a type error names it, such as "Dictionary '$d'"
   * @param signatures its signatures, none of which takes the same arguments as another
   * @return the construct
   */
  static GrammarConstruct of(final String name, final Signature... signatures) {
    return new GrammarConstruct(name, signatures);
  }

  /**
   * Gives a construct that takes one value of any type and gives it as it is: a single value stays
   * one, and a sequence stays a sequence. It has a signature for each, {@code (T) -> T} and
   * {@code (T*) -> T*}, and a single value takes the first, the more specific.
   *
   * @param name the construct as a type error names it, such as "Parenthesised expression"
   * @param parameter the name of its parameter, such as "content"
   * @return the construct
   */
  static GrammarConstruct ofValueAsGiven(final String name, final String parameter) {
    return new GrammarConstruct(name, signature(parameter(parameter, T)).returning(T),
        signature(parameter(parameter, T_SEQUENCE)).returning(T_SEQUENCE));
  }

  /**
   * Gives the condition of a quantifier, which gives whether some or every iteration satisfies it.
   *
   * @param token the quantifier as written, {@code every} or {@code some}
   * @return the quantifier, whose result is an indicator
   */
  static GrammarConstruct quantifier(final String token) {
    return new GrammarConstruct("Quantifier '" + token + "'",
        signature(parameter("condition", INDICATOR)).returning(INDICATOR));
  }

  /**
   * Gives a step that only a field has, which gives a reference. The step is a reading of the
   * field's origin, not a match: its signature is never matched, and its error, which says that
   * the operand must be a field reference, is the origin's, with the words of this construct.
   *
   * @param token the step as written after the field, such as {@code /@listName} or
   *        {@code :rawValue}
   * @return the step, named as an operator after its one operand, the field
   */
  static GrammarConstruct step(final String token) {
    return new GrammarConstruct(Construct.operatorName(token),
        signature(parameter("operand", PATH)).returning(PATH));
  }

  @Override
  public String describe() {
    return this.name;
  }

  /**
   * @return the name of the parameter at the position, numbered among the items of a list; the
   *         signatures of a construct name their parameters alike
   */
  @Override
  public String roleOf(final int position, final int count) {
    return this.signatures.get(0).getParameters().roleOf(position, count);
  }

  @Override
  public List<Parameters<?>> getParameterLists() {
    return this.signatures.stream().<Parameters<?>>map(Signature::getParameters)
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Gives the class of what a call of this construct gives: the result of the signature that takes
   * its arguments, with {@code T} bound by them.
   *
   * @param resolution the resolved call
   * @return the expression class of the result
   * @throws IllegalArgumentException if no signature of this construct has the parameter list that
   *         resolved the call
   */
  Class<? extends TypedExpression> resultOf(final Resolution resolution) {
    return this.signatures.stream()
        .filter(signature -> signature.getParameters().equals(resolution.getParameters()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            this.name + " has no signature that takes " + resolution.getParameters()))
        .resultFor(resolution.getArguments().getArgumentValues()).getExpressionType();
  }
}
