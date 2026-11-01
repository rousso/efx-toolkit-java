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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import eu.europa.ted.efx.exceptions.Construct;
import eu.europa.ted.efx.exceptions.Diagnosis;
import eu.europa.ted.efx.exceptions.InvalidArgumentException;
import eu.europa.ted.efx.exceptions.InvalidIdentifierException;
import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.exceptions.SdkInconsistencyException;
import eu.europa.ted.efx.exceptions.TranslatorConfigurationException;
import eu.europa.ted.efx.exceptions.TypeMismatchException;
import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.interfaces.SymbolResolver;
import eu.europa.ted.efx.model.CallStack;
import eu.europa.ted.efx.model.Context;
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
import eu.europa.ted.efx.model.expressions.sequence.SequencePath;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.TimeSequenceExpression;
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
import eu.europa.ted.efx.model.types.EfxTypeTokenLookup;
import eu.europa.ted.efx.model.types.FieldTypes;
import eu.europa.ted.efx.model.variables.Dictionary;
import eu.europa.ted.efx.model.variables.Parametrised;
import eu.europa.ted.efx.model.variables.ParsedArguments;
import eu.europa.ted.efx.model.variables.StrictArguments;
import eu.europa.ted.efx.model.variables.Variable;
import eu.europa.ted.efx.sdk2.EfxParser.*;
import eu.europa.ted.efx.sdk2.Origin.ContextVariableOrigin;
import eu.europa.ted.efx.sdk2.Origin.DictionaryNameOrigin;
import eu.europa.ted.efx.sdk2.Origin.DictionaryOrigin;
import eu.europa.ted.efx.sdk2.Origin.FieldOrigin;
import eu.europa.ted.efx.sdk2.Origin.IdentifierOrigin;
import eu.europa.ted.efx.sdk2.Origin.NodeOrigin;
import eu.europa.ted.efx.sdk2.Origin.PathOrigin;
import eu.europa.ted.efx.sdk2.Origin.VariableOrigin;
import eu.europa.ted.efx.util.EfxRegexValidator;

/**
 * Translates the expressions of EFX-2 to the target language of a {@link ScriptGenerator}.
 *
 * <p>The grammar does not type expressions: each visit translates its subtree and returns an
 * {@link Operand}, the translated expression with what it is written as. The parent reads each
 * operand as a value, as a path or as a context, and the catalogue of built-ins decides what each
 * operator and built-in accepts and gives. The composites (lists, conditionals, iterations,
 * predicates and indexes) check their own parts.</p>
 *
 * <p>The template and rules translators hold a visitor for the expressions their files contain.
 * Their behaviour in expressions is given by subclasses: the template's allows the built-ins that
 * only templates may call, and the rules' translates calls of API functions and records the
 * dynamic dependencies of variables.</p>
 */
class EfxExpressionVisitor extends EfxBaseVisitor<Operand<?>> {

  /**
   * The context an absolute reference is resolved from: none, so that its path starts at the root.
   */
  private static final String NO_CONTEXT = null;

  /** Parentheses, as a type error names them. */
  private static final Construct PARENTHESES = Construct.of("Parenthesised expression", "content");

  /** A conditional, as a type error names it. */
  private static final Construct CONDITIONAL =
      Construct.of("Conditional 'if'", "condition", "then branch", "else branch");

  /** An iteration, as a type error names it. */
  private static final Construct ITERATION = Construct.of("Iteration 'for'", "body");

  /** A predicate after a field or a node, as a type error names it. */
  private static final Construct PREDICATE = Construct.of("Predicate", "sequence", "condition");

  /** An index after a sequence, as a type error names it. */
  private static final Construct INDEX = Construct.of("Index", "sequence", "position");

  /**
   * What the error of a predicate whose condition is a number suggests: the index it may mean.
   */
  private static final List<String> SELECT_BY_POSITION =
      List.of("To select by position, write [at n].");

  /**
   * The variable that an index binds its position to, where the position is read once before the
   * element is selected. Like every name the translator generates, it starts with a prefix that no
   * declaration may give.
   */
  private static final String INDEX_VARIABLE = CallStackV2.RESERVED_PREFIX + "index";

  /** A context override, as a type error names it. */
  private static final Construct CONTEXT_OVERRIDE = Construct.operator("::", 2);

  private final SymbolResolver symbols;
  private final ScriptGenerator script;
  private final CallStack stack;
  private final ContextStack efxContext;
  private final Origin<TypedExpression> none;
  private final IteratorDeclarations iteratorDeclarations = new IteratorDeclarations();

  /**
   * The context of each context variable, by its declaration. The call stack finds the declaration
   * that a name refers to where it is read, and that declaration is a context variable if it has a
   * context here.
   */
  private final Map<Variable, Context> contextsOfVariables = new IdentityHashMap<>();

  /**
   * The reads made in each context while it is the current one, so that an index can tell whether
   * its position reads what the target language would read differently for each element. A
   * reference is resolved only through {@link #relativePathOfField}, {@link #relativePathOfNode},
   * {@link #absolutePathOfCurrentContext}, {@link #absolutePathOfField} and
   * {@link #absolutePathOfNode}, and a dictionary is only looked up by {@link #lookUp}, which
   * count the read in the current context.
   */
  private final Map<Context, Reads> readsInContexts = new IdentityHashMap<>();

  /**
   * The reads made where no context is declared.
   */
  private final Reads readsWithoutContext = new Reads();

  EfxExpressionVisitor(final TranslationContext translation) {
    this.symbols = translation.symbols();
    this.script = translation.script();
    this.stack = translation.stack();
    this.efxContext = translation.efxContext();
    this.none = new Origin.None(this);
  }

  SymbolResolver symbols() {
    return this.symbols;
  }

  ScriptGenerator script() {
    return this.script;
  }

  ContextStack efxContext() {
    return this.efxContext;
  }

  /**
   * Keeps the node being translated on the call stack, which names it in the errors it gives.
   */
  @Override
  public Operand<?> visit(final ParseTree tree) {
    this.stack.pushParserContext((ParserRuleContext) tree);
    try {
      return super.visit(tree);
    } finally {
      this.stack.popParserContext();
    }
  }

  // #region Readings of an expression ----------------------------------------

  /**
   * Translates an expression where a value of any type is taken, and nothing after checks its
   * type: what a translation gives, such as the expression of a template block, and the body of an
   * iteration. A node has no value, so it is rejected, as the operators and the built-ins that
   * take a value reject it.
   *
   * @param expression an expression
   * @param construct what takes the expression as its one operand, as a type error names it
   * @return its value
   * @throws TypeMismatchException if the expression is a node
   */
  TypedExpression valueOf(final ExpressionContext expression, final Construct construct) {
    return this.onlyOperandOf(construct, expression).valueAt(0, expression);
  }

  /**
   * Translates an expression where a value of one class is required: the condition of a clause,
   * the value of a declaration, a part of a composite. A single value where a sequence is required
   * is promoted to one.
   *
   * @param <T> the class of the value required
   * @param expression the expression
   * @param type the class of the value required
   * @param construct what takes the expression as its one operand, as a type error names it
   * @return the value, converted to that class
   * @throws TypeMismatchException if the value does not fit: a sequence where a single value is
   *         required, or a node, is reported by the reference the expression is written as where
   *         it explains the mistake, and any other value cannot be converted
   */
  <T extends TypedExpression> T valueAs(final ExpressionContext expression, final Class<T> type,
      final Construct construct) {
    return this.onlyOperandOf(construct, expression).converted(0, type, expression);
  }

  /**
   * Translates the value a variable is initialized with, which must be of its declared type.
   *
   * @param initializer the initializer of the variable
   * @return the value, converted to the declared type
   * @throws TypeMismatchException if the value does not fit the declared type
   */
  TypedExpression initialValueOf(final TypedVariableInitializerContext initializer) {
    return this.valueAs(initializer.expression(), typeOf(initializer.typeDeclaration()),
        Construct.of("Variable '" + initializer.typeDeclaration().getText() + ":$"
            + initializer.variableName.getText() + "'", "value"));
  }

  /**
   * Gives the operands of a construct that are values given to it as they are, written as nothing
   * the construct could name in its error, such as the parts of a label block.
   *
   * @param construct the construct, as a type error names it
   * @param values the values of its operands, in order
   * @return the operands, which convert each value to what its position takes
   */
  Operands operandsOf(final Construct construct, final List<? extends TypedExpression> values) {
    return new Operands(construct,
        values.stream().map(this::operand).collect(Collectors.toList()));
  }

  private Operands onlyOperandOf(final Construct construct, final ExpressionContext expression) {
    final Operand<?> operand = this.visit(expression);
    return new Operands(construct, List.of(operand));
  }

  /**
   * @param fieldOrNodeContext a field or a node, with its predicates, where a context is declared
   * @return the context it declares
   */
  Context contextOf(final ParserRuleContext fieldOrNodeContext) {
    return this.visit(fieldOrNodeContext).context().orElseThrow();
  }

  /**
   * @param initializer the initializer of a context variable
   * @return the context it gives the variable: a field or a node, with its predicates
   * @throws TranslatorConfigurationException if the variable is initialized with the root, which
   *         no translator handles yet
   */
  Context contextAssignedBy(final ContextVariableInitializerContext initializer) {
    if (initializer.Slash() != null) {
      throw TranslatorConfigurationException.unhandledVariableContext(initializer.getText());
    }
    return this.contextOf(initializer.getRuleContext(ParserRuleContext.class, 0));
  }

  /**
   * @param expression a translated expression
   * @return the operand of an expression written as anything but a reference
   */
  Operand<TypedExpression> operand(final TypedExpression expression) {
    return new Operand<>(expression, this.none);
  }

