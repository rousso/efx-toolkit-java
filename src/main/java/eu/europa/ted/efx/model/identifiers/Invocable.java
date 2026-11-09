package eu.europa.ted.efx.model.identifiers;

import java.util.List;

import eu.europa.ted.efx.model.constructs.Construct;
import eu.europa.ted.efx.model.constructs.Parameters;
import eu.europa.ted.efx.model.types.EfxDataType;

/**
 * An identifier that EFX source declares with parameters and invokes with arguments: a function,
 * invoked as {@code ?name(...)}, or a template, invoked as {@code invoke name(...)}.
 *
 * <p>An invocable is a {@link Construct} with one parameter list, its declared parameters, so
 * that an invocation of it is checked as a call of a built-in is. The caller translates the
 * invocation from the converted arguments.</p>
 */
public abstract class Invocable extends Identifier implements Construct {

    public final ParsedParameters parameters;

    public Invocable(String name, Class<? extends EfxDataType> returnType,
            ParsedParameters parameters) {
        super(name, returnType);
        this.parameters = parameters;
    }

    /**
     * @return the declared parameters, the only list a call is checked against
     */
    @Override
    public List<Parameters<?>> getParameterLists() {
        return List.of(this.parameters);
    }

    /**
     * @return the arguments of a call by their position, as a function's: "argument", or
     *         "1st argument", "2nd argument" and so on
     */
    @Override
    public String roleOf(int position, int count) {
        return Construct.argumentRole(position, count);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        Invocable other = (Invocable) o;
        return parameters != null ? parameters.equals(other.parameters) : other.parameters == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (parameters != null ? parameters.hashCode() : 0);
        return result;
    }
}