package com.archivenexus.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ArchiveNexusBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArchiveNexusBackendApplication.class, args);
	}

}
