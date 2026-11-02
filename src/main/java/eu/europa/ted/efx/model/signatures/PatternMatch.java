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

import static eu.europa.ted.efx.model.signatures.Overload.overload;
import static eu.europa.ted.efx.model.signatures.BuiltInParameter.parameter;
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

import eu.europa.ted.efx.util.EfxRegexValidator;

/**
 * The operator {@code like} or {@code not like} with its pattern, made for each use.
 *
 * <p>The pattern is a literal of the expression, not an argument: it reaches the script generator
 * as EFX text, which no translated argument can carry. So each use of the operator is a built-in
 * of its own, with the single overload {@code (TEXT) -> INDICATOR}, the way a user-defined function
 * is a built-in made from its declaration.</p>
 */
public final class PatternMatch implements BuiltIn {

  private final String name;
  private final List<Overload> overloads;

  private PatternMatch(final String name, final Overload overload) {
    this.name = name;
    this.overloads = List.of(overload);
  }

  /**
   * Makes the operator {@code like} with a pattern.
   *
   * @param pattern the pattern token, quotes included
   * @return the built-in
   * @throws eu.europa.ted.efx.exceptions.InvalidUsageException if the pattern uses a regular
   *         expression construct EFX does not support
   */
  public static PatternMatch like(final String pattern) {
    EfxRegexValidator.validate(pattern);
    return new PatternMatch("like",
        overload(signature(parameter("value", TEXT)).returning(INDICATOR),
            (script, arguments) -> script.composePatternMatchCondition(arguments.stringAt(0), pattern)));
  }

  /**
   * Makes the operator {@code not like} with a pattern.
   *
   * @param pattern the pattern token, quotes included
   * @return the built-in
   * @throws eu.europa.ted.efx.exceptions.InvalidUsageException if the pattern uses a regular
   *         expression construct EFX does not support
   */
  public static PatternMatch notLike(final String pattern) {
    return new PatternMatch("not like", like(pattern).overloads.get(0).negated());
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public List<Overload> getOverloads() {
    return this.overloads;
  }
}
