package dev.ermer.mythglass;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Reicht die Routen der Weboberfläche an das React-Bundle weiter.
 *
 * <p>Bewusst nur die tatsächlich vorhandenen Routen statt eines Auffangmusters: Ein Tippfehler in
 * einem API-Pfad soll ein sauberes 404 ergeben und nicht stillschweigend die Weboberfläche liefern.
 */
@Controller
class SpaForwardController {

    @GetMapping("/stage/{surfaceId}")
    String stage() {
        return "forward:/index.html";
    }
}
