package dev.ermer.mythglass;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Sichert die Modulgrenzen ab.
 *
 * <p>Das ist der Test, der "erweiterbar" von einer Absicht zu einer Eigenschaft macht: Sobald jemand
 * — auch ich selbst in einem Jahr — aus der Bibliothek auf die Bühne greift oder an der öffentlichen
 * Schnittstelle eines Moduls vorbei in dessen {@code internal}-Paket, bricht der Build.
 */
class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(MythglassApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void printModuleStructure() {
        System.out.println(MODULES);
    }
}
