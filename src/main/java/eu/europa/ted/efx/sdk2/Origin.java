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

import eu.europa.ted.efx.exceptions.InvalidIdentifierException;
import eu.europa.ted.efx.exceptions.TypeMismatchException;
import eu.europa.ted.efx.model.Context;
import eu.europa.ted.efx.model.Context.FieldContext;
import eu.europa.ted.efx.model.Context.NodeContext;
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.types.FieldTypes;
import eu.europa.ted.efx.sdk2.EfxParser.ExpressionContext;

/**
 * What an operand is written as: a field, a node, a value read from a field, a variable, a context
 * variable, a function invocation, a dictionary or a lookup in one, or anything else, such as a
 * literal, a list or the result of an operator.
 *
 * <p>Each kind answers, for the translated expression of the operand, the questions its parent
 * asks: what its value is, what context it names for the reference after {@code ::}, what a
 * bracket after it means, whether a step that reads a field may follow it, and which error to
 * give when it gives a sequence where a single value is required. The defaults are those of an
 * operand written as anything but a reference.</p>
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

  protected final EfxExpressionVisitor translator;

  protected Origin(final EfxExpressionVisitor translator) {
    this.translator = translator;
  }

  /**
   * @param expression the translated operand
   * @return its value, where an operator, a built-in, a declaration or a clause takes it
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
   * Translates a bracket after the operand. A bracket after a value is a position in the sequence
   * of its values.
   *
   * @param expression the translated operand
   * @param content the content of the bracket
   * @return the operand the bracket selects
   */
  Operand<?> indexedBy(final E expression, final ExpressionContext content) {
    return this.translator.positionInValue(new Operand<>(expression, this), content);
  }

  /**
   * Translates a step that only a field has, such as an attribute or the raw value, after the
   * operand.
   *
   * @param expression the translated operand
   * @param step the step
   * @param reader what the step reads from the field
   * @return what the step gives
   * @throws TypeMismatchException if the operand is not a field
   */
  Operand<?> readFromField(final E expression, final ParserRuleContext step,
      final FieldReader reader) {
    throw TypeMismatchException.cannotConvert(step, PathExpression.class, expression.getClass());
  }

  /**
   * @return the origin of the operand's value, as parentheses give it: no longer a reference, but
   *         with the error of the reference
   */
  Origin<TypedExpression> asValue() {
    return new ValueOrigin(this.translator, this);
  }

  /**
   * @return the error where the operand gives a sequence and a single value is required, if the
   *         operand is written as a reference that it names
   */
  Optional<TypeMismatchException> sequenceError() {
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
    Optional<TypeMismatchException> sequenceError() {
      return this.reference.sequenceError();
    }
  }

  // #endregion Values --------------------------------------------------------

  // #region Fields and nodes -------------------------------------------------

  /**
   * A field or a node: a path, whose elements a bracket after it filters or selects.
   */
  abstract static class PathOrigin extends Origin<PathExpression> {

    protected PathOrigin(final EfxExpressionVisitor translator) {
      super(translator);
    }

    @Override
    Operand<?> indexedBy(final PathExpression path, final ExpressionContext content) {
      return this.translator.positionInPath(path, this, content);
    }

    /**
     * Pushes the context that the content of a bracket after the path is read in: the field or
     * the node, as each element it selects.
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
   * A field. Its value is the value step on its path. It records the context it is read in, which
   * its error names.
   */
  static final class FieldOrigin extends PathOrigin {

    private final ParserRuleContext reference;
    private final String fieldId;
    private final String contextSymbol;

    FieldOrigin(final EfxExpressionVisitor translator, final ParserRuleContext reference,
        final String fieldId) {
      super(translator);
      this.reference = reference;
      this.fieldId = fieldId;
      this.contextSymbol = translator.efxContext().symbol();
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
        final FieldReader reader) {
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
    Optional<TypeMismatchException> sequenceError() {
      if (FieldTypes.TEXT_MULTILINGUAL.getName()
          .equals(this.translator.symbols().getTypeOfField(this.fieldId))) {
        return Optional.of(TypeMismatchException.fieldIsMultilingual(this.reference, this.fieldId));
      }
      return Optional.of(
          TypeMismatchException.fieldMayRepeat(this.reference, this.fieldId, this.contextSymbol));
    }
  }

  /**
   * A node. It has no value: where a value is required, its path is given as it is, and the
   * built-in or the composite that requires the value rejects it.
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
    Optional<TypeMismatchException> sequenceError() {
      return Optional.of(TypeMismatchException.identifierIsSequence(this.reference, this.name));
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
   * that field or node as a context. A field context variable is a value of its field; a node
   * context variable has no value.
   */
  static final class ContextVariableOrigin extends IdentifierOrigin<PathExpression> {

    private final Context context;

    ContextVariableOrigin(final EfxExpressionVisitor translator,
        final ParserRuleContext reference, final String name, final Context context) {
      super(translator, reference, name);
      this.context = context;
    }

    @Override
    TypedExpression valueOf(final PathExpression variable) {
      if (this.context.isNodeContext()) {
        throw TypeMismatchException.nodeContextUsedAsValue(this.reference, this.name,
            this.context.symbol());
      }
      return variable;
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
   * value: it may only name a dictionary, which a bracket after it looks a key up in.
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
    Operand<?> indexedBy(final TypedExpression expression, final ExpressionContext key) {
      return this.translator.lookUp(this.name, this.reference, key);
    }

    @Override
    Operand<?> readFromField(final TypedExpression expression, final ParserRuleContext step,
        final FieldReader reader) {
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
    Optional<TypeMismatchException> sequenceError() {
      return Optional.of(TypeMismatchException.dictionaryIsSequence(this.reference, this.name));
    }
  }

  // #endregion Identifiers ---------------------------------------------------
}
