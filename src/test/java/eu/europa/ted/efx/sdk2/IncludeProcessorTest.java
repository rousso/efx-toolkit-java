package eu.europa.ted.efx.sdk2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.junit.jupiter.api.Test;

import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.exceptions.TranslatorConfigurationException;
import eu.europa.ted.efx.interfaces.IncludedFileResolver;

class IncludeProcessorTest {

  /**
   * Simple in-memory resolver backed by a map.
   */
  private static IncludedFileResolver mapResolver(Map<String, String> files) {
    return path -> {
      String content = files.get(path);
      if (content == null) {
        throw new IOException("File not found: " + path);
      }
      return content;
    };
  }

  /**
   * @return the text of the tokens of the input, and of the files it includes
   */
  private static String process(IncludedFileResolver resolver, String input) throws IOException {
    TokenSource tokens = new IncludeProcessor(resolver)
        .resolve(new EfxLexer(CharStreams.fromString(input)));
    StringBuilder text = new StringBuilder();
    for (Token token = tokens.nextToken(); token.getType() != Token.EOF;
        token = tokens.nextToken()) {
      text.append(token.getText());
    }
    return text.toString();
  }

  /**
   * @return the first token of the input, or of the files it includes, that the parser reads and
   *         that has the given text
   */
  private static Token tokenOf(IncludedFileResolver resolver, String input, String text)
      throws IOException {
    TokenSource tokens = new IncludeProcessor(resolver)
        .resolve(new EfxLexer(CharStreams.fromString(input)));
    Token token = tokens.nextToken();
    while (token.getChannel() != Token.DEFAULT_CHANNEL || !token.getText().equals(text)) {
      token = tokens.nextToken();
    }
    return token;
  }

  @Test
  void testNoIncludes_PassesThrough() throws IOException {
    String input = "---- STAGE 1a ----\nWITH ND-Root\n";
    assertEquals(input, process(null, input));
  }

  @Test
  void testNoResolver_WithInclude_ThrowsError() {
    String input = "#include \"extra-rules.efx\"\n---- STAGE 1a ----\n";
    assertThrows(TranslatorConfigurationException.class, () -> process(null, input));
  }

  @Test
  void testSingleInclude_SubstitutesContent() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "---- STAGE 2a ----\n");

    String input = "---- STAGE 1a ----\n#include \"extra.efx\"\n";

    assertEquals("---- STAGE 1a ----\n---- STAGE 2a ----\n", process(mapResolver(files), input));
  }

  @Test
  void testMultipleIncludes_SubstitutesAll() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("a.efx", "// content A\n");
    files.put("b.efx", "// content B\n");

    String input = "#include \"a.efx\"\n#include \"b.efx\"\n";

    assertEquals("// content A\n// content B\n", process(mapResolver(files), input));
  }

  @Test
  void testRecursiveIncludes_ResolvesTransitively() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("a.efx", "#include \"b.efx\"\n// from A\n");
    files.put("b.efx", "// from B\n");

    String input = "#include \"a.efx\"\n";

    assertEquals("// from B\n// from A\n", process(mapResolver(files), input));
  }

  @Test
  void testIncludeAfterRule_IsRecognized() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "---- STAGE 2a ----\n");

    String input = "WITH ND-Root ASSERT TRUE AS ERROR R-K7P-M2Q FOR BT-00-Text IN 1\n"
        + "#include \"extra.efx\"\n";

    assertEquals("WITH ND-Root ASSERT TRUE AS ERROR R-K7P-M2Q FOR BT-00-Text IN 1\n"
        + "---- STAGE 2a ----\n", process(mapResolver(files), input));
  }

  @Test
  void testCircularInclude_ThrowsError() {
    Map<String, String> files = new HashMap<>();
    files.put("a.efx", "#include \"b.efx\"\n");
    files.put("b.efx", "#include \"a.efx\"\n");

    String input = "#include \"a.efx\"\n";
    InvalidUsageException exception =
        assertThrows(InvalidUsageException.class, () -> process(mapResolver(files), input));
    assertEquals("line 1:0 in 'b.efx' Circular #include: 'a.efx' includes 'b.efx',"
        + " which includes 'a.efx'.", exception.getMessage());
  }

  @Test
  void testCircularIncludeBelowAnother_NamesOnlyTheCircle() {
    Map<String, String> files = new HashMap<>();
    files.put("main.efx", "#include \"a.efx\"\n");
    files.put("a.efx", "#include \"b.efx\"\n");
    files.put("b.efx", "#include \"c.efx\"\n");
    files.put("c.efx", "// c includes a\n#include \"a.efx\"\n");

    String input = "#include \"main.efx\"\n";
    InvalidUsageException exception =
        assertThrows(InvalidUsageException.class, () -> process(mapResolver(files), input));
    assertEquals("line 2:0 in 'c.efx' Circular #include: 'a.efx' includes 'b.efx',"
        + " which includes 'c.efx', which includes 'a.efx'.", exception.getMessage());
  }

  @Test
  void testSelfInclude_ThrowsError() {
    Map<String, String> files = new HashMap<>();
    files.put("self.efx", "#include \"self.efx\"\n");

    String input = "#include \"self.efx\"\n";
    InvalidUsageException exception =
        assertThrows(InvalidUsageException.class, () -> process(mapResolver(files), input));
    assertEquals("line 1:0 in 'self.efx' Circular #include: 'self.efx' includes 'self.efx'.",
        exception.getMessage());
  }

  @Test
  void testIncludeWithComment_IsRecognized() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// included\n");

    String input = "#include \"extra.efx\" // hand-written rules\n";

    assertEquals("// included\n", process(mapResolver(files), input));
  }

  @Test
  void testIncludeWithLeadingWhitespace_IsRecognized() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// included\n");

    String input = "  #include \"extra.efx\"\n";

    assertEquals("// included\n", process(mapResolver(files), input));
  }

  @Test
  void testIncludeBetweenContent_PreservesContext() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("middle.efx", "// middle\n");

    String input = "// before\n#include \"middle.efx\"\n// after\n";

    assertEquals("// before\n// middle\n// after\n", process(mapResolver(files), input));
  }

  @Test
  void testIncludedFileWithoutLineEnd_EndsItsLastLine() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// included");

    String input = "#include \"extra.efx\"\n// after\n";

    assertEquals("// included\n// after\n", process(mapResolver(files), input));
  }

  @Test
  void testIncludedTokens_KeepTheirPositionInTheirFile() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// extra\n\n---- STAGE 2a ----\n");

    String input = "// main\n#include \"extra.efx\"\n---- STAGE 3a ----\n";

    Token included = tokenOf(mapResolver(files), input, "2a");
    assertEquals("extra.efx", included.getInputStream().getSourceName());
    assertEquals(3, included.getLine());
    assertEquals(11, included.getCharPositionInLine());

    Token after = tokenOf(mapResolver(files), input, "3a");
    assertEquals(3, after.getLine());
    assertEquals(11, after.getCharPositionInLine());
  }

  @Test
  void testUnresolvablePath_ThrowsIOException() {
    IncludedFileResolver resolver = path -> {
      throw new IOException("Not found: " + path);
    };

    String input = "#include \"missing.efx\"\n";
    assertThrows(IOException.class, () -> process(resolver, input));
  }
}
