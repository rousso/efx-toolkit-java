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

import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;

import eu.europa.ted.efx.exceptions.Construct;
import eu.europa.ted.efx.exceptions.Diagnosis;
import eu.europa.ted.efx.exceptions.InvalidIdentifierException;
import eu.europa.ted.efx.exceptions.TypeMismatchException;
import eu.europa.ted.efx.model.Context;
import eu.europa.ted.efx.model.Context.FieldContext;
import eu.europa.ted.efx.model.Context.NodeContext;
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.signatures.Mismatch.Rejection;
import eu.europa.ted.efx.model.types.FieldTypes;
import eu.europa.ted.efx.sdk2.EfxParser.ExpressionContext;

/**
 * What an operand is written as: a field, a node, a value read from a field, a variable, a context
 * variable, a function invocation, a dictionary or a lookup in one, or anything else, such as a
 * literal, a list or the result of an operator.
 *
 * <p>Each kind answers, for the translated expression of the operand, the questions its parent
 * asks: what its value is, what context it names for the reference after {@code ::}, what a
 * predicate or an index after it means, whether a step that reads a field may follow it, and which
 * error explains why a construct rejects it: a sequence where a single value is required, or a
 * node, which has no value, where a value is. The defaults are those of an operand written as
 * anything but a reference.</p>
 *
 * @param <E> the class of the expressions of this kind: a path for a field or a node
 */
abstract class Origin<E extends TypedExpression> {

  /**
   * Reads what a step after a field gives, such as its attribute or its raw value.
   */
  interface FieldReader {

    /**
     * @param field the path of the field
     * @param origin the field
     * @return what the step gives
     */
    Operand<?> read(PathExpression field, FieldOrigin origin);
  }

  /**
   * What a step that only a field has takes, as a type error says it.
   */
  private static final String FIELD_REFERENCE = "a field reference";

  protected final EfxExpressionVisitor translator;

  protected Origin(final EfxExpressionVisitor translator) {
    this.translator = translator;
  }

  /**
   * @param expression the translated operand
   * @return its value, where an operator, a built-in, a declaration or a clause takes it; for an
   *         operand that has no value, such as a node, its path, which the construct rejects
   */
  TypedExpression valueOf(final E expression) {
    return expression;
  }

  /**
   * @param expression the translated operand
   * @return the operand as a path, where {@code is present} takes it
   */
  TypedExpression pathOf(final E expression) {
    return expression;
  }

  /**
   * @param expression the translated operand
   * @return the context the reference after {@code ::} is read in, if the operand names one
   */
  Optional<Context> contextOf(final E expression) {
    return Optional.empty();
  }

  /**
   * Translates a predicate after the operand. A value has no elements that the condition could be
   * read from, so a predicate after it is rejected.
   *
   * @param expression the translated operand
   * @param condition the condition of the predicate
   * @return the operand the predicate selects
   * @throws TypeMismatchException unless the operand is a field or a node
   */
  Operand<?> filteredBy(final E expression, final ExpressionContext condition) {
    throw this.translator.predicateOnValue(new Operand<>(expression, this), condition);
  }

  /**
   * Translates an index after the operand: the value at a position in the sequence of its values.
   * A single value is a sequence of one.
   *
   * @param expression the translated operand
   * @param position the position the index selects
   * @return the value at that position
   */
  Operand<?> indexedBy(final E expression, final ExpressionContext position) {
    return this.translator.positionInValue(new Operand<>(expression, this), position);
  }

  /**
   * Translates a step that only a field has, such as an attribute or the raw value, after the
   * operand.
   *
   * @param expression the translated operand
   * @param step the step
   * @param construct the step as a type error names it, such as "Operator ':rawValue'"
   * @param reader what the step reads from the field
   * @return what the step gives
   * @throws TypeMismatchException if the operand is not a field
   */
  Operand<?> readFromField(final E expression, final ParserRuleContext step,
      final Construct construct, final FieldReader reader) {
    throw TypeMismatchException.cannotConvert(step,
        new Diagnosis(construct, List.of(expression), 0, List.of(FIELD_REFERENCE)));
  }

