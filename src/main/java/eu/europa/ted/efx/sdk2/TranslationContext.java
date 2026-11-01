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

import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.interfaces.SymbolResolver;
import eu.europa.ted.efx.model.CallStack;
import eu.europa.ted.efx.model.ContextStack;

/**
 * The state that the translators of SDK 2 share during one translation: the symbols that
 * references resolve to, the script generator that composes the target language, the call stack
 * of declarations and template data, and the stack of the contexts that references are read in.
 *
 * <p>A translator creates one for each translation, so that the declarations and contexts of one
 * translation are not visible in the next.</p>
 */
final class TranslationContext {

  private final SymbolResolver symbols;
  private final ScriptGenerator script;
  private final CallStack stack = new CallStackV2();
  private final ContextStack efxContext;

  TranslationContext(final SymbolResolver symbols, final ScriptGenerator script) {
    this.symbols = symbols;
    this.script = script;
    this.efxContext = new ContextStack(symbols);
  }

  SymbolResolver symbols() {
    return this.symbols;
  }

  ScriptGenerator script() {
    return this.script;
  }

  CallStack stack() {
    return this.stack;
  }

  ContextStack efxContext() {
    return this.efxContext;
  }
}
