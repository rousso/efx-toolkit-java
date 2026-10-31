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

import static eu.europa.ted.efx.model.signatures.Parameter.parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.types.EfxDataType;
import eu.europa.ted.efx.model.variables.ParsedParameters;

/**
 * A parameter list: fixed parameters, in order, and an optional variadic tail. It is the part of a
 * {@link Signature} that arguments are matched against, and the whole of what a function or
 * template declared in an EFX file declares about its arguments.
 *
 * <p>A variadic tail takes one or more arguments after the fixed parameters. Parameters that
 * mention {@code T} are generic: they are bound to the value primitive of the argument at the
 * first position of {@code T} before the arguments are checked against them.</p>
 */
public final class Parameters {

  private final List<Parameter> fixed;
  private final Parameter variadic;
  private final boolean generic;
  private final int typeVariablePosition;

  Parameters(final List<Parameter> fixed, final Parameter variadic) {
    this.fixed = List.copyOf(fixed);
    this.variadic = variadic;
    this.generic = this.all().anyMatch(parameter -> parameter.getType().isTypeVariable());
    this.typeVariablePosition = IntStream.range(0, this.fixed.size())
        .filter(position -> this.fixed.get(position).getType().isTypeVariable())
        .findFirst()
        .orElse(this.fixed.size());
  }

  /**
   * Gives the parameters a function or template declares.
   *
   * @param declared the parameters of the declaration, as they were translated
   * @return the parameter list, with no variadic tail
   */
  public static Parameters of(final ParsedParameters declared) {
    return new Parameters(declared.stream()
        .map(parameter -> parameter(parameter.name, ParameterType.of(parameter.getParameterType())))
        .collect(Collectors.toList()), null);
  }

  /**
   * @return the fixed parameters, in order
   */
  public List<Parameter> getFixed() {
    return this.fixed;
  }

  /**
   * @return the parameter of the variadic tail, if there is one
   */
  public Optional<Parameter> getVariadic() {
    return Optional.ofNullable(this.variadic);
  }

  /**
   * Tells whether the parameter list takes a number of arguments.
   *
   * @param count the number of arguments
   * @return {@code true} if the count is that of the fixed parameters, or, with a variadic tail,
   *         greater than it
   */
  public boolean takes(final int count) {
    return this.variadic == null ? count == this.fixed.size() : count > this.fixed.size();
  }

  /**
   * Gives the parameter at a position, which is the variadic tail past the fixed parameters.
   *
   * @param position the position of an argument the parameter list {@link #takes}
   * @return the parameter at that position
   */
  public Parameter parameterAt(final int position) {
    return position < this.fixed.size() ? this.fixed.get(position) : this.variadic;
  }

  /**
   * Matches the arguments of a call: checks their number, binds {@code T} by the argument at its
   * first position, checks each argument against its parameter, and converts the arguments to the
   * expression classes of their parameters.
   *
   * @param arguments the translated arguments of the call
   * @return the converted arguments, or the first reason they do not match
   */
  public Match match(final List<? extends TypedExpression> arguments) {
    if (!this.takes(arguments.size())) {
      return Match.countMismatch();
    }
    Optional<Parameters> bound = this.boundTo(arguments);
    if (bound.isEmpty()) {
      int position = this.typeVariablePosition;
      return Match.rejected(position, this.parameterAt(position).getType(), arguments.get(position));
    }
    return bound.get().matchBound(arguments);
  }

  /**
   * Gives these parameters with {@code T} bound.
   *
   * @param primitive the value primitive {@code T} is bound to
   * @return the parameter list with every parameter type bound
   */
  Parameters bind(final Class<? extends EfxDataType.Primitive> primitive) {
    return new Parameters(
        this.fixed.stream().map(parameter -> parameter.bind(primitive)).collect(Collectors.toList()),
        this.variadic == null ? null : this.variadic.bind(primitive));
  }

  /**
   * Tells whether a parameter of this list is {@code T}.
   *
   * @return {@code true} if a fixed parameter or the variadic tail is a type variable
   */
  boolean isGeneric() {
    return this.generic;
  }

  private Match matchBound(final List<? extends TypedExpression> arguments) {
    List<TypedExpression> values = new ArrayList<>();
    List<Class<? extends TypedExpression>> types = new ArrayList<>();
    for (int position = 0; position < arguments.size(); position++) {
      ParameterType type = this.parameterAt(position).getType();
      TypedExpression argument = arguments.get(position);
      if (!type.accepts(argument)) {
        return Match.rejected(position, type, argument);
      }
      values.add(type.convert(argument));
      types.add(type.getExpressionType());
    }
    return Match.of(new Arguments(values, types));
  }

  private Optional<Parameters> boundTo(final List<? extends TypedExpression> arguments) {
    if (!this.generic) {
      return Optional.of(this);
    }
    return TypeVariableParameterType.bindingOf(arguments.get(this.typeVariablePosition))
        .map(this::bind);
  }

  private Stream<Parameter> all() {
    return Stream.concat(this.fixed.stream(), this.getVariadic().stream());
  }

  @Override
  public String toString() {
    return "(" + this.all().map(Parameter::toString).collect(Collectors.joining(", "))
        + (this.variadic == null ? "" : "...") + ")";
  }
}
