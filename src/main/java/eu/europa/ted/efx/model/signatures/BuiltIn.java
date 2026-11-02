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

import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * An EFX operator or built-in function: its name and its {@link Overload overloads}.
 *
 * <p>The name is the operator as written, such as {@code +} or {@code is not empty}, or the
 * function token, such as {@code substring}. The standard built-ins are the constants of the enums
 * of this package, gathered by {@link BuiltIns}; a {@link PatternMatch} is made for each use of
 * {@code like}.</p>
 */
public interface BuiltIn {

  /**
   * @return the operator as written, or the function token
   */
  String getName();

  /**
   * @return the overloads, none of which applies to the same arguments as another
   */
  List<Overload> getOverloads();

  /**
   * Tells whether the built-in may only be used in a template.
   *
   * @return {@code true} for the formatting and preferred-language functions
   */
  default boolean isTemplateOnly() {
    return false;
  }

  /**
   * Tells whether the built-in is a function, called with its arguments in parentheses after its
   * name, rather than an operator.
   *
   * @return {@code true} for the functions, {@code not} among them
   */
  default boolean isFunction() {
    return false;
  }

  /**
   * Resolves a call of this built-in.
   *
   * @param arguments the translated arguments of the call
   * @return the overload that applies with the converted arguments, or the mismatch
   */
  default Resolution resolve(final List<? extends TypedExpression> arguments) {
    return Resolution.of(this, arguments);
  }
}
