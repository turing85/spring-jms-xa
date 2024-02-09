package de.turing85.spring.boot.jms.xa.config;

import java.util.concurrent.Executors;

import jakarta.jms.ConnectionFactory;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;

@Configuration
public class BeanProvider {
  public static final String JMS_FACTORY = "jmsFactory";

  @Bean(name = "userTransaction")
  public UserTransactionImp userTransaction() throws SystemException {
    UserTransactionImp userTransactionImp = new UserTransactionImp();
    userTransactionImp.setTransactionTimeout(10000);
    return userTransactionImp;
  }

  @Bean(initMethod = "init", destroyMethod = "close")
  public UserTransactionManager transactionManager() {
    UserTransactionManager userTransactionManager = new UserTransactionManager();
    userTransactionManager.setForceShutdown(false);
    return userTransactionManager;
  }

  @Bean
  public PlatformTransactionManager platformTransactionManager(UserTransactionImp userTransaction,
      TransactionManager transactionManager) {
    return new JtaTransactionManager(userTransaction, transactionManager);
  }

  @Bean(JMS_FACTORY)
  public DefaultJmsListenerContainerFactory jmsFactory(
      @Value("${jms-factory.concurrent-consumers}") int concurrentConsumers,
      @Value("${jms-factory.thread-pool-executor.enabled}") boolean useThreadPoolExecutor,
      @Value("${jms-factory.thread-pool-executor.num-threads:1}") int numThreads,
      DefaultJmsListenerContainerFactoryConfigurer configurer,
      @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") ConnectionFactory connectionFactory,
      PlatformTransactionManager platformTransactionManager) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory() {
      @Override
      @NonNull
      protected DefaultMessageListenerContainer createContainerInstance() {
        DefaultMessageListenerContainer consumer = new DefaultMessageListenerContainer();
        consumer.setConcurrentConsumers(concurrentConsumers);
        consumer.setTransactionManager(platformTransactionManager);
        return consumer;
      }
    };

    configurer.configure(factory, connectionFactory);
    if (useThreadPoolExecutor) {
      factory.setTaskExecutor(Executors.newFixedThreadPool(numThreads));
    }
    return factory;
  }
}
