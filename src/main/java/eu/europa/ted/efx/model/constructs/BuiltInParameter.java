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
package eu.europa.ted.efx.model.constructs;

import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * A parameter declared in the catalogue, by a {@link Signature}: a name and a {@link ParameterType},
 * which may mention {@code T}. The names are those of the autocomplete catalogue, so that the
 * catalogue can be derived from the signatures.
 *
 * @param <E> the expression class an argument given for it is converted to
 */
public final class BuiltInParameter<E extends TypedExpression> implements Parameter {

  private final String name;
  private final ParameterType<E> type;

  private BuiltInParameter(final String name, final ParameterType<E> type) {
    this.name = name;
    this.type = type;
  }

  /**
   * Declares a parameter.
   *
   * @param <E> the expression class of its type
   * @param name the name of the parameter
   * @param type the type of the parameter
   * @return the parameter
   */
  public static <E extends TypedExpression> BuiltInParameter<E> parameter(final String name,
      final ParameterType<E> type) {
    return new BuiltInParameter<>(name, type);
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public ParameterType<E> getType() {
    return this.type;
  }

  @Override
  public String toString() {
    return this.name + ": " + this.type;
  }
}
