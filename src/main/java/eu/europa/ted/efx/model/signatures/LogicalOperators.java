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
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

/**
 * The logical operators {@code and} and {@code or}, and the function {@code not}.
 */
public enum LogicalOperators implements BuiltIn {

  AND("and",
      overload(signature(parameter("left", INDICATOR), parameter("right", INDICATOR)).returning(INDICATOR),
          (script, args) -> script.composeLogicalAnd(args.booleanAt(0), args.booleanAt(1)))),

  OR("or",
      overload(signature(parameter("left", INDICATOR), parameter("right", INDICATOR)).returning(INDICATOR),
          (script, args) -> script.composeLogicalOr(args.booleanAt(0), args.booleanAt(1)))),

  NOT("not",
      overload(signature(parameter("value", INDICATOR)).returning(INDICATOR),
          (script, args) -> script.composeLogicalNot(args.booleanAt(0))));

  private final String name;
  private final List<Overload> overloads;

  LogicalOperators(final String name, final Overload... overloads) {
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
