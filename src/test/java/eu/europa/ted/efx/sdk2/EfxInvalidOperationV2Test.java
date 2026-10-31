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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import eu.europa.ted.efx.EfxTestsBase;
import eu.europa.ted.efx.exceptions.InvalidArgumentException;
import eu.europa.ted.efx.exceptions.ThrowingErrorListener;
import eu.europa.ted.efx.exceptions.TypeMismatchException;

/**
 * The table of operations that EFX rejects: one row per expression, grouped by the rule the
 * expression breaks.
 * <p>
 * Each row is expected to fail with a syntax error or a type error, so that a row holds whether the
 * parser or the translator rejects the expression. A syntax error is a bare
 * {@link ParseCancellationException}, as the {@link ThrowingErrorListener} raises it; a type error is
 * a {@link TypeMismatchException} or an {@link InvalidArgumentException}. Any other failure, such as
 * a name that does not resolve, would let a row pass without testing its rule.
 * <p>
 * Binary operations appear in both operand orders, and with literals as well as field references,
 * because the two kinds of operand are rejected along different paths.
 * <p>
 * Template-only functions are rejected outside templates whatever their arguments, so their rows
 * are translated inside a template, where only the arguments can be wrong.
 */
class EfxInvalidOperationV2Test extends EfxTestsBase {

  @Override
  protected String getSdkVersion() {
    return "eforms-sdk-2.0";
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidOperations")
  void testInvalidOperation_IsRejected(final String expression) {
    ParseCancellationException exception = assertThrows(ParseCancellationException.class,
        () -> translateExpressionWithContext("ND-Root", expression));
    assertTrue(isSyntaxOrTypeError(exception), exception::toString);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidTemplateOperations")
  void testInvalidTemplateOperation_IsRejected(final String expression) {
    ParseCancellationException exception = assertThrows(ParseCancellationException.class,
        () -> translateTemplate("{ND-Root} ${" + expression + "}"));
    assertTrue(isSyntaxOrTypeError(exception), exception::toString);
  }

  private static boolean isSyntaxOrTypeError(final ParseCancellationException exception) {
    return exception.getClass() == ParseCancellationException.class
        || exception instanceof TypeMismatchException
        || exception instanceof InvalidArgumentException;
  }

  static Stream<String> invalidOperations() {
    return Stream.of(
        // A comparison needs two operands of the same type
        "'a' == 1",
        "1 == 'a'",
        "BT-00-Text == BT-00-Number",
        "BT-00-Number == 'a'",
        "'a' != BT-00-Number",
        "'a' < TRUE",
        "BT-00-Indicator > 1",
        "'a' <= 2020-01-01Z",
        "BT-00-StartTime == 'a'",
        "'a' >= P1D",
        "1 == TRUE",
        "BT-00-Number < BT-00-StartDate",
        "10:00:00Z > 1",
        "1 != BT-00-Duration",
        "TRUE == 2020-01-01Z",
        "BT-00-StartTime != BT-00-Indicator",
        "BT-00-Indicator == P1D",
        "2020-01-01Z == 10:00:00Z",
        "BT-00-StartDate == BT-00-StartTime",
        "2020-01-01Z < BT-00-Duration",
        "P1D > 10:00:00Z",
        "BT-00-StartTime <= BT-00-Duration",

        // + adds two numbers, two durations, or a duration to a date
        "'a' + 'b'",
        "BT-00-Text + BT-00-Text",
        "BT-00-Text + 1",
        "'a' + 1",
        "1 + 'a'",
        "TRUE + 1",
        "BT-00-Number + BT-00-Indicator",
        "2020-01-01Z + 2020-01-02Z",
        "BT-00-StartDate + BT-00-EndDate",
        "1 + BT-00-StartDate",
        "10:00:00Z + P1D",
        "BT-00-StartTime + BT-00-Duration",
        "BT-00-StartTime + P1D",
        "1 + P1D",
        "BT-00-Duration + BT-00-Number",
        "P1D + BT-00-Number",
        "1 + 10:00:00Z",
        "2020-01-01Z + 1",

        // - subtracts two numbers, two durations, two dates, or a duration from a date
        "BT-00-Text - 1",
        "'a' - 'b'",
        "1 - BT-00-Indicator",
        "TRUE - FALSE",
        "1 - 2020-01-01Z",
        "BT-00-Number - BT-00-StartDate",
        "2020-01-01Z - 1",
        "11:00:00Z - 10:00:00Z",
        "BT-00-EndTime - BT-00-StartTime",
        "BT-00-Duration - BT-00-Number",
        "P1D - 2020-01-01Z",
        "BT-00-Duration - BT-00-StartDate",
        "TRUE - 1",
        "10:00:00Z - 1",
        "1 - 'a'",
        "1 - 10:00:00Z",
        "1 - P1D",

        // * multiplies two numbers, or a number and a duration
        "'a' * 2",
        "2 * BT-00-Text",
        "TRUE * 2",
        "BT-00-Indicator * BT-00-Number",
        "2020-01-01Z * 2",
        "2 * BT-00-StartDate",
        "10:00:00Z * 2",
        "P1D * P2D",
        "BT-00-Duration * BT-00-Duration",
        "2 * TRUE",
        "2 * 10:00:00Z",

        // / and % divide numbers only
        "'a' / 2",
        "BT-00-Number / BT-00-Text",
        "TRUE / 2",
        "2 / 2020-01-01Z",
        "BT-00-StartTime / 2",
        "P2D / 2",
        "BT-00-Duration / 2",
        "2 / P1D",
        "5 % 'a'",
        "TRUE % 2",
        "2020-01-01Z % 2",
        "5 % BT-00-StartTime",
        "BT-00-Duration % 2",
        "2020-01-01Z / 2",
        "2 / TRUE",
        "2 / 10:00:00Z",
        "'a' % 2",
        "10:00:00Z % 2",
        "2 % TRUE",
        "2 % 2020-01-01Z",
        "2 % P1D",

        // and, or and not() take booleans
        "1 and TRUE",
        "TRUE or 'a'",
        "BT-00-Text and TRUE",
        "TRUE or BT-00-Number",
        "BT-00-Text and BT-00-Indicator",
        "2020-01-01Z or TRUE",
        "P1D and BT-00-StartTime",
        "not(1)",
        "not(BT-00-Text)",
        "not(BT-00-Duration)",
        "TRUE and 2020-01-01Z",
        "10:00:00Z or TRUE",
        "TRUE or P1D",
        "not(2020-01-01Z)",
        "not(10:00:00Z)",
        "2020-01-01Z and TRUE",
        "10:00:00Z and TRUE",
        "P1D and TRUE",
        "TRUE and 1",
        "TRUE and 'a'",
        "TRUE and BT-00-StartTime",
        "TRUE and P1D",
        "1 or TRUE",
        "'a' or TRUE",
        "P1D or TRUE",
        "TRUE or 2020-01-01Z",
        "TRUE or 10:00:00Z",

        // like matches text
        "1 like '[0-9]'",
        "BT-00-Number like '[0-9]*'",
        "TRUE like 'true'",
        "BT-00-StartDate like '2020'",
        "10:00:00Z like '10'",
        "BT-00-Duration like 'P1D'",

        // in looks for a value in a sequence of the same type
        "1 in ['a', 'b']",
        "'a' in [1, 2]",
        "BT-00-Number in ['a', 'b']",
        "'a' in BT-00-Repeatable-Number",
        "BT-00-Text in BT-00-Repeatable-Number",
        "TRUE in [1, 2]",
        "2020-01-01Z not in [10:00:00Z]",
        "P1D in [1, 2]",
        "BT-00-StartTime in BT-00-Repeatable-Date",

        // is unique in looks for a value in a sequence of the same type
        "'a' is unique in [1, 2]",
        "1 is unique in BT-00-Repeatable-Text",
        "BT-00-Number is unique in ['a']",
        "BT-00-Text is unique in BT-00-Repeatable-Number",
        "2020-01-01Z is unique in [P1D]",
        "TRUE is not unique in [10:00:00Z]",

        // The condition of if ... then ... else is a boolean
        "if 1 then 'a' else 'b'",
        "if 'a' then 1 else 2",
        "if BT-00-Text then 1 else 2",
        "if BT-00-Number then 'a' else 'b'",
        "if 2020-01-01Z then TRUE else FALSE",
        "if 10:00:00Z then 1 else 2",
        "if P1D then 1 else 2",

        // The two branches of if ... then ... else have the same type
        "if TRUE then 1 else 'a'",
        "if TRUE then 'a' else BT-00-Number",
        "if TRUE then BT-00-StartDate else BT-00-Text",
        "if TRUE then 2020-01-01Z else P1D",
        "if TRUE then 10:00:00Z else BT-00-StartDate",
        "if TRUE then BT-00-Indicator else 1",
        "if TRUE then [1] else ['a']",
        "if TRUE then 1 else ['a']",

        // The body of every ... satisfies and some ... satisfies is a boolean
        "every text:$x in ['a', 'b'] satisfies $x",
        "some number:$n in [1, 2] satisfies $n + 1",
        "every text:$x in ['a'] satisfies 'a'",
        "some date:$d in [2020-01-01Z] satisfies $d",
        "some time:$t in [10:00:00Z] satisfies $t",
        "every duration:$d in [P1D] satisfies $d",

        // The values of a list have one type
        "[1, 'a']",
        "['a', TRUE]",
        "[2020-01-01Z, 10:00:00Z]",
        "[P1D, 1]",
        "[1, BT-00-Text]",
        "[BT-00-Number, BT-00-Text]",
        "[BT-00-StartDate, BT-00-StartTime]",
        "count([1, 'a'])",

        // An iterator variable has the type of the sequence it iterates over
        "for number:$n in ['a'] return $n",
        "for text:$t in [1, 2] return $t",
        "for date:$d in BT-00-Repeatable-Text return $d",
        "for indicator:$i in [1] return $i",
        "for text:$t in BT-00-Repeatable-Number return $t",
        "every time:$t in [2020-01-01Z] satisfies TRUE",
        "some duration:$d in BT-00-Repeatable-Number satisfies TRUE",

        // A sequence cannot stand where a single value is required
        "BT-00-Repeatable-Text == 'x'",
        "'x' == BT-00-Repeatable-Text",
        "['a', 'b'] == 'a'",
        "BT-00-Repeatable-Number + 1",
        "BT-00-Repeatable-Indicator and TRUE",
        "if BT-00-Repeatable-Indicator then 1 else 2",
        "BT-00-Repeatable-Text like 'a'",
        "['a', 'b'] like 'a'",
        "BT-00-Repeatable-Number in [1, 2]",
        "[1, 2] in [1, 2, 3]",
        "string-length(BT-00-Repeatable-Text)",
        "upper-case(['a'])",
        "BT-00-Text-Multilingual == 'a'",
        "['a', BT-00-Repeatable-Text]",
        "[1, [2, 3]]",
        "1 + BT-00-Repeatable-Number",
        "1 + [2]",
        "TRUE and BT-00-Repeatable-Indicator",

        // A node has no value
        "ND-SubNode == 'a'",
        "'a' == ND-Root",
        "ND-SubNode + 1",
        "ND-SubNode in ['a']",
        "string-length(ND-SubNode)",
        "ND-SubNode is empty",
        "ND-SubNode has duplicates",
        "distinct-values(ND-RepeatableNode)",
        "count-duplicates(ND-RepeatableNode)",
        "get-duplicates(ND-RepeatableNode)",
        "sort(ND-RepeatableNode)",
        "reverse(ND-RepeatableNode)",
        "subsequence(ND-RepeatableNode, 1)",

        // is present applies to a field or node reference
        "1 is present",
        "'a' is present",
        "(1 + 1) is present",
        "upper-case(BT-00-Text) is present",
        "some text:$x in ['a'] satisfies $x is present",
        "for text:$x in ['a'] return $x is present",

        // A predicate is a boolean or a number, and an index on a sequence is a number
        "BT-00-Text['x']",
        "BT-00-Repeatable-Text[2020-01-01Z]",
        "BT-00-Repeatable-Text[P1D]",
        "BT-00-Repeatable-Text[BT-00-Text]",
        "ND-SubNode['x'] is present",
        "['a', 'b']['x']",
        "['a', 'b'][TRUE]",
        "['a', 'b'][BT-00-Text]",
        "[1, 2][2020-01-01Z]",

        // A raw value is read from a field
        "ND-SubNode:rawValue",
        "'a':rawValue",

        // Each argument of a built-in function has the type its parameter declares
        "string-length(1)",
        "string-length(BT-00-Number)",
        "upper-case(TRUE)",
        "substring(1, 2)",
        "substring('abc', 'x')",
        "contains(1, 2)",
        "contains(BT-00-Text, BT-00-Number)",
        "starts-with('a', 1)",
        "concat('a', 1)",
        "concat('a', BT-00-StartDate)",
        "string-join([1, 2], ',')",
        "string-join(['a'], 1)",
        "replace('a', 1, 'b')",
        "pad-left('a', 'b', 'c')",
        "split(1, ',')",
        "empty(1)",
        "sum(['a', 'b'])",
        "sum(BT-00-Repeatable-Text)",
        "min([2020-01-01Z])",
        "average(BT-00-Repeatable-Date)",
        "absolute('a')",
        "round(TRUE)",
        "round(BT-00-Text)",
        "year('2020')",
        "year(BT-00-StartTime)",
        "day(10:00:00Z)",
        "hours(2020-01-01Z)",
        "minutes(BT-00-StartDate)",
        "years(1)",
        "days(BT-00-Number)",
        "date(1)",
        "date(BT-00-Number)",
        "time(TRUE)",
        "day-time-duration(1)",
        "year-month-duration(BT-00-Number)",
        "indicator('x')",
        "indicator(BT-00-Text)",
        "number(2020-01-01Z)",
        "number(P1D)",
        "add-duration(10:00:00Z, P1D)",
        "add-duration(2020-01-01Z, 1)",
        "add-duration(BT-00-StartDate, BT-00-Number)",
        "subtract-duration(P1D, 2020-01-01Z)",
        "index-of(['a', 'b'], 1)",
        "index-of(BT-00-Repeatable-Text, 1)",
        "sequence-equal(['a'], [1])",
        "sequence-equal(BT-00-Repeatable-Text, BT-00-Repeatable-Number)",
        "value-union(['a'], [1])",
        "value-intersect([1], [2020-01-01Z])",
        "value-except(BT-00-Repeatable-Date, BT-00-Repeatable-Time)",
        "subsequence(['a', 'b'], 'x')",
        "subsequence(['a', 'b'], 1, 'x')",
        "contains(1, 'a')",
        "starts-with(1, 'a')",
        "ends-with(1, 'a')",
        "ends-with('a', 1)",
        "max(['a'])",
        "index-of-substring(1, 'a')",
        "index-of-substring('a', 1)",
        "round-down('a')",
        "round-up(TRUE)",
        "month(10:00:00Z)",
        "seconds(2020-01-01Z)",
        "months('P1M')",
        "substring('abc', 1, 'x')",
        "substring-before(1, 'a')",
        "substring-before('a', 1)",
        "substring-after(1, 'a')",
        "substring-after('a', 1)",
        "concat(1, 'a')",
        "concat('a', 'b', 1)",
        "lower-case(1)",
        "normalize-space(1)",
        "trim(1)",
        "trim-left(1)",
        "trim-right(1)",
        "pad-left(1, 2, ' ')",
        "pad-left('a', 2, 1)",
        "pad-right(1, 2, ' ')",
        "pad-right('a', 'b', ' ')",
        "pad-right('a', 2, 1)",
        "replace(1, 'a', 'b')",
        "replace('a', 'b', 1)",
        "replace-regex(1, 'a', 'b')",
        "replace-regex('a', 1, 'b')",
        "replace-regex('a', 'b', 1)",
        "repeat(1, 2)",
        "repeat('a', 'b')",
        "url-encode(1)",
        "capitalize-first(1)",
        "split('a', 1)",
        "subtract-duration(1, P1D)",
        "subtract-duration(2020-01-01Z, 1)",
        "text([1])",
        "string(BT-00-Repeatable-Number)",

        // A built-in function takes the number of arguments it declares
        "upper-case()",
        "concat()",
        "count()",
        "count([1], [2])",
        "string-length('a', 'b')",
        "contains('a')",
        "substring('a')",
        "current-date(1)",
        "add-duration(2020-01-01Z)",
        "not(TRUE, FALSE)",
        "value-union(['a'])",
        "sequence-equal(['a'], ['b'], ['c'])",
        "current-time(1)",

        // The result of a built-in function has the type the function declares
        "count(['a']) like 'a'",
        "string-length('a') and TRUE",
        "upper-case('a') + 1",
        "current-date() + 1",
        "year(current-date()) == 'a'",
        "contains('a', 'b') + 1");
  }

  static Stream<String> invalidTemplateOperations() {
    return Stream.of(
        // Each argument of a template-only function has the type its parameter declares
        "format-number('x', '0.00')",
        "format-number(1, 2)",
        "format-number(BT-00-Text, '0.00')",
        "format-number(BT-00-Number, BT-00-Number)",
        "format-short(1)",
        "format-short('a')",
        "format-short(1, 10:00:00Z)",
        "format-short(2020-01-01Z, 1)",
        "format-medium(TRUE)",
        "format-medium(P1D, 10:00:00Z)",
        "format-medium(2020-01-01Z, 'a')",
        "format-long(BT-00-Number)",
        "format-long(10:00:00Z, 10:00:00Z)",
        "format-long(BT-00-StartDate, BT-00-StartDate)",
        "preferred-language('a')",
        "preferred-language(BT-00-Text)",
        "preferred-language-text(1)",
        "preferred-language-text(BT-00-Number)",

        // A template-only function takes the number of arguments it declares
        "format-number(1)",
        "format-short()",
        "preferred-language()");
  }
}
