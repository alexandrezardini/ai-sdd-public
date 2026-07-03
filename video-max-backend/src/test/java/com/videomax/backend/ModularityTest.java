package com.videomax.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(VideoMaxApplication.class).verify();
    }

    @Test
    void createModuleDocumentation() {
        var modules = ApplicationModules.of(VideoMaxApplication.class);
        new Documenter(modules).writeDocumentation();
    }
}
