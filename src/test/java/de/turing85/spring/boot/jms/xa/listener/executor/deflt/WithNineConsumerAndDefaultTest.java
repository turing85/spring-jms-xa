package de.turing85.spring.boot.jms.xa.listener.executor.deflt;

import de.turing85.spring.boot.jms.xa.listener.executor.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("9 consumers, default task executor")
class WithNineConsumerAndDefaultTest extends BaseTest {
  @DynamicPropertySource
  static void jmsFactoryProperties(DynamicPropertyRegistry registry) {
    registry.add("jms-factory.concurrent-consumers", () -> "9");
    registry.add("jms-factory.use-thread-pool-executor", () -> "false");
  }
}
