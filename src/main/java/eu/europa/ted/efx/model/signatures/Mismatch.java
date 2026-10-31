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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * Why no overload of a {@link BuiltIn} applies to the arguments of a call: either no overload
 * takes that many arguments, or each overload that does rejects one of them.
 *
 * <p>A mismatch keeps the arguments and, for each overload of the right arity, the first position
 * it rejected. Its {@link #getDiagnosis diagnosis} is what the translator needs to raise the error
 * a call deserves.</p>
 */
public final class Mismatch {

  private final BuiltIn builtIn;
  private final List<TypedExpression> arguments;
  private final List<Rejection> rejections;

  Mismatch(final BuiltIn builtIn, final List<? extends TypedExpression> arguments,
      final List<Rejection> rejections) {
    this.builtIn = builtIn;
    this.arguments = List.copyOf(arguments);
    this.rejections = List.copyOf(rejections);
  }

  /**
   * @return the built-in that was called
   */
  public BuiltIn getBuiltIn() {
    return this.builtIn;
  }

  /**
   * @return the arguments of the call
   */
  public List<TypedExpression> getArguments() {
    return this.arguments;
  }

  /**
   * Tells whether the mismatch is the number of arguments.
   *
   * @return {@code true} if no overload takes that many arguments
   */
  public boolean isArityMismatch() {
    return this.rejections.isEmpty();
  }

  /**
   * @return for each overload that takes that many arguments, the first argument it rejected
   */
  public List<Rejection> getRejections() {
    return this.rejections;
  }

  /**
   * Gives the rejection that explains the mismatch, whatever the order of the overloads: the one
   * at the furthest position, and among those a cardinality failure before a primitive one, and a
   * primitive failure before a path one.
   *
   * @return the rejection to report
   * @throws IllegalStateException if the mismatch is the number of arguments
   */
  public Rejection getDiagnosis() {
    return this.rejections.stream()
        .min(Comparator.comparingInt(Rejection::getPosition).reversed()
            .thenComparing(Rejection::getKind))
        .orElseThrow(() -> new IllegalStateException(this.toString()));
  }

  @Override
  public String toString() {
    return "no overload of '" + this.builtIn.getName() + "' takes (" + describe(this.arguments) + ")";
  }

  /**
   * Names the classes of the arguments of a call, as the messages about the call show them.
   *
   * @param arguments the translated arguments of the call
   * @return the simple class names of the arguments, separated by commas
   */
  static String describe(final List<? extends TypedExpression> arguments) {
    return arguments.stream().map(argument -> argument.getClass().getSimpleName())
        .collect(Collectors.joining(", "));
  }

  /**
   * The first argument an overload rejected.
   */
  public static final class Rejection {

    private final Overload overload;
    private final Match match;

    Rejection(final Overload overload, final Match match) {
      this.overload = overload;
      this.match = match;
    }

    /**
     * @return the overload that rejected the argument
     */
    public Overload getOverload() {
      return this.overload;
    }

    /**
     * @return the position of the rejected argument
     */
    public int getPosition() {
      return this.match.getPosition();
    }

    /**
     * @return the parameter type the overload declares at that position, before {@code T} is
     *         bound
     */
    public ParameterType getExpected() {
      return this.overload.getSignature().parameterAt(this.getPosition()).getType();
    }

    /**
     * @return the parameter type the argument had to convert to: the one declared at that
     *         position, with {@code T} bound by the arguments of the call if they bind it
     */
    public ParameterType getRequired() {
      return this.match.getRequired();
    }

    /**
     * @return the rejected argument
     */
    public TypedExpression getArgument() {
      return this.match.getArgument();
    }

    /**
     * @return what is wrong with the argument
     */
    public Kind getKind() {
      ParameterType required = this.getRequired();
      TypedExpression argument = this.getArgument();
      if (PathExpression.class.isAssignableFrom(required.getExpressionType())
          || ParameterType.T.boundBy(argument).isEmpty()) {
        return Kind.PATH;
      }
      return required.acceptsValuesOf(argument) ? Kind.CARDINALITY : Kind.PRIMITIVE;
    }

    /**
     * What is wrong with a rejected argument, in the order in which a mismatch reports them.
     */
    public enum Kind {

      /** A sequence where a single value of its primitive is required. */
      CARDINALITY,

      /** A value of another primitive than the one required. */
      PRIMITIVE,

      /** A value where a path is required, or a node, which has no value, where a value is. */
      PATH
    }
  }
}
