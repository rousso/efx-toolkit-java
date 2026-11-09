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
package eu.europa.ted.efx.model.constructs;

/**
 * How an expression writes a function or an operator: as a function, with the token before its
 * arguments in parentheses, or as an operator, with the token beside its operands. A type error
 * names what was written by its spelling.
 *
 * <p>A built-in is usually spelled by its own name, but a call may spell it otherwise:
 * {@code string} is a spelling of the function {@code text}, and {@code :preferredLanguage} spells
 * the function {@code preferred-language} as an operator after a field. A step that only a field
 * has, such as {@code :rawValue}, is spelled as an operator after the field.</p>
 */
public final class Spelling {

  private final String token;
  private final boolean function;

  private Spelling(final String token, final boolean function) {
    this.token = token;
    this.function = function;
  }

  /**
   * @param token the function as written, such as {@code substring} or {@code string}
   * @return the spelling of a function
   */
  public static Spelling function(final String token) {
    return new Spelling(token, true);
  }

  /**
   * @param token the operator as written, such as {@code +} or {@code :preferredLanguage}
   * @return the spelling of an operator
   */
  public static Spelling operator(final String token) {
    return new Spelling(token, false);
  }

  /**
   * @return how a type error names what was written: "Function 'string'" or
   *         "Operator ':preferredLanguage'"
   */
  public String describe() {
    return this.function ? Construct.functionName(this.token)
        : Construct.operatorName(this.token);
  }

  /**
   * @param position the position of an argument
   * @param count the number of arguments
   * @return how a type error names the argument at that position: the arguments of a function by
   *         their position, such as "2nd argument"; the operands of an operator, "operand", or
   *         "left operand" and "right operand"
   */
  public String roleOf(final int position, final int count) {
    return this.function ? Construct.argumentRole(position, count)
        : Construct.operandRole(position, count);
  }
}
