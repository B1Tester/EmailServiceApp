package com.farmers.ecom.email;

import com.farmers.ecom.email.service.PubSubPullService;

import org.springframework.boot.SpringApplication;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;

@SpringBootApplication

public class EcomEmailServiceApplication {

	private final PubSubPullService pubSubPullService;

	public EcomEmailServiceApplication(PubSubPullService pubSubPullService) {

		this.pubSubPullService = pubSubPullService;

	}

	public static void main(String[] args) {

		SpringApplication.run(EcomEmailServiceApplication.class, args);

	}

	@PostConstruct

	public void startSubscriber() {

		pubSubPullService.startPullingMessages();

	}

}

