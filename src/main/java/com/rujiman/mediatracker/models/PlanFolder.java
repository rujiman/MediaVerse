package com.rujiman.mediatracker.models;

/**
 * Carpeta dentro de una de las listas "Pienso ver / Pienso jugar / Pienso
 * escuchar", para agrupar items relacionados (por ejemplo, una carpeta
 * "Re:Zero" que contiene todas sus temporadas).
 *
 * Cada lista (PlanService.ListKind) tiene su propio conjunto de carpetas,
 * completamente independientes entre listas — una carpeta de "Pienso ver"
 * no es visible ni compartida con "Pienso jugar".
 */
public class PlanFolder {
    private String id;
    private String name;
    private long createdDate;

    public PlanFolder() {}

    public PlanFolder(String name) {
        this.id = generateId();
        this.name = name;
        this.createdDate = System.currentTimeMillis();
    }

    private String generateId() {
        return "folder_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCreatedDate() { return createdDate; }
    public void setCreatedDate(long createdDate) { this.createdDate = createdDate; }
}