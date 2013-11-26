package com.mymita.spring.test3;

import org.springframework.stereotype.Service;

import com.mymita.spring.FoobarImpl;

@Service
public class FoobarImpl1 extends FoobarImpl {
	public FoobarImpl1() {
		setName("1");
	}
}
