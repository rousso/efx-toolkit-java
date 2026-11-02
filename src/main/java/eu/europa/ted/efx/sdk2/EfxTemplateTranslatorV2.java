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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.atn.DecisionInfo;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.atn.ProfilingATNSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ted.eforms.sdk.component.SdkComponent;
import eu.europa.ted.eforms.sdk.component.SdkComponentType;
import eu.europa.ted.efx.exceptions.Construct;
import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.exceptions.InvalidIndentationException;
import eu.europa.ted.efx.interfaces.EfxTemplateTranslator;
import eu.europa.ted.efx.interfaces.MarkupGenerator;
import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.interfaces.SymbolResolver;
import eu.europa.ted.efx.interfaces.TranslatorOptions;
import eu.europa.ted.efx.interfaces.TemplateSection;
import eu.europa.ted.efx.interfaces.TranslatorContext;
import eu.europa.ted.efx.model.Context;
import eu.europa.ted.efx.util.EfxProfilerReportGenerator;
import eu.europa.ted.efx.util.TranslatorTimings;
import eu.europa.ted.efx.model.Context.FieldContext;
import eu.europa.ted.efx.model.Context.NodeContext;
import eu.europa.ted.efx.model.expressions.Expression;
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.DurationExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.iteration.IteratorListExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericPath;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.TimeExpression;
import eu.europa.ted.efx.model.expressions.sequence.DateSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DurationSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.NumericSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.NumericSequencePath;
import eu.europa.ted.efx.model.expressions.sequence.SequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequencePath;
import eu.europa.ted.efx.model.expressions.sequence.TimeSequenceExpression;
import eu.europa.ted.efx.model.templates.ContentTemplate;
import eu.europa.ted.efx.model.templates.ContentTemplateFragment;
import eu.europa.ted.efx.model.templates.DisplayContentTemplate;
import eu.europa.ted.efx.model.templates.FormatContentTemplateFragment;
import eu.europa.ted.efx.model.templates.FormatOptions;
import eu.europa.ted.efx.model.templates.FormatOptions.DateTimeFormatOptions;
import eu.europa.ted.efx.model.templates.FormatOptions.DateTimeStyle;
import eu.europa.ted.efx.model.templates.FormatOptions.DefaultFormatOptions;
import eu.europa.ted.efx.model.templates.FormatOptions.NoFormattingOptions;
import eu.europa.ted.efx.model.templates.FormatOptions.NumberFormatOptions;
import eu.europa.ted.efx.model.templates.HyperlinkContentTemplateFragment;
import eu.europa.ted.efx.model.templates.InvokeContentTemplate;
import eu.europa.ted.efx.model.templates.LabelFromExpressionContentTemplateFragment;
import eu.europa.ted.efx.model.templates.LabelFromKeyContentTemplateFragment;
import eu.europa.ted.efx.model.templates.LineBreakContentTemplateFragment;
import eu.europa.ted.efx.model.templates.TextContentTemplateFragment;
import eu.europa.ted.efx.model.templates.ContentBlock;
import eu.europa.ted.efx.model.templates.ContentBlockStack;
import eu.europa.ted.efx.model.templates.TemplateInvocation;
import eu.europa.ted.efx.model.templates.ViewTemplate;
import eu.europa.ted.efx.model.types.EfxDataType;
import eu.europa.ted.efx.model.types.FieldTypes;
import eu.europa.ted.efx.model.variables.Dictionary;
import eu.europa.ted.efx.model.variables.Function;
import eu.europa.ted.efx.model.variables.ParsedParameters;
import eu.europa.ted.efx.model.variables.Template;
import eu.europa.ted.efx.model.variables.Variable;
import eu.europa.ted.efx.model.variables.Variables;
import eu.europa.ted.efx.sdk2.EfxParser.*;

/**
 * The EfxTemplateTranslator translates EFX templates. It listens to the rules of a template file
 * that are not expressions, and asks an {@link EfxExpressionVisitor} for each expression, field
 * context and node context they hold. Its visitor may call the built-ins that only templates may
 * call, such as the formatting functions.
 */
