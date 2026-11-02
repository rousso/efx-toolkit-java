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

import static eu.europa.ted.efx.model.signatures.Overload.overload;
import static eu.europa.ted.efx.model.signatures.BuiltInParameter.parameter;
import static eu.europa.ted.efx.model.signatures.ParameterType.DATE;
import static eu.europa.ted.efx.model.signatures.ParameterType.DATE_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.DURATION;
import static eu.europa.ted.efx.model.signatures.ParameterType.DURATION_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR;
import static eu.europa.ted.efx.model.signatures.ParameterType.INDICATOR_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.PATH;
import static eu.europa.ted.efx.model.signatures.ParameterType.T;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.TIME;
import static eu.europa.ted.efx.model.signatures.ParameterType.TIME_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.ParameterType.T_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.model.expressions.sequence.BooleanSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DateSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.DurationSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.NumericSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;
import eu.europa.ted.efx.model.expressions.sequence.TimeSequenceExpression;

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
      overload(signature(parameter("needle", TEXT), parameter("haystack", TEXT_SEQUENCE))
          .returning(INDICATOR),
          (script, args) -> script.composeUniqueValueCondition(args.stringAt(0),
              args.at(1, StringSequenceExpression.class))),
      overload(signature(parameter("needle", NUMBER), parameter("haystack", NUMBER_SEQUENCE))
          .returning(INDICATOR),
          (script, args) -> script.composeUniqueValueCondition(args.numericAt(0),
              args.at(1, NumericSequenceExpression.class))),
      overload(signature(parameter("needle", INDICATOR), parameter("haystack", INDICATOR_SEQUENCE))
          .returning(INDICATOR),
          (script, args) -> script.composeUniqueValueCondition(args.booleanAt(0),
              args.at(1, BooleanSequenceExpression.class))),
      overload(signature(parameter("needle", DATE), parameter("haystack", DATE_SEQUENCE))
          .returning(INDICATOR),
          (script, args) -> script.composeUniqueValueCondition(args.dateAt(0),
              args.at(1, DateSequenceExpression.class))),
      overload(signature(parameter("needle", TIME), parameter("haystack", TIME_SEQUENCE))
          .returning(INDICATOR),
          (script, args) -> script.composeUniqueValueCondition(args.timeAt(0),
              args.at(1, TimeSequenceExpression.class))),
      overload(signature(parameter("needle", DURATION), parameter("haystack", DURATION_SEQUENCE))
          .returning(INDICATOR),
          (script, args) -> script.composeUniqueValueCondition(args.durationAt(0),
              args.at(1, DurationSequenceExpression.class)))),

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
}
