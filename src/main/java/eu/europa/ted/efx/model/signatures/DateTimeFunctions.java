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
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.TIME;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

/**
 * The functions on dates, times and durations.
 */
public enum DateTimeFunctions implements BuiltIn {

  YEAR("year",
      overload(signature(parameter("value", DATE)).returning(NUMBER),
          (script, args) -> script.composeYearFunction(args.dateAt(0)))),

  MONTH("month",
      overload(signature(parameter("value", DATE)).returning(NUMBER),
          (script, args) -> script.composeMonthFunction(args.dateAt(0)))),

  DAY("day",
      overload(signature(parameter("value", DATE)).returning(NUMBER),
          (script, args) -> script.composeDayFunction(args.dateAt(0)))),

  HOURS("hours",
      overload(signature(parameter("value", TIME)).returning(NUMBER),
          (script, args) -> script.composeHoursFunction(args.timeAt(0)))),

  MINUTES("minutes",
      overload(signature(parameter("value", TIME)).returning(NUMBER),
          (script, args) -> script.composeMinutesFunction(args.timeAt(0)))),

  SECONDS("seconds",
      overload(signature(parameter("value", TIME)).returning(NUMBER),
          (script, args) -> script.composeSecondsFunction(args.timeAt(0)))),

  YEARS("years",
      overload(signature(parameter("value", DURATION)).returning(NUMBER),
          (script, args) -> script.composeYearsFromDurationFunction(args.durationAt(0)))),

  MONTHS("months",
      overload(signature(parameter("value", DURATION)).returning(NUMBER),
          (script, args) -> script.composeMonthsFromDurationFunction(args.durationAt(0)))),

  DAYS("days",
      overload(signature(parameter("value", DURATION)).returning(NUMBER),
          (script, args) -> script.composeDaysFromDurationFunction(args.durationAt(0)))),

  CURRENT_DATE("current-date",
      overload(signature().returning(DATE),
          (script, args) -> script.getCurrentDate())),

  CURRENT_TIME("current-time",
      overload(signature().returning(TIME),
          (script, args) -> script.getCurrentTime())),

  ADD_DURATION("add-duration",
      overload(signature(parameter("date", DATE), parameter("duration", DURATION)).returning(DATE),
          (script, args) -> script.composeAddition(args.dateAt(0), args.durationAt(1)))),

  SUBTRACT_DURATION("subtract-duration",
      overload(signature(parameter("date", DATE), parameter("duration", DURATION)).returning(DATE),
          (script, args) -> script.composeSubtraction(args.dateAt(0), args.durationAt(1))));

  private final String name;
  private final List<Overload> overloads;

  DateTimeFunctions(final String name, final Overload... overloads) {
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