@SdkComponent(versions = {"2"}, componentType = SdkComponentType.EFX_TEMPLATE_TRANSLATOR)
public class EfxTemplateTranslatorV2 extends TranslatorListener
    implements EfxTemplateTranslator {

  private static final Logger logger = LoggerFactory.getLogger(EfxTemplateTranslatorV2.class);

  private static final String UNEXPECTED_INDENTATION = "Unexpected indentation tracker state.";

  /**
   * The variable that the translator's own loops bind each item to: the numbers, durations,
   * dates and times it formats and the asset ids of a label. It starts with the prefix that no
   * declaration may give, so it cannot hide a variable of the template that a label's asset type
   * or label type reads inside the loop.
   */
  private static final String ITEM_VARIABLE = TranslationContext.RESERVED_PREFIX + "item";

  /** The condition of a conditional template block, as a type error names it. */
  private static final Construct WHEN = Construct.of("'when'", "condition");

  /** The key of a dictionary declaration, as a type error names it. */
  private static final Construct BY = Construct.of("'by'", "key");

  /** The URL of a hyperlink, as a type error names it. */
  private static final Construct HYPERLINK = Construct.of("Hyperlink", "URL");

  /** A label block, as a type error names it with the one of its parts that is at fault. */
  private static final String LABEL_BLOCK = "Label block";

  /** The parts of a label block, each as a type error names it. */
  private static final Construct ASSET_TYPE = Construct.of(LABEL_BLOCK, "asset type");
  private static final Construct LABEL_TYPE = Construct.of(LABEL_BLOCK, "label type");
  private static final Construct ASSET_ID = Construct.of(LABEL_BLOCK, "asset id");
  private static final Construct PLURALISER = Construct.of(LABEL_BLOCK, "pluraliser");
  private static final Construct LABEL_KEY = Construct.of(LABEL_BLOCK, "key");

  private static final String LABEL_TYPE_NAME = getLexerSymbol(EfxLexer.LABEL_TYPE_NAME);
  private static final String LABEL_TYPE_WHEN = getLexerSymbol(EfxLexer.LABEL_TYPE_WHEN_TRUE).replace("-true", "");
  private static final String SHORTHAND_CONTEXT_FIELD_LABEL_REFERENCE = getLexerSymbol(EfxLexer.ValueKeyword);
  private static final String ASSET_TYPE_INDICATOR = getLexerSymbol(EfxLexer.Indicator);
  private static final String ASSET_TYPE_BT = getLexerSymbol(EfxLexer.ASSET_TYPE_BT);
  private static final String ASSET_TYPE_FIELD = getLexerSymbol(EfxLexer.ASSET_TYPE_FIELD);
  private static final String ASSET_TYPE_NODE = getLexerSymbol(EfxLexer.ASSET_TYPE_NODE);
  private static final String ASSET_TYPE_CODE = getLexerSymbol(EfxLexer.Code);

  /**
   * Used to control the indentation style used in a template
   */
  private enum Indent {
    TABS, SPACES, UNDETERMINED
  }

  // Set by the first indented line of each template.
  private Indent indentWith;
  private int indentSpaces;

  /**
   * The MarkupGenerator is called to retrieve markup in the target template language when needed.
   */
  MarkupGenerator markup;

  TranslatorContext translatorContext = new TranslatorContext();

  /**
   * The view template built while the template file is parsed.
   */
  ViewTemplate viewTemplate;

  /**
   * The block stack is used to keep track of the indentation of template lines and adjust the EFX
   * context accordingly. A block is a template line together with the template lines nested
   * (through indentation) under it. At the top of the blockStack is the template block that is
   * currently being processed. The next block in the stack is its parent block, and so on.
   */
  ContentBlockStack blockStack = new ContentBlockStack();

  /**
   * The block of the line above the template line being translated, which the line is nested in:
   * the root of its section or of the declared templates for a line at the top, or none for the
   * first line.
   */
  private ContentBlock blockAbove;

  @SuppressWarnings("unused")
  private EfxTemplateTranslatorV2() {
    this(null, null, null, null);
  }

  public EfxTemplateTranslatorV2(final MarkupGenerator markupGenerator,
      final SymbolResolver symbolResolver, final ScriptGenerator scriptGenerator,
      final BaseErrorListener errorListener) {
    super(symbolResolver, scriptGenerator, errorListener);
    this.markup = markupGenerator;
  }

  @Override
  EfxExpressionVisitor visitorOf(final TranslationContext translation) {
    return new TemplateExpressionVisitor(translation);
  }

  /**
   * Translates a single expression, in which the built-ins that only templates may call may be
   * called.
   */
  @Override
  public String translateExpression(final String expression, final String... arguments) {
    return new EfxExpressionTranslatorV2(this.symbols, this.script, this.errorListener,
        TemplateExpressionVisitor::new).translateExpression(expression, arguments);
  }

  /**
   * Opens the indicated EFX file and translates the EFX template it contains.
   */
  @Override
  public String renderTemplate(final Path pathname, TranslatorOptions options) throws IOException {
    // Default to filesystem-based include resolution relative to the input file
    if (options.getIncludedFileResolver() == null) {
      Path baseDir = pathname.toAbsolutePath().getParent();
      options = TranslatorOptions.withResolver(options, new FileSystemIncludedFileResolver(baseDir));
    }

    return renderTemplate(CharStreams.fromPath(pathname), options);
  }

  /**
   * Translates the template contained in the string passed as a parameter.
   */
  @Override
  public String renderTemplate(final String template, TranslatorOptions options) {
    try {
      return renderTemplate(CharStreams.fromString(template), options);
    } catch (IOException e) {
      throw new UncheckedIOException("Include resolution failed during template rendering", e);
    }
  }

  @Override
  public String renderTemplate(final InputStream stream, TranslatorOptions options) throws IOException {
    return renderTemplate(CharStreams.fromStream(stream), options);
  }

  private String renderTemplate(final CharStream charStream, TranslatorOptions options)
      throws IOException {
    logger.debug("Rendering template");
    final long startTime = System.currentTimeMillis();

    // Resolve #include directives before parsing
    final CharStream resolvedInput =
        new IncludeProcessor(options.getIncludedFileResolver()).resolve(charStream);

    final EfxParser parser = this.parserOf(resolvedInput);

    // Enable profiling if requested
    if (options != null && options.isProfilerEnabled()) {
      parser.setInterpreter(new ProfilingATNSimulator(parser));
    }

    this.translate(parser.templateFile());
    assert this.stack.empty() : "Stack should be empty at this point.";

    final String output = this.viewTemplate.render(this.markup, this.translatorContext).script.trim();

    final long endTime = System.currentTimeMillis();
    final long totalDuration = endTime - startTime;

    // Log profiling information if enabled
    if (options != null && options.isProfilerEnabled()) {
      TranslatorTimings timingData = new TranslatorTimings(0, totalDuration, totalDuration);
      this.generateAndSaveProfilerReport(parser, options.getProfilerOutputPath(), timingData);
    }

    logger.debug("Finished rendering template");

    return output;
  }

  /**
   * Logs ANTLR4 profiling results showing which grammar rules took the most time.
   * Only active when profiling is enabled via system property.
   * 
   * @param parser The EfxParser instance to extract profiling data from
   * @param profilingOutputPath Path where the HTML report should be saved
   * @param timingData Timing measurements for different processing phases
   */
  private void generateAndSaveProfilerReport(final EfxParser parser, final Path profilingOutputPath, final TranslatorTimings timingData) {
    final ParseInfo parseInfo = parser.getParseInfo();
    if (parseInfo == null) {
      logger.warn("ParseInfo not available - profiling may not be enabled in parser");
      return;
    }

    final DecisionInfo[] decisions = parseInfo.getDecisionInfo();
    
    // Sort decisions by time descending
    java.util.Arrays.sort(decisions, (a, b) -> Long.compare(b.timeInPrediction, a.timeInPrediction));
    
    long totalTime = java.util.Arrays.stream(decisions)
        .mapToLong(d -> d.timeInPrediction)
        .sum();

    // Write HTML report to file if path is provided
    EfxProfilerReportGenerator.generateAndSaveProfilerReport(parser, decisions, totalTime, timingData, profilingOutputPath);
  }


  // #region Global declarationExpressions ---------------------------------------

  @Override
  public void exitVariableDeclaration(VariableDeclarationContext ctx) {
    var variable = this.stack.pop(Variable.class);
    this.stack.declareGlobalIdentifier(variable);
    this.viewTemplate.addVariable(variable);
  }

  @Override
  public void enterFunctionDeclaration(FunctionDeclarationContext ctx) {
    this.stack.push(new ParsedParameters());
  }

  /**
   * Declares a function. Its body must give a value of the declared type.
   */
  @Override
  public void exitFunctionDeclaration(FunctionDeclarationContext ctx) {
    var expression = this.expressions.valueAs(ctx.expression(),
        EfxExpressionVisitor.typeOf(ctx.typeDeclaration()),
        Construct.of("Function '" + ctx.typeDeclaration().getText() + ":?"
            + ctx.functionName.getText() + "'", "value"));
    var parameters = this.stack.pop(ParsedParameters.class);
    var function = new Function(ctx.functionName.getText(), parameters, expression);
    this.stack.declareFunction(function);
    this.viewTemplate.addFunction(function);
  }

  @Override
  public void exitTemplateDefinition(TemplateDefinitionContext ctx) {
    var parameters = this.stack.pop(ParsedParameters.class);
    var template = new Template(ctx.templateName.getText(), parameters);
    this.stack.declareTemplate(template);
    this.stack.push(template);
  }

  @Override
  public void enterTemplateDefinition(TemplateDefinitionContext ctx) {
    this.stack.push(new ParsedParameters());
  }

  // #endregion Global declarationExpressions ------------------------------------

  // #region Template File ----------------------------------------------------

  @Override
  public void enterTemplateFile(TemplateFileContext ctx) {
    assert this.blockStack.isEmpty() : UNEXPECTED_INDENTATION;
    this.viewTemplate = new ViewTemplate();
    this.translatorContext = new TranslatorContext();
    this.indentWith = Indent.UNDETERMINED;
    this.indentSpaces = -1;
  }

  @Override
  public void exitTemplateFile(TemplateFileContext ctx) {
    // The end of the file closes the levels still open, as a line back at level 0 would: the block of
    // each nested level and its stack frame.
    while (this.blockStack.currentIndentationLevel() > 0) {
      this.blockStack.pop();
      this.stack.popStackFrame();
    }
    // If there are template lines, the top-level block of the last one is left; so we remove it here.
    if (!this.blockStack.isEmpty()) {
      this.blockStack.pop();
    }
  }

  // #endregion Template File -------------------------------------------------
  
  // #region Source template blocks -------------------------------------------

  @Override
  public void exitTextTemplate(TextTemplateContext ctx) {
    DisplayContentTemplate template = ctx.templateFragment() != null ? this.stack.pop(DisplayContentTemplate.class) : new DisplayContentTemplate();
    String text = ctx.textBlock() != null ? ctx.textBlock().getText() : "";
    template.prepend(new TextContentTemplateFragment(text));
    this.stack.push(template);
  }

  @Override
  public void exitLinkedTextTemplate(LinkedTextTemplateContext ctx) {
    DisplayContentTemplate template = ctx.templateFragment() != null ? this.stack.pop(DisplayContentTemplate.class) : new DisplayContentTemplate();
    ContentTemplateFragment text = this.stack.pop(ContentTemplateFragment.class);
    template.prepend(text);
    this.stack.push(template);
  }

  @Override
  public void exitLabelTemplate(LabelTemplateContext ctx) {
    DisplayContentTemplate template = ctx.templateFragment() != null ? this.stack.pop(DisplayContentTemplate.class) : new DisplayContentTemplate();
    if (ctx.labelBlock() != null) {
      template.prepend(this.stack.pop(ContentTemplateFragment.class));
    }
    this.stack.push(template);
  }

  @Override
  public void exitLinkedLabelTemplate(LinkedLabelTemplateContext ctx) {
    DisplayContentTemplate template = ctx.templateFragment() != null ? this.stack.pop(DisplayContentTemplate.class) : new DisplayContentTemplate();
    ContentTemplateFragment label = this.stack.pop(ContentTemplateFragment.class);
    template.prepend(label);
    this.stack.push(template);
  }

  // #region New in EFX-2: Formatted expression blocks -------------------------

  @Override
  public void exitExpressionTemplate(ExpressionTemplateContext ctx) {
    DisplayContentTemplate template = ctx.templateFragment() != null ? this.stack.pop(DisplayContentTemplate.class) : new DisplayContentTemplate();
    template.prepend(this.stack.pop(FormatContentTemplateFragment.class));
    this.stack.push(template);
  }

  @Override
  public void exitLinkedExpressionTemplate(LinkedExpressionTemplateContext ctx) {
    DisplayContentTemplate template = ctx.templateFragment() != null ? this.stack.pop(DisplayContentTemplate.class) : new DisplayContentTemplate();
    template.prepend(this.stack.pop(HyperlinkContentTemplateFragment.class));
    this.stack.push(template);
  }

  /**
   * Formats the value of an expression: ${BT-00-Number * 2}, or with options, ${BT-00-Number * 2|2}. A
   * calculated duration is the only value of an expression that has a unit, and it is formatted as a
   * number.
   */
  @Override
  public void exitComputedExpressionBlock(ComputedExpressionBlockContext ctx) {
    FormatOptions options = ctx.formatOptions() != null ? this.stack.pop(FormatOptions.class) : new DefaultFormatOptions(false);
    TypedExpression value = this.expressions.valueOf(ctx.expression(), EXPRESSION_BLOCK);
    Optional<NumberFormatOptions> numberOptions = options.forNumbers();
    if (value.is(EfxDataType.DurationScalar.class) && numberOptions.isPresent()) {
      this.stack.push(this.createFormattedExpressionBlockFragment(TypedExpression.from(value, DurationExpression.class),
          numberOptions.get()));
    } else if (value.is(EfxDataType.DurationSequence.class) && numberOptions.isPresent()) {
      this.stack.push(this.createFormattedExpressionBlockFragment(TypedExpression.from(value, DurationSequenceExpression.class),
          numberOptions.get()));
    } else {
      this.stack.push(this.createFormattedExpressionBlockFragment(value, options));
    }
  }

  /**
   * Formats the value of a field: ${BT-00-Number}, or with options, ${BT-00-Number|2}.
   */
  @Override
  public void exitFieldExpressionBlock(FieldExpressionBlockContext ctx) {
    FormatOptions options = ctx.formatOptions() != null ? this.stack.pop(FormatOptions.class) : new DefaultFormatOptions(false);
    this.stack.push(this.createFormattedExpressionBlockFragment(ctx.FieldId().getText(), options));
  }

  /**
   * Formats the value of the context field: $value. It has no options.
   */
  @Override
  public void exitContextFieldExpressionBlock(ContextFieldExpressionBlockContext ctx) {
    if (!this.efxContext.isFieldContext()) {
      throw InvalidUsageException.shorthandRequiresFieldContext(ctx, "$value");
    }
    this.stack.push(this.createFormattedExpressionBlockFragment(this.efxContext.symbol(), new DefaultFormatOptions(false)));
  }

  /**
   * Reads the format options once, for the expression block that uses them.
   */
  @Override
  public void exitFormatOptions(FormatOptionsContext ctx) {
    boolean noUnit = ctx.FormatNoUnit() != null;
    FormatSpecifierContext specifier = ctx.formatSpecifier();
    if (ctx.FormatNoFormatting() != null) {
      this.stack.push(new NoFormattingOptions());
    } else if (specifier == null) {
      this.stack.push(new DefaultFormatOptions(noUnit));
    } else if (specifier.FormatDecimals() != null) {
      this.stack.push(new NumberFormatOptions(Integer.parseInt(specifier.FormatDecimals().getText()), noUnit));
    } else if (specifier.FormatShort() != null) {
      this.stack.push(new DateTimeFormatOptions(DateTimeStyle.SHORT, noUnit));
    } else if (specifier.FormatMedium() != null) {
      this.stack.push(new DateTimeFormatOptions(DateTimeStyle.MEDIUM, noUnit));
    } else if (specifier.FormatLong() != null) {
      this.stack.push(new DateTimeFormatOptions(DateTimeStyle.LONG, noUnit));
    }
  }

  @Override
  public void exitLinkedExpressionBlock(LinkedExpressionBlockContext ctx) {
    var url =
        this.expressions.valueAs(ctx.linkBlock().expression(), StringExpression.class, HYPERLINK);
    var value = this.stack.pop(FormatContentTemplateFragment.class);
    this.stack.push(new HyperlinkContentTemplateFragment(value, url));
  }

  /**
   * Creates the fragment of an expression block that displays the value of a field. Only the type of the field
   * tells whether its value has a unit: amounts, measures and durations do. The value of any other field
   * is formatted like the value of an expression. No-formatting displays the value of the field as entered:
   * the text of a number, a date, a time or a duration, and the value of a text, which is its text.
   */
  private FormatContentTemplateFragment createFormattedExpressionBlockFragment(String fieldId, FormatOptions options) {
    PathExpression field = this.symbols.getRelativePathOfField(fieldId, this.efxContext.symbol());
    if (!options.formatsValue()) {
      return new FormatContentTemplateFragment(field.is(EfxDataType.String.class)
          ? this.expressions.fieldValueOf(fieldId, field)
          : this.script.composeFieldRawValueReference(field));
    }
    String fieldType = this.symbols.getTypeOfField(fieldId);
    switch (FieldTypes.fromString(fieldType)) {
      case DURATION:
      case MEASURE:
      case AMOUNT:
        return this.createFormattedExpressionBlockFragment(field,
            this.symbols.getAttributeFieldIdOfField(fieldId), options);
      default:
        return this.createFormattedExpressionBlockFragment(this.expressions.fieldValueOf(fieldId, field), options);
    }
  }

  /**
   * Creates the fragment of an expression block for a field with a unit: the number entered, followed by its
   * unit unless no-unit is given. The number is also the quantity that selects the plural form of the unit.
   * A unit can only be displayed with a single value, so the numbers of a repeatable field are displayed
   * without it, and only if no-unit is given.
   *
   * @param unitFieldId The attribute field that holds the unit, from its code list.
   */
  private FormatContentTemplateFragment createFormattedExpressionBlockFragment(PathExpression field, String unitFieldId,
      FormatOptions options) {
    if (field instanceof SequenceExpression) {
      if (!options.hideUnit()) {
        throw InvalidUsageException.unitForList(this.stack.peekParserContext());
      }
      PathExpression numbers = this.script.composeFieldValueReference(new NumericSequencePath(field.getScript()));
      return new FormatContentTemplateFragment(this.composeFormattedExpression(numbers, options));
    }
    NumericExpression number = TypedExpression.from(
        this.script.composeFieldValueReference(new NumericPath(field.getScript())), NumericExpression.class);
    SequenceExpression unitCode =
        this.composeGetUnitCode(field, this.symbols.getAttributeNameFromAttributeField(unitFieldId));
    return new FormatContentTemplateFragment(this.composeFormattedExpression(number, options),
        this.composeUnitSeparator(unitCode),
        this.composeCodeLabelKey(this.symbols.getRootCodelistOfField(unitFieldId), unitCode),
        number, options.hideUnit());
  }

  /**
   * Creates the fragment of an expression block for a calculated duration. It has no unit entered with it, so
   * it is displayed as a number of months or days, in the unit it is calculated in. That number also
   * selects the plural form of the unit. It has no field either, so the code list of its unit is the one that
   * the duration data type defines for its supplementary component.
   */
  private FormatContentTemplateFragment createFormattedExpressionBlockFragment(DurationExpression duration, NumberFormatOptions options) {
    NumericExpression number = this.composeGetNumberFromDuration(duration);
    String unitCodelistId = this.symbols.getAttributeFieldCodelistIdFromDataType(FieldTypes.DURATION.getName());
    SequenceExpression unitCode = this.composeGetUnitCode(duration);
    return new FormatContentTemplateFragment(this.composeFormattedScalar(number, options),
        this.composeUnitSeparator(unitCode), this.composeCodeLabelKey(unitCodelistId, unitCode),
        number, options.hideUnit());
  }

  /**
   * Creates the fragment of an expression block for a sequence of calculated durations. A unit can only be
   * displayed with a single value, so each duration is displayed as its number of months or days, and
   * only if no-unit is given.
   */
  private FormatContentTemplateFragment createFormattedExpressionBlockFragment(DurationSequenceExpression durations,
      NumberFormatOptions options) {
    if (!options.hideUnit()) {
      throw InvalidUsageException.unitForList(this.stack.peekParserContext());
    }
    return new FormatContentTemplateFragment(this.composeFormattedSequence(durations, options));
  }

  /**
   * Creates the fragment of an expression block for a value that has no unit, so no-unit is not applicable.
   * No-formatting displays the value as it is.
   */
  private FormatContentTemplateFragment createFormattedExpressionBlockFragment(TypedExpression value, FormatOptions options) {
    if (!options.formatsValue()) {
      return new FormatContentTemplateFragment(value);
    }
    if (options.hideUnit()) {
      throw InvalidUsageException.noUnitNotApplicable(this.stack.peekParserContext());
    }
    return new FormatContentTemplateFragment(this.composeFormattedExpression(value, options));
  }

  /**
   * Formats a value, or each value of a sequence, according to its type. Format options are a shorthand
   * for the formatting functions: ${BT-00-Number|2} displays the same as
   * ${format-number(BT-00-Number, '#,##0.00')}, and ${BT-00-Date|medium} the same as
   * ${format-medium(BT-00-Date)}. A number is formatted with options for numbers, and a date or a time
   * with options for dates and times. Any other value is displayed as it is, unless the options give a
   * number of decimals or a style, which it cannot be formatted with.
   */
  private Expression composeFormattedExpression(TypedExpression value, FormatOptions options) {
    Optional<NumberFormatOptions> numberOptions = options.forNumbers();
    Optional<DateTimeFormatOptions> dateTimeOptions = options.forDatesAndTimes();
    if (value.is(EfxDataType.NumberScalar.class) && numberOptions.isPresent()) {
      return this.composeFormattedScalar(TypedExpression.from(value, NumericExpression.class), numberOptions.get());
    } else if (value.is(EfxDataType.NumberSequence.class) && numberOptions.isPresent()) {
      return this.composeFormattedSequence(TypedExpression.from(value, NumericSequenceExpression.class),
          numberOptions.get());
    } else if (value.is(EfxDataType.DateScalar.class) && dateTimeOptions.isPresent()) {
      return this.composeFormattedScalar(TypedExpression.from(value, DateExpression.class), dateTimeOptions.get());
    } else if (value.is(EfxDataType.DateSequence.class) && dateTimeOptions.isPresent()) {
      return this.composeFormattedSequence(TypedExpression.from(value, DateSequenceExpression.class),
          dateTimeOptions.get());
    } else if (value.is(EfxDataType.TimeScalar.class) && dateTimeOptions.isPresent()) {
      return this.composeFormattedScalar(TypedExpression.from(value, TimeExpression.class), dateTimeOptions.get());
    } else if (value.is(EfxDataType.TimeSequence.class) && dateTimeOptions.isPresent()) {
      return this.composeFormattedSequence(TypedExpression.from(value, TimeSequenceExpression.class),
          dateTimeOptions.get());
    }
    if (options.hasSpecifier()) {
      throw InvalidUsageException.invalidFormatOptions(this.stack.peekParserContext());
    }
    return value;
  }

  /**
   * Formats a number like a sequence of at most one number: format-number would display an absent number as
   * NaN, whereas an absent number is not displayed at all.
   */
  private StringSequenceExpression composeFormattedScalar(NumericExpression number, NumberFormatOptions options) {
    return this.composeFormattedSequence(new NumericSequenceExpression(number.getScript()), options);
  }

  private StringSequenceExpression composeFormattedSequence(NumericSequenceExpression numbers, NumberFormatOptions options) {
    IteratorListExpression iterators = this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
        this.script.composeVariableDeclaration(ITEM_VARIABLE, NumericExpression.class), numbers)));
    return this.script.composeForExpression(iterators,
        this.composeFormattedNumber(
            this.script.composeVariableReference(ITEM_VARIABLE, NumericExpression.class), options),
        StringSequenceExpression.class);
  }

  private StringSequenceExpression composeFormattedSequence(DurationSequenceExpression durations,
      NumberFormatOptions options) {
    IteratorListExpression iterators = this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
        this.script.composeVariableDeclaration(ITEM_VARIABLE, DurationExpression.class),
        durations)));
    return this.script.composeForExpression(iterators,
        this.composeFormattedNumber(
            this.composeGetNumberFromDuration(
                this.script.composeVariableReference(ITEM_VARIABLE, DurationExpression.class)),
            options),
        StringSequenceExpression.class);
  }

  /**
   * Formats a number that exists.
   */
  private StringExpression composeFormattedNumber(NumericExpression number, NumberFormatOptions options) {
    return this.script.composeNumberFormatting(number,
        this.script.getStringLiteralFromUnquotedString(options.getNumberPattern()));
  }

  private StringExpression composeFormattedScalar(DateExpression date, DateTimeFormatOptions options) {
    switch (options.getStyle()) {
      case MEDIUM:
        return this.script.composeFormatDateMedium(date);
      case LONG:
        return this.script.composeFormatDateLong(date);
      default:
        return this.script.composeFormatDateShort(date);
    }
  }

  private StringSequenceExpression composeFormattedSequence(DateSequenceExpression dates, DateTimeFormatOptions options) {
    IteratorListExpression iterators = this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
        this.script.composeVariableDeclaration(ITEM_VARIABLE, DateExpression.class), dates)));
    return this.script.composeForExpression(iterators,
        this.composeFormattedScalar(
            this.script.composeVariableReference(ITEM_VARIABLE, DateExpression.class), options),
        StringSequenceExpression.class);
  }

  private StringExpression composeFormattedScalar(TimeExpression time, DateTimeFormatOptions options) {
    switch (options.getStyle()) {
      case MEDIUM:
        return this.script.composeFormatTimeMedium(time);
      case LONG:
        return this.script.composeFormatTimeLong(time);
      default:
        return this.script.composeFormatTimeShort(time);
    }
  }

  private StringSequenceExpression composeFormattedSequence(TimeSequenceExpression times, DateTimeFormatOptions options) {
    IteratorListExpression iterators = this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
        this.script.composeVariableDeclaration(ITEM_VARIABLE, TimeExpression.class), times)));
    return this.script.composeForExpression(iterators,
        this.composeFormattedScalar(
            this.script.composeVariableReference(ITEM_VARIABLE, TimeExpression.class), options),
        StringSequenceExpression.class);
  }

  /**
   * Returns the number of a calculated duration: its months if it is calculated in years and months,
   * otherwise its whole days, as days() does.
   */
  private NumericExpression composeGetNumberFromDuration(DurationExpression duration) {
    return this.script.composeConditionalExpression(this.composeIsYearMonthDuration(duration),
        this.script.composeMonthsFromDurationFunction(duration),
        this.script.composeDaysFromDurationFunction(duration), NumericExpression.class);
  }

  /**
   * Tests whether a duration is a year-month duration. EFX calculates a duration either in years and
   * months or in days, never in both, so a year-month duration is one that has months. The script
   * generator has no test for the kind of a duration, so a duration of zero counts as a day duration.
   */
  private BooleanExpression composeIsYearMonthDuration(DurationExpression duration) {
    return this.script.composeComparisonOperation(this.script.composeMonthsFromDurationFunction(duration), "!=",
        this.script.getNumericLiteralEquivalent("0"));
  }

  /**
   * Returns the unit code entered with the value of a field, from the given attribute. An absent value has
   * none.
   */
  private SequenceExpression composeGetUnitCode(PathExpression field, String attribute) {
    return this.script.composeFieldAttributeReference(field, attribute, StringSequencePath.class);
  }

  /**
   * Returns the unit code of a calculated duration, in the duration-unit code list: MONTH or DAY. An absent
   * duration has none.
   */
  private StringSequenceExpression composeGetUnitCode(DurationExpression duration) {
    IteratorListExpression iterators = this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
        this.script.composeVariableDeclaration(ITEM_VARIABLE, DurationExpression.class),
        new DurationSequenceExpression(duration.getScript()))));
    return this.script.composeForExpression(iterators,
        this.script.composeConditionalExpression(
            this.composeIsYearMonthDuration(
                this.script.composeVariableReference(ITEM_VARIABLE, DurationExpression.class)),
            this.script.getStringLiteralFromUnquotedString("MONTH"),
            this.script.getStringLiteralFromUnquotedString("DAY"), StringExpression.class),
        StringSequenceExpression.class);
  }

  /**
   * Returns the space that separates a unit from its value, for each unit code: none when there is no code.
   */
  private StringSequenceExpression composeUnitSeparator(SequenceExpression code) {
    IteratorListExpression iterators = this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
        this.script.composeVariableDeclaration("code", StringExpression.class), code)));
    return this.script.composeForExpression(iterators, this.script.getStringLiteralFromUnquotedString(" "),
        StringSequenceExpression.class);
  }

  /**
   * Returns the label key of each code: code|name|codelist.code. There is none when there is no code.
   */
  private StringSequenceExpression composeCodeLabelKey(String codelist, SequenceExpression code) {
    IteratorListExpression iterators = this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
        this.script.composeVariableDeclaration("code", StringExpression.class), code)));
    return this.script.composeForExpression(iterators, this.script.composeStringConcatenation(List.of(
        this.script.getStringLiteralFromUnquotedString(ASSET_TYPE_CODE),
        this.script.getStringLiteralFromUnquotedString("|"),
        this.script.getStringLiteralFromUnquotedString(LABEL_TYPE_NAME),
        this.script.getStringLiteralFromUnquotedString("|"),
        this.script.getStringLiteralFromUnquotedString(codelist + "."),
        this.script.composeVariableReference("code", StringExpression.class))),
        StringSequenceExpression.class);
  }

  // #endregion New in EFX-2: Formatted expression blocks ----------------------

  // #region New in EFX-2: Secondary templates --------------------------------

  @Override
  public void exitSecondaryTemplate(SecondaryTemplateContext ctx) {
    DisplayContentTemplate template = ctx.templateFragment() != null ? this.stack.pop(DisplayContentTemplate.class) : new DisplayContentTemplate();
    template.prepend(new LineBreakContentTemplateFragment());
    this.stack.push(template);
  }

  // #endregion New in EFX-2: Secondary templates -----------------------------

  // #endregion Source template blocks ----------------------------------------
  
  // #region Label Blocks #{...} ----------------------------------------------

  @Override
  public void exitStandardLabelReference(StandardLabelReferenceContext ctx) {

    // New in EFX-2: Pluralisation of labels based on a supplied quantity.
    // The quantity is on top of the stack, above the assetId, so it is popped first.
    NumericExpression quantity = ctx.pluraliser() != null
        ? this.labelPart(ctx, NumericExpression.class, PLURALISER)
        : NumericExpression.empty();

    if (!this.stack.empty() && StringSequenceExpression.class.isAssignableFrom(this.stack.peek().getClass()) && ctx.assetId() != null) {

      // TODO: Review this in EFX-2

      // This is a workaround that allows EFX 1 to render a sequence of labels without a special
      // syntax. When a standard label reference is processed, the template translator checks if
      // the assetId is provided with a SequenceExpression. If this is the case, then the
      // translator generates the appropriate code to render a sequence of labels.
      //
      // For example, this will render a sequence of labels for a label reference of the form
      // #{assetType|labelType|${for text:$t in ('assetId1','assetId2') return $t}}:} 
      // The only restriction is that the assetType and labelType must be the same for all labels in the sequence.

      StringSequenceExpression assetIdSequence = this.stack.pop(StringSequenceExpression.class);
      this.exitStandardLabelReference(ctx, assetIdSequence, quantity);
    } else {

      // Standard implementation as originally intended by EFX 1

      StringExpression assetId = ctx.assetId() != null
          ? this.labelPart(ctx, StringExpression.class, ASSET_ID)
          : this.script.getStringLiteralFromUnquotedString("");
      this.exitStandardLabelReference(ctx, assetId, quantity);
    }
  }
  
  /**
   * Renders a single label from a standard label reference.
   * 
   * @param ctx     The ParserRuleContext of the standard label reference.
   * @param assetId The assetId of the label to render.
   */
  private void exitStandardLabelReference(StandardLabelReferenceContext ctx, StringExpression assetId, NumericExpression quantity) {
    StringExpression labelType = ctx.labelType() != null
        ? this.labelPart(ctx, StringExpression.class, LABEL_TYPE)
        : this.script.getStringLiteralFromUnquotedString("");
    StringExpression assetType = ctx.assetType() != null
        ? this.labelPart(ctx, StringExpression.class, ASSET_TYPE)
        : this.script.getStringLiteralFromUnquotedString("");

    StringExpression key = this.script.composeStringConcatenation(
        List.of(assetType, this.script.getStringLiteralFromUnquotedString("|"), labelType,
            this.script.getStringLiteralFromUnquotedString("|"), assetId));
    this.stack.push(new LabelFromKeyContentTemplateFragment(key, quantity));
  }

  /**
   * Returns the variable that a label's loop binds each of its asset ids to.
   */
  private Variable composeAssetIdVariable() {
    return new Variable(ITEM_VARIABLE,
        this.script.composeVariableDeclaration(ITEM_VARIABLE, StringExpression.class),
        StringExpression.empty(),
        this.script.composeVariableReference(ITEM_VARIABLE, StringExpression.class));
  }

  /**
   * Renders a sequence of labels from a standard label reference.
   * 
   * @param ctx             The ParserRuleContext of the standard label reference.
   * @param assetIdSequence The sequence of assetIds for the labels to render.
   * @param quantity        The quantity used to select the plural form of the labels, or an
   *                        empty expression if no pluraliser was given.
   */
  private void exitStandardLabelReference(StandardLabelReferenceContext ctx, StringSequenceExpression assetIdSequence,
      NumericExpression quantity) {
    StringExpression labelType = ctx.labelType() != null
        ? this.labelPart(ctx, StringExpression.class, LABEL_TYPE)
        : this.script.getStringLiteralFromUnquotedString("");
    StringExpression assetType = ctx.assetType() != null
        ? this.labelPart(ctx, StringExpression.class, ASSET_TYPE)
        : this.script.getStringLiteralFromUnquotedString("");

    Variable loopVariable = this.composeAssetIdVariable();

    StringSequenceExpression keys = this.script.composeDistinctValuesFunction(
        this.script.composeForExpression(
            this.script.composeIteratorList(
                List.of(
                    this.script.composeIteratorExpression(loopVariable.declarationExpression, assetIdSequence))),
            this.script.composeStringConcatenation(List.of(
                assetType,
                this.script.getStringLiteralFromUnquotedString("|"),
                labelType,
                this.script.getStringLiteralFromUnquotedString("|"),
                new StringExpression(loopVariable.referenceExpression.getScript()))),
            StringSequenceExpression.class),
        StringSequenceExpression.class);
    this.stack.push(new LabelFromExpressionContentTemplateFragment(keys, quantity));
  }


  @Override
  public void exitShorthandBtLabelReference(ShorthandBtLabelReferenceContext ctx) {
    NumericExpression quantity = ctx.pluraliser() != null
        ? this.labelPart(ctx, NumericExpression.class, PLURALISER)
        : NumericExpression.empty();
    StringExpression assetId = this.script.getStringLiteralFromUnquotedString(ctx.BtId().getText());
    StringExpression labelType = ctx.labelType() != null
        ? this.labelPart(ctx, StringExpression.class, LABEL_TYPE)
        : this.script.getStringLiteralFromUnquotedString("");
    StringExpression key = this.script.composeStringConcatenation(
        List.of(this.script.getStringLiteralFromUnquotedString(ASSET_TYPE_BT),
            this.script.getStringLiteralFromUnquotedString("|"), labelType,
            this.script.getStringLiteralFromUnquotedString("|"), assetId));
    this.stack.push(new LabelFromKeyContentTemplateFragment(key, quantity));
  }

  @Override
  public void exitShorthandFieldLabelReference(ShorthandFieldLabelReferenceContext ctx) {
    final String fieldId = ctx.FieldId().getText();
    NumericExpression quantity = ctx.pluraliser() != null
        ? this.labelPart(ctx, NumericExpression.class, PLURALISER)
        : NumericExpression.empty();
    StringExpression labelType = ctx.labelType() != null
        ? this.labelPart(ctx, StringExpression.class, LABEL_TYPE)
        : this.script.getStringLiteralFromUnquotedString("");

    if (labelType.getScript().equals("value")) {
      this.shorthandIndirectLabelReference(fieldId, quantity);
    } else {
      StringExpression key = this.script.composeStringConcatenation(
          List.of(this.script.getStringLiteralFromUnquotedString(ASSET_TYPE_FIELD),
              this.script.getStringLiteralFromUnquotedString("|"), labelType,
              this.script.getStringLiteralFromUnquotedString("|"),
              this.script.getStringLiteralFromUnquotedString(fieldId)));
      this.stack.push(new LabelFromKeyContentTemplateFragment(key, quantity));
    }
  }

  @Override
  public void exitShorthandIndirectLabelReference(ShorthandIndirectLabelReferenceContext ctx) {
    // New in EFX-2: Pluralisation of labels based on a supplied quantity
    NumericExpression quantity = ctx.pluraliser() != null
        ? this.labelPart(ctx, NumericExpression.class, PLURALISER)
        : NumericExpression.empty();
    this.shorthandIndirectLabelReference(ctx.FieldId().getText(), quantity);
  }

  /**
   * Renders the label of the value of a code or indicator field. Without a pluraliser, the label of
   * the unit of an amount, a measure or a duration is pluralised by the number that it is the unit of.
   */
  private void shorthandIndirectLabelReference(final String fieldId, final NumericExpression pluraliser) {
    final Context currentContext = this.efxContext.peek();
    final NumericExpression quantity = pluraliser.isEmpty() ? this.composeQuantityOfUnit(fieldId) : pluraliser;
    final String fieldType = this.symbols.getTypeOfField(fieldId);
    final PathExpression valueReference = this.expressions.fieldValueOf(fieldId,
        this.symbols.getRelativePathOfField(fieldId, currentContext.symbol()));
    Variable loopVariable = this.composeAssetIdVariable();
    switch (fieldType) {
      case "indicator": {
        StringSequenceExpression keys = this.script.composeDistinctValuesFunction(
            this.script.composeForExpression(
                this.script.composeIteratorList(
                    List.of(
                        this.script.composeIteratorExpression(loopVariable.declarationExpression, valueReference.asSequence()))),
                this.script.composeStringConcatenation(
                    List.of(this.script.getStringLiteralFromUnquotedString(ASSET_TYPE_INDICATOR),
                        this.script.getStringLiteralFromUnquotedString("|"),
                        this.script.getStringLiteralFromUnquotedString(LABEL_TYPE_WHEN),
                        this.script.getStringLiteralFromUnquotedString("-"),
                        new StringExpression(loopVariable.referenceExpression.getScript()),
                        this.script.getStringLiteralFromUnquotedString("|"),
                        this.script.getStringLiteralFromUnquotedString(fieldId))),
                StringSequenceExpression.class),
            StringSequenceExpression.class);
        this.stack.push(new LabelFromExpressionContentTemplateFragment(keys, quantity));
        break;
      }
      case "code":
      case "internal-code": {
        StringSequenceExpression keys = this.script.composeDistinctValuesFunction(
            this.script.composeForExpression(
                this.script.composeIteratorList(
                    List.of(
                        this.script.composeIteratorExpression(loopVariable.declarationExpression, valueReference.asSequence()))),
                this.script.composeStringConcatenation(List.of(
                    this.script.getStringLiteralFromUnquotedString(ASSET_TYPE_CODE),
                    this.script.getStringLiteralFromUnquotedString("|"),
                    this.script.getStringLiteralFromUnquotedString(LABEL_TYPE_NAME),
                    this.script.getStringLiteralFromUnquotedString("|"),
                    this.script.getStringLiteralFromUnquotedString(
                        this.symbols.getRootCodelistOfField(fieldId)),
                    this.script.getStringLiteralFromUnquotedString("."),
                    new StringExpression(loopVariable.referenceExpression.getScript()))),
                StringSequenceExpression.class),
            StringSequenceExpression.class);
        this.stack.push(new LabelFromExpressionContentTemplateFragment(keys, quantity));
        break;
      }
      default:
        throw InvalidUsageException.shorthandRequiresCodeOrIndicator(this.stack.peekParserContext(), fieldId, fieldType);
    }
  }

  /**
   * Returns the number that the given field is the unit of: the value of the amount, measure or
   * duration that holds it as its attribute, on the same element. It is empty if the given field is not
   * such a unit, or if that number repeats, because a label is pluralised by a single quantity.
   */
  private NumericExpression composeQuantityOfUnit(final String unitFieldId) {
    final String fieldId = this.symbols.getFieldIdOfAttributeField(unitFieldId);
    if (fieldId == null) {
      return NumericExpression.empty();
    }
    switch (FieldTypes.fromString(this.symbols.getTypeOfField(fieldId))) {
      case DURATION:
      case MEASURE:
      case AMOUNT:
        break;
      default:
        return NumericExpression.empty();
    }
    final PathExpression field = this.symbols.getRelativePathOfField(fieldId, this.efxContext.symbol());
    if (field instanceof SequenceExpression) {
      return NumericExpression.empty();
    }
    return TypedExpression.from(
        this.script.composeFieldValueReference(new NumericPath(field.getScript())), NumericExpression.class);
  }

  /**
   * Handles the #{labelType} shorthand syntax which renders the label of the Field or Node declared
   * as the context.
   * 
   * If the labelType is 'value', then the label depends on the field's value and is rendered
   * according to the field's type. The assetType is inferred from the Field or Node declared as
   * context.
   */
  @Override
  public void exitShorthandLabelReferenceFromContext(ShorthandLabelReferenceFromContextContext ctx) {

    // New in EFX-2: Pluralisation of labels based on a supplied quantity
    NumericExpression quantity = ctx.pluraliser() != null
        ? this.labelPart(ctx, NumericExpression.class, PLURALISER)
        : NumericExpression.empty();

    final String labelType = ctx.LabelType().getText();
    if (this.efxContext.isFieldContext()) {
      if (labelType.equals(SHORTHAND_CONTEXT_FIELD_LABEL_REFERENCE)) {
        this.shorthandIndirectLabelReference(this.efxContext.symbol(), quantity);
      } else {
        StringExpression key = this.script.composeStringConcatenation(
            List.of(this.script.getStringLiteralFromUnquotedString(ASSET_TYPE_FIELD),
                this.script.getStringLiteralFromUnquotedString("|"),
                this.script.getStringLiteralFromUnquotedString(labelType),
                this.script.getStringLiteralFromUnquotedString("|"),
                this.script.getStringLiteralFromUnquotedString(this.efxContext.symbol())));
        this.stack.push(new LabelFromKeyContentTemplateFragment(key, quantity));
      }
    } else if (this.efxContext.isNodeContext()) {
      StringExpression key = this.script.composeStringConcatenation(
          List.of(this.script.getStringLiteralFromUnquotedString(ASSET_TYPE_NODE),
              this.script.getStringLiteralFromUnquotedString("|"),
              this.script.getStringLiteralFromUnquotedString(labelType),
              this.script.getStringLiteralFromUnquotedString("|"),
              this.script.getStringLiteralFromUnquotedString(this.efxContext.symbol())));
      this.stack.push(new LabelFromKeyContentTemplateFragment(key, quantity));
    }
  }

  /**
   * Handles the #value shorthand syntax which renders the label corresponding to the value of the
   * the field declared as the context of the current line of the template. This shorthand syntax is
   * only supported for fields of type 'code' or 'indicator'.
   */
  @Override
  public void exitShorthandIndirectLabelReferenceFromContextField(
      ShorthandIndirectLabelReferenceFromContextFieldContext ctx) {
    if (!this.efxContext.isFieldContext()) {
      throw InvalidUsageException.shorthandRequiresFieldContext(ctx, "#value");
    }
    this.shorthandIndirectLabelReference(this.efxContext.symbol(), NumericExpression.empty());
  }

  /**
   * Takes a part of a label block off the stack, where the part, or its expression block, left
   * it, and converts it to the class the part requires. The error of a part that does not convert
   * names that part alone, since each part is taken, and checked, on its own.
   *
   * @param <T> the class the part requires
   * @param labelBlock the label block, where an error is reported
   * @param type the class the part requires
   * @param part the part, as a type error names it, such as {@link #ASSET_ID}
   * @return the part, converted to that class
   * @throws eu.europa.ted.efx.exceptions.TypeMismatchException if the part does not convert to
   *         that class
   */
  private <T extends TypedExpression> T labelPart(final ParserRuleContext labelBlock,
      final Class<T> type, final Construct part) {
    return this.expressions.operandsOf(part, List.of(this.stack.pop(TypedExpression.class)))
        .converted(0, type, labelBlock);
  }

  @Override
  public void exitAssetType(AssetTypeContext ctx) {
    if (ctx.expressionBlock() == null) {
      this.stack.push(this.script.getStringLiteralFromUnquotedString(ctx.getText()));
    }
  }

  @Override
  public void exitLabelType(LabelTypeContext ctx) {
    if (ctx.expressionBlock() == null) {
      this.stack.push(this.script.getStringLiteralFromUnquotedString(ctx.getText()));
    }
  }

  @Override
  public void exitAssetId(AssetIdContext ctx) {
    if (ctx.expressionBlock() == null) {
      this.stack.push(this.script.getStringLiteralFromUnquotedString(ctx.getText()));
    }
  }
 
  // #region New in EFX-2 -----------------------------------------------------

  @Override
  public void exitComputedLabelReference(ComputedLabelReferenceContext ctx) {
    NumericExpression quantity = ctx.pluraliser() != null
        ? this.labelPart(ctx, NumericExpression.class, PLURALISER)
        : NumericExpression.empty();
    StringExpression expression = this.labelPart(ctx, StringExpression.class, LABEL_KEY);
    this.stack.push(new LabelFromExpressionContentTemplateFragment(expression, quantity));
  }


  @Override
  public void exitDictionaryDeclaration(DictionaryDeclarationContext ctx) {
    String name = ctx.dictionaryName.getText();
    StringExpression key = this.stack.pop(StringExpression.class);
    var match = this.stack.pop(PathExpression.class);

    // Declare the dictionary in the script
    var dictionary = new Dictionary(name, match, key);
    this.stack.declareGlobalIdentifier(dictionary);
    this.viewTemplate.addDictionary(dictionary);
  }

  /**
   * Leaves the path of the indexed field for the declaration, and switches to the context of the
   * field, which the key is read in.
   */
  @Override
  public void exitDictionaryIndexClause(DictionaryIndexClauseContext ctx) {
    final Context index = this.expressions.contextOf(ctx.fieldContext());
    this.stack.push(index.relativePath());
    this.efxContext.pushFieldContext(index.symbol());
  }

  /**
   * Takes the value of the key as the text it is, and then switches back from the context of the
   * indexed field. The value is taken first, so that a field of the key that may repeat is
   * reported with the context the key reads it from.
   */
  @Override
  public void exitDictionaryKeyClause(DictionaryKeyClauseContext ctx) {
    this.stack.push(this.expressions.valueAs(ctx.expression(), StringExpression.class, BY));
    this.efxContext.pop();
  }

  // #endregion New in EFX-2 --------------------------------------------------

  // #endregion Label Blocks #{...} -------------------------------------------
  
  // #region Expression Blocks ${...} -----------------------------------------

  /***
   * Handles the $value shorthand syntax which renders the value of the field declared as context in
   * the current line of the template.
   */
  @Override
  public void exitShorthandFieldValueReferenceFromContextField(
      ShorthandFieldValueReferenceFromContextFieldContext ctx) {
    if (!this.efxContext.isFieldContext()) {
      throw InvalidUsageException.shorthandRequiresFieldContext(ctx, "$value");
    }
    this.stack.push(this.script.composeFieldValueReference(
        this.symbols.getRelativePathOfField(this.efxContext.symbol(), this.efxContext.symbol())));
  }

  // #endregion Expression Blocks ${...} --------------------------------------
  
  // #region Context Declaration Blocks {...} ---------------------------------

  // #region New in EFX-2 -----------------------------------------------------

  @Override
  public void exitContextDeclaration(ContextDeclarationContext ctx) {
    String shortcut = ctx.shortcut != null ? ctx.shortcut.getText() : "none";
    switch (shortcut) {
      case ".":
        this.exitSameContextDeclaration();
        break;
      case "..":
        this.exitParentContextDeclaration();
        break;
      case "/":
        this.declareRootLineContext();
        break;
      default:
        final Context declared = this.declaredContextOf(ctx);
        Variable contextVariable = null;
        if (ctx.contextVariableInitializer() != null) {
          contextVariable =
              this.getContextVariable(ctx.contextVariableInitializer(), declared.relativePath());
          this.stack.peek(Variables.class).add(contextVariable);
        }
        this.declareContext(declared, contextVariable);
        break;
    }
  }

  private void exitSameContextDeclaration() {
    final Context currentContext = this.blockStack.currentContext();
    // A line nested in the line before it stands in that line's element. A line after it, at the
    // same level, stands in the same line above, so it takes that line's context as it is.
    if (this.blockAbove != null && this.blockAbove == this.blockStack.peek()) {
      this.declareLineContext(currentContext);
    } else {
      this.declareContext(currentContext, null);
    }
  }

  private void exitParentContextDeclaration() {
    this.declareLineContext(this.blockStack.parentContext());
  }

  /**
   * Declares the root as the context of a template line that writes it, {@code /}, or that
   * declares no context.
   */
  private void declareRootLineContext() {
    this.declareLineContext(
        new NodeContext(this.symbols.getRootNodeId(), this.symbols.getRootPath()));
  }

  /**
   * Declares the context of a template line that names a context by its absolute path: the root,
   * or the context of another line. Its path from the context above is the path between the
   * two.
   *
   * @param named the context the line names
   */
  private void declareLineContext(final Context named) {
    this.declareContext(this.relativizeContext(named, this.contextAbove()), null);
  }

  // #region Conditional template blocks --------------------------------------


  @Override
  public void exitWhenDisplayTemplate(WhenDisplayTemplateContext ctx) {
    var template = this.stack.pop(DisplayContentTemplate.class);
    template.setCondition(
        this.expressions.valueAs(ctx.expression(), BooleanExpression.class, WHEN));
    this.stack.push(template);
  }

  @Override
  public void exitWhenInvokeTemplate(WhenInvokeTemplateContext ctx) {
    var template = this.stack.pop(InvokeContentTemplate.class);
    template.setCondition(
        this.expressions.valueAs(ctx.expression(), BooleanExpression.class, WHEN));
    this.stack.push(template);
  }

  /**
   * Invokes a declared template, with its arguments matched against the parameters it declares.
   */
  @Override
  public void exitInvokeTemplate(InvokeTemplateContext ctx) {
    final Template template = this.stack.getTemplate(ctx.templateName.getText());
    this.stack.push(new InvokeContentTemplate(ctx.templateName.getText(),
        this.expressions.argumentsOf(ctx, template, ctx.argumentList(),
            Construct.template(ctx.templateName.getText(), template.parameters.size()))));
  }
  
  // #endregion Conditional template blocks ---------------------------------

  // #region Variable Initializers --------------------------------------------

  /**
   * A variable is initialized with a value of its declared type.
   */
  @Override
  public void exitTypedVariableInitializer(TypedVariableInitializerContext ctx) {
    var variableName = ctx.variableName.getText();
    var expression = this.expressions.initialValueOf(ctx);
    var variable = new Variable(variableName,
        this.script.composeVariableDeclaration(variableName, expression.getClass()),
        expression,
        this.script.composeVariableReference(variableName, expression.getClass()));
    this.exitVariableInitializer(ctx, variable);
  }

  /**
   * A variable of a context declaration block is declared at once, so that the initializers after
   * it can read it. The variable of a global declaration is declared when the declaration exits.
   */
  private void exitVariableInitializer(VariableInitializerContext initializer, Variable variable) {
    if (initializer.getParent() instanceof VariableListContext) {
      this.stack.declareIdentifier(variable);
      this.stack.peek(Variables.class).add(variable);
    } else {
      this.stack.push(variable);
    }
  }

  // #endregion Variable Initializers -----------------------------------------

  // #region Hyperlinks -------------------------------------------------------

  @Override
  public void exitLinkedTextBlock(LinkedTextBlockContext ctx) {
    var url =
        this.expressions.valueAs(ctx.linkBlock().expression(), StringExpression.class, HYPERLINK);
    this.stack.push(new HyperlinkContentTemplateFragment(new TextContentTemplateFragment(ctx.textBlock().getText()), url));
  }

  @Override
  public void exitLinkedLabelBlock(LinkedLabelBlockContext ctx) {
    var url =
        this.expressions.valueAs(ctx.linkBlock().expression(), StringExpression.class, HYPERLINK);
    var label = this.stack.pop(ContentTemplateFragment.class);
    this.stack.push(new HyperlinkContentTemplateFragment(label, url));
  }

  // #endregion Hyperlinks ----------------------------------------------------

  // #endregion New in EFX-2 --------------------------------------------------

  @Override
  public void enterContextDeclarationBlock(ContextDeclarationBlockContext arg0) {
    this.stack.push(new Variables());
  }




  // #endregion Context Declaration Blocks {...} ------------------------------
  
  @Override
  public void enterSummarySection(SummarySectionContext ctx) {
    while (!this.blockStack.isEmpty()) {
      this.blockStack.pop();
    }
    this.translatorContext.setCurrentSection(TemplateSection.SUMMARY);
  }

  @Override
    public void exitSummarySection(SummarySectionContext ctx) {

      this.translatorContext.setCurrentSection(TemplateSection.DEFAULT);
    }

  @Override
  public void enterNavigationSection(NavigationSectionContext ctx) {
    while (!this.blockStack.isEmpty()) {
      this.blockStack.pop();
    }
    this.translatorContext.setCurrentSection(TemplateSection.NAVIGATION);
  }

  @Override
  public void exitNavigationSection(NavigationSectionContext ctx) {
      this.translatorContext.setCurrentSection(TemplateSection.DEFAULT);
  }

  // #region Template lines  --------------------------------------------------

  @Override
  public void enterTemplateLine(TemplateLineContext ctx) {
    final int indentLevel = this.getIndentLevel(ctx.indentation());
    this.enterTemplateLine(ctx, indentLevel);
    this.blockAbove = this.blockStack.blockAtLevel(indentLevel - 1);
    if (ctx.contextDeclarationBlock() == null) {
      this.declareRootLineContext();
      this.stack.push(new Variables());
    }
  }

  @Override
  public void enterTemplateDeclaration(TemplateDeclarationContext ctx) {
    this.enterTemplateLine(ctx, 0);
  }

  private void enterTemplateLine(ParserRuleContext ctx, final int indentLevel) {
    final int indentChange = indentLevel - this.blockStack.currentIndentationLevel();
    if (indentChange > 1) {
      throw InvalidIndentationException.indentationLevelSkipped(ctx);
    } else if (indentChange == 1) {
      if (this.blockStack.isEmpty()) {
          throw InvalidIndentationException.startIndentAtZero(ctx);
      }
      this.stack.pushStackFrame(); // Create a stack frame for the new template line.
    } else if (indentChange < 0) {
      for (int i = indentChange; i < 0; i++) {
        assert !this.blockStack.isEmpty() : UNEXPECTED_INDENTATION;
        assert this.blockStack.currentIndentationLevel() > indentLevel : UNEXPECTED_INDENTATION;
        this.blockStack.pop();
        this.stack.popStackFrame(); // Each skipped indentation level must go out of scope.
      }
      this.stack.popStackFrame(); // The previous sibling goes out of scope (same indentation
                                  // level).
      this.stack.pushStackFrame(); // Create a stack frame for the new template line.
      assert this.blockStack.currentIndentationLevel() == indentLevel : UNEXPECTED_INDENTATION;
    } else if (indentChange == 0) {
      this.stack.popStackFrame(); // The previous sibling goes out of scope (same indentation
                                  // level).
      this.stack.pushStackFrame(); // Create a stack frame for the new template line.
    }
  }

  /**
   * @return the context of the line above the template line being translated, which the line is
   *         nested in, or {@code null} when there is none, as for a line at the top
   */
  private Context contextAbove() {
    return this.blockAbove != null ? this.blockAbove.getContext() : null;
  }

  @Override
  public void exitTemplateLine(TemplateLineContext ctx) {
    final Context lineContext = this.efxContext.pop();
    final int indentLevel = this.getIndentLevel(ctx.indentation());
    final int indentChange = indentLevel - this.blockStack.currentIndentationLevel();
    // What the line displays is on the stack in order: a content template for each WHEN alternative,
    // then the one displayed otherwise.
    final LinkedList<ContentTemplate> contentTemplates = new LinkedList<>();
    while (this.stack.peek() instanceof ContentTemplate) {
      contentTemplates.addFirst(this.stack.pop(ContentTemplate.class));
    }
    final Variables variables = this.stack.pop(Variables.class);
    final Integer outlineNumber =
        ctx.OutlineNumber() != null ? Integer.parseInt(ctx.OutlineNumber().getText().trim()) : -1;
    assert this.stack.empty() : "Stack should be empty at this point.";

    if (indentChange > 1) {
      throw InvalidIndentationException.indentationLevelSkipped(ctx);
    } else if (indentChange == 1) {
      if (this.blockStack.isEmpty()) {
          throw InvalidIndentationException.startIndentAtZero(ctx);
      }
      if (this.blockStack.peek() instanceof TemplateInvocation) {
        throw InvalidIndentationException.noNestingOnInvocations(ctx);
      }
      this.blockStack.pushChild(outlineNumber, lineContext, variables, contentTemplates);
    } else if (indentLevel == 0) {
      // A top-level line is a new block of the current section, also when it follows a declared
      // template and its lines.
      this.blockStack.clear();
      final ContentBlock sectionRoot = this.viewTemplate.getSection(this.translatorContext.getCurrentSection()).getRoot();
      this.blockStack.push(sectionRoot.addChild(outlineNumber, lineContext, variables,
          contentTemplates));
    } else {
      this.blockStack.pushSibling(outlineNumber, lineContext, variables, contentTemplates);
    }
  }

  @Override
  public void exitTemplateDeclaration(TemplateDeclarationContext ctx) {
    // What the line displays is on the stack in order: a content template for each WHEN alternative,
    // then the one displayed otherwise.
    final LinkedList<ContentTemplate> contentTemplates = new LinkedList<>();
    while (this.stack.peek() instanceof ContentTemplate) {
      contentTemplates.addFirst(this.stack.pop(ContentTemplate.class));
    }
    final Template template = this.stack.pop(Template.class);
    assert this.stack.empty() : "Stack should be empty at this point.";

    if (this.getIndentLevel(ctx.indentation()) != 0) {
      throw InvalidIndentationException.noIndentOnTemplateDeclarations(ctx);
    }

    this.blockStack.clear();
    this.blockStack.push(this.viewTemplate.getTemplateDeclarations().addChild(template.name, contentTemplates, template.parameters));
  }

  /**
   * Translates the context a template line declares by a field or a node, or by a context
   * variable assigned one, where the line stands: in the context of the line above, so that an
   * index after the reference, and every position, condition and reference in its brackets, is
   * read within the element of that line, as in an expression.
   *
   * @param declaration the context declaration of the line
   * @return the context it declares: the absolute path of its field or node, and its path from
   *         the context above
   */
  private Context declaredContextOf(final ContextDeclarationContext declaration) {
    final Context contextAbove = this.contextAbove();
    if (contextAbove == null) {
      return this.translatedContextOf(declaration);
    }
    this.efxContext.push(contextAbove);
    final Context declared = this.translatedContextOf(declaration);
    this.efxContext.pop();
    return declared;
  }

  /**
   * @param declaration the context declaration of a template line, by a field or a node, or by a
   *        context variable assigned one
   * @return the context it declares, translated in the current context
   */
  private Context translatedContextOf(final ContextDeclarationContext declaration) {
    return declaration.contextVariableInitializer() != null
        ? this.expressions.contextAssignedBy(declaration.contextVariableInitializer())
        : this.expressions.contextOf(declaration.getRuleContext(ParserRuleContext.class, 0));
  }

  private Context relativizeContext(Context childContext, Context parentContext) {
    if (parentContext == null) {
      return childContext;
    }

    PathExpression parentContextAbsolutePath = parentContext.isFieldContext()
        ? this.symbols.getAbsolutePathOfField(parentContext.symbol())
        : this.symbols.getAbsolutePathOfNode(parentContext.symbol());

    if (childContext.isFieldContext()) {
      return new FieldContext(childContext.symbol(), childContext.absolutePath(),
          this.script.contextualizePath(childContext.absolutePath(), parentContextAbsolutePath), childContext.variable());
    }

    assert childContext.isNodeContext() : "Child context should be either a FieldContext or a NodeContext.";

    return new NodeContext(childContext.symbol(), childContext.absolutePath(),
        this.script.contextualizePath(childContext.absolutePath(), parentContextAbsolutePath));
  }

  // #endregion Template lines  -----------------------------------------------
  
  // #region Helpers ----------------------------------------------------------

  private static String getLexerSymbol(int tokenType) {
    return EfxLexer.VOCABULARY.getLiteralName(tokenType).replaceAll("^'|'$", "");
  }

    private int getIndentLevel(IndentationContext ctx) {

      if (ctx == null) {
        return 0; // No indentation, default to 0.
      }

      if (ctx.MixedIndent() != null) {
          throw InvalidIndentationException.mixedIndentation(ctx);
      }

      if (ctx.Spaces() != null) {
        if (this.indentWith == Indent.UNDETERMINED) {
          this.indentWith = Indent.SPACES;
          this.indentSpaces = ctx.Spaces().getText().length();
        } else if (this.indentWith == Indent.TABS) {
          throw InvalidIndentationException.mixedIndentation(ctx);
        }

        if (ctx.Spaces().getText().length() % this.indentSpaces != 0) {
          throw InvalidIndentationException.inconsistentSpaces(ctx, this.indentSpaces);
        }
        return ctx.Spaces().getText().length() / this.indentSpaces;
      } else if (ctx.Tabs() != null) {
        if (this.indentWith == Indent.UNDETERMINED) {
          this.indentWith = Indent.TABS;
        } else if (this.indentWith == Indent.SPACES) {
          throw InvalidIndentationException.mixedIndentation(ctx);
        }

        return ctx.Tabs().getText().length();
      }
      return 0;
    }

  // #endregion Helpers -------------------------------------------------------


  /**
   * Translates the expressions of a template, which may call the built-ins that only templates may
   * call, such as the formatting functions.
   */
  private static final class TemplateExpressionVisitor extends EfxExpressionVisitor {

    TemplateExpressionVisitor(final TranslationContext translation) {
      super(translation);
    }

    @Override
    protected boolean translatesTemplateOnlyBuiltIns() {
      return true;
    }
  }
}
