package de.turing85.spring.boot.jms.xa.listener;

import de.turing85.spring.boot.jms.xa.config.BeanProvider;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(MessageListener.ROOT_PATH)
@Log4j2
public class MessageListener implements HealthIndicator {
  public static final String QUEUE = "queue";
  public static final String ROOT_PATH = "listener";
  public static final String START_PATH = "start";
  public static final String STOP_PATH = "stop";

  private final TaskExecutor executor;
  private final JmsListenerEndpointRegistry registry;

  public MessageListener(
      @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") TaskExecutor executor,
      ApplicationContext context) {
    this.executor = executor;
    registry = context.getBean(JmsListenerEndpointRegistry.class);
  }

  @PostMapping(START_PATH)
  void startListener() {
    if (!registry.isRunning()) {
      registry.start();
    }
  }

  @PostMapping(STOP_PATH)
  void stopListener() {
    if (registry.isRunning()) {
      registry.stop();
    }
  }

  @JmsListener(destination = QUEUE, containerFactory = BeanProvider.JMS_FACTORY)
  @Transactional
  @SuppressWarnings("unused")
  public void receiveMessage(String message) {
    log.info("Received message \"{}\"", message);
    if (message.contains("fail")) {
      executor.execute(registry::stop);
      throw new RuntimeException("ouch");
    }
  }

  @Override
  public Health health() {
    if (isUp()) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }

  private boolean isUp() {
    return registry.isRunning();
  }
}
