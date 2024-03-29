package de.turing85.spring.boot.jms.xa.listener.executor.extension;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Slf4j
public class ArtemisContainerExtension
    implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {
  public static final String USERNAME = "admin";
  public static final String PASSWORD = "admin";

  private static final Logger TC_LOGGER = LoggerFactory.getLogger("\uD83D\uDC33");

  // @formatter:off
  @SuppressWarnings("resource")
  private static final GenericContainer<?> ARTEMIS = new GenericContainer<>(DockerImageName
      .parse("docker.io/apache/activemq-artemis:2.32.0-alpine"))
      .withEnv("ARTEMIS_USER", USERNAME)
      .withEnv("ARTEMIS_PASSWORD", PASSWORD)
      .withCopyToContainer(
          MountableFile.forClasspathResource("artemis/var/lib/artemis-instance/etc-override/broker.xml", 0b110_100_100),
          "/var/lib/artemis-instance/etc-override/broker.xml")
      .withExposedPorts(61616, 8161)
      .withLogConsumer(new Slf4jLogConsumer(TC_LOGGER).withSeparateOutputStreams())
      .waitingFor(Wait
          .forHttp("/console")
          .forPort(8161)
          .forStatusCode(HttpStatus.OK.value()));
  // @formatter:on

  public static String constructBrokerUrl() {
    return "tcp://%s:%d".formatted(ARTEMIS.getHost(), ARTEMIS.getMappedPort(61616));
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) {
    if (!ARTEMIS.isRunning()) {
      extensionContext.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
          .put(ArtemisContainerExtension.class, this);
      ARTEMIS.start();
    }
  }

  @Override
  public void close() {
    if (ARTEMIS.isRunning()) {
      ARTEMIS.stop();
    }
  }
}
