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
package eu.europa.ted.efx.exceptions;

import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.types.EfxTypeTokenLookup;

/**
 * What a type error says: which construct was given operands of which types, which of them does
 * not fit, and what that operand must be instead.
 *
 * <p>Every type error starts with the same two sentences, whichever part of the translator finds
 * it: "Operator '==' cannot take 'text' and 'number'. Its right operand must be 'text'." Types are
 * named as a declaration writes them, with a star for a sequence, and a node is 'node'.</p>
 */
public final class Diagnosis {

  private final Construct construct;
  private final List<TypedExpression> operands;
  private final int position;
  private final List<String> requirements;

  /**
   * @param construct the construct that rejects an operand
   * @param operands all its operands, in order, as it read them
   * @param position the position of the operand it rejects
   * @param requirements what that position takes, each as a message says it: a type in quotes,
   *        such as 'text', or a phrase, such as "a field reference"
   */
  public Diagnosis(final Construct construct, final List<? extends TypedExpression> operands,
      final int position, final List<String> requirements) {
    this.construct = construct;
    this.operands = List.copyOf(operands);
    this.position = position;
    this.requirements = List.copyOf(requirements);
  }

  /**
   * @return the two sentences: what the construct was given, and what the operand at fault must
   *         be
   */
  public String describe() {
    return String.format("%s cannot take %s. Its %s must be %s.", this.construct.getName(),
        joined(this.operands.stream().map(Diagnosis::typeOf).collect(Collectors.toList()), "and"),
        this.construct.roleOf(this.position), joined(this.requirements, "or"));
  }

  private static String typeOf(final TypedExpression operand) {
    return "'" + EfxTypeTokenLookup.nameOf(operand.getDataType()) + "'";
  }

  /**
   * Joins words as a sentence lists them: "a", "a and b", "a, b and c".
   */
  private static String joined(final List<String> words, final String conjunction) {
    final int last = words.size() - 1;
    return last == 0 ? words.get(0)
        : String.join(", ", words.subList(0, last)) + " " + conjunction + " " + words.get(last);
  }
}
