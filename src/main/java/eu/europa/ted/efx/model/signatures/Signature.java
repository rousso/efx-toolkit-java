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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * The signature of an {@link Overload}: its parameters, an optional variadic tail, and its result
 * type.
 *
 * <p>A variadic tail takes one or more arguments after the fixed parameters. An optional argument
 * is not part of a signature: the built-in declares a second overload instead. A signature whose
 * parameters mention {@code T} is generic, and is bound to the value primitive of the argument at
 * the first position of {@code T} before its arguments are checked.</p>
 */
public final class Signature {

  private final List<Parameter> parameters;
  private final Parameter variadic;
  private final ParameterType result;

  private Signature(final List<Parameter> parameters, final Parameter variadic,
      final ParameterType result) {
    this.parameters = parameters;
    this.variadic = variadic;
    this.result = result;
  }

  /**
   * Starts a signature with fixed parameters. Complete it with {@link Builder#returning}.
   *
   * @param parameters the fixed parameters, in order
   * @return a builder for the signature
   */
  public static Builder signature(final Parameter... parameters) {
    return new Builder(List.of(parameters), null);
  }

  /**
   * @return the fixed parameters, in order
   */
  public List<Parameter> getParameters() {
    return this.parameters;
  }

  /**
   * @return the parameter of the variadic tail, if the signature has one
   */
  public Optional<Parameter> getVariadic() {
    return Optional.ofNullable(this.variadic);
  }

  /**
   * @return the result type
   */
  public ParameterType getResult() {
    return this.result;
  }

  /**
   * Tells whether the signature takes a number of arguments.
   *
   * @param count the number of arguments
   * @return {@code true} if the count is that of the fixed parameters, or, with a variadic tail,
   *         greater than it
   */
  public boolean takes(final int count) {
    return this.variadic == null ? count == this.parameters.size() : count > this.parameters.size();
  }

  /**
   * Gives the parameter at a position, which is the variadic tail past the fixed parameters.
   *
   * @param position the position of an argument the signature {@link #takes}
   * @return the parameter at that position
   */
  public Parameter parameterAt(final int position) {
    return position < this.parameters.size() ? this.parameters.get(position) : this.variadic;
  }

  /**
   * Gives this signature with {@code T} bound.
   *
   * @param primitive the value primitive {@code T} is bound to
   * @return the signature with every parameter type and the result type bound
   */
  Signature bind(final Class<? extends EfxDataType.Primitive> primitive) {
    return new Signature(
        this.parameters.stream().map(parameter -> parameter.bind(primitive)).collect(Collectors.toList()),
        this.variadic == null ? null : this.variadic.bind(primitive),
        this.result.bind(primitive));
  }

  /**
   * Tells whether a parameter of this signature is {@code T}.
   *
   * @return {@code true} if a fixed parameter or the variadic tail is a type variable
   */
  boolean isGeneric() {
    return this.allParameters().anyMatch(parameter -> parameter.getType().isTypeVariable());
  }

  /**
   * Finds the first argument this signature does not accept. For a generic signature that is the
   * argument at the first position of {@code T} when it cannot bind {@code T}, and otherwise the
   * first argument the bound signature does not accept.
   *
   * @param arguments arguments whose number the signature {@link #takes}
   * @return the position of the first argument not accepted, or nothing if all are
   */
  OptionalInt firstRejectedPosition(final List<? extends TypedExpression> arguments) {
    Optional<Signature> bound = this.boundTo(arguments);
    if (bound.isEmpty()) {
      return OptionalInt.of(this.typeVariablePosition());
    }
    return IntStream.range(0, arguments.size())
        .filter(position -> !bound.get().parameterAt(position).getType().accepts(arguments.get(position)))
        .findFirst();
  }

  /**
   * Converts arguments this signature accepts to the expression classes of its bound parameters.
   *
   * @param arguments arguments with no {@link #firstRejectedPosition rejected position}
   * @return the converted arguments
   */
  Arguments convert(final List<? extends TypedExpression> arguments) {
    Signature bound = this.boundTo(arguments).orElseThrow();
    List<TypedExpression> values = new ArrayList<>();
    List<Class<? extends TypedExpression>> types = new ArrayList<>();
    for (int position = 0; position < arguments.size(); position++) {
      ParameterType type = bound.parameterAt(position).getType();
      values.add(type.convert(arguments.get(position)));
      types.add(type.getExpressionType());
    }
    return new Arguments(values, types);
  }

  private Optional<Signature> boundTo(final List<? extends TypedExpression> arguments) {
    if (!this.isGeneric()) {
      return Optional.of(this);
    }
    return TypeVariableParameterType.bindingOf(arguments.get(this.typeVariablePosition()))
        .map(this::bind);
  }

  private int typeVariablePosition() {
    return IntStream.range(0, this.parameters.size())
        .filter(position -> this.parameters.get(position).getType().isTypeVariable())
        .findFirst()
        .orElse(this.parameters.size());
  }

  private Stream<Parameter> allParameters() {
    return Stream.concat(this.parameters.stream(), this.getVariadic().stream());
  }

  @Override
  public String toString() {
    return "(" + this.allParameters().map(Parameter::toString).collect(Collectors.joining(", "))
        + (this.variadic == null ? "" : "...") + ") -> " + this.result;
  }

  /**
   * Builds a {@link Signature} from its fixed parameters, an optional variadic tail and its result
   * type.
   */
  public static final class Builder {

    private final List<Parameter> parameters;
    private final Parameter variadic;

    private Builder(final List<Parameter> parameters, final Parameter variadic) {
      this.parameters = parameters;
      this.variadic = variadic;
    }

    /**
     * Adds a variadic tail, which takes one or more arguments after the fixed parameters.
     *
     * @param tail the parameter every argument of the tail matches
     * @return a builder for the signature with that tail
     */
    public Builder variadic(final Parameter tail) {
      return new Builder(this.parameters, tail);
    }

    /**
     * Completes the signature.
     *
     * @param result the result type
     * @return the signature
     */
    public Signature returning(final ParameterType result) {
      return new Signature(this.parameters, this.variadic, result);
    }
  }
}
