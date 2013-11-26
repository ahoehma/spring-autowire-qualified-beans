package com.mymita.spring.test3;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.mymita.spring.FoobarContext;
import com.mymita.spring.FoobarContext.ContextType;
import com.mymita.spring.FoobarImpl;

@Service
@FoobarContext(ContextType.FOO)
@Scope(value = "foobarScope")
public class FoobarImpl3 extends FoobarImpl {
	public FoobarImpl3() {
		setName("3");
	}
}
