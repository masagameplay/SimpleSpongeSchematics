package dev.masa.simplespongeschematics;

import org.spongepowered.api.world.volume.archetype.ArchetypeVolume;
import org.spongepowered.math.vector.Vector3i;

import java.util.UUID;

public class SchematicPlayer {

    private final UUID uniqueId;
    private Vector3i firstPos;
    private Vector3i secondPos;
    private Vector3i origin;
    private ArchetypeVolume clipboard;

    public SchematicPlayer(UUID uniqueId) {
        this.uniqueId = uniqueId;
    }


    public UUID uniqueId() {
        return uniqueId;
    }

    public Vector3i firstPos() {
        return firstPos;
    }

    public void firstPos(Vector3i firstPos) {
        this.firstPos = firstPos;
    }

    public Vector3i secondPos() {
        return secondPos;
    }

    public void secondPos(Vector3i secondPos) {
        this.secondPos = secondPos;
    }

    public Vector3i origin() {
        return origin;
    }

    public void origin(Vector3i origin) {
        this.origin = origin;
    }

    public ArchetypeVolume clipboard() {
        return clipboard;
    }

    public void clipboard(ArchetypeVolume clipboard) {
        this.clipboard = clipboard;
    }
}
