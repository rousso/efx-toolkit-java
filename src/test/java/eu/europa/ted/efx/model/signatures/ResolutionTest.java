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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import eu.europa.ted.efx.EfxTranslatorOptions;
import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.Expression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanLiteral;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.DatePath;
import eu.europa.ted.efx.model.expressions.scalar.DurationLiteral;
import eu.europa.ted.efx.model.expressions.scalar.DynamicExpression;
import eu.europa.ted.efx.model.expressions.scalar.MultilingualStringPath;
import eu.europa.ted.efx.model.expressions.scalar.NodePath;
import eu.europa.ted.efx.model.expressions.scalar.NumericLiteral;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringLiteral;
import eu.europa.ted.efx.model.expressions.scalar.StringPath;
import eu.europa.ted.efx.model.expressions.sequence.MultilingualStringSequencePath;
import eu.europa.ted.efx.model.expressions.sequence.NodeSequencePath;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequencePath;
import eu.europa.ted.efx.xpath.XPathScriptGenerator;

/**
 * Tests how a call resolves: arity, the binding of {@code T}, conversion by widening and by
 * promotion, path parameters, and each kind of mismatch.
 */
class ResolutionTest {

  private static final ScriptGenerator SCRIPT =
      new XPathScriptGenerator(EfxTranslatorOptions.DEFAULT);

  private static final DatePath DATE_FIELD =
      Expression.instantiate("$date", DatePath.class);
  private static final DurationLiteral DURATION =
      Expression.instantiate("$duration", DurationLiteral.class);
  private static final StringLiteral TEXT =
      Expression.instantiate("$text", StringLiteral.class);
  private static final StringLiteral OTHER_TEXT =
      Expression.instantiate("$other", StringLiteral.class);
  private static final NumericLiteral NUMBER =
      Expression.instantiate("$number", NumericLiteral.class);
  private static final BooleanLiteral INDICATOR =
      Expression.instantiate("$indicator", BooleanLiteral.class);
  private static final StringPath TEXT_FIELD =
      Expression.instantiate("$field", StringPath.class);
  private static final MultilingualStringPath MULTILINGUAL_FIELD =
      Expression.instantiate("$multilingual", MultilingualStringPath.class);
  private static final StringSequencePath REPEATABLE_TEXT_FIELD =
      Expression.instantiate("$repeatable", StringSequencePath.class);
  private static final MultilingualStringSequencePath REPEATABLE_MULTILINGUAL_FIELD =
      Expression.instantiate("$repeatableMultilingual", MultilingualStringSequencePath.class);
  private static final StringSequenceExpression TEXTS =
      Expression.instantiate("$texts", StringSequenceExpression.class);
  private static final NodePath NODE =
      Expression.instantiate("$node", NodePath.class);
  private static final DynamicExpression DYNAMIC =
      Expression.instantiate("$dynamic", DynamicExpression.class);

  @Test
  void testDatePlusDuration_IsADateAddition() {
    TypedExpression result = resolve(ArithmeticOperators.PLUS, DATE_FIELD, DURATION).compose(SCRIPT);
    assertEquals(DateExpression.class, result.getClass());
    assertEquals("($date + $duration)", result.getScript());
  }

  @Test
  void testDurationPlusDate_IsTheSameDateAddition() {
    TypedExpression result = resolve(ArithmeticOperators.PLUS, DURATION, DATE_FIELD).compose(SCRIPT);
    assertEquals(DateExpression.class, result.getClass());
    assertEquals("($date + $duration)", result.getScript());
  }

  @Test
  void testDateMinusDate_SubtractsTheSecondFromTheFirst() {
    DatePath later = Expression.instantiate("$later", DatePath.class);
    TypedExpression result = resolve(ArithmeticOperators.MINUS, later, DATE_FIELD).compose(SCRIPT);
    assertEquals(SCRIPT.composeSubtraction(TypedExpression.from(DATE_FIELD, DateExpression.class),
        TypedExpression.from(later, DateExpression.class)).getScript(), result.getScript());
  }

