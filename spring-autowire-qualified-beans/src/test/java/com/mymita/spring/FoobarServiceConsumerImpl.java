package com.mymita.spring;

import java.util.List;

public class FoobarServiceConsumerImpl implements FoobarServiceConsumer {

  private List<FoobarService> services;

  public FoobarServiceConsumerImpl(final List<FoobarService> services) {
    this.services = services;
  }

  @Override
  public List<FoobarService> getServices() {
    return services;
  }

  public void setServices(final List<FoobarService> services) {
    this.services = services;
  }

}
