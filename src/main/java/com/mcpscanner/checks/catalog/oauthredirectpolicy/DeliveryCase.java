package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.net.URI;

record DeliveryCase(String shape, URI candidate, AuthorizationVariant variant, boolean normalisePath) {

    static DeliveryCase of(String shape, URI candidate, AuthorizationVariant variant) {
        return new DeliveryCase(shape, candidate, variant, false);
    }

    static DeliveryCase normalising(String shape, URI candidate) {
        return new DeliveryCase(shape, candidate, AuthorizationVariant.STANDARD, true);
    }

    String describe(Delivery delivery) {
        String kind = delivery == Delivery.CODE ? "authorization code" : "OAuth error";
        String destination = normalisePath ? candidate + " or the URI it normalises to" : candidate.toString();
        return kind + " delivered to " + destination + " (" + shape + "; " + variant.label() + ")";
    }
}
