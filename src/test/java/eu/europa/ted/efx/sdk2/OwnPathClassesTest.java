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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.antlr.v4.runtime.BaseErrorListener;
import org.junit.jupiter.api.Test;

import eu.europa.ted.eforms.sdk.component.SdkComponent;
import eu.europa.ted.eforms.sdk.component.SdkComponentType;
import eu.europa.ted.efx.EfxTestsBase;
import eu.europa.ted.efx.exceptions.TypeMismatchException;
import eu.europa.ted.efx.interfaces.MarkupGenerator;
import eu.europa.ted.efx.interfaces.ScriptGenerator;
import eu.europa.ted.efx.interfaces.SymbolResolver;
import eu.europa.ted.efx.interfaces.TranslatorDependencyFactory;
import eu.europa.ted.efx.interfaces.TranslatorOptions;
import eu.europa.ted.efx.interfaces.ValidatorGenerator;
import eu.europa.ted.efx.mock.DependencyFactoryMock;
import eu.europa.ted.efx.mock.sdk2.SymbolResolverMockV2;
import eu.europa.ted.efx.model.expressions.PathExpression;
import eu.europa.ted.efx.model.expressions.scalar.StringPath;

/**
 * Translates with a symbol resolver that gives the paths of text fields as a class of its own,
 * which has no constructor that takes a script alone. The script generator builds what it composes
 * with that constructor, so the translator must compose with the toolkit's own classes.
 */
class OwnPathClassesTest extends EfxTestsBase {

  @Override
  protected String getSdkVersion() {
    return "eforms-sdk-2.0";
  }

  @Override
  protected TranslatorDependencyFactory getDependencyFactory() {
    return OwnPathsDependencyFactory.INSTANCE;
  }

  @Test
  void testIndexAfterATextField_SelectsFromTheWholeSequence() {
    testExpressionTranslationWithContext("(PathNode/TextField)[1]", "ND-Root",
        "BT-00-Text[at 1] is present");
  }

  @Test
  void testIndexAfterATextField_NamesItsTypeInATypeError() {
    final TypeMismatchException error = assertThrows(TypeMismatchException.class,
        () -> translateExpressionWithContext("ND-Root", "BT-00-Text[at TRUE]"));
    assertEquals("line 1:26 Type mismatch. Index cannot take 'text' and 'indicator'."
        + " Its position must be 'number'.", error.getMessage());
  }

  /**
   * The path of a text field, built from the toolkit's own: it has no constructor that takes a
   * script alone.
   */
  private static final class OwnTextPath extends StringPath {

    OwnTextPath(final StringPath path) {
      super(path.getScript());
    }
  }

  /**
   * The symbol resolver of the unit tests, giving the path of a text field as an
   * {@link OwnTextPath}. It is declared with a qualifier of its own, as it would otherwise inherit
   * the declaration of the mock it extends, and two resolvers would be declared as one.
   */
  @SdkComponent(versions = {"2"}, componentType = SdkComponentType.SYMBOL_RESOLVER,
      qualifier = "own-paths")
  private static final class OwnPathsSymbolResolver extends SymbolResolverMockV2 {

    OwnPathsSymbolResolver() throws InstantiationException {
      super();
    }

    @Override
    public PathExpression getRelativePathOfField(final String fieldId, final String contextId) {
      final PathExpression path = super.getRelativePathOfField(fieldId, contextId);
      return path.getClass() == StringPath.class ? new OwnTextPath((StringPath) path) : path;
    }
  }

  /**
   * The dependencies of the unit tests, with the {@link OwnPathsSymbolResolver}.
   */
  private static final class OwnPathsDependencyFactory implements TranslatorDependencyFactory {

    private static final OwnPathsDependencyFactory INSTANCE = new OwnPathsDependencyFactory();

    private final SymbolResolver symbols;

    private OwnPathsDependencyFactory() {
      try {
        this.symbols = new OwnPathsSymbolResolver();
      } catch (InstantiationException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public SymbolResolver createSymbolResolver(final String sdkVersion, final String qualifier) {
      return this.symbols;
    }

    @Override
    public ScriptGenerator createScriptGenerator(final String sdkVersion, final String qualifier,
        final TranslatorOptions options) {
      return DependencyFactoryMock.INSTANCE.createScriptGenerator(sdkVersion, qualifier, options);
    }

    @Override
    public MarkupGenerator createMarkupGenerator(final String sdkVersion, final String qualifier,
        final TranslatorOptions options) {
      return DependencyFactoryMock.INSTANCE.createMarkupGenerator(sdkVersion, qualifier, options);
    }

    @Override
    public ValidatorGenerator createValidatorGenerator(final String sdkVersion,
        final String qualifier, final TranslatorOptions options) {
      return DependencyFactoryMock.INSTANCE.createValidatorGenerator(sdkVersion, qualifier,
          options);
    }

    @Override
    public BaseErrorListener createErrorListener() {
      return DependencyFactoryMock.INSTANCE.createErrorListener();
    }
  }
}
