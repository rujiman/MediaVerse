package com.rujiman.mediatracker.services;

import com.google.gson.*;
import com.rujiman.mediatracker.models.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de autenticación
 * Maneja login/logout con archivo JSON local
 */
public class AuthService {

    private static final String USERS_FILE = "users.json";
    private static final Gson gson = new Gson();

    private static String currentUser = null;

    // ============================
    // LOGIN
    // ============================
    public static boolean login(String username, String password) {
        System.out.println("🔐 Intentando login: " + username);

        List<User> users = loadUsers();

        for (User user : users) {
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                currentUser = username;
                System.out.println("✅ Login exitoso: " + username);
                return true;
            }
        }

        System.err.println("❌ Login fallido: usuario o contraseña incorrectos");
        return false;
    }

    // ============================
    // LOGOUT
    // ============================
    public static void logout() {
        System.out.println("🚪 Logout: " + currentUser);
        currentUser = null;
    }

    // ============================
    // OBTENER USUARIO ACTUAL
    // ============================
    public static String getCurrentUser() {
        return currentUser;
    }

    public static boolean isLogged() {
        return currentUser != null;
    }

    // ============================
    // CARGAR USUARIOS DESDE JSON
    // ============================
    private static List<User> loadUsers() {
        List<User> users = new ArrayList<>();

        try {
            if (!Files.exists(Paths.get(USERS_FILE))) {
                System.out.println("⚠️ users.json no encontrado, creando con usuario por defecto...");
                createDefaultUsersFile();
            }

            String json = new String(Files.readAllBytes(Paths.get(USERS_FILE)));
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray usersArray = root.getAsJsonArray("users");

            for (JsonElement el : usersArray) {
                JsonObject obj = el.getAsJsonObject();
                User user = new User(
                        obj.get("username").getAsString(),
                        obj.get("password").getAsString()
                );
                users.add(user);
            }

            System.out.println("✅ Usuarios cargados: " + users.size());

        } catch (IOException e) {
            System.err.println("❌ Error al cargar users.json: " + e.getMessage());
        }

        return users;
    }

    // ============================
    // CREAR ARCHIVO POR DEFECTO
    // ============================
    private static void createDefaultUsersFile() {
        try {
            JsonObject root = new JsonObject();
            JsonArray users = new JsonArray();

            JsonObject user = new JsonObject();
            user.addProperty("username", "admin");
            user.addProperty("password", "1234");
            users.add(user);

            root.add("users", users);

            String json = gson.toJson(root);
            Files.write(Paths.get(USERS_FILE), json.getBytes());
            System.out.println("✅ users.json creado con usuario por defecto (admin / 1234)");
        } catch (IOException e) {
            System.err.println("❌ Error al crear users.json: " + e.getMessage());
        }
    }
}
