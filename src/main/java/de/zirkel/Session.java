package de.zirkel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zustand und Vorschau eines Spielers.
 * Die Vorschau besteht aus BlockDisplay-Entities: Sie haben keine Kollision und keine
 * Hitbox, man kann also Blöcke direkt "durch" die Vorschau hindurch platzieren.
 * Sie sind nur für den Besitzer sichtbar und werden nicht gespeichert.
 */
final class Session {

    static final int MAX_RADIUS = 64;

    record Pos(int x, int y, int z) {}

    private final ZirkelPlugin plugin;
    private final UUID owner;
    private final Map<Pos, BlockDisplay> displays = new HashMap<>();

    private World world;
    private int cx, cy, cz;
    private boolean placed;

    private int radius = 8;
    private Orientation orientation = Orientation.FLOOR;
    /** -1 = Vollkreis, 0..3 = Halbkreis (+v, +u, -v, -u) */
    private int half = -1;
    private BlockData blockData = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();

    Session(ZirkelPlugin plugin, UUID owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    // ---------------------------------------------------------------- Steuerung

    boolean isPlaced() {
        return placed;
    }

    void setCenter(Block block) {
        if (world != null && !world.equals(block.getWorld())) {
            clear();
        }
        world = block.getWorld();
        cx = block.getX();
        cy = block.getY();
        cz = block.getZ();
        placed = true;
        refresh();
    }

    void setRadius(int r) {
        radius = Math.max(1, Math.min(MAX_RADIUS, r));
        refresh();
    }

    int getRadius() {
        return radius;
    }

    void move(int dx, int dy, int dz) {
        if (!placed) return;
        cx += dx;
        cz += dz;
        cy = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, cy + dy));
        refresh();
    }

    void cycleOrientation() {
        orientation = orientation.next();
        refresh();
    }

    void setOrientation(Orientation o) {
        orientation = o;
        refresh();
    }

    /** Vollkreis -> Halbkreis (4 Richtungen) -> Vollkreis ... */
    void cycleForm() {
        half = half >= 3 ? -1 : half + 1;
        refresh();
    }

    void setFull() {
        half = -1;
        refresh();
    }

    void setBlock(Material material) {
        blockData = material.createBlockData();
        for (BlockDisplay d : displays.values()) {
            if (d.isValid()) d.setBlock(blockData);
        }
    }

    /** Entfernt Vorschau und Mittelpunkt. */
    void clear() {
        for (BlockDisplay d : displays.values()) {
            d.remove();
        }
        displays.clear();
        placed = false;
    }

    String status() {
        String form = half < 0 ? "Vollkreis" : "Halbkreis (" + halfLabel() + ")";
        String center = placed ? cx + " " + cy + " " + cz : "nicht gesetzt";
        return "Radius " + radius + " | " + orientation.label + " | " + form + " | Mitte: " + center;
    }

    private String halfLabel() {
        return switch (half) {
            case 0 -> orientation.vPos;
            case 1 -> orientation.uPos;
            case 2 -> orientation.vNeg;
            default -> orientation.uNeg;
        };
    }

    // ---------------------------------------------------------------- Form berechnen

    private boolean inside(int u, int v) {
        return u * u + v * v <= radius * radius + radius;
    }

    private boolean inHalf(int u, int v) {
        return switch (half) {
            case 0 -> v >= 0;
            case 1 -> u >= 0;
            case 2 -> v <= 0;
            case 3 -> u <= 0;
            default -> true;
        };
    }

    private Pos map(int u, int v) {
        return switch (orientation) {
            case FLOOR -> new Pos(cx + u, cy, cz + v);
            case WALL_X -> new Pos(cx + u, cy + v, cz);
            case WALL_Z -> new Pos(cx, cy + v, cz + u);
        };
    }

    /** Nur der Rand (1 Block dick, lückenlos) - damit man innen frei bauen kann. */
    private Set<Pos> computeShape() {
        Set<Pos> result = new HashSet<>();
        int r = radius;
        for (int u = -r - 1; u <= r + 1; u++) {
            for (int v = -r - 1; v <= r + 1; v++) {
                if (!inside(u, v)) continue;
                if (inside(u + 1, v) && inside(u - 1, v) && inside(u, v + 1) && inside(u, v - 1)) continue;
                if (!inHalf(u, v)) continue;
                Pos p = map(u, v);
                if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight()) continue;
                result.add(p);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- Vorschau

    void refresh() {
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !placed || world == null) {
            clear();
            return;
        }

        Set<Pos> wanted = computeShape();

        // Überflüssige bzw. ungültige entfernen
        Iterator<Map.Entry<Pos, BlockDisplay>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Pos, BlockDisplay> e = it.next();
            if (!e.getValue().isValid() || !wanted.contains(e.getKey())) {
                e.getValue().remove();
                it.remove();
            }
        }

        // Fehlende erzeugen
        for (Pos pos : wanted) {
            if (displays.containsKey(pos)) continue;
            Location loc = new Location(world, pos.x(), pos.y(), pos.z());
            BlockDisplay d = world.spawn(loc, BlockDisplay.class, e -> {
                e.setBlock(blockData);
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.setViewRange(2.0f);
                // minimal kleiner als ein Block, damit sich Nachbarn nicht überlagern
                e.setTransformation(new Transformation(
                        new Vector3f(0.02f, 0.02f, 0.02f),
                        new AxisAngle4f(),
                        new Vector3f(0.96f, 0.96f, 0.96f),
                        new AxisAngle4f()));
            });
            player.showEntity(plugin, d);
            displays.put(pos, d);
        }
    }
}
