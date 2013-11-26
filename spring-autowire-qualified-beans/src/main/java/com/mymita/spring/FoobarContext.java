package com.mymita.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Custom annotation to qualify beans (creation time and autowire time).
 */
@Target({
	ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER
})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface FoobarContext {

  public enum ContextType {
    FOO, BAR
  }

  ContextType value() default ContextType.FOO;
}
