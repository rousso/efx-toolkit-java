package eu.europa.ted.efx.model.constructs;

import java.util.Objects;

import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * An argument of a call: the value given for a parameter, with that parameter.
 *
 * <p>An argument of a built-in is paired with the parameter of its overload, {@code T} bound, and
 * its value is converted to that parameter. An argument of a function or a template declared in
 * EFX, or a value a template block passes down, is paired with the declared parameter.</p>
 *
 * @param <P> the kind of parameter: declared in the catalogue, or parsed with a declaration
 */
public class ParsedArgument<P extends Parameter> {

    public final P parameter;

    public final TypedExpression value;

    /**
     * Pairs a value with the parameter it is given for.
     *
     * @param parameter the parameter
     * @param value the value given for it
     */
    public ParsedArgument(P parameter, TypedExpression value) {
        this.parameter = parameter;
        this.value = value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameter, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParsedArgument<?> other = (ParsedArgument<?>) obj;
        return Objects.equals(parameter, other.parameter) && Objects.equals(value, other.value);
    }
}
