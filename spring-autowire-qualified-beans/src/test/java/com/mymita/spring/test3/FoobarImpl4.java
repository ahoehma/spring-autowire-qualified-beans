package com.mymita.spring.test3;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import com.mymita.spring.FoobarContext;
import com.mymita.spring.FoobarContext.ContextType;
import com.mymita.spring.FoobarImpl;

@Service
@FoobarContext(ContextType.FOO)
@Scope(value = "foobarScope", proxyMode = ScopedProxyMode.INTERFACES)
public class FoobarImpl4 extends FoobarImpl {
	public FoobarImpl4() {
		setName("4");
	}
}
