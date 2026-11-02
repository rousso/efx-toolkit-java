/*
 * Copyright 2025 European Union
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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ted.eforms.sdk.component.SdkComponent;
import eu.europa.ted.eforms.sdk.component.SdkComponentType;
import eu.europa.ted.eforms.sdk.entity.SdkNoticeSubtype;
import eu.europa.ted.efx.interfaces.EfxRulesTranslator;
import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.interfaces.SymbolResolver;
import eu.europa.ted.efx.interfaces.TranslatorOptions;
import eu.europa.ted.efx.interfaces.ValidatorGenerator;
import eu.europa.ted.efx.exceptions.Construct;
import eu.europa.ted.efx.exceptions.InvalidIdentifierException;
import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.model.Context;
import eu.europa.ted.efx.model.Context.FieldContext;
import eu.europa.ted.efx.model.Context.NodeContext;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DynamicExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.rules.AssertRule;
import eu.europa.ted.efx.model.rules.ValidationPlan;
import eu.europa.ted.efx.model.rules.NoticeSubtypeRange;
import eu.europa.ted.efx.model.rules.ReportRule;
import eu.europa.ted.efx.model.rules.RuleSet;
import eu.europa.ted.efx.model.rules.RuleScope;
import eu.europa.ted.efx.model.rules.RuleSeverity;
import eu.europa.ted.efx.model.rules.RuleStack;
import eu.europa.ted.efx.model.rules.ValidationRule;
import eu.europa.ted.efx.model.rules.ValidationStage;
import eu.europa.ted.efx.model.variables.DynamicFunction;
import eu.europa.ted.efx.model.variables.DynamicVariable;
import eu.europa.ted.efx.model.variables.Function;
import eu.europa.ted.efx.model.variables.ParsedParameters;
import eu.europa.ted.efx.model.variables.Variable;
import eu.europa.ted.efx.sdk2.EfxParser.*;

/**
 * EFX Rules translator for SDK version 2.
 *
 * This translator parses EFX Rules files and produces an intermediate model
 * (List of ValidationStage) which is then passed to a ValidatorGenerator
 * for output generation.
 *
 * It listens to the rules of a rules file that are not expressions, asks an
 * {@link EfxExpressionVisitor} for each expression, field context and node context they hold, and
 * implements EfxRulesTranslator to provide the rules translation API.
 */