  /**
   * @return the origin of the operand's value, as parentheses give it: no longer a reference, but
   *         with the error of the reference
   */
  Origin<TypedExpression> asValue() {
    return new ValueOrigin(this.translator, this);
  }

  /**
   * Gives the error of the reference the operand is written as, where the reference explains why
   * a construct rejects the operand.
   *
   * @param kind what is wrong with the operand
   * @param diagnosis what the construct was given, and what it requires
   * @return the error of the reference, or nothing if the construct gives its own
   */
  final Optional<TypeMismatchException> errorOf(final Rejection.Kind kind,
      final Diagnosis diagnosis) {
    switch (kind) {
      case CARDINALITY:
        return this.sequenceError(diagnosis);
      case PATH:
        return this.noValueError(diagnosis);
      default:
        return Optional.empty();
    }
  }

  /**
   * @param diagnosis what the construct taking the operand was given, and what it requires
   * @return the error where the operand gives a sequence and a single value is required, if the
   *         operand is written as a reference that it names
   */
  Optional<TypeMismatchException> sequenceError(final Diagnosis diagnosis) {
    return Optional.empty();
  }

  /**
   * @param diagnosis what the construct taking the operand was given, and what it requires
   * @return the error where the operand has no value and a value is required, if the operand is
   *         written as a reference that it names
   */
  Optional<TypeMismatchException> noValueError(final Diagnosis diagnosis) {
    return Optional.empty();
  }

  // #region Values -----------------------------------------------------------

  /**
   * An operand written as anything but a reference: a literal, a list, the result of an operator
   * or of a built-in.
   */
  static final class None extends Origin<TypedExpression> {

    None(final EfxExpressionVisitor translator) {
      super(translator);
    }
  }

  /**
   * The value of an operand that is no longer the reference it is read from, such as a field in
   * parentheses or the attribute of a field. It keeps the error of the reference.
   */
  static final class ValueOrigin extends Origin<TypedExpression> {

    private final Origin<?> reference;

    ValueOrigin(final EfxExpressionVisitor translator, final Origin<?> reference) {
      super(translator);
      this.reference = reference;
    }

    @Override
    Optional<TypeMismatchException> sequenceError(final Diagnosis diagnosis) {
      return this.reference.sequenceError(diagnosis);
    }

    @Override
    Optional<TypeMismatchException> noValueError(final Diagnosis diagnosis) {
      return this.reference.noValueError(diagnosis);
    }
  }

  // #endregion Values --------------------------------------------------------

  // #region Fields and nodes -------------------------------------------------

  /**
   * A field or a node: a path, whose elements a predicate after it filters and an index after it
   * selects one of.
   */
  abstract static class PathOrigin extends Origin<PathExpression> {

    protected PathOrigin(final EfxExpressionVisitor translator) {
      super(translator);
    }

    @Override
    Operand<?> filteredBy(final PathExpression path, final ExpressionContext condition) {
      return this.translator.filtered(path, this, condition);
    }

    @Override
    Operand<?> indexedBy(final PathExpression path, final ExpressionContext position) {
      return this.translator.positionInPath(path, this, position);
    }

    /**
     * Pushes the context that the condition of a predicate after the path is read in: the field
     * or the node, as each element it selects.
     */
    abstract void pushElementContext();

    /**
     * @param path the path
     * @param predicate the condition its elements must meet
     * @return the path to the elements that meet it
     */
    abstract PathExpression filter(PathExpression path, BooleanExpression predicate);
  }

  /**
   * A field. Its value is the value step on its path. It records the context its error names.
   */
  static final class FieldOrigin extends PathOrigin {

    private final ParserRuleContext reference;
    private final String fieldId;
    private final String contextSymbol;

