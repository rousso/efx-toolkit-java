/*
 * Copyright 2022 European Union
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import eu.europa.ted.eforms.sdk.component.SdkComponent;
import eu.europa.ted.eforms.sdk.component.SdkComponentType;
import eu.europa.ted.efx.exceptions.InvalidArgumentException;
import eu.europa.ted.efx.exceptions.InvalidIdentifierException;
import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.exceptions.SdkInconsistencyException;
import eu.europa.ted.efx.exceptions.SymbolResolutionException;
import eu.europa.ted.efx.exceptions.TypeMismatchException;
import eu.europa.ted.efx.exceptions.TranslatorConfigurationException;
import eu.europa.ted.efx.util.EfxRegexValidator;
import eu.europa.ted.efx.interfaces.EfxExpressionTranslator;
import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.interfaces.SymbolResolver;
import eu.europa.ted.efx.model.CallStack;
import eu.europa.ted.efx.model.Context;
import eu.europa.ted.efx.model.Context.FieldContext;
import eu.europa.ted.efx.model.Context.NodeContext;
import eu.europa.ted.efx.model.ContextStack;
import eu.europa.ted.efx.model.PrivacySetting;
import eu.europa.ted.efx.model.expressions.Expression;
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.iteration.IteratorExpression;
import eu.europa.ted.efx.model.expressions.iteration.IteratorListExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.DurationExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.scalar.ScalarExpression;
import eu.europa.ted.efx.model.expressions.scalar.ScalarPath;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringPath;
import eu.europa.ted.efx.model.expressions.scalar.TimeExpression;
import eu.europa.ted.efx.model.expressions.sequence.BooleanSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DateSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DurationSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.NumericSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.SequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.TimeSequenceExpression;
import eu.europa.ted.efx.model.signatures.ArithmeticOperators;
import eu.europa.ted.efx.model.signatures.BuiltIn;
import eu.europa.ted.efx.model.signatures.BuiltIns;
import eu.europa.ted.efx.model.signatures.ConditionOperators;
import eu.europa.ted.efx.model.signatures.ConversionFunctions;
import eu.europa.ted.efx.model.signatures.DateTimeFunctions;
import eu.europa.ted.efx.model.signatures.LogicalOperators;
import eu.europa.ted.efx.model.signatures.Match;
import eu.europa.ted.efx.model.signatures.Mismatch;
import eu.europa.ted.efx.model.signatures.Mismatch.Rejection;
import eu.europa.ted.efx.model.signatures.NumericFunctions;
import eu.europa.ted.efx.model.signatures.ParameterType;
import eu.europa.ted.efx.model.signatures.Parameters;
import eu.europa.ted.efx.model.signatures.PatternMatch;
import eu.europa.ted.efx.model.signatures.Resolution;
import eu.europa.ted.efx.model.signatures.SequenceFunctions;
import eu.europa.ted.efx.model.signatures.StringFunctions;
import eu.europa.ted.efx.model.signatures.TemplateFunctions;
import eu.europa.ted.efx.model.types.EfxDataType;
import eu.europa.ted.efx.model.types.EfxTypeLattice;
import eu.europa.ted.efx.model.types.FieldTypes;
import eu.europa.ted.efx.model.variables.ParsedArguments;
import eu.europa.ted.efx.model.variables.Function;
import eu.europa.ted.efx.model.variables.StrictArguments;
import eu.europa.ted.efx.model.variables.ParsedParameter;
import eu.europa.ted.efx.model.variables.Variable;
import eu.europa.ted.efx.model.variables.Parametrised;
import eu.europa.ted.efx.sdk2.EfxParser.*;

/**
 * The goal of the EfxExpressionTranslator is to take an EFX expression and translate it to a
 * target scripting language.
 * 
 * The target language syntax is not hardcoded into the translator so that this class can be reused
 * to translate to several different languages. Instead a {@link ScriptGenerator} interface is used
 * to provide specifics on the syntax of the target scripting language.
 * 
 * Apart from writing expressions that can be translated and evaluated in a target scripting
 * language (e.g. XPath/XQuery, JavaScript etc.), EFX also allows the definition of templates that
 * can be translated to a target template markup language (e.g. XSLT, Thymeleaf etc.). The
 * {@link EfxExpressionTranslatorV2} only focuses on EFX expressions. To translate EFX templates you
 * need to use the {@link EfxTemplateTranslatorV2} which derives from this class.
 */
