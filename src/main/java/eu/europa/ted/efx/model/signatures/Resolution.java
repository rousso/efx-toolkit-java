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
import java.util.OptionalInt;

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * The outcome of resolving a call of a {@link BuiltIn}: the overload that applies, with the
 * arguments converted to its parameters, or the {@link Mismatch} when none applies.
 *
 * <p>An overload applies when it takes the number of arguments and, once {@code T} is bound, every
 * argument converts to its parameter. At most one overload of a built-in applies to any arguments;
 * the catalogue's unit tests prove it for every built-in.</p>
 */
public final class Resolution {

  private final Overload overload;
  private final Arguments arguments;
  private final Mismatch mismatch;

  private Resolution(final Overload overload, final Arguments arguments, final Mismatch mismatch) {
    this.overload = overload;
    this.arguments = arguments;
    this.mismatch = mismatch;
  }

  /**
   * Resolves a call of a built-in.
   *
   * @param builtIn the built-in called
   * @param arguments the translated arguments of the call
   * @return the resolution
   */
  static Resolution of(final BuiltIn builtIn, final List<? extends TypedExpression> arguments) {
    List<Overload> applicable = new ArrayList<>();
    List<Mismatch.Rejection> rejections = new ArrayList<>();
    for (Overload overload : builtIn.getOverloads()) {
      if (overload.getSignature().takes(arguments.size())) {
        OptionalInt rejected = overload.getSignature().firstRejectedPosition(arguments);
        if (rejected.isPresent()) {
          rejections.add(new Mismatch.Rejection(overload, rejected.getAsInt()));
        } else {
          applicable.add(overload);
        }
      }
    }
    assert applicable.size() <= 1 : "Overloads " + applicable + " of '" + builtIn.getName()
        + "' all apply to the same arguments";
    if (applicable.isEmpty()) {
      return new Resolution(null, null, new Mismatch(builtIn, arguments, rejections));
    }
    Overload overload = applicable.get(0);
    return new Resolution(overload, overload.getSignature().convert(arguments), null);
  }

  /**
   * @return {@code true} if an overload applies
   */
  public boolean isResolved() {
    return this.mismatch == null;
  }

  /**
   * @return the overload that applies
   * @throws IllegalStateException if none applies
   */
  public Overload getOverload() {
    this.requireResolved();
    return this.overload;
  }

  /**
   * @return the arguments, converted to the parameters of the overload that applies
   * @throws IllegalStateException if none applies
   */
  public Arguments getArguments() {
    this.requireResolved();
    return this.arguments;
  }

  /**
   * @return why no overload applies
   * @throws IllegalStateException if one applies
   */
  public Mismatch getMismatch() {
    if (this.isResolved()) {
      throw new IllegalStateException("The call is resolved");
    }
    return this.mismatch;
  }

  /**
   * Translates the call with the overload that applies.
   *
   * @param script the script generator of the target language
   * @return the translated call
   * @throws IllegalStateException if no overload applies
   */
  public TypedExpression compose(final ScriptGenerator script) {
    return this.getOverload().compose(script, this.arguments);
  }

  private void requireResolved() {
    if (!this.isResolved()) {
      throw new IllegalStateException(this.mismatch.toString());
    }
  }
}