    /**
     * @param translator the visitor
     * @param reference the reference to the field
     * @param fieldId the field
     * @param contextSymbol the context its error names: the context of the expression the field
     *        is written in, also after {@code ::}, or {@code null} for an absolute reference
     */
    FieldOrigin(final EfxExpressionVisitor translator, final ParserRuleContext reference,
        final String fieldId, final String contextSymbol) {
      super(translator);
      this.reference = reference;
      this.fieldId = fieldId;
      this.contextSymbol = contextSymbol;
    }

    @Override
    TypedExpression valueOf(final PathExpression path) {
      return this.translator.fieldValueOf(this.fieldId, path);
    }

    @Override
    Optional<Context> contextOf(final PathExpression path) {
      return Optional.of(new FieldContext(this.fieldId,
          this.translator.symbols().getAbsolutePathOfField(this.fieldId), path));
    }

    @Override
    Operand<?> readFromField(final PathExpression path, final ParserRuleContext step,
        final Construct construct, final FieldReader reader) {
      return reader.read(path, this);
    }

    @Override
    void pushElementContext() {
      this.translator.efxContext().pushFieldContext(this.fieldId);
    }

    @Override
    PathExpression filter(final PathExpression path, final BooleanExpression predicate) {
      return this.translator.script().composeFieldReferenceWithPredicate(path, predicate);
    }

    @Override
    Optional<TypeMismatchException> sequenceError(final Diagnosis diagnosis) {
      if (FieldTypes.TEXT_MULTILINGUAL.getName()
          .equals(this.translator.symbols().getTypeOfField(this.fieldId))) {
        return Optional.of(
            TypeMismatchException.fieldIsMultilingual(this.reference, diagnosis, this.fieldId));
      }
      return Optional.of(TypeMismatchException.fieldMayRepeat(this.reference, diagnosis,
          this.fieldId, this.contextSymbol));
    }
  }

  /**
   * A node. It has no value: where a value is required, its path is given as it is, and the
   * built-in, the composite or the translation that requires the value rejects it.
   */
  static final class NodeOrigin extends PathOrigin {

    private final String nodeId;

    NodeOrigin(final EfxExpressionVisitor translator, final String nodeId) {
      super(translator);
      this.nodeId = nodeId;
    }

    @Override
    Optional<Context> contextOf(final PathExpression path) {
      return Optional.of(new NodeContext(this.nodeId,
          this.translator.symbols().getAbsolutePathOfNode(this.nodeId), path));
    }

    @Override
    void pushElementContext() {
      this.translator.efxContext().pushNodeContext(this.nodeId);
    }

    @Override
    PathExpression filter(final PathExpression path, final BooleanExpression predicate) {
      return this.translator.script().composeNodeReferenceWithPredicate(path, predicate);
    }
  }

  // #endregion Fields and nodes ----------------------------------------------

  // #region Identifiers ------------------------------------------------------

  /**
   * A declared identifier that gives a value: a function invocation, a variable or a parameter, or
   * a context variable.
   *
   * @param <E> the class of the reference to the identifier: a path for a context variable
   */
  static class IdentifierOrigin<E extends TypedExpression> extends Origin<E> {

    protected final ParserRuleContext reference;
    protected final String name;

    IdentifierOrigin(final EfxExpressionVisitor translator, final ParserRuleContext reference,
        final String name) {
      super(translator);
      this.reference = reference;
      this.name = name;
    }

    @Override
    Optional<TypeMismatchException> sequenceError(final Diagnosis diagnosis) {
      return Optional.of(
          TypeMismatchException.identifierIsSequence(this.reference, diagnosis, this.name));
    }
  }

  /**
   * A variable or a parameter that holds a value. It names no context.
   */
  static final class VariableOrigin extends IdentifierOrigin<TypedExpression> {

    VariableOrigin(final EfxExpressionVisitor translator, final ParserRuleContext reference,
        final String name) {
      super(translator, reference, name);
    }

    @Override
    Optional<Context> contextOf(final TypedExpression expression) {
      throw InvalidIdentifierException.notAContextVariable(this.reference, this.name);
    }
  }

