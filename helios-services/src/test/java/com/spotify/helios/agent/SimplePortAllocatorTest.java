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
import com.google.common.collect.ImmutableSet;

import com.spotify.helios.common.descriptors.JobId;
import com.spotify.helios.common.descriptors.PortMapping;

import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class SimplePortAllocatorTest {

  private final JobId jobId = new JobId("empty", "0");

  @Test
  public void testAllocate() throws Exception {
    final PortAllocator sut = new SimplePortAllocator(20000, 20010);
    final Map<String, PortMapping> mapping = ImmutableMap.of("p1", PortMapping.of(17),
                                                             "p2", PortMapping.of(18, 18));
    final Set<Integer> used = ImmutableSet.of(10, 11);
    final Map<String, Integer> allocation = sut.allocate(jobId, mapping, used);
    assertThat(allocation, hasEntry(is("p1"),
                                    allOf(greaterThanOrEqualTo(20000), lessThanOrEqualTo(20010))));
    assertThat(allocation, hasEntry("p2", 18));
  }

  /**
   * Tests that a SimplePortAllocator with given start/end range returns assignments in an
   * expected order.
   * While this test is tied too closely to the implementation inside the class, it is useful for
   * testing that refactorings of SimplePortAllocator do not change behavior.
   */
  @Test
  public void testExpectedOrder() throws Exception {
    final int start = 20000;
    final int end = 20010;
    final PortAllocator allocator = new SimplePortAllocator(start, end);
    final Map<String, PortMapping> mapping = ImmutableMap.of("dynamic", PortMapping.of(17),
                                                             "static", PortMapping.of(18, 18));

    final Set<Integer> used = new HashSet<>();
    final IntStream expectedAllocations = IntStream.range(start, end);

    expectedAllocations.forEach(expectedDynamicPort -> {
      final Map<String, Integer> allocation = allocator.allocate(jobId, mapping, used);

      assertThat(allocation, hasEntry("static", 18));

      final int dynamicPort = allocation.get("dynamic");
      assertThat(expectedDynamicPort, is(dynamicPort));

      used.add(dynamicPort);
    });

    //next allocation = out of space
    assertThat(allocator.allocate(jobId, mapping, used), nullValue());
  }

  @Test
  public void testInsufficientPortsFail1() throws Exception {
    final PortAllocator sut = new SimplePortAllocator(10, 11);
    final Map<String, PortMapping> mapping = ImmutableMap.of("p1", PortMapping.of(17),
                                                             "p2", PortMapping.of(18, 18));
    final Set<Integer> used = ImmutableSet.of(10, 11);
    final Map<String, Integer> allocation = sut.allocate(jobId, mapping, used);
    assertNull(allocation);
  }

  @Test
  public void testInsufficientPortsFail2() throws Exception {
    final PortAllocator sut = new SimplePortAllocator(10, 11);
    final Map<String, PortMapping> mapping = ImmutableMap.of("p1", PortMapping.of(1),
                                                             "p2", PortMapping.of(2),
                                                             "p3", PortMapping.of(4),
                                                             "p4", PortMapping.of(18, 18));
    final Set<Integer> used = ImmutableSet.of();
    final Map<String, Integer> allocation = sut.allocate(jobId, mapping, used);
    assertNull(allocation);
  }
}
