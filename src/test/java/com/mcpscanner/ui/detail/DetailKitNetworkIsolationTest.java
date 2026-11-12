package com.mcpscanner.ui.detail;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Toolkit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.mcpscanner.ui.detail", importOptions = ImportOption.DoNotIncludeTests.class)
public class DetailKitNetworkIsolationTest {

    @ArchTest
    static final ArchRule the_detail_kit_cannot_open_urls_or_files =
            noClasses().that().resideInAPackage("com.mcpscanner.ui.detail..")
                    .should().dependOnClassesThat().resideInAnyPackage("java.net..", "java.nio.file..")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.File")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileInputStream")
                    .orShould().callMethod(ImageIO.class, "read", java.io.InputStream.class)
                    .orShould().callMethod(ImageIO.class, "createImageInputStream", Object.class)
                    .orShould().callConstructor(ImageIcon.class, String.class)
                    .orShould().callMethod(Toolkit.class, "getImage", String.class)
                    .orShould().callMethod(Toolkit.class, "createImage", String.class)
                    .because("server-supplied icon sources are untrusted; the UI decodes only in-memory data: URI "
                            + "bytes and must never be able to fetch a URL or read a file named by the server.");
}