  @Test
  void testMultilingualTextEqualsText_WidensToText() {
    Resolution resolution = resolve(ComparisonOperators.EQUAL, MULTILINGUAL_FIELD, TEXT);
    assertEquals(StringExpression.class, resolution.getArguments().typeAt(0));
    assertEquals(StringExpression.class, resolution.getArguments().typeAt(1));
  }

  @Test
  void testDynamicEqualsIndicator_BindsTToIndicator() {
    Resolution resolution = resolve(ComparisonOperators.EQUAL, DYNAMIC, INDICATOR);
    assertEquals(BooleanExpression.class, resolution.getArguments().typeAt(0));
  }

  @Test
  void testCountOfAField_PromotesItToASequence() {
    Resolution resolution = resolve(NumericFunctions.COUNT, TEXT_FIELD);
    assertEquals(StringSequenceExpression.class, resolution.getArguments().typeAt(0));
  }

  @Test
  void testTextInText_PromotesTheHaystack() {
    Resolution resolution = resolve(ConditionOperators.IN, TEXT, OTHER_TEXT);
    assertEquals(StringExpression.class, resolution.getArguments().typeAt(0));
    assertEquals(StringSequenceExpression.class, resolution.getArguments().typeAt(1));
  }

  @Test
  void testCountOfANode_CountsTheNodesItSelects() {
    Resolution resolution = resolve(NumericFunctions.COUNT, NODE);
    assertEquals(NodeSequencePath.class, resolution.getArguments().typeAt(0));
  }

  @Test
  void testIsPresentOfAField_KeepsThePath() {
    Resolution resolution = resolve(ConditionOperators.IS_PRESENT, TEXT_FIELD);
    assertSame(TEXT_FIELD, resolution.getArguments().pathAt(0));
  }

  @Test
  void testUnionOfTextAndMultilingualSequences_IsATextSequence() {
    TypedExpression result = resolve(SequenceFunctions.VALUE_UNION, REPEATABLE_TEXT_FIELD,
        REPEATABLE_MULTILINGUAL_FIELD).compose(SCRIPT);
    assertEquals(StringSequenceExpression.class, result.getClass());
  }

  @Test
  void testConcatOfThreeTexts_ConcatenatesThemInOrder() {
    TypedExpression result = resolve(StringFunctions.CONCAT, TEXT, OTHER_TEXT, TEXT_FIELD)
        .compose(SCRIPT);
    assertEquals(SCRIPT.composeStringConcatenation(List.of(TEXT, OTHER_TEXT,
        TypedExpression.from(TEXT_FIELD, StringExpression.class)))
        .getScript(), result.getScript());
  }

  @Test
  void testTextPlusNumber_IsRejectedAtTheTextByEveryOverload() {
    Mismatch mismatch = mismatch(ArithmeticOperators.PLUS, TEXT, NUMBER);
    assertFalse(mismatch.isArityMismatch());
    assertEquals(List.of(0, 0, 0, 0), positions(mismatch));
  }

  @Test
  void testTextEqualsNumber_IsRejectedAtTheNumber() {
    Mismatch mismatch = mismatch(ComparisonOperators.EQUAL, TEXT, NUMBER);
    assertEquals(List.of(1), positions(mismatch));
    assertEquals(ParameterType.T, mismatch.getRejections().get(0).getExpected());
  }

  @Test
  void testRepeatableFieldEqualsText_IsRejectedAtTheSequence() {
    assertEquals(List.of(0), positions(mismatch(ComparisonOperators.EQUAL, REPEATABLE_TEXT_FIELD, TEXT)));
  }

  @Test
  void testNodeEqualsText_IsRejectedAtTheNode() {
    assertEquals(List.of(0), positions(mismatch(ComparisonOperators.EQUAL, NODE, TEXT)));
  }