  /**
   * A context variable: it holds one element of the field or the node it iterates, so it names
   * that field or node as a context. A field context variable is a value of its field. A node
   * context variable has no value: where a value is required, its path is given as it is, the
   * construct that requires the value rejects it, and the error says that the variable holds a
   * node. Where a node is taken, as {@code count} takes one, the variable gives its node.
   */
  static final class ContextVariableOrigin extends IdentifierOrigin<PathExpression> {

    private final Context context;

    ContextVariableOrigin(final EfxExpressionVisitor translator,
        final ParserRuleContext reference, final String name, final Context context) {
      super(translator, reference, name);
      this.context = context;
    }

    /**
     * The value of a field context variable is the value of its field, read from the element it
     * holds. The element of an attribute field is its attribute, which is its own value. A node
     * context variable gives its node.
     */
    @Override
    TypedExpression valueOf(final PathExpression variable) {
      final String symbol = this.context.symbol();
      if (this.context.isNodeContext() || this.translator.symbols().isAttributeField(symbol)) {
        return variable;
      }
      return this.translator.script().composeFieldValueReference(variable);
    }

    @Override
    Optional<TypeMismatchException> noValueError(final Diagnosis diagnosis) {
      if (this.context.isNodeContext()) {
        return Optional.of(TypeMismatchException.nodeContextUsedAsValue(this.reference, diagnosis,
            this.name, this.context.symbol()));
      }
      return Optional.empty();
    }

    /**
     * The reference after {@code ::} is read from the element the variable holds.
     */
    @Override
    Optional<Context> contextOf(final PathExpression variable) {
      final String symbol = this.context.symbol();
      if (this.context.isNodeContext()) {
        return Optional.of(new NodeContext(symbol,
            this.translator.symbols().getAbsolutePathOfNode(symbol), variable));
      }
      return Optional.of(new FieldContext(symbol,
          this.translator.symbols().getAbsolutePathOfField(symbol), variable));
    }
  }

  /**
   * A name that is not declared as a variable, a parameter or a context variable. It is not a
   * value: it may only name a dictionary, which a key in brackets after it is looked up in. An
   * index after it reads its value, and is rejected.
   */
  static final class DictionaryNameOrigin extends Origin<TypedExpression> {

    private final ParserRuleContext reference;
    private final String name;

    DictionaryNameOrigin(final EfxExpressionVisitor translator, final ParserRuleContext reference,
        final String name) {
      super(translator);
      this.reference = reference;
      this.name = name;
    }

    @Override
    TypedExpression valueOf(final TypedExpression expression) {
      throw InvalidIdentifierException.undeclaredIdentifier(this.reference, this.name);
    }

    @Override
    TypedExpression pathOf(final TypedExpression expression) {
      throw InvalidIdentifierException.undeclaredIdentifier(this.reference, this.name);
    }

    @Override
    Optional<Context> contextOf(final TypedExpression expression) {
      throw InvalidIdentifierException.notAContextVariable(this.reference, this.name);
    }

    @Override
    Operand<?> filteredBy(final TypedExpression expression, final ExpressionContext key) {
      return this.translator.lookUp(this.name, this.reference, key);
    }

    @Override
    Operand<?> readFromField(final TypedExpression expression, final ParserRuleContext step,
        final Construct construct, final FieldReader reader) {
      throw InvalidIdentifierException.undeclaredIdentifier(this.reference, this.name);
    }
  }

  /**
   * A lookup in a dictionary, which gives the sequence of the values the key indexes.
   */
  static final class DictionaryOrigin extends Origin<TypedExpression> {

    private final ParserRuleContext reference;
    private final String name;

    DictionaryOrigin(final EfxExpressionVisitor translator, final ParserRuleContext reference,
        final String name) {
      super(translator);
      this.reference = reference;
      this.name = name;
    }

    @Override
    Optional<TypeMismatchException> sequenceError(final Diagnosis diagnosis) {
      return Optional.of(
          TypeMismatchException.dictionaryIsSequence(this.reference, diagnosis, this.name));
    }
  }

  // #endregion Identifiers ---------------------------------------------------
}
