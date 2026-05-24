package com.mikunaigen.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class BackendApplication {

	@PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
    }

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}
}
