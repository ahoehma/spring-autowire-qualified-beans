// @formatter:off
/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import static org.springframework.context.annotation.MetadataUtils.attributesFor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanMetadataAttribute;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Reads a given fully-populated set of ConfigurationClass instances, registering bean definitions with the given
 * {@link BeanDefinitionRegistry} based on its contents.
 *
 * <p>
 * This class was modeled after the {@link BeanDefinitionReader} hierarchy, but does not implement/extend any of its artifacts as a set of
 * configuration classes is not a {@link Resource}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.0
 * @see ConfigurationClassParser
 */
class ConfigurationClassBeanDefinitionReader {

  /**
   * {@link RootBeanDefinition} marker subclass used to signify that a bean definition was created from a configuration class as opposed to
   * any other configuration source. Used in bean overriding cases where it's necessary to determine whether the bean definition was created
   * externally.
   */
  @SuppressWarnings("serial")
  private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

    private final AnnotationMetadata annotationMetadata;

    public ConfigurationClassBeanDefinition(final ConfigurationClass configClass) {
      this.annotationMetadata = configClass.getMetadata();
      setLenientConstructorResolution(false);
    }

    public ConfigurationClassBeanDefinition(final RootBeanDefinition original, final ConfigurationClass configClass) {
      super(original);
      this.annotationMetadata = configClass.getMetadata();
    }

    private ConfigurationClassBeanDefinition(final ConfigurationClassBeanDefinition original) {
      super(original);
      this.annotationMetadata = original.annotationMetadata;
    }

    @Override
    public ConfigurationClassBeanDefinition cloneBeanDefinition() {
      return new ConfigurationClassBeanDefinition(this);
    }

    @Override
    public AnnotationMetadata getMetadata() {
      return this.annotationMetadata;
    }

