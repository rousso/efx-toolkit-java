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

import static eu.europa.ted.efx.model.signatures.BuiltInParameter.parameter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.signatures.Mismatch.Rejection;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * A parameter list: fixed parameters, in order, and an optional variadic tail. Arguments are
 * matched against it: those of a built-in against the list of a {@link Signature}, those of a
 * function or template declared in EFX against the list of its declaration.
 *
 * <p>A variadic tail takes one or more arguments after the fixed parameters. Parameters that
 * mention {@code T} are generic: they are bound to the value primitive of the argument at the
 * first position of {@code T} before the arguments are checked against them.</p>
 *
 * @param <P> the kind of parameter: declared in the catalogue, or parsed with a declaration
 */
public class Parameters<P extends Parameter> extends LinkedList<P> {

  /**
   * The order in which the rejected arguments of a call are reported: any argument of the wrong
   * primitive, or not the path required, before a sequence where a single value is required, and
   * then by position.
   */
  private static final Comparator<Match> REPORTING_ORDER =
      Comparator.comparing((Match rejection) -> rejection.getKind() == Rejection.Kind.CARDINALITY)
          .thenComparingInt(Match::getPosition);

  private final P variadic;

  /**
   * Creates an empty list with no variadic tail, to which a declaration adds its parameters as
   * they are translated.
   */
  protected Parameters() {
    this.variadic = null;
  }

  Parameters(final List<P> fixed, final P variadic) {
    super(fixed);
    this.variadic = variadic;
  }

  /**
   * @return the parameter of the variadic tail, if there is one
   */
  public Optional<P> getVariadic() {
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
    return this.variadic == null ? count == this.size() : count > this.size();
  }

  /**
   * Gives the parameter at a position, which is the variadic tail past the fixed parameters.
   *
   * @param position the position of an argument the parameter list {@link #takes}
   * @return the parameter at that position
   */
  public P parameterAt(final int position) {
    return position < this.size() ? this.get(position) : this.variadic;
  }

  /**
   * Matches the arguments of a call: checks their number, binds {@code T} by the argument at its
   * first position, checks each argument against its parameter, and converts the arguments to the
   * expression classes of their parameters.
   *
   * <p>Every argument is checked before one is reported, and a wrong primitive or path is reported
   * before a wrong cardinality: the first argument of another primitive than its parameter's, or
   * that is not the path its parameter requires, and only if there is none, the first sequence
   * where a single value is required. A call wrong in both ways is thus reported by its primitive,
   * whichever of its arguments is the sequence.</p>
   *
   * @param arguments the translated arguments of the call
   * @return the converted arguments, or the reason they do not match: their number, a {@code T}
   *         that the argument at its first position cannot bind, or the argument to report
   */
  public Match match(final List<? extends TypedExpression> arguments) {
    if (!this.takes(arguments.size())) {
      return Match.countMismatch();
    }
    Optional<Parameters<?>> bound = this.boundTo(arguments);
    if (bound.isEmpty()) {
      int position = this.typeVariablePosition();
      return Match.rejected(position, this.parameterAt(position).getType(), arguments.get(position));
    }
    return bound.get().matchBound(arguments);
  }

  /**
   * Gives these parameters with {@code T} bound. The bound list holds catalogue parameters; it is
   * only reached for a list that mentions {@code T}, which the list of a declaration never does.
   *
   * @param primitive the value primitive {@code T} is bound to
   * @return the parameter list with every parameter type bound
   */
  Parameters<BuiltInParameter> bind(final Class<? extends EfxDataType.Primitive> primitive) {
    return new Parameters<>(
        this.stream().map(parameter -> bound(parameter, primitive)).collect(Collectors.toList()),
        this.variadic == null ? null : bound(this.variadic, primitive));
  }

  /**
   * Tells whether a parameter of this list is {@code T}.
   *
   * @return {@code true} if a fixed parameter or the variadic tail is a type variable
   */
  boolean isGeneric() {
    return this.all().anyMatch(parameter -> parameter.getType().isTypeVariable());
  }

  private Match matchBound(final List<? extends TypedExpression> arguments) {
    return IntStream.range(0, arguments.size())
        .filter(position -> !this.typeAt(position).accepts(arguments.get(position)))
        .mapToObj(
            position -> Match.rejected(position, this.typeAt(position), arguments.get(position)))
        .min(REPORTING_ORDER)
        .orElseGet(() -> this.converted(arguments));
  }

  private Match converted(final List<? extends TypedExpression> arguments) {
    List<TypedExpression> values = new ArrayList<>();
    List<Class<? extends TypedExpression>> types = new ArrayList<>();
    for (int position = 0; position < arguments.size(); position++) {
      ParameterType type = this.typeAt(position);
      values.add(type.convert(arguments.get(position)));
      types.add(type.getExpressionType());
    }
    return Match.of(new Arguments(values, types));
  }

  private ParameterType typeAt(final int position) {
    return this.parameterAt(position).getType();
  }

  private Optional<Parameters<?>> boundTo(final List<? extends TypedExpression> arguments) {
    if (!this.isGeneric()) {
      return Optional.of(this);
    }
    return TypeVariableParameterType.bindingOf(arguments.get(this.typeVariablePosition()))
        .map(this::bind);
  }

  private int typeVariablePosition() {
    return IntStream.range(0, this.size())
        .filter(position -> this.get(position).getType().isTypeVariable())
        .findFirst()
        .orElse(this.size());
  }

  private Stream<P> all() {
    return Stream.concat(this.stream(), this.getVariadic().stream());
  }

  private static BuiltInParameter bound(final Parameter parameter,
      final Class<? extends EfxDataType.Primitive> primitive) {
    return parameter(parameter.getName(), parameter.getType().bind(primitive));
  }

  /**
   * Two parameter lists are equal when their fixed parameters and their variadic tails are.
   */
  @Override
  public boolean equals(final Object other) {
    return other instanceof Parameters && super.equals(other)
        && Objects.equals(this.variadic, ((Parameters<?>) other).variadic);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hashCode(this.variadic);
  }

  @Override
  public String toString() {
    return "(" + this.all().map(Parameter::toString).collect(Collectors.joining(", "))
        + (this.variadic == null ? "" : "...") + ")";
  }
}
