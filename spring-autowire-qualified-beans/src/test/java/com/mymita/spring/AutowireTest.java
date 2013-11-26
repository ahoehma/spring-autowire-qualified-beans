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
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.mymita.spring.FoobarContext.ContextType;

/**
 * This doesn't work. The qualified beans are created via java configuration.
 * 
 * https://jira.springsource.org/browse/SPR-11116
 * http://docs.spring.io/spring/docs/3.2.x/spring-framework-reference/html/beans.html#beans-annotation-config
 */
@ContextConfiguration(classes = {
  AutowireTest.TestConfiguration.class
})
public class AutowireTest extends AbstractTestNGSpringContextTests {

  @Configuration
  @EnableSpringConfigured
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
    FoobarServiceConsumer consumer2(@FoobarContext(ContextType.FOO) final List<FoobarService> services) {
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

    @Bean
    FoobarService service1() {
      return new FoobarImpl().setName("1");
    }

    @Bean
    @FoobarContext(ContextType.FOO)
    FoobarService service2() {
      return new FoobarImpl().setName("2");
    }

    @Bean
    @FoobarContext(ContextType.FOO)
    @Scope(value = "foobarScope")
    FoobarService service3() {
      return new FoobarImpl().setName("3");
    }

    @Bean
    @FoobarContext(ContextType.FOO)
    @Scope(value = "foobarScope", proxyMode = ScopedProxyMode.INTERFACES)
    FoobarService service4() {
      return new FoobarImpl().setName("4");
    }

    @Bean
    @FoobarContext(ContextType.FOO)
    @Scope(value = "foobarScope", proxyMode = ScopedProxyMode.TARGET_CLASS)
    FoobarService service5() {
      return new FoobarImpl().setName("5");
    }

    @Bean
    @FoobarContext(ContextType.BAR)
    FoobarService service6() {
      return new FoobarImpl().setName("6");
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

  private static final Logger LOGGER = LoggerFactory.getLogger(AutowireTest.class);

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
    // expected 4 bean with qualifier @FoobarContext(ContextType.FOO)
    // but only 2 are autowired because 2 of them are proxies (doesn't matter if jdk or cglib proxies)
    Assert.assertEquals(consumer2.getServices().size(), 4);
  }
}