@SdkComponent(versions = {"2"}, componentType = SdkComponentType.EFX_EXPRESSION_TRANSLATOR)
public class EfxExpressionTranslatorV2 extends EfxBaseListener
    implements EfxExpressionTranslator {

  private static final String BEGIN_EXPRESSION_BLOCK = "{";
  private static final String END_EXPRESSION_BLOCK = "}";

  /**
   * The stack is used by the methods of this listener to pass data to each other as the parse tree
   * is being walked.
   */
  protected CallStack stack = new CallStack();

  /**
   * The context stack is used to keep track of context switching in nested expressions.
   */
  protected ContextStack efxContext;

  /**
   * Symbols are the field identifiers and node identifiers. The symbols map is used to resolve them
   * to their location in the data source (typically their XPath).
   */
  protected SymbolResolver symbols;

  protected BaseErrorListener errorListener;

  /**
   * The ScriptGenerator is called to determine the target language syntax whenever needed.
   */
  protected ScriptGenerator script;

  private LinkedList<String> expressionArguments = new LinkedList<>();

  protected EfxExpressionTranslatorV2() {}

  public EfxExpressionTranslatorV2(final SymbolResolver symbolResolver,
      final ScriptGenerator scriptGenerator, final BaseErrorListener errorListener) {
    this.symbols = symbolResolver;
    this.script = scriptGenerator;
    this.errorListener = errorListener;

    this.efxContext = new ContextStack(symbols);
  }

  @Override
  public void enterEveryRule(final ParserRuleContext ctx) {
    this.stack.pushParserContext(ctx);
  }

  @Override
  public void exitEveryRule(final ParserRuleContext ctx) {
    this.stack.popParserContext();
  }

  @Override
  public String translateExpression(final String expression, final String... arguments) {
    this.expressionArguments.addAll(Arrays.asList(arguments));

    final EfxLexer lexer = new EfxLexer(CharStreams.fromString(expression));
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final EfxParser parser = new EfxParser(tokens);
    parser.setErrorHandler(new EfxErrorStrategy());

    if (errorListener != null) {
      lexer.removeErrorListeners();
      lexer.addErrorListener(errorListener);
      parser.removeErrorListeners();
      parser.addErrorListener(errorListener);
    }

    final ParseTree tree = parser.singleExpression();
    final ParseTreeWalker walker = new ParseTreeWalker();

    walker.walk(this, tree);

    return getTranslatedScript();
  }

  private <T extends Expression> T translateArgument(final String parameterValue,
      final Class<T> parameterType) {
    final EfxExpressionTranslatorV2 translator =
        new EfxExpressionTranslatorV2(this.symbols, this.script,
            this.errorListener);

    final EfxLexer lexer =
        new EfxLexer(
            CharStreams.fromString(BEGIN_EXPRESSION_BLOCK + parameterValue + END_EXPRESSION_BLOCK));
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final EfxParser parser = new EfxParser(tokens);
    parser.setErrorHandler(new EfxErrorStrategy());

    if (errorListener != null) {
      lexer.removeErrorListeners();
      lexer.addErrorListener(errorListener);
      parser.removeErrorListeners();
      parser.addErrorListener(errorListener);
    }

    final ParseTree tree = parser.parameterValue();
    final ParseTreeWalker walker = new ParseTreeWalker();

    walker.walk(translator, tree);

    return Expression.instantiate(translator.getTranslatedScript(), parameterType);
  }

  /**
   * Used to get the translated target language script, after the walker finished its walk.
   *
   * @return The translated code, trimmed
   */
  private String getTranslatedScript() {
    final StringBuilder sb = new StringBuilder(this.stack.size() * 100);
    while (!this.stack.empty()) {
      sb.insert(0, '\n').insert(0, this.stack.pop(Expression.class).getScript());
    }
    return sb.toString().trim();
  }

  private String getLinkedFieldId(String baseFieldId, LinkedFieldPropertyContext ctx) {
    if (ctx.PublicationDate() != null)
      return this.symbols.getPrivacySettingOfField(baseFieldId, PrivacySetting.PUBLICATION_DATE_FIELD);
    else if (ctx.JustificationCode() != null)
      return this.symbols.getPrivacySettingOfField(baseFieldId, PrivacySetting.JUSTIFICATION_CODE_FIELD);
    else if (ctx.JustificationDescription() != null)
      return this.symbols.getPrivacySettingOfField(baseFieldId, PrivacySetting.JUSTIFICATION_DESCRIPTION_FIELD);
    else
      throw TranslatorConfigurationException.unhandledLinkedFieldProperty(ctx.getText());
  }

  protected String getFieldId(LinkedFieldReferenceContext ctx) {
    if (ctx == null) {
      return null;
    }
    String baseFieldId = ctx.simpleFieldReference().fieldId.getText();
    if (ctx.linkedFieldProperty() == null) {
      return baseFieldId;
    }
    return this.getLinkedFieldId(baseFieldId, ctx.linkedFieldProperty());
  }

  protected String getFieldId(FieldMentionContext ctx) {
    if (ctx == null) {
      return null;
    }
    String baseFieldId = ctx.fieldId.getText();
    if (ctx.linkedFieldProperty() == null) {
      return baseFieldId;
    }
    return this.getLinkedFieldId(baseFieldId, ctx.linkedFieldProperty());
  }

  protected String getFieldId(FieldReferenceContext ctx) {
    if (ctx == null) {
      return null;
    }

    if (ctx.absoluteFieldReference() != null) {
      return getFieldId(ctx.absoluteFieldReference());
    }

    if (ctx.fieldReferenceWithVariableContextOverride() != null) {
      return getFieldId(ctx.fieldReferenceWithVariableContextOverride());
    }
    assert false : "Unexpected context type for field reference: " + ctx.getClass().getSimpleName();
    return null;
  }

  protected String getFieldId(AbsoluteFieldReferenceContext ctx) {
    if (ctx == null) {
      return null;
    }
    return getFieldId(ctx.reference.reference);
  }

    protected String getFieldId(FieldReferenceWithVariableContextOverrideContext ctx) {
    if (ctx == null) {
      return null;
    }
    return getFieldId(ctx.reference.reference.reference.reference);
  }

  protected String getFieldId(FieldContextContext ctx) {
    if (ctx == null) {
      return null;
    }

    if (ctx.absoluteFieldReference() != null) {
      return getFieldId(ctx.absoluteFieldReference());
    }

    if (ctx.fieldReferenceWithPredicate() != null) {
      return getFieldId(ctx.fieldReferenceWithPredicate());
    }

    assert false : "Unexpected context type for field reference: " + ctx.getClass().getSimpleName();
    return null;
  }

  protected String getFieldId(FieldReferenceWithPredicateContext ctx) {
    if (ctx == null) {
      return null;
    }
    return getFieldId(ctx.linkedFieldReference());
  }

  protected static String getNodeId(NodeReferenceContext ctx) {
    if (ctx == null) {
      return null;
    }

    if (ctx.absoluteNodeReference() != null) {
      return getNodeId(ctx.absoluteNodeReference().nodeReferenceWithPredicate());
    }

    if (ctx.nodeReferenceWithPredicate() != null) {
      return getNodeId(ctx.nodeReferenceWithPredicate());
    }

    assert false : "Unexpected context type for node reference: " + ctx.getClass().getSimpleName();
    return null;
  }

  protected static String getNodeId(NodeContextContext ctx) {
    if (ctx == null) {
      return null;
    }

    if (ctx.absoluteNodeReference() != null) {
      return getNodeId(ctx.absoluteNodeReference().nodeReferenceWithPredicate());
    }

    if (ctx.nodeReferenceWithPredicate() != null) {
      return getNodeId(ctx.nodeReferenceWithPredicate());
    }

    assert false : "Unexpected context type for node reference: " + ctx.getClass().getSimpleName();
    return null;
  }

  protected static String getNodeId(NodeReferenceWithPredicateContext ctx) {
    if (ctx == null) {
      return null;
    }
    return ctx.simpleNodeReference().NodeId().getText();
  }

  @Override
  public void enterSingleExpression(SingleExpressionContext ctx) {
    final TerminalNode fieldContext = ctx.FieldId();
    if (fieldContext != null) {
      this.efxContext.pushFieldContext(fieldContext.getText());
    } else {
      final TerminalNode nodeContext = ctx.NodeId();
      if (nodeContext != null) {
        this.efxContext.pushNodeContext(nodeContext.getText());
      } else {
        final TerminalNode alias = ctx.Identifier();
        if (alias != null) {
          String fieldId = this.symbols.getFieldIdFromAlias(alias.getText());
          if (fieldId != null) {
            this.efxContext.pushFieldContext(fieldId);
          } else {
            String nodeId = this.symbols.getNodeIdFromAlias(alias.getText());
            if (nodeId != null) {
              this.efxContext.pushNodeContext(nodeId);
            } else {
              throw SymbolResolutionException.unknownSymbol(alias.getText());
            }
          }
        }
      }
    }
  }

  @Override
  public void exitSingleExpression(SingleExpressionContext ctx) {
    this.efxContext.pop();
  }

  // #region Boolean expressions ----------------------------------------------

  @Override
  public void exitParenthesizedBooleanExpression(
      ParenthesizedBooleanExpressionContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(BooleanExpression.class), BooleanExpression.class));
  }

  @Override
  public void exitLogicalAndCondition(LogicalAndConditionContext ctx) {
    this.exitOperation(ctx, LogicalOperators.AND);
  }

  @Override
  public void exitLateBoundLogicalAndConditionRight(LateBoundLogicalAndConditionRightContext ctx) {
    this.exitOperation(ctx, LogicalOperators.AND);
  }

  @Override
  public void exitLateBoundLogicalAndConditionLeft(LateBoundLogicalAndConditionLeftContext ctx) {
    this.exitOperation(ctx, LogicalOperators.AND);
  }

  @Override
  public void exitLateBoundLogicalAndCondition(LateBoundLogicalAndConditionContext ctx) {
    this.exitOperation(ctx, LogicalOperators.AND);
  }

  @Override
  public void exitLogicalOrCondition(LogicalOrConditionContext ctx) {
    this.exitOperation(ctx, LogicalOperators.OR);
  }

  @Override
  public void exitLateBoundLogicalOrConditionRight(LateBoundLogicalOrConditionRightContext ctx) {
    this.exitOperation(ctx, LogicalOperators.OR);
  }

  @Override
  public void exitLateBoundLogicalOrConditionLeft(LateBoundLogicalOrConditionLeftContext ctx) {
    this.exitOperation(ctx, LogicalOperators.OR);
  }

  @Override
  public void exitLateBoundLogicalOrCondition(LateBoundLogicalOrConditionContext ctx) {
    this.exitOperation(ctx, LogicalOperators.OR);
  }

  // #region Boolean expressions - Comparisons --------------------------------

  @Override
  public void exitStringComparison(StringComparisonContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundStringComparisonLeft(LateBoundStringComparisonLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundStringComparisonRight(LateBoundStringComparisonRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitNumericComparison(NumericComparisonContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundNumericComparisonLeft(LateBoundNumericComparisonLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundNumericComparisonRight(LateBoundNumericComparisonRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitBooleanComparison(BooleanComparisonContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundBooleanComparisonLeft(LateBoundBooleanComparisonLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundBooleanComparisonRight(LateBoundBooleanComparisonRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitDateComparison(DateComparisonContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundDateComparisonLeft(LateBoundDateComparisonLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundDateComparisonRight(LateBoundDateComparisonRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitTimeComparison(TimeComparisonContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundTimeComparisonLeft(LateBoundTimeComparisonLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundTimeComparisonRight(LateBoundTimeComparisonRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitDurationComparison(DurationComparisonContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundDurationComparisonLeft(LateBoundDurationComparisonLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundDurationComparisonRight(LateBoundDurationComparisonRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundComparison(LateBoundComparisonContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  // #endregion Boolean expressions - Comparisons -----------------------------

  // #region Boolean expressions - Conditions --------------------------------

  @Override
  public void exitStringEmptyFunction(StringEmptyFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.EMPTY);
  }

  @Override
  public void exitPresenceCondition(PresenceConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_PRESENT : ConditionOperators.IS_NOT_PRESENT);
  }

  @Override
  public void exitStringUniqueValueCondition(StringUniqueValueConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundStringUniqueValueConditionRight(LateBoundStringUniqueValueConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundStringUniqueValueConditionLeft(LateBoundStringUniqueValueConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitNumericUniqueValueCondition(NumericUniqueValueConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundNumericUniqueValueConditionRight(LateBoundNumericUniqueValueConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundNumericUniqueValueConditionLeft(LateBoundNumericUniqueValueConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitBooleanUniqueValueCondition(BooleanUniqueValueConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundBooleanUniqueValueConditionRight(LateBoundBooleanUniqueValueConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundBooleanUniqueValueConditionLeft(LateBoundBooleanUniqueValueConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitDateUniqueValueCondition(DateUniqueValueConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundDateUniqueValueConditionRight(LateBoundDateUniqueValueConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundDateUniqueValueConditionLeft(LateBoundDateUniqueValueConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitTimeUniqueValueCondition(TimeUniqueValueConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundTimeUniqueValueConditionRight(LateBoundTimeUniqueValueConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundTimeUniqueValueConditionLeft(LateBoundTimeUniqueValueConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitDurationUniqueValueCondition(DurationUniqueValueConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundDurationUniqueValueConditionRight(LateBoundDurationUniqueValueConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundDurationUniqueValueConditionLeft(LateBoundDurationUniqueValueConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitLateBoundUniqueValueCondition(LateBoundUniqueValueConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public void exitStringSequenceEmptinessCondition(
      StringSequenceEmptinessConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public void exitBooleanSequenceEmptinessCondition(
      BooleanSequenceEmptinessConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public void exitNumericSequenceEmptinessCondition(
      NumericSequenceEmptinessConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public void exitDateSequenceEmptinessCondition(
      DateSequenceEmptinessConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public void exitTimeSequenceEmptinessCondition(
      TimeSequenceEmptinessConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public void exitDurationSequenceEmptinessCondition(
      DurationSequenceEmptinessConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public void exitLateBoundEmptinessCondition(LateBoundEmptinessConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public void exitStringSequenceDistinctCondition(
      StringSequenceDistinctConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public void exitBooleanSequenceDistinctCondition(
      BooleanSequenceDistinctConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public void exitNumericSequenceDistinctCondition(
      NumericSequenceDistinctConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public void exitDateSequenceDistinctCondition(
      DateSequenceDistinctConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public void exitTimeSequenceDistinctCondition(
      TimeSequenceDistinctConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public void exitDurationSequenceDistinctCondition(
      DurationSequenceDistinctConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public void exitLateBoundDistinctCondition(LateBoundDistinctConditionContext ctx) {
    this.exitCall(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public void exitLikePatternCondition(LikePatternConditionContext ctx) {
    final String pattern = ctx.pattern.getText();
    this.exitCall(ctx, ctx.modifier == null ? PatternMatch.like(pattern) : PatternMatch.notLike(pattern));
  }

  @Override
  public void exitLateBoundLikePatternCondition(LateBoundLikePatternConditionContext ctx) {
    final String pattern = ctx.pattern.getText();
    this.exitCall(ctx, ctx.modifier == null ? PatternMatch.like(pattern) : PatternMatch.notLike(pattern));
  }

  // #endregion Boolean expressions - Conditions ------------------------------

  // #region Boolean expressions - List membership conditions -----------------

  @Override
  public void exitStringInListCondition(StringInListConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundStringInListConditionRight(LateBoundStringInListConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundStringInListConditionLeft(LateBoundStringInListConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitBooleanInListCondition(BooleanInListConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundBooleanInListConditionRight(LateBoundBooleanInListConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundBooleanInListConditionLeft(LateBoundBooleanInListConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitNumberInListCondition(NumberInListConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundNumberInListConditionRight(LateBoundNumberInListConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundNumberInListConditionLeft(LateBoundNumberInListConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitDateInListCondition(DateInListConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundDateInListConditionRight(LateBoundDateInListConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundDateInListConditionLeft(LateBoundDateInListConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitTimeInListCondition(TimeInListConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundTimeInListConditionRight(LateBoundTimeInListConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundTimeInListConditionLeft(LateBoundTimeInListConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitDurationInListCondition(DurationInListConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundDurationInListConditionRight(LateBoundDurationInListConditionRightContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundDurationInListConditionLeft(LateBoundDurationInListConditionLeftContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public void exitLateBoundInListCondition(LateBoundInListConditionContext ctx) {
    this.exitOperation(ctx, ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  // #endregion Boolean expressions - List membership conditions -----------------

  // #endregion Boolean expressions -------------------------------------------

  // #region Quantified expressions -------------------------------------------

  @Override
  public void enterQuantifiedExpression(QuantifiedExpressionContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void enterLateBoundQuantifiedExpression(LateBoundQuantifiedExpressionContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitQuantifiedExpression(QuantifiedExpressionContext ctx) {
    this.exitQuantifiedExpression(ctx.Every() != null, ctx.booleanExpression());
  }

  @Override
  public void exitLateBoundQuantifiedExpression(LateBoundQuantifiedExpressionContext ctx) {
    this.exitQuantifiedExpression(ctx.Every() != null, ctx.lateBoundScalar());
  }

  private void exitQuantifiedExpression(boolean isEvery, ParserRuleContext condition) {
    BooleanExpression booleanExpression = this.popOperand(condition, BooleanExpression.class);
    if (isEvery) {
      this.stack.push(this.script.composeAllSatisfy(this.stack.pop(IteratorListExpression.class),
          booleanExpression));
    } else {
      this.stack.push(this.script.composeAnySatisfies(this.stack.pop(IteratorListExpression.class),
          booleanExpression));
    }
    this.stack.popStackFrame();
  }

  // #endregion Quantified expressions ----------------------------------------

  // #region Numeric expressions ----------------------------------------------

  @Override
  public void exitAdditiveExpression(AdditiveExpressionContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundAdditiveExpressionLeft(LateBoundAdditiveExpressionLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundAdditiveExpressionRight(LateBoundAdditiveExpressionRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundAdditiveExpression(LateBoundAdditiveExpressionContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitMultiplicativeExpression(MultiplicativeExpressionContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundMultiplicativeExpressionLeft(LateBoundMultiplicativeExpressionLeftContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundMultiplicativeExpressionRight(LateBoundMultiplicativeExpressionRightContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitLateBoundMultiplicativeExpression(LateBoundMultiplicativeExpressionContext ctx) {
    this.exitOperation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public void exitParenthesizedNumericExpression(ParenthesizedNumericExpressionContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(NumericExpression.class), NumericExpression.class));
  }

  // #endregion Numeric expressions -------------------------------------------

  // #region Duration Expressions ---------------------------------------------

  @Override
  public void exitDurationAdditionExpression(DurationAdditionExpressionContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.PLUS);
  }

  @Override
  public void exitLateBoundDurationAdditionExpressionLeft(LateBoundDurationAdditionExpressionLeftContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.PLUS);
  }

  @Override
  public void exitLateBoundDurationAdditionExpressionRight(LateBoundDurationAdditionExpressionRightContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.PLUS);
  }

  @Override
  public void exitDurationSubtractionExpression(DurationSubtractionExpressionContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MINUS);
  }

  @Override
  public void exitLateBoundDurationSubtractionExpressionLeft(LateBoundDurationSubtractionExpressionLeftContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MINUS);
  }

  @Override
  public void exitLateBoundDurationSubtractionExpressionRight(LateBoundDurationSubtractionExpressionRightContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MINUS);
  }

  @Override
  public void exitDurationMultiplicationExpressionLeft(DurationMultiplicationExpressionLeftContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MULTIPLY);
  }

  @Override
  public void exitLateBoundDurationMultiplicationExpressionLeft(LateBoundDurationMultiplicationExpressionLeftContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MULTIPLY);
  }

  @Override
  public void exitDurationMultiplicationExpressionRight(DurationMultiplicationExpressionRightContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MULTIPLY);
  }

  @Override
  public void exitLateBoundDurationMultiplicationExpressionRight(LateBoundDurationMultiplicationExpressionRightContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MULTIPLY);
  }

  @Override
  public void exitLateBoundReversedDurationMultiplicationExpression(LateBoundReversedDurationMultiplicationExpressionContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MULTIPLY);
  }

  @Override
  public void exitDateSubtractionExpression(DateSubtractionExpressionContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MINUS);
  }

  @Override
  public void exitLateBoundDateSubtractionExpressionLeft(LateBoundDateSubtractionExpressionLeftContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MINUS);
  }

  @Override
  public void exitLateBoundDateSubtractionExpressionRight(LateBoundDateSubtractionExpressionRightContext ctx) {
    this.exitOperation(ctx, ArithmeticOperators.MINUS);
  }

  // #endregion Duration Expressions ------------------------------------------

  // #region List Expressions -------------------------------------------------

  @Override
  public void exitCodeList(CodeListContext ctx) {
    if (this.stack.empty()) {
      this.stack.push(this.script.composeList(Collections.emptyList(), StringSequenceExpression.class));
    }
  }

  @Override
  public void exitStringList(StringListContext ctx) {
    this.exitList(ctx, StringExpression.class, StringSequenceExpression.class);
  }

  @Override
  public void exitBooleanList(BooleanListContext ctx) {
    this.exitList(ctx, BooleanExpression.class, BooleanSequenceExpression.class);
  }

  @Override
  public void exitNumericList(NumericListContext ctx) {
    this.exitList(ctx, NumericExpression.class, NumericSequenceExpression.class);
  }

  @Override
  public void exitDateList(DateListContext ctx) {
    this.exitList(ctx, DateExpression.class, DateSequenceExpression.class);
  }

  @Override
  public void exitTimeList(TimeListContext ctx) {
    this.exitList(ctx, TimeExpression.class, TimeSequenceExpression.class);
  }

  @Override
  public void exitDurationList(DurationListContext ctx) {
    this.exitList(ctx, DurationExpression.class, DurationSequenceExpression.class);
  }

  /**
   * Translates a list: each item is an operand that must be a single value of the list's primitive.
   */
  private <T extends ScalarExpression> void exitList(final ParserRuleContext list,
      final Class<T> itemType, final Class<? extends SequenceExpression> listType) {
    final List<ParserRuleContext> items = list.getRuleContexts(ParserRuleContext.class);
    final LinkedList<T> values = new LinkedList<>();
    for (int position = items.size() - 1; position >= 0; position--) {
      values.addFirst(this.popOperand(items.get(position), itemType));
    }
    this.stack.push(this.script.composeList(values, listType));
  }

  @Override
  public void exitLateBoundList(LateBoundListContext ctx) {
    final TypedExpression last = this.stack.peekType();
    this.exitList(ctx, this.scalarTypeOf(last), this.sequenceTypeOf(last));
  }

  // #endregion List Expressions ----------------------------------------------

  // #region Parenthesized Sequences ------------------------------------------

  @Override
  public void exitParenthesizedStrings(ParenthesizedStringsContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(StringSequenceExpression.class), StringSequenceExpression.class));
  }

  @Override
  public void exitParenthesizedNumbers(ParenthesizedNumbersContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(NumericSequenceExpression.class), NumericSequenceExpression.class));
  }

  @Override
  public void exitParenthesizedBooleans(ParenthesizedBooleansContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(BooleanSequenceExpression.class), BooleanSequenceExpression.class));
  }

  @Override
  public void exitParenthesizedDates(ParenthesizedDatesContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(DateSequenceExpression.class), DateSequenceExpression.class));
  }

  @Override
  public void exitParenthesizedTimes(ParenthesizedTimesContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(TimeSequenceExpression.class), TimeSequenceExpression.class));
  }

  @Override
  public void exitParenthesizedDurations(ParenthesizedDurationsContext ctx) {
    this.stack.push(this.script.composeParenthesizedExpression(
        this.stack.pop(DurationSequenceExpression.class), DurationSequenceExpression.class));
  }

  @Override
  public void exitLateBoundParenthesizedSequence(LateBoundParenthesizedSequenceContext ctx) {
    this.exitLateBoundParenthesizedExpression();
  }

  @Override
  public void exitLateBoundParenthesizedScalar(LateBoundParenthesizedScalarContext ctx) {
    this.exitLateBoundParenthesizedExpression();
  }

  private void exitLateBoundParenthesizedExpression() {
    TypedExpression inner = this.stack.peekType();
    if (inner instanceof SequenceExpression) {
      Class<? extends SequenceExpression> type = this.sequenceTypeOf(inner);
      this.stack.push(this.composeParenthesizedExpression(this.stack.pop(type), type));
    } else {
      Class<? extends ScalarExpression> type = this.scalarTypeOf(inner);
      this.stack.push(this.composeParenthesizedExpression(this.stack.pop(type), type));
    }
  }

  private <T extends ScalarExpression> T composeParenthesizedExpression(ScalarExpression inner, Class<T> type) {
    return this.script.composeParenthesizedExpression(Expression.from(inner, type), type);
  }

  private <T extends SequenceExpression> T composeParenthesizedExpression(SequenceExpression sequence, Class<T> sequenceType) {
    return this.script.composeParenthesizedExpression(Expression.from(sequence, sequenceType), sequenceType);
  }

  // #endregion Parenthesized Sequences ---------------------------------------

  // #region Conditional Expressions ------------------------------------------

  @Override
  public void exitConditionalBooleanExpression(ConditionalBooleanExpressionContext ctx) {
    this.exitConditionalExpression(ctx, BooleanExpression.class);
  }

  @Override
  public void exitLateBoundConditionalBooleanExpressionLeft(LateBoundConditionalBooleanExpressionLeftContext ctx) {
    this.exitConditionalExpression(ctx, BooleanExpression.class);
  }

  @Override
  public void exitLateBoundConditionalBooleanExpressionRight(LateBoundConditionalBooleanExpressionRightContext ctx) {
    this.exitConditionalExpression(ctx, BooleanExpression.class);
  }

  @Override
  public void exitConditionalNumericExpression(ConditionalNumericExpressionContext ctx) {
    this.exitConditionalExpression(ctx, NumericExpression.class);
  }

  @Override
  public void exitLateBoundConditionalNumericExpressionLeft(LateBoundConditionalNumericExpressionLeftContext ctx) {
    this.exitConditionalExpression(ctx, NumericExpression.class);
  }

  @Override
  public void exitLateBoundConditionalNumericExpressionRight(LateBoundConditionalNumericExpressionRightContext ctx) {
    this.exitConditionalExpression(ctx, NumericExpression.class);
  }

  @Override
  public void exitConditionalStringExpression(ConditionalStringExpressionContext ctx) {
    this.exitConditionalExpression(ctx, StringExpression.class);
  }

  @Override
  public void exitLateBoundConditionalStringExpressionLeft(LateBoundConditionalStringExpressionLeftContext ctx) {
    this.exitConditionalExpression(ctx, StringExpression.class);
  }

  @Override
  public void exitLateBoundConditionalStringExpressionRight(LateBoundConditionalStringExpressionRightContext ctx) {
    this.exitConditionalExpression(ctx, StringExpression.class);
  }

  @Override
  public void exitConditionalDateExpression(ConditionalDateExpressionContext ctx) {
    this.exitConditionalExpression(ctx, DateExpression.class);
  }

  @Override
  public void exitLateBoundConditionalDateExpressionLeft(LateBoundConditionalDateExpressionLeftContext ctx) {
    this.exitConditionalExpression(ctx, DateExpression.class);
  }

  @Override
  public void exitLateBoundConditionalDateExpressionRight(LateBoundConditionalDateExpressionRightContext ctx) {
    this.exitConditionalExpression(ctx, DateExpression.class);
  }

  @Override
  public void exitConditionalTimeExpression(ConditionalTimeExpressionContext ctx) {
    this.exitConditionalExpression(ctx, TimeExpression.class);
  }

  @Override
  public void exitLateBoundConditionalTimeExpressionLeft(LateBoundConditionalTimeExpressionLeftContext ctx) {
    this.exitConditionalExpression(ctx, TimeExpression.class);
  }

  @Override
  public void exitLateBoundConditionalTimeExpressionRight(LateBoundConditionalTimeExpressionRightContext ctx) {
    this.exitConditionalExpression(ctx, TimeExpression.class);
  }

  @Override
  public void exitConditionalDurationExpression(ConditionalDurationExpressionContext ctx) {
    this.exitConditionalExpression(ctx, DurationExpression.class);
  }

  @Override
  public void exitLateBoundConditionalDurationExpressionLeft(LateBoundConditionalDurationExpressionLeftContext ctx) {
    this.exitConditionalExpression(ctx, DurationExpression.class);
  }

  @Override
  public void exitLateBoundConditionalDurationExpressionRight(LateBoundConditionalDurationExpressionRightContext ctx) {
    this.exitConditionalExpression(ctx, DurationExpression.class);
  }

  @Override
  public void exitLateBoundConditionalExpression(LateBoundConditionalExpressionContext ctx) {
    this.exitConditionalExpression(ctx, this.scalarTypeOf(this.stack.peekType(-1)));
  }

  /**
   * Translates a conditional: its three child rules are the condition and the two branches, which
   * must be single values of the conditional's primitive.
   */
  private <T extends ScalarExpression> void exitConditionalExpression(
      final ParserRuleContext conditional, final Class<T> type) {
    final List<ParserRuleContext> operands = conditional.getRuleContexts(ParserRuleContext.class);
    final T whenFalse = this.popOperand(operands.get(2), type);
    final T whenTrue = this.popOperand(operands.get(1), type);
    final BooleanExpression condition = this.popOperand(operands.get(0), BooleanExpression.class);
    this.stack.push(this.script.composeConditionalExpression(condition, whenTrue, whenFalse, type));
  }

  // #endregion Conditional Expressions ---------------------------------------

  // #region Iterators --------------------------------------------------------

  @Override
  public void exitStringIteratorExpression(StringIteratorExpressionContext ctx) {
    this.exitIteratorExpression(ctx.stringIteratorVariableDeclaration().variableName.getText(), StringExpression.class, StringSequenceExpression.class);
  }

  @Override
  public void exitBooleanIteratorExpression(BooleanIteratorExpressionContext ctx) {
    this.exitIteratorExpression(ctx.booleanIteratorVariableDeclaration().variableName.getText(), BooleanExpression.class, BooleanSequenceExpression.class);
  }

  @Override
  public void exitNumericIteratorExpression(NumericIteratorExpressionContext ctx) {
    this.exitIteratorExpression(ctx.numericIteratorVariableDeclaration().variableName.getText(),  NumericExpression.class, NumericSequenceExpression.class);
  }

  @Override
  public void exitDateIteratorExpression(DateIteratorExpressionContext ctx) {
    this.exitIteratorExpression(ctx.dateIteratorVariableDeclaration().variableName.getText(), DateExpression.class, DateSequenceExpression.class);
  }

  @Override
  public void exitTimeIteratorExpression(TimeIteratorExpressionContext ctx) {
    this.exitIteratorExpression(ctx.timeIteratorVariableDeclaration().variableName.getText(), TimeExpression.class, TimeSequenceExpression.class);
  }

  @Override
  public void exitDurationIteratorExpression(DurationIteratorExpressionContext ctx) {
    this.exitIteratorExpression(ctx.durationIteratorVariableDeclaration().variableName.getText(), DurationExpression.class, DurationSequenceExpression.class);
  }

  @Override
  public void exitContextIteratorExpression(ContextIteratorExpressionContext ctx) {
    PathExpression path = this.stack.pop(PathExpression.class);

    var variableType = path.asScalar().getClass();
    var variableName = ctx.contextIteratorVariableDeclaration().variableName.getText();
    Variable variable = new Variable(variableName,
        this.script.composeVariableDeclaration(variableName, variableType),
        Expression.empty(variableType),
        this.script.composeVariableReference(variableName, variableType));
    this.stack.declareIdentifier(variable);

    this.stack.push(this.script.composeIteratorExpression(variable.declarationExpression, path.asSequence()));
    if (ctx.fieldContext() != null) {
      final String contextFieldId = getFieldId(ctx.fieldContext());
      this.efxContext.declareContextVariable(variable.name,
          new FieldContext(contextFieldId, this.symbols.getAbsolutePathOfField(contextFieldId),
              path));
    } else if (ctx.nodeContext() != null) {
      final String contextNodeId =
          getNodeId(ctx.nodeContext());
      this.efxContext.declareContextVariable(variable.name,
          new NodeContext(contextNodeId, this.symbols.getAbsolutePathOfNode(contextNodeId),
              this.symbols.getRelativePathOfNode(contextNodeId, this.efxContext.symbol())));
    }
  }

  @Override
  public void exitIteratorList(IteratorListContext ctx) {
    List<IteratorExpression> iterators = new ArrayList<>();
    for (int i = 0; i < ctx.iteratorExpression().size(); i++) {
      iterators.add(0, this.stack.pop(IteratorExpression.class));
    }
    this.stack.push(this.script.composeIteratorList(iterators));
  }

  private <T1 extends ScalarExpression, T2 extends SequenceExpression> void exitIteratorExpression(String variableName,
      Class<T1> variableType, Class<T2> listType) {
    Expression declarationExpression = this.script.composeVariableDeclaration(variableName, variableType);
    SequenceExpression initialisationExpression = this.stack.pop(listType);
    ScalarExpression referenceExpression = this.script.composeVariableReference(variableName, variableType);
    Variable variable = new Variable(variableName, declarationExpression, initialisationExpression,
        referenceExpression);
    this.stack.declareIdentifier(variable);
    this.stack.push(this.script.composeIteratorExpression(variable.declarationExpression, initialisationExpression));
  }

  // #endregion Iterators -----------------------------------------------------

  // #region Iteration Expressions ---------------------------------------------

  // for ... return <scalar> (map iterations)

  @Override
  public void enterStringSequenceFromIteration(StringSequenceFromIterationContext ctx) {
    this.stack.pushStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void exitStringSequenceFromIteration(StringSequenceFromIterationContext ctx) {
    this.exitSequenceFromIteration(StringExpression.class, StringSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void enterNumericSequenceFromIteration(NumericSequenceFromIterationContext ctx) {
    this.stack.pushStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void exitNumericSequenceFromIteration(NumericSequenceFromIterationContext ctx) {
    this.exitSequenceFromIteration(NumericExpression.class, NumericSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void enterBooleanSequenceFromIteration(BooleanSequenceFromIterationContext ctx) {
    this.stack.pushStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void exitBooleanSequenceFromIteration(BooleanSequenceFromIterationContext ctx) {
    this.exitSequenceFromIteration(BooleanExpression.class, BooleanSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void enterDateSequenceFromIteration(DateSequenceFromIterationContext ctx) {
    this.stack.pushStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void exitDateSequenceFromIteration(DateSequenceFromIterationContext ctx) {
    this.exitSequenceFromIteration(DateExpression.class, DateSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void enterTimeSequenceFromIteration(TimeSequenceFromIterationContext ctx) {
    this.stack.pushStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void exitTimeSequenceFromIteration(TimeSequenceFromIterationContext ctx) {
    this.exitSequenceFromIteration(TimeExpression.class, TimeSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void enterDurationSequenceFromIteration(DurationSequenceFromIterationContext ctx) {
    this.stack.pushStackFrame(); // Iteration variables are local to the iteration
  }

  @Override
  public void exitDurationSequenceFromIteration(DurationSequenceFromIterationContext ctx) {
    this.exitSequenceFromIteration(DurationExpression.class, DurationSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame(); // Iteration variables are local to the iteration
  }

  private <T extends ScalarExpression, L extends SequenceExpression> void exitSequenceFromIteration(
      Class<T> expressionType, Class<L> targetListType, boolean distinct) {
    T expression = this.stack.pop(expressionType);
    IteratorListExpression iterators = this.stack.pop(IteratorListExpression.class);
    L result = this.script.composeForExpression(iterators, expression, targetListType);
    if (distinct) {
      result = this.script.composeDistinctValuesFunction(result, targetListType);
    }
    this.stack.push(result);
  }

  // for ... return <sequence> (concatenated iterations, i.e. flatMap)

  @Override
  public void enterStringSequenceFromConcatenatedIterations(StringSequenceFromConcatenatedIterationsContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitStringSequenceFromConcatenatedIterations(StringSequenceFromConcatenatedIterationsContext ctx) {
    this.exitSequenceFromConcatenatedIterations(StringSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame();
  }

  @Override
  public void enterBooleanSequenceFromConcatenatedIterations(BooleanSequenceFromConcatenatedIterationsContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitBooleanSequenceFromConcatenatedIterations(BooleanSequenceFromConcatenatedIterationsContext ctx) {
    this.exitSequenceFromConcatenatedIterations(BooleanSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame();
  }

  @Override
  public void enterNumericSequenceFromConcatenatedIterations(NumericSequenceFromConcatenatedIterationsContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitNumericSequenceFromConcatenatedIterations(NumericSequenceFromConcatenatedIterationsContext ctx) {
    this.exitSequenceFromConcatenatedIterations(NumericSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame();
  }

  @Override
  public void enterDateSequenceFromConcatenatedIterations(DateSequenceFromConcatenatedIterationsContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitDateSequenceFromConcatenatedIterations(DateSequenceFromConcatenatedIterationsContext ctx) {
    this.exitSequenceFromConcatenatedIterations(DateSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame();
  }

  @Override
  public void enterTimeSequenceFromConcatenatedIterations(TimeSequenceFromConcatenatedIterationsContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitTimeSequenceFromConcatenatedIterations(TimeSequenceFromConcatenatedIterationsContext ctx) {
    this.exitSequenceFromConcatenatedIterations(TimeSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame();
  }

  @Override
  public void enterDurationSequenceFromConcatenatedIterations(DurationSequenceFromConcatenatedIterationsContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitDurationSequenceFromConcatenatedIterations(DurationSequenceFromConcatenatedIterationsContext ctx) {
    this.exitSequenceFromConcatenatedIterations(DurationSequenceExpression.class, ctx.Distinct() != null);
    this.stack.popStackFrame();
  }

  private <T extends SequenceExpression> void exitSequenceFromConcatenatedIterations(Class<T> sequenceType, boolean distinct) {
    T sequenceExpression = this.stack.pop(sequenceType);
    IteratorListExpression iterators = this.stack.pop(IteratorListExpression.class);
    T result = this.script.composeForExpression(iterators, sequenceExpression, sequenceType);
    if (distinct) {
      result = this.script.composeDistinctValuesFunction(result, sequenceType);
    }
    this.stack.push(result);
  }

  // for ... return <late-bound> (late-bound iteration)

  @Override
  public void enterLateBoundSequenceFromIteration(LateBoundSequenceFromIterationContext ctx) {
    this.stack.pushStackFrame();
  }

  @Override
  public void exitLateBoundSequenceFromIteration(LateBoundSequenceFromIterationContext ctx) {
    TypedExpression body = this.stack.pop(TypedExpression.class);
    IteratorListExpression iterators = this.stack.pop(IteratorListExpression.class);

    this.composeForExpression(iterators, body, ctx.Distinct() != null, this.sequenceTypeOf(body));
    this.stack.popStackFrame();
  }

  private <T extends SequenceExpression> void composeForExpression(
      IteratorListExpression iterators, TypedExpression body, boolean distinct, Class<T> sequenceType) {
    T result = (body instanceof ScalarExpression)
        ? this.script.composeForExpression(iterators, (ScalarExpression) body, sequenceType)
        : this.script.composeForExpression(iterators, (SequenceExpression) body, sequenceType);
    if (distinct) {
      result = this.script.composeDistinctValuesFunction(result, sequenceType);
    }
    this.stack.push(result);
  }

  // #endregion Iteration Expressions ------------------------------------------

  // #region Literals ---------------------------------------------------------

  @Override
  public void exitNumericLiteral(NumericLiteralContext ctx) {
    this.stack.push(this.script.getNumericLiteralEquivalent(ctx.getText()));
  }

  @Override
  public void exitStringLiteral(StringLiteralContext ctx) {
    this.stack.push(this.script.getStringLiteralEquivalent(ctx.getText()));
  }

  @Override
  public void exitTrueBooleanLiteral(TrueBooleanLiteralContext ctx) {
    this.stack.push(this.script.getBooleanEquivalent(true));
  }

  @Override
  public void exitFalseBooleanLiteral(FalseBooleanLiteralContext ctx) {
    this.stack.push(this.script.getBooleanEquivalent(false));
  }

  @Override
  public void exitDateLiteral(DateLiteralContext ctx) {
    this.stack.push(this.script.getDateLiteralEquivalent(ctx.DateLiteral().getText()));
  }

  @Override
  public void exitTimeLiteral(TimeLiteralContext ctx) {
    this.stack.push(this.script.getTimeLiteralEquivalent(ctx.TimeLiteral().getText()));
  }

  @Override
  public void exitDurationLiteral(DurationLiteralContext ctx) {
    this.stack.push(this.script.getDurationLiteralEquivalent(ctx.getText()));
  }

  // Sequence literals for parameter values
  @Override
  public void exitStringSequenceLiteral(StringSequenceLiteralContext ctx) {
    int count = ctx.stringLiteral().size();
    List<StringExpression> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(0, this.stack.pop(StringExpression.class));
    }
    this.stack.push(this.script.composeList(items, StringSequenceExpression.class));
  }

  @Override
  public void exitNumericSequenceLiteral(NumericSequenceLiteralContext ctx) {
    int count = ctx.numericLiteral().size();
    List<NumericExpression> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(0, this.stack.pop(NumericExpression.class));
    }
    this.stack.push(this.script.composeList(items, NumericSequenceExpression.class));
  }

  @Override
  public void exitBooleanSequenceLiteral(BooleanSequenceLiteralContext ctx) {
    int count = ctx.booleanLiteral().size();
    List<BooleanExpression> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(0, this.stack.pop(BooleanExpression.class));
    }
    this.stack.push(this.script.composeList(items, BooleanSequenceExpression.class));
  }

  @Override
  public void exitDateSequenceLiteral(DateSequenceLiteralContext ctx) {
    int count = ctx.dateLiteral().size();
    List<DateExpression> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(0, this.stack.pop(DateExpression.class));
    }
    this.stack.push(this.script.composeList(items, DateSequenceExpression.class));
  }

  @Override
  public void exitTimeSequenceLiteral(TimeSequenceLiteralContext ctx) {
    int count = ctx.timeLiteral().size();
    List<TimeExpression> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(0, this.stack.pop(TimeExpression.class));
    }
    this.stack.push(this.script.composeList(items, TimeSequenceExpression.class));
  }

  @Override
  public void exitDurationSequenceLiteral(DurationSequenceLiteralContext ctx) {
    int count = ctx.durationLiteral().size();
    List<DurationExpression> items = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      items.add(0, this.stack.pop(DurationExpression.class));
    }
    this.stack.push(this.script.composeList(items, DurationSequenceExpression.class));
  }

  // #endregion Literals ------------------------------------------------------

  // #region References -------------------------------------------------------

  @Override
  public void exitSimpleNodeReference(SimpleNodeReferenceContext ctx) {
    this.stack.push(
        this.symbols.getRelativePathOfNode(ctx.NodeId().getText(), this.efxContext.symbol()));
  }

  @Override
  public void exitSimpleFieldReference(SimpleFieldReferenceContext ctx) {
    this.stack.push(
        symbols.getRelativePathOfField(ctx.fieldId.getText(), this.efxContext.symbol()));
  }

  @Override
  public void exitLinkedFieldReference(LinkedFieldReferenceContext ctx) {
    if (ctx.linkedFieldProperty() != null) {
      this.stack.pop(PathExpression.class); // discard base field path
      String companionFieldId = getFieldId(ctx);
      this.stack.push(
          symbols.getRelativePathOfField(companionFieldId, this.efxContext.symbol()));
    }
  }

  @Override
  public void enterAbsoluteFieldReference(AbsoluteFieldReferenceContext ctx) {
    if (ctx.Slash() != null) {
      this.efxContext.push(null);
    }
  }

  @Override
  public void exitAbsoluteFieldReference(AbsoluteFieldReferenceContext ctx) {
    if (ctx.Slash() != null) {
      this.efxContext.pop();
    }
  }

  @Override
  public void exitFieldContext(FieldContextContext ctx) {
    if (ctx.indexer() != null) {
      NumericExpression index = this.popOperand(ctx.indexer(), NumericExpression.class);
      final TypedExpression top = this.stack.peekType();
      if (!(top instanceof PathExpression)) {
        throw TypeMismatchException.cannotConvert(ctx, PathExpression.class, top.getClass());
      }
      PathExpression fieldPath = (PathExpression) this.stack.pop(top.getClass());
      this.stack.push(this.script.composeIndexer(fieldPath.asSequence(), index,
          ScalarPath.fromEfxDataType.get(EfxTypeLattice.toPrimitive(fieldPath.getDataType()))));
    }
  }

  @Override
  public void enterAbsoluteNodeReference(AbsoluteNodeReferenceContext ctx) {
    if (ctx.Slash() != null) {
      this.efxContext.push(null);
    }
  }

  @Override
  public void exitAbsoluteNodeReference(AbsoluteNodeReferenceContext ctx) {
    if (ctx.Slash() != null) {
      this.efxContext.pop();
    }
  }


  // #region References with Predicates ---------------------------------------

  @Override
  public void exitNodeReferenceWithPredicate(NodeReferenceWithPredicateContext ctx) {
    if (ctx.predicate() != null) {
      BooleanExpression predicate = this.stack.pop(BooleanExpression.class);
      PathExpression nodeReference = this.stack.pop(PathExpression.class);
      this.stack.push(this.script.composeNodeReferenceWithPredicate(nodeReference, predicate));
    }
  }

  @Override
  public void exitFieldReferenceWithPredicate(FieldReferenceWithPredicateContext ctx) {
    if (ctx.predicate() != null) {
      BooleanExpression predicate = this.stack.pop(BooleanExpression.class);
      PathExpression fieldReference = this.stack.pop(PathExpression.class);
      this.stack.push(this.script.composeFieldReferenceWithPredicate(fieldReference, predicate));
    }
  }

  /**
   * Any field references in the predicate must be resolved relative to the node or field on which
   * the predicate is applied. Therefore we need to switch to that context while the predicate is
   * being parsed.
   * 
   * @param ctx The predicate context
   */
  @Override
  public void enterPredicate(PredicateContext ctx) {
    var parent = ctx.getParent();
    if (parent instanceof NodeReferenceWithPredicateContext) {
      final String nodeId = getNodeId((NodeReferenceWithPredicateContext) parent);
      this.efxContext.pushNodeContext(nodeId);
    } else if (parent instanceof FieldReferenceWithPredicateContext) {
      final String fieldId = getFieldId((FieldReferenceWithPredicateContext) parent);
      this.efxContext.pushFieldContext(fieldId);
    } else {
      throw TranslatorConfigurationException.unhandledPredicateContext(parent.getClass().getSimpleName());
    }
  }

  /**
   * After the predicate is parsed, its value is taken as the condition it is, and we switch back to
   * the previous context. The value is taken first, so that a field of the predicate that may
   * repeat is reported with the context the predicate reads it from.
   */
  @Override
  public void exitPredicate(PredicateContext ctx) {
    this.stack.push(this.popOperand(ctx, BooleanExpression.class));
    this.efxContext.pop();
  }

  // #endregion References with Predicates ------------------------------------

  // #region Value References -------------------------------------------------

  @Override
  public void exitScalarFromFieldReference(ScalarFromFieldReferenceContext ctx) {
    PathExpression path = this.stack.pop(PathExpression.class);
    this.stack.push(this.composeFieldValueReference(getFieldId(ctx.fieldReference()), path));
  }

  @Override
  public void exitSequenceFromFieldReference(SequenceFromFieldReferenceContext ctx) {
    PathExpression path = this.stack.pop(PathExpression.class);
    this.stack.push(this.composeFieldValueReference(getFieldId(ctx.fieldReference()), path));
  }

  /**
   * Returns the reference to the value of a field, given the path of the field. The value of an attribute
   * field is its attribute, on the element of the field. The value has the cardinality of the path.
   */
  protected PathExpression composeFieldValueReference(final String fieldId, final PathExpression path) {
    if (!this.symbols.isAttributeField(fieldId)) {
      return this.script.composeFieldValueReference(path);
    }
    final PathExpression attribute = this.script.composeFieldAttributeReference(
        this.script.contextualizePath(
            this.symbols.getAbsolutePathOfFieldWithoutTheAttribute(fieldId), this.efxContext.peek().absolutePath()),
        this.symbols.getAttributeNameFromAttributeField(fieldId),
        ScalarPath.fromFieldType.get(FieldTypes.fromString(this.symbols.getTypeOfField(fieldId))));
    return continuing(path, attribute);
  }

  /**
   * Types a path that continues from the nodes another path selects: it may select several nodes if the
   * other may, and otherwise as many as it selects from a single node.
   *
   * @param start the path whose nodes the continuation starts from
   * @param continuation the continuing path, typed as it is from a single node
   * @return the continuation, as a sequence if the start is one
   */
  private static PathExpression continuing(final PathExpression start, final PathExpression continuation) {
    return start instanceof SequenceExpression ? continuation.asSequence() : continuation;
  }

  /**
   * A selector yields the reference itself rather than its value: the value step that every other
   * reference position applies is deliberately not composed here. The path is otherwise resolved
   * exactly as it would be in an expression, relative to the declared context unless the author
   * wrote it as an absolute reference.
   *
   * <p>The reference tier has already left the path on the stack. Only an attribute reference
   * needs work, because {@code attributeReference} has no exit handler of its own; composing the
   * attribute step globally would double-compose it for the scalar and sequence positions.
   */
  @Override
  public void exitSelection(final SelectionContext ctx) {
    if (ctx.attributeReference() != null) {
      this.stack.push(this.script.composeFieldAttributeReference(
          this.stack.pop(PathExpression.class),
          ctx.attributeReference().attributeName.getText(), StringPath.class));
    }
  }

  @Override
  public void exitScalarFromAttributeReference(ScalarFromAttributeReferenceContext ctx) {
    this.pushAttributeValueReference(ctx.attributeReference());
  }

  @Override
  public void exitSequenceFromAttributeReference(SequenceFromAttributeReferenceContext ctx) {
    this.pushAttributeValueReference(ctx.attributeReference());
  }

  /**
   * Pushes the value of an attribute written after a field reference. The attribute is on each element
   * the field reference selects.
   */
  private void pushAttributeValueReference(AttributeReferenceContext ctx) {
    final PathExpression field = this.stack.pop(PathExpression.class);
    final PathExpression attribute =
        this.script.composeFieldAttributeReference(field, ctx.attributeName.getText(), StringPath.class);
    this.stack.push(continuing(field, attribute));
  }

  /**
   * Creates the appropriate exception for a field that may return multiple values but is used
   * as a scalar. Returns {@link TypeMismatchException#fieldIsMultilingual} for multilingual fields,
   * or {@link TypeMismatchException#fieldMayRepeat} for structurally repeatable fields.
   *
   * @param ctx the reference to the field, a field, attribute or raw value reference
   * @param fieldId the identifier of the field
   * @return the exception, which names the field and, if it may repeat, the current context
   */
  TypeMismatchException fieldMayReturnMultipleValuesException(final ParserRuleContext ctx,
      final String fieldId) {
    if (FieldTypes.TEXT_MULTILINGUAL.getName().equals(this.symbols.getTypeOfField(fieldId))) {
      return TypeMismatchException.fieldIsMultilingual(ctx, fieldId);
    }
    return TypeMismatchException.fieldMayRepeat(ctx, fieldId, this.efxContext.symbol());
  }

  // #endregion Value References ----------------------------------------------

  // #region References with context override ---------------------------------

  /**
   * Handles expressions of the form ContextField::ReferencedField. Changes the context before the
   * reference is resolved.
   */
  @Override
  public void exitContextFieldSpecifier(ContextFieldSpecifierContext ctx) {
    final PathExpression contextFieldPath = this.stack.pop(PathExpression.class);
    final String contextFieldId = getFieldId(ctx.fieldContext());
    this.efxContext
        .push(new FieldContext(contextFieldId, this.symbols.getAbsolutePathOfField(contextFieldId),
            contextFieldPath));
  }


  /**
   * Handles expressions of the form ContextField::ReferencedField. Changes the context before the
   * reference is resolved.
   */
  @Override
  public void exitFieldReferenceWithFieldContextOverride(
      FieldReferenceWithFieldContextOverrideContext ctx) {
    if (ctx.contextFieldSpecifier() != null) {
      final PathExpression field = this.stack.pop(PathExpression.class);
      this.stack.push(this.joinReference(this.efxContext.relativePath(), field));
      this.efxContext.pop(); // Restores the previous context
    }
  }

  /**
   * Handles expressions of the form ContextNode::ReferencedField. Changes the context before the
   * reference is resolved.
   */
  @Override
  public void exitContextNodeSpecifier(ContextNodeSpecifierContext ctx) {
    final PathExpression contextNodePath = this.stack.pop(PathExpression.class);
    final String contextNodeId = getNodeId(ctx.node);
    this.efxContext
        .push(new NodeContext(contextNodeId, this.symbols.getAbsolutePathOfNode(contextNodeId),
            contextNodePath));
  }

  /**
   * Handles expressions of the form ContextNode::ReferencedField. Restores the context after the
   * reference is resolved.
   */
  @Override
  public void exitFieldReferenceWithNodeContextOverride(
      FieldReferenceWithNodeContextOverrideContext ctx) {
    if (ctx.contextNodeSpecifier() != null) {
      final PathExpression field = this.stack.pop(PathExpression.class);
      this.stack.push(this.joinReference(this.efxContext.relativePath(), field));
      this.efxContext.pop(); // Restores the previous context
    }
  }

  @Override
  public void exitContextVariableSpecifier(ContextVariableSpecifierContext ctx) {
    String variableName = ctx.variableReference().variableName.getText();
    Context variableContext = this.efxContext.getContextFromVariable(variableName);
    if (variableContext == null) {
      throw InvalidIdentifierException.notAContextVariable(ctx, variableName);
    }
    if (variableContext.isFieldContext()) {
      this.efxContext.push(new FieldContext(variableContext.symbol(),
          this.symbols.getAbsolutePathOfField(variableContext.symbol()), this.symbols
              .getRelativePathOfField(variableContext.symbol(), this.efxContext.symbol())));
    } else if (variableContext.isNodeContext()) {
      this.efxContext.push(new NodeContext(variableContext.symbol(),
          this.symbols.getAbsolutePathOfNode(variableContext.symbol()), this.symbols
              .getRelativePathOfNode(variableContext.symbol(), this.efxContext.symbol())));
    } else {
      assert false : "Context variable must be either a field or node context: " + variableName;
    }
    // Push the context variable's path onto the stack for use by exitFieldReferenceWithVariableContextOverride
    this.stack.pushIdentifierReference(variableName);
  }

  @Override
  public void exitFieldReferenceWithVariableContextOverride(
      FieldReferenceWithVariableContextOverrideContext ctx) {
    if (ctx.contextVariableSpecifier() != null) {
      final PathExpression field = this.stack.pop(PathExpression.class);
      final PathExpression contextVariable = this.stack.pop(PathExpression.class);
      this.stack.push(this.joinReference(contextVariable, field));
      this.efxContext.pop(); // Restores the previous context
    }
  }

  /**
   * Joins the path to the context of a context override with the path of the field from that context.
   * The joined reference may give several values if either path may select several nodes. A context
   * variable holds one node, so its own path never does.
   *
   * @param context the path to the context, from the context the override is written in
   * @param field the path of the field, from the context of the override
   * @return the path of the field from the context the override is written in
   */
  private PathExpression joinReference(final PathExpression context, final PathExpression field) {
    return continuing(context, this.script.joinPaths(context, field));
  }

  // #endregion References with context override ------------------------------

  // #region Codelist and Variable References ---------------------------------

  @Override
  public void exitCodelistReference(CodelistReferenceContext ctx) {
    this.stack.push(this.script.composeList(this.symbols.expandCodelist(ctx.codelistName.getText())
        .stream().map(s -> this.script.getStringLiteralFromUnquotedString(s))
        .collect(Collectors.toList()), StringSequenceExpression.class));
  }

  @Override
  public void exitScalarFromVariableReference(ScalarFromVariableReferenceContext ctx) {
    this.resolveAndPushVariableReference(ctx.variableReference());
  }

  @Override
  public void exitSequenceFromVariableReference(SequenceFromVariableReferenceContext ctx) {
    this.resolveAndPushVariableReference(ctx.variableReference());
  }

  /**
   * Resolves and pushes a variable reference, typed by its declaration. Context variables (bound by
   * {@code for context:}) are single values of their field; node context variables cannot be used
   * as values.
   *
   * @param ctx the variable reference context
   */
  protected void resolveAndPushVariableReference(VariableReferenceContext ctx) {
    String variableName = ctx.variableName.getText();
    Context variableContext = this.efxContext.getContextFromVariable(variableName);
    if (variableContext != null && variableContext.isNodeContext()) {
      throw TypeMismatchException.nodeContextUsedAsValue(ctx, variableName, variableContext.symbol());
    }
    this.stack.pushIdentifierReference(variableName);
  }

  // #endregion Codelist and Variable References ------------------------------

  // #endregion References ----------------------------------------------------

  // #region New in EFX-2: Indexers ------------------------------------------

  @Override
  public void exitStringAtSequenceIndex(StringAtSequenceIndexContext ctx) {
    this.exitSequenceAtIndex(ctx.indexer(), StringExpression.class, StringSequenceExpression.class);
  }

  @Override
  public void exitNumericAtSequenceIndex(NumericAtSequenceIndexContext ctx) {
    this.exitSequenceAtIndex(ctx.indexer(), NumericExpression.class, NumericSequenceExpression.class);
  }

  @Override
  public void exitBooleanAtSequenceIndex(BooleanAtSequenceIndexContext ctx) {
    this.exitSequenceAtIndex(ctx.indexer(), BooleanExpression.class, BooleanSequenceExpression.class);
  }

  @Override
  public void exitDateAtSequenceIndex(DateAtSequenceIndexContext ctx) {
    this.exitSequenceAtIndex(ctx.indexer(), DateExpression.class, DateSequenceExpression.class);
  }

  @Override
  public void exitTimeAtSequenceIndex(TimeAtSequenceIndexContext ctx) {
    this.exitSequenceAtIndex(ctx.indexer(), TimeExpression.class, TimeSequenceExpression.class);
  }

  @Override
  public void exitDurationAtSequenceIndex(DurationAtSequenceIndexContext ctx) {
    this.exitSequenceAtIndex(ctx.indexer(), DurationExpression.class, DurationSequenceExpression.class);
  }

  private <T extends SequenceExpression> void exitSequenceAtIndex(IndexerContext indexer,
      Class<? extends ScalarExpression> itemType, Class<T> listType) {
    NumericExpression index = this.popOperand(indexer, NumericExpression.class);

    var addParenthesis = this.stack.peek() instanceof PathExpression;

    T list = this.stack.pop(listType);

    if (addParenthesis) {
      list = this.script.composeParenthesizedExpression(list, listType);
    }
    this.stack.push(this.script.composeIndexer(list, index, itemType));
  }

  @Override
  public void exitLateBoundScalarAtSequenceIndex(LateBoundScalarAtSequenceIndexContext ctx) {
    TypedExpression sequence = this.stack.peekType(-1);
    this.exitSequenceAtIndex(ctx.indexer(), this.scalarTypeOf(sequence), this.sequenceTypeOf(sequence));
  }


  // #endregion New in EFX-2: Indexers ---------------------------------------

  // #region New in EFX-2: Function invocation ------------------------------


  @Override
  public void enterFunctionInvocation(FunctionInvocationContext ctx) {
    final Function function = this.stack.getFunction(ctx.functionName.getText());
    this.stack.push(new StrictArguments(function));
  }

  @Override
  public void exitArgument(ArgumentContext ctx) {
    var argument = this.stack.pop(TypedExpression.class);        // pop the argument
    var functionArguments = this.stack.pop(StrictArguments.class);  // pop the function arguments
    functionArguments.addArgument(argument);                                  // add the argument to the list of arguments
    this.stack.push(functionArguments);                                       // push the updated list of arguments
  }

  /**
   * Matches the arguments of the invocation against the parameters the function declares, and
   * leaves them for the expression that composes the invocation.
   */
  @Override
  public void exitFunctionInvocation(FunctionInvocationContext ctx) {
    this.stack.push(this.popMatchedArguments());
  }

  /**
   * Pops the arguments of an invocation of a declared function or template, and matches them
   * against the parameters of the declaration, as the arguments of a built-in are matched against
   * its overloads.
   *
   * @return the arguments, each converted to the class of its parameter
   * @throws InvalidArgumentException if the number of arguments or the type of one does not match
   */
  protected ParsedArguments popMatchedArguments() {
    final StrictArguments arguments = this.stack.pop(StrictArguments.class);
    final Parametrised declaration = arguments.identifier;
    final Match match = Parameters.of(declaration.parameters).match(arguments.getValues());
    if (match.isCountMismatch()) {
      throw InvalidArgumentException.argumentNumberMismatch(declaration,
          declaration.parameters.size(), arguments.getValues().size());
    }
    if (!match.isMatched()) {
      throw InvalidArgumentException.argumentTypeMismatch(match.getPosition(), declaration,
          match.getRequired().getExpressionType(), match.getArgument().getClass());
    }
    return new ParsedArguments(declaration.parameters, match.getArguments().getValues());
  }

  @Override
  public void exitStringFunctionInvocation(StringFunctionInvocationContext ctx) {
    var parameters = this.stack.pop(ParsedArguments.class).getArgumentValues();
    this.stack.push(this.script.composeFunctionInvocation(ctx.functionInvocation().functionName.getText(), parameters, StringExpression.class));
  }

  @Override
  public void exitNumericFunctionInvocation(NumericFunctionInvocationContext ctx) {
    var parameters = this.stack.pop(ParsedArguments.class).getArgumentValues();
    this.stack.push(this.script.composeFunctionInvocation(ctx.functionInvocation().functionName.getText(), parameters, NumericExpression.class));
  }

  @Override
  public void exitBooleanFunctionInvocation(BooleanFunctionInvocationContext ctx) {
    var parameters = this.stack.pop(ParsedArguments.class).getArgumentValues();
    this.stack.push(this.script.composeFunctionInvocation(ctx.functionInvocation().functionName.getText(), parameters, BooleanExpression.class));
  }

  @Override
  public void exitDateFunctionInvocation(DateFunctionInvocationContext ctx) {
    var parameters = this.stack.pop(ParsedArguments.class).getArgumentValues();
    this.stack.push(this.script.composeFunctionInvocation(ctx.functionInvocation().functionName.getText(), parameters, DateExpression.class));
  }

  @Override
  public void exitTimeFunctionInvocation(TimeFunctionInvocationContext ctx) {
    var parameters = this.stack.pop(ParsedArguments.class).getArgumentValues();
    this.stack.push(this.script.composeFunctionInvocation(ctx.functionInvocation().functionName.getText(), parameters, TimeExpression.class));
  }

  @Override
  public void exitDurationFunctionInvocation(DurationFunctionInvocationContext ctx) {
    var parameters = this.stack.pop(ParsedArguments.class).getArgumentValues();
    this.stack.push(this.script.composeFunctionInvocation(ctx.functionInvocation().functionName.getText(), parameters, DurationExpression.class));
  }

  @Override
  public void exitScalarFromFunctionInvocation(ScalarFromFunctionInvocationContext ctx) {
    this.resolveAndPushFunctionInvocation(ctx.functionInvocation());
  }

  @Override
  public void exitSequenceFromFunctionInvocation(SequenceFromFunctionInvocationContext ctx) {
    this.resolveAndPushFunctionInvocation(ctx.functionInvocation());
  }

  /**
   * Resolves and pushes a function invocation, typed by the declaration of the function: a single
   * value or a sequence of the primitive of its value.
   *
   * @param ctx the function invocation context
   */
  private void resolveAndPushFunctionInvocation(FunctionInvocationContext ctx) {
    final ParsedArguments arguments = this.stack.pop(ParsedArguments.class);
    final Function function = this.stack.getFunction(ctx.functionName.getText());
    final ParameterType result = EfxTypeLattice.isSequence(function.dataType)
        ? ParameterType.T_SEQUENCE
        : ParameterType.T;
    this.stack.push(this.script.composeFunctionInvocation(ctx.functionName.getText(),
        arguments.getArgumentValues(), this.typeOf(result, function.expression)));
  }

  // #endregion New in EFX-2: Function invocation -----------------------------

  // #region Parameter Declarations -------------------------------------------


  @Override
  public void exitStringParameterDeclaration(StringParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), StringExpression.class);
  }

  @Override
  public void exitNumericParameterDeclaration(NumericParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), NumericExpression.class);
  }

  @Override
  public void exitBooleanParameterDeclaration(BooleanParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), BooleanExpression.class);
  }

  @Override
  public void exitDateParameterDeclaration(DateParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), DateExpression.class);
  }

  @Override
  public void exitTimeParameterDeclaration(TimeParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), TimeExpression.class);
  }

  @Override
  public void exitDurationParameterDeclaration(DurationParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), DurationExpression.class);
  }

  // Sequence parameter declarations
  @Override
  public void exitStringSequenceParameterDeclaration(StringSequenceParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), StringSequenceExpression.class);
  }

  @Override
  public void exitNumericSequenceParameterDeclaration(NumericSequenceParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), NumericSequenceExpression.class);
  }

  @Override
  public void exitBooleanSequenceParameterDeclaration(BooleanSequenceParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), BooleanSequenceExpression.class);
  }

  @Override
  public void exitDateSequenceParameterDeclaration(DateSequenceParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), DateSequenceExpression.class);
  }

  @Override
  public void exitTimeSequenceParameterDeclaration(TimeSequenceParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), TimeSequenceExpression.class);
  }

  @Override
  public void exitDurationSequenceParameterDeclaration(DurationSequenceParameterDeclarationContext ctx) {
    this.exitParameterDeclaration(ctx, ctx.parameterName.getText(), DurationSequenceExpression.class);
  }

  protected void exitParameterDeclaration(ParserRuleContext ctx, String parameterName, Class<? extends TypedExpression> parameterType) {
    if (this.expressionArguments.isEmpty()) {
      throw InvalidArgumentException.missingArgument(ctx, parameterName);
    }

    ParsedParameter parameter = new ParsedParameter(parameterName,
        this.translateArgument(this.expressionArguments.pop(), parameterType));
    this.stack.declareIdentifier(parameter);
  }

  // #endregion Parameter Declarations ----------------------------------------

  // #region Boolean functions ------------------------------------------------

  @Override
  public void exitNotFunction(NotFunctionContext ctx) {
    this.exitCall(ctx, LogicalOperators.NOT);
  }

  @Override
  public void exitBooleanFromNumberFunction(BooleanFromNumberFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_INDICATOR);
  }

  @Override
  public void exitContainsFunction(ContainsFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.CONTAINS);
  }

  @Override
  public void exitStartsWithFunction(StartsWithFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.STARTS_WITH);
  }

  @Override
  public void exitEndsWithFunction(EndsWithFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.ENDS_WITH);
  }

  // #endregion Boolean functions ---------------------------------------------

  // #region Privacy settings ------------------------------------------------

  @Override
  public void exitFieldWasWithheldProperty(FieldWasWithheldPropertyContext ctx) {
    final String fieldId = getFieldId(ctx.fieldMention());
    final String privacyCode = this.symbols.getPrivacyCodeOfField(fieldId);
    if (privacyCode == null || privacyCode.isEmpty()) {
      throw InvalidUsageException.fieldNotWithholdable(ctx, fieldId);
    }

    this.stack.push(this.composeWasWithheldCondition(fieldId, privacyCode));
  }

  @Override
  public void exitFieldIsWithheldProperty(FieldIsWithheldPropertyContext ctx) {
    final String fieldId = getFieldId(ctx.fieldMention());
    final String privacyCode = this.symbols.getPrivacyCodeOfField(fieldId);
    if (privacyCode == null || privacyCode.isEmpty()) {
      throw InvalidUsageException.fieldNotWithholdable(ctx, fieldId);
    }

    this.stack.push(this.script.composeLogicalAnd(
        this.composeWasWithheldCondition(fieldId, privacyCode),
        this.composeStillWithheldCondition(fieldId)));
  }

  @Override
  public void exitFieldIsWithholdableProperty(FieldIsWithholdablePropertyContext ctx) {
    final String fieldId = getFieldId(ctx.fieldMention());
    final String privacyCode = this.symbols.getPrivacyCodeOfField(fieldId);
    final boolean isWithholdable = privacyCode != null && !privacyCode.isEmpty();
    this.stack.push(this.script.getBooleanEquivalent(isWithholdable));
  }

  @Override
  public void exitFieldIsDisclosedProperty(FieldIsDisclosedPropertyContext ctx) {
    final String fieldId = getFieldId(ctx.fieldMention());
    final String privacyCode = this.symbols.getPrivacyCodeOfField(fieldId);
    if (privacyCode == null || privacyCode.isEmpty()) {
      throw InvalidUsageException.fieldNotWithholdable(ctx, fieldId);
    }

    // "isDisclosed" = "was withheld" AND NOT "still withheld" AND NOT "masked"
    this.stack.push(this.script.composeLogicalAnd(
        this.script.composeLogicalAnd(
            this.composeWasWithheldCondition(fieldId, privacyCode),
            this.script.composeLogicalNot(this.composeStillWithheldCondition(fieldId))),
        this.script.composeLogicalNot(this.composeIsMaskedCondition(fieldId))));
  }

  @Override
  public void exitFieldIsMaskedProperty(FieldIsMaskedPropertyContext ctx) {
    final String fieldId = getFieldId(ctx.fieldMention());
    final String privacyCode = this.symbols.getPrivacyCodeOfField(fieldId);
    if (privacyCode == null || privacyCode.isEmpty()) {
      throw InvalidUsageException.fieldNotWithholdable(ctx, fieldId);
    }

    // "isMasked" = was withheld AND field value equals the privacy mask
    this.stack.push(this.script.composeLogicalAnd(
        this.composeWasWithheldCondition(fieldId, privacyCode),
        this.composeIsMaskedCondition(fieldId)));
  }

  @Override
  public void exitFieldPrivacyCodeProperty(FieldPrivacyCodePropertyContext ctx) {
    final String fieldId = getFieldId(ctx.fieldMention());
    final String privacyCode = this.symbols.getPrivacyCodeOfField(fieldId);
    if (privacyCode == null || privacyCode.isEmpty()) {
      throw InvalidUsageException.fieldNotWithholdable(ctx, fieldId);
    }
    this.stack.push(this.script.getStringLiteralFromUnquotedString(privacyCode));
  }

  @Override
  public void exitRawValueReference(RawValueReferenceContext ctx) {
    final TypedExpression top = this.stack.peekType();
    if (!(top instanceof PathExpression)) {
      throw TypeMismatchException.cannotConvert(ctx, PathExpression.class, top.getClass());
    }
    final PathExpression fieldPath = (PathExpression) this.stack.pop(top.getClass());
    this.stack.push(this.script.composeFieldRawValueReference(fieldPath));
  }


  private BooleanExpression composeWasWithheldCondition(String fieldId, String privacyCode) {
    final String privacyCodeFieldId = this.symbols.getPrivacySettingOfField(fieldId, PrivacySetting.PRIVACY_CODE_FIELD);
    if (privacyCodeFieldId == null) {
      throw SdkInconsistencyException.missingPrivacyCodeField(fieldId);
    }

    return this.script.composeComparisonOperation(
        new StringExpression(this.script.composeFieldValueReference(
            this.symbols.getRelativePathOfField(privacyCodeFieldId, this.efxContext.symbol())).getScript()),
        "==",
        this.script.getStringLiteralFromUnquotedString(privacyCode));
  }

  private BooleanExpression composeStillWithheldCondition(String fieldId) {
    final String publicationDateFieldId = this.symbols.getPrivacySettingOfField(fieldId, PrivacySetting.PUBLICATION_DATE_FIELD);
    if (publicationDateFieldId == null) {
      throw SdkInconsistencyException.missingPublicationDateField(fieldId);
    }

    final PathExpression pubDateFieldPath = this.symbols.getRelativePathOfField(publicationDateFieldId,
        this.efxContext.symbol());

    return this.script.composeParenthesizedExpression(
        this.script.composeLogicalOr(
            this.script.composeLogicalNot(this.script.composeExistsCondition(pubDateFieldPath)),
            this.script.composeComparisonOperation(
                new DateExpression(this.script.composeFieldValueReference(pubDateFieldPath).getScript()), ">",
                this.script.getCurrentDate())),
        BooleanExpression.class);
  }

  private BooleanExpression composeIsMaskedCondition(String fieldId) {
    final String maskingValue = this.symbols.getPrivacyMask(fieldId);
    final PathExpression fieldValue = this.script.composeFieldValueReference(this.symbols.getRelativePathOfField(fieldId, this.efxContext.symbol()));

    return this.script.composeComparisonOperation(
        TypedExpression.from(fieldValue, ScalarExpression.class),
        "==",
        this.getTypedLiteralFromUnquotedString(maskingValue, fieldValue.getDataType()));
  }

  private ScalarExpression getTypedLiteralFromUnquotedString(String value, Class<? extends EfxDataType> type) {
    if (EfxDataType.Number.class.isAssignableFrom(type)) {
      return this.script.getNumericLiteralEquivalent(value);
    }
    if (EfxDataType.Date.class.isAssignableFrom(type)) {
      return this.script.getDateLiteralEquivalent(value);
    }
    if (EfxDataType.Time.class.isAssignableFrom(type)) {
      return this.script.getTimeLiteralEquivalent(value);
    }
    if (EfxDataType.Duration.class.isAssignableFrom(type)) {
      return this.script.getDurationLiteralEquivalent(value);
    }
    if (EfxDataType.Boolean.class.isAssignableFrom(type)) {
      return this.script.getBooleanEquivalent(Boolean.parseBoolean(value));
    }
    return this.script.getStringLiteralFromUnquotedString(value);
  }

  // #endregion Privacy settings ---------------------------------------------

  // #region Sequence-equal ----------------------------------------------------

  @Override
  public void exitLateBoundSequenceEqualFunction(LateBoundSequenceEqualFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitStringSequenceEqualFunction(StringSequenceEqualFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundStringSequenceEqualLeft(LateBoundStringSequenceEqualLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundStringSequenceEqualRight(LateBoundStringSequenceEqualRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitBooleanSequenceEqualFunction(BooleanSequenceEqualFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundBooleanSequenceEqualLeft(LateBoundBooleanSequenceEqualLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundBooleanSequenceEqualRight(LateBoundBooleanSequenceEqualRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitNumericSequenceEqualFunction(NumericSequenceEqualFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundNumericSequenceEqualLeft(LateBoundNumericSequenceEqualLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundNumericSequenceEqualRight(LateBoundNumericSequenceEqualRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitDateSequenceEqualFunction(DateSequenceEqualFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundDateSequenceEqualLeft(LateBoundDateSequenceEqualLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundDateSequenceEqualRight(LateBoundDateSequenceEqualRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitTimeSequenceEqualFunction(TimeSequenceEqualFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundTimeSequenceEqualLeft(LateBoundTimeSequenceEqualLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundTimeSequenceEqualRight(LateBoundTimeSequenceEqualRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitDurationSequenceEqualFunction(DurationSequenceEqualFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundDurationSequenceEqualLeft(LateBoundDurationSequenceEqualLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public void exitLateBoundDurationSequenceEqualRight(LateBoundDurationSequenceEqualRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  // #endregion Sequence-equal -------------------------------------------------

  // #region Numeric functions ------------------------------------------------

  @Override
  public void exitLateBoundCountFunction(LateBoundCountFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.COUNT);
  }

  @Override
  public void exitCountStringsFunction(CountStringsFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.COUNT);
  }

  @Override
  public void exitCountBooleansFunction(CountBooleansFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.COUNT);
  }

  @Override
  public void exitCountNumbersFunction(CountNumbersFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.COUNT);
  }

  @Override
  public void exitCountDatesFunction(CountDatesFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.COUNT);
  }

  @Override
  public void exitCountTimesFunction(CountTimesFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.COUNT);
  }

  @Override
  public void exitCountDurationsFunction(CountDurationsFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.COUNT);
  }

  @Override
  public void exitLateBoundCountDuplicatesFunction(LateBoundCountDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public void exitCountDuplicatesStringsFunction(CountDuplicatesStringsFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public void exitCountDuplicatesBooleansFunction(CountDuplicatesBooleansFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public void exitCountDuplicatesNumbersFunction(CountDuplicatesNumbersFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public void exitCountDuplicatesDatesFunction(CountDuplicatesDatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public void exitCountDuplicatesTimesFunction(CountDuplicatesTimesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public void exitCountDuplicatesDurationsFunction(CountDuplicatesDurationsFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public void exitNumberFromStringFunction(NumberFromStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_NUMBER);
  }

  @Override
  public void exitNumberFromBooleanFunction(NumberFromBooleanFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_NUMBER);
  }

  @Override
  public void exitLateBoundToNumberFunction(LateBoundToNumberFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_NUMBER);
  }

  @Override
  public void exitSumFunction(SumFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.SUM);
  }

  @Override
  public void exitMinFunction(MinFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.MIN);
  }

  @Override
  public void exitMaxFunction(MaxFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.MAX);
  }

  @Override
  public void exitAverageFunction(AverageFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.AVERAGE);
  }

  @Override
  public void exitStringLengthFunction(StringLengthFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.STRING_LENGTH);
  }

  @Override
  public void exitAbsoluteFunction(AbsoluteFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.ABSOLUTE);
  }

  @Override
  public void exitRoundFunction(RoundFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.ROUND);
  }

  @Override
  public void exitRoundDownFunction(RoundDownFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.ROUND_DOWN);
  }

  @Override
  public void exitRoundUpFunction(RoundUpFunctionContext ctx) {
    this.exitCall(ctx, NumericFunctions.ROUND_UP);
  }

  // #endregion Numeric functions ---------------------------------------------

  // #region String functions -------------------------------------------------

  @Override
  public void exitSubstringFunction(SubstringFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.SUBSTRING);
  }

  @Override
  public void exitSubstringBeforeFunction(SubstringBeforeFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.SUBSTRING_BEFORE);
  }

  @Override
  public void exitSubstringAfterFunction(SubstringAfterFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.SUBSTRING_AFTER);
  }

  @Override
  public void exitNumberToStringFunction(NumberToStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_TEXT);
  }

  @Override
  public void exitBooleanToStringFunction(BooleanToStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_TEXT);
  }

  @Override
  public void exitDateToStringFunction(DateToStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_TEXT);
  }

  @Override
  public void exitTimeToStringFunction(TimeToStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_TEXT);
  }

  @Override
  public void exitDurationToStringFunction(DurationToStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_TEXT);
  }

  @Override
  public void exitLateBoundToStringFunction(LateBoundToStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_TEXT);
  }

  @Override
  public void exitConcatFunction(ConcatFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.CONCAT);
  }

  @Override
  public void exitFormatNumberFunction(FormatNumberFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_NUMBER);
  }

  @Override
  public void exitLateBoundFormatShortFunction(LateBoundFormatShortFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_SHORT);
  }

  @Override
  public void exitFormatShortDateFunction(FormatShortDateFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_SHORT);
  }

  @Override
  public void exitFormatShortTimeFunction(FormatShortTimeFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_SHORT);
  }

  @Override
  public void exitLateBoundFormatMediumFunction(LateBoundFormatMediumFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_MEDIUM);
  }

  @Override
  public void exitFormatMediumDateFunction(FormatMediumDateFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_MEDIUM);
  }

  @Override
  public void exitFormatMediumTimeFunction(FormatMediumTimeFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_MEDIUM);
  }

  @Override
  public void exitLateBoundFormatLongFunction(LateBoundFormatLongFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_LONG);
  }

  @Override
  public void exitFormatLongDateFunction(FormatLongDateFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_LONG);
  }

  @Override
  public void exitFormatLongTimeFunction(FormatLongTimeFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_LONG);
  }

  @Override
  public void exitFormatShortDateTimeFunction(FormatShortDateTimeFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_SHORT);
  }

  @Override
  public void exitFormatMediumDateTimeFunction(FormatMediumDateTimeFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_MEDIUM);
  }

  @Override
  public void exitFormatLongDateTimeFunction(FormatLongDateTimeFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.FORMAT_LONG);
  }

  @Override
  public void exitUpperCaseFunction(UpperCaseFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.UPPER_CASE);
  }

  @Override
  public void exitLowerCaseFunction(LowerCaseFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.LOWER_CASE);
  }

  @Override
  public void exitNormalizeSpaceFunction(NormalizeSpaceFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.NORMALIZE_SPACE);
  }

  @Override
  public void exitTrimFunction(TrimFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.TRIM);
  }

  @Override
  public void exitTrimLeftFunction(TrimLeftFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.TRIM_LEFT);
  }

  @Override
  public void exitTrimRightFunction(TrimRightFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.TRIM_RIGHT);
  }

  @Override
  public void exitPadLeftFunction(PadLeftFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.PAD_LEFT);
  }

  @Override
  public void exitPadRightFunction(PadRightFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.PAD_RIGHT);
  }

  @Override
  public void exitRepeatFunction(RepeatFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.REPEAT);
  }

  @Override
  public void exitReplaceFunction(ReplaceFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.REPLACE);
  }

  @Override
  public void exitReplaceRegexFunction(ReplaceRegexFunctionContext ctx) {
    if (ctx.pattern instanceof StringLiteralExpressionContext) {
      EfxRegexValidator.validate(ctx.pattern.getText());
    }
    this.exitCall(ctx, StringFunctions.REPLACE_REGEX);
  }

  @Override
  public void exitUrlEncodeFunction(UrlEncodeFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.URL_ENCODE);
  }

  @Override
  public void exitCapitalizeFirstFunction(CapitalizeFirstFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.CAPITALIZE_FIRST);
  }

  @Override
  public void exitStringJoinFunction(StringJoinFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.STRING_JOIN);
  }

  @Override
  public void exitPreferredLanguageFunction(PreferredLanguageFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.PREFERRED_LANGUAGE);
  }

  @Override
  public void exitPreferredLanguageTextFunction(PreferredLanguageTextFunctionContext ctx) {
    this.exitCall(ctx, TemplateFunctions.PREFERRED_LANGUAGE_TEXT);
  }

  @Override
  public void exitFieldPreferredLanguageProperty(FieldPreferredLanguagePropertyContext ctx) {
    this.exitCall(ctx, TemplateFunctions.PREFERRED_LANGUAGE);
  }

  @Override
  public void exitFieldPreferredLanguageTextProperty(FieldPreferredLanguageTextPropertyContext ctx) {
    this.exitCall(ctx, TemplateFunctions.PREFERRED_LANGUAGE_TEXT);
  }

  @Override
  public void exitDictionaryLookup(DictionaryLookupContext ctx) {
    var dictionary = this.stack.getDictionary(ctx.dictionaryName.getText());
    var key = this.popOperand(ctx.getRuleContext(ParserRuleContext.class, 0), StringExpression.class);
    this.stack.push(this.script.composeDictionaryLookup(dictionary.name, key, dictionary.type));
  }

  @Override
  public void exitSplitFunction(SplitFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.SPLIT);
  }

  @Override
  public void exitIndexOfSubstringFunction(IndexOfSubstringFunctionContext ctx) {
    this.exitCall(ctx, StringFunctions.INDEX_OF_SUBSTRING);
  }

  // #endregion String functions ----------------------------------------------

  // #region Date functions ---------------------------------------------------

  @Override
  public void exitDateFromStringFunction(DateFromStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_DATE);
  }

  @Override
  public void exitDatePlusDurationFunction(DatePlusDurationFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.ADD_DURATION);
  }

  @Override
  public void exitDateMinusDurationFunction(DateMinusDurationFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.SUBTRACT_DURATION);
  }

  @Override
  public void exitCurrentDateFunction(CurrentDateFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.CURRENT_DATE);
  }

  @Override
  public void exitYearFromDateFunction(YearFromDateFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.YEAR);
  }

  @Override
  public void exitMonthFromDateFunction(MonthFromDateFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.MONTH);
  }

  @Override
  public void exitDayFromDateFunction(DayFromDateFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.DAY);
  }

  // #endregion Date functions ------------------------------------------------

  // #region Time functions ---------------------------------------------------

  @Override
  public void exitTimeFromStringFunction(TimeFromStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_TIME);
  }

  @Override
  public void exitCurrentTimeFunction(CurrentTimeFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.CURRENT_TIME);
  }

  @Override
  public void exitHoursFromTimeFunction(HoursFromTimeFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.HOURS);
  }

  @Override
  public void exitMinutesFromTimeFunction(MinutesFromTimeFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.MINUTES);
  }

  @Override
  public void exitSecondsFromTimeFunction(SecondsFromTimeFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.SECONDS);
  }

  // #endregion Time functions ----------------------------------------------

  // #region Duration Functions -----------------------------------------------

  @Override
  public void exitDayTimeDurationFromStringFunction(DayTimeDurationFromStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_DAY_TIME_DURATION);
  }

  @Override
  public void exitYearMonthDurationFromStringFunction(
      YearMonthDurationFromStringFunctionContext ctx) {
    this.exitCall(ctx, ConversionFunctions.TO_YEAR_MONTH_DURATION);
  }

  @Override
  public void exitYearsFromDurationFunction(YearsFromDurationFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.YEARS);
  }

  @Override
  public void exitMonthsFromDurationFunction(MonthsFromDurationFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.MONTHS);
  }

  @Override
  public void exitDaysFromDurationFunction(DaysFromDurationFunctionContext ctx) {
    this.exitCall(ctx, DateTimeFunctions.DAYS);
  }

  // #endregion Duration Functions --------------------------------------------

  // #region Sequence Functions -----------------------------------------------

  // #region Distinct-values ---------------------------------------------------

  @Override
  public void exitStringDistinctValuesFunction(StringDistinctValuesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  @Override
  public void exitBooleanDistinctValuesFunction(BooleanDistinctValuesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  @Override
  public void exitNumericDistinctValuesFunction(NumericDistinctValuesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  @Override
  public void exitDateDistinctValuesFunction(DateDistinctValuesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  @Override
  public void exitTimeDistinctValuesFunction(TimeDistinctValuesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  @Override
  public void exitDurationDistinctValuesFunction(DurationDistinctValuesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  @Override
  public void exitLateBoundDistinctValuesFunction(LateBoundDistinctValuesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  // #endregion Distinct-values ------------------------------------------------

  // #region Get-duplicates ---------------------------------------------------

  @Override
  public void exitStringGetDuplicatesFunction(StringGetDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  @Override
  public void exitBooleanGetDuplicatesFunction(BooleanGetDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  @Override
  public void exitNumericGetDuplicatesFunction(NumericGetDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  @Override
  public void exitDateGetDuplicatesFunction(DateGetDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  @Override
  public void exitTimeGetDuplicatesFunction(TimeGetDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  @Override
  public void exitDurationGetDuplicatesFunction(DurationGetDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  @Override
  public void exitLateBoundGetDuplicatesFunction(LateBoundGetDuplicatesFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  // #endregion Get-duplicates ------------------------------------------------

  // #region Union ------------------------------------------------------------

  @Override
  public void exitStringUnionFunction(StringUnionFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundStringUnionLeft(LateBoundStringUnionLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundStringUnionRight(LateBoundStringUnionRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitBooleanUnionFunction(BooleanUnionFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundBooleanUnionLeft(LateBoundBooleanUnionLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundBooleanUnionRight(LateBoundBooleanUnionRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitNumericUnionFunction(NumericUnionFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundNumericUnionLeft(LateBoundNumericUnionLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundNumericUnionRight(LateBoundNumericUnionRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitDateUnionFunction(DateUnionFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundDateUnionLeft(LateBoundDateUnionLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundDateUnionRight(LateBoundDateUnionRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitTimeUnionFunction(TimeUnionFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundTimeUnionLeft(LateBoundTimeUnionLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundTimeUnionRight(LateBoundTimeUnionRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitDurationUnionFunction(DurationUnionFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundDurationUnionLeft(LateBoundDurationUnionLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundDurationUnionRight(LateBoundDurationUnionRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public void exitLateBoundUnionFunction(LateBoundUnionFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_UNION);
  }

  // #endregion Union ---------------------------------------------------------

  // #region Intersect -------------------------------------------------------

  @Override
  public void exitStringIntersectFunction(StringIntersectFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundStringIntersectLeft(LateBoundStringIntersectLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundStringIntersectRight(LateBoundStringIntersectRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitBooleanIntersectFunction(BooleanIntersectFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundBooleanIntersectLeft(LateBoundBooleanIntersectLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundBooleanIntersectRight(LateBoundBooleanIntersectRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitNumericIntersectFunction(NumericIntersectFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundNumericIntersectLeft(LateBoundNumericIntersectLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundNumericIntersectRight(LateBoundNumericIntersectRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitDateIntersectFunction(DateIntersectFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundDateIntersectLeft(LateBoundDateIntersectLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundDateIntersectRight(LateBoundDateIntersectRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitTimeIntersectFunction(TimeIntersectFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundTimeIntersectLeft(LateBoundTimeIntersectLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundTimeIntersectRight(LateBoundTimeIntersectRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitDurationIntersectFunction(DurationIntersectFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundDurationIntersectLeft(LateBoundDurationIntersectLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundDurationIntersectRight(LateBoundDurationIntersectRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public void exitLateBoundIntersectFunction(LateBoundIntersectFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  // #endregion Intersect ------------------------------------------------------

  // #region Except ----------------------------------------------------------

  @Override
  public void exitStringExceptFunction(StringExceptFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundStringExceptLeft(LateBoundStringExceptLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundStringExceptRight(LateBoundStringExceptRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitBooleanExceptFunction(BooleanExceptFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundBooleanExceptLeft(LateBoundBooleanExceptLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundBooleanExceptRight(LateBoundBooleanExceptRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitNumericExceptFunction(NumericExceptFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundNumericExceptLeft(LateBoundNumericExceptLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundNumericExceptRight(LateBoundNumericExceptRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitDateExceptFunction(DateExceptFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundDateExceptLeft(LateBoundDateExceptLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundDateExceptRight(LateBoundDateExceptRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitTimeExceptFunction(TimeExceptFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundTimeExceptLeft(LateBoundTimeExceptLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundTimeExceptRight(LateBoundTimeExceptRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitDurationExceptFunction(DurationExceptFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundDurationExceptLeft(LateBoundDurationExceptLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundDurationExceptRight(LateBoundDurationExceptRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public void exitLateBoundExceptFunction(LateBoundExceptFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  // #endregion Except ---------------------------------------------------------

  // #region Sort --------------------------------------------------------------

  @Override
  public void exitStringSortFunction(StringSortFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SORT);
  }

  @Override
  public void exitBooleanSortFunction(BooleanSortFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SORT);
  }

  @Override
  public void exitNumericSortFunction(NumericSortFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SORT);
  }

  @Override
  public void exitDateSortFunction(DateSortFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SORT);
  }

  @Override
  public void exitTimeSortFunction(TimeSortFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SORT);
  }

  @Override
  public void exitDurationSortFunction(DurationSortFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SORT);
  }

  @Override
  public void exitLateBoundSortFunction(LateBoundSortFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SORT);
  }

  // #endregion Sort -----------------------------------------------------------

  // #region Reverse ----------------------------------------------------------

  @Override
  public void exitLateBoundReverseFunction(LateBoundReverseFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.REVERSE);
  }

  @Override
  public void exitStringReverseFunction(StringReverseFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.REVERSE);
  }

  @Override
  public void exitBooleanReverseFunction(BooleanReverseFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.REVERSE);
  }

  @Override
  public void exitNumericReverseFunction(NumericReverseFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.REVERSE);
  }

  @Override
  public void exitDateReverseFunction(DateReverseFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.REVERSE);
  }

  @Override
  public void exitTimeReverseFunction(TimeReverseFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.REVERSE);
  }

  @Override
  public void exitDurationReverseFunction(DurationReverseFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.REVERSE);
  }

  // #endregion Reverse --------------------------------------------------------

  // #region Subsequence ------------------------------------------------------

  @Override
  public void exitLateBoundSubsequenceFunction(LateBoundSubsequenceFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  @Override
  public void exitStringSubsequenceFunction(StringSubsequenceFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  @Override
  public void exitBooleanSubsequenceFunction(BooleanSubsequenceFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  @Override
  public void exitNumericSubsequenceFunction(NumericSubsequenceFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  @Override
  public void exitDateSubsequenceFunction(DateSubsequenceFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  @Override
  public void exitTimeSubsequenceFunction(TimeSubsequenceFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  @Override
  public void exitDurationSubsequenceFunction(DurationSubsequenceFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  // #endregion Subsequence ----------------------------------------------------

  // #region Index-of ----------------------------------------------------------

  @Override
  public void exitLateBoundIndexOfFunction(LateBoundIndexOfFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitIndexOfStringFunction(IndexOfStringFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundStringIndexOfLeft(LateBoundStringIndexOfLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundStringIndexOfRight(LateBoundStringIndexOfRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitIndexOfBooleanFunction(IndexOfBooleanFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundBooleanIndexOfLeft(LateBoundBooleanIndexOfLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundBooleanIndexOfRight(LateBoundBooleanIndexOfRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitIndexOfNumericFunction(IndexOfNumericFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundNumericIndexOfLeft(LateBoundNumericIndexOfLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundNumericIndexOfRight(LateBoundNumericIndexOfRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitIndexOfDateFunction(IndexOfDateFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundDateIndexOfLeft(LateBoundDateIndexOfLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundDateIndexOfRight(LateBoundDateIndexOfRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitIndexOfTimeFunction(IndexOfTimeFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundTimeIndexOfLeft(LateBoundTimeIndexOfLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundTimeIndexOfRight(LateBoundTimeIndexOfRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitIndexOfDurationFunction(IndexOfDurationFunctionContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundDurationIndexOfLeft(LateBoundDurationIndexOfLeftContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public void exitLateBoundDurationIndexOfRight(LateBoundDurationIndexOfRightContext ctx) {
    this.exitCall(ctx, SequenceFunctions.INDEX_OF);
  }

  // #endregion Index-of -------------------------------------------------------

  // #endregion Sequence Functions --------------------------------------------

  protected static String getLexerSymbol(int tokenType) {
    return EfxLexer.VOCABULARY.getLiteralName(tokenType).replaceAll("^'|'$", "");
  }

  /**
   * Guard: throws if an {@code #include} directive survived into the parse tree,
   * meaning the {@link IncludeProcessor} did not resolve it.
   */
  @Override
  public void exitIncludeDirective(IncludeDirectiveContext ctx) {
    String path = ctx.IncludePath() != null ? ctx.IncludePath().getText().trim() : "<unknown>";
    throw TranslatorConfigurationException.unresolvedIncludeDirective(path);
  }

  // #region Built-ins and operands -------------------------------------------

  /**
   * Translates a call of a built-in function, or of a condition with a single operand: pops one
   * argument for each operand, resolves the built-in against them and pushes the result. The
   * operands of a call are the child rules of its grammar label, in order.
   *
   * <p>A sequence where a single value is required is reported by the reference the operand is
   * written as; any other argument that does not fit cannot be converted.</p>
   *
   * @param call the call
   * @param builtIn the built-in called
   */
  private void exitCall(final ParserRuleContext call, final BuiltIn builtIn) {
    final Resolution resolution = this.resolve(call, builtIn);
    if (!resolution.isResolved()) {
      final Rejection rejection = resolution.getMismatch().getDiagnosis();
      throw this.sequenceError(call, rejection)
          .orElseGet(() -> TypeMismatchException.cannotConvert(call,
              rejection.getRequired().getExpressionType(), rejection.getArgument().getClass()));
    }
    this.stack.push(resolution.compose(this.script));
  }

  /**
   * Translates an infix operator as {@link #exitCall} translates a call, except that operands
   * that do not fit are incompatible operands of the operator.
   *
   * @param operation the operation, whose two child rules are its operands
   * @param operator the operator
   */
  private void exitOperation(final ParserRuleContext operation, final BuiltIn operator) {
    final Resolution resolution = this.resolve(operation, operator);
    if (!resolution.isResolved()) {
      final Mismatch mismatch = resolution.getMismatch();
      throw this.sequenceError(operation, mismatch.getDiagnosis())
          .orElseGet(() -> TypeMismatchException.incompatibleOperands(operation, operator.getName(),
              mismatch.getArguments().get(0), mismatch.getArguments().get(1)));
    }
    this.stack.push(resolution.compose(this.script));
  }

  private Resolution resolve(final ParserRuleContext call, final BuiltIn builtIn) {
    if (builtIn.isTemplateOnly() && !this.translatesTemplateOnlyBuiltIns()) {
      throw InvalidUsageException.templateOnlyFunction(call, builtIn.getName());
    }
    return builtIn.resolve(this.popArguments(call.getRuleContexts(ParserRuleContext.class).size()));
  }

  private List<TypedExpression> popArguments(final int count) {
    final LinkedList<TypedExpression> arguments = new LinkedList<>();
    for (int position = 0; position < count; position++) {
      arguments.addFirst(this.stack.pop(TypedExpression.class));
    }
    return arguments;
  }

  private Optional<TypeMismatchException> sequenceError(final ParserRuleContext call,
      final Rejection rejection) {
    if (rejection.getKind() != Rejection.Kind.CARDINALITY) {
      return Optional.empty();
    }
    return OperandOrigin.sequenceErrorOf(
        call.getRuleContext(ParserRuleContext.class, rejection.getPosition()), this);
  }

  /**
   * Finds the operator written as a token.
   *
   * @param operator the token of a comparison or arithmetic operator
   * @return the operator of the catalogue that has the token's symbol as its name
   */
  private BuiltIn operatorNamed(final Token operator) {
    return BuiltIns.INSTANCE.find(operator.getText()).orElseThrow(
        () -> TranslatorConfigurationException.unhandledOperator(operator.getText(), "operatorNamed"));
  }

  /**
   * Tells whether the translator translates the built-ins that only templates may call, such as
   * the formatting functions. An expression alone has no template, so it does not.
   *
   * @return {@code false}
   */
  protected boolean translatesTemplateOnlyBuiltIns() {
    return false;
  }

  /**
   * Pops the value of an operand where a value of one class is required: the condition of a clause,
   * the value of a declaration, or a part of a composite expression such as a list item or the
   * branch of a conditional. A single value where a sequence is required is promoted to one.
   *
   * @param <T> the class of the value required
   * @param operand the operand, as the parser read it
   * @param type the class of the value required
   * @return the value, converted to that class
   * @throws TypeMismatchException if the value does not fit; a sequence where a single value is
   *         required is reported by the reference the operand is written as
   */
  protected <T extends TypedExpression> T popOperand(final ParserRuleContext operand,
      final Class<T> type) {
    final ParameterType required = ParameterType.of(type);
    final TypedExpression value = this.stack.pop(TypedExpression.class);
    if (required.accepts(value)) {
      return type.cast(required.convert(value));
    }
    if (required.acceptsValuesOf(value)) {
      final Optional<TypeMismatchException> sequenceError = OperandOrigin.sequenceErrorOf(operand, this);
      if (sequenceError.isPresent()) {
        throw sequenceError.get();
      }
    }
    throw TypeMismatchException.cannotConvert(this.stack.peekParserContext(), type, value.getClass());
  }

  /**
   * Gives the class of the single values of a late-bound operand: the one {@code T} is bound to by
   * the operand.
   *
   * @param operand the operand
   * @return the scalar class of the operand's primitive
   */
  private Class<? extends ScalarExpression> scalarTypeOf(final TypedExpression operand) {
    return this.typeOf(ParameterType.T, operand).asSubclass(ScalarExpression.class);
  }

  /**
   * Gives the class of the sequences of a late-bound operand: the one {@code T*} is bound to by
   * the operand.
   *
   * @param operand the operand
   * @return the sequence class of the operand's primitive
   */
  private Class<? extends SequenceExpression> sequenceTypeOf(final TypedExpression operand) {
    return this.typeOf(ParameterType.T_SEQUENCE, operand).asSubclass(SequenceExpression.class);
  }

  private Class<? extends TypedExpression> typeOf(final ParameterType typeVariable,
      final TypedExpression operand) {
    return typeVariable.boundBy(operand)
        .orElseThrow(() -> TypeMismatchException.cannotConvert(this.stack.peekParserContext(),
            typeVariable.getExpressionType(), operand.getClass()))
        .getExpressionType();
  }

  // #endregion Built-ins and operands ----------------------------------------

}
