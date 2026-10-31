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

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import eu.europa.ted.efx.sdk2.EfxParser.ExpressionContext;
import eu.europa.ted.efx.sdk2.EfxParser.FieldContextContext;
import eu.europa.ted.efx.sdk2.EfxParser.NodeContextContext;

/**
 * Walks a parse tree as {@link ParseTreeWalker} does, except that it does not enter the three
 * rules that {@link EfxExpressionVisitor} translates: an expression, a field context and a node
 * context. No listener method is called inside them: the listener of the enclosing rule asks the
 * visitor for them.
 */
final class ExpressionSkippingWalker extends ParseTreeWalker {

  @Override
  public void walk(final ParseTreeListener listener, final ParseTree tree) {
    if (tree instanceof ExpressionContext || tree instanceof FieldContextContext
        || tree instanceof NodeContextContext) {
      return;
    }
    super.walk(listener, tree);
  }
}
