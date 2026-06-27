package me.gadisplay.infydamage.command;

import me.gadisplay.infydamage.InfyDamage;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calco de {@code BaseCommand} de THCore (CONTEXT.md §11.2), simplificado: sin
 * {@code MessageManager} propio, mensajes en texto plano con {@link ChatColor}.
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final InfyDamage plugin;

    // LinkedHashMap para preservar el orden de registro en los listados de ayuda.
    private final Map<String, SubCommand> subcommands = new LinkedHashMap<>();

    protected BaseCommand(InfyDamage plugin) {
        this.plugin = plugin;
    }

    public void register(Plugin owningPlugin, String commandName) {
        if (!(owningPlugin instanceof JavaPlugin javaPlugin)) {
            plugin.getLogger().warning("No se pudo registrar el comando '" + commandName + "': el plugin no es un JavaPlugin.");
            return;
        }
        PluginCommand cmd = javaPlugin.getCommand(commandName);
        if (cmd == null) {
            plugin.getLogger().warning("Comando '" + commandName + "' no existe en plugin.yml de " + owningPlugin.getName());
            return;
        }
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }

    protected void registerSubCommand(SubCommand sub) {
        subcommands.put(sub.getName().toLowerCase(), sub);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            onNoArgs(sender, label);
            return true;
        }

        SubCommand sub = subcommands.get(args[0].toLowerCase());
        if (sub == null) {
            sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /" + label + " para ver la lista.");
            return true;
        }

        if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
            sender.sendMessage(ChatColor.RED + "No tenés permiso para usar este comando.");
            return true;
        }

        sub.execute(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return subcommands.values().stream()
                    .filter(s -> s.getPermission() == null || sender.hasPermission(s.getPermission()))
                    .map(SubCommand::getName)
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        SubCommand sub = subcommands.get(args[0].toLowerCase());
        if (sub != null && (sub.getPermission() == null || sender.hasPermission(sub.getPermission()))) {
            return sub.tabComplete(sender, args);
        }

        return Collections.emptyList();
    }

    /** Llamado cuando el comando se ejecuta sin argumentos. */
    protected void onNoArgs(CommandSender sender, String label) {
        String available = subcommands.keySet().stream().collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.YELLOW + "Subcomandos disponibles: " + ChatColor.WHITE + available);
    }

    public Collection<SubCommand> getSubCommands() {
        return Collections.unmodifiableCollection(subcommands.values());
    }
}
