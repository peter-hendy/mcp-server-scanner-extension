package consumer;

import com.mcpscanner.core.protocol.JsonDocuments;
import java.util.Map;

public final class JsonDocumentsTest {
    public static void main(String[] args) {
        if (!((Map<?, ?>) JsonDocuments.parse("{\"expires_in\":60}", 100)).containsKey("expires_in")) throw new AssertionError();
        try { JsonDocuments.parse("{\"x\":1,\"x\":2}", 100); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
    }
}
