package com.bts.fairticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class FairticketApplication {

	public static void main(String[] args) {
		SpringApplication.run(FairticketApplication.class, args);
	}

}
