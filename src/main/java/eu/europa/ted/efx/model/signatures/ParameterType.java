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

import java.util.Optional;

import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.DurationExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.TimeExpression;
import eu.europa.ted.efx.model.expressions.sequence.BooleanSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DateSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DurationSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.MultilingualStringSequencePath;
import eu.europa.ted.efx.model.expressions.sequence.NodeSequencePath;
import eu.europa.ted.efx.model.expressions.sequence.NumericSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.TimeSequenceExpression;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * The type of a {@link Parameter}: the pattern an argument must match, and the expression class
 * the argument is converted to.
 *
 * <p>A parameter type is one of three kinds: a concrete value type such as {@link #NUMBER}, the
 * type variable {@link #T} in a cardinality, or a path such as {@link #PATH}. A signature that
 * mentions {@code T} is generic: it is bound to a value primitive before its arguments are
 * checked, and only the parameter types of a bound signature accept and convert arguments.</p>
 *
 * <p>Outside a signature, {@link #T} and {@link #T_SEQUENCE} also give the type of a value that a
 * construct takes from one of its arguments: {@link #boundBy} binds them as a signature is
 * bound.</p>
 *
 * <p>The constants are named after the type vocabulary of the autocomplete catalogue.</p>
 */
public interface ParameterType {

  /** A text, or a multilingual text read as a text. */
  ParameterType TEXT = new ConcreteParameterType(StringExpression.class);

  /** A number. */
  ParameterType NUMBER = new ConcreteParameterType(NumericExpression.class);

  /** An indicator, or a dynamic value read as an indicator. */
  ParameterType INDICATOR = new ConcreteParameterType(BooleanExpression.class);

  /** A date. */
  ParameterType DATE = new ConcreteParameterType(DateExpression.class);

  /** A time. */
  ParameterType TIME = new ConcreteParameterType(TimeExpression.class);

  /** A duration. */
  ParameterType DURATION = new ConcreteParameterType(DurationExpression.class);

  /** A sequence of texts; a single text is promoted to a sequence of one. */
  ParameterType TEXT_SEQUENCE = new ConcreteParameterType(StringSequenceExpression.class);

  /** A sequence of numbers; a single number is promoted to a sequence of one. */
  ParameterType NUMBER_SEQUENCE = new ConcreteParameterType(NumericSequenceExpression.class);

  /** A sequence of indicators; a single indicator is promoted to a sequence of one. */
  ParameterType INDICATOR_SEQUENCE = new ConcreteParameterType(BooleanSequenceExpression.class);

  /** A sequence of dates; a single date is promoted to a sequence of one. */
  ParameterType DATE_SEQUENCE = new ConcreteParameterType(DateSequenceExpression.class);

  /** A sequence of times; a single time is promoted to a sequence of one. */
  ParameterType TIME_SEQUENCE = new ConcreteParameterType(TimeSequenceExpression.class);

  /** A sequence of durations; a single duration is promoted to a sequence of one. */
  ParameterType DURATION_SEQUENCE = new ConcreteParameterType(DurationSequenceExpression.class);

  /** A single value of the value primitive {@code T} stands for. */
  ParameterType T = TypeVariableParameterType.SCALAR;

  /** A sequence of the value primitive {@code T} stands for, or a single value promoted to one. */
  ParameterType T_SEQUENCE = TypeVariableParameterType.SEQUENCE;

  /** A reference to a field or a node, taken as a path rather than as its value. */
  ParameterType PATH = new PathParameterType(PathExpression.class, "a field or node reference");

  /** A reference to a node, repeatable or not, taken as the sequence of nodes it selects. */
  ParameterType NODES = new PathParameterType(NodeSequencePath.class, "a node reference");

  /** A reference to a multilingual text field, taken as a path. */
  ParameterType MULTILINGUAL_FIELD =
      new PathParameterType(MultilingualStringSequencePath.class, "a multilingual field");

  /**
   * Gives the parameter type of a value expression class, such as the class of a parameter
   * declared in an EFX file.
   *
   * @param expressionType a scalar or sequence class of one value primitive
   * @return the parameter type that accepts what converts to that class
   */
  static ParameterType of(final Class<? extends TypedExpression> expressionType) {
    return new ConcreteParameterType(expressionType);
  }

  /**
   * Tells whether this parameter type is the type variable {@code T}, as a single value or as a
   * sequence.
   *
   * @return {@code true} for {@link #T} and {@link #T_SEQUENCE}
   */
  default boolean isTypeVariable() {
    return false;
  }

  /**
   * Gives the parameter type this one stands for once {@code T} is bound.
   *
   * @param primitive the value primitive {@code T} is bound to
   * @return the concrete type for a type variable, and this parameter type for any other
   */
  default ParameterType bind(Class<? extends EfxDataType.Primitive> primitive) {
    return this;
  }

  /**
   * Gives the parameter type this one stands for once an argument binds {@code T}, as a generic
   * signature is bound by the argument at the first position of {@code T}.
   *
   * @param argument the argument that binds {@code T}
   * @return the concrete type for a type variable, or nothing if the argument cannot bind
   *         {@code T} (a node); this parameter type for any other
   */
  default Optional<ParameterType> boundBy(TypedExpression argument) {
    return Optional.of(this);
  }

  /**
   * Gives the expression class an argument is converted to. For a type variable, whose arguments
   * are only converted once it is bound, it is the class of every value or every sequence it
   * stands for.
   *
   * @return the expression class of this parameter type
   */
  Class<? extends TypedExpression> getExpressionType();

  /**
   * Tells whether an argument converts to this parameter type: unchanged, widened from a subtype,
   * or promoted from a single value to a sequence of one.
   *
   * @param argument the translated argument
   * @return {@code true} if the argument converts to {@link #getExpressionType()}
   */
  boolean accepts(TypedExpression argument);

  /**
   * Tells what is wrong with an argument this parameter type does not accept: its cardinality
   * alone, when a single value of its primitive would convert, so that it is a sequence where a
   * single value is required; its primitive; or a path where a value is required, or a node, which
   * has no value, where one is.
   *
   * @param argument a translated argument this parameter type does not {@link #accepts accept}
   * @return the kind of the rejection
   */
  Match.Kind rejectionKindOf(TypedExpression argument);

  /**
   * Says what this parameter type takes, as a type error says it: a type in quotes, as a
   * declaration writes it, such as {@code 'text'} or {@code 'number*'}, or a phrase, such as
   * "a value" or "a field or node reference".
   *
   * @return the words for what this parameter type takes
   */
  String describe();

  /**
   * Converts an accepted argument to {@link #getExpressionType() its class}, as
   * {@link eu.europa.ted.efx.model.CallStack#pop(Class)} does on the call stack.
   *
   * @param argument an argument this parameter type accepts
   * @return the argument itself if it already has that class, otherwise its conversion
   */
  default TypedExpression convert(TypedExpression argument) {
    Class<? extends TypedExpression> type = this.getExpressionType();
    return type.isInstance(argument) ? argument : TypedExpression.from(argument, type);
  }
}
