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
package eu.europa.ted.efx.sdk2;

import java.util.List;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.signatures.BuiltIn;
import eu.europa.ted.efx.model.signatures.Construct;
import eu.europa.ted.efx.model.signatures.Overload;

/**
 * A built-in as a call writes it: under another of its names, as {@code string} for {@code text},
 * or written as an operator after a field, as {@code :preferredLanguage} for the function
 * {@code preferred-language}. A type error names the call as it is written; everything else is
 * the built-in's own, its overloads included, so the built-in checks and translates the call.
 */
final class AsWritten implements BuiltIn {

  private final String token;
  private final boolean function;
  private final BuiltIn builtIn;

  private AsWritten(final String token, final boolean function, final BuiltIn builtIn) {
    this.token = token;
    this.function = function;
    this.builtIn = builtIn;
  }

  /**
   * @param token the function as the call writes it, such as {@code string}
   * @param builtIn the built-in called
   * @return the built-in, named as a function under that token
   */
  static AsWritten function(final String token, final BuiltIn builtIn) {
    return new AsWritten(token, true, builtIn);
  }

  /**
   * @param token the operator as written, such as {@code :preferredLanguage}
   * @param builtIn the built-in it stands for
   * @return the built-in, named as an operator under that token
   */
  static AsWritten operator(final String token, final BuiltIn builtIn) {
    return new AsWritten(token, false, builtIn);
  }

  /**
   * @return the name of the built-in, such as {@code text} for {@code string}
   */
  @Override
  public String getName() {
    return this.builtIn.getName();
  }

  @Override
  public List<Overload> getOverloads() {
    return this.builtIn.getOverloads();
  }

  @Override
  public boolean isTemplateOnly() {
    return this.builtIn.isTemplateOnly();
  }

  /**
   * @return {@code true} if the call writes it as a function
   */
  @Override
  public boolean isFunction() {
    return this.function;
  }

  @Override
  public List<String> suggestionsFor(final List<? extends TypedExpression> arguments) {
    return this.builtIn.suggestionsFor(arguments);
  }

  /**
   * @return the call as written: "Function 'string'", "Operator ':preferredLanguage'"
   */
  @Override
  public String describe() {
    return this.function ? Construct.functionName(this.token)
        : Construct.operatorName(this.token);
  }
}
