package com.beaconstrategists.onms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenNmsAlertmanagerBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenNmsAlertmanagerBridgeApplication.class, args);
    }
}
