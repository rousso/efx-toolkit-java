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
package eu.europa.ted.efx.model.builtins;

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.sequence.SequenceExpression;
import eu.europa.ted.efx.model.signatures.ParsedArguments;

/**
 * Translates a call of an {@link Overload}: it calls the {@link ScriptGenerator} method of the
 * overload with the converted {@link ParsedArguments}.
 *
 * <p>Most composers are a lambda. A generic method of {@link ScriptGenerator}, such as
 * {@link ScriptGenerator#composeUnionFunction}, cannot be called from a lambda for an unknown
 * sequence class, so each of its four shapes has a generic functional interface below, implemented
 * by a method reference, and a factory that applies it with the sequence class {@code T*} is bound
 * to.</p>
 */
@FunctionalInterface
public interface Composer {

  /**
   * Translates a call.
   *
   * @param script the script generator of the target language
   * @param arguments the converted arguments of the call
   * @return the translated call
   */
  TypedExpression compose(ScriptGenerator script, ParsedArguments<?> arguments);

  /**
   * Composes a generic function of one sequence, such as {@code sort}.
   *
   * @param function the script generator method, as a method reference
   * @return a composer that applies it to the sequence at position 0
   */
  static Composer ofSequence(final SequenceFunction function) {
    return (script, arguments) -> applySequence(function, script, arguments,
        arguments.sequenceTypeAt(0));
  }

  /**
   * Composes a generic function of two sequences of the same class, such as {@code value-union}.
   *
   * @param function the script generator method, as a method reference
   * @return a composer that applies it to the sequences at positions 0 and 1
   */
  static Composer ofSequencePair(final SequencePairFunction function) {
    return (script, arguments) -> applySequencePair(function, script, arguments,
        arguments.sequenceTypeAt(0));
  }

  /**
   * Composes {@code subsequence} with a start.
   *
   * @param function the script generator method, as a method reference
   * @return a composer that applies it to the sequence at position 0 and the start at position 1
   */
  static Composer ofSubsequence(final SubsequenceFunction function) {
    return (script, arguments) -> applySubsequence(function, script, arguments,
        arguments.sequenceTypeAt(0));
  }

  /**
   * Composes {@code subsequence} with a start and a length.
   *
   * @param function the script generator method, as a method reference
   * @return a composer that applies it to the sequence at position 0, the start at position 1 and
   *         the length at position 2
   */
  static Composer ofSubsequenceWithLength(final SubsequenceWithLengthFunction function) {
    return (script, arguments) -> applySubsequenceWithLength(function, script, arguments,
        arguments.sequenceTypeAt(0));
  }

  private static <T extends SequenceExpression> T applySequence(final SequenceFunction function,
      final ScriptGenerator script, final ParsedArguments<?> arguments, final Class<T> type) {
    return function.apply(script, arguments.at(0, type), type);
  }

  private static <T extends SequenceExpression> T applySequencePair(
      final SequencePairFunction function, final ScriptGenerator script,
      final ParsedArguments<?> arguments, final Class<T> type) {
    return function.apply(script, arguments.at(0, type), arguments.at(1, type), type);
  }

  private static <T extends SequenceExpression> T applySubsequence(
      final SubsequenceFunction function, final ScriptGenerator script,
      final ParsedArguments<?> arguments, final Class<T> type) {
    return function.apply(script, arguments.at(0, type), arguments.numericAt(1), type);
  }

  private static <T extends SequenceExpression> T applySubsequenceWithLength(
      final SubsequenceWithLengthFunction function, final ScriptGenerator script,
      final ParsedArguments<?> arguments, final Class<T> type) {
    return function.apply(script, arguments.at(0, type), arguments.numericAt(1),
        arguments.numericAt(2), type);
  }

  /**
   * The shape of a generic {@link ScriptGenerator} method of one sequence, such as
   * {@link ScriptGenerator#composeSortFunction}.
   */
  @FunctionalInterface
  interface SequenceFunction {

    /**
     * @param <T> the sequence class
     * @param script the script generator
     * @param list the sequence
     * @param type the sequence class
     * @return the translated call
     */
    <T extends SequenceExpression> T apply(ScriptGenerator script, T list, Class<T> type);
  }

  /**
   * The shape of a generic {@link ScriptGenerator} method of two sequences, such as
   * {@link ScriptGenerator#composeUnionFunction}.
   */
  @FunctionalInterface
  interface SequencePairFunction {

    /**
     * @param <T> the sequence class
     * @param script the script generator
     * @param one the first sequence
     * @param two the second sequence
     * @param type the sequence class
     * @return the translated call
     */
    <T extends SequenceExpression> T apply(ScriptGenerator script, T one, T two, Class<T> type);
  }

  /**
   * The shape of {@link ScriptGenerator#composeSubsequenceFunction} with a start.
   */
  @FunctionalInterface
  interface SubsequenceFunction {

    /**
     * @param <T> the sequence class
     * @param script the script generator
     * @param list the sequence
     * @param start the position of the first item kept
     * @param type the sequence class
     * @return the translated call
     */
    <T extends SequenceExpression> T apply(ScriptGenerator script, T list, NumericExpression start,
        Class<T> type);
  }

  /**
   * The shape of {@link ScriptGenerator#composeSubsequenceFunction} with a start and a length.
   */
  @FunctionalInterface
  interface SubsequenceWithLengthFunction {

    /**
     * @param <T> the sequence class
     * @param script the script generator
     * @param list the sequence
     * @param start the position of the first item kept
     * @param length the number of items kept
     * @param type the sequence class
     * @return the translated call
     */
    <T extends SequenceExpression> T apply(ScriptGenerator script, T list, NumericExpression start,
        NumericExpression length, Class<T> type);
  }
}