  private Operand<?> visitOnlyChild(final ParserRuleContext wrapper) {
    return this.visit(wrapper.getRuleContext(ParserRuleContext.class, 0));
  }

  // #endregion Readings of an expression -------------------------------------

  // #region Literals ---------------------------------------------------------

  @Override
  public Operand<?> visitLiteralExpression(final LiteralExpressionContext ctx) {
    return this.visit(ctx.literal());
  }

  @Override
  public Operand<?> visitLiteral(final LiteralContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  @Override
  public Operand<?> visitStringLiteral(final StringLiteralContext ctx) {
    return this.operand(this.script.getStringLiteralEquivalent(ctx.getText()));
  }

  @Override
  public Operand<?> visitNumericLiteral(final NumericLiteralContext ctx) {
    return this.operand(this.script.getNumericLiteralEquivalent(ctx.getText()));
  }

  @Override
  public Operand<?> visitBooleanLiteral(final BooleanLiteralContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  @Override
  public Operand<?> visitTrueBooleanLiteral(final TrueBooleanLiteralContext ctx) {
    return this.operand(this.script.getBooleanEquivalent(true));
  }

  @Override
  public Operand<?> visitFalseBooleanLiteral(final FalseBooleanLiteralContext ctx) {
    return this.operand(this.script.getBooleanEquivalent(false));
  }

  @Override
  public Operand<?> visitDateLiteral(final DateLiteralContext ctx) {
    return this.operand(this.script.getDateLiteralEquivalent(ctx.DateLiteral().getText()));
  }

  @Override
  public Operand<?> visitTimeLiteral(final TimeLiteralContext ctx) {
    return this.operand(this.script.getTimeLiteralEquivalent(ctx.TimeLiteral().getText()));
  }

  @Override
  public Operand<?> visitDurationLiteral(final DurationLiteralContext ctx) {
    return this.operand(this.script.getDurationLiteralEquivalent(ctx.getText()));
  }

  // Parameter values: the arguments of a single expression

  @Override
  public Operand<?> visitParameterValue(final ParameterValueContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  @Override
  public Operand<?> visitScalarParameterValue(final ScalarParameterValueContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  @Override
  public Operand<?> visitSequenceParameterValue(final SequenceParameterValueContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  @Override
  public Operand<?> visitStringSequenceLiteral(final StringSequenceLiteralContext ctx) {
    return this.literalList(ctx.stringLiteral(), StringExpression.class,
        StringSequenceExpression.class);
  }

  @Override
  public Operand<?> visitBooleanSequenceLiteral(final BooleanSequenceLiteralContext ctx) {
    return this.literalList(ctx.booleanLiteral(), BooleanExpression.class,
        BooleanSequenceExpression.class);
  }

  @Override
  public Operand<?> visitNumericSequenceLiteral(final NumericSequenceLiteralContext ctx) {
    return this.literalList(ctx.numericLiteral(), NumericExpression.class,
        NumericSequenceExpression.class);
  }

  @Override
  public Operand<?> visitDateSequenceLiteral(final DateSequenceLiteralContext ctx) {
    return this.literalList(ctx.dateLiteral(), DateExpression.class,
        DateSequenceExpression.class);
  }

  @Override
  public Operand<?> visitTimeSequenceLiteral(final TimeSequenceLiteralContext ctx) {
    return this.literalList(ctx.timeLiteral(), TimeExpression.class,
        TimeSequenceExpression.class);
  }

  @Override
  public Operand<?> visitDurationSequenceLiteral(final DurationSequenceLiteralContext ctx) {
    return this.literalList(ctx.durationLiteral(), DurationExpression.class,
        DurationSequenceExpression.class);
  }

  private <T extends ScalarExpression> Operand<?> literalList(
      final List<? extends ParserRuleContext> literals, final Class<T> itemType,
      final Class<? extends SequenceExpression> listType) {
    final List<T> items = new ArrayList<>(literals.size());
    for (final ParserRuleContext literal : literals) {
      items.add(itemType.cast(this.visit(literal).value()));
    }
    return this.operand(this.script.composeList(items, listType));
  }

  // #endregion Literals ------------------------------------------------------

  // #region References -------------------------------------------------------

  @Override
  public Operand<?> visitReferenceExpression(final ReferenceExpressionContext ctx) {
    return this.visit(ctx.reference());
  }

  @Override
  public Operand<?> visitReference(final ReferenceContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  @Override
  public Operand<?> visitFieldReference(final FieldReferenceContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  /**
   * A field, by its path from the current context.
   */
  @Override
  public Operand<?> visitLinkedFieldReference(final LinkedFieldReferenceContext ctx) {
    final String fieldId = this.fieldIdOf(ctx);
    return new Operand<>(this.relativePathOfField(fieldId),
        new FieldOrigin(this, ctx, fieldId, this.efxContext.symbol()));
  }

  /**
   * A field, by its path from the root, which its error names as the context it is read from.
   */
  @Override
  public Operand<?> visitAbsoluteFieldReference(final AbsoluteFieldReferenceContext ctx) {
    final String fieldId = this.fieldIdOf(ctx.linkedFieldReference());
    return new Operand<>(this.absolutePathOfField(fieldId),
        new FieldOrigin(this, ctx, fieldId, NO_CONTEXT));
  }

  @Override
  public Operand<?> visitNodeReference(final NodeReferenceContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  @Override
  public Operand<?> visitSimpleNodeReference(final SimpleNodeReferenceContext ctx) {
    final String nodeId = ctx.NodeId().getText();
    return new Operand<>(this.relativePathOfNode(nodeId), new NodeOrigin(this, nodeId));
  }

  @Override
  public Operand<?> visitAbsoluteNodeReference(final AbsoluteNodeReferenceContext ctx) {
    final String nodeId = ctx.simpleNodeReference().NodeId().getText();
    return new Operand<>(this.absolutePathOfNode(nodeId), new NodeOrigin(this, nodeId));
  }

  /**
   * A field with its predicates and indexes, where a context is declared or a field is taken as
   * it is.
   */
  @Override
  public Operand<?> visitFieldContext(final FieldContextContext ctx) {
    return this.withBrackets(this.visitOnlyChild(ctx), ctx.contextBracket());
  }

  /**
   * A node with its predicates and indexes, where a context is declared.
   */
  @Override
  public Operand<?> visitNodeContext(final NodeContextContext ctx) {
    return this.withBrackets(this.visitOnlyChild(ctx), ctx.contextBracket());
  }

  /**
   * Applies the predicates and the indexes after the reference of a context, in the order they
   * are written.
   */
  private Operand<?> withBrackets(final Operand<?> reference,
      final List<ContextBracketContext> brackets) {
    Operand<?> selected = reference;
    for (final ContextBracketContext bracket : brackets) {
      selected = bracket.predicate() != null
          ? selected.filteredBy(bracket.predicate().expression())
          : selected.indexedBy(bracket.position);
    }
    return selected;
  }

  /**
   * A variable, a parameter, a context variable, or a name that none of them has. Such a name has
   * no value: it may only name a dictionary, which a key in brackets after it looks up, and its
   * operand holds an empty expression, which no reading takes.
   */
  @Override
  public Operand<?> visitVariableReference(final VariableReferenceContext ctx) {
    final String name = ctx.variableName.getText();
    final Optional<TypedExpression> parameter = this.stack.getParameter(name);
    if (parameter.isPresent()) {
      return new Operand<>(parameter.get(), new VariableOrigin(this, ctx, name));
    }
    final Optional<Variable> variable = this.stack.getVariable(name);
    if (variable.isPresent()) {
      return this.variableReference(ctx, variable.get());
    }
    return new Operand<>(Expression.empty(StringExpression.class),
        new DictionaryNameOrigin(this, ctx, name));
  }

  /**
   * A variable, or a context variable, whose reference is a path to the one element of its context
   * that it holds.
   */
  private Operand<?> variableReference(final VariableReferenceContext ctx,
      final Variable variable) {
    final Context context = this.contextsOfVariables.get(variable);
    if (context == null) {
      return new Operand<>(variable.referenceExpression,
          new VariableOrigin(this, ctx, variable.name));
    }
    return new Operand<>((PathExpression) variable.referenceExpression,
        new ContextVariableOrigin(this, ctx, variable.name, context));
  }

  /**
   * Declares a context variable: a variable that holds one element of its context, and names the
   * context where it is read.
   *
   * @param variable the variable, whose reference is a path
   * @param context the context
   */
  void declareContextVariable(final Variable variable, final Context context) {
    this.stack.declareIdentifier(variable);
    this.contextsOfVariables.put(variable, context);
  }

  @Override
  public Operand<?> visitCodelistExpression(final CodelistExpressionContext ctx) {
    return this.visit(ctx.codelistReference());
  }

  @Override
  public Operand<?> visitCodelistReference(final CodelistReferenceContext ctx) {
    return this.operand(this.script.composeList(
        this.symbols.expandCodelist(ctx.codelistName.getText()).stream()
            .map(this.script::getStringLiteralFromUnquotedString)
            .collect(Collectors.toList()),
        StringSequenceExpression.class));
  }

  private String fieldIdOf(final LinkedFieldReferenceContext ctx) {
    return this.fieldIdOf(ctx, ctx.simpleFieldReference().fieldId.getText(),
        ctx.linkedFieldProperty());
  }

  private String fieldIdOf(final FieldMentionContext ctx) {
    return this.fieldIdOf(ctx, ctx.fieldId.getText(), ctx.linkedFieldProperty());
  }

  /**
   * @param reference the reference to the field, where an error is reported
   * @param baseFieldId the field written
   * @param property the linked field property after it, or {@code null}
   * @return the identifier of the field written, or of the field the property links to it
   * @throws InvalidUsageException if a property links to a field that cannot be withheld
   */
  private String fieldIdOf(final ParserRuleContext reference, final String baseFieldId,
      final LinkedFieldPropertyContext property) {
    return property == null ? baseFieldId : this.linkedFieldIdOf(reference, baseFieldId, property);
  }

  /**
   * Gives the field linked to the privacy settings of a field. Only a field that can be withheld
   * has privacy settings, so any other field is rejected, as the privacy properties reject it.
   */
  private String linkedFieldIdOf(final ParserRuleContext reference, final String baseFieldId,
      final LinkedFieldPropertyContext ctx) {
    this.requireWithholdable(reference, baseFieldId);
    if (ctx.PublicationDate() != null) {
      return this.symbols.getPrivacySettingOfField(baseFieldId,
          PrivacySetting.PUBLICATION_DATE_FIELD);
    }
    if (ctx.JustificationCode() != null) {
      return this.symbols.getPrivacySettingOfField(baseFieldId,
          PrivacySetting.JUSTIFICATION_CODE_FIELD);
    }
    if (ctx.JustificationDescription() != null) {
      return this.symbols.getPrivacySettingOfField(baseFieldId,
          PrivacySetting.JUSTIFICATION_DESCRIPTION_FIELD);
    }
    throw TranslatorConfigurationException.unhandledLinkedFieldProperty(ctx.getText());
  }

  /**
   * Returns the reference to the value of a field, given the path of the field. The value of an
   * attribute field is its attribute, on the element of the field. The value has the cardinality
   * of the path.
   *
   * @param fieldId the field
   * @param path the path of the field
   * @return the value of the field on that path
   */
  PathExpression fieldValueOf(final String fieldId, final PathExpression path) {
    if (!this.symbols.isAttributeField(fieldId)) {
      return this.script.composeFieldValueReference(path);
    }
    final PathExpression attribute = this.script.composeFieldAttributeReference(
        this.script.contextualizePath(
            this.symbols.getAbsolutePathOfFieldWithoutTheAttribute(fieldId),
            this.absolutePathOfCurrentContext()),
        this.symbols.getAttributeNameFromAttributeField(fieldId),
        ScalarPath.fromFieldType.get(FieldTypes.fromString(this.symbols.getTypeOfField(fieldId))));
    return continuing(path, attribute);
  }

  /**
   * Types a path that continues from the nodes another path selects: it may select several nodes if
   * the other may, and otherwise as many as it selects from a single node.
   *
   * @param start the path whose nodes the continuation starts from
   * @param continuation the continuing path, typed as it is from a single node
   * @return the continuation, as a sequence if the start is one
   */
  private static PathExpression continuing(final PathExpression start,
      final PathExpression continuation) {
    return start instanceof SequenceExpression ? continuation.asSequence() : continuation;
  }

  // #endregion References ----------------------------------------------------

  // #region Steps after a reference ------------------------------------------

  /**
   * The attribute of a field, on each element the field's path selects.
   */
  @Override
  public Operand<?> visitAttributeExpression(final AttributeExpressionContext ctx) {
    final String attribute = ctx.attributeName.getText();
    return this.visit(ctx.expression()).readFromField(ctx, Construct.operator("/@" + attribute, 1),
        (field, origin) -> new Operand<>(continuing(field,
            this.script.composeFieldAttributeReference(field, attribute, StringPath.class)),
            origin.asValue()));
  }

  @Override
  public Operand<?> visitRawValueExpression(final RawValueExpressionContext ctx) {
    return this.visit(ctx.expression()).readFromField(ctx,
        Construct.operator(":" + ctx.RawValue().getText(), 1),
        (field, origin) -> new Operand<>(this.script.composeFieldRawValueReference(field),
            origin.asValue()));
  }

  @Override
  public Operand<?> visitPreferredLanguageExpression(final PreferredLanguageExpressionContext ctx) {
    return this.preferredLanguage(ctx, ctx.expression(), TemplateFunctions.PREFERRED_LANGUAGE,
        Construct.operator(":" + ctx.PreferredLanguage().getText(), 1));
  }

  @Override
  public Operand<?> visitPreferredLanguageTextExpression(
      final PreferredLanguageTextExpressionContext ctx) {
    return this.preferredLanguage(ctx, ctx.expression(), TemplateFunctions.PREFERRED_LANGUAGE_TEXT,
        Construct.operator(":" + ctx.PreferredLanguageText().getText(), 1));
  }

  /**
   * Translates the preferred language of a multilingual field, or its text in that language, as
   * the postfix form and the function form both write it: the operand must be a field, and the
   * built-in takes it as a path.
   *
   * @param call the postfix form or the call of the function
   * @param field the operand
   * @param builtIn the built-in
   * @param construct the form, as a type error names it
   * @return the result of the built-in
   */
  private Operand<?> preferredLanguage(final ParserRuleContext call, final ExpressionContext field,
      final BuiltIn builtIn, final Construct construct) {
    return this.visit(field).readFromField(call, construct,
        (path, origin) -> this.pathCall(call, builtIn, construct,
            List.of(new Operand<>(path, origin))));
  }

  /**
   * A field read in the context its left side names: {@code ND-1::BT-1}, {@code BT-1::BT-2},
   * {@code $context::BT-1}. The path of the field from that context continues the path to it.
   */
  @Override
  public Operand<?> visitFieldFromContextExpression(final FieldFromContextExpressionContext ctx) {
    final Context context = this.contextBefore(ctx.expression());
    final String fieldId = this.fieldIdOf(ctx.linkedFieldReference());
    final PathExpression field = this.symbols.getRelativePathOfField(fieldId, context.symbol());
    return new Operand<>(this.joinReference(context.relativePath(), field),
        new FieldOrigin(this, ctx, fieldId, this.efxContext.symbol()));
  }

  /**
   * A node read in the context its left side names: {@code ND-1::ND-2}, {@code $context::ND-1}.
   */
  @Override
  public Operand<?> visitNodeFromContextExpression(final NodeFromContextExpressionContext ctx) {
    final Context context = this.contextBefore(ctx.expression());
    final String nodeId = ctx.simpleNodeReference().NodeId().getText();
    final PathExpression node = this.symbols.getRelativePathOfNode(nodeId, context.symbol());
    return new Operand<>(this.joinReference(context.relativePath(), node),
        new NodeOrigin(this, nodeId));
  }

  private Context contextBefore(final ExpressionContext left) {
    final Operand<?> operand = this.visit(left);
    return operand.context().orElseThrow(
        () -> new Operands(CONTEXT_OVERRIDE, List.of(operand), List.of(operand.expression()))
            .rejected(0, List.of(ParameterType.PATH), left));
  }

  /**
   * Joins the path to the context of a context override with the path of the reference from that
   * context. The joined reference may give several values if either path may select several
   * nodes. A context variable holds one node, so its own path never does.
   *
   * @param context the path to the context, from the context the override is written in
   * @param reference the path of the reference, from the context of the override
   * @return the path of the reference from the context the override is written in
   */
  private PathExpression joinReference(final PathExpression context,
      final PathExpression reference) {
    return continuing(context, this.script.joinPaths(context, reference));
  }

  // #endregion Steps after a reference ---------------------------------------

  // #region Brackets ---------------------------------------------------------

  /**
   * A predicate, or a key in brackets after the name of a dictionary.
   */
  @Override
  public Operand<?> visitFilteredExpression(final FilteredExpressionContext ctx) {
    return this.visit(ctx.expression(0)).filteredBy(ctx.expression(1));
  }

  /**
   * An index: the element at a position in a sequence.
   */
  @Override
  public Operand<?> visitIndexedExpression(final IndexedExpressionContext ctx) {
    return this.visit(ctx.expression(0)).indexedBy(ctx.expression(1));
  }

  /**
   * A predicate after a field or a node. Its condition is read in the context of the field or
   * node, as each element the path selects, and keeps the elements for which it holds.
   *
   * @param path the path of the field or the node
   * @param origin the field or the node
   * @param condition the condition of the predicate
   * @return the elements the predicate keeps, with the origin of the field or node
   * @throws TypeMismatchException if the condition is not an indicator
   */
  Operand<?> filtered(final PathExpression path, final PathOrigin origin,
      final ExpressionContext condition) {
    origin.pushElementContext();
    final PathExpression selected = origin.filter(path,
        predicate(new Operand<>(path, origin), path, this.visit(condition))
            .converted(1, BooleanExpression.class, condition));
    this.readsInContexts.remove(this.efxContext.pop());
    return new Operand<>(selected, origin);
  }

  /**
   * A predicate after a value, which is rejected: a value has no elements that the condition could
   * be read from. The condition is translated only so that the error names its type.
   *
   * @param sequence the value
   * @param condition the condition of the predicate
   * @return the error
   */
  TypeMismatchException predicateOnValue(final Operand<?> sequence,
      final ExpressionContext condition) {
    return predicate(sequence, sequence.expression(), this.visit(condition))
        .rejected(0, List.of(ParameterType.PATH), this.stack.peekParserContext());
  }

  /**
   * Gives the operands of a predicate. A number for its condition is a position written as a
   * predicate, so its errors suggest the index.
   *
   * @param sequence what the predicate follows
   * @param elements the elements the predicate reads from it
   * @param condition the condition, translated
   * @return the operands
   */
  private static Operands predicate(final Operand<?> sequence, final TypedExpression elements,
      final Operand<?> condition) {
    final TypedExpression value = condition.value();
    return new Operands(PREDICATE, List.of(sequence, condition), List.of(elements, value),
        value.is(EfxDataType.Number.class) ? SELECT_BY_POSITION : List.of());
  }

  /**
   * An index after a field or a node: the element at a position in the whole sequence the path
   * selects, rather than at that position among the children of each parent, which a predicate on
   * the last step of the path would select. The position is read once, where the expression
   * stands. It is bound before the element is selected if it reads the context, which the target
   * language would read from each element instead.
   *
   * @param path the path of the field or the node
   * @param origin the field or the node
   * @param position the position the index selects
   * @return the element at that position, with the origin of the field or node
   * @throws TypeMismatchException if the position is not a number
   */
  Operand<?> positionInPath(final PathExpression path, final PathOrigin origin,
      final ExpressionContext position) {
    final Reads reads = this.readsInCurrentContext();
    final int readsBefore = reads.ofContext();
    final NumericExpression number = index(new Operand<>(path, origin), path,
        this.visit(position)).converted(1, NumericExpression.class, position);
    final Class<? extends EfxDataType.Primitive> primitive =
        EfxTypeLattice.toPrimitive(path.getDataType());
    final Class<? extends SequencePath> elementsType = SequencePath.fromEfxDataType.get(primitive);
    return new Operand<>(this.elementAt(this.inParentheses(path, elementsType), elementsType,
        number, ScalarPath.fromEfxDataType.get(primitive), reads.ofContext() > readsBefore),
        origin);
  }

  /**
   * An index after a value: the value at a position in the sequence of its values. A single value
   * is a sequence of one. The position is read once, where the expression stands. It is bound
   * before the value is selected if it reads the context or the root, by a path or by a lookup in a
   * dictionary: the target language would read it for each value, which has no element that a
   * path or a lookup could start from.
   *
   * @param sequence the value
   * @param position the position the index selects
   * @return the value at that position
   * @throws TypeMismatchException if the operand has no value or the position is not a number
   */
  Operand<?> positionInValue(final Operand<?> sequence, final ExpressionContext position) {
    final TypedExpression given = sequence.value();
    final Reads reads = this.readsInCurrentContext();
    final int readsBefore = reads.all();
    final Operands operands = index(sequence, given, this.visit(position));
    final TypedExpression value = operands.valueAt(0, this.stack.peekParserContext());
    final NumericExpression number = operands.converted(1, NumericExpression.class, position);
    final Class<? extends SequenceExpression> listType = this.sequenceTypeOf(value);
    final SequenceExpression list = value instanceof PathExpression
        ? this.inParentheses(value, listType)
        : TypedExpression.from(value, listType);
    return this.operand(this.elementAt(list, listType, number, this.scalarTypeOf(value),
        reads.all() > readsBefore));
  }

  /**
   * Gives the operands of an index.
   *
   * @param sequence what the index follows
   * @param elements the elements the index selects from
   * @param position the position, translated
   * @return the operands
   */
  private static Operands index(final Operand<?> sequence, final TypedExpression elements,
      final Operand<?> position) {
    return new Operands(INDEX, List.of(sequence, position), List.of(elements, position.value()));
  }

  /**
   * The element at a position in a sequence. The position is read once, where the expression
   * stands: if the target language would read it again for each element, it is bound before the
   * element is selected.
   *
   * @param <T> the class of the element
   * @param sequence the sequence
   * @param sequenceType the class of the sequence
   * @param position the position, a single number
   * @param type the class of the element
   * @param readForEachElement whether the target language would read the position for each element
   * @return the element at that position
   */
  private <T extends ScalarExpression> T elementAt(final SequenceExpression sequence,
      final Class<? extends SequenceExpression> sequenceType, final NumericExpression position,
      final Class<T> type, final boolean readForEachElement) {
    return readForEachElement
        ? this.elementAtBoundPosition(sequence, sequenceType, position, type)
        : this.script.composeIndexer(sequence, position, type);
  }

  /**
   * The element at a position that is bound before the element is selected, so that the target
   * language reads it once rather than for each element of the sequence. The binding is in
   * parentheses, so that a step, a predicate or an operator after it applies to the element:
   * {@code (for $__index in position return sequence[$__index])}.
   *
   * @param <T> the class of the element
   * @param sequence the sequence
   * @param sequenceType the class of the sequence
   * @param position the position, a single number
   * @param type the class of the element
   * @return the element at that position
   */
  private <T extends ScalarExpression> T elementAtBoundPosition(final SequenceExpression sequence,
      final Class<? extends SequenceExpression> sequenceType, final NumericExpression position,
      final Class<T> type) {
    final IteratorListExpression binding =
        this.script.composeIteratorList(List.of(this.script.composeIteratorExpression(
            this.script.composeVariableDeclaration(INDEX_VARIABLE, NumericExpression.class),
            TypedExpression.from(position, NumericSequenceExpression.class))));
    final T element = this.script.composeIndexer(sequence,
        this.script.composeVariableReference(INDEX_VARIABLE, NumericExpression.class), type);
    return this.inParentheses(
        this.script.composeForExpression(binding, element, sequenceType), type);
  }

  /**
   * A key in brackets after the name of a dictionary: the values the key indexes. A dictionary
   * indexes the fields of the whole notice, so a lookup reads the root.
   *
   * @param name the name of the dictionary
   * @param reference the name of the dictionary, as written
   * @param key the key
   * @return the values the dictionary indexes by the key
   * @throws InvalidIdentifierException if no dictionary has the name
   */
  Operand<?> lookUp(final String name, final ParserRuleContext reference,
      final ExpressionContext key) {
    final Dictionary dictionary = this.stack.getDictionary(name);
    this.readsInCurrentContext().countReadOfRoot();
    return new Operand<>(
        this.script.composeDictionaryLookup(dictionary.name,
            this.valueAs(key, StringExpression.class,
                Construct.of("Dictionary '" + reference.getText() + "'", "key")),
            dictionary.type),
        new DictionaryOrigin(this, reference, dictionary.name));
  }

  /**
   * @param fieldId a field
   * @return the path of the field from the current context, whose read is counted
   */
  private PathExpression relativePathOfField(final String fieldId) {
    this.countReadOfCurrentContext();
    return this.symbols.getRelativePathOfField(fieldId, this.efxContext.symbol());
  }

  /**
   * @param nodeId a node
   * @return the path of the node from the current context, whose read is counted
   */
  private PathExpression relativePathOfNode(final String nodeId) {
    this.countReadOfCurrentContext();
    return this.symbols.getRelativePathOfNode(nodeId, this.efxContext.symbol());
  }

  /**
   * @return the absolute path of the current context, whose read is counted
   */
  private PathExpression absolutePathOfCurrentContext() {
    this.countReadOfCurrentContext();
    return this.efxContext.peek().absolutePath();
  }

  /**
   * @param fieldId a field
   * @return the path of the field from the root, whose read is counted
   */
  private PathExpression absolutePathOfField(final String fieldId) {
    this.readsInCurrentContext().countReadOfRoot();
    return this.symbols.getRelativePathOfField(fieldId, NO_CONTEXT);
  }

  /**
   * @param nodeId a node
   * @return the path of the node from the root, whose read is counted
   */
  private PathExpression absolutePathOfNode(final String nodeId) {
    this.readsInCurrentContext().countReadOfRoot();
    return this.symbols.getRelativePathOfNode(nodeId, NO_CONTEXT);
  }

  /**
   * Counts a read of the current context: a reference is resolved from it.
   */
  private void countReadOfCurrentContext() {
    this.readsInCurrentContext().countReadOfContext();
  }

  /**
   * @return the reads made in the current context, or where no context is declared
   */
  private Reads readsInCurrentContext() {
    return this.efxContext.isEmpty() ? this.readsWithoutContext
        : this.readsInContexts.computeIfAbsent(this.efxContext.peek(), context -> new Reads());
  }

  private <T extends TypedExpression> T inParentheses(final TypedExpression expression,
      final Class<T> type) {
    return this.script.composeParenthesizedExpression(TypedExpression.from(expression, type),
        type);
  }

  /**
   * The references resolved, and the dictionaries looked up, while a context is the current one:
   * the reads of the context, and the reads of the root, which an absolute reference and a lookup
   * start from. Where no context is declared, the root is the context that a relative reference
   * reads.
   */
  private static final class Reads {

    private int ofContext;
    private int ofRoot;

    void countReadOfContext() {
      this.ofContext++;
    }

    void countReadOfRoot() {
      this.ofRoot++;
    }

    /**
     * @return how many references have been resolved from the context
     */
    int ofContext() {
      return this.ofContext;
    }

    /**
     * @return how many reads of the context and of the root have been made
     */
    int all() {
      return this.ofContext + this.ofRoot;
    }
  }

  // #endregion Brackets ------------------------------------------------------

  // #region Operators --------------------------------------------------------

  @Override
  public Operand<?> visitMultiplicativeExpression(final MultiplicativeExpressionContext ctx) {
    return this.operation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public Operand<?> visitAdditiveExpression(final AdditiveExpressionContext ctx) {
    return this.operation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public Operand<?> visitComparisonExpression(final ComparisonExpressionContext ctx) {
    return this.operation(ctx, this.operatorNamed(ctx.operator));
  }

  @Override
  public Operand<?> visitInListCondition(final InListConditionContext ctx) {
    return this.operation(ctx,
        ctx.modifier == null ? ConditionOperators.IN : ConditionOperators.NOT_IN);
  }

  @Override
  public Operand<?> visitUniqueValueCondition(final UniqueValueConditionContext ctx) {
    return this.operation(ctx, ctx.modifier == null ? ConditionOperators.IS_UNIQUE_IN
        : ConditionOperators.IS_NOT_UNIQUE_IN);
  }

  @Override
  public Operand<?> visitEmptinessCondition(final EmptinessConditionContext ctx) {
    return this.condition(ctx,
        ctx.modifier == null ? ConditionOperators.IS_EMPTY : ConditionOperators.IS_NOT_EMPTY);
  }

  @Override
  public Operand<?> visitDistinctCondition(final DistinctConditionContext ctx) {
    return this.condition(ctx, ctx.modifier == null ? ConditionOperators.HAS_DUPLICATES
        : ConditionOperators.HAS_NO_DUPLICATES);
  }

  @Override
  public Operand<?> visitLikePatternCondition(final LikePatternConditionContext ctx) {
    final String pattern = ctx.pattern.getText();
    return this.condition(ctx,
        ctx.modifier == null ? PatternMatch.like(pattern) : PatternMatch.notLike(pattern));
  }

  /**
   * {@code is present} takes its operand as a path.
   */
  @Override
  public Operand<?> visitPresenceCondition(final PresenceConditionContext ctx) {
    final BuiltIn operator =
        ctx.modifier == null ? ConditionOperators.IS_PRESENT : ConditionOperators.IS_NOT_PRESENT;
    return this.pathCall(ctx, operator, Construct.operator(operator.getName(), 1),
        this.operandsOf(ctx));
  }

  @Override
  public Operand<?> visitLogicalAndCondition(final LogicalAndConditionContext ctx) {
    return this.operation(ctx, LogicalOperators.AND);
  }

  @Override
  public Operand<?> visitLogicalOrCondition(final LogicalOrConditionContext ctx) {
    return this.operation(ctx, LogicalOperators.OR);
  }

  /**
   * Finds the operator written as a token.
   *
   * @param operator the token of a comparison or arithmetic operator
   * @return the operator of the catalogue that has the token's symbol as its name
   */
  private BuiltIn operatorNamed(final Token operator) {
    return BuiltIns.INSTANCE.find(operator.getText()).orElseThrow(
        () -> TranslatorConfigurationException.unhandledOperator(operator.getText(),
            "operatorNamed"));
  }

  // #endregion Operators -----------------------------------------------------

  // #region Composites -------------------------------------------------------

  /**
   * Parentheses give the value of what they hold. It is no longer a reference, but keeps the error
   * of the reference. A node has no value, so it is rejected in parentheses.
   */
  @Override
  public Operand<?> visitParenthesizedExpression(final ParenthesizedExpressionContext ctx) {
    final Operand<?> inner = this.visit(ctx.expression());
    final TypedExpression value = new Operands(PARENTHESES, List.of(inner)).valueAt(0, ctx);
    final Class<? extends TypedExpression> type = value instanceof SequenceExpression
        ? this.sequenceTypeOf(value)
        : this.scalarTypeOf(value);
    return new Operand<>(this.inParentheses(value, type), inner.origin().asValue());
  }

  /**
   * A conditional: the condition is a boolean, and the two branches have one primitive. It gives
   * the join of its branches: a sequence if either branch is one, the other promoted to a sequence
   * of one, and a single value if both are.
   */
  @Override
  public Operand<?> visitConditionalExpression(final ConditionalExpressionContext ctx) {
    final Operands parts = new Operands(CONDITIONAL, this.operandsOf(ctx));
    return this.operand(this.conditional(ctx, parts, this.joinOfBranches(ctx, parts)));
  }

  /**
   * Gives the class of a conditional, the join of its branches. The then branch binds {@code T},
   * as the first argument of a generic built-in does, and the else branch must have its primitive,
   * as a single value or as a sequence.
   *
   * @param ctx the conditional
   * @param parts its condition and its two branches
   * @return the sequence class of the primitive if either branch is a sequence, and its scalar
   *         class otherwise
   * @throws TypeMismatchException if the then branch is a node, or the else branch is not of the
   *         primitive of the then branch
   */
  private Class<? extends TypedExpression> joinOfBranches(final ConditionalExpressionContext ctx,
      final Operands parts) {
    final TypedExpression then = parts.valueAt(1, ctx);
    final ParameterType single = this.bindingOf(ParameterType.T, then);
    final ParameterType sequence = this.bindingOf(ParameterType.T_SEQUENCE, then);
    final TypedExpression otherwise =
        parts.valueAt(2, List.of(single, sequence), ctx.expression(2));
    return (then instanceof SequenceExpression || otherwise instanceof SequenceExpression
        ? sequence
        : single).getExpressionType();
  }

  private <T extends TypedExpression> T conditional(final ConditionalExpressionContext ctx,
      final Operands parts, final Class<T> type) {
    final T otherwise = parts.converted(2, type, ctx.expression(2));
    final T then = parts.converted(1, type, ctx.expression(1));
    final BooleanExpression test = parts.converted(0, BooleanExpression.class, ctx.expression(0));
    return this.script.composeConditionalExpression(test, then, otherwise, type);
  }

  /**
   * A list: its items are single values of the primitive of the first.
   */
  @Override
  public Operand<?> visitList(final ListContext ctx) {
    final List<ExpressionContext> expressions = ctx.expression();
    final List<Operand<?>> items = new ArrayList<>(expressions.size());
    final List<TypedExpression> values = new ArrayList<>(expressions.size());
    for (final ExpressionContext item : expressions) {
      final Operand<?> operand = this.visit(item);
      items.add(operand);
      values.add(operand.value());
    }
    final Operands list = new Operands(Construct.list(items.size()), items, values);
    return this.operand(this.list(expressions, list, this.scalarTypeOf(list.valueAt(0, ctx))));
  }

  private <T extends ScalarExpression> SequenceExpression list(
      final List<ExpressionContext> expressions, final Operands items, final Class<T> itemType) {
    final List<T> list = new ArrayList<>(expressions.size());
    for (int position = 0; position < expressions.size(); position++) {
      list.add(items.converted(position, itemType, expressions.get(position)));
    }
    return this.script.composeList(list, this.sequenceTypeOf(list.get(0)));
  }

  @Override
  public Operand<?> visitQuantifiedExpression(final QuantifiedExpressionContext ctx) {
    this.stack.pushStackFrame();
    final IteratorListExpression iterators = this.iteratorsOf(ctx.iteratorList());
    final BooleanExpression condition = this.valueAs(ctx.expression(), BooleanExpression.class,
        Construct.of("Quantifier '" + (ctx.Every() != null ? ctx.Every() : ctx.Some()).getText()
            + "'", "condition"));
    final BooleanExpression quantified = ctx.Every() != null
        ? this.script.composeAllSatisfy(iterators, condition)
        : this.script.composeAnySatisfies(iterators, condition);
    this.stack.popStackFrame();
    return this.operand(quantified);
  }

  /**
   * An iteration: a single value for each iteration gives the sequence of them, and a sequence
   * for each iteration gives their concatenation.
   */
  @Override
  public Operand<?> visitSequenceFromIteration(final SequenceFromIterationContext ctx) {
    this.stack.pushStackFrame();
    final IteratorListExpression iterators = this.iteratorsOf(ctx.iteratorList());
    final TypedExpression body = this.valueOf(ctx.expression(), ITERATION);
    final SequenceExpression iteration =
        this.iteration(iterators, body, ctx.Distinct() != null, this.sequenceTypeOf(body));
    this.stack.popStackFrame();
    return this.operand(iteration);
  }

  private <T extends SequenceExpression> T iteration(final IteratorListExpression iterators,
      final TypedExpression body, final boolean distinct, final Class<T> sequenceType) {
    final T result = body instanceof ScalarExpression
        ? this.script.composeForExpression(iterators, (ScalarExpression) body, sequenceType)
        : this.script.composeForExpression(iterators, (SequenceExpression) body, sequenceType);
    return distinct ? this.script.composeDistinctValuesFunction(result, sequenceType) : result;
  }

  /**
   * Declares the variables of the iterators in the current stack frame, each before the source of
   * the next is translated.
   */
  private IteratorListExpression iteratorsOf(final IteratorListContext ctx) {
    final List<IteratorExpression> iterators = new ArrayList<>(ctx.iteratorExpression().size());
    for (final IteratorExpressionContext iterator : ctx.iteratorExpression()) {
      iterators.add(iterator.accept(this.iteratorDeclarations));
    }
    return this.script.composeIteratorList(iterators);
  }

  /**
   * Translates an iterator and declares its variable.
   */
  private final class IteratorDeclarations extends EfxBaseVisitor<IteratorExpression> {

    /**
     * An iterator over values: they are of the primitive declared for the variable.
     */
    @Override
    public IteratorExpression visitValueIterator(final ValueIteratorContext ctx) {
      final Class<? extends EfxDataType.Primitive> primitive = primitiveOf(ctx.primitiveType());
      final String name = ctx.variableName.getText();
      return this.iterator(name, ScalarExpression.fromEfxDataType.get(primitive),
          EfxExpressionVisitor.this.valueAs(ctx.expression(),
              SequenceExpression.fromEfxDataType.get(primitive),
              Construct.of("Iterator '" + ctx.primitiveType().getText() + ":$" + name + "'",
                  "sequence")));
    }

    private <T extends ScalarExpression> IteratorExpression iterator(final String name,
        final Class<T> variableType, final SequenceExpression source) {
      final ScriptGenerator script = EfxExpressionVisitor.this.script;
      final Variable variable = new Variable(name,
          script.composeVariableDeclaration(name, variableType), source,
          script.composeVariableReference(name, variableType));
      EfxExpressionVisitor.this.stack.declareIdentifier(variable);
      return script.composeIteratorExpression(variable.declarationExpression, source);
    }

    /**
     * An iterator over the elements of a field or a node: its variable names the element as a
     * context.
     */
    @Override
    public IteratorExpression visitContextIterator(final ContextIteratorContext ctx) {
      final ScriptGenerator script = EfxExpressionVisitor.this.script;
      final Context context = EfxExpressionVisitor.this
          .contextOf(ctx.getRuleContext(ParserRuleContext.class, 0));
      final PathExpression path = context.relativePath();
      final String name = ctx.variableName.getText();
      final Class<? extends TypedExpression> variableType = path.asScalar().getClass();
      final Variable variable = new Variable(name,
          script.composeVariableDeclaration(name, variableType), Expression.empty(variableType),
          script.composeVariableReference(name, variableType));
      EfxExpressionVisitor.this.declareContextVariable(variable, context);
      return script.composeIteratorExpression(variable.declarationExpression, path.asSequence());
    }
  }

  // #endregion Composites ----------------------------------------------------

  // #region Field properties -------------------------------------------------

  @Override
  public Operand<?> visitFieldPropertyExpression(final FieldPropertyExpressionContext ctx) {
    return this.visit(ctx.fieldProperty());
  }

  @Override
  public Operand<?> visitFieldWasWithheldProperty(final FieldWasWithheldPropertyContext ctx) {
    final String fieldId = this.fieldIdOf(ctx.fieldMention());
    return this.operand(
        this.composeWasWithheldCondition(fieldId, this.privacyCodeOf(ctx, fieldId)));
  }

  @Override
  public Operand<?> visitFieldIsWithheldProperty(final FieldIsWithheldPropertyContext ctx) {
    final String fieldId = this.fieldIdOf(ctx.fieldMention());
    return this.operand(this.script.composeLogicalAnd(
        this.composeWasWithheldCondition(fieldId, this.privacyCodeOf(ctx, fieldId)),
        this.composeStillWithheldCondition(fieldId)));
  }

  @Override
  public Operand<?> visitFieldIsWithholdableProperty(
      final FieldIsWithholdablePropertyContext ctx) {
    return this.operand(
        this.script.getBooleanEquivalent(this.isWithholdable(this.fieldIdOf(ctx.fieldMention()))));
  }

  /**
   * A field is disclosed if it was withheld, is no longer withheld, and is not masked.
   */
  @Override
  public Operand<?> visitFieldIsDisclosedProperty(final FieldIsDisclosedPropertyContext ctx) {
    final String fieldId = this.fieldIdOf(ctx.fieldMention());
    return this.operand(this.script.composeLogicalAnd(
        this.script.composeLogicalAnd(
            this.composeWasWithheldCondition(fieldId, this.privacyCodeOf(ctx, fieldId)),
            this.script.composeLogicalNot(this.composeStillWithheldCondition(fieldId))),
        this.script.composeLogicalNot(this.composeIsMaskedCondition(fieldId))));
  }

  /**
   * A field is masked if it was withheld and its value is the privacy mask.
   */
  @Override
  public Operand<?> visitFieldIsMaskedProperty(final FieldIsMaskedPropertyContext ctx) {
    final String fieldId = this.fieldIdOf(ctx.fieldMention());
    return this.operand(this.script.composeLogicalAnd(
        this.composeWasWithheldCondition(fieldId, this.privacyCodeOf(ctx, fieldId)),
        this.composeIsMaskedCondition(fieldId)));
  }

  @Override
  public Operand<?> visitFieldPrivacyCodeProperty(final FieldPrivacyCodePropertyContext ctx) {
    final String fieldId = this.fieldIdOf(ctx.fieldMention());
    return this.operand(
        this.script.getStringLiteralFromUnquotedString(this.privacyCodeOf(ctx, fieldId)));
  }

  /**
   * @throws InvalidUsageException if the field cannot be withheld
   */
  private String privacyCodeOf(final ParserRuleContext property, final String fieldId) {
    this.requireWithholdable(property, fieldId);
    return this.symbols.getPrivacyCodeOfField(fieldId);
  }

  /**
   * Rejects a field that cannot be withheld where only such a field has what is asked of it: its
   * privacy code, or a field linked to its privacy settings.
   *
   * @param reference where the error is reported
   * @param fieldId the field
   * @throws InvalidUsageException if the field cannot be withheld
   */
  private void requireWithholdable(final ParserRuleContext reference, final String fieldId) {
    if (!this.isWithholdable(fieldId)) {
      throw InvalidUsageException.fieldNotWithholdable(reference, fieldId);
    }
  }

  /**
   * @param fieldId a field
   * @return whether the field can be withheld from publication: whether it has a privacy code
   */
  private boolean isWithholdable(final String fieldId) {
    final String privacyCode = this.symbols.getPrivacyCodeOfField(fieldId);
    return privacyCode != null && !privacyCode.isEmpty();
  }

  private BooleanExpression composeWasWithheldCondition(final String fieldId,
      final String privacyCode) {
    final String privacyCodeFieldId =
        this.symbols.getPrivacySettingOfField(fieldId, PrivacySetting.PRIVACY_CODE_FIELD);
    if (privacyCodeFieldId == null) {
      throw SdkInconsistencyException.missingPrivacyCodeField(fieldId);
    }
    return this.script.composeComparisonOperation(
        new StringExpression(this.script.composeFieldValueReference(
            this.relativePathOfField(privacyCodeFieldId)).getScript()),
        "==", this.script.getStringLiteralFromUnquotedString(privacyCode));
  }

  private BooleanExpression composeStillWithheldCondition(final String fieldId) {
    final String publicationDateFieldId =
        this.symbols.getPrivacySettingOfField(fieldId, PrivacySetting.PUBLICATION_DATE_FIELD);
    if (publicationDateFieldId == null) {
      throw SdkInconsistencyException.missingPublicationDateField(fieldId);
    }
    final PathExpression publicationDate = this.relativePathOfField(publicationDateFieldId);
    return this.script.composeParenthesizedExpression(
        this.script.composeLogicalOr(
            this.script.composeLogicalNot(this.script.composeExistsCondition(publicationDate)),
            this.script.composeComparisonOperation(
                new DateExpression(
                    this.script.composeFieldValueReference(publicationDate).getScript()),
                ">", this.script.getCurrentDate())),
        BooleanExpression.class);
  }

  private BooleanExpression composeIsMaskedCondition(final String fieldId) {
    final PathExpression fieldValue =
        this.script.composeFieldValueReference(this.relativePathOfField(fieldId));
    return this.script.composeComparisonOperation(
        TypedExpression.from(fieldValue, ScalarExpression.class), "==",
        this.literalOf(this.symbols.getPrivacyMask(fieldId), fieldValue.getDataType()));
  }

  private ScalarExpression literalOf(final String value,
      final Class<? extends EfxDataType> type) {
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

  // #endregion Field properties ----------------------------------------------

  // #region Function invocation ----------------------------------------------

  /**
   * An invocation of a declared function, typed by the declaration: a single value or a sequence
   * of the primitive of its value.
   */
  @Override
  public Operand<?> visitFunctionInvocation(final FunctionInvocationContext ctx) {
    final String name = ctx.functionName.getText();
    final eu.europa.ted.efx.model.variables.Function function = this.stack.getFunction(name);
    final ParsedArguments arguments = this.argumentsOf(ctx, function);
    final ParameterType result = EfxTypeLattice.isSequence(function.dataType)
        ? ParameterType.T_SEQUENCE
        : ParameterType.T;
    return new Operand<>(
        this.script.composeFunctionInvocation(name, arguments.getArgumentValues(),
            this.bindingOf(result, function.expression).getExpressionType()),
        new IdentifierOrigin<>(this, ctx, name));
  }

  /**
   * Translates the arguments of an invocation of a declared function, with
   * {@link #argumentsOf(ParserRuleContext, Parametrised, ArgumentListContext, Construct)}.
   *
   * @param invocation the invocation
   * @param function the function invoked
   * @return the arguments, each converted to the class of its parameter
   * @throws InvalidArgumentException if the number of arguments or the type of one does not match
   */
  ParsedArguments argumentsOf(final FunctionInvocationContext invocation,
      final Parametrised function) {
    final String name =
        invocation.FunctionPrefix().getText() + invocation.functionName.getText();
    return this.argumentsOf(invocation, function, invocation.argumentList(),
        Construct.function(name, function.parameters.size()));
  }

  /**
   * Translates the arguments of an invocation of a declared function or template, and matches
   * them against the parameters of the declaration, as the arguments of a built-in are matched
   * against its overloads. The parameters reject an argument that does not fit them, a node
   * included.
   *
   * <p>An argument that does not fit is reported as such, even a sequence where a single value is
   * required. Only a context variable that holds a node is reported by itself, as it is wherever a
   * value is read from it.</p>
   *
   * @param call the invocation
   * @param declaration the function or template invoked
   * @param list the arguments, or {@code null} if there are none
   * @param construct the invocation, as a type error names it, with an operand for each parameter
   * @return the arguments, each converted to the class of its parameter
   * @throws InvalidArgumentException if the number of arguments or the type of one does not match
   * @throws TypeMismatchException if an argument is a context variable that holds a node
   */
  ParsedArguments argumentsOf(final ParserRuleContext call, final Parametrised declaration,
      final ArgumentListContext list, final Construct construct) {
    final StrictArguments arguments = new StrictArguments(declaration);
    final List<Operand<?>> operands = new ArrayList<>();
    if (list != null) {
      for (final ArgumentContext argument : list.argument()) {
        final Operand<?> operand = this.visit(argument.expression());
        operands.add(operand);
        arguments.addArgument(operand.value());
      }
    }
    final Match match = Parameters.of(declaration.parameters).match(arguments.getValues());
    if (match.isCountMismatch()) {
      throw InvalidArgumentException.argumentNumberMismatch(declaration,
          declaration.parameters.size(), arguments.getValues().size());
    }
    if (!match.isMatched()) {
      final Diagnosis diagnosis = new Diagnosis(construct, arguments.getValues(),
          match.getPosition(), List.of(match.getRequired().describe()));
      final Optional<TypeMismatchException> noValue =
          operands.get(match.getPosition()).errorOf(Rejection.Kind.PATH, diagnosis);
      if (noValue.isPresent()) {
        throw noValue.get();
      }
      throw InvalidArgumentException.argumentTypeMismatch(call, diagnosis);
    }
    return new ParsedArguments(declaration.parameters, match.getArguments().getValues());
  }

  // #endregion Function invocation -------------------------------------------

  // #region Built-in functions -----------------------------------------------

  @Override
  public Operand<?> visitFunctionExpression(final FunctionExpressionContext ctx) {
    return this.visit(ctx.builtInFunction());
  }

  @Override
  public Operand<?> visitBuiltInFunction(final BuiltInFunctionContext ctx) {
    return this.visitOnlyChild(ctx);
  }

  // Boolean functions

  @Override
  public Operand<?> visitNotFunction(final NotFunctionContext ctx) {
    return this.call(ctx, LogicalOperators.NOT);
  }

  @Override
  public Operand<?> visitContainsFunction(final ContainsFunctionContext ctx) {
    return this.call(ctx, StringFunctions.CONTAINS);
  }

  @Override
  public Operand<?> visitStartsWithFunction(final StartsWithFunctionContext ctx) {
    return this.call(ctx, StringFunctions.STARTS_WITH);
  }

  @Override
  public Operand<?> visitEndsWithFunction(final EndsWithFunctionContext ctx) {
    return this.call(ctx, StringFunctions.ENDS_WITH);
  }

  @Override
  public Operand<?> visitSequenceEqualFunction(final SequenceEqualFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.SEQUENCE_EQUAL);
  }

  @Override
  public Operand<?> visitBooleanFromNumberFunction(final BooleanFromNumberFunctionContext ctx) {
    return this.call(ctx, ConversionFunctions.TO_INDICATOR);
  }

  @Override
  public Operand<?> visitStringEmptyFunction(final StringEmptyFunctionContext ctx) {
    return this.call(ctx, StringFunctions.EMPTY);
  }

  // Numeric functions

  @Override
  public Operand<?> visitCountFunction(final CountFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.COUNT);
  }

  @Override
  public Operand<?> visitCountDuplicatesFunction(final CountDuplicatesFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.COUNT_DUPLICATES);
  }

  @Override
  public Operand<?> visitNumberFunction(final NumberFunctionContext ctx) {
    return this.call(ctx, ConversionFunctions.TO_NUMBER);
  }

  @Override
  public Operand<?> visitSumFunction(final SumFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.SUM);
  }

  @Override
  public Operand<?> visitMinFunction(final MinFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.MIN);
  }

  @Override
  public Operand<?> visitMaxFunction(final MaxFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.MAX);
  }

  @Override
  public Operand<?> visitAverageFunction(final AverageFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.AVERAGE);
  }

  @Override
  public Operand<?> visitStringLengthFunction(final StringLengthFunctionContext ctx) {
    return this.call(ctx, StringFunctions.STRING_LENGTH);
  }

  @Override
  public Operand<?> visitIndexOfFunction(final IndexOfFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.INDEX_OF);
  }

  @Override
  public Operand<?> visitIndexOfSubstringFunction(final IndexOfSubstringFunctionContext ctx) {
    return this.call(ctx, StringFunctions.INDEX_OF_SUBSTRING);
  }

  @Override
  public Operand<?> visitAbsoluteFunction(final AbsoluteFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.ABSOLUTE);
  }

  @Override
  public Operand<?> visitRoundFunction(final RoundFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.ROUND);
  }

  @Override
  public Operand<?> visitRoundDownFunction(final RoundDownFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.ROUND_DOWN);
  }

