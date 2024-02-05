package de.turing85.spring.boot.jms.xa.listener.executor;

import java.time.Duration;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

import com.google.common.truth.Truth;
import de.turing85.spring.boot.jms.xa.listener.MessageListener;
import de.turing85.spring.boot.jms.xa.listener.executor.extension.ArtemisContainerExtension;
import io.restassured.RestAssured;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ExtendWith({ArtemisContainerExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseTest {

  private static JmsTemplate JMS_TEMPLATE;

  @LocalServerPort
  int sutPort;

  @DynamicPropertySource
  static void artemisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.artemis.broker-url", ArtemisContainerExtension::constructBrokerUrl);
    registry.add("spring.artemis.user", () -> ArtemisContainerExtension.USERNAME);
    registry.add("spring.artemis.password", () -> ArtemisContainerExtension.PASSWORD);
  }

  @BeforeAll
  static void globalSetup() {
    ConnectionFactory factory =
        new ActiveMQConnectionFactory(ArtemisContainerExtension.constructBrokerUrl(),
            ArtemisContainerExtension.USERNAME, ArtemisContainerExtension.PASSWORD);
    JMS_TEMPLATE = new JmsTemplate(factory);
    JMS_TEMPLATE.setReceiveTimeout(Duration.ofMillis(200).toMillis());
  }

  @BeforeEach
  void setup() throws JMSException {
    RestAssured.baseURI = "http://localhost:%d".formatted(sutPort);
    stopService();
    assertThatServiceIsDown();

    emptyQueue();

    startService();
    assertThatServiceIsUp();
  }

  @Test
  @DisplayName("happy test, message should get consumed")
  void happyTest() {
    // GIVEN
    String message = "hello";

    // WHEN
    JMS_TEMPLATE.send(MessageListener.QUEUE, session -> session.createTextMessage(message));

    // THEN
    assertThatMessageIsConsumedAfter(Duration.ofSeconds(5));
    assertThatServiceIsUp();
  }

  @Test
  @DisplayName("rollback test, message should not get consumed")
  void rollbackTest() throws JMSException {
    // GIVEN
    String message = "fail";

    // WHEN
    JMS_TEMPLATE.send(MessageListener.QUEUE, session -> session.createTextMessage(message));

    // THEN
    assertThatServiceIsDownAfter(Duration.ofSeconds(5));
    assertThatMessageIsInQueue(message);
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

  private void assertThatMessageIsConsumedAfter(Duration duration) {
    // @formatter:off
    Awaitility.await()
        .atMost(duration)
        .untilAsserted(this::assertThatMessageIsConsumed);
    // @formatter:on
  }

  private void assertThatMessageIsConsumed() {
    Truth.assertThat(JMS_TEMPLATE.receive(MessageListener.QUEUE)).isNull();
  }

  private void assertThatMessageIsInQueue(String message) throws JMSException {
    Message received = JMS_TEMPLATE.receive(MessageListener.QUEUE);
    Truth.assertThat(received).isNotNull();
    Truth.assertThat(received.getBody(String.class)).isEqualTo(message);
  }

  private void assertThatServiceIsUp() {
    // @formatter:off
    RestAssured
        .when().get("actuator/health")
        .then().statusCode(is(HttpStatus.OK.value()));
    // @formatter:on
  }

  private void assertThatServiceIsDownAfter(Duration duration) {
    // @formatter:off
    Awaitility.await()
        .atMost(duration)
        .untilAsserted(this::assertThatServiceIsDown);
    // @formatter:on
  }

  private void assertThatServiceIsDown() {
    // @formatter:off
    RestAssured
        .when().get("actuator/health")
        .then().statusCode(is(not(HttpStatus.OK.value())));
    // @formatter:on
  }
}
