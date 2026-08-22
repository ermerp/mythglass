package dev.ermer.mythglass.stage;

import jakarta.validation.constraints.NotBlank;

/**
 * Ein Bild aus der Bibliothek, adressiert über seine ID.
 *
 * @param assetId ID eines Bildes; wird beim Setzen gegen die Bibliothek geprüft
 * @param fit wie das Bild in den Monitor eingepasst wird; ohne Angabe {@link Fit#CONTAIN}
 */
public record ImageScene(@NotBlank String assetId, Fit fit) implements Scene {

    public ImageScene {
        fit = fit == null ? Fit.CONTAIN : fit;
    }
}
