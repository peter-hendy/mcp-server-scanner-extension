package com.mcpscanner.access;

import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessItemKey;

import java.util.Optional;

public interface AccessExpectations {

    Optional<AccessExpectation> stored(AccessItemKey item);

    void store(AccessItemKey item, AccessExpectation expectation);
}
