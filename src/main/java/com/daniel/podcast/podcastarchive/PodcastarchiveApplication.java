package com.daniel.podcast.podcastarchive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// Used to start app.
@ConfigurationPropertiesScan
// Scans for @ConfigurationProperties classes and binds values from application.properties into those config beans.

public class PodcastarchiveApplication {

	public static void main(String[] args) {
		SpringApplication.run(PodcastarchiveApplication.class, args);
	}

}
