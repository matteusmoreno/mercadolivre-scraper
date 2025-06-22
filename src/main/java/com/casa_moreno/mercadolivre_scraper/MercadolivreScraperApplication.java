package com.casa_moreno.mercadolivre_scraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MercadolivreScraperApplication {

	public static void main(String[] args) {
		SpringApplication.run(MercadolivreScraperApplication.class, args);
	}

}