@SdkComponent(versions = {"2"}, componentType = SdkComponentType.EFX_RULES_TRANSLATOR)
public class EfxRulesTranslatorV2 extends TranslatorListener
    implements EfxRulesTranslator {

  private static final Logger logger = LoggerFactory.getLogger(EfxRulesTranslatorV2.class);

  /** The clauses of a rule that take a condition, as a type error names them. */
  private static final Construct WHEN = Construct.of("'WHEN'", "condition");
  private static final Construct ASSERT = Construct.of("'ASSERT'", "condition");
  private static final Construct REPORT = Construct.of("'REPORT'", "condition");
  private static final Construct OTHERWISE_ASSERT = Construct.of("'OTHERWISE ASSERT'", "condition");
  private static final Construct OTHERWISE_REPORT = Construct.of("'OTHERWISE REPORT'", "condition");

  /**
   * The ValidatorGenerator is used to generate validation output from the intermediate model.
   */
  private final ValidatorGenerator validatorGenerator;

  /**
   * List of validation stages collected during parsing.
   * This is the intermediate model passed to the validator generator.
   */
  private ValidationPlan validationPlan = new ValidationPlan();

  private List<String> cachedSortedNoticeSubtypeIds;

  private final RuleStack ruleStack = new RuleStack();

  /**
   * Tracks dynamic variable dependencies during variable initializer processing.
   * Set to non-null when inside a variable initializer, null otherwise.
   */
  private List<DynamicVariable> currentDynamicDependencies = null;

  /**
   * Constructor for EfxRulesTranslatorV2.
   *
   * @param validatorGenerator The generator for creating validation output.
   * @param symbolResolver The symbol resolver for looking up fields, nodes, and codelists.
   * @param scriptGenerator The script generator for creating XPath expressions.
   * @param errorListener The error listener for capturing parse errors.
   */
  public EfxRulesTranslatorV2(final ValidatorGenerator validatorGenerator,
      final SymbolResolver symbolResolver, final ScriptGenerator scriptGenerator,
      final BaseErrorListener errorListener) {
    super(symbolResolver, scriptGenerator, errorListener);
    this.validatorGenerator = validatorGenerator;
  }

  @Override
  EfxExpressionVisitor visitorOf(final TranslationContext translation) {
    return new RulesExpressionVisitor(translation);
  }

  /**
   * Translates a single expression, as {@link EfxExpressionTranslatorV2} does.
   */
  @Override
  public String translateExpression(final String expression, final String... arguments) {
    return new EfxExpressionTranslatorV2(this.symbols, this.script, this.errorListener)
        .translateExpression(expression, arguments);
  }

  @Override
  public Map<String, String> translateRules(Path pathname, TranslatorOptions options)
      throws IOException {
    logger.debug("Translating EFX rules from file: {}", pathname);
    CharStream input = CharStreams.fromPath(pathname);

    // Default to filesystem-based include resolution relative to the input file
    if (options.getIncludedFileResolver() == null) {
      Path baseDir = pathname.toAbsolutePath().getParent();
      options = TranslatorOptions.withResolver(options, new FileSystemIncludedFileResolver(baseDir));
    }

    return translateRulesFromCharStream(input, options);
  }

  @Override
  public Map<String, String> translateRules(String rules, TranslatorOptions options) {
    logger.debug("Translating EFX rules from string");
    CharStream input = CharStreams.fromString(rules);
    try {
      return translateRulesFromCharStream(input, options);
    } catch (IOException e) {
      throw new UncheckedIOException("Include resolution failed during rules translation", e);
    }
  }

  @Override
  public Map<String, String> translateRules(InputStream stream, TranslatorOptions options)
      throws IOException {
    logger.debug("Translating EFX rules from input stream");
    CharStream input = CharStreams.fromStream(stream);
    return translateRulesFromCharStream(input, options);
  }

  /**
   * Internal method to translate EFX rules from a CharStream.
   *
   * @param input The CharStream containing the EFX rules.
   * @param options The translator options.
   * @return A map of output file paths to generated content.
   * @throws IOException If an I/O error occurs during translation.
   */
  private Map<String, String> translateRulesFromCharStream(CharStream input,
      TranslatorOptions options) throws IOException {
    logger.debug("Parsing EFX rules");

    // Resolve #include directives before parsing
    final CharStream resolvedInput =
        new IncludeProcessor(options.getIncludedFileResolver()).resolve(input);

    // Parse the rules file
    ParseTree tree = this.parserOf(resolvedInput).rulesFile();

    logger.debug("Walking parse tree to build intermediate model");

    // Initialize per-translation state
    this.validationPlan = new ValidationPlan();
    this.apiCallCounter = 0;
    this.ruleStack.clear();

    // Walk the parse tree - this translator IS the listener
    this.translate(tree);

    if (this.validationPlan.getStages().isEmpty()) {
      throw InvalidUsageException.emptyRulesFile();
    }

    // Generate output using the validator generator
    return this.validatorGenerator.generateOutput(this.validationPlan);
  }

  // #region ANTLR Listener Methods for EFX Rules Grammar

  // #region Variable Initializers

  /**
   * Records a variable that an initializer reads, if it is a dynamic variable or depends on one.
   */
  private void trackDynamicDependenciesOf(final String variableName) {
    if (this.currentDynamicDependencies != null) {
      this.stack.getVariable(variableName).ifPresent(var -> {
        if (var instanceof DynamicVariable) {
          this.currentDynamicDependencies.add((DynamicVariable) var);
        } else if (var.hasDynamicDependencies()) {
          this.currentDynamicDependencies.addAll(var.getDynamicDependencies());
        }
      });
    }
  }

  private void beginTrackingDynamicDependencies() {
    this.currentDynamicDependencies = new ArrayList<>();
  }

  private void endTrackingDynamicDependencies() {
    this.currentDynamicDependencies = null;
  }

  private void attachDynamicDependencies(final Variable variable) {
    if (this.currentDynamicDependencies != null && !this.currentDynamicDependencies.isEmpty()) {
      variable.addDynamicDependencies(this.currentDynamicDependencies);
    }
  }

  /**
   * A variable is initialized with a value of its declared type. The dynamic variables its
   * initializer reads become its dynamic dependencies.
   */
  @Override
  public void exitTypedVariableInitializer(TypedVariableInitializerContext ctx) {
    var variableName = ctx.variableName.getText();
    this.beginTrackingDynamicDependencies();
    try {
      var expression = this.expressions.initialValueOf(ctx);
      var variable = new Variable(variableName,
          this.script.composeVariableDeclaration(variableName, expression.getClass()),
          expression,
          this.script.composeVariableReference(variableName, expression.getClass()));
      this.attachDynamicDependencies(variable);
      this.exitVariableInitializer(ctx, variable);
    } finally {
      this.endTrackingDynamicDependencies();
    }
  }

  @Override
  public void exitDynamicFunctionInvocation(DynamicFunctionInvocationContext ctx) {
    FunctionInvocationContext invocation = ctx.functionInvocation();
    String funcName = invocation.functionName.getText();
    var func = this.stack.getFunction(funcName);
    List<? extends TypedExpression> args =
        this.expressions.argumentsOf(invocation, func).getArgumentValues();
    if (!(func instanceof DynamicFunction)) {
      throw InvalidUsageException.notADynamicFunction(ctx, funcName);
    }
    DynamicFunction apiFunc = (DynamicFunction) func;
    var rawCall = this.script.composeDynamicFunction(apiFunc.endpointName, funcName, args);
    this.stack.push(new DynamicExpression(rawCall.getScript()));
    this.stack.push(apiFunc);
  }

  @Override
  public void exitDynamicVariableInitializer(DynamicVariableInitializerContext ctx) {
    String variableName = ctx.variableName.getText();
    var apiFunc = this.stack.pop(DynamicFunction.class);
    var rawCall = this.stack.pop(DynamicExpression.class);
    var declaration = this.script.composeVariableDeclaration(variableName, DynamicExpression.class);
    var varRef = this.script.composeVariableReference(variableName, NumericExpression.class);
    var one = this.script.getNumericLiteralEquivalent("1");
    var referenceExpression = this.script.composeComparisonOperation(varRef, "==", one);
    var variable = new DynamicVariable(variableName, declaration, rawCall, referenceExpression,
        apiFunc.endpointName, apiFunc.errorSeverity, apiFunc.errorLabel);
    this.exitVariableInitializer(ctx, variable);
  }

  /**
   * A variable of a WITH clause is declared at once, so that the initializers after it can read
   * it, and belongs to the rule set. The variable of a global or stage declaration is declared
   * when the declaration exits.
   */
  private void exitVariableInitializer(VariableInitializerContext initializer, Variable variable) {
    if (initializer.getParent() instanceof VariableListContext) {
      this.stack.declareIdentifier(variable);
      this.ruleStack.peek(RuleSet.class).addVariable(variable);
    } else {
      this.stack.push(variable);
    }
  }

  // #endregion Variable Initializers

  // #region Schema-level Variables

  /**
   * Called when exiting a schema-level variable declaration.
   * Declares the variable as a global identifier so it can be referenced later.
   * The variable will be passed to the validator generator for output generation.
   */
  @Override
  public void exitGlobalVariableDeclaration(GlobalVariableDeclarationContext ctx) {
    logger.debug("Processing schema-level variable declaration");

    // The variable initializer exit methods above should have created a Variable
    // object and pushed it onto the stack. Pop it and declare it as global.
    if (!this.stack.empty()) {
      Variable variable = this.stack.pop(Variable.class);

      // Declare the variable in the stack so it can be referenced later
      // and retrieved via stack.getGlobals() for the validator generator
      this.stack.declareGlobalIdentifier(variable);
      this.validationPlan.addVariable(variable);
      logger.debug("Declared global variable: {}", variable.name);
    }
  }

  /**
   * Called when entering a stage section.
   * Each stage section becomes a validation stage in the intermediate model.
   * Pushes a new ValidationStage to the stack and creates a stack frame for scoping.
   */
  @Override
  public void enterValidationStage(ValidationStageContext ctx) {

    this.stack.pushStackFrame();
    this.ruleStack.push(new ValidationStage(ctx.StageIdentifier().getText(), this.validationPlan));
  }

  /**
   * Called when exiting a stage section.
   * Pops the ValidationStage from the stack and adds it to the stages list.
   */
  @Override
  public void exitValidationStage(ValidationStageContext ctx) {
    logger.debug("Exiting STAGE section");

    var stage = this.ruleStack.pop(ValidationStage.class);
    this.validationPlan.addStage(stage);
    this.stack.popStackFrame();
  }

  /**
   * Called when exiting a pattern-level variable declaration.
   * Pattern-level variables are added to the current ValidationStage.
   * Uses peek to access the current stage on the stack.
   */
  @Override
  public void exitStageVariableDeclaration(StageVariableDeclarationContext ctx) {
    logger.debug("Processing pattern-level variable declaration");

    // The variable initializer should have created a Variable object
    if (!this.stack.empty()) {
      Variable variable = this.stack.pop(Variable.class);

      // Declare the variable in the stack so it can be referenced within this stage
      this.stack.declareIdentifier(variable);

      this.ruleStack.peek(ValidationStage.class).addVariable(variable);
    }
  }

  /**
   * Called when entering a rule block.
   * Pushes placeholders for rule components in the correct order.
   * These will be filled in by exit methods and popped in exitRuleBlock.
   */
  @Override
  public void enterRuleSet(RuleSetContext ctx) {
    this.stack.pushStackFrame();
    this.ruleStack.push(new RuleSet(this.ruleStack.peek(ValidationStage.class)));
  }

  /**
   * Called when exiting a rule block.
   * Pops all rule components from the stack, assembles them into a RuleSet,
   * and adds the rule set to the current stage.
   */
  @Override
  public void exitRuleSet(RuleSetContext ctx) {
    logger.debug("Exiting rule block");

    var ruleSet = this.ruleStack.pop(RuleSet.class);
    this.stack.popStackFrame();
    var stage = this.ruleStack.peek(ValidationStage.class);
    stage.addRuleSet(ruleSet);
    assert !this.efxContext.isEmpty() : "Expected context to be set in rule block (WITH clause should have pushed it)";
    this.efxContext.pop();
  }


  @Override
  public void exitContextDeclaration(ContextDeclarationContext ctx) {
    String shortcut = ctx.shortcut != null ? ctx.shortcut.getText() : "none";
    switch (shortcut) {
      case ".":
        throw new UnsupportedOperationException("Same context (.) is not supported in EFX Rules.");
      case "..":
        throw new UnsupportedOperationException("Parent context (..) is not supported in EFX Rules.");
      case "/":
        this.exitRootContextDeclaration();
        break;
      default:
        if (ctx.contextVariableInitializer() != null) {
          Context declared = this.expressions.contextAssignedBy(ctx.contextVariableInitializer());
          Variable contextVariable =
              this.getContextVariable(ctx.contextVariableInitializer(), declared.relativePath());
          this.exitContextDeclaration(declared, contextVariable);
        } else {
          this.exitContextDeclaration(
              this.expressions.contextOf(ctx.getRuleContext(ParserRuleContext.class, 0)), null);
        }
        break;
    }
  }

  /**
   * The context declared is also the context of the rule set.
   */
  @Override
  void declareContext(final Context context, final Variable contextVariable) {
    this.ruleStack.peek(RuleSet.class).setContext(context, contextVariable);
    super.declareContext(context, contextVariable);
  }


  /**
   * Called when exiting a WITH clause.
   * The WITH clause defines the context and local variables for the rule.
   * Pushes a RuleContextInfo object to the stack containing all WITH clause data.
   */
  @Override
  public void exitWithClause(WithClauseContext ctx) {
    logger.debug("Processing WITH clause");
  }


  /**
   * Called when exiting a WHEN clause.
   * The WHEN clause provides conditional application of the rule.
   * Pushes the condition XPath string to the stack.
   */
  @Override
  public void exitWhenClause(WhenClauseContext ctx) {
    var condition = this.expressions.valueAs(ctx.expression(), BooleanExpression.class, WHEN);
    var rule = this.ruleStack.peek(ValidationRule.class);
    var invertedCondition = this.script.composeLogicalNot(condition);
    rule.setCondition(condition, invertedCondition, this.combineWithOrParenthesized(rule.getExpression(), invertedCondition));
  }

  @Override
  public void exitAsClause(AsClauseContext ctx) {
    var rule = this.ruleStack.peek(ValidationRule.class);
    rule.setSeverity(RuleSeverity.fromString(ctx.severity().getText()));
    rule.setId(ctx.ruleId().getText().replaceAll("^\"|\"$", ""));
  }

  /**
   * Called when exiting a FOR clause.
   * The FOR clause specifies the subject (field or node) that the rule validates.
   * Pushes the subject Context to the stack.
   */
  @Override
  public void exitForClause(ForClauseContext ctx) {

    if (ctx.simpleFieldReference() != null) {
      var fieldId = ctx.simpleFieldReference().FieldId().getText();
      this.ruleStack.peek(ValidationRule.class).setSubject(new FieldContext(fieldId, this.symbols.getAbsolutePathOfField(fieldId)));
    } else if (ctx.simpleNodeReference() != null) {
      var nodeId = ctx.simpleNodeReference().NodeId().getText();
      this.ruleStack.peek(ValidationRule.class).setSubject(new NodeContext(nodeId, this.symbols.getAbsolutePathOfNode(nodeId)));
    } else {
      assert false : "The grammar should prevent reaching this point without a field or node reference";
    }
  }

  /**
   * Called when exiting an IN clause.
   * The IN clause specifies which notice subtypes the assertion applies to.
   */
  @Override
  public void exitInClause(InClauseContext ctx) {
    logger.debug("Processing IN clause");

    String compressedList = ctx.noticeTypeList() instanceof AnyNoticeTypesContext ? "*" : ctx.noticeTypeList().getText();
    if (this.cachedSortedNoticeSubtypeIds == null) {
      this.cachedSortedNoticeSubtypeIds = this.symbols.getAllNoticeSubtypes().stream()
          .sorted()
          .map(SdkNoticeSubtype::getId)
          .collect(Collectors.toUnmodifiableList());
    }
    var noticeSubtypes = new NoticeSubtypeRange(compressedList, this.cachedSortedNoticeSubtypeIds);
    this.ruleStack.peek(ValidationRule.class).setNoticeSubtypeRange(noticeSubtypes);
    this.validationPlan.addNoticeSubtypes(noticeSubtypes.asList());
  }

  @Override
  public void exitScopeClause(ScopeClauseContext ctx) {
    var rule = this.ruleStack.peek(ValidationRule.class);
    if (ctx.flag() != null) {
      rule.setFlag(ctx.flag().flagName.getText());
    }
    if (ctx.scopeAnnotation() != null) {
      if (ctx.scopeAnnotation().Pre() != null) {
        rule.setScope(RuleScope.PRE);
      } else {
        rule.setScope(RuleScope.POST);
      }
    }
  }

  /**
   * Called when exiting an ASSERT clause.
   * Build the ValidationRule and wrap it in an AssertRule.
   */
  @Override
  public void exitAssertClause(AssertClauseContext ctx) {
    var test = this.expressions.valueAs(ctx.expression(), BooleanExpression.class, ASSERT);
    var rule = this.ruleStack.pop(ValidationRule.class);
    var invertedCondition = rule.getInvertedCondition();
    rule.setExpression(test, invertedCondition == null ? test : this.combineWithOrParenthesized(test, invertedCondition));
    this.ruleStack.push(new AssertRule(rule));
  }

  @Override
  public void exitReportClause(ReportClauseContext ctx) {
    var test = this.expressions.valueAs(ctx.expression(), BooleanExpression.class, REPORT);
    var rule = this.ruleStack.pop(ValidationRule.class);
    var invertedCondition = rule.getInvertedCondition();
    rule.setExpression(test, invertedCondition == null ? test : this.combineWithOrParenthesized(test, invertedCondition));
    this.ruleStack.push(new ReportRule(rule));
  }

  /**
   * Called when exiting an OTHERWISE clause.
   * Build the ValidationRule and wrap it in an AssertRule.
   */
  @Override
  public void exitOtherwiseAssertClause(OtherwiseAssertClauseContext ctx) {
    var test =
        this.expressions.valueAs(ctx.expression(), BooleanExpression.class, OTHERWISE_ASSERT);
    var rule = this.ruleStack.pop(ValidationRule.class);
    var invertedCondition = rule.getInvertedCondition();
    rule.setExpression(test, invertedCondition == null ? test : this.combineWithOrParenthesized(test, invertedCondition));
    this.ruleStack.push(new AssertRule(rule));
  }

  @Override
  public void exitOtherwiseReportClause(OtherwiseReportClauseContext ctx) {
    var test =
        this.expressions.valueAs(ctx.expression(), BooleanExpression.class, OTHERWISE_REPORT);
    var rule = this.ruleStack.pop(ValidationRule.class);
    var invertedCondition = rule.getInvertedCondition();
    rule.setExpression(test, invertedCondition == null ? test : this.combineWithOrParenthesized(test, invertedCondition));
    this.ruleStack.push(new ReportRule(rule));
  }


  private BooleanExpression combineWithOrParenthesized(BooleanExpression left, BooleanExpression right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return this.script.composeLogicalOr(
        this.script.composeParenthesizedExpression(left, BooleanExpression.class),
        this.script.composeParenthesizedExpression(right, BooleanExpression.class));
  }

  @Override
  public void enterSimpleRule(SimpleRuleContext ctx) {
    this.ruleStack.push(new ValidationRule(this.ruleStack.peek(RuleSet.class)));
  }

  @Override
  public void exitSimpleRule(SimpleRuleContext ctx) {
    var rule = this.ruleStack.pop(ValidationRule.class);
    this.ruleStack.peek(RuleSet.class).addRule(rule);
  }

  @Override
  public void enterConditionalRule(ConditionalRuleContext ctx) {
    this.ruleStack.push(new ValidationRule(this.ruleStack.peek(RuleSet.class)));
  }

  @Override
  public void exitConditionalRule(ConditionalRuleContext ctx) {
    var rule = this.ruleStack.pop(ValidationRule.class);
    this.ruleStack.peek(RuleSet.class).addRule(rule);
  }

  @Override
  public void enterFallbackRule(FallbackRuleContext ctx) {
    this.ruleStack.push(new ValidationRule(this.ruleStack.peek(RuleSet.class)));
  }

  @Override
  public void exitFallbackRule(FallbackRuleContext ctx) {
    var fallbackRule = this.ruleStack.pop(ValidationRule.class);
    var ruleSet = this.ruleStack.pop(RuleSet.class);

    BooleanExpression combined = null;
    for (ValidationRule rule : ruleSet) {
      assert rule != null &&  rule.getCondition() != null : "The EFX grammar should have prevented this. All rules in a RuleSet should have conditions if a fallback is defined.";
      combined = (combined == null) ? rule.getInvertedCondition() : this.script.composeLogicalAnd(combined, rule.getInvertedCondition());
    }
    var invertedCombined = this.script.composeLogicalNot(combined);

    fallbackRule.setCondition(combined, invertedCombined, this.combineWithOrParenthesized(fallbackRule.getExpression(), invertedCombined));

    ruleSet.setFallbackRule(fallbackRule);
    this.ruleStack.push(ruleSet);
  }

  // #region Dynamic Function Declarations

  private static final java.util.regex.Pattern VALID_ENDPOINT_NAME = java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");

  @Override
  public void exitApiEndpointDeclaration(ApiEndpointDeclarationContext ctx) {
    String name = unquote(ctx.endpointName.getText());
    if (!VALID_ENDPOINT_NAME.matcher(name).matches()) {
      throw InvalidUsageException.invalidEndpointName(ctx, name);
    }
    String url = ctx.endpointUrl != null ? unquote(ctx.endpointUrl.getText()) : null;
    this.validationPlan.declareEndpoint(name, url);
  }

  @Override
  public void enterDynamicFunctionDeclaration(DynamicFunctionDeclarationContext ctx) {
    this.stack.pushStackFrame();
    this.stack.push(new ParsedParameters());
  }

  @Override
  public void exitDynamicFunctionDeclaration(DynamicFunctionDeclarationContext ctx) {
    ParsedParameters params = this.stack.pop(ParsedParameters.class);
    this.stack.popStackFrame();
    String apiName = ctx.functionName.getText();
    String endpointName = ctx.endpointName != null ? unquote(ctx.endpointName.getText()) : "default";
    if (!this.validationPlan.getEndpoints().containsKey(endpointName)) {
      throw InvalidIdentifierException.undeclaredEndpoint(ctx, apiName, endpointName);
    }
    RuleSeverity errorSeverity = ctx.onErrorClause() != null && ctx.onErrorClause().Warn() != null
        ? RuleSeverity.WARNING : RuleSeverity.ERROR;
    String errorLabel = ctx.onErrorClause() != null && ctx.onErrorClause().errorLabel != null
        ? unquote(ctx.onErrorClause().errorLabel.getText()) : null;
    this.stack.declareFunction(new DynamicFunction(apiName, params, endpointName, errorSeverity, errorLabel));
  }

  private int apiCallCounter = 0;

  /**
   * Handles an inline dynamic function call in a rule expression.
   * Creates an auto-generated dynamic variable on the current ValidationRule and returns
   * a boolean reference expression ($varName = 1).
   */
  private BooleanExpression composeAutoGeneratedDynamicVariable(FunctionInvocationContext ctx,
      DynamicFunction apiFunc) {
    String funcName = ctx.functionName.getText();

    // Compose the raw dynamic function call.
    List<? extends TypedExpression> args =
        this.expressions.argumentsOf(ctx, apiFunc).getArgumentValues();
    if (!this.ruleStack.contains(ValidationRule.class)) {
      throw InvalidUsageException.dynamicFunctionOutsideRule(ctx, funcName);
    }
    var rawCall = this.script.composeDynamicFunction(apiFunc.endpointName, funcName, args);

    // Compose the variable's expressions: declaration, initialization, and success check ($var == 1).
    String varName = TranslationContext.RESERVED_PREFIX + "apiResult" + (++this.apiCallCounter);
    var declaration = this.script.composeVariableDeclaration(varName, DynamicExpression.class);
    var initExpression = new DynamicExpression(rawCall.getScript());
    var varRef = this.script.composeVariableReference(varName, NumericExpression.class);
    var one = this.script.getNumericLiteralEquivalent("1");
    var referenceExpression = this.script.composeComparisonOperation(varRef, "==", one);

    // Create the auto-generated variable and register it on the current rule.
    var rule = this.ruleStack.peek(ValidationRule.class);
    int index = rule.getAutoGeneratedVariables().size() + 1;
    var variable = new DynamicVariable.AutoGenerated(index, varName, declaration,
        initExpression, referenceExpression, apiFunc.endpointName, apiFunc.errorSeverity,
        apiFunc.errorLabel);
    rule.addAutoGeneratedVariable(variable);

    // Return the success check so it can be used in the rule's condition.
    return referenceExpression;
  }

  private static String unquote(String literal) {
    if (literal.length() >= 2
        && ((literal.startsWith("'") && literal.endsWith("'"))
            || (literal.startsWith("\"") && literal.endsWith("\"")))) {
      return literal.substring(1, literal.length() - 1);
    }
    return literal;
  }

  // #endregion Dynamic Function Declarations

  // #endregion ANTLR Listener Methods


  /**
   * Translates the expressions of a rules file. A call of an API function inside a rule becomes an
   * auto-generated dynamic variable of the rule, and a variable that a variable initializer reads
   * is recorded among the dynamic dependencies of the variable it initializes.
   */
  private final class RulesExpressionVisitor extends EfxExpressionVisitor {

    RulesExpressionVisitor(final TranslationContext translation) {
      super(translation);
    }

    @Override
    public Operand<?> visitFunctionInvocation(final FunctionInvocationContext ctx) {
      final Function function =
          EfxRulesTranslatorV2.this.stack.getFunction(ctx.functionName.getText());
      if (function instanceof DynamicFunction) {
        return this.operand(EfxRulesTranslatorV2.this.composeAutoGeneratedDynamicVariable(ctx,
            (DynamicFunction) function));
      }
      return super.visitFunctionInvocation(ctx);
    }

    @Override
    public Operand<?> visitVariableReference(final VariableReferenceContext ctx) {
      EfxRulesTranslatorV2.this.trackDynamicDependenciesOf(ctx.variableName.getText());
      return super.visitVariableReference(ctx);
    }
  }
}
