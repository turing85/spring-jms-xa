package de.turing85.spring.boot.jms.xa.config;

import java.util.concurrent.Executors;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.boot.jms.XAConnectionFactoryWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.lang.NonNull;
import org.springframework.transaction.jta.JtaTransactionManager;

@Configuration
public class BeanProvider {
  public static final String JMS_FACTORY = "jmsFactory";

  @Bean(JMS_FACTORY)
  public DefaultJmsListenerContainerFactory jmsFactory(
      @Value("${jms-factory.concurrent-consumers}") int concurrentConsumers,
      @Value("${jms-factory.thread-pool-executor.enabled}") boolean useThreadPoolExecutor,
      @Value("${jms-factory.thread-pool-executor.num-threads:1}") int numThreads,
      DefaultJmsListenerContainerFactoryConfigurer configurer,
      @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") XAConnectionFactoryWrapper connectionFactoryWrapper,
      ActiveMQConnectionFactory connectionFactory, JtaTransactionManager jtaTransactionManager)
      throws Exception {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory() {
      @Override
      @NonNull
      protected DefaultMessageListenerContainer createContainerInstance() {
        DefaultMessageListenerContainer consumer = new DefaultMessageListenerContainer();
        consumer.setConcurrentConsumers(concurrentConsumers);
        consumer.setTransactionManager(jtaTransactionManager);
        return consumer;
      }
    };
    configurer.configure(factory,
        connectionFactoryWrapper.wrapConnectionFactory(connectionFactory));
    if (useThreadPoolExecutor) {
      factory.setTaskExecutor(Executors.newFixedThreadPool(numThreads));
    }
    return factory;
  }
}
