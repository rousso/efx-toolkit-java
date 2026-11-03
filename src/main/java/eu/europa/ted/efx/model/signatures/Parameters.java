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
      Comparator.comparing((Match rejection) -> rejection.getKind() == Match.Kind.CARDINALITY)
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
   * Gives the parameter list of a construct that the catalogue does not hold, such as the
   * conditional, from fixed parameters.
   *
   * @param parameters the parameters, in order
   * @return the list of those parameters, with no variadic tail
   */
  public static Parameters<BuiltInParameter> fixed(final BuiltInParameter... parameters) {
    return new Parameters<>(List.of(parameters), null);
  }

  /**
   * Gives the parameter list of a construct that the catalogue does not hold and that takes one or
   * more arguments of the same parameter, such as a list.
   *
   * @param tail the parameter of every argument
   * @return the list with no fixed parameters and that variadic tail
   */
  public static Parameters<BuiltInParameter> variadic(final BuiltInParameter tail) {
    return new Parameters<>(List.of(), tail);
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
   * Says what a type error calls the argument at a position: the name of its parameter, numbered
   * by position among the arguments of a variadic tail that takes more than one.
   *
   * @param position the position of an argument the parameter list {@link #takes}
   * @param count the number of arguments of the call
   * @return the role, such as "condition", or "item" and "2nd item" for the tail of a list
   */
  public String roleOf(final int position, final int count) {
    final String name = this.parameterAt(position).getName();
    return position < this.size() ? name
        : numbered(name, position - this.size(), count - this.size());
  }

  /**
   * Names one of several things of a kind by its position, or by the noun alone if it is the only
   * one.
   *
   * @param noun the kind of thing, such as "argument"
   * @param position its position among them, from 0
   * @param count the number of them
   * @return "argument" if there is one, otherwise "1st argument", "2nd argument" and so on
   */
  static String numbered(final String noun, final int position, final int count) {
    return count == 1 ? noun : ordinal(position + 1) + " " + noun;
  }

  private static String ordinal(final int number) {
    final int lastTwoDigits = number % 100;
    if (lastTwoDigits >= 11 && lastTwoDigits <= 13) {
      return number + "th";
    }
    switch (number % 10) {
      case 1:
        return number + "st";
      case 2:
        return number + "nd";
      case 3:
        return number + "rd";
      default:
        return number + "th";
    }
  }

  /**
   * Matches the arguments of a call that the parameter list {@link #takes}: binds {@code T} by the
   * argument at its first position, checks each argument against its parameter, and converts the
   * arguments to the expression classes of their parameters.
   *
   * <p>Every argument is checked before one is reported, and a wrong primitive or path is reported
   * before a wrong cardinality: the first argument of another primitive than its parameter's, or
   * that is not the path its parameter requires, and only if there is none, the first sequence
   * where a single value is required. A call wrong in both ways is thus reported by its primitive,
   * whichever of its arguments is the sequence.</p>
   *
   * @param arguments the translated arguments of the call, as many as the list takes
   * @return the converted arguments, or the argument to report: the one at the first position of
   *         a {@code T} it cannot bind, or the one the reporting order puts first
   * @throws IllegalArgumentException if the parameter list does not take that many arguments
   */
  public Match match(final List<? extends TypedExpression> arguments) {
    if (!this.takes(arguments.size())) {
      throw new IllegalArgumentException(this + " does not take " + arguments.size()
          + " arguments");
    }
    Optional<Parameters<?>> bound = this.boundTo(arguments);
    if (bound.isEmpty()) {
      int position = this.typeVariablePosition();
      return Match.rejected(this, position, this.typeAt(position), arguments.get(position));
    }
    return bound.get().matchBound(this, arguments);
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

  /**
   * Matches the arguments against these parameters, with {@code T} bound.
   *
   * @param declared the parameter list as the construct declares it, which the match reports
   * @param arguments the translated arguments of the call
   * @return the converted arguments, or the argument to report
   */
  private Match matchBound(final Parameters<?> declared,
      final List<? extends TypedExpression> arguments) {
    return IntStream.range(0, arguments.size())
        .filter(position -> !this.typeAt(position).accepts(arguments.get(position)))
        .mapToObj(position -> Match.rejected(declared, position, this.typeAt(position),
            arguments.get(position)))
        .min(REPORTING_ORDER)
        .orElseGet(() -> this.converted(declared, arguments));
  }

  private Match converted(final Parameters<?> declared,
      final List<? extends TypedExpression> arguments) {
    List<P> parameters = new ArrayList<>();
    List<TypedExpression> values = new ArrayList<>();
    for (int position = 0; position < arguments.size(); position++) {
      parameters.add(this.parameterAt(position));
      values.add(this.typeAt(position).convert(arguments.get(position)));
    }
    return Match.of(declared, new ParsedArguments<>(parameters, values));
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
