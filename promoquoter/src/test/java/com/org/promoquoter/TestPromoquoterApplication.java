package com.org.promoquoter;

import org.springframework.boot.SpringApplication;



public class TestPromoquoterApplication {

	public static void main(String[] args) {
		SpringApplication.from(PromoquoterApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
