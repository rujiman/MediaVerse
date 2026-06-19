package com.rujiman.mediatracker.services;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    private static final String PROFILES_FILE = "profile.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Datos de perfil de un usuario concreto.
     */
    public static class Profile {
        public String displayName;
        public String photoPath; // ruta relativa al archivo de foto, o null si no tiene

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
            return new Profile(displayName, photoPath);
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
            if (!Files.exists(Paths.get(PROFILES_FILE))) {
                JsonObject empty = new JsonObject();
                empty.add("profiles", new JsonObject());
                writeRoot(empty);
                return empty;
            }

            String json = new String(Files.readAllBytes(Paths.get(PROFILES_FILE)));
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
            Files.write(Paths.get(PROFILES_FILE), json.getBytes());
        } catch (IOException e) {
            System.err.println("❌ Error al guardar profile.json: " + e.getMessage());
        }
    }
}