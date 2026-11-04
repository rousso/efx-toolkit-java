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

import static eu.europa.ted.efx.model.builtins.Overload.overload;
import static eu.europa.ted.efx.model.signatures.BuiltInParameter.parameter;
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR;
import static eu.europa.ted.efx.model.signatures.ParameterType.PATH;
import static eu.europa.ted.efx.model.signatures.ParameterType.T;
import static eu.europa.ted.efx.model.signatures.ParameterType.T_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.TypedExpression;
import eu.europa.ted.efx.model.expressions.scalar.BooleanExpression;
import eu.europa.ted.efx.model.expressions.scalar.DateExpression;
import eu.europa.ted.efx.model.expressions.scalar.DurationExpression;
import eu.europa.ted.efx.model.expressions.scalar.NumericExpression;
import eu.europa.ted.efx.model.expressions.scalar.ScalarExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringExpression;
import eu.europa.ted.efx.model.expressions.scalar.TimeExpression;
import eu.europa.ted.efx.model.expressions.sequence.BooleanSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DateSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DurationSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.NumericSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.TimeSequenceExpression;
import eu.europa.ted.efx.model.signatures.ParsedArguments;

/**
 * The condition operators: membership, emptiness, presence, uniqueness and duplicates, each with
 * its negated form. ({@code like} is a {@link PatternMatch}.)
 *
 * <p>A negated form is the logical negation of its positive form. {@code has duplicates} is the
 * negation of {@code has no duplicates}, which is the condition the script generator composes.</p>
 */
public enum ConditionOperators implements BuiltIn {

  IN("in",
      overload(signature(parameter("needle", T), parameter("haystack", T_SEQUENCE)).returning(INDICATOR),
          (script, args) -> script.composeContainsCondition(args.scalarAt(0), args.sequenceAt(1)))),

  NOT_IN("not in", IN),

  IS_EMPTY("is empty",
      overload(signature(parameter("sequence", T_SEQUENCE)).returning(INDICATOR),
          (script, args) -> script.composeEmptySequenceCondition(args.sequenceAt(0)))),

  IS_NOT_EMPTY("is not empty", IS_EMPTY),

  IS_PRESENT("is present",
      overload(signature(parameter("reference", PATH)).returning(INDICATOR),
          (script, args) -> script.composeExistsCondition(args.pathAt(0)))),

  IS_NOT_PRESENT("is not present", IS_PRESENT),

  IS_UNIQUE_IN("is unique in",
      overload(signature(parameter("needle", T), parameter("haystack", T_SEQUENCE))
          .returning(INDICATOR), UniqueValueCondition::compose)),

  IS_NOT_UNIQUE_IN("is not unique in", IS_UNIQUE_IN),

  HAS_NO_DUPLICATES("has no duplicates",
      overload(signature(parameter("sequence", T_SEQUENCE)).returning(INDICATOR),
          (script, args) -> script.composeIsDistinctCondition(args.sequenceAt(0)))),

  HAS_DUPLICATES("has duplicates", HAS_NO_DUPLICATES);

  private final String name;
  private final List<Overload> overloads;

  ConditionOperators(final String name, final Overload... overloads) {
    this.name = name;
    this.overloads = List.of(overloads);
  }

  /**
   * Declares the negated form of a condition.
   *
   * @param name the negated operator as written
   * @param positive the condition it negates
   */
  ConditionOperators(final String name, final ConditionOperators positive) {
    this.name = name;
    this.overloads = positive.overloads.stream().map(Overload::negated)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public List<Overload> getOverloads() {
    return this.overloads;
  }

  /**
   * The condition {@code is unique in} for each value type. The script generator composes it with
   * one method for each type, so the type of the needle picks the method.
   */
  private enum UniqueValueCondition {

    STRING(StringExpression.class, (script, args) -> script.composeUniqueValueCondition(
        args.stringAt(0), args.at(1, StringSequenceExpression.class))),

    NUMERIC(NumericExpression.class, (script, args) -> script.composeUniqueValueCondition(
        args.numericAt(0), args.at(1, NumericSequenceExpression.class))),

    BOOLEAN(BooleanExpression.class, (script, args) -> script.composeUniqueValueCondition(
        args.booleanAt(0), args.at(1, BooleanSequenceExpression.class))),

    DATE(DateExpression.class, (script, args) -> script.composeUniqueValueCondition(
        args.dateAt(0), args.at(1, DateSequenceExpression.class))),

    TIME(TimeExpression.class, (script, args) -> script.composeUniqueValueCondition(
        args.timeAt(0), args.at(1, TimeSequenceExpression.class))),

    DURATION(DurationExpression.class, (script, args) -> script.composeUniqueValueCondition(
        args.durationAt(0), args.at(1, DurationSequenceExpression.class)));

    private final Class<? extends ScalarExpression> needleType;
    private final Composer composer;

    UniqueValueCondition(final Class<? extends ScalarExpression> needleType,
        final Composer composer) {
      this.needleType = needleType;
      this.composer = composer;
    }

    /**
     * Composes the condition with the method for the type of the needle.
     *
     * @param script the script generator of the target language
     * @param arguments the needle and the haystack, converted to the type {@code T} is bound to
     * @return the condition
     */
    static TypedExpression compose(final ScriptGenerator script,
        final ParsedArguments<?> arguments) {
      final Class<? extends TypedExpression> needleType = arguments.typeAt(0);
      return Arrays.stream(values()).filter(condition -> condition.needleType.equals(needleType))
          .findFirst().orElseThrow().composer.compose(script, arguments);
    }
  }
}