  @Test
  void testIndexOfANumberInTexts_IsRejectedAtTheNumber() {
    assertEquals(List.of(1), positions(mismatch(SequenceFunctions.INDEX_OF, TEXTS, NUMBER)));
  }

  @Test
  void testIsPresentOfAValue_IsRejected() {
    assertEquals(List.of(0), positions(mismatch(ConditionOperators.IS_PRESENT, TEXT)));
  }

  @Test
  void testSubstringOfOneArgument_IsAnArityMismatch() {
    assertTrue(mismatch(StringFunctions.SUBSTRING, TEXT).isArityMismatch());
  }

  @Test
  void testConcatOfNoArgument_IsAnArityMismatch() {
    assertTrue(mismatch(StringFunctions.CONCAT).isArityMismatch());
  }

  @Test
  void testNotIn_NegatesIn() {
    assertEquals(SCRIPT.composeLogicalNot(composed(ConditionOperators.IN, TEXT, TEXTS)).getScript(),
        resolve(ConditionOperators.NOT_IN, TEXT, TEXTS).compose(SCRIPT).getScript());
  }

  @Test
  void testHasDuplicates_NegatesHasNoDuplicates() {
    assertEquals(SCRIPT.composeLogicalNot(composed(ConditionOperators.HAS_NO_DUPLICATES, TEXTS))
        .getScript(), resolve(ConditionOperators.HAS_DUPLICATES, TEXTS).compose(SCRIPT).getScript());
  }

  @Test
  void testLike_MatchesThePattern() {
    TypedExpression result = resolve(PatternMatch.like("'[0-9]+'"), TEXT).compose(SCRIPT);
    assertEquals(SCRIPT.composePatternMatchCondition(TEXT, "'[0-9]+'").getScript(),
        result.getScript());
  }

  @Test
  void testNotLike_NegatesLike() {
    assertEquals(SCRIPT.composeLogicalNot(composed(PatternMatch.like("'[0-9]+'"), TEXT)).getScript(),
        resolve(PatternMatch.notLike("'[0-9]+'"), TEXT).compose(SCRIPT).getScript());
  }

  @Test
  void testLike_RejectsAnUnsupportedPattern() {
    assertThrows(InvalidUsageException.class, () -> PatternMatch.like("'(?=a)'"));
  }

  @Test
  void testMismatch_HasNoOverload() {
    Resolution resolution = ArithmeticOperators.PLUS.resolve(List.of(TEXT, NUMBER));
    assertThrows(IllegalStateException.class, resolution::getOverload);
  }

  @Test
  void testResolution_HasNoMismatch() {
    Resolution resolution = ArithmeticOperators.PLUS.resolve(List.of(NUMBER, NUMBER));
    assertThrows(IllegalStateException.class, resolution::getMismatch);
  }

  @Test
  void testCatalogue_FindsABuiltInByName() {
    assertSame(ConditionOperators.IS_NOT_EMPTY, BuiltIns.INSTANCE.find("is not empty").orElseThrow());
    assertTrue(BuiltIns.INSTANCE.find("like").isEmpty());
  }

  private static Resolution resolve(final BuiltIn builtIn, final TypedExpression... arguments) {
    Resolution resolution = builtIn.resolve(List.of(arguments));
    assertTrue(resolution.isResolved(), () -> resolution.getMismatch().toString());
    return resolution;
  }

  private static BooleanExpression composed(final BuiltIn builtIn,
      final TypedExpression... arguments) {
    return TypedExpression.from(resolve(builtIn, arguments).compose(SCRIPT), BooleanExpression.class);
  }

  private static Mismatch mismatch(final BuiltIn builtIn, final TypedExpression... arguments) {
    Resolution resolution = builtIn.resolve(List.of(arguments));
    assertFalse(resolution.isResolved());
    return resolution.getMismatch();
  }

  private static List<Integer> positions(final Mismatch mismatch) {
    return mismatch.getRejections().stream().map(Mismatch.Rejection::getPosition)
        .collect(Collectors.toList());
  }
}
