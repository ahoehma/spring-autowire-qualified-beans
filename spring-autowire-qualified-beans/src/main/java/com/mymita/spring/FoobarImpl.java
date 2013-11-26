package com.mymita.spring;

import org.apache.commons.lang.builder.ToStringBuilder;

public class FoobarImpl implements FoobarService {
  private String name;
  public FoobarService setName(final String name) {
    this.name = name;
    return this;
  }
  @Override
  public String toString() {
    return "Service "+name;
  }
}
