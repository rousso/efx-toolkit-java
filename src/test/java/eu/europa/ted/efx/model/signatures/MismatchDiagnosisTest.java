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
import static eu.europa.ted.efx.model.signatures.Signature.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import eu.europa.ted.efx.model.expressions.Expression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.NodePath;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringLiteral;
import eu.europa.ted.efx.model.expressions.sequence.StringSequencePath;
import eu.europa.ted.efx.model.signatures.Mismatch.Rejection;

/**
 * Tests that the rejection a mismatch reports does not depend on the order of the overloads: the
 * one at the furthest position wins, and among those a cardinality failure comes before a
 * primitive one, and a primitive failure before a path one.
 */
class MismatchDiagnosisTest {

  private static final StringLiteral TEXT =
      Expression.instantiate("$text", StringLiteral.class);
  private static final StringSequencePath REPEATABLE_TEXT_FIELD =
      Expression.instantiate("$repeatable", StringSequencePath.class);
  private static final NodePath NODE =
      Expression.instantiate("$node", NodePath.class);

  private static final Overload NUMBER_THEN_TEXT = overload(
      signature(parameter("first", ParameterType.NUMBER), parameter("second", ParameterType.TEXT))
          .returning(ParameterType.TEXT),
      (script, args) -> args.stringAt(1));
  private static final Overload TEXT_THEN_TEXT = overload(
      signature(parameter("first", ParameterType.TEXT), parameter("second", ParameterType.TEXT))
          .returning(ParameterType.TEXT),
      (script, args) -> args.stringAt(1));
  private static final Overload TEXT_THEN_NUMBER = overload(
      signature(parameter("first", ParameterType.TEXT), parameter("second", ParameterType.NUMBER))
          .returning(ParameterType.TEXT),
      (script, args) -> args.stringAt(0));
  private static final Overload NUMBER = overload(
      signature(parameter("value", ParameterType.NUMBER)).returning(ParameterType.NUMBER),
      (script, args) -> args.numericAt(0));
  private static final Overload NODES = overload(
      signature(parameter("nodes", ParameterType.NODES)).returning(ParameterType.NUMBER),
      (script, args) -> script.composeCountOperation(args.sequenceAt(0)));

  @Test
  void testFurthestPositionAndCardinalityWin_WhateverTheOrderOfTheOverloads() {
    for (List<Overload> overloads : orders(NUMBER_THEN_TEXT, TEXT_THEN_TEXT, TEXT_THEN_NUMBER)) {
      Rejection diagnosis = diagnosis(overloads, TEXT, REPEATABLE_TEXT_FIELD);
      assertEquals(1, diagnosis.getPosition(), overloads::toString);
      assertEquals(Rejection.Kind.CARDINALITY, diagnosis.getKind(), overloads::toString);
      assertEquals(StringExpression.class, diagnosis.getRequired().getExpressionType(),
          overloads::toString);
    }
  }

  @Test
  void testPrimitiveWinsOverPath_WhateverTheOrderOfTheOverloads() {
    for (List<Overload> overloads : orders(NUMBER, NODES)) {
      Rejection diagnosis = diagnosis(overloads, TEXT);
      assertEquals(Rejection.Kind.PRIMITIVE, diagnosis.getKind(), overloads::toString);
      assertEquals(NumericExpression.class, diagnosis.getRequired().getExpressionType(),
          overloads::toString);
    }
  }

  @Test
  void testNodeWhereAValueIsRequired_IsAPathFailure() {
    assertEquals(Rejection.Kind.PATH, diagnosis(List.of(NUMBER), NODE).getKind());
  }

  @Test
  void testSequenceWhereASingleValueOfItsPrimitiveIsRequired_IsACardinalityFailure() {
    Rejection diagnosis = ComparisonOperators.EQUAL.resolve(List.of(REPEATABLE_TEXT_FIELD, TEXT))
        .getMismatch().getDiagnosis();
    assertEquals(0, diagnosis.getPosition());
    assertEquals(Rejection.Kind.CARDINALITY, diagnosis.getKind());
  }

  @Test
  void testArityMismatch_HasNoDiagnosis() {
    Mismatch mismatch = StringFunctions.SUBSTRING.resolve(List.of(TEXT)).getMismatch();
    assertThrows(IllegalStateException.class, mismatch::getDiagnosis);
  }

  private static Rejection diagnosis(final List<Overload> overloads,
      final TypedExpression... arguments) {
    Resolution resolution = new TestBuiltIn(overloads).resolve(List.of(arguments));
    assertFalse(resolution.isResolved());
    return resolution.getMismatch().getDiagnosis();
  }

  /**
   * Gives every order of the overloads.
   */
  private static List<List<Overload>> orders(final Overload... overloads) {
    List<List<Overload>> orders = new ArrayList<>();
    permute(new ArrayList<>(List.of(overloads)), 0, orders);
    return orders;
  }

  private static void permute(final List<Overload> overloads, final int from,
      final List<List<Overload>> orders) {
    if (from == overloads.size()) {
      orders.add(List.copyOf(overloads));
      return;
    }
    for (int position = from; position < overloads.size(); position++) {
      Collections.swap(overloads, from, position);
      permute(overloads, from + 1, orders);
      Collections.swap(overloads, from, position);
    }
  }

  /**
   * A built-in whose overloads the test gives, in the order it gives them.
   */
  private static final class TestBuiltIn implements BuiltIn {

    private final List<Overload> overloads;

    TestBuiltIn(final List<Overload> overloads) {
      this.overloads = overloads;
    }

    @Override
    public String getName() {
      return "test";
    }

    @Override
    public List<Overload> getOverloads() {
      return this.overloads;
    }
  }
}
