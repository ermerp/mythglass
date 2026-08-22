package dev.ermer.mythglass.stage.internal;

import dev.ermer.mythglass.stage.Scene;
import dev.ermer.mythglass.stage.StageService;
import dev.ermer.mythglass.stage.SurfaceState;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/surfaces")
class StageController {

    private final StageService stage;

    StageController(StageService stage) {
        this.stage = stage;
    }

    @GetMapping
    List<SurfaceState> surfaces() {
        return stage.state().surfaces();
    }

    @PutMapping("/{id}/scene")
    List<SurfaceState> setScene(@PathVariable String id, @Valid @RequestBody Scene scene) {
        stage.show(id, scene);
        return stage.state().surfaces();
    }

    /**
     * Die Panik-Taste bekommt einen eigenen Endpunkt, damit sie im Frontend ein einziger Aufruf ohne
     * Nutzlast ist — je weniger dazwischen liegt, desto besser.
     */
    @PostMapping("/{id}/blank")
    List<SurfaceState> blank(@PathVariable String id) {
        stage.blank(id);
        return stage.state().surfaces();
    }
}
