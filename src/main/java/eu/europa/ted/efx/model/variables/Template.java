package eu.europa.ted.efx.model.variables;

import eu.europa.ted.efx.model.types.EfxDataType;

public class Template extends Parametrised {

    public Template(String name, ParsedParameters parameters) {
        super(name, EfxDataType.VOID, parameters);
    }

    /**
     * @return "Template 'greeting'"
     */
    @Override
    public String describe() {
        return "Template '" + this.name + "'";
    }
}