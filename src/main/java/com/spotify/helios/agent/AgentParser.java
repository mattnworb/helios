package com.spotify.helios.agent;

import com.spotify.helios.common.ServiceParser;

import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import static com.google.common.base.Throwables.propagate;

public class AgentParser extends ServiceParser {

  private final AgentConfig agentConfig;

  public AgentParser(final String... args) throws ArgumentParserException {
    super("helios-agent", "Spotify Helios Agent", args);

    final Namespace options = getNamespace();
    final String uriString = options.getString("docker");
    try {
      new URI(uriString);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Bad docker endpoint: " + uriString, e);
    }

    final String name = options.getString("name");

    agentConfig = new AgentConfig()
        .setName(name)
        .setZooKeeperConnectionString(options.getString("zk"))
        .setSite(options.getString("site"))
        .setMuninReporterPort(options.getInt("munin_port"))
        .setDockerEndpoint(options.getString("docker"));
  }

  protected void addArgs(final ArgumentParser parser) {

    parser.addArgument("--name")
        .setDefault(getHostName())
        .help("agent name");

    parser.addArgument("--munin-port")
        .type(Integer.class)
        .setDefault(4952)
        .help("munin port (0 = disabled)");

    parser.addArgument("--docker")
        .setDefault("http://localhost:4160")
        .help("docker endpoint");
  }

  private static String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw propagate(e);
    }
  }

  public AgentConfig getAgentConfig() {
    return agentConfig;
  }

}