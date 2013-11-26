package com.mymita.spring.test3;

import org.springframework.stereotype.Service;

import com.mymita.spring.FoobarContext;
import com.mymita.spring.FoobarContext.ContextType;
import com.mymita.spring.FoobarImpl;

@Service
@FoobarContext(ContextType.FOO)
public class FoobarImpl2 extends FoobarImpl {
	public FoobarImpl2() {
		setName("2");
	}
}
