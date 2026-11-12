package com.mcpscanner.checks.catalog.jwtvalidation;

import java.util.List;

interface ForgeryStrategy {

    List<ForgeryAttempt> attemptsFor(ForgeryContext context);
}
