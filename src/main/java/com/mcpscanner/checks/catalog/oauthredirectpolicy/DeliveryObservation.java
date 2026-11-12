package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record DeliveryObservation(Delivery delivery, List<HttpRequestResponse> exchanges) {

    DeliveryObservation {
        exchanges = List.copyOf(exchanges);
    }

    static DeliveryObservation unconfirmed() {
        return new DeliveryObservation(Delivery.INCONCLUSIVE, List.of());
    }
}
