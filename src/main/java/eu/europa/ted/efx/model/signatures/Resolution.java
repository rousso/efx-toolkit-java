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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import eu.europa.ted.efx.exceptions.TranslatorConfigurationException;
import eu.europa.ted.efx.interfaces.TypeChecker;
import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * The parameter list of a {@link Construct} that takes the arguments of a call, with the arguments
 * converted to its parameters.
 *
 * <p>A parameter list takes the arguments when it {@link Parameters#match matches} them: it takes
 * their number and, once {@code T} is bound, every argument converts to its parameter. When more
 * than one takes them, the most specific one is chosen: the one whose converted arguments would
 * also convert to the parameters of every other, as {@code (T)} is more specific than
 * {@code (T*)} for a single value. A resolution with no single most specific parameter list raises
 * a {@link TranslatorConfigurationException}: the construct is ambiguous, which the catalogue's
 * unit tests prove no built-in is.</p>
 *
 * <p>A call that no parameter list takes has no resolution; its {@link Mismatch} says why. A
 * built-in translates a resolved call itself; any other construct is translated where it is
 * written, from the converted arguments.</p>
 */
public final class Resolution {

  private final Parameters<?> parameters;
  private final ParsedArguments<?> arguments;

  private Resolution(final Parameters<?> parameters, final ParsedArguments<?> arguments) {
    this.parameters = parameters;
    this.arguments = arguments;
  }

  /**
   * Checks the arguments of a call of a construct against each of its parameter lists that takes
   * their number, and chooses the most specific of those that take them.
   *
   * @param construct the construct called
   * @param arguments the translated arguments of the call
   * @return the resolution, or nothing if no parameter list takes the arguments
   * @throws TranslatorConfigurationException if more than one parameter list takes the arguments
   *         and none of them is more specific than all the others, a defect of the construct
   */
  static Optional<Resolution> of(final Construct construct,
      final List<? extends TypedExpression> arguments) {
    final List<Resolution> applicable = construct.getParameterLists().stream()
        .filter(parameters -> parameters.takes(arguments.size()))
        .flatMap(parameters -> parameters.match(arguments)
            .map(converted -> new Resolution(parameters, converted)).stream())
        .collect(Collectors.toList());
    if (applicable.isEmpty()) {
      return Optional.empty();
    }
    final List<Resolution> mostSpecific = applicable.stream()
        .filter(candidate -> applicable.stream().allMatch(candidate::isAsSpecificAs))
        .collect(Collectors.toList());
    if (mostSpecific.size() != 1) {
      throw TranslatorConfigurationException.ambiguousOverloads(construct.describe(),
          applicable.stream().map(resolution -> resolution.getParameters().toString())
              .collect(Collectors.joining(", ", "[", "]")),
          Mismatch.describe(arguments));
    }
    return Optional.of(mostSpecific.get(0));
  }

  /**
   * @return the parameter list that takes the arguments, as the construct declares it
   */
  public Parameters<?> getParameters() {
    return this.parameters;
  }

  /**
   * @return the arguments, converted to the parameters of the parameter list that takes them
   */
  public ParsedArguments<?> getArguments() {
    return this.arguments;
  }

  /**
   * Tells whether this resolution is at least as specific as another of the same arguments: each
   * argument was converted to a class that converts to the class the other converted it to, as a
   * single value converts to a sequence of one.
   *
   * @param other a resolution of the same arguments by another parameter list
   * @return {@code true} if every argument of this resolution would also convert to the parameter
   *         of the other
   */
  private boolean isAsSpecificAs(final Resolution other) {
    final ParsedArguments<?> mine = this.getArguments();
    final ParsedArguments<?> theirs = other.getArguments();
    return IntStream.range(0, mine.size()).allMatch(position -> TypeChecker.V2
        .canConvert(mine.typeAt(position), theirs.typeAt(position)));
  }
}
