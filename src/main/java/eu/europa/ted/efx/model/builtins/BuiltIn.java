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
package eu.europa.ted.efx.model.builtins;

import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.signatures.Construct;
import eu.europa.ted.efx.model.signatures.Parameters;
import eu.europa.ted.efx.model.signatures.Resolution;

/**
 * An EFX operator or built-in function: a construct that the catalogue translates itself. It has
 * a name and {@link Overload overloads}, each a signature with the composer that translates a call
 * the signature takes.
 *
 * <p>The name is the operator as written, such as {@code +} or {@code is not empty}, or the
 * function token, such as {@code substring}. The standard built-ins are the constants of the enums
 * of this package, gathered by {@link BuiltIns}; a {@link PatternMatch} is made for each use of
 * {@code like}.</p>
 */
public interface BuiltIn extends Construct {

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
   * @return "Function 'substring'" for a function, "Operator '+'" for an operator
   */
  @Override
  default String describe() {
    return this.isFunction() ? Construct.functionName(this.getName())
        : Construct.operatorName(this.getName());
  }

  /**
   * @return the arguments of a function by their position, such as "2nd argument"; the operands of
   *         an operator, "operand", or "left operand" and "right operand"
   */
  @Override
  default String roleOf(final int position, final int count) {
    return this.isFunction() ? Construct.argumentRole(position, count)
        : Construct.operandRole(position, count);
  }

  /**
   * @return the parameters of the signature of each overload
   */
  @Override
  default List<Parameters<?>> getParameterLists() {
    return this.getOverloads().stream()
        .<Parameters<?>>map(overload -> overload.getSignature().getParameters())
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Translates a call of this built-in with the overload whose signature takes its arguments.
   *
   * @param resolution the resolution of the call by this built-in
   * @param script the script generator
   * @return the translated call
   * @throws IllegalArgumentException if no overload of this built-in has the parameter list that
   *         resolved the call
   */
  default TypedExpression compose(final Resolution resolution, final ScriptGenerator script) {
    return this.getOverloads().stream()
        .filter(overload -> overload.getSignature().getParameters()
            .equals(resolution.getParameters()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "No overload of '" + this.getName() + "' takes " + resolution.getParameters()))
        .compose(script, resolution.getArguments());
  }
}
