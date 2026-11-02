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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import eu.europa.ted.efx.exceptions.TranslatorConfigurationException;
import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * The outcome of checking the arguments of a call against the parameter lists of a
 * {@link Construct}: the parameter list that takes them, with the arguments converted to its
 * parameters, or the {@link Mismatch} when none does.
 *
 * <p>A parameter list takes the arguments when it {@link Parameters#match matches} them: it takes
 * their number and, once {@code T} is bound, every argument converts to its parameter. At most one
 * parameter list of a construct takes any arguments; the catalogue's unit tests prove it for every
 * built-in, and a resolution that finds more than one raises a
 * {@link TranslatorConfigurationException}.</p>
 *
 * <p>A built-in translates a resolved call with {@link BuiltIn#compose}; any other construct is
 * translated where it is written, from the converted arguments.</p>
 */
public final class Resolution {

  private final Parameters<?> parameters;
  private final ParsedArguments<?> arguments;
  private final Mismatch mismatch;

  private Resolution(final Parameters<?> parameters, final ParsedArguments<?> arguments,
      final Mismatch mismatch) {
    this.parameters = parameters;
    this.arguments = arguments;
    this.mismatch = mismatch;
  }

  /**
   * Checks the arguments of a call of a construct.
   *
   * @param construct the construct called
   * @param arguments the translated arguments of the call
   * @return the resolution
   * @throws TranslatorConfigurationException if more than one parameter list takes the arguments,
   *         a defect of the catalogue
   */
  static Resolution of(final Construct construct,
      final List<? extends TypedExpression> arguments) {
    List<Resolution> applicable = new ArrayList<>();
    List<Mismatch.Rejection> rejections = new ArrayList<>();
    for (Parameters<?> parameters : construct.getParameterLists()) {
      Match match = parameters.match(arguments);
      if (match.isMatched()) {
        applicable.add(new Resolution(parameters, match.getArguments(), null));
      } else if (!match.isCountMismatch()) {
        rejections.add(new Mismatch.Rejection(parameters, match));
      }
    }
    if (applicable.size() > 1) {
      throw TranslatorConfigurationException.ambiguousOverloads(construct.describe(),
          applicable.stream().map(resolution -> resolution.parameters.toString())
              .collect(Collectors.joining(", ", "[", "]")),
          Mismatch.describe(arguments));
    }
    if (applicable.isEmpty()) {
      return new Resolution(null, null, new Mismatch(construct, arguments, rejections));
    }
    return applicable.get(0);
  }

  /**
   * @return {@code true} if a parameter list takes the arguments
   */
  public boolean isResolved() {
    return this.mismatch == null;
  }

  /**
   * @return the parameter list that takes the arguments, as the construct declares it
   * @throws IllegalStateException if none does
   */
  public Parameters<?> getParameters() {
    this.requireResolved();
    return this.parameters;
  }

  /**
   * @return the arguments, converted to the parameters of the parameter list that takes them
   * @throws IllegalStateException if none does
   */
  public ParsedArguments<?> getArguments() {
    this.requireResolved();
    return this.arguments;
  }

  /**
   * @return why no parameter list takes the arguments
   * @throws IllegalStateException if one does
   */
  public Mismatch getMismatch() {
    if (this.isResolved()) {
      throw new IllegalStateException("The call is resolved");
    }
    return this.mismatch;
  }

  private void requireResolved() {
    if (!this.isResolved()) {
      throw new IllegalStateException(this.mismatch.toString());
    }
  }
}