    @Override
    public boolean isFactoryMethod(final Method candidate) {
      return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate));
    }
  }

  /**
   * Configuration classes must be annotated with {@link Configuration @Configuration} or declare at least one {@link Bean @Bean} method.
   */
  private static class InvalidConfigurationImportProblem extends Problem {

    public InvalidConfigurationImportProblem(final String className, final Resource resource, final AnnotationMetadata metadata) {
      super(String.format("%s was @Import'ed but is not annotated with @Configuration "
          + "nor does it declare any @Bean methods; it does not implement ImportSelector "
          + "or extend ImportBeanDefinitionRegistrar. Update the class to meet one of these requirements "
          + "or do not attempt to @Import it.", className), new Location(resource, metadata));
    }
  }

  private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);

  private final BeanDefinitionRegistry registry;

  private final SourceExtractor sourceExtractor;

  private final ProblemReporter problemReporter;

  private final MetadataReaderFactory metadataReaderFactory;

  private final ResourceLoader resourceLoader;

  private final Environment environment;

  private final BeanNameGenerator importBeanNameGenerator;

  /**
   * Create a new {@link ConfigurationClassBeanDefinitionReader} instance that will be used to populate the given
   * {@link BeanDefinitionRegistry}.
   */
  public ConfigurationClassBeanDefinitionReader(final BeanDefinitionRegistry registry, final SourceExtractor sourceExtractor,
      final ProblemReporter problemReporter, final MetadataReaderFactory metadataReaderFactory, final ResourceLoader resourceLoader,
      final Environment environment, final BeanNameGenerator importBeanNameGenerator) {

    this.registry = registry;
    this.sourceExtractor = sourceExtractor;
    this.problemReporter = problemReporter;
    this.metadataReaderFactory = metadataReaderFactory;
    this.resourceLoader = resourceLoader;
    this.environment = environment;
    this.importBeanNameGenerator = importBeanNameGenerator;
  }

  static Map<Class<? extends Annotation>,AnnotationAttributes> qualifierAttributesFor(final MethodMetadata metadata) {
    if (metadata instanceof StandardMethodMetadata) {
      return qualifierFor(metadata, ((StandardMethodMetadata)metadata).getIntrospectedMethod());
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug(String
            .format("Found unsupported method meta data %s for method %s.%s", metadata.getClass(), metadata.getDeclaringClassName(),metadata.getMethodName()));
      }
      try {
        return qualifierFor(metadata, ReflectionUtils.findMethod(Class.forName(metadata.getDeclaringClassName()), metadata.getMethodName(), null));
      } catch (final ClassNotFoundException e) {
        logger.warn(String
            .format("Found unsupported method meta data %s for method %s.%s", metadata.getClass(), metadata.getDeclaringClassName(),metadata.getMethodName()));
      }
    }
    return new HashMap<Class<? extends Annotation>, AnnotationAttributes>();
  }

  private static Map<Class<? extends Annotation>, AnnotationAttributes> qualifierFor(final MethodMetadata metadata,
      final Method introspectedMethod) {
    final Map<Class<? extends Annotation>,AnnotationAttributes> result = new HashMap<Class<? extends Annotation>, AnnotationAttributes>();
    final String annotationType = Qualifier.class.getName();
    for (final Annotation ann : introspectedMethod.getAnnotations()) {
      if (ann.annotationType().getName().equals(annotationType)) {
        // @Qualifier directly added to bean method
        final Class<? extends Annotation> qualifierClass = ann.annotationType();
        final AnnotationAttributes annotationAttributes = AnnotationUtils.getAnnotationAttributes(
            ann, true, true);
        result.put(qualifierClass, annotationAttributes);
        if (logger.isDebugEnabled()) {
          logger.debug(String
              .format("Found annotation %s with attributes %s for method %s.%s",qualifierClass.getName(), annotationAttributes, metadata.getDeclaringClassName(),metadata.getMethodName()));
        }
      }
      for (final Annotation metaAnn : ann.annotationType().getAnnotations()) {
        if (metaAnn.annotationType().getName().equals(annotationType)) {
          // @Qualifier used in custom annotation and then added to bean method
          final Class<? extends Annotation> qualifierClass = ann.annotationType();
          final AnnotationAttributes subAnnotationAttributes = AnnotationUtils.getAnnotationAttributes(
              ann, true, true);
          result.put(qualifierClass, subAnnotationAttributes);
          if (logger.isDebugEnabled()) {
            logger.debug(String
                .format("Found annotation %s with attributes %s for method %s.%s", qualifierClass.getName(), subAnnotationAttributes, metadata.getDeclaringClassName(),metadata.getMethodName()));
          }
        }
      }
    }
    return result;
  }

  /**
   * Read {@code configurationModel}, registering bean definitions with {@link #registry} based on its contents.
   */
  public void loadBeanDefinitions(final Set<ConfigurationClass> configurationModel) {
    for (final ConfigurationClass configClass : configurationModel) {
      loadBeanDefinitionsForConfigurationClass(configClass);
    }
  }

  /**
   * Read the given {@link BeanMethod}, registering bean definitions with the BeanDefinitionRegistry based on its contents.
   */
  private void loadBeanDefinitionsForBeanMethod(final BeanMethod beanMethod) {
    final ConfigurationClass configClass = beanMethod.getConfigurationClass();
    final MethodMetadata metadata = beanMethod.getMetadata();

    final ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass);
    beanDef.setResource(configClass.getResource());
    beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));
    if (metadata.isStatic()) {
      // static @Bean method
      beanDef.setBeanClassName(configClass.getMetadata().getClassName());
      beanDef.setFactoryMethodName(metadata.getMethodName());
    } else {
      // instance @Bean method
      beanDef.setFactoryBeanName(configClass.getBeanName());
      beanDef.setUniqueFactoryMethodName(metadata.getMethodName());
    }
    beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
    beanDef.setAttribute(RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);

    // consider role
    final AnnotationAttributes role = attributesFor(metadata, Role.class);
    if (role != null) {
      beanDef.setRole(role.<Integer> getNumber("value"));
    }

    // consider name and any aliases
    final AnnotationAttributes bean = attributesFor(metadata, Bean.class);
    final List<String> names = new ArrayList<String>(Arrays.asList(bean.getStringArray("name")));
    final String beanName = (names.size() > 0 ? names.remove(0) : beanMethod.getMetadata().getMethodName());
    for (final String alias : names) {
      this.registry.registerAlias(beanName, alias);
    }

    // has this already been overridden (e.g. via XML)?
    if (this.registry.containsBeanDefinition(beanName)) {
      final BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);
      // Is the existing bean definition one that was created from a configuration class?
      // -> allow the current bean method to override, since both are at second-pass level.
      // However, if the bean method is an overloaded case on the same configuration class,
      // preserve the existing bean definition.
      if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
        final ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
        if (ccbd.getMetadata().getClassName().equals(beanMethod.getConfigurationClass().getMetadata().getClassName())) {
          return;
        }
      } else {
        // no -> then it's an external override, probably XML
        // overriding is legal, return immediately
        if (logger.isDebugEnabled()) {
          logger.debug(String.format("Skipping loading bean definition for %s: a definition for bean "
              + "'%s' already exists. This is likely due to an override in XML.", beanMethod, beanName));
        }
        return;
      }
    }

    if (metadata.isAnnotated(Primary.class.getName())) {
      beanDef.setPrimary(true);
    }

    // is this bean to be instantiated lazily?
    if (metadata.isAnnotated(Lazy.class.getName())) {
      final AnnotationAttributes lazy = attributesFor(metadata, Lazy.class);
      beanDef.setLazyInit(lazy.getBoolean("value"));
    } else if (configClass.getMetadata().isAnnotated(Lazy.class.getName())) {
      final AnnotationAttributes lazy = attributesFor(configClass.getMetadata(), Lazy.class);
      beanDef.setLazyInit(lazy.getBoolean("value"));
    }

    if (metadata.isAnnotated(DependsOn.class.getName())) {
      final AnnotationAttributes dependsOn = attributesFor(metadata, DependsOn.class);
      final String[] otherBeans = dependsOn.getStringArray("value");
      if (otherBeans.length > 0) {
        beanDef.setDependsOn(otherBeans);
      }
    }

    final Autowire autowire = bean.getEnum("autowire");
    if (autowire.isAutowire()) {
      beanDef.setAutowireMode(autowire.value());
    }

    final String initMethodName = bean.getString("initMethod");
    if (StringUtils.hasText(initMethodName)) {
      beanDef.setInitMethodName(initMethodName);
    }

    final String destroyMethodName = bean.getString("destroyMethod");
    if (StringUtils.hasText(destroyMethodName)) {
      beanDef.setDestroyMethodName(destroyMethodName);
    }

    // Consider scoping
    ScopedProxyMode proxyMode = ScopedProxyMode.NO;
    final AnnotationAttributes scope = attributesFor(metadata, Scope.class);
    if (scope != null) {
      beanDef.setScope(scope.getString("value"));
      proxyMode = scope.getEnum("proxyMode");
      if (proxyMode == ScopedProxyMode.DEFAULT) {
        proxyMode = ScopedProxyMode.NO;
      }
    }

    for (final Entry<Class<? extends Annotation>, AnnotationAttributes> qualifierAnnotation : qualifierAttributesFor(metadata).entrySet()) {
      final AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(qualifierAnnotation.getKey());
      for (final Entry<String, Object> entry : qualifierAnnotation.getValue().entrySet()) {
        final String attributeName = entry.getKey();
        final Object attributeValue = entry.getValue();
        final BeanMetadataAttribute attribute = new BeanMetadataAttribute(attributeName, attributeValue);
        qualifier.addMetadataAttribute(attribute);
        if (logger.isDebugEnabled()) {
          logger.debug(String
              .format("Add attribute %s=%s to qualifier %s for @Bean method %s.%s()", attributeName, attributeValue, qualifier.getTypeName(), configClass.getMetadata().getClassName(), beanName));
        }
      }
      beanDef.addQualifier(qualifier);
      if (logger.isDebugEnabled()) {
        logger.debug(String
            .format("Add qualifier %s for @Bean method %s.%s()", qualifier.getTypeName(), configClass.getMetadata().getClassName(), beanName));
      }
    }

    // Replace the original bean definition with the target one, if necessary
    BeanDefinition beanDefToRegister = beanDef;
    if (proxyMode != ScopedProxyMode.NO) {
      final BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(new BeanDefinitionHolder(beanDef, beanName),
          this.registry, proxyMode == ScopedProxyMode.TARGET_CLASS);
      beanDefToRegister = new ConfigurationClassBeanDefinition((RootBeanDefinition) proxyDef.getBeanDefinition(), configClass);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(String
          .format("Registering bean definition for @Bean method %s.%s()", configClass.getMetadata().getClassName(), beanName));
    }

    registry.registerBeanDefinition(beanName, beanDefToRegister);
  }

  /**
   * Read a particular {@link ConfigurationClass}, registering bean definitions for the class itself, all its {@link Bean} methods
   */
  private void loadBeanDefinitionsForConfigurationClass(final ConfigurationClass configClass) {
    if (configClass.isImported()) {
      registerBeanDefinitionForImportedConfigurationClass(configClass);
    }
    for (final BeanMethod beanMethod : configClass.getBeanMethods()) {
      loadBeanDefinitionsForBeanMethod(beanMethod);
    }
    loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
  }

  private void loadBeanDefinitionsFromImportedResources(final Map<String, Class<? extends BeanDefinitionReader>> importedResources) {

    final Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<Class<?>, BeanDefinitionReader>();
    for (final Map.Entry<String, Class<? extends BeanDefinitionReader>> entry : importedResources.entrySet()) {
      final String resource = entry.getKey();
      final Class<? extends BeanDefinitionReader> readerClass = entry.getValue();
      if (!readerInstanceCache.containsKey(readerClass)) {
        try {
          // Instantiate the specified BeanDefinitionReader
          final BeanDefinitionReader readerInstance = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
          // Delegate the current ResourceLoader to it if possible
          if (readerInstance instanceof AbstractBeanDefinitionReader) {
            final AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) readerInstance);
            abdr.setResourceLoader(this.resourceLoader);
            abdr.setEnvironment(this.environment);
          }
          readerInstanceCache.put(readerClass, readerInstance);
        } catch (final Exception ex) {
          throw new IllegalStateException("Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
        }
      }
      final BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
      // TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
      reader.loadBeanDefinitions(resource);
    }
  }

  /**
   * Register the {@link Configuration} class itself as a bean definition.
   */
  private void registerBeanDefinitionForImportedConfigurationClass(final ConfigurationClass configClass) {
    final AnnotationMetadata metadata = configClass.getMetadata();
    final BeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);
    if (ConfigurationClassUtils.checkConfigurationClassCandidate(configBeanDef, this.metadataReaderFactory)) {
      final String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
      this.registry.registerBeanDefinition(configBeanName, configBeanDef);
      configClass.setBeanName(configBeanName);
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("Registered bean definition for imported @Configuration class %s", configBeanName));
      }
    } else {
      this.problemReporter.error(new InvalidConfigurationImportProblem(metadata.getClassName(), configClass.getResource(), metadata));
    }
  }

}
