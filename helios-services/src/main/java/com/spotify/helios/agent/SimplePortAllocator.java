/*
 * Copyright (c) 2014 Spotify AB.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import com.spotify.helios.common.descriptors.JobId;
import com.spotify.helios.common.descriptors.PortMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Set;

/**
 * A simple port allocator. Given a port range and a set of used ports it will linearly search
 * through the port range until it finds an available port and claim it.
 *
 * The index into the port range is kept between calls to {@link #allocate(JobId, Map, Set)}.
 * Successive allocations will not reuse an available port until the port range has been exhausted
 * and the index wraps around from the start of the port range.
 */
public class SimplePortAllocator implements PortAllocator {

  private static final Logger log = LoggerFactory.getLogger(Agent.class);

  /**
   * Index for port allocation. Reused between allocations so we do not immediately reuse ports.
   */
  private int i;

  private final int start;
  private final int end;

  public SimplePortAllocator(final int start, final int end) {
    this.start = start;
    this.end = end;
    this.i = start;
  }

  @Override
  public Map<String, Integer> allocate(final JobId jobId, final Map<String, PortMapping> ports,
                                       final Set<Integer> used) {
    return allocate0(ports, Sets.newHashSet(used));
  }

  private Map<String, Integer> allocate0(final Map<String, PortMapping> mappings,
                                         final Set<Integer> used) {

    final ImmutableMap.Builder<String, Integer> allocation = ImmutableMap.builder();

    // Allocate static ports
    for (Map.Entry<String, PortMapping> entry : mappings.entrySet()) {
      final String name = entry.getKey();
      final PortMapping portMapping = entry.getValue();
      final Integer externalPort = portMapping.getExternalPort();

      // Skip dynamic ports
      if (externalPort == null) {
        continue;
      }

      // Verify that this port is not in use
      if (used.contains(externalPort)) {
        return null;
      }
      used.add(externalPort);
      allocation.put(name, externalPort);
    }

    // Allocate dynamic ports
    for (Map.Entry<String, PortMapping> entry : mappings.entrySet()) {
      final String name = entry.getKey();
      final PortMapping portMapping = entry.getValue();
      final Integer externalPort = portMapping.getExternalPort();

      // Skip static ports
      if (externalPort != null) {
        continue;
      }

      // Look for an available port, checking at most (end - start) ports.
      Integer port = null;
      for (int i = start; i < end; i++) {
        final int candidate = next();
        if (!used.contains(candidate) && portAvailable(candidate)) {
          port = candidate;
          break;
        }
      }
      if (port == null) {
        return null;
      }
      used.add(port);
      allocation.put(name, port);
    }

    return allocation.build();
  }

  /**
   * Get the next port number to try, continuing from the previous port allocation to avoid eagerly
   * reusing ports. Wraps around when the end of the port range has been reached.
   *
   * @return The next port.
   */
  private int next() {
    if (i == end) {
      i = start;
    }
    return i++;
  }

  /**
   * Check if the port is available on the host. This is racy but it's better than nothing.
   * @param port Port number to check.
   * @return True if port is available. False otherwise.
   */
  private boolean portAvailable(final int port) {
    boolean available = false;

    ServerSocket s = null;
    try {
      s = new ServerSocket(port);
      available = true;
    } catch (IOException ignored) {
    } finally {
      if (s != null) {
        try {
          s.close();
        } catch (IOException e) {
          log.error("Couldn't close socket on port {} when checking availability: {}", port, e);
        }
      }
    }

    return available;
  }
}
