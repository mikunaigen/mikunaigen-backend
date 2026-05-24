package com.mikunaigen.backend.support;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public abstract class WireMockIntegrationSupport {

    @RegisterExtension
    protected static final WireMockExtension WIRE_MOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    protected static String wireMockBaseUrl() {
        return "http://localhost:" + WIRE_MOCK.getPort();
    }
}
