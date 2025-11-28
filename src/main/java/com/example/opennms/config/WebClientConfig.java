package com.example.opennms.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private final BridgeProperties bridgeProperties;

    public WebClientConfig(BridgeProperties bridgeProperties) {
        this.bridgeProperties = bridgeProperties;
    }

    @Bean
    public WebClient alertmanagerWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) bridgeProperties.getAlertmanager().getConnectTimeout().toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(
                                bridgeProperties.getAlertmanager().getReadTimeout().toSeconds(),
                                TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(
                                bridgeProperties.getAlertmanager().getReadTimeout().toSeconds(),
                                TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(bridgeProperties.getAlertmanager().getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
