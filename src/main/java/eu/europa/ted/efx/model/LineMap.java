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
package eu.europa.ted.efx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps each line of the combined text of a file back to the file and the line it was written on.
 *
 * <p>The combined text of a file is its text with each {@code #include} directive replaced by the
 * combined text of the file it names. The line map of a file holds an {@link Insert} for each of
 * its directives, and each insert holds the line map of the file it inserted. Columns are not
 * mapped: an inserted text starts on a line of its own, so each line keeps its columns. The line
 * map of a text that includes no file maps each line to itself.</p>
 */
public final class LineMap {

  /** The inserts, in the order of their lines. */
  private final List<Insert> inserts = new ArrayList<>();

  /**
   * Adds an insert after those added before it.
   *
   * @param line the line of the combined text where the inserted text starts, counted from 1
   * @param file the file inserted, as its {@code #include} directive names it
   * @param lineCount how many lines of the combined text the inserted text takes
   * @param lineMap the line map of the file inserted
   */
  public void addInsert(final int line, final String file, final int lineCount,
      final LineMap lineMap) {
    this.inserts.add(new Insert(line, file, lineCount, lineMap));
  }

  /**
   * Writes a position in the combined text as the position where it was written, as an error
   * message starts with it.
   *
   * @param line the line of the combined text, counted from 1
   * @param column the column, counted from 0
   * @return the position as "line X:Y", followed by " in 'file'" when it was written in an included
   *         file
   */
  public String positionOf(final int line, final int column) {
    return this.positionOf(line, column, null);
  }

  /**
   * @param line the line of the combined text, counted from 1
   * @param column the column, counted from 0
   * @param file the file this line map is of, as its {@code #include} directive names it; null for
   *        the file the translation reads
   * @return the position as {@link #positionOf(int, int)} writes it
   */
  private String positionOf(final int line, final int column, final String file) {
    int insertedLines = 0;
    for (final Insert insert : this.inserts) {
      if (line < insert.line) {
        break;
      }
      if (line < insert.line + insert.lineCount) {
        return insert.lineMap.positionOf(line - insert.line + 1, column, insert.file);
      }
      insertedLines += insert.lineCount;
    }
    final String position = String.format("line %d:%d", line - insertedLines, column);
    return file == null ? position : String.format("%s in '%s'", position, file);
  }

  /**
   * The combined text of an included file, in the place of the {@code #include} directive that
   * names it.
   */
  private static final class Insert {

    private final int line;
    private final String file;
    private final int lineCount;
    private final LineMap lineMap;

    private Insert(final int line, final String file, final int lineCount,
        final LineMap lineMap) {
      this.line = line;
      this.file = file;
      this.lineCount = lineCount;
      this.lineMap = lineMap;
    }
  }
}
