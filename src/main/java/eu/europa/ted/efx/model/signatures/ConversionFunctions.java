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
import static eu.europa.ted.efx.model.signatures.Parameter.parameter;
import static eu.europa.ted.efx.model.signatures.ParameterType.DATE;
import static eu.europa.ted.efx.model.signatures.ParameterType.DURATION;
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT;
import static eu.europa.ted.efx.model.signatures.ParameterType.TIME;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

/**
 * The functions that convert a value from one primitive to another.
 */
public enum ConversionFunctions implements BuiltIn {

  TO_TEXT("text",
      overload(signature(parameter("value", NUMBER)).returning(TEXT),
          (script, args) -> script.composeToStringConversion(args.numericAt(0))),
      overload(signature(parameter("value", INDICATOR)).returning(TEXT),
          (script, args) -> script.composeToStringConversion(args.booleanAt(0))),
      overload(signature(parameter("value", DATE)).returning(TEXT),
          (script, args) -> script.composeToStringConversion(args.dateAt(0))),
      overload(signature(parameter("value", TIME)).returning(TEXT),
          (script, args) -> script.composeToStringConversion(args.timeAt(0))),
      overload(signature(parameter("value", DURATION)).returning(TEXT),
          (script, args) -> script.composeToStringConversion(args.durationAt(0)))),

  TO_NUMBER("number",
      overload(signature(parameter("value", TEXT)).returning(NUMBER),
          (script, args) -> script.composeToNumberConversion(args.stringAt(0))),
      overload(signature(parameter("value", INDICATOR)).returning(NUMBER),
          (script, args) -> script.composeToNumberConversion(args.booleanAt(0)))),

  TO_INDICATOR("indicator",
      overload(signature(parameter("value", NUMBER)).returning(INDICATOR),
          (script, args) -> script.composeToBooleanConversion(args.numericAt(0)))),

  TO_DATE("date",
      overload(signature(parameter("value", TEXT)).returning(DATE),
          (script, args) -> script.composeToDateConversion(args.stringAt(0)))),

  TO_TIME("time",
      overload(signature(parameter("value", TEXT)).returning(TIME),
          (script, args) -> script.composeToTimeConversion(args.stringAt(0)))),

  TO_DAY_TIME_DURATION("day-time-duration",
      overload(signature(parameter("value", TEXT)).returning(DURATION),
          (script, args) -> script.composeToDayTimeDurationConversion(args.stringAt(0)))),

  TO_YEAR_MONTH_DURATION("year-month-duration",
      overload(signature(parameter("value", TEXT)).returning(DURATION),
          (script, args) -> script.composeToYearMonthDurationConversion(args.stringAt(0))));

  private final String name;
  private final List<Overload> overloads;

  ConversionFunctions(final String name, final Overload... overloads) {
    this.name = name;
    this.overloads = List.of(overloads);
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
