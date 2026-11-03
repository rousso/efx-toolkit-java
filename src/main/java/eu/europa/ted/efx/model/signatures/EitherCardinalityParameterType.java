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
import eu.europa.ted.efx.model.expressions.scalar.ScalarExpression;
import eu.europa.ted.efx.model.expressions.sequence.SequenceExpression;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * The type {@code T} in either cardinality stands for once it is bound: a single value or a
 * sequence of one value primitive, each taken as it is. A single value is converted to the single
 * values of the primitive and a sequence to its sequences; neither is promoted.
 */
final class EitherCardinalityParameterType implements ParameterType {

  private final ParameterType single;
  private final ParameterType sequence;

  EitherCardinalityParameterType(final Class<? extends EfxDataType.Primitive> primitive) {
    this.single = new ConcreteParameterType(ScalarExpression.fromEfxDataType.get(primitive));
    this.sequence = new ConcreteParameterType(SequenceExpression.fromEfxDataType.get(primitive));
  }

  /**
   * @return the class of every value, since the class an argument is converted to depends on its
   *         cardinality
   */
  @Override
  public Class<? extends TypedExpression> getExpressionType() {
    return TypedExpression.class;
  }

  @Override
  public Class<? extends TypedExpression> expressionTypeOf(final TypedExpression argument) {
    return (this.single.accepts(argument) ? this.single : this.sequence).getExpressionType();
  }

  @Override
  public boolean accepts(final TypedExpression argument) {
    return this.single.accepts(argument) || this.sequence.accepts(argument);
  }

  /**
   * Either cardinality is taken, so an argument is only rejected for its primitive, or for having
   * no value.
   */
  @Override
  public Match.Kind rejectionKindOf(final TypedExpression argument) {
    return this.sequence.rejectionKindOf(argument);
  }

  /**
   * @return both cardinalities of the primitive, such as "'number' or 'number*'"
   */
  @Override
  public String describe() {
    return this.single.describe() + " or " + this.sequence.describe();
  }

  @Override
  public String toString() {
    return this.single + "|" + this.sequence;
  }
}
