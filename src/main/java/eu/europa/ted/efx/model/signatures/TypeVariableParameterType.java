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
import java.util.Optional;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.ScalarExpression;
import eu.europa.ted.efx.model.expressions.sequence.SequenceExpression;
import eu.europa.ted.efx.model.signatures.Mismatch.Rejection;
import eu.europa.ted.efx.model.types.EfxDataType;
import eu.europa.ted.efx.model.types.EfxTypeLattice;

/**
 * The type variable {@code T} of a generic signature: as a single value, as a sequence, or in
 * either cardinality, taken as it is.
 *
 * <p>{@code T} ranges over the six value primitives. A multilingual text binds it to text and a
 * dynamic value to indicator, as the typed grammar reads them today; a node never binds it. Until
 * it is bound, a type variable accepts no argument, and its expression class is that of every
 * value, or every sequence, it stands for.</p>
 */
enum TypeVariableParameterType implements ParameterType {

  SCALAR("T", "a value", ScalarExpression.class) {
    @Override
    public ParameterType bind(final Class<? extends EfxDataType.Primitive> primitive) {
      return new ConcreteParameterType(ScalarExpression.fromEfxDataType.get(primitive));
    }
  },

  SEQUENCE("T*", "a sequence of values", SequenceExpression.class) {
    @Override
    public ParameterType bind(final Class<? extends EfxDataType.Primitive> primitive) {
      return new ConcreteParameterType(SequenceExpression.fromEfxDataType.get(primitive));
    }
  },

  /**
   * A single value or a sequence, as it is: what a construct takes whose own cardinality is that
   * of what it is given, such as parentheses.
   */
  EITHER("T|T*", "a value", TypedExpression.class) {
    @Override
    public ParameterType bind(final Class<? extends EfxDataType.Primitive> primitive) {
      return new EitherCardinalityParameterType(primitive);
    }
  };

  private static final List<Class<? extends EfxDataType.Primitive>> VALUE_PRIMITIVES = List.of(
      EfxDataType.String.class, EfxDataType.Boolean.class, EfxDataType.Number.class,
      EfxDataType.Date.class, EfxDataType.Time.class, EfxDataType.Duration.class);

  private final String symbol;
  private final String description;
  private final Class<? extends TypedExpression> expressionType;

  TypeVariableParameterType(final String symbol, final String description,
      final Class<? extends TypedExpression> expressionType) {
    this.symbol = symbol;
    this.description = description;
    this.expressionType = expressionType;
  }

  /**
   * Gives the concrete type this type variable stands for once {@code T} is bound.
   */
  @Override
  public abstract ParameterType bind(Class<? extends EfxDataType.Primitive> primitive);

  /**
   * Gives the value primitive an argument binds {@code T} to.
   *
   * @param argument the argument at the first position of {@code T} in a signature
   * @return the value primitive of the argument, or nothing for a node
   */
  static Optional<Class<? extends EfxDataType.Primitive>> bindingOf(final TypedExpression argument) {
    Class<? extends EfxDataType.Primitive> primitive = EfxTypeLattice.toPrimitive(argument.getDataType());
    return VALUE_PRIMITIVES.stream().filter(value -> value.isAssignableFrom(primitive)).findFirst();
  }

  /**
   * Lists the value primitives {@code T} ranges over.
   *
   * @return the six value primitives
   */
  static List<Class<? extends EfxDataType.Primitive>> valuePrimitives() {
    return VALUE_PRIMITIVES;
  }

  @Override
  public boolean isTypeVariable() {
    return true;
  }

  @Override
  public Optional<ParameterType> boundBy(final TypedExpression argument) {
    return bindingOf(argument).map(this::bind);
  }

  @Override
  public Class<? extends TypedExpression> getExpressionType() {
    return this.expressionType;
  }

  @Override
  public boolean accepts(final TypedExpression argument) {
    throw new IllegalStateException(this.symbol + " accepts no argument until it is bound");
  }

  /**
   * An unbound type variable rejects only the argument that cannot bind it, a node.
   */
  @Override
  public Rejection.Kind rejectionKindOf(final TypedExpression argument) {
    return Rejection.Kind.PATH;
  }

  /**
   * An unbound type variable takes a value of any primitive, which a node is not.
   */
  @Override
  public String describe() {
    return this.description;
  }

  @Override
  public String toString() {
    return this.symbol;
  }
}
