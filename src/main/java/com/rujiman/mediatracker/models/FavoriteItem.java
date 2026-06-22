package com.rujiman.mediatracker.models;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modelo para representar un item guardado en favoritos
 */
public class FavoriteItem {
    private String id; // ID único (generado)
    private MediaType type;
    private String title;
    private String description;
    private String imageUrl;
    private Integer year;
    private Integer score;
    private String externalUrl;
    private boolean viewed; // visto/jugado (para items sin episodios: película, juego, canción)
    private long addedDate; // timestamp cuando se agregó

    // Episodios (solo aplica a ANIME / SERIES)
    private Integer totalEpisodes;
    private Set<Integer> watchedEpisodes = new HashSet<>(); // números de episodio (1-based) marcados como vistos

    // Datos adicionales para que el detalle se vea completo también desde favoritos
    private List<String> platforms;
    private List<String> genres;
    private Integer tmdbId;
    private String trailerKey;
    private String previewUrl;
    private Integer anilistId;
    private Integer igdbId;

    public FavoriteItem() {}

    public FavoriteItem(MediaItem item) {
        this.id = generateId();
        this.type = item.getType();
        this.title = item.getTitle();
        this.description = item.getDescription();
        this.imageUrl = item.getImageUrl();
        this.year = item.getYear();
        this.score = item.getScore();
        this.externalUrl = item.getExternalUrl();
        this.viewed = false;
        this.addedDate = System.currentTimeMillis();
        this.totalEpisodes = item.getEpisodes();
        this.watchedEpisodes = new HashSet<>();
        this.platforms = item.getPlatforms();
        this.genres = item.getGenres();
        this.tmdbId = item.getTmdbId();
        this.trailerKey = item.getTrailerKey();
        this.previewUrl = item.getPreviewUrl();
        this.anilistId = item.getAnilistId();
        this.igdbId = item.getIgdbId();
    }

    private String generateId() {
        return System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    // ===== GETTERS Y SETTERS =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public MediaType getType() { return type; }
    public void setType(MediaType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public boolean isViewed() { return viewed; }
    public void setViewed(boolean viewed) { this.viewed = viewed; }

    public long getAddedDate() { return addedDate; }
    public void setAddedDate(long addedDate) { this.addedDate = addedDate; }

    public Integer getTotalEpisodes() { return totalEpisodes; }
    public void setTotalEpisodes(Integer totalEpisodes) { this.totalEpisodes = totalEpisodes; }

    public Set<Integer> getWatchedEpisodes() { return watchedEpisodes; }
    public void setWatchedEpisodes(Set<Integer> watchedEpisodes) { this.watchedEpisodes = watchedEpisodes; }

    public boolean isEpisodeWatched(int episodeNumber) {
        return watchedEpisodes.contains(episodeNumber);
    }

    public void setEpisodeWatched(int episodeNumber, boolean watched) {
        if (watched) {
            watchedEpisodes.add(episodeNumber);
        } else {
            watchedEpisodes.remove(episodeNumber);
        }
    }

    public List<String> getPlatforms() { return platforms; }
    public void setPlatforms(List<String> platforms) { this.platforms = platforms; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getTrailerKey() { return trailerKey; }
    public void setTrailerKey(String trailerKey) { this.trailerKey = trailerKey; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public Integer getAnilistId() { return anilistId; }
    public void setAnilistId(Integer anilistId) { this.anilistId = anilistId; }

    public Integer getIgdbId() { return igdbId; }
    public void setIgdbId(Integer igdbId) { this.igdbId = igdbId; }

    /**
     * Reconstruye un MediaItem equivalente a partir de este favorito,
     * para poder reutilizar la misma tarjeta visual y la misma pantalla
     * de detalle que se usan en los resultados de búsqueda.
     */
    public MediaItem toMediaItem() {
        MediaItem item = new MediaItem();
        item.setType(this.type);
        item.setTitle(this.title);
        item.setDescription(this.description);
        item.setImageUrl(this.imageUrl);
        item.setYear(this.year);
        item.setScore(this.score);
        item.setExternalUrl(this.externalUrl);
        item.setEpisodes(this.totalEpisodes);
        item.setPlatforms(this.platforms);
        item.setGenres(this.genres);
        item.setTmdbId(this.tmdbId);
        item.setTrailerKey(this.trailerKey);
        item.setPreviewUrl(this.previewUrl);
        item.setAnilistId(this.anilistId);
        item.setIgdbId(this.igdbId);
        return item;
    }
}