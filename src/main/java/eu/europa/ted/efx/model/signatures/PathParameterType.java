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
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * A parameter type that takes a reference as a path rather than as its value: any path, or a path
 * of one type. An argument that is not a path never matches it, and since a path parameter takes
 * no values, no argument is rejected by it for its cardinality alone.
 */
final class PathParameterType implements ParameterType {

  private final Class<? extends PathExpression> pathType;
  private final String description;

  /**
   * @param pathType the class of the paths this type takes
   * @param description what it takes, as a type error says it, such as "a field reference"
   */
  PathParameterType(final Class<? extends PathExpression> pathType, final String description) {
    this.pathType = pathType;
    this.description = description;
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
  public Match.Kind rejectionKindOf(final TypedExpression argument) {
    return Match.Kind.PATH;
  }

  @Override
  public String describe() {
    return this.description;
  }

  @Override
  public String toString() {
    return this.pathType.getSimpleName();
  }
}
