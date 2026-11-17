package com.rujiman.mediatracker.models;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base común para cualquier item que el usuario "trackea" (favorito o
 * plan). Evita duplicar los 15 campos idénticos entre FavoriteItem y
 * PlanItem: un campo nuevo se añade aquí una sola vez.
 */
public abstract class TrackedItem {

    protected String id;
    protected MediaType type;
    protected String title;
    protected String description;
    protected String imageUrl;
    protected Integer year;
    protected Integer score;
    protected String externalUrl;
    protected boolean viewed;
    protected long addedDate;

    // Episodios (ANIME / SERIES)
    protected Integer totalEpisodes;
    protected Set<Integer> watchedEpisodes = new HashSet<>();

    // Datos extra para el detalle
    protected List<String> platforms;
    protected List<String> genres;
    protected Integer tmdbId;
    protected String trailerKey;
    protected String previewUrl;
    protected Integer anilistId;
    protected Integer igdbId;

    protected TrackedItem() {}

    /** Rellena todos los campos comunes desde un MediaItem de búsqueda. */
    protected void fillFromMediaItem(MediaItem item) {
        this.id          = generateId();
        this.type        = item.getType();
        this.title       = item.getTitle();
        this.description = item.getDescription();
        this.imageUrl    = item.getImageUrl();
        this.year        = item.getYear();
        this.score       = item.getScore();
        this.externalUrl = item.getExternalUrl();
        this.viewed      = false;
        this.addedDate   = System.currentTimeMillis();
        this.totalEpisodes   = item.getEpisodes();
        this.watchedEpisodes = new HashSet<>();
        this.platforms   = item.getPlatforms();
        this.genres      = item.getGenres();
        this.tmdbId      = item.getTmdbId();
        this.trailerKey  = item.getTrailerKey();
        this.previewUrl  = item.getPreviewUrl();
        this.anilistId   = item.getAnilistId();
        this.igdbId      = item.getIgdbId();
    }

    protected static String generateId() {
        return System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    /** Reconstruye un MediaItem para reutilizar DetailView. */
    public MediaItem toMediaItem() {
        MediaItem item = new MediaItem();
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        item.setYear(year);
        item.setScore(score);
        item.setExternalUrl(externalUrl);
        item.setEpisodes(totalEpisodes);
        item.setPlatforms(platforms);
        item.setGenres(genres);
        item.setTmdbId(tmdbId);
        item.setTrailerKey(trailerKey);
        item.setPreviewUrl(previewUrl);
        item.setAnilistId(anilistId);
        item.setIgdbId(igdbId);
        return item;
    }

    public boolean isEpisodeWatched(int ep) { return watchedEpisodes.contains(ep); }
    public void setEpisodeWatched(int ep, boolean watched) {
        if (watched) watchedEpisodes.add(ep); else watchedEpisodes.remove(ep);
    }

    // ── Getters / Setters ──────────────────────────────────────────────
    public String getId()                   { return id; }
    public void setId(String id)            { this.id = id; }

    public MediaType getType()              { return type; }
    public void setType(MediaType t)        { this.type = t; }

    public String getTitle()                { return title; }
    public void setTitle(String t)          { this.title = t; }

    public String getDescription()          { return description; }
    public void setDescription(String d)    { this.description = d; }

    public String getImageUrl()             { return imageUrl; }
    public void setImageUrl(String u)       { this.imageUrl = u; }

    public Integer getYear()                { return year; }
    public void setYear(Integer y)          { this.year = y; }

    public Integer getScore()               { return score; }
    public void setScore(Integer s)         { this.score = s; }

    public String getExternalUrl()          { return externalUrl; }
    public void setExternalUrl(String u)    { this.externalUrl = u; }

    public boolean isViewed()               { return viewed; }
    public void setViewed(boolean v)        { this.viewed = v; }

    public long getAddedDate()              { return addedDate; }
    public void setAddedDate(long d)        { this.addedDate = d; }

    public Integer getTotalEpisodes()       { return totalEpisodes; }
    public void setTotalEpisodes(Integer e) { this.totalEpisodes = e; }

    public Set<Integer> getWatchedEpisodes()          { return watchedEpisodes; }
    public void setWatchedEpisodes(Set<Integer> set)  { this.watchedEpisodes = set; }

    public List<String> getPlatforms()      { return platforms; }
    public void setPlatforms(List<String> p){ this.platforms = p; }

    public List<String> getGenres()         { return genres; }
    public void setGenres(List<String> g)   { this.genres = g; }

    public Integer getTmdbId()              { return tmdbId; }
    public void setTmdbId(Integer id)       { this.tmdbId = id; }

    public String getTrailerKey()           { return trailerKey; }
    public void setTrailerKey(String k)     { this.trailerKey = k; }

    public String getPreviewUrl()           { return previewUrl; }
    public void setPreviewUrl(String u)     { this.previewUrl = u; }

    public Integer getAnilistId()           { return anilistId; }
    public void setAnilistId(Integer id)    { this.anilistId = id; }

    public Integer getIgdbId()              { return igdbId; }
    public void setIgdbId(Integer id)       { this.igdbId = id; }
}
