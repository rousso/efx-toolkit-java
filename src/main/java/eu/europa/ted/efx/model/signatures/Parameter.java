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
package eu.europa.ted.efx.model.signatures;

/**
 * A parameter: a name, and the type an argument given for it must have. A function or template
 * declared in EFX has the parameters parsed with it; a built-in has those its signatures declare.
 */
public interface Parameter {

  /**
   * @return the name of the parameter
   */
  String getName();

  /**
   * @return the type an argument given for this parameter must have
   */
  ParameterType getType();
}
