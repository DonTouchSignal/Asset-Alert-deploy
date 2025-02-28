package org.example.msasbalert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;


@SpringBootApplication
@EnableScheduling
public class MsaSbAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsaSbAlertApplication.class, args);
    }

}
