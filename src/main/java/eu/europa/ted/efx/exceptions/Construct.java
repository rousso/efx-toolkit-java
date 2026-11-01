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
import java.util.stream.IntStream;

/**
 * A construct of EFX as a type error names it: what it is called, as it is written, and the role
 * of each of its operands, such as "Operator '=='" with its "left operand" and "right operand".
 */
public final class Construct {

  private final String name;
  private final List<String> roles;

  private Construct(final String name, final List<String> roles) {
    this.name = name;
    this.roles = List.copyOf(roles);
  }

  /**
   * An operator, written between its two operands or after its one.
   *
   * @param symbol the operator as it is written, such as {@code ==} or {@code is present}
   * @param arity the number of its operands, one or two
   * @return "Operator '=='", whose operands are the left and the right one, or the operand
   */
  public static Construct operator(final String symbol, final int arity) {
    return new Construct("Operator '" + symbol + "'",
        arity == 1 ? List.of("operand") : List.of("left operand", "right operand"));
  }

  /**
   * A function, built in or declared, called with its arguments in parentheses.
   *
   * @param name the name of the function as it is written in a call, such as {@code substring} or
   *        {@code ?greeting}
   * @param arity the number of its arguments
   * @return "Function 'substring'", whose operands are its arguments
   */
  public static Construct function(final String name, final int arity) {
    return new Construct("Function '" + name + "'", numbered("argument", arity));
  }

  /**
   * A template invoked with its arguments.
   *
   * @param name the name of the template
   * @param arity the number of its arguments
   * @return "Template 'greeting'", whose operands are its arguments
   */
  public static Construct template(final String name, final int arity) {
    return new Construct("Template '" + name + "'", numbered("argument", arity));
  }

  /**
   * A list of values written in brackets.
   *
   * @param size the number of its items
   * @return "List", whose operands are its items
   */
  public static Construct list(final int size) {
    return new Construct("List", numbered("item", size));
  }

  /**
   * Any other construct, whose operands have fixed roles.
   *
   * @param name what the construct is called, such as "Conditional 'if'"
   * @param roles the role of each of its operands, in order, such as "condition"
   * @return the construct
   */
  public static Construct of(final String name, final String... roles) {
    return new Construct(name, List.of(roles));
  }

  /**
   * @return what the construct is called, as it is written
   */
  public String getName() {
    return this.name;
  }

  /**
   * @param position the position of an operand
   * @return the role of the operand at that position, such as "right operand" or "2nd argument"
   */
  public String roleOf(final int position) {
    return this.roles.get(position);
  }

  /**
   * Names the operands of a construct that takes any number of them by their position, or by the
   * noun alone when there is only one.
   */
  private static List<String> numbered(final String noun, final int count) {
    if (count == 1) {
      return List.of(noun);
    }
    return IntStream.rangeClosed(1, count).mapToObj(position -> ordinal(position) + " " + noun)
        .collect(Collectors.toList());
  }

  private static String ordinal(final int position) {
    final int lastTwoDigits = position % 100;
    if (lastTwoDigits >= 11 && lastTwoDigits <= 13) {
      return position + "th";
    }
    switch (position % 10) {
      case 1:
        return position + "st";
      case 2:
        return position + "nd";
      case 3:
        return position + "rd";
      default:
        return position + "th";
    }
  }
}
