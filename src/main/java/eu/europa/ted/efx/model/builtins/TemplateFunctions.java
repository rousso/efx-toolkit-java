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

import static eu.europa.ted.efx.model.builtins.Overload.overload;
import static eu.europa.ted.efx.model.signatures.BuiltInParameter.parameter;
import static eu.europa.ted.efx.model.signatures.ParameterType.DATE;
import static eu.europa.ted.efx.model.signatures.ParameterType.MULTILINGUAL_FIELD;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT;
import static eu.europa.ted.efx.model.signatures.ParameterType.TIME;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;
import java.util.function.BiFunction;

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.TimeExpression;

/**
 * The functions that may only be used in a template: formatting, and the preferred language of a
 * multilingual field.
 */
public enum TemplateFunctions implements BuiltIn {

  FORMAT_NUMBER("format-number",
      overload(signature(parameter("value", NUMBER), parameter("format", TEXT)).returning(TEXT),
          (script, args) -> script.composeNumberFormatting(args.numericAt(0), args.stringAt(1)))),

  FORMAT_SHORT("format-short",
      formats(ScriptGenerator::composeFormatDateShort, ScriptGenerator::composeFormatTimeShort)),

  FORMAT_MEDIUM("format-medium",
      formats(ScriptGenerator::composeFormatDateMedium, ScriptGenerator::composeFormatTimeMedium)),

  FORMAT_LONG("format-long",
      formats(ScriptGenerator::composeFormatDateLong, ScriptGenerator::composeFormatTimeLong)),

  PREFERRED_LANGUAGE("preferred-language",
      overload(signature(parameter("field", MULTILINGUAL_FIELD)).returning(TEXT),
          (script, args) -> script.getPreferredLanguage(args.pathAt(0)))),

  PREFERRED_LANGUAGE_TEXT("preferred-language-text",
      overload(signature(parameter("field", MULTILINGUAL_FIELD)).returning(TEXT),
          (script, args) -> script.getTextInPreferredLanguage(args.pathAt(0))));

  private final String name;
  private final List<Overload> overloads;

  TemplateFunctions(final String name, final Overload... overloads) {
    this.name = name;
    this.overloads = List.of(overloads);
  }

  /**
   * Declares the three overloads of a date and time format: a date, a time, or a date and a time
   * separated by a space.
   */
  private static Overload[] formats(
      final BiFunction<ScriptGenerator, DateExpression, StringExpression> formatDate,
      final BiFunction<ScriptGenerator, TimeExpression, StringExpression> formatTime) {
    return new Overload[] {
        overload(signature(parameter("value", DATE)).returning(TEXT),
            (script, args) -> formatDate.apply(script, args.dateAt(0))),
        overload(signature(parameter("value", TIME)).returning(TEXT),
            (script, args) -> formatTime.apply(script, args.timeAt(0))),
        overload(signature(parameter("value", DATE), parameter("time", TIME)).returning(TEXT),
            (script, args) -> script.composeStringConcatenation(List.of(
                formatDate.apply(script, args.dateAt(0)),
                script.getStringLiteralFromUnquotedString(" "),
                formatTime.apply(script, args.timeAt(1)))))};
  }

  @Override
  public boolean isTemplateOnly() {
    return true;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public List<Overload> getOverloads() {
    return this.overloads;
  }

  @Override
  public boolean isFunction() {
    return true;
  }
}
