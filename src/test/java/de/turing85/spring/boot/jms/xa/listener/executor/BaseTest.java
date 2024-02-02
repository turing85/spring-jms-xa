package de.turing85.spring.boot.jms.xa.listener.executor;

import java.time.Duration;
import java.util.Map;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

import com.google.common.truth.Truth;
import de.turing85.spring.boot.jms.xa.listener.MessageListener;
import io.restassured.RestAssured;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseTest {
  public static final String ARTEMIS_USERNAME = "admin";
  public static final String ARTEMIS_PASSWORD = "admin";

  private static final Logger TC_LOGGER = LoggerFactory.getLogger("\uD83D\uDC33");

  @Container
  @SuppressWarnings("resource")
  // @formatter:off
  protected static final GenericContainer<?> ARTEMIS =
      new GenericContainer<>(
          DockerImageName.parse("docker.io/apache/activemq-artemis:2.32.0-alpine"))
          .withEnv(Map.of(
              "ARTEMIS_USER", ARTEMIS_USERNAME,
              "ARTEMIS_PASSWORD", ARTEMIS_PASSWORD))
          .withExposedPorts(61616, 8161)
          .withLogConsumer(new Slf4jLogConsumer(TC_LOGGER)
              .withSeparateOutputStreams())
          .waitingFor(Wait
              .forHttp("/console")
              .forPort(8161)
              .forStatusCode(HttpStatus.OK.value()));
  // @formatter:on

  private static JmsTemplate JMS_TEMPLATE;

  @LocalServerPort
  int sutPort;

  @DynamicPropertySource
  static void artemisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.artemis.broker-url", BaseTest::constructBrokerUrl);
    registry.add("spring.artemis.user", () -> ARTEMIS_USERNAME);
    registry.add("spring.artemis.password", () -> ARTEMIS_PASSWORD);
  }

  @BeforeAll
  static void globalSetup() {
    ConnectionFactory factory =
        new ActiveMQConnectionFactory(constructBrokerUrl(), ARTEMIS_USERNAME, ARTEMIS_PASSWORD);
    JMS_TEMPLATE = new JmsTemplate(factory);
    JMS_TEMPLATE.setReceiveTimeout(Duration.ofMillis(200).toMillis());
  }

  @BeforeEach
  void setup() throws JMSException {
    RestAssured.baseURI = "http://localhost:%d".formatted(sutPort);
    stopService();
    serviceIsDown();

    emptyQueue();

    startService();
    serviceIsUp();
  }

  @Test
  @DisplayName("happy test, message should get consumed")
  void happyTest() {
    // GIVEN
    String message = "hello";

    // WHEN
    JMS_TEMPLATE.send(MessageListener.QUEUE, session -> session.createTextMessage(message));

    // THEN
    messageIsConsumedAfter(Duration.ofSeconds(5));
    serviceIsUp();
  }

  @Test
  @DisplayName("rollback test, message should not get consumed")
  void rollbackTest() throws JMSException {
    // GIVEN
    String message = "fail";

    // WHEN
    JMS_TEMPLATE.send(MessageListener.QUEUE, session -> session.createTextMessage(message));

    // THEN
    serviceIsDownAfter(Duration.ofSeconds(5));
    messageIsInQueue(message);
  }

  private static String constructBrokerUrl() {
    return "tcp://%s:%d".formatted(ARTEMIS.getHost(), ARTEMIS.getMappedPort(61616));
  }

  private void stopService() {
    // @formatter:off
    RestAssured
        .when().post("%s/%s".formatted(MessageListener.ROOT_PATH, MessageListener.STOP_PATH))
        .then().statusCode(is(HttpStatus.OK.value()));
    // @formatter:on
  }

  private void startService() {
    // @formatter:off
    RestAssured
        .when().post("%s/%s".formatted(MessageListener.ROOT_PATH, MessageListener.START_PATH))
        .then().statusCode(is(HttpStatus.OK.value()));
    // @formatter:on
  }

  private void emptyQueue() throws JMSException {
    Message received;
    while ((received = JMS_TEMPLATE.receive(MessageListener.QUEUE)) != null) {
      received.acknowledge();
    }
  }

  private void messageIsConsumedAfter(Duration duration) {
    // @formatter:off
    Awaitility.await()
        .atMost(duration)
        .untilAsserted(this::messageIsConsumed);
    // @formatter:on
  }

  private void messageIsConsumed() {
    Truth.assertThat(JMS_TEMPLATE.receive(MessageListener.QUEUE)).isNull();
  }

  private void messageIsInQueue(String message) throws JMSException {
    Message received = JMS_TEMPLATE.receive(MessageListener.QUEUE);
    Truth.assertThat(received).isNotNull();
    Truth.assertThat(received.getBody(String.class)).isEqualTo(message);
  }

  private void serviceIsUp() {
    // @formatter:off
    RestAssured
        .when().get("actuator/health")
        .then().statusCode(is(HttpStatus.OK.value()));
    // @formatter:on
  }

  private void serviceIsDownAfter(Duration duration) {
    // @formatter:off
    Awaitility.await()
        .atMost(duration)
        .untilAsserted(this::serviceIsDown);
    // @formatter:on
  }

  private void serviceIsDown() {
    // @formatter:off
    RestAssured
        .when().get("actuator/health")
        .then().statusCode(is(not(HttpStatus.OK.value())));
    // @formatter:on
  }
}
