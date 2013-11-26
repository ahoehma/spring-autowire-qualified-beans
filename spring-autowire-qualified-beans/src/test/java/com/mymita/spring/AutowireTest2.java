package com.mymita.spring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.mymita.spring.FoobarContext.ContextType;

/**
 * https://jira.springsource.org/browse/SPR-11116
 * http://docs.spring.io/spring/docs/3.2.x/spring-framework-reference/html/beans.html#beans-annotation-config
 */
@ContextConfiguration(classes = {
  AutowireTest2.TestConfiguration.class
})
public class AutowireTest2 extends AbstractTestNGSpringContextTests {

  @Configuration
  @EnableSpringConfigured
  @ImportResource(value = {
    "/com/mymita/spring-autowire-qualified-beans/beans.xml"
  })
  static class TestConfiguration {

    @Bean
    static CustomScopeConfigurer customScopes() {
      final CustomScopeConfigurer configurer = new CustomScopeConfigurer();
      final Map<String, Object> scopes = new HashMap<String, Object>();
      scopes.put("foobarScope", new SimpleThreadScope());
      configurer.setScopes(scopes);
      return configurer;
    }

    @Bean
    @Qualifier("consumer1")
    FoobarServiceConsumer consumer1(final List<FoobarService> services) {
      LOGGER.debug("Create consumer with services '{}'", services);
      return new FoobarServiceConsumer() {

        @Override
        public List<FoobarService> getServices() {
          return ImmutableList.copyOf(services);
        }
      };
    }

    @Bean
    @Qualifier("consumer2")
    FoobarServiceConsumer consumer2(@FoobarContext final List<FoobarService> services) {
      LOGGER.debug("Create consumer with services '{}'", services);
      return new FoobarServiceConsumer() {
        @Override
        public List<FoobarService> getServices() {
          return ImmutableList.copyOf(services);
        }
      };
    }

    @Bean
    @Qualifier("consumer3")
    FoobarServiceConsumer consumer3(@FoobarContext(ContextType.BAR) final List<FoobarService> services) {
      LOGGER.debug("Create consumer with services '{}'", services);
      return new FoobarServiceConsumer() {
        @Override
        public List<FoobarService> getServices() {
          return ImmutableList.copyOf(services);
        }
      };
    }
  }

  @Autowired
  @Qualifier("consumer1")
  transient FoobarServiceConsumer consumer1;
  @Autowired
  @Qualifier("consumer2")
  transient FoobarServiceConsumer consumer2;
  @Autowired
  @Qualifier("consumer3")
  transient FoobarServiceConsumer consumer3;

  private static final Logger LOGGER = LoggerFactory.getLogger(AutowireTest2.class);

  @Test
  public void testAutowiredBeans() {
    Assert.assertEquals(consumer1.getServices().size(), 6);
  }

  @Test
  public void testAutowiredQualifiedBeansBar() {
    Assert.assertEquals(consumer3.getServices().size(), 1);
  }

  @Test
  public void testAutowiredQualifiedBeansFoo() {
    Assert.assertEquals(consumer2.getServices().size(), 4);
  }
}
