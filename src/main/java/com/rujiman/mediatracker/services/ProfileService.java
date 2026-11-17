package com.rujiman.mediatracker.services;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Servicio para guardar y cargar datos de perfil (nombre visible y foto)
 * de cada usuario, en un archivo separado de users.json.
 *
 * Estructura de profile.json:
 * {
 *   "profiles": {
 *     "admin": { "displayName": "admin", "photoPath": "userdata/admin_profile.png" },
 *     "Rujiman": { "displayName": "Rujiman", "photoPath": null }
 *   }
 * }
 */
public class ProfileService {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static java.nio.file.Path profilesFile() {
        return AppPaths.global("profile.json");
    }

    /**
     * Datos de perfil de un usuario concreto.
     */
    public static class Profile {
        public String displayName;
        public String photoPath; // ruta relativa al archivo de foto, o null si no tiene

        /**
         * Imagen de la portada de la vitrina: una URL (portada de un
         * favorito) o la URI de un archivo subido por el usuario.
         * null = automática (el favorito absoluto de la primera vitrina).
         */
        public String bannerUrl;

        /** Si la imagen de la portada se muestra desenfocada. */
        public boolean bannerBlur;

        /** Intensidad del desenfoque (radio en px, 2–40). */
        public int bannerBlurAmount = 12;

        /** Color sólido de la portada (#rrggbb). Si no es null, sustituye a la imagen. */
        public String bannerColor;

        public Profile() {}

        public Profile(String displayName, String photoPath) {
            this.displayName = displayName;
            this.photoPath = photoPath;
        }
    }

    // ============================
    // CARGAR PERFIL DE UN USUARIO
    // ============================
    public static Profile loadProfile(String username) {
        JsonObject root = loadRoot();
        JsonObject profiles = root.getAsJsonObject("profiles");

        if (profiles.has(username)) {
            JsonObject obj = profiles.getAsJsonObject(username);
            String displayName = obj.has("displayName") && !obj.get("displayName").isJsonNull()
                    ? obj.get("displayName").getAsString() : username;
            String photoPath = obj.has("photoPath") && !obj.get("photoPath").isJsonNull()
                    ? obj.get("photoPath").getAsString() : null;
            Profile profile = new Profile(displayName, photoPath);
            if (obj.has("bannerUrl") && !obj.get("bannerUrl").isJsonNull()) {
                profile.bannerUrl = obj.get("bannerUrl").getAsString();
            }
            if (obj.has("bannerBlur") && !obj.get("bannerBlur").isJsonNull()) {
                profile.bannerBlur = obj.get("bannerBlur").getAsBoolean();
            }
            if (obj.has("bannerBlurAmount") && !obj.get("bannerBlurAmount").isJsonNull()) {
                profile.bannerBlurAmount = obj.get("bannerBlurAmount").getAsInt();
            }
            if (obj.has("bannerColor") && !obj.get("bannerColor").isJsonNull()) {
                profile.bannerColor = obj.get("bannerColor").getAsString();
            }
            return profile;
        }

        // Si no existe perfil todavía, devolvemos uno por defecto (sin foto)
        return new Profile(username, null);
    }

    // ============================
    // GUARDAR / ACTUALIZAR PERFIL
    // ============================
    public static void saveProfile(String username, Profile profile) {
        JsonObject root = loadRoot();
        JsonObject profiles = root.getAsJsonObject("profiles");

        JsonObject obj = new JsonObject();
        obj.addProperty("displayName", profile.displayName);
        if (profile.photoPath != null) {
            obj.addProperty("photoPath", profile.photoPath);
        } else {
            obj.add("photoPath", JsonNull.INSTANCE);
        }
        if (profile.bannerUrl != null) {
            obj.addProperty("bannerUrl", profile.bannerUrl);
        }
        obj.addProperty("bannerBlur", profile.bannerBlur);
        obj.addProperty("bannerBlurAmount", profile.bannerBlurAmount);
        if (profile.bannerColor != null) {
            obj.addProperty("bannerColor", profile.bannerColor);
        }

        profiles.add(username, obj);
        root.add("profiles", profiles);

        writeRoot(root);
    }

    /**
     * Actualiza solo el nombre visible, conservando la foto que ya tuviera.
     */
    public static void updateDisplayName(String username, String newDisplayName) {
        Profile profile = loadProfile(username);
        profile.displayName = newDisplayName;
        saveProfile(username, profile);
    }

    /**
     * Actualiza solo la foto, conservando el nombre visible que ya tuviera.
     */
    public static void updatePhotoPath(String username, String newPhotoPath) {
        Profile profile = loadProfile(username);
        profile.photoPath = newPhotoPath;
        saveProfile(username, profile);
    }

    /**
     * Cambia la portada de la vitrina (null = volver a la automática),
     * conservando nombre y foto.
     */
    public static void updateBannerUrl(String username, String newBannerUrl) {
        Profile profile = loadProfile(username);
        profile.bannerUrl = newBannerUrl;
        profile.bannerColor = null; // elegir imagen quita el color sólido
        saveProfile(username, profile);
    }

    /** Activa o desactiva el desenfoque de la imagen de portada. */
    public static void updateBannerBlur(String username, boolean blur) {
        Profile profile = loadProfile(username);
        profile.bannerBlur = blur;
        saveProfile(username, profile);
    }

    /** Cambia la intensidad del desenfoque (se limita a 2–40 px). */
    public static void updateBannerBlurAmount(String username, int amount) {
        Profile profile = loadProfile(username);
        profile.bannerBlurAmount = Math.max(2, Math.min(40, amount));
        saveProfile(username, profile);
    }

    /** Pone la portada de un color sólido (#rrggbb), o null para volver a la imagen. */
    public static void updateBannerColor(String username, String hexColor) {
        Profile profile = loadProfile(username);
        profile.bannerColor = hexColor;
        saveProfile(username, profile);
    }

    /**
     * Cuando AuthService cambia el username de login de una cuenta,
     * migra su entrada en profile.json a la nueva clave para no perder
     * la foto/nombre visible ya guardados.
     */
    public static void renameProfileKey(String oldUsername, String newUsername) {
        JsonObject root = loadRoot();
        JsonObject profiles = root.getAsJsonObject("profiles");

        if (profiles.has(oldUsername)) {
            JsonElement obj = profiles.get(oldUsername);
            profiles.remove(oldUsername);
            profiles.add(newUsername, obj);
            root.add("profiles", profiles);
            writeRoot(root);
        }
    }

    // ============================
    // HELPERS INTERNOS
    // ============================
    private static JsonObject loadRoot() {
        try {
            if (!Files.exists(profilesFile())) {
                JsonObject empty = new JsonObject();
                empty.add("profiles", new JsonObject());
                writeRoot(empty);
                return empty;
            }

            String json = new String(Files.readAllBytes(profilesFile()));
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root == null) {
                root = new JsonObject();
            }
            if (!root.has("profiles") || !root.get("profiles").isJsonObject()) {
                root.add("profiles", new JsonObject());
            }
            return root;

        } catch (IOException e) {
            System.err.println("❌ Error al cargar profile.json: " + e.getMessage());
            JsonObject fallback = new JsonObject();
            fallback.add("profiles", new JsonObject());
            return fallback;
        }
    }

    private static void writeRoot(JsonObject root) {
        try {
            String json = gson.toJson(root);
            Files.write(profilesFile(), json.getBytes());
        } catch (IOException e) {
            System.err.println("❌ Error al guardar profile.json: " + e.getMessage());
        }
    }
}