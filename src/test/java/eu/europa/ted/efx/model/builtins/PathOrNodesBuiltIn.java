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
package eu.europa.ted.efx.model.builtins;

import static eu.europa.ted.efx.model.constructs.Overload.overload;
import static eu.europa.ted.efx.model.constructs.Parameter.Impl.parameter;
import static eu.europa.ted.efx.model.constructs.ParameterType.NODES;
import static eu.europa.ted.efx.model.constructs.ParameterType.PATH;
import static eu.europa.ted.efx.model.constructs.Signature.signature;

import java.util.List;

import eu.europa.ted.efx.model.constructs.Overload;

/**
 * A built-in with two overloads that take a path, {@code (PATH)} and {@code (NODES)}: both apply
 * to a path that selects nodes, and the second, the more specific, is the one chosen, in either
 * order of declaration. Kept to prove that the resolution compares path parameters.
 */
public enum PathOrNodesBuiltIn implements BuiltIn {

  PATH_FIRST(true),

  NODES_FIRST(false);

  private final Overload anyPath =
      overload(signature(parameter("reference", PATH)).returning(PATH), (script, path) -> path);

  private final Overload nodes =
      overload(signature(parameter("nodes", NODES)).returning(NODES), (script, nodes) -> nodes);

  private final List<Overload> overloads;

  PathOrNodesBuiltIn(final boolean pathFirst) {
    this.overloads =
        pathFirst ? List.of(this.anyPath, this.nodes) : List.of(this.nodes, this.anyPath);
  }

  /**
   * @return the overload that takes nodes, the more specific
   */
  public Overload getNodesOverload() {
    return this.nodes;
  }

  @Override
  public String getName() {
    return "path or nodes";
  }

  @Override
  public List<Overload> getOverloads() {
    return this.overloads;
  }
}
