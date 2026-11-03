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

import eu.europa.ted.efx.interfaces.TypeChecker;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.types.EfxTypeTokenLookup;

/**
 * A parameter type that names one expression class, such as a number or a sequence of texts. An
 * argument matches it when the EFX-2 type checker allows the conversion.
 */
final class ConcreteParameterType implements ParameterType {

  private final Class<? extends TypedExpression> expressionType;

  ConcreteParameterType(final Class<? extends TypedExpression> expressionType) {
    this.expressionType = expressionType;
  }

  @Override
  public Class<? extends TypedExpression> getExpressionType() {
    return this.expressionType;
  }

  @Override
  public boolean accepts(final TypedExpression argument) {
    return TypeChecker.V2.canConvert(argument.getClass(), this.expressionType);
  }

  /**
   * Compares the class of the argument's single values, the one the argument binds {@code T} to,
   * with this one: if they convert, only the argument's cardinality is wrong.
   */
  @Override
  public Match.Kind rejectionKindOf(final TypedExpression argument) {
    return T.boundBy(argument)
        .map(value -> TypeChecker.V2.canConvert(value.getExpressionType(), this.expressionType)
            ? Match.Kind.CARDINALITY
            : Match.Kind.PRIMITIVE)
        .orElse(Match.Kind.PATH);
  }

  /**
   * Names the type in quotes, as a declaration writes it.
   */
  @Override
  public String describe() {
    return "'" + EfxTypeTokenLookup.nameOf(TypedExpression.getEfxDataType(this.expressionType))
        + "'";
  }

  @Override
  public String toString() {
    return this.expressionType.getSimpleName();
  }
}
