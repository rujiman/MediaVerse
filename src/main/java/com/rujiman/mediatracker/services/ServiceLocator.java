package com.rujiman.mediatracker.services;

/**
 * Proveedor central de servicios para la sesión activa.
 *
 * FavoritesService ya es no-estático (instancia por usuario).
 * DashboardService, PlanService, ProfileService y WatchProgressService
 * siguen siendo estáticos por ahora — se migrarán en iteraciones
 * posteriores sin romper la compilación.
 *
 * Uso:
 *   ServiceLocator.init(username);                // al hacer login
 *   ServiceLocator.get().favorites()              // en los controladores
 *   ServiceLocator.reset();                       // al hacer logout
 */
public final class ServiceLocator {

    private static ServiceLocator instance;

    private final String           username;
    private final FavoritesService favorites;

    private ServiceLocator(String username) {
        this.username  = username;
        this.favorites = new FavoritesService(username);
    }

    // ── Ciclo de vida ──────────────────────────────────────────────────

    /** Debe llamarse justo después de que el usuario haga login. */
    public static void init(String username) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("username no puede ser vacío");
        instance = new ServiceLocator(username);
    }

    /**
     * Devuelve el locator de la sesión actual.
     * @throws IllegalStateException si aún no se ha llamado a init().
     */
    public static ServiceLocator get() {
        if (instance == null)
            throw new IllegalStateException(
                "ServiceLocator no inicializado — llama a init(username) tras el login"
            );
        return instance;
    }

    /** ¿Hay una sesión activa? */
    public static boolean isInitialized() { return instance != null; }

    /** Invalida todos los servicios. Llamar desde AuthService.logout(). */
    public static void reset() { instance = null; }

    // ── Acceso a servicios ─────────────────────────────────────────────

    public String           username()  { return username;  }
    public FavoritesService favorites() { return favorites; }
}
