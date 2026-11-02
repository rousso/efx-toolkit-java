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

import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * The signature of an {@link Overload}: its {@link Parameters}, which may end in a variadic tail,
 * and its result type.
 *
 * <p>A variadic tail takes one or more arguments after the fixed parameters. An optional argument
 * is not part of a signature: the built-in declares a second overload instead. A signature whose
 * parameters mention {@code T} is generic, and is bound to the value primitive of the argument at
 * the first position of {@code T} before its arguments are checked.</p>
 */
public final class Signature {

  private final Parameters<BuiltInParameter> parameters;
  private final ParameterType result;

  private Signature(final Parameters<BuiltInParameter> parameters, final ParameterType result) {
    this.parameters = parameters;
    this.result = result;
  }

  /**
   * Starts a signature with fixed parameters. Complete it with {@link Builder#returning}.
   *
   * @param parameters the fixed parameters, in order
   * @return a builder for the signature
   */
  public static Builder signature(final BuiltInParameter... parameters) {
    return new Builder(List.of(parameters), null);
  }

  /**
   * @return the parameters, which arguments are matched against
   */
  public Parameters<BuiltInParameter> getParameters() {
    return this.parameters;
  }

  /**
   * @return the result type
   */
  public ParameterType getResult() {
    return this.result;
  }

  /**
   * Gives this signature with {@code T} bound.
   *
   * @param primitive the value primitive {@code T} is bound to
   * @return the signature with every parameter type and the result type bound
   */
  Signature bind(final Class<? extends EfxDataType.Primitive> primitive) {
    return new Signature(this.parameters.bind(primitive), this.result.bind(primitive));
  }

  /**
   * Gives the signatures this one stands for: one for each value primitive {@code T} can be bound
   * to, when it mentions {@code T}, and itself otherwise.
   *
   * @return the signatures with {@code T} bound, or this signature alone
   */
  public List<Signature> concreteForms() {
    if (!this.parameters.isGeneric()) {
      return List.of(this);
    }
    return TypeVariableParameterType.valuePrimitives().stream().map(this::bind)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public String toString() {
    return this.parameters + " -> " + this.result;
  }

  /**
   * Builds a {@link Signature} from its fixed parameters, an optional variadic tail and its result
   * type.
   */
  public static final class Builder {

    private final List<BuiltInParameter> parameters;
    private final BuiltInParameter variadic;

    private Builder(final List<BuiltInParameter> parameters, final BuiltInParameter variadic) {
      this.parameters = parameters;
      this.variadic = variadic;
    }

    /**
     * Adds a variadic tail, which takes one or more arguments after the fixed parameters.
     *
     * @param tail the parameter every argument of the tail matches
     * @return a builder for the signature with that tail
     */
    public Builder variadic(final BuiltInParameter tail) {
      return new Builder(this.parameters, tail);
    }

    /**
     * Completes the signature.
     *
     * @param result the result type
     * @return the signature
     */
    public Signature returning(final ParameterType result) {
      return new Signature(new Parameters<>(this.parameters, this.variadic), result);
    }
  }
}
