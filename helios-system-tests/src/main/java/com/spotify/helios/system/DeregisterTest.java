/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package com.spotify.helios.system;

import com.google.common.collect.ImmutableMap;

import com.spotify.helios.TemporaryPorts;
import com.spotify.helios.agent.AgentMain;
import com.spotify.helios.client.HeliosClient;
import com.spotify.helios.common.descriptors.Deployment;
import com.spotify.helios.common.descriptors.HostStatus;
import com.spotify.helios.common.descriptors.Job;
import com.spotify.helios.common.descriptors.JobId;
import com.spotify.helios.common.descriptors.PortMapping;
import com.spotify.helios.common.protocol.CreateJobResponse;
import com.spotify.helios.common.protocol.HostDeregisterResponse;
import com.spotify.helios.common.protocol.JobDeleteResponse;
import com.spotify.helios.common.protocol.JobDeployResponse;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

import static com.spotify.helios.common.descriptors.Goal.START;
import static com.spotify.helios.common.descriptors.HostStatus.Status.DOWN;
import static com.spotify.helios.common.descriptors.HostStatus.Status.UP;
import static com.spotify.helios.common.descriptors.TaskStatus.State.RUNNING;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class DeregisterTest extends SystemTestBase {

  @Rule public final TemporaryPorts ports = TemporaryPorts.create();

  @Test
  public void testDeregisterHostThatDoesntExist() throws Exception {
    startDefaultMaster();
    final String host = testHost();
    final HeliosClient client = defaultClient();

    final HostDeregisterResponse deregisterResponse = client.deregisterHost(host).get();
    assertEquals(HostDeregisterResponse.Status.NOT_FOUND, deregisterResponse.getStatus());
  }

  @Test
  public void testDeregister() throws Exception {
    startDefaultMaster();
    final String host = testHost();
    final AgentMain agent = startDefaultAgent(host);

    final HeliosClient client = defaultClient();

    // Create a job
    final Job job = Job.newBuilder()
        .setName(testJobName)
        .setVersion(testJobVersion)
        .setImage(BUSYBOX)
        .setCommand(IDLE_COMMAND)
        .setPorts(ImmutableMap.of("foo", PortMapping.of(4711),
                                  "bar", PortMapping.of(4712, ports.localPort("bar"))))
        .build();
    final JobId jobId = job.getId();
    final CreateJobResponse created = client.createJob(job).get();
    assertEquals(CreateJobResponse.Status.OK, created.getStatus());

    // Wait for agent to come up
    awaitHostRegistered(client, host, LONG_WAIT_SECONDS, SECONDS);
    awaitHostStatus(client, host, UP, LONG_WAIT_SECONDS, SECONDS);

    // Deploy the job on the agent
    final Deployment deployment = Deployment.of(jobId, START);
    final JobDeployResponse deployed = client.deploy(deployment, host).get();
    assertEquals(JobDeployResponse.Status.OK, deployed.getStatus());

    // Wait for the job to run
    awaitJobState(client, host, jobId, RUNNING, LONG_WAIT_SECONDS, SECONDS);

    // Kill off agent
    agent.stopAsync().awaitTerminated();

    // Deregister agent
    final HostDeregisterResponse deregisterResponse = client.deregisterHost(host).get();
    assertEquals(HostDeregisterResponse.Status.OK, deregisterResponse.getStatus());

    // Verify that it's possible to remove the job
    final JobDeleteResponse deleteResponse = client.deleteJob(jobId).get();
    assertEquals(JobDeleteResponse.Status.OK, deleteResponse.getStatus());
  }

  @Test
  public void testRegistrationResolution() throws Exception {
    startDefaultMaster();
    final String host = testHost();
    AgentMain agent = startDefaultAgent(host, "--labels", "num=1");

    final HeliosClient client = defaultClient();

    // Wait for agent to come up
    // We wait because LabelReporter only reports labels once every second. :(
    Thread.sleep(1000);
    awaitHostRegistered(client, host, LONG_WAIT_SECONDS, SECONDS);
    final HostStatus hostStatus1 = awaitHostStatus(client, host, UP, LONG_WAIT_SECONDS, SECONDS);
    assertThat(hostStatus1.getLabels(), Matchers.hasEntry("num", "1"));

    // Kill off agent
    agent.stopAsync().awaitTerminated();
    awaitHostStatus(client, host, DOWN, LONG_WAIT_SECONDS, SECONDS);

    // Start a new agent with the same hostname but have it generate a different ID
    resetAgentStateDir();

    // Set TTL to 0 so new agent will deregister previous one.
    startDefaultAgent(testHost(), "--zk-registration-ttl", "0");

    // Check that the new host is registered
    awaitHostRegistered(client, host, LONG_WAIT_SECONDS, SECONDS);
    final HostStatus hostStatus2 = awaitHostStatus(client, host, UP, LONG_WAIT_SECONDS, SECONDS);
    assertThat(hostStatus2.getLabels(), not(Matchers.hasEntry("num", "1")));
  }

  @Test
  public void testRegistrationResolutionTtlNotExpired() throws Exception {
    startDefaultMaster();
    final String host = testHost() + "2";
    AgentMain agent = startDefaultAgent(host);

    final HeliosClient client = defaultClient();

    // Wait for agent to come up
    awaitHostRegistered(client, host, LONG_WAIT_SECONDS, SECONDS);
    awaitHostStatus(client, host, UP, LONG_WAIT_SECONDS, SECONDS);

    // Kill off agent
    agent.stopAsync().awaitTerminated();
    awaitHostStatus(client, host, DOWN, LONG_WAIT_SECONDS, SECONDS);

    // Start a new agent with the same hostname but have it generate a different ID
    resetAgentStateDir();
    // Set TTL to a large number so new agent will not deregister previous one.
    startDefaultAgent(testHost(), "--zk-registration-ttl", "9999");
    awaitHostRegistered(client, host, 10, SECONDS);

    // Check that the new host didn't register
    final String output = cli("hosts", host, "--status", "UP");
    assertThat(output, not(Matchers.containsString(host)));
  }
}
