package dev.ermer.mythglass.stage.internal;

import dev.ermer.mythglass.library.LibraryRescanned;
import dev.ermer.mythglass.stage.StageService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reagiert darauf, dass die Bibliothek neu eingelesen wurde.
 *
 * <p>Damit kennt die Bibliothek die Bühne nicht — sie meldet nur, dass sich etwas geändert hat. Ein
 * einfacher {@code @EventListener} und bewusst kein {@code @ApplicationModuleListener}: Letzterer
 * arbeitet transaktional, und ohne Datenbank gibt es hier keinen Transaktionsmanager, unter dem er
 * auslösen würde.
 */
@Component
class LibraryChangeListener {

    private final StageService stage;

    LibraryChangeListener(StageService stage) {
        this.stage = stage;
    }

    @EventListener
    void onLibraryRescanned(LibraryRescanned event) {
        stage.dropScenesWithMissingAssets();
    }
}
