package com.financetracker;

import org.springframework.boot.SpringApplication;

import com.financetracker.support.TestcontainersConfiguration;

public class TestFinanceTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinanceTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
