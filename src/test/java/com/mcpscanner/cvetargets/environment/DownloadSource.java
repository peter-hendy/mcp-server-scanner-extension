package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

public interface DownloadSource {

    void retrieve(URI url, Path destination) throws IOException;
}
