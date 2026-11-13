package eu.europa.ted.efx.sdk2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;

import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.exceptions.TranslatorConfigurationException;
import eu.europa.ted.efx.interfaces.IncludedFileResolver;

/**
 * Processes the {@code #include} directives of an EFX file: puts the tokens of each included file
 * in place of the directive that includes it.
 *
 * <p>
 * The lexer reads the directive and its path. Each included file is read by a lexer of its own,
 * from its start, so that its tokens keep their lines and columns in that file and are named after
 * it, as an error message names them. Recursive includes are supported with circular dependency
 * detection.
 * </p>
 */
public class IncludeProcessor {

  private final IncludedFileResolver resolver;
  /** The files being resolved, in the order in which they include each other. */
  private final Set<String> resolving = new LinkedHashSet<>();

  public IncludeProcessor(IncludedFileResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * Reads the tokens of a file, with the tokens of each file it includes in place of the
   * directive.
   *
   * @param lexer the lexer of the file
   * @return the tokens of the file and of the files it includes, up to the end of the file
   * @throws IOException If an included file cannot be resolved.
   */
  public TokenSource resolve(final EfxLexer lexer) throws IOException {
    final List<Token> tokens = new ArrayList<>();
    tokens.add(this.read(lexer, tokens));
    return new ListTokenSource(tokens);
  }

  /**
   * Adds the tokens of a file to a list, with the tokens of each file it includes in place of the
   * directive.
   *
   * @param lexer the lexer of the file
   * @param tokens the list
   * @return the end of the file, which is not added
   * @throws IOException If an included file cannot be resolved.
   */
  private Token read(final EfxLexer lexer, final List<Token> tokens) throws IOException {
    Token token = lexer.nextToken();
    while (token.getType() != Token.EOF) {
      if (token.getType() == EfxLexer.IncludeDirective) {
        // The lexer reads the path right after the directive, unless the file ends there.
        final Token path = lexer.nextToken();
        if (path.getType() != EfxLexer.IncludePath) {
          // The parser rejects a directive with no path.
          tokens.add(token);
          return path;
        }
        this.include(token, path, lexer, tokens);
      } else {
        tokens.add(token);
      }
      token = lexer.nextToken();
    }
    return token;
  }

  /**
   * Adds the tokens of an included file to a list.
   *
   * @param directive the {@code #include} directive
   * @param path the path that follows it
   * @param including the lexer of the file that holds the directive, whose error listeners the
   *        lexer of the included file reports to
   * @param tokens the list
   * @throws IOException If the included file cannot be resolved.
   */
  private void include(final Token directive, final Token path, final EfxLexer including,
      final List<Token> tokens) throws IOException {
    if (this.resolver == null) {
      throw TranslatorConfigurationException.includeResolverNotConfigured();
    }
    final String file = fileOf(path);
    if (!this.resolving.add(file)) {
      throw InvalidUsageException.circularInclude(this.cycleClosedBy(file)).at(directive);
    }
    try {
      final EfxLexer lexer = new EfxLexer(CharStreams.fromString(this.textOf(file), file));
      lexer.removeErrorListeners();
      including.getErrorListeners().forEach(lexer::addErrorListener);
      this.read(lexer, tokens);
    } finally {
      this.resolving.remove(file);
    }
  }

  /**
   * @param file an included file
   * @return its text, which ends its last line, so that the line after the directive is a line of
   *         its own
   * @throws IOException If the file cannot be resolved.
   */
  private String textOf(final String file) throws IOException {
    final String text = this.resolver.resolve(file);
    if (!text.isEmpty() && !text.endsWith("\n")) {
      return text + "\n";
    }
    return text;
  }

  /**
   * @param path the path of a directive, as the lexer reads it: in quotes, and followed by the
   *        rest of its line
   * @return the file the path names
   */
  private static String fileOf(final Token path) {
    final String text = path.getText();
    return text.substring(1, text.indexOf('"', 1));
  }

  /**
   * @param path a file being resolved, which is included again
   * @return the files that include each other in a circle, from that file back to it
   */
  private List<String> cycleClosedBy(final String path) {
    final List<String> files = new ArrayList<>(this.resolving);
    final List<String> cycle = new ArrayList<>(files.subList(files.indexOf(path), files.size()));
    cycle.add(path);
    return cycle;
  }
}
