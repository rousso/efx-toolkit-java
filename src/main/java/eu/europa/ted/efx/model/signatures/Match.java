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

import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * The outcome of matching the arguments of a call against {@link Parameters}: the arguments
 * converted to their parameters, or the first reason they do not match, which is either their
 * number or the first argument rejected.
 */
public final class Match {

  private final Arguments arguments;
  private final int position;
  private final ParameterType required;
  private final TypedExpression argument;

  private Match(final Arguments arguments, final int position, final ParameterType required,
      final TypedExpression argument) {
    this.arguments = arguments;
    this.position = position;
    this.required = required;
    this.argument = argument;
  }

  static Match of(final Arguments arguments) {
    return new Match(arguments, -1, null, null);
  }

  static Match countMismatch() {
    return new Match(null, -1, null, null);
  }

  static Match rejected(final int position, final ParameterType required,
      final TypedExpression argument) {
    return new Match(null, position, required, argument);
  }

  /**
   * @return {@code true} if the arguments match the parameters
   */
  public boolean isMatched() {
    return this.arguments != null;
  }

  /**
   * @return {@code true} if the parameters do not take that many arguments
   */
  public boolean isCountMismatch() {
    return !this.isMatched() && this.required == null;
  }

  /**
   * @return the arguments, each converted to the expression class of its parameter
   * @throws IllegalStateException if the arguments do not match
   */
  public Arguments getArguments() {
    if (!this.isMatched()) {
      throw new IllegalStateException("The arguments do not match the parameters");
    }
    return this.arguments;
  }

  /**
   * @return the position of the first argument rejected
   */
  public int getPosition() {
    this.requireRejection();
    return this.position;
  }

  /**
   * @return the parameter type the rejected argument had to convert to, with {@code T} bound by
   *         the arguments if they bind it
   */
  public ParameterType getRequired() {
    this.requireRejection();
    return this.required;
  }

  /**
   * @return the rejected argument
   */
  public TypedExpression getArgument() {
    this.requireRejection();
    return this.argument;
  }

  private void requireRejection() {
    if (this.required == null) {
      throw new IllegalStateException("No argument was rejected");
    }
  }
}
