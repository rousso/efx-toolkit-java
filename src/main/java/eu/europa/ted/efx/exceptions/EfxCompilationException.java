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

import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

/**
 * Abstract base class for all EFX compilation exceptions that represent user errors in EFX code.
 * Extends ParseCancellationException to properly stop ANTLR4 parsing and bypass error recovery
 * mechanisms.
 *
 * <p>An error has a position in the EFX source when it is raised with the parse context it is
 * about, or when it is placed there afterwards with {@link #at}. Its message starts with that
 * position, as "line X:Y ", or as "line X:Y in 'file' " when the EFX source was read from a file
 * with a name, such as a file included with {@code #include}.
 */
@SuppressWarnings("squid:MaximumInheritanceDepth")
public abstract class EfxCompilationException extends ParseCancellationException {

    /**
     * Where the EFX source the error is about starts, as "line X:Y" or "line X:Y in 'file'"; null
     * while the error has no position.
     */
    private String position;

    protected EfxCompilationException(String template, Object... args) {
        super(args.length > 0 ? String.format(template, args) : template);
    }

    protected EfxCompilationException(ParserRuleContext ctx, String template, Object... args) {
        super(String.format(template, args));
        this.position = positionOf(ctx.getStart());
    }

    /**
     * Places an error that has no position yet at the start of the given parse context. An error
     * that has a position keeps it.
     *
     * @param ctx the parse context being translated when the error was raised
     * @return this error
     */
    public EfxCompilationException at(final ParserRuleContext ctx) {
        return this.at(ctx.getStart());
    }

    /**
     * Places an error that has no position yet at the given token. An error that has a position
     * keeps it.
     *
     * @param token the token the error is about
     * @return this error
     */
    public EfxCompilationException at(final Token token) {
        if (this.position == null) {
            this.position = positionOf(token);
        }
        return this;
    }

    /**
     * @return the message, after the position of the error when it has one
     */
    @Override
    public String getMessage() {
        if (this.position == null) {
            return super.getMessage();
        }
        return this.position + " " + super.getMessage();
    }

    private static String positionOf(final Token token) {
        return positionOf(token.getLine(), token.getCharPositionInLine(), token.getInputStream());
    }

    /**
     * Writes a position in the EFX source, as an error message starts with it.
     *
     * @param line the line, counted from 1
     * @param column the column, counted from 0
     * @param source the text that holds the position
     * @return the position as "line X:Y", followed by " in 'file'" when the text was read from a
     *         file with a name
     */
    static String positionOf(final int line, final int column, final IntStream source) {
        final String position = String.format("line %d:%d", line, column);
        if (source == null || IntStream.UNKNOWN_SOURCE_NAME.equals(source.getSourceName())) {
            return position;
        }
        return String.format("%s in '%s'", position, source.getSourceName());
    }
}
