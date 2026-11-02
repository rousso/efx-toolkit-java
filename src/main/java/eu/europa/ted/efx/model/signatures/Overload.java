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

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;

/**
 * One overload of a {@link BuiltIn}: a {@link Signature} and the {@link Composer} that translates
 * a call matching it.
 */
public final class Overload {

  private final Signature signature;
  private final Composer composer;

  private Overload(final Signature signature, final Composer composer) {
    this.signature = signature;
    this.composer = composer;
  }

  /**
   * Declares an overload.
   *
   * @param signature the signature of the overload
   * @param composer the composer that translates a call of it
   * @return the overload
   */
  public static Overload overload(final Signature signature, final Composer composer) {
    return new Overload(signature, composer);
  }

  /**
   * @return the signature of the overload
   */
  public Signature getSignature() {
    return this.signature;
  }

  /**
   * Translates a call of this overload.
   *
   * @param script the script generator of the target language
   * @param arguments the arguments, converted to this overload's parameters
   * @return the translated call
   */
  public TypedExpression compose(final ScriptGenerator script, final ParsedArguments<?> arguments) {
    return this.composer.compose(script, arguments);
  }

  /**
   * Gives the negation of this overload, for an overload whose result is an indicator: the same
   * signature, and the logical negation of its translation.
   *
   * @return the negated overload
   */
  Overload negated() {
    return new Overload(this.signature, (script, arguments) -> script
        .composeLogicalNot(BooleanExpression.class.cast(this.compose(script, arguments))));
  }

  @Override
  public String toString() {
    return this.signature.toString();
  }
}
