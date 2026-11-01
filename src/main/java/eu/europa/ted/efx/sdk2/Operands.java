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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;

import eu.europa.ted.efx.exceptions.Construct;
import eu.europa.ted.efx.exceptions.Diagnosis;
import eu.europa.ted.efx.exceptions.TypeMismatchException;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.signatures.ParameterType;

/**
 * The operands of a construct that checks them itself rather than through the catalogue of
 * built-ins: a composite, such as a list or a conditional, a clause, such as the condition of a
 * rule, or the parts of a label block. It keeps what each operand is written as, and the value the
 * construct reads from it.
 *
 * <p>It converts the value at a position to what the position takes, and rejects a value that does
 * not fit with the error of the construct: the error of the reference the operand is written as,
 * where the reference explains the mistake, and otherwise a value that cannot be converted.</p>
 */
final class Operands {

  private final Construct construct;
  private final List<Operand<?>> operands;
  private final List<TypedExpression> values;

  /**
   * @param construct the construct, as a type error names it
   * @param operands its operands, in order
   * @param values the value the construct reads from each operand, in the same order
   */
  Operands(final Construct construct, final List<? extends Operand<?>> operands,
      final List<? extends TypedExpression> values) {
    this.construct = construct;
    this.operands = List.copyOf(operands);
    this.values = List.copyOf(values);
  }

  /**
   * Converts the value at a position to the class the position requires. A single value where a
   * sequence is required is promoted to one.
   *
   * @param <T> the class required
   * @param position the position of the operand
   * @param type the class required
   * @param where where the error is reported
   * @return the value, converted to that class
   * @throws TypeMismatchException if the value does not convert to that class
   */
  <T extends TypedExpression> T converted(final int position, final Class<T> type,
      final ParserRuleContext where) {
    final ParameterType required = ParameterType.of(type);
    final TypedExpression value = this.values.get(position);
    if (!required.accepts(value)) {
      throw this.rejected(position, List.of(required), where);
    }
    return type.cast(required.convert(value));
  }

  /**
   * Gives the value at a position that takes a value of any type. A node has no value, so it is
   * rejected.
   *
   * @param position the position of the operand
   * @param where where the error is reported
   * @return the value
   * @throws TypeMismatchException if the operand is a node
   */
  TypedExpression valueAt(final int position, final ParserRuleContext where) {
    final TypedExpression value = this.values.get(position);
    if (ParameterType.T.boundBy(value).isEmpty()) {
      throw this.rejected(position, List.of(ParameterType.T), where);
    }
    return value;
  }

  /**
   * Gives the error of a value that fits none of the types its position takes. What is wrong with
   * the value is what is least wrong with it for any of those types: its cardinality before its
   * primitive, and its primitive before the path or the value it is not.
   *
   * @param position the position of the operand
   * @param takes the types the position takes
   * @param where where the error is reported, unless the reference the operand is written as
   *        explains it
   * @return the error
   */
  TypeMismatchException rejected(final int position, final List<ParameterType> takes,
      final ParserRuleContext where) {
    final TypedExpression value = this.values.get(position);
    final Diagnosis diagnosis = new Diagnosis(this.construct, this.values, position,
        takes.stream().map(ParameterType::describe).collect(Collectors.toList()));
    return takes.stream()
        .map(type -> type.rejectionKindOf(value))
        .min(Comparator.naturalOrder())
        .flatMap(kind -> this.operands.get(position).errorOf(kind, diagnosis))
        .orElseGet(() -> TypeMismatchException.cannotConvert(where, diagnosis));
  }
}
