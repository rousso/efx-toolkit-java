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

import static eu.europa.ted.efx.model.signatures.BuiltInParameter.parameter;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT_SEQUENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import eu.europa.ted.efx.model.expressions.Expression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.sequence.DateSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.NumericSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;

/**
 * Proves that a grammar construct gives the result of the signature that takes its arguments,
 * with {@code T} bound by them: the class the visitor translates the construct to.
 */
class GrammarConstructTest {

  @Test
  void testParentheses_GiveASingleValueForASingleValue() {
    assertResult(StringExpression.class, GrammarConstruct.PARENTHESES,
        value(StringExpression.class));
  }

  @Test
  void testParentheses_GiveASequenceForASequence() {
    assertResult(StringSequenceExpression.class, GrammarConstruct.PARENTHESES,
        value(StringSequenceExpression.class));
  }

  @Test
  void testConditional_GivesASingleValueForTwoSingleValues() {
    assertResult(NumericExpression.class, GrammarConstruct.CONDITIONAL,
        value(BooleanExpression.class), value(NumericExpression.class),
        value(NumericExpression.class));
  }

  @Test
  void testConditional_GivesASequenceIfEitherBranchIsOne() {
    assertResult(NumericSequenceExpression.class, GrammarConstruct.CONDITIONAL,
        value(BooleanExpression.class), value(NumericExpression.class),
        value(NumericSequenceExpression.class));
  }

  @Test
  void testList_GivesTheSequenceOfItsItems() {
    assertResult(DateSequenceExpression.class, GrammarConstruct.LIST, value(DateExpression.class),
        value(DateExpression.class));
  }

  @Test
  void testIteration_GivesASequenceForASingleValue() {
    assertResult(NumericSequenceExpression.class, GrammarConstruct.ITERATION,
        value(NumericExpression.class));
  }

  @Test
  void testIndexInValue_GivesASingleValue() {
    assertResult(DateExpression.class, GrammarConstruct.INDEX_IN_VALUE,
        value(DateSequenceExpression.class), value(NumericExpression.class));
  }

  @Test
  void testConstructOfOneValue_GivesItConvertedToItsParameter() {
    assertResult(StringSequenceExpression.class,
        GrammarConstruct.of("Iterator 'text:$x'", parameter("sequence", TEXT_SEQUENCE)),
        value(StringExpression.class));
  }

  private static void assertResult(final Class<? extends TypedExpression> expected,
      final GrammarConstruct construct, final TypedExpression... arguments) {
    assertEquals(expected, construct.resultOf(construct.resolve(List.of(arguments)).orElseThrow()));
  }

  private static TypedExpression value(final Class<? extends TypedExpression> type) {
    return Expression.instantiate(type.getSimpleName(), type);
  }
}
