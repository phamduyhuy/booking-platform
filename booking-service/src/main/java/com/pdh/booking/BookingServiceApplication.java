package com.pdh.booking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = { "com.pdh.booking", "com.pdh.common" })
@EnableDiscoveryClient
@EnableJpaRepositories(basePackages = {"com.pdh.booking"})
@EntityScan(basePackages = {"com.pdh.booking"})
@EnableScheduling
@ComponentScan(basePackages = {"com.pdh.booking", "com.pdh.common"})
@Slf4j
public class BookingServiceApplication {

    public static void main(String[] args) {
        log.info("Starting BookingSmart Booking Service...");
        log.info("Booking Service will be available at: http://localhost:8083");
        
        TimeZone.setDefault(TimeZone.getTimeZone("GMT +0:00"));
        SpringApplication.run(BookingServiceApplication.class, args);
        
        log.info("BookingSmart Booking Service started successfully!");
    }
}
