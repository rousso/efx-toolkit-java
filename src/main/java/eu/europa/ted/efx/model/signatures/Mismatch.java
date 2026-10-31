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
import java.util.stream.Collectors;

import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * Why no overload of a {@link BuiltIn} applies to the arguments of a call: either no overload
 * takes that many arguments, or each overload that does rejects one of them.
 *
 * <p>A mismatch keeps the arguments and, for each overload of the right arity, the first position
 * it rejected. That is what the translator needs to raise the error a call deserves.</p>
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

  @Override
  public String toString() {
    return "no overload of '" + this.builtIn.getName() + "' takes ("
        + this.arguments.stream().map(argument -> argument.getClass().getSimpleName())
            .collect(Collectors.joining(", "))
        + ")";
  }

  /**
   * The first argument an overload rejected.
   */
  public static final class Rejection {

    private final Overload overload;
    private final int position;

    Rejection(final Overload overload, final int position) {
      this.overload = overload;
      this.position = position;
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
      return this.position;
    }

    /**
     * @return the parameter type the overload declares at that position, before {@code T} is
     *         bound
     */
    public ParameterType getExpected() {
      return this.overload.getSignature().parameterAt(this.position).getType();
    }
  }
}
