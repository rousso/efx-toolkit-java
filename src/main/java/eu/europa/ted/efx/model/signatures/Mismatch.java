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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.exceptions.Diagnosis;
import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * Why no parameter list of a {@link Construct} takes the arguments of a call: either none takes
 * that many arguments, or each one that does rejects one of them.
 *
 * <p>A mismatch keeps the arguments and, for each parameter list that takes their number, the
 * rejected {@link Match}: the parameter list, the argument it rejected and why. Its
 * {@link #getRejection rejection} tells the translator which argument to report, and its
 * {@link #diagnose diagnosis} what the error says about it.</p>
 */
public final class Mismatch {

  private final Construct construct;
  private final List<TypedExpression> arguments;
  private final List<Match> rejections;

  Mismatch(final Construct construct, final List<? extends TypedExpression> arguments,
      final List<Match> rejections) {
    this.construct = construct;
    this.arguments = List.copyOf(arguments);
    this.rejections = List.copyOf(rejections);
  }

  /**
   * Gives the mismatch of an argument that a construct cannot read for what it is written as,
   * although its type fits the parameter at its position: a text path where a context is
   * required, for example, or a predicate after a value. The construct has one parameter list.
   *
   * @param construct the construct that cannot read the argument
   * @param arguments the translated arguments of the call
   * @param position the position of the argument it cannot read
   * @return the mismatch, rejected at that position by the parameter there
   */
  public static Mismatch unreadable(final Construct construct,
      final List<? extends TypedExpression> arguments, final int position) {
    final Parameters<?> parameters = construct.getParameterLists().get(0);
    return new Mismatch(construct, arguments, List.of(Match.rejected(parameters, position,
        parameters.parameterAt(position).getType(), arguments.get(position))));
  }

  /**
   * Tells whether the mismatch is the number of arguments.
   *
   * @return {@code true} if no parameter list takes that many arguments
   */
  public boolean isArityMismatch() {
    return this.rejections.isEmpty();
  }

  /**
   * @return for each parameter list that takes that many arguments, the rejected match
   */
  public List<Match> getRejections() {
    return this.rejections;
  }

  /**
   * Gives the rejection that explains the mismatch, whatever the order of the parameter lists.
   *
   * <p>A parameter list that rejects only a cardinality, since every primitive of the call fits
   * it, explains the mismatch before any that rejects a primitive or a path. Among the rest,
   * the rejection at the furthest position explains it, and at the same position a primitive
   * failure before a path one.</p>
   *
   * @return the rejection to report
   * @throws IllegalStateException if the mismatch is the number of arguments
   */
  public Match getRejection() {
    return this.rejections.stream()
        .min(Comparator
            .comparing((Match rejection) -> rejection.getKind() != Match.Kind.CARDINALITY)
            .thenComparing(Comparator.comparingInt(Match::getPosition).reversed())
            .thenComparing(Match::getKind))
        .orElseThrow(() -> new IllegalStateException(this.toString()));
  }

  /**
   * Says what the error of the mismatch says: the construct, with the types of all the arguments,
   * the role of the argument at the position of the {@link #getRejection rejection}, and every
   * type that position takes. Those are the types required by all the parameter lists that reject
   * the same position in the same way, in their order, each once. What the construct suggests for
   * the arguments follows them.
   *
   * @return the diagnosis of the mismatch
   * @throws IllegalStateException if the mismatch is the number of arguments
   */
  public Diagnosis diagnose() {
    final Match reported = this.getRejection();
    final List<String> requirements = this.rejections.stream()
        .filter(rejection -> rejection.getPosition() == reported.getPosition()
            && rejection.getKind() == reported.getKind())
        .map(rejection -> rejection.getRequired().describe())
        .distinct()
        .collect(Collectors.toList());
    return new Diagnosis(this.construct.describe(),
        this.construct.roleOf(reported.getPosition(), this.arguments.size()), this.arguments,
        requirements, this.construct.suggestionsFor(this.arguments));
  }

  @Override
  public String toString() {
    return "no parameter list of " + this.construct.describe() + " takes ("
        + describe(this.arguments) + ")";
  }

  /**
   * Names the classes of the arguments of a call, as the messages about the call show them.
   *
   * @param arguments the translated arguments of the call
   * @return the simple class names of the arguments, separated by commas
   */
  static String describe(final List<? extends TypedExpression> arguments) {
    return arguments.stream().map(argument -> argument.getClass().getSimpleName())
        .collect(Collectors.joining(", "));
  }
}
