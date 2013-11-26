package com.mymita.spring.test3;

import org.springframework.stereotype.Service;

import com.mymita.spring.FoobarContext;
import com.mymita.spring.FoobarContext.ContextType;
import com.mymita.spring.FoobarImpl;

@Service
@FoobarContext(ContextType.BAR)
public class FoobarImpl6 extends FoobarImpl {
	public FoobarImpl6() {
		setName("6");
	}
}
