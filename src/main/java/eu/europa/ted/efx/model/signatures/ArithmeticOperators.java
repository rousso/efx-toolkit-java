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
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

/**
 * The arithmetic operators: on numbers, and on dates and durations.
 */
public enum ArithmeticOperators implements BuiltIn {

  PLUS("+",
      numeric("+"),
      overload(signature(parameter("left", DURATION), parameter("right", DURATION)).returning(DURATION),
          (script, args) -> script.composeAddition(args.durationAt(0), args.durationAt(1))),
      overload(signature(parameter("date", DATE), parameter("duration", DURATION)).returning(DATE),
          (script, args) -> script.composeAddition(args.dateAt(0), args.durationAt(1))),
      overload(signature(parameter("duration", DURATION), parameter("date", DATE)).returning(DATE),
          (script, args) -> script.composeAddition(args.dateAt(1), args.durationAt(0)))),

  MINUS("-",
      numeric("-"),
      overload(signature(parameter("left", DURATION), parameter("right", DURATION)).returning(DURATION),
          (script, args) -> script.composeSubtraction(args.durationAt(0), args.durationAt(1))),
      overload(signature(parameter("end", DATE), parameter("start", DATE)).returning(DURATION),
          (script, args) -> script.composeSubtraction(args.dateAt(1), args.dateAt(0))),
      overload(signature(parameter("date", DATE), parameter("duration", DURATION)).returning(DATE),
          (script, args) -> script.composeSubtraction(args.dateAt(0), args.durationAt(1)))),

  MULTIPLY("*",
      numeric("*"),
      overload(signature(parameter("number", NUMBER), parameter("duration", DURATION)).returning(DURATION),
          (script, args) -> script.composeMultiplication(args.numericAt(0), args.durationAt(1))),
      overload(signature(parameter("duration", DURATION), parameter("number", NUMBER)).returning(DURATION),
          (script, args) -> script.composeMultiplication(args.numericAt(1), args.durationAt(0)))),

  DIVIDE("/",
      numeric("/")),

  MODULO("%",
      numeric("%"));

  private final String name;
  private final List<Overload> overloads;

  ArithmeticOperators(final String name, final Overload... overloads) {
    this.name = name;
    this.overloads = List.of(overloads);
  }

  private static Overload numeric(final String symbol) {
    return overload(signature(parameter("left", NUMBER), parameter("right", NUMBER)).returning(NUMBER),
        (script, args) -> script.composeNumericOperation(args.numericAt(0), symbol, args.numericAt(1)));
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
