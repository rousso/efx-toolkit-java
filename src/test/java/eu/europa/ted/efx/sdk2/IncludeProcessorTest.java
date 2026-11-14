package eu.europa.ted.efx.sdk2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;

import eu.europa.ted.efx.exceptions.InvalidUsageException;
import eu.europa.ted.efx.exceptions.TranslatorConfigurationException;
import eu.europa.ted.efx.interfaces.IncludedFileResolver;
import eu.europa.ted.efx.model.CombinedText;
import eu.europa.ted.efx.model.LineMap;

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

  @Test
  void testNoIncludes_PassesThrough() throws IOException {
    String input = "---- STAGE 1a ----\nWITH ND-Root\n";
    IncludeProcessor processor = new IncludeProcessor(null);
    assertEquals(input, processor.process(input));
  }

  @Test
  void testNoResolver_WithInclude_ThrowsError() {
    String input = "#include \"extra-rules.efx\"\n---- STAGE 1a ----\n";
    IncludeProcessor processor = new IncludeProcessor(null);
    assertThrows(TranslatorConfigurationException.class, () -> processor.process(input));
  }

  @Test
  void testSingleInclude_SubstitutesContent() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "---- STAGE 2a ----\n");

    String input = "---- STAGE 1a ----\n#include \"extra.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    String result = processor.process(input);

    assertEquals("---- STAGE 1a ----\n---- STAGE 2a ----\n\n", result);
  }

  @Test
  void testMultipleIncludes_SubstitutesAll() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("a.efx", "// content A\n");
    files.put("b.efx", "// content B\n");

    String input = "#include \"a.efx\"\n#include \"b.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    String result = processor.process(input);

    assertEquals("// content A\n\n// content B\n\n", result);
  }

  @Test
  void testRecursiveIncludes_ResolvesTransitively() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("a.efx", "#include \"b.efx\"\n// from A\n");
    files.put("b.efx", "// from B\n");

    String input = "#include \"a.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    String result = processor.process(input);

    assertEquals("// from B\n\n// from A\n\n", result);
  }

  @Test
  void testCircularInclude_ThrowsError() {
    Map<String, String> files = new HashMap<>();
    files.put("a.efx", "#include \"b.efx\"\n");
    files.put("b.efx", "#include \"a.efx\"\n");

    String input = "#include \"a.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    InvalidUsageException exception =
        assertThrows(InvalidUsageException.class, () -> processor.process(input));
    assertEquals("Circular #include: 'a.efx' includes 'b.efx', which includes 'a.efx'.",
        exception.getMessage());
  }

  @Test
  void testCircularIncludeBelowAnother_NamesOnlyTheCircle() {
    Map<String, String> files = new HashMap<>();
    files.put("main.efx", "#include \"a.efx\"\n");
    files.put("a.efx", "#include \"b.efx\"\n");
    files.put("b.efx", "#include \"c.efx\"\n");
    files.put("c.efx", "#include \"a.efx\"\n");

    String input = "#include \"main.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    InvalidUsageException exception =
        assertThrows(InvalidUsageException.class, () -> processor.process(input));
    assertEquals("Circular #include: 'a.efx' includes 'b.efx', which includes 'c.efx',"
        + " which includes 'a.efx'.", exception.getMessage());
  }

  @Test
  void testSelfInclude_ThrowsError() {
    Map<String, String> files = new HashMap<>();
    files.put("self.efx", "#include \"self.efx\"\n");

    String input = "#include \"self.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    InvalidUsageException exception =
        assertThrows(InvalidUsageException.class, () -> processor.process(input));
    assertEquals("Circular #include: 'self.efx' includes 'self.efx'.", exception.getMessage());
  }

  @Test
  void testIncludeWithComment_IsRecognized() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// included\n");

    String input = "#include \"extra.efx\" // hand-written rules\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    String result = processor.process(input);

    assertEquals("// included\n\n", result);
  }

  @Test
  void testIncludeWithLeadingWhitespace_IsRecognized() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// included\n");

    String input = "  #include \"extra.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    String result = processor.process(input);

    assertEquals("// included\n\n", result);
  }

  @Test
  void testIncludeBetweenContent_PreservesContext() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("middle.efx", "// middle\n");

    String input = "// before\n#include \"middle.efx\"\n// after\n";
    IncludeProcessor processor = new IncludeProcessor(mapResolver(files));
    String result = processor.process(input);

    assertEquals("// before\n// middle\n\n// after\n", result);
  }

  @Test
  void testUnresolvablePath_ThrowsIOException() {
    IncludedFileResolver resolver = path -> {
      throw new IOException("Not found: " + path);
    };

    String input = "#include \"missing.efx\"\n";
    IncludeProcessor processor = new IncludeProcessor(resolver);
    assertThrows(IOException.class, () -> processor.process(input));
  }

  @Test
  void testLineMap_MapsLinesToTheFileTheyWereWrittenIn() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// extra 1\n// extra 2\n");

    String input = "// main 1\n#include \"extra.efx\"\n// main 3\n";
    CombinedText combined =
        new IncludeProcessor(mapResolver(files)).resolve(CharStreams.fromString(input));
    LineMap lineMap = combined.getLineMap();

    assertEquals("// main 1\n// extra 1\n// extra 2\n\n// main 3\n", combined.toString());
    assertEquals("line 1:3", lineMap.positionOf(1, 3));
    assertEquals("line 1:3 in 'extra.efx'", lineMap.positionOf(2, 3));
    assertEquals("line 2:3 in 'extra.efx'", lineMap.positionOf(3, 3));
    assertEquals("line 2:0", lineMap.positionOf(4, 0));
    assertEquals("line 3:3", lineMap.positionOf(5, 3));
  }

  @Test
  void testLineMap_NestedInclude_MapsLinesToTheInnermostFile() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("a.efx", "// a 1\n#include \"b.efx\"\n// a 3\n");
    files.put("b.efx", "// b 1\n");

    String input = "#include \"a.efx\"\n// main 2\n";
    CombinedText combined =
        new IncludeProcessor(mapResolver(files)).resolve(CharStreams.fromString(input));
    LineMap lineMap = combined.getLineMap();

    assertEquals("// a 1\n// b 1\n\n// a 3\n\n// main 2\n", combined.toString());
    assertEquals("line 1:3 in 'a.efx'", lineMap.positionOf(1, 3));
    assertEquals("line 1:3 in 'b.efx'", lineMap.positionOf(2, 3));
    assertEquals("line 2:0 in 'a.efx'", lineMap.positionOf(3, 0));
    assertEquals("line 3:3 in 'a.efx'", lineMap.positionOf(4, 3));
    assertEquals("line 1:0", lineMap.positionOf(5, 0));
    assertEquals("line 2:3", lineMap.positionOf(6, 3));
  }

  @Test
  void testLineMap_IncludedFileWithoutLastLineEnd_KeepsItsLines() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("extra.efx", "// extra 1\n// extra 2");

    String input = "#include \"extra.efx\"\n// main 2\n";
    CombinedText combined =
        new IncludeProcessor(mapResolver(files)).resolve(CharStreams.fromString(input));
    LineMap lineMap = combined.getLineMap();

    assertEquals("// extra 1\n// extra 2\n\n// main 2\n", combined.toString());
    assertEquals("line 2:3 in 'extra.efx'", lineMap.positionOf(2, 3));
    assertEquals("line 1:0", lineMap.positionOf(3, 0));
    assertEquals("line 2:3", lineMap.positionOf(4, 3));
  }

  @Test
  void testLineMap_EmptyIncludedFile_TakesNoLine() throws IOException {
    Map<String, String> files = new HashMap<>();
    files.put("empty.efx", "");

    String input = "// main 1\n#include \"empty.efx\"\n// main 3\n";
    CombinedText combined =
        new IncludeProcessor(mapResolver(files)).resolve(CharStreams.fromString(input));
    LineMap lineMap = combined.getLineMap();

    assertEquals("// main 1\n\n// main 3\n", combined.toString());
    assertEquals("line 2:0", lineMap.positionOf(2, 0));
    assertEquals("line 3:3", lineMap.positionOf(3, 3));
  }
}
