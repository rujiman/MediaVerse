package com.rujiman.mediatracker.models;

/**
 * Reseña personal de un título (texto libre, aparte de las estrellas).
 *
 * Se guarda ya con todo lo que hará falta el día que las reseñas se
 * compartan con otros usuarios (servidor): un identificador estable de
 * la obra (workId, el ID de su API), si tiene spoilers, si es pública
 * y las fechas. Mientras todo sea local, siempre es privada.
 */
public class Review {

    public static final String PRIVATE = "private";
    public static final String PUBLIC  = "public";

    /** ID estable de la obra: "tmdb:tv:1399", "anilist:16498", "igdb:1942"... null si la API no da ID. */
    public String workId;
    /** Tipo (MediaType.name()) y título, por si no hay workId. */
    public String type;
    public String title;
    /** Portada y año, para pintar la reseña sin depender de favoritos o progreso. */
    public String imageUrl;
    public Integer year;

    public String text;
    public boolean spoilers;
    /** "private" o "public". De momento siempre "private". */
    public String visibility = PRIVATE;

    /** Fechas en ISO-8601 (Instant.toString()). */
    public String createdAt;
    public String updatedAt;
}
