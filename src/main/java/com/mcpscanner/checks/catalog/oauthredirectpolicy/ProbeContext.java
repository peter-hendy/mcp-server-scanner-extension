package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.ProbeClientRegistrar;

record ProbeContext(ProbeClientRegistrar registrar, OwnedClients clients, DeliveryProbe delivery) {
}
