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
import static eu.europa.ted.efx.model.signatures.ParameterType.NODES;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.T_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

import eu.europa.ted.efx.model.expressions.sequence.NumericSequenceExpression;

/**
 * The functions that count, aggregate or round numbers.
 */
public enum NumericFunctions implements BuiltIn {

  COUNT("count",
      overload(signature(parameter("sequence", T_SEQUENCE)).returning(NUMBER),
          (script, args) -> script.composeCountOperation(args.sequenceAt(0))),
      overload(signature(parameter("nodes", NODES)).returning(NUMBER),
          (script, args) -> script.composeCountOperation(args.sequenceAt(0)))),

  SUM("sum",
      overload(signature(parameter("values", NUMBER_SEQUENCE)).returning(NUMBER),
          (script, args) -> script.composeSumOperation(args.at(0, NumericSequenceExpression.class)))),

  MIN("min",
      overload(signature(parameter("values", NUMBER_SEQUENCE)).returning(NUMBER),
          (script, args) -> script.composeMinFunction(args.at(0, NumericSequenceExpression.class)))),

  MAX("max",
      overload(signature(parameter("values", NUMBER_SEQUENCE)).returning(NUMBER),
          (script, args) -> script.composeMaxFunction(args.at(0, NumericSequenceExpression.class)))),

  AVERAGE("average",
      overload(signature(parameter("values", NUMBER_SEQUENCE)).returning(NUMBER),
          (script, args) -> script.composeAvgFunction(args.at(0, NumericSequenceExpression.class)))),

  ABSOLUTE("absolute",
      overload(signature(parameter("value", NUMBER)).returning(NUMBER),
          (script, args) -> script.composeAbsFunction(args.numericAt(0)))),

  ROUND("round",
      overload(signature(parameter("value", NUMBER)).returning(NUMBER),
          (script, args) -> script.composeRoundFunction(args.numericAt(0)))),

  ROUND_DOWN("round-down",
      overload(signature(parameter("value", NUMBER)).returning(NUMBER),
          (script, args) -> script.composeFloorFunction(args.numericAt(0)))),

  ROUND_UP("round-up",
      overload(signature(parameter("value", NUMBER)).returning(NUMBER),
          (script, args) -> script.composeCeilingFunction(args.numericAt(0))));

  private final String name;
  private final List<Overload> overloads;

  NumericFunctions(final String name, final Overload... overloads) {
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

  @Override
  public boolean isFunction() {
    return true;
  }
}
