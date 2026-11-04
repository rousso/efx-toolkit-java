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
package eu.europa.ted.efx.model.signatures;

import java.util.List;

import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * A construct as a type check sees it: the parameter lists its arguments are checked against, and
 * the words a type error uses for it and for each of its arguments.
 *
 * <p>Every construct is checked this way. A built-in of the catalogue also translates
 * itself; a function or a template declared in EFX has one parameter list, its declared
 * parameters; and the constructs the grammar writes, such as the conditional, a predicate or the
 * condition of a rule, are checked as constructs and translated where they are written.</p>
 */
public interface Construct {

  /**
   * @return what a type error calls the construct, such as "Operator '+'",
   *         "Function 'substring'", "Template 'greeting'" or "Predicate"
   */
  String describe();

  /**
   * @param position the position of an argument
   * @param count the number of arguments of the call
   * @return what a type error calls the argument at that position, such as "right operand",
   *         "2nd argument" or "condition"
   */
  String roleOf(int position, int count);

  /**
   * @return the parameter lists that the arguments of a call are checked against: one, or one for
   *         each overload, none of which takes the same arguments as another
   */
  List<Parameters<?>> getParameterLists();

  /**
   * Says what to write instead of the arguments of a call that does not resolve, where the mistake
   * suggests it.
   *
   * @param arguments the translated arguments of the call
   * @return each suggestion, a sentence; none by default
   */
  default List<String> suggestionsFor(final List<? extends TypedExpression> arguments) {
    return List.of();
  }

  /**
   * Checks the arguments of a call against the parameter lists of this construct.
   *
   * @param arguments the translated arguments of the call
   * @return the parameter list that takes them, with the converted arguments, or the mismatch
   */
  default Resolution resolve(final List<? extends TypedExpression> arguments) {
    return Resolution.of(this, arguments);
  }

  /**
   * Names a function as a type error does.
   *
   * @param token the function as the call writes it, such as {@code substring} or
   *        {@code ?greeting}
   * @return "Function 'substring'"
   */
  static String functionName(final String token) {
    return "Function '" + token + "'";
  }

  /**
   * Names an operator as a type error does.
   *
   * @param token the operator as written, such as {@code +} or {@code ::}
   * @return "Operator '+'"
   */
  static String operatorName(final String token) {
    return "Operator '" + token + "'";
  }

  /**
   * Names an argument of a function by its position.
   *
   * @param position the position of the argument
   * @param count the number of arguments of the call
   * @return "argument" if there is one, otherwise "1st argument", "2nd argument" and so on
   */
  static String argumentRole(final int position, final int count) {
    return Parameters.numbered("argument", position, count);
  }

  /**
   * Names an operand of an operator by its position.
   *
   * @param position the position of the operand
   * @param count the number of operands, one or two
   * @return "operand" if there is one, otherwise "left operand" or "right operand"
   */
  static String operandRole(final int position, final int count) {
    if (count == 1) {
      return "operand";
    }
    return position == 0 ? "left operand" : "right operand";
  }
}