  @Override
  public Operand<?> visitRoundUpFunction(final RoundUpFunctionContext ctx) {
    return this.call(ctx, NumericFunctions.ROUND_UP);
  }

  @Override
  public Operand<?> visitYearFromDateFunction(final YearFromDateFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.YEAR);
  }

  @Override
  public Operand<?> visitMonthFromDateFunction(final MonthFromDateFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.MONTH);
  }

  @Override
  public Operand<?> visitDayFromDateFunction(final DayFromDateFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.DAY);
  }

  @Override
  public Operand<?> visitHoursFromTimeFunction(final HoursFromTimeFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.HOURS);
  }

  @Override
  public Operand<?> visitMinutesFromTimeFunction(final MinutesFromTimeFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.MINUTES);
  }

  @Override
  public Operand<?> visitSecondsFromTimeFunction(final SecondsFromTimeFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.SECONDS);
  }

  @Override
  public Operand<?> visitYearsFromDurationFunction(final YearsFromDurationFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.YEARS);
  }

  @Override
  public Operand<?> visitMonthsFromDurationFunction(final MonthsFromDurationFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.MONTHS);
  }

  @Override
  public Operand<?> visitDaysFromDurationFunction(final DaysFromDurationFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.DAYS);
  }

  // String functions

  @Override
  public Operand<?> visitSubstringFunction(final SubstringFunctionContext ctx) {
    return this.call(ctx, StringFunctions.SUBSTRING);
  }

  @Override
  public Operand<?> visitSubstringBeforeFunction(final SubstringBeforeFunctionContext ctx) {
    return this.call(ctx, StringFunctions.SUBSTRING_BEFORE);
  }

  @Override
  public Operand<?> visitSubstringAfterFunction(final SubstringAfterFunctionContext ctx) {
    return this.call(ctx, StringFunctions.SUBSTRING_AFTER);
  }

  @Override
  public Operand<?> visitTextFunction(final TextFunctionContext ctx) {
    return this.call(ctx, ConversionFunctions.TO_TEXT);
  }

  @Override
  public Operand<?> visitConcatFunction(final ConcatFunctionContext ctx) {
    return this.call(ctx, StringFunctions.CONCAT);
  }

  @Override
  public Operand<?> visitStringJoinFunction(final StringJoinFunctionContext ctx) {
    return this.call(ctx, StringFunctions.STRING_JOIN);
  }

  @Override
  public Operand<?> visitFormatNumberFunction(final FormatNumberFunctionContext ctx) {
    return this.call(ctx, TemplateFunctions.FORMAT_NUMBER);
  }

  @Override
  public Operand<?> visitFormatShortFunction(final FormatShortFunctionContext ctx) {
    return this.call(ctx, TemplateFunctions.FORMAT_SHORT);
  }

  @Override
  public Operand<?> visitFormatMediumFunction(final FormatMediumFunctionContext ctx) {
    return this.call(ctx, TemplateFunctions.FORMAT_MEDIUM);
  }

  @Override
  public Operand<?> visitFormatLongFunction(final FormatLongFunctionContext ctx) {
    return this.call(ctx, TemplateFunctions.FORMAT_LONG);
  }

  @Override
  public Operand<?> visitUpperCaseFunction(final UpperCaseFunctionContext ctx) {
    return this.call(ctx, StringFunctions.UPPER_CASE);
  }

  @Override
  public Operand<?> visitLowerCaseFunction(final LowerCaseFunctionContext ctx) {
    return this.call(ctx, StringFunctions.LOWER_CASE);
  }

  @Override
  public Operand<?> visitNormalizeSpaceFunction(final NormalizeSpaceFunctionContext ctx) {
    return this.call(ctx, StringFunctions.NORMALIZE_SPACE);
  }

  @Override
  public Operand<?> visitTrimFunction(final TrimFunctionContext ctx) {
    return this.call(ctx, StringFunctions.TRIM);
  }

  @Override
  public Operand<?> visitTrimLeftFunction(final TrimLeftFunctionContext ctx) {
    return this.call(ctx, StringFunctions.TRIM_LEFT);
  }

  @Override
  public Operand<?> visitTrimRightFunction(final TrimRightFunctionContext ctx) {
    return this.call(ctx, StringFunctions.TRIM_RIGHT);
  }

  @Override
  public Operand<?> visitPadLeftFunction(final PadLeftFunctionContext ctx) {
    return this.call(ctx, StringFunctions.PAD_LEFT);
  }

  @Override
  public Operand<?> visitPadRightFunction(final PadRightFunctionContext ctx) {
    return this.call(ctx, StringFunctions.PAD_RIGHT);
  }

  @Override
  public Operand<?> visitReplaceFunction(final ReplaceFunctionContext ctx) {
    return this.call(ctx, StringFunctions.REPLACE);
  }

  /**
   * A pattern written as a literal is checked to be a regular expression that every target
   * supports.
   */
  @Override
  public Operand<?> visitReplaceRegexFunction(final ReplaceRegexFunctionContext ctx) {
    if (ctx.pattern instanceof LiteralExpressionContext
        && ((LiteralExpressionContext) ctx.pattern).literal().stringLiteral() != null) {
      EfxRegexValidator.validate(ctx.pattern.getText());
    }
    return this.call(ctx, StringFunctions.REPLACE_REGEX);
  }

  @Override
  public Operand<?> visitRepeatFunction(final RepeatFunctionContext ctx) {
    return this.call(ctx, StringFunctions.REPEAT);
  }

  @Override
  public Operand<?> visitUrlEncodeFunction(final UrlEncodeFunctionContext ctx) {
    return this.call(ctx, StringFunctions.URL_ENCODE);
  }

  @Override
  public Operand<?> visitCapitalizeFirstFunction(final CapitalizeFirstFunctionContext ctx) {
    return this.call(ctx, StringFunctions.CAPITALIZE_FIRST);
  }

  @Override
  public Operand<?> visitPreferredLanguageFunction(final PreferredLanguageFunctionContext ctx) {
    return this.preferredLanguage(ctx, ctx.expression(), TemplateFunctions.PREFERRED_LANGUAGE,
        Construct.function(ctx.PreferredLanguageFunction().getText(), 1));
  }

  @Override
  public Operand<?> visitPreferredLanguageTextFunction(
      final PreferredLanguageTextFunctionContext ctx) {
    return this.preferredLanguage(ctx, ctx.expression(), TemplateFunctions.PREFERRED_LANGUAGE_TEXT,
        Construct.function(ctx.PreferredLanguageTextFunction().getText(), 1));
  }

  // Date, time and duration functions

  @Override
  public Operand<?> visitDateFromStringFunction(final DateFromStringFunctionContext ctx) {
    return this.call(ctx, ConversionFunctions.TO_DATE);
  }

  @Override
  public Operand<?> visitDatePlusDurationFunction(final DatePlusDurationFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.ADD_DURATION);
  }

  @Override
  public Operand<?> visitDateMinusDurationFunction(final DateMinusDurationFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.SUBTRACT_DURATION);
  }

  @Override
  public Operand<?> visitCurrentDateFunction(final CurrentDateFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.CURRENT_DATE);
  }

  @Override
  public Operand<?> visitTimeFromStringFunction(final TimeFromStringFunctionContext ctx) {
    return this.call(ctx, ConversionFunctions.TO_TIME);
  }

  @Override
  public Operand<?> visitCurrentTimeFunction(final CurrentTimeFunctionContext ctx) {
    return this.call(ctx, DateTimeFunctions.CURRENT_TIME);
  }

  @Override
  public Operand<?> visitDayTimeDurationFromStringFunction(
      final DayTimeDurationFromStringFunctionContext ctx) {
    return this.call(ctx, ConversionFunctions.TO_DAY_TIME_DURATION);
  }

  @Override
  public Operand<?> visitYearMonthDurationFromStringFunction(
      final YearMonthDurationFromStringFunctionContext ctx) {
    return this.call(ctx, ConversionFunctions.TO_YEAR_MONTH_DURATION);
  }

  // Sequence functions

  @Override
  public Operand<?> visitDistinctValuesFunction(final DistinctValuesFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.DISTINCT_VALUES);
  }

  @Override
  public Operand<?> visitGetDuplicatesFunction(final GetDuplicatesFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.GET_DUPLICATES);
  }

  @Override
  public Operand<?> visitUnionFunction(final UnionFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.VALUE_UNION);
  }

  @Override
  public Operand<?> visitIntersectFunction(final IntersectFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.VALUE_INTERSECT);
  }

  @Override
  public Operand<?> visitExceptFunction(final ExceptFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.VALUE_EXCEPT);
  }

  @Override
  public Operand<?> visitSortFunction(final SortFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.SORT);
  }

  @Override
  public Operand<?> visitReverseFunction(final ReverseFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.REVERSE);
  }

  @Override
  public Operand<?> visitSubsequenceFunction(final SubsequenceFunctionContext ctx) {
    return this.call(ctx, SequenceFunctions.SUBSEQUENCE);
  }

  @Override
  public Operand<?> visitSplitFunction(final SplitFunctionContext ctx) {
    return this.call(ctx, StringFunctions.SPLIT);
  }

  // #endregion Built-in functions --------------------------------------------

  // #region Built-ins and operands -------------------------------------------

  /**
   * Translates a call of a built-in function with {@link #builtIn}: its operands are the
   * expressions of its grammar label, in order, read as values, and an argument of the wrong
   * primitive cannot be converted. A type error names the function as the call writes it, which
   * may be another name of the built-in, as {@code string} is of {@code text}.
   *
   * @param call the call, which starts with the name of the function
   * @param function the built-in called
   * @return the result of the call
   */
  private Operand<?> call(final ParserRuleContext call, final BuiltIn function) {
    final List<Operand<?>> operands = this.operandsOf(call);
    return this.builtIn(call, function,
        Construct.function(call.getStart().getText(), operands.size()), operands, Operand::value,
        TypeMismatchException::cannotConvert);
  }

  /**
   * Translates a condition written as an operator after its one operand, such as {@code like} or
   * {@code is empty}, with {@link #builtIn}: its operand is read as a value, and an operand of the
   * wrong primitive cannot be converted.
   *
   * @param condition the condition
   * @param operator the operator
   * @return the result of the condition
   */
  private Operand<?> condition(final ParserRuleContext condition, final BuiltIn operator) {
    return this.builtIn(condition, operator, Construct.operator(operator.getName(), 1),
        this.operandsOf(condition), Operand::value, TypeMismatchException::cannotConvert);
  }

  /**
   * Translates a call of a built-in that reads its operands as paths, with {@link #builtIn}: an
   * argument of the wrong primitive cannot be converted.
   *
   * @param call the call
   * @param builtIn the built-in called
   * @param construct the call, as a type error names it
   * @param operands the operands of the call
   * @return the result of the call
   */
  private Operand<?> pathCall(final ParserRuleContext call, final BuiltIn builtIn,
      final Construct construct, final List<Operand<?>> operands) {
    return this.builtIn(call, builtIn, construct, operands, Operand::path,
        TypeMismatchException::cannotConvert);
  }

  /**
   * Translates an infix operator with {@link #builtIn}: operands of the wrong primitive are
   * incompatible operands of the operator.
   *
   * @param operation the operation, whose two expressions are its operands
   * @param operator the operator
   * @return the result of the operation
   */
  private Operand<?> operation(final ParserRuleContext operation, final BuiltIn operator) {
    return this.builtIn(operation, operator, Construct.operator(operator.getName(), 2),
        this.operandsOf(operation), Operand::value, TypeMismatchException::incompatibleOperands);
  }

  private List<Operand<?>> operandsOf(final ParserRuleContext call) {
    final List<ExpressionContext> expressions = call.getRuleContexts(ExpressionContext.class);
    final List<Operand<?>> operands = new ArrayList<>(expressions.size());
    for (final ExpressionContext expression : expressions) {
      operands.add(this.visit(expression));
    }
    return operands;
  }

  /**
   * Resolves a built-in against the arguments its operands give, and composes the result.
   *
   * <p>An argument that no overload takes is reported by the reference the operand is written as,
   * where the reference explains the mistake, such as a field that may repeat where a single value
   * is required; any other mismatch gets the error the kind of call gives.</p>
   *
   * @param call the call
   * @param builtIn the built-in called
   * @param construct the call, as a type error names it
   * @param operands the operands of the call
   * @param reading how the call reads each operand: as a value, or as a path
   * @param typeError the error of a mismatch that the operand's origin does not report
   * @return the result of the call
   */
  private Operand<?> builtIn(final ParserRuleContext call, final BuiltIn builtIn,
      final Construct construct, final List<Operand<?>> operands,
      final Function<Operand<?>, TypedExpression> reading,
      final BiFunction<ParserRuleContext, Diagnosis, TypeMismatchException> typeError) {
    final List<TypedExpression> arguments =
        operands.stream().map(reading).collect(Collectors.toList());
    if (builtIn.isTemplateOnly() && !this.translatesTemplateOnlyBuiltIns()) {
      throw InvalidUsageException.templateOnlyFunction(call, builtIn.getName());
    }
    final Resolution resolution = builtIn.resolve(arguments);
    if (!resolution.isResolved()) {
      final Mismatch mismatch = resolution.getMismatch();
      final Rejection rejection = mismatch.getRejection();
      final Diagnosis diagnosis = mismatch.diagnose(construct);
      throw operands.get(rejection.getPosition()).errorOf(rejection.getKind(), diagnosis)
          .orElseGet(() -> typeError.apply(call, diagnosis));
    }
    return this.operand(resolution.compose(this.script));
  }

  /**
   * Tells whether the visitor translates the built-ins that only templates may call, such as the
   * formatting functions. An expression alone has no template, so it does not.
   *
   * @return {@code false}
   */
  protected boolean translatesTemplateOnlyBuiltIns() {
    return false;
  }

  /**
   * @param type the declared type of a variable, a parameter or a function
   * @return the class of its values
   */
  static Class<? extends TypedExpression> typeOf(final TypeDeclarationContext type) {
    final Class<? extends EfxDataType.Primitive> primitive = primitiveOf(type.primitiveType());
    return type.Star() == null
        ? ScalarExpression.fromEfxDataType.get(primitive)
        : SequenceExpression.fromEfxDataType.get(primitive);
  }

  private static Class<? extends EfxDataType.Primitive> primitiveOf(
      final PrimitiveTypeContext primitive) {
    return EfxTypeTokenLookup.toJavaType(primitive.getText());
  }

  /**
   * Gives the class of the single values of an operand: the one {@code T} is bound to by the
   * operand.
   *
   * @param operand the operand
   * @return the scalar class of the operand's primitive
   */
  private Class<? extends ScalarExpression> scalarTypeOf(final TypedExpression operand) {
    return this.bindingOf(ParameterType.T, operand).getExpressionType()
        .asSubclass(ScalarExpression.class);
  }

  /**
   * Gives the class of the sequences of an operand: the one {@code T*} is bound to by the operand.
   *
   * @param operand the operand
   * @return the sequence class of the operand's primitive
   */
  private Class<? extends SequenceExpression> sequenceTypeOf(final TypedExpression operand) {
    return this.bindingOf(ParameterType.T_SEQUENCE, operand).getExpressionType()
        .asSubclass(SequenceExpression.class);
  }

  /**
   * Gives the type a type variable is bound to by an operand. Each construct rejects a node, which
   * binds no type variable, before it asks for the type of its values.
   *
   * @throws IllegalStateException if the operand is a node
   */
  private ParameterType bindingOf(final ParameterType typeVariable,
      final TypedExpression operand) {
    return typeVariable.boundBy(operand)
        .orElseThrow(() -> new IllegalStateException("A node has no value: " + operand));
  }

  // #endregion Built-ins and operands ----------------------------------------
}
