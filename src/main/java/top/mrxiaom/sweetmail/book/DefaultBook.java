package top.mrxiaom.sweetmail.book;

import net.kyori.adventure.inventory.Book;
import org.bukkit.Bukkit;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import top.mrxiaom.sweetmail.SweetMail;
import top.mrxiaom.sweetmail.database.entry.Mail;
import top.mrxiaom.sweetmail.depend.PAPI;
import top.mrxiaom.sweetmail.func.AbstractPluginHolder;
import top.mrxiaom.sweetmail.func.data.Draft;
import top.mrxiaom.sweetmail.gui.IGui;
import top.mrxiaom.sweetmail.utils.Util;

import java.util.*;

public class DefaultBook extends AbstractPluginHolder implements IBook, Listener {
    private boolean enableReturnWhenMove = false;
    private boolean openForMail, openForDraft, useLegacyBook;
    public Map<UUID, Listener> listeners = new HashMap<>();
    public DefaultBook(SweetMail plugin) {
        super(plugin);
        registerEvents(this);
        register();
    }

    @Override
    public void reloadConfig(MemoryConfiguration config) {
        enableReturnWhenMove = config.getBoolean("book.return-when-move");
        openForMail = config.getBoolean("book.open-for-mail", true);
        openForDraft = config.getBoolean("book.open-for-draft", true);
        useLegacyBook = config.getBoolean("book.use-legacy-book", false);
    }

    @Override
    public void openBook(Player player, Draft draft) {
        if (!openForDraft) return;
        IGui gui = plugin.getGuiManager().getOpeningGui(player);
        List<String> content = new ArrayList<>();
        if (draft.advPlaceholders) {
            content.addAll(PAPI.setPlaceholders(player, draft.content));
        } else {
            content.addAll(draft.content);
        }
        Book book = Util.legacyBook(content, player.getName());
        if (useLegacyBook) {
            Util.openBookLegacy(player, book);
        } else {
            Util.openBook(player, book);
        }
        afterOpenBook(gui);
    }

    @Override
    public void openBook(Player player, Mail mail) {
        if (!openForMail) return;
        IGui gui = plugin.getGuiManager().getOpeningGui(player);
        Book book = mail.generateBook(player);
        if (useLegacyBook) {
            Util.openBookLegacy(player, book);
        } else {
            Util.openBook(player, book);
        }
        afterOpenBook(gui);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        handleListenerUpdate(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        handleListenerUpdate(e.getPlayer().getUniqueId());
    }

    private void handleListenerUpdate(UUID uuid) {
        Listener old = listeners.remove(uuid);
        if (old != null) {
            HandlerList.unregisterAll(old);
        }
    }

    private void afterOpenBook(IGui gui) {
        if (gui == null || !enableReturnWhenMove) return;
        UUID uuid = gui.getPlayer().getUniqueId();
        handleListenerUpdate(uuid);
        Listener listener = new Listener() {
            final long checkStartTime = System.currentTimeMillis() + 1000L;
            boolean done = false;
            @EventHandler
            public void onMove(PlayerMoveEvent e) {
                if (e.isCancelled() || System.currentTimeMillis() < checkStartTime) return;
                if (done) {
                    HandlerList.unregisterAll(this);
                    listeners.remove(uuid);
                    return;
                }
                if (e.getPlayer().getUniqueId().equals(uuid)) {
                    done = true;
                    HandlerList.unregisterAll(this);
                    listeners.remove(uuid);
                    gui.open();
                }
            }
        };
        plugin.getScheduler().runLater(() -> {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            listeners.put(uuid, listener);
        }, 10L);
    }
}
