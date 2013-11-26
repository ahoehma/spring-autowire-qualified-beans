package com.mymita.spring.test3;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import com.mymita.spring.FoobarContext;
import com.mymita.spring.FoobarContext.ContextType;
import com.mymita.spring.FoobarImpl;

@Service
@FoobarContext(ContextType.FOO)
@Scope(value = "foobarScope", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class FoobarImpl5 extends FoobarImpl {
	public FoobarImpl5() {
		setName("5");
	}
}
