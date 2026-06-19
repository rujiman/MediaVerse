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
    // REGISTRAR NUEVO USUARIO
    // ============================
    public static boolean register(String username, String password, String confirmPassword) {
        System.out.println("📝 Intentando registrar: " + username);

        // Validaciones
        if (username.isEmpty() || username.length() < 3) {
            System.err.println("❌ Usuario debe tener al menos 3 caracteres");
            return false;
        }

        if (password.isEmpty() || password.length() < 4) {
            System.err.println("❌ Contraseña debe tener al menos 4 caracteres");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            System.err.println("❌ Las contraseñas no coinciden");
            return false;
        }

        List<User> users = loadUsers();

        // Verificar si el usuario ya existe
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                System.err.println("❌ El usuario ya existe");
                return false;
            }
        }

        // Agregar nuevo usuario
        User newUser = new User(username, password);
        users.add(newUser);
        saveUsers(users);

        System.out.println("✅ Usuario registrado: " + username);
        return true;
    }

    // ============================
    // GUARDAR USUARIOS EN JSON
    // ============================
    private static void saveUsers(List<User> users) {
        try {
            JsonObject root = new JsonObject();
            JsonArray usersArray = new JsonArray();

            for (User user : users) {
                JsonObject obj = new JsonObject();
                obj.addProperty("username", user.getUsername());
                obj.addProperty("password", user.getPassword());
                usersArray.add(obj);
            }

            root.add("users", usersArray);

            String json = gson.toJson(root);
            Files.write(Paths.get(USERS_FILE), json.getBytes());
            System.out.println("💾 users.json actualizado");
        } catch (IOException e) {
            System.err.println("❌ Error al guardar users.json: " + e.getMessage());
        }
    }

    // ============================
    // CREAR ARCHIVO POR DEFECTO
    // ============================
    private static void createDefaultUsersFile() {
        try {
            JsonObject root = new JsonObject();
            JsonArray users = new JsonArray();

            root.add("users", users);

            String json = gson.toJson(root);
            Files.write(Paths.get(USERS_FILE), json.getBytes());
            System.out.println("✅ users.json creado vacío (sin usuarios por defecto)");
        } catch (IOException e) {
            System.err.println("❌ Error al crear users.json: " + e.getMessage());
        }
    }
}