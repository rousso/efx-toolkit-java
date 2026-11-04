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
import static eu.europa.ted.efx.model.signatures.ParameterType.NUMBER;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT;
import static eu.europa.ted.efx.model.signatures.ParameterType.TEXT_SEQUENCE;
import static eu.europa.ted.efx.model.signatures.Signature.signature;

import java.util.List;

import eu.europa.ted.efx.model.expressions.sequence.StringSequenceExpression;

/**
 * The functions on texts.
 */
public enum StringFunctions implements BuiltIn {

  EMPTY("empty",
      overload(signature(parameter("value", TEXT)).returning(INDICATOR),
          (script, args) -> script.composeComparisonOperation(args.stringAt(0), "==",
              script.getStringLiteralFromUnquotedString("")))),

  CONTAINS("contains",
      overload(signature(parameter("haystack", TEXT), parameter("needle", TEXT)).returning(INDICATOR),
          (script, args) -> script.composeContainsCondition(args.stringAt(0), args.stringAt(1)))),

  STARTS_WITH("starts-with",
      overload(signature(parameter("haystack", TEXT), parameter("needle", TEXT)).returning(INDICATOR),
          (script, args) -> script.composeStartsWithCondition(args.stringAt(0), args.stringAt(1)))),

  ENDS_WITH("ends-with",
      overload(signature(parameter("haystack", TEXT), parameter("needle", TEXT)).returning(INDICATOR),
          (script, args) -> script.composeEndsWithCondition(args.stringAt(0), args.stringAt(1)))),

  STRING_LENGTH("string-length",
      overload(signature(parameter("value", TEXT)).returning(NUMBER),
          (script, args) -> script.composeStringLengthCalculation(args.stringAt(0)))),

  INDEX_OF_SUBSTRING("index-of-substring",
      overload(signature(parameter("value", TEXT), parameter("substring", TEXT)).returning(NUMBER),
          (script, args) -> script.composeIndexOfSubstringFunction(args.stringAt(0), args.stringAt(1)))),

  SUBSTRING("substring",
      overload(signature(parameter("value", TEXT), parameter("start", NUMBER)).returning(TEXT),
          (script, args) -> script.composeSubstringExtraction(args.stringAt(0), args.numericAt(1))),
      overload(signature(parameter("value", TEXT), parameter("start", NUMBER), parameter("length", NUMBER))
          .returning(TEXT),
          (script, args) -> script.composeSubstringExtraction(args.stringAt(0), args.numericAt(1),
              args.numericAt(2)))),

  SUBSTRING_BEFORE("substring-before",
      overload(signature(parameter("value", TEXT), parameter("delimiter", TEXT)).returning(TEXT),
          (script, args) -> script.composeSubstringBeforeFunction(args.stringAt(0), args.stringAt(1)))),

  SUBSTRING_AFTER("substring-after",
      overload(signature(parameter("value", TEXT), parameter("delimiter", TEXT)).returning(TEXT),
          (script, args) -> script.composeSubstringAfterFunction(args.stringAt(0), args.stringAt(1)))),

  UPPER_CASE("upper-case",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeToUpperCaseConversion(args.stringAt(0)))),

  LOWER_CASE("lower-case",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeToLowerCaseConversion(args.stringAt(0)))),

  NORMALIZE_SPACE("normalize-space",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeNormalizeSpaceFunction(args.stringAt(0)))),

  TRIM("trim",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeTrimFunction(args.stringAt(0)))),

  TRIM_LEFT("trim-left",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeTrimLeftFunction(args.stringAt(0)))),

  TRIM_RIGHT("trim-right",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeTrimRightFunction(args.stringAt(0)))),

  PAD_LEFT("pad-left",
      overload(signature(parameter("value", TEXT), parameter("length", NUMBER), parameter("padChar", TEXT))
          .returning(TEXT),
          (script, args) -> script.composePadLeftFunction(args.stringAt(0), args.numericAt(1),
              args.stringAt(2)))),

  PAD_RIGHT("pad-right",
      overload(signature(parameter("value", TEXT), parameter("length", NUMBER), parameter("padChar", TEXT))
          .returning(TEXT),
          (script, args) -> script.composePadRightFunction(args.stringAt(0), args.numericAt(1),
              args.stringAt(2)))),

  REPEAT("repeat",
      overload(signature(parameter("value", TEXT), parameter("count", NUMBER)).returning(TEXT),
          (script, args) -> script.composeRepeatFunction(args.stringAt(0), args.numericAt(1)))),

  REPLACE("replace",
      overload(signature(parameter("value", TEXT), parameter("search", TEXT), parameter("replacement", TEXT))
          .returning(TEXT),
          (script, args) -> script.composeReplaceFunction(args.stringAt(0), args.stringAt(1),
              args.stringAt(2)))),

  REPLACE_REGEX("replace-regex",
      overload(signature(parameter("value", TEXT), parameter("pattern", TEXT), parameter("replacement", TEXT))
          .returning(TEXT),
          (script, args) -> script.composeReplaceRegexFunction(args.stringAt(0), args.stringAt(1),
              args.stringAt(2)))),

  URL_ENCODE("url-encode",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeUrlEncodeFunction(args.stringAt(0)))),

  CAPITALIZE_FIRST("capitalize-first",
      overload(signature(parameter("value", TEXT)).returning(TEXT),
          (script, args) -> script.composeCapitalizeFirstFunction(args.stringAt(0)))),

  CONCAT("concat",
      overload(signature().variadic(parameter("values", TEXT)).returning(TEXT),
          (script, args) -> script.composeStringConcatenation(args.stringsFrom(0)))),

  STRING_JOIN("string-join",
      overload(signature(parameter("values", TEXT_SEQUENCE), parameter("separator", TEXT)).returning(TEXT),
          (script, args) -> script.composeStringJoin(args.at(0, StringSequenceExpression.class),
              args.stringAt(1)))),

  SPLIT("split",
      overload(signature(parameter("value", TEXT), parameter("delimiter", TEXT)).returning(TEXT_SEQUENCE),
          (script, args) -> script.composeSplitFunction(args.stringAt(0), args.stringAt(1))));

  private final String name;
  private final List<Overload> overloads;

  StringFunctions(final String name, final Overload... overloads) {
    this.name = name;
    this.overloads = List.of(overloads);
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public List<Overload> getOverloads() {
    return this.overloads;
  }

  @Override
  public boolean isFunction() {
    return true;
  }
}
