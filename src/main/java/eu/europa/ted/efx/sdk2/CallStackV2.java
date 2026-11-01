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

import eu.europa.ted.efx.exceptions.InvalidIdentifierException;
import eu.europa.ted.efx.model.CallStack;
import eu.europa.ted.efx.model.variables.Function;
import eu.europa.ted.efx.model.variables.Identifier;
import eu.europa.ted.efx.model.variables.Template;

/**
 * The call stack of the EFX-2 translators. The names that start with {@value #RESERVED_PREFIX}
 * are kept for the names the translator generates, such as the variable an index binds its
 * position to, so that no declaration can hide one or be hidden by one: a declaration that gives
 * such a name is rejected.
 */
final class CallStackV2 extends CallStack {

  /**
   * What the names the translator generates start with.
   */
  static final String RESERVED_PREFIX = "__";

  @Override
  public void declareIdentifier(final Identifier identifier) {
    this.rejectReserved(identifier.name);
    super.declareIdentifier(identifier);
  }

  @Override
  public void declareGlobalIdentifier(final Identifier identifier) {
    this.rejectReserved(identifier.name);
    super.declareGlobalIdentifier(identifier);
  }

  @Override
  public void declareFunction(final Function function) {
    this.rejectReserved(function.name);
    super.declareFunction(function);
  }

  @Override
  public void declareTemplate(final Template template) {
    this.rejectReserved(template.name);
    super.declareTemplate(template);
  }

  /**
   * @param name a name being declared
   * @throws InvalidIdentifierException if the translator keeps the name for itself
   */
  private void rejectReserved(final String name) {
    if (name.startsWith(RESERVED_PREFIX)) {
      throw InvalidIdentifierException.identifierIsReserved(this.peekParserContext(), name,
          RESERVED_PREFIX);
    }
  }
}
