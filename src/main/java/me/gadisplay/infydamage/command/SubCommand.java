package me.gadisplay.infydamage.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/** Un subcomando registrado bajo {@link BaseCommand} (ej. {@code /infydamage reload}). */
public interface SubCommand {

    /** Nombre literal que matchea contra {@code args[0]} (ej. "reload", "simulate"). */
    String getName();

    /** Permiso requerido para ejecutarlo. {@code null} = cualquiera puede ejecutarlo. */
    default String getPermission() {
        return null;
    }

    void execute(CommandSender sender, String[] args);

    /** Sugerencias de tab-complete para args más allá de {@code args[0]}. */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    default String getDescription() {
        return "";
    }
}
