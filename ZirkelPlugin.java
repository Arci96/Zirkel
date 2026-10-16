package de.zirkel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ZirkelPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Session> sessions = new HashMap<>();
    private NamespacedKey itemKey;

    @Override
    public void onEnable() {
        itemKey = new NamespacedKey(this, "zirkel");
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("zirkel");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        sessions.values().forEach(Session::clear);
        sessions.clear();
    }

    // ---------------------------------------------------------------- Item

    private Session session(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), id -> new Session(this, id));
    }

    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Zirkel", NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Rechtsklick Block: Mittelpunkt setzen"),
                line("Rechtsklick Luft: Mittelpunkt auf deine Position"),
                line("Shift + Rechtsklick: Ausrichtung wechseln"),
                line("Linksklick: Vollkreis / Halbkreis wechseln"),
                line("Shift + Linksklick: Zirkel entfernen"),
                line("Shift + Mausrad: Radius ändern"),
                line("Q: hoch  |  Shift + Q: runter")));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private boolean isZirkel(ItemStack item) {
        return item != null
                && item.getType() == Material.COMPASS
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private void bar(Player p, Session s) {
        p.sendActionBar(Component.text(s.status(), NamedTextColor.AQUA));
    }

    // ---------------------------------------------------------------- Events

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!isZirkel(e.getItem())) return;
        Action action = e.getAction();
        if (action == Action.PHYSICAL) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("zirkel.use")) return;
        Session s = session(p);

        switch (action) {
            case RIGHT_CLICK_BLOCK -> {
                if (p.isSneaking()) {
                    s.cycleOrientation();
                } else {
                    Block clicked = e.getClickedBlock();
                    if (clicked != null) s.setCenter(clicked.getRelative(e.getBlockFace()));
                }
            }
            case RIGHT_CLICK_AIR -> {
                if (p.isSneaking()) {
                    s.cycleOrientation();
                } else {
                    s.setCenter(p.getLocation().getBlock());
                }
            }
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> {
                if (p.isSneaking()) {
                    s.clear();
                    p.sendActionBar(Component.text("Zirkel entfernt", NamedTextColor.YELLOW));
                    return;
                }
                s.cycleForm();
            }
            default -> {
                return;
            }
        }
        bar(p, s);
    }

    /** Shift + Mausrad = Radius ändern */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (!isZirkel(p.getInventory().getItem(e.getPreviousSlot()))) return;
        if (!p.hasPermission("zirkel.use")) return;

        e.setCancelled(true);
        int diff = e.getNewSlot() - e.getPreviousSlot();
        if (diff == 8) diff = -1;        // Wrap-around 0 -> 8
        else if (diff == -8) diff = 1;   // Wrap-around 8 -> 0

        Session s = session(p);
        s.setRadius(s.getRadius() + (diff < 0 ? 1 : -1)); // nach oben scrollen = größer
        bar(p, s);
    }

    /** Q = hoch, Shift + Q = runter */
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!isZirkel(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("zirkel.use")) return;
        Session s = session(p);
        s.move(0, p.isSneaking() ? -1 : 1, 0);
        bar(p, s);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) s.clear();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) s.clear();
    }

    // ---------------------------------------------------------------- Befehle

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Dieser Befehl ist nur für Spieler.");
            return true;
        }
        Session s = session(p);
        String sub = args.length == 0 ? "give" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "give" -> {
                p.getInventory().addItem(createItem());
                msg(p, "Du hast einen Zirkel erhalten.", NamedTextColor.GREEN);
            }
            case "radius" -> {
                Integer r = args.length > 1 ? parse(args[1]) : null;
                if (r == null) {
                    msg(p, "Benutzung: /zirkel radius <1-" + Session.MAX_RADIUS + ">", NamedTextColor.RED);
                } else {
                    s.setRadius(r);
                    bar(p, s);
                }
            }
            case "hoch", "runter" -> {
                Integer n = args.length > 1 ? parse(args[1]) : Integer.valueOf(1);
                if (n == null) {
                    msg(p, "Benutzung: /zirkel " + sub + " [Anzahl]", NamedTextColor.RED);
                } else {
                    requirePlaced(p, s, () -> s.move(0, sub.equals("hoch") ? n : -n, 0));
                }
            }
            case "move" -> {
                Integer dx = args.length > 3 ? parse(args[1]) : null;
                Integer dy = args.length > 3 ? parse(args[2]) : null;
                Integer dz = args.length > 3 ? parse(args[3]) : null;
                if (dx == null || dy == null || dz == null) {
                    msg(p, "Benutzung: /zirkel move <dx> <dy> <dz>", NamedTextColor.RED);
                } else {
                    requirePlaced(p, s, () -> s.move(dx, dy, dz));
                }
            }
            case "ausrichtung" -> {
                if (args.length < 2) {
                    s.cycleOrientation();
                } else {
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "boden" -> s.setOrientation(Orientation.FLOOR);
                        case "wand-x" -> s.setOrientation(Orientation.WALL_X);
                        case "wand-z" -> s.setOrientation(Orientation.WALL_Z);
                        default -> {
                            msg(p, "Benutzung: /zirkel ausrichtung <boden|wand-x|wand-z>", NamedTextColor.RED);
                            return true;
                        }
                    }
                }
                bar(p, s);
            }
            case "form" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("voll")) s.setFull();
                else s.cycleForm();
                bar(p, s);
            }
            case "block" -> {
                Material m = args.length > 1 ? Material.matchMaterial(args[1]) : null;
                if (m == null || !m.isBlock() || m.isAir()) {
                    msg(p, "Benutzung: /zirkel block <Blockname>  (z.B. red_stained_glass)", NamedTextColor.RED);
                } else {
                    s.setBlock(m);
                    msg(p, "Vorschau-Block: " + m.name().toLowerCase(Locale.ROOT), NamedTextColor.GREEN);
                }
            }
            case "loeschen" -> {
                s.clear();
                msg(p, "Zirkel entfernt.", NamedTextColor.YELLOW);
            }
            default -> msg(p, "Befehle: give, radius, hoch, runter, move, ausrichtung, form, block, loeschen",
                    NamedTextColor.GRAY);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "radius", "hoch", "runter", "move", "ausrichtung", "form", "block", "loeschen"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "ausrichtung" -> out.addAll(List.of("boden", "wand-x", "wand-z"));
                case "form" -> out.add("voll");
                case "block" -> {
                    for (Material m : Material.values()) {
                        if (m.isBlock() && !m.isAir() && !m.isLegacy()) out.add(m.getKey().getKey());
                    }
                }
                default -> { }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(prefix));
        return out;
    }

    // ---------------------------------------------------------------- Helfer

    private void requirePlaced(Player p, Session s, Runnable action) {
        if (!s.isPlaced()) {
            msg(p, "Setze zuerst einen Mittelpunkt (Rechtsklick mit dem Zirkel).", NamedTextColor.RED);
            return;
        }
        action.run();
        bar(p, s);
    }

    private void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Component.text("[Zirkel] ", NamedTextColor.DARK_AQUA).append(Component.text(text, color)));
    }

    private Integer parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
