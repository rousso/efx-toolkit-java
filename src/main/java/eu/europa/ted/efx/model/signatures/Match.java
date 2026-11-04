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
 * The outcome of matching the arguments of a call against {@link Parameters} that
 * {@link Parameters#takes take} their number: the arguments converted to their parameters, or the
 * argument {@link Parameters#match} reports.
 *
 * <p>Both outcomes know the parameter list the arguments were matched against. Each is otherwise a
 * class of its own, made by its own factory, that holds only its own facts: a match its arguments,
 * a rejection the position, the required type and the argument. Each answers every question, and
 * one about a fact of the other outcome with an {@link IllegalStateException}.</p>
 */
public abstract class Match {

  private final Parameters<?> parameters;

  private Match(final Parameters<?> parameters) {
    this.parameters = parameters;
  }

  static Match of(final Parameters<?> parameters, final ParsedArguments<?> arguments) {
    return new Matched(parameters, arguments);
  }

  static Match rejected(final Parameters<?> parameters, final int position,
      final ParameterType required, final TypedExpression argument) {
    return new Rejected(parameters, position, required, argument);
  }

  /**
   * @return the parameter list the arguments were matched against, as the construct declares it
   */
  public Parameters<?> getParameters() {
    return this.parameters;
  }

  /**
   * @return {@code true} if the arguments match the parameters
   */
  public abstract boolean isMatched();

  /**
   * @return the arguments, each converted to the expression class of its parameter
   * @throws IllegalStateException if an argument is rejected
   */
  public abstract ParsedArguments<?> getArguments();

  /**
   * @return the position of the rejected argument
   * @throws IllegalStateException if the arguments match
   */
  public abstract int getPosition();

  /**
   * @return the parameter type the rejected argument had to convert to, with {@code T} bound by
   *         the arguments if they bind it
   * @throws IllegalStateException if the arguments match
   */
  public abstract ParameterType getRequired();

  /**
   * @return what is wrong with the rejected argument
   * @throws IllegalStateException if the arguments match
   */
  public abstract Kind getKind();

  /**
   * What is wrong with a rejected argument, in the order in which a mismatch reports them at the
   * same position.
   */
  public enum Kind {

    /** A sequence where a single value of its primitive is required. */
    CARDINALITY,

    /** A value of another primitive than the one required. */
    PRIMITIVE,

    /** A value where a path is required, or a node, which has no value, where a value is. */
    PATH
  }

  private static final class Matched extends Match {

    private final ParsedArguments<?> arguments;

    private Matched(final Parameters<?> parameters, final ParsedArguments<?> arguments) {
      super(parameters);
      this.arguments = arguments;
    }

    @Override
    public boolean isMatched() {
      return true;
    }

    @Override
    public ParsedArguments<?> getArguments() {
      return this.arguments;
    }

    @Override
    public int getPosition() {
      throw noRejection();
    }

    @Override
    public ParameterType getRequired() {
      throw noRejection();
    }

    @Override
    public Kind getKind() {
      throw noRejection();
    }

    private static IllegalStateException noRejection() {
      return new IllegalStateException("No argument was rejected");
    }
  }

  private static final class Rejected extends Match {

    private final int position;
    private final ParameterType required;
    private final TypedExpression argument;

    private Rejected(final Parameters<?> parameters, final int position,
        final ParameterType required, final TypedExpression argument) {
      super(parameters);
      this.position = position;
      this.required = required;
      this.argument = argument;
    }

    @Override
    public boolean isMatched() {
      return false;
    }

    @Override
    public ParsedArguments<?> getArguments() {
      throw new IllegalStateException("The arguments do not match the parameters");
    }

    @Override
    public int getPosition() {
      return this.position;
    }

    @Override
    public ParameterType getRequired() {
      return this.required;
    }

    @Override
    public Kind getKind() {
      return this.required.rejectionKindOf(this.argument);
    }
  }
}
