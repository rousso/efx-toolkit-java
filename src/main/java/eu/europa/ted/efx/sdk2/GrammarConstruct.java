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

import java.util.List;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.signatures.BuiltInParameter;
import eu.europa.ted.efx.model.signatures.Construct;
import eu.europa.ted.efx.model.signatures.Parameters;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * A construct that the grammar writes and the catalogue does not hold: a composite such as the
 * conditional, a predicate or an index; the clause of a rule or a template that takes an
 * expression, such as {@code WHEN}; the value of a declaration; or a step that only a field has.
 * It is checked against its parameter lists, and translated where it is written.
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
   * first parameter list, the more specific; a sequence on either side takes the second, and the
   * other branch is promoted to a sequence of one.
   */
  static final GrammarConstruct CONDITIONAL = new GrammarConstruct("Conditional 'if'", List.of(
      Parameters.fixed(parameter("condition", INDICATOR), parameter("then branch", T),
          parameter("else branch", T)),
      Parameters.fixed(parameter("condition", INDICATOR), parameter("then branch", T_SEQUENCE),
          parameter("else branch", T_SEQUENCE))));

  /** A list in brackets: single values of the primitive of the first. */
  static final GrammarConstruct LIST =
      new GrammarConstruct("List", List.of(Parameters.variadic(parameter("item", T))));

  /**
   * The body of an iteration, which gives values for each iteration: a single value is a sequence
   * of one, and the iteration joins the sequences.
   */
  static final GrammarConstruct ITERATION = GrammarConstruct.of("Iteration 'for'",
      parameter("body", T_SEQUENCE));

  /**
   * A predicate after a field or a node. A number for its condition is a position written as a
   * predicate, so its errors suggest the index.
   */
  static final GrammarConstruct PREDICATE = new GrammarConstruct("Predicate",
      List.of(Parameters.fixed(parameter("sequence", PATH), parameter("condition", INDICATOR)))) {

    @Override
    public List<String> suggestionsFor(final List<? extends TypedExpression> arguments) {
      return arguments.get(1).is(EfxDataType.Number.class)
          ? List.of("To select by position, write [at n].")
          : List.of();
    }
  };

  /** An index after a field or a node: the element at a position of the whole sequence. */
  static final GrammarConstruct INDEX_IN_PATH = GrammarConstruct.of("Index",
      parameter("sequence", PATH), parameter("position", NUMBER));

  /**
   * An index after a value: the value at a position of the sequence of its values, a single value
   * being a sequence of one.
   */
  static final GrammarConstruct INDEX_IN_VALUE = GrammarConstruct.of("Index",
      parameter("sequence", T_SEQUENCE), parameter("position", NUMBER));

  /**
   * A field or a node read in the context its left side names. The right side is the reference
   * itself, not an argument, so the left side keeps the word of the left operand of an operator.
   */
  static final GrammarConstruct CONTEXT_OVERRIDE =
      GrammarConstruct.of(Construct.operatorName("::"), parameter("left operand", PATH));

  private final String name;
  private final List<Parameters<BuiltInParameter>> parameterLists;

  private GrammarConstruct(final String name,
      final List<Parameters<BuiltInParameter>> parameterLists) {
    this.name = name;
    this.parameterLists = parameterLists;
  }

  /**
   * @param name the construct as a type error names it, such as "'WHEN'" or
   *        "Variable 'text:$x'"
   * @param parameters its parameters, in order, such as {@code condition: INDICATOR}
   * @return the construct
   */
  static GrammarConstruct of(final String name, final BuiltInParameter... parameters) {
    return new GrammarConstruct(name, List.of(Parameters.fixed(parameters)));
  }

  /**
   * Gives a construct that takes one value of any type as it is: a single value stays one, and a
   * sequence stays a sequence. It has a parameter list for each, {@code (T)} and {@code (T*)}, and
   * a single value takes the first, the more specific.
   *
   * @param name the construct as a type error names it, such as "Parenthesised expression"
   * @param parameter the name of its parameter, such as "content"
   * @return the construct
   */
  static GrammarConstruct ofValueAsGiven(final String name, final String parameter) {
    return new GrammarConstruct(name, List.of(Parameters.fixed(parameter(parameter, T)),
        Parameters.fixed(parameter(parameter, T_SEQUENCE))));
  }

  /**
   * Gives a step that only a field has. The step is a reading of the field's origin, not a match:
   * its parameter list is never matched, and its error, which says that the operand must be a
   * field reference, is the origin's, with the words of this construct.
   *
   * @param token the step as written after the field, such as {@code /@listName} or
   *        {@code :rawValue}
   * @return the step, named as an operator after its one operand, the field
   */
  static GrammarConstruct step(final String token) {
    return of(Construct.operatorName(token), parameter("operand", PATH));
  }

  @Override
  public String describe() {
    return this.name;
  }

  /**
   * @return the name of the parameter at the position, numbered among the items of a list; the
   *         parameter lists of a construct name their parameters alike
   */
  @Override
  public String roleOf(final int position, final int count) {
    return this.parameterLists.get(0).roleOf(position, count);
  }

  @Override
  public List<Parameters<?>> getParameterLists() {
    return List.copyOf(this.parameterLists);
  }
}
