package eu.europa.ted.efx.model.identifiers;

import eu.europa.ted.efx.model.constructs.Spelling;
import eu.europa.ted.efx.model.types.EfxDataType;

public class Template extends Invocable {

    public Template(String name, ParsedParameters parameters) {
        super(name, EfxDataType.VOID, parameters);
    }

    /**
     * @return the template as {@code invoke} writes it, by its name
     */
    @Override
    public Spelling getSpelling() {
        return Spelling.template(this.name);
    }
}