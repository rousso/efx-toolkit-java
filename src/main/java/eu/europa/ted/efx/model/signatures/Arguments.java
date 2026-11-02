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

import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.DurationExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.scalar.ScalarExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.TimeExpression;
import eu.europa.ted.efx.model.expressions.sequence.SequenceExpression;

/**
 * The arguments of a resolved call, each converted to the expression class of its parameter in
 * the chosen overload, with {@code T} bound. The typed accessors are what a {@link Composer} reads
 * them with.
 */
public final class Arguments {

  private final List<TypedExpression> values;
  private final List<Class<? extends TypedExpression>> types;

  Arguments(final List<TypedExpression> values, final List<Class<? extends TypedExpression>> types) {
    this.values = List.copyOf(values);
    this.types = List.copyOf(types);
  }

  /**
   * @return the arguments, in order, each converted to its parameter's class
   */
  public List<TypedExpression> getValues() {
    return this.values;
  }

  /**
   * Gives the argument at a position as an expression of its parameter's class.
   *
   * @param <T> the expression class of the parameter at that position, or a supertype of it
   * @param position the position of the argument
   * @param type the expression class of the parameter at that position, or a supertype of it
   * @return the argument
   */
  public <T extends TypedExpression> T at(final int position, final Class<T> type) {
    return type.cast(this.values.get(position));
  }

  /**
   * Gives the expression class an argument was converted to, which for {@code T} or {@code T*} is
   * the class of the value primitive {@code T} is bound to.
   *
   * @param position the position of the argument
   * @return the expression class of the parameter at that position
   */
  public Class<? extends TypedExpression> typeAt(final int position) {
    return this.types.get(position);
  }

  /**
   * Gives the sequence class an argument was converted to, for a parameter that is a sequence.
   *
   * @param position the position of the argument
   * @return the sequence class of the parameter at that position
   */
  public Class<? extends SequenceExpression> sequenceTypeAt(final int position) {
    return this.typeAt(position).asSubclass(SequenceExpression.class);
  }

  /**
   * @param position the position of a text argument
   * @return the argument
   */
  public StringExpression stringAt(final int position) {
    return this.at(position, StringExpression.class);
  }

  /**
   * @param position the position of a number argument
   * @return the argument
   */
  public NumericExpression numericAt(final int position) {
    return this.at(position, NumericExpression.class);
  }

  /**
   * @param position the position of an indicator argument
   * @return the argument
   */
  public BooleanExpression booleanAt(final int position) {
    return this.at(position, BooleanExpression.class);
  }

  /**
   * @param position the position of a date argument
   * @return the argument
   */
  public DateExpression dateAt(final int position) {
    return this.at(position, DateExpression.class);
  }

  /**
   * @param position the position of a time argument
   * @return the argument
   */
  public TimeExpression timeAt(final int position) {
    return this.at(position, TimeExpression.class);
  }

  /**
   * @param position the position of a duration argument
   * @return the argument
   */
  public DurationExpression durationAt(final int position) {
    return this.at(position, DurationExpression.class);
  }

  /**
   * @param position the position of a single-value argument
   * @return the argument
   */
  public ScalarExpression scalarAt(final int position) {
    return this.at(position, ScalarExpression.class);
  }

  /**
   * @param position the position of a sequence argument
   * @return the argument
   */
  public SequenceExpression sequenceAt(final int position) {
    return this.at(position, SequenceExpression.class);
  }

  /**
   * @param position the position of a path argument
   * @return the argument
   */
  public PathExpression pathAt(final int position) {
    return this.at(position, PathExpression.class);
  }

  /**
   * Gives the text arguments of a variadic tail.
   *
   * @param position the position of the first argument of the tail
   * @return the arguments from that position to the last
   */
  public List<StringExpression> stringsFrom(final int position) {
    return this.values.subList(position, this.values.size()).stream()
        .map(StringExpression.class::cast)
        .collect(Collectors.toList());
  }
}
