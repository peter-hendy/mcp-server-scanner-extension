package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

interface IdPattern {

    Judgement judge(IdSample sample);
}
