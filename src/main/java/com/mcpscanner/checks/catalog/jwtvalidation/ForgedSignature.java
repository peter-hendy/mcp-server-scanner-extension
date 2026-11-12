package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;

interface ForgedSignature {

    String description();

    String sign(ObjectNode claims);
}
