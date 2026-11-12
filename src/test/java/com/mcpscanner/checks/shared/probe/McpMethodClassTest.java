package com.mcpscanner.checks.shared.probe;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class McpMethodClassTest {

    @ParameterizedTest
    @CsvSource({
            "tools/call,false,OPERATION",
            "resources/read,false,OPERATION",
            "prompts/get,false,OPERATION",
            "tools/list,false,CATALOG",
            "resources/list,false,CATALOG",
            "resources/templates/list,false,CATALOG",
            "prompts/list,false,CATALOG",
            "initialize,false,PROTOCOL",
            "ping,false,PROTOCOL",
            "completion/complete,false,PROTOCOL",
            "server/discover,false,PROTOCOL",
            "notifications/initialized,true,NOTIFICATION",
            "tools/call,true,NOTIFICATION"
    })
    void classifiesEveryMethod(String method, boolean notification, McpMethodClass expected) {
        assertThat(McpMethodClass.of(method, notification)).isEqualTo(expected);
    }
}
