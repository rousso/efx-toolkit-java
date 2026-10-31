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

import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;

import eu.europa.ted.efx.exceptions.TypeMismatchException;
import eu.europa.ted.efx.sdk2.EfxParser.AttributeReferenceContext;
import eu.europa.ted.efx.sdk2.EfxParser.DictionaryLookupContext;
import eu.europa.ted.efx.sdk2.EfxParser.FieldReferenceContext;
import eu.europa.ted.efx.sdk2.EfxParser.FunctionInvocationContext;
import eu.europa.ted.efx.sdk2.EfxParser.IndexerContext;
import eu.europa.ted.efx.sdk2.EfxParser.LateBoundExpressionContext;
import eu.europa.ted.efx.sdk2.EfxParser.LateBoundParenthesizedScalarContext;
import eu.europa.ted.efx.sdk2.EfxParser.LateBoundParenthesizedSequenceContext;
import eu.europa.ted.efx.sdk2.EfxParser.LateBoundScalarReferenceContext;
import eu.europa.ted.efx.sdk2.EfxParser.LateBoundSequenceReferenceContext;
import eu.europa.ted.efx.sdk2.EfxParser.PredicateContext;
import eu.europa.ted.efx.sdk2.EfxParser.RawValueReferenceContext;
import eu.europa.ted.efx.sdk2.EfxParser.VariableReferenceContext;

/**
 * The reference an operand is written as: a field, attribute or raw value reference, a variable, a
 * function invocation or a dictionary lookup, read through parentheses and through the grammar
 * rules that only wrap it. When the value of an operand is a sequence where a single value is
 * required, the error names that reference. An operand written otherwise, such as a list, a cast
 * or the result of an operator, has no origin, and its error is the generic one.
 *
 * <p>The reading is a double dispatch on the parse tree: the operand's node calls the method of
 * its grammar rule, a wrapper passes the reading on to the node it wraps, and a reference ends
 * it.</p>
 */
final class OperandOrigin extends EfxBaseListener {

  private final EfxExpressionTranslatorV2 translator;
  private TypeMismatchException sequenceError;

  private OperandOrigin(final EfxExpressionTranslatorV2 translator) {
    this.translator = translator;
  }

  /**
   * Gives the error for an operand whose value is a sequence where a single value is required.
   *
   * @param operand the operand, as the parser read it
   * @param translator the translator of the operand, which knows the fields it may name
   * @return the error that names the reference the operand is written as, or nothing if the
   *         operand is not written as a reference
   */
  static Optional<TypeMismatchException> sequenceErrorOf(final ParserRuleContext operand,
      final EfxExpressionTranslatorV2 translator) {
    final OperandOrigin origin = new OperandOrigin(translator);
    operand.enterRule(origin);
    return Optional.ofNullable(origin.sequenceError);
  }

  // #region Wrappers ---------------------------------------------------------

  @Override
  public void enterLateBoundParenthesizedScalar(final LateBoundParenthesizedScalarContext ctx) {
    ctx.lateBoundExpression().enterRule(this);
  }

  @Override
  public void enterLateBoundParenthesizedSequence(final LateBoundParenthesizedSequenceContext ctx) {
    ctx.lateBoundExpression().enterRule(this);
  }

  @Override
  public void enterLateBoundExpression(final LateBoundExpressionContext ctx) {
    this.readOnlyChildOf(ctx);
  }

  @Override
  public void enterPredicate(final PredicateContext ctx) {
    this.readOnlyChildOf(ctx);
  }

  @Override
  public void enterIndexer(final IndexerContext ctx) {
    this.readOnlyChildOf(ctx);
  }

  /**
   * A late-bound reference holds the one reference of its alternative.
   */
  @Override
  public void enterLateBoundScalarReference(final LateBoundScalarReferenceContext ctx) {
    this.readOnlyChildOf(ctx.lateBoundScalarFromReference());
  }

  /**
   * A late-bound reference holds the one reference of its alternative.
   */
  @Override
  public void enterLateBoundSequenceReference(final LateBoundSequenceReferenceContext ctx) {
    this.readOnlyChildOf(ctx.lateBoundSequenceFromReference());
  }

  private void readOnlyChildOf(final ParserRuleContext wrapper) {
    wrapper.getRuleContext(ParserRuleContext.class, 0).enterRule(this);
  }

  // #endregion Wrappers ------------------------------------------------------

  // #region References -------------------------------------------------------

  @Override
  public void enterFieldReference(final FieldReferenceContext ctx) {
    this.sequenceError =
        this.translator.fieldMayReturnMultipleValuesException(ctx, this.translator.getFieldId(ctx));
  }

  @Override
  public void enterAttributeReference(final AttributeReferenceContext ctx) {
    this.sequenceError = this.translator.fieldMayReturnMultipleValuesException(ctx,
        this.translator.getFieldId(ctx.fieldReference()));
  }

  @Override
  public void enterRawValueReference(final RawValueReferenceContext ctx) {
    this.sequenceError = this.translator.fieldMayReturnMultipleValuesException(ctx,
        this.translator.getFieldId(ctx.fieldContext()));
  }

  @Override
  public void enterVariableReference(final VariableReferenceContext ctx) {
    this.sequenceError = TypeMismatchException.identifierIsSequence(ctx, ctx.variableName.getText());
  }

  @Override
  public void enterFunctionInvocation(final FunctionInvocationContext ctx) {
    this.sequenceError = TypeMismatchException.identifierIsSequence(ctx, ctx.functionName.getText());
  }

  @Override
  public void enterDictionaryLookup(final DictionaryLookupContext ctx) {
    this.sequenceError =
        TypeMismatchException.dictionaryIsSequence(ctx, ctx.dictionaryName.getText());
  }

  // #endregion References ----------------------------------------------------
}
