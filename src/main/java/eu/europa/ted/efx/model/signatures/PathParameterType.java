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

import java.util.Optional;

import eu.europa.ted.efx.interfaces.TypeChecker;
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * A parameter type that takes a reference as a path rather than as its value: any path, or a path
 * of one type. An argument that is not a path never matches it, and since a path parameter takes
 * no values, no argument is rejected by it for its cardinality alone.
 */
final class PathParameterType implements ParameterType {

  private final Class<? extends PathExpression> pathType;

  PathParameterType(final Class<? extends PathExpression> pathType) {
    this.pathType = pathType;
  }

  @Override
  public boolean isTypeVariable() {
    return false;
  }

  @Override
  public ParameterType bind(final Class<? extends EfxDataType.Primitive> primitive) {
    return this;
  }

  @Override
  public Optional<ParameterType> boundBy(final TypedExpression argument) {
    return Optional.of(this);
  }

  @Override
  public Class<? extends TypedExpression> getExpressionType() {
    return this.pathType;
  }

  @Override
  public boolean accepts(final TypedExpression argument) {
    return this.pathType.isInstance(argument) || argument instanceof PathExpression
        && TypeChecker.V2.canConvert(argument.getClass(), this.pathType);
  }

  @Override
  public boolean acceptsValuesOf(final TypedExpression argument) {
    return false;
  }

  @Override
  public String toString() {
    return this.pathType.getSimpleName();
  }
}
