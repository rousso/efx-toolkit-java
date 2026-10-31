/*
 * Copyright 2025 European Union
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
package eu.europa.ted.efx.model.variables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.europa.ted.efx.model.ParsedEntity;
import eu.europa.ted.efx.model.expressions.TypedExpression;

/**
 * The arguments of an invocation of a declared function or template, collected in order as they
 * are translated. Collecting checks nothing: once the invocation is complete, the translator
 * matches the arguments against the parameters of the declaration, which checks their number and
 * their types.
 */
public class StrictArguments implements ParsedEntity {

    public final transient Parametrised identifier;

    private final List<TypedExpression> values = new ArrayList<>();

    public StrictArguments(Parametrised identifier) {
        this.identifier = identifier;
    }

    /**
     * Collects the next argument of the invocation.
     *
     * @param argument the translated argument
     */
    public void addArgument(TypedExpression argument) {
        this.values.add(argument);
    }

    /**
     * @return the arguments collected so far, in order
     */
    public List<TypedExpression> getValues() {
        return Collections.unmodifiableList(this.values);
    }
}
