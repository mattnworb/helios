/*
 * Copyright (c) 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.agent;

import com.spotify.helios.common.descriptors.PortMapping;

import java.util.Map;
import java.util.Set;

public interface PortAllocator {

  /**
   * Allocate ports for port mappings with no external ports configured.
   *
   * @param ports A mutable map of port mappings for a container, both with statically configured
   *              external ports and dynamic unconfigured external ports.
   * @param used  A mutable set of used ports. The ports allocated will not clash with these ports.
   * @return The allocated ports.
   */
  Map<String, Integer> allocate(Map<String, PortMapping> ports,
                                Set<Integer> used);
}
