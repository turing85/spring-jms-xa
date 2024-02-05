package de.turing85.spring.boot.jms.xa.listener.executor.threadpool.threads.ten;

import de.turing85.spring.boot.jms.xa.listener.executor.BaseTest;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("10 consumers, thread pool task executor, 10 threads")
class WithTenConsumersAndThreadPoolTenThreadsTest extends BaseTest {
  @DynamicPropertySource
  static void jmsFactoryProperties(DynamicPropertyRegistry registry) {
    registry.add("jms-factory.concurrent-consumers", () -> 10);
    registry.add("jms-factory.thread-pool-executor.enabled", () -> "true");
    registry.add("jms-factory.thread-pool-executor.num-threads", () -> "10");
  }
}
