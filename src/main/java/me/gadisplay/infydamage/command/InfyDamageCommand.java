package me.gadisplay.infydamage.command;

import me.gadisplay.infydamage.InfyDamage;
import me.gadisplay.infydamage.formula.DamageSimulationResult;
import me.gadisplay.infydamage.formula.DamageSimulator;
import me.gadisplay.infydamage.hook.impl.NexoHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Comando principal {@code /infydamage}: {@code reload} y {@code simulate}. */
public final class InfyDamageCommand extends BaseCommand {

    public InfyDamageCommand(InfyDamage plugin) {
        super(plugin);

        registerSubCommand(new SubCommand() {
            @Override
            public String getName() {
                return "reload";
            }

            @Override
            public String getPermission() {
                return "infydamage.admin";
            }

            @Override
            public String getDescription() {
                return "Recarga config.yml en caliente, sin reiniciar el servidor.";
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                plugin.getConfigManager().reload();
                sender.sendMessage(ChatColor.GREEN + "[InfyDamage] config.yml recargado.");
            }
        });

        registerSubCommand(new SubCommand() {
            @Override
            public String getName() {
                return "simulate";
            }

            @Override
            public String getPermission() {
                return "infydamage.admin";
            }

            @Override
            public String getDescription() {
                return "Simula un golpe entre dos jugadores conectados, sin aplicar daño real.";
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uso: /infydamage simulate <atacante> <defensor>");
                    return;
                }

                Player attacker = Bukkit.getPlayer(args[1]);
                if (attacker == null) {
                    sender.sendMessage(ChatColor.RED + "Atacante no encontrado o desconectado: " + args[1]);
                    return;
                }

                Player defender = Bukkit.getPlayer(args[2]);
                if (defender == null) {
                    sender.sendMessage(ChatColor.RED + "Defensor no encontrado o desconectado: " + args[2]);
                    return;
                }

                NexoHook nexoHook = plugin.getHookManager().getHook(NexoHook.class);
                DamageSimulationResult result = DamageSimulator.simulate(
                        plugin.getConfigManager().getFormulaConfig(), nexoHook, attacker, defender);

                sender.sendMessage(ChatColor.GOLD + "[InfyDamage] Simulación: " + ChatColor.WHITE + attacker.getName()
                        + ChatColor.GOLD + " ataca a " + ChatColor.WHITE + defender.getName());

                if (!result.appliesFormula()) {
                    sender.sendMessage(ChatColor.GRAY + "  " + attacker.getName()
                            + " no tiene un arma reconocida en mano (daño de equipo " + format(result.equipmentDamage())
                            + " ≤ umbral configurado). Vanilla aplicaría su propia mitigación normal acá, sin pasar por la fórmula custom.");
                    return;
                }

                sender.sendMessage(ChatColor.YELLOW + "  Daño de equipo: " + ChatColor.WHITE
                        + format(result.equipmentDamage()) + ChatColor.GRAY
                        + " (ATTACK_DAMAGE " + format(result.attackAttribute())
                        + " + Filo " + result.sharpnessLevel() + " → +" + format(result.sharpnessBonus()) + ")");
                sender.sendMessage(ChatColor.YELLOW + "  Defensa: " + ChatColor.WHITE
                        + "Armadura " + format(result.armorTotal())
                        + " | Toughness " + format(result.toughnessTotal())
                        + " | Protección (suma niveles) " + result.protectionSum()
                        + ChatColor.GRAY + " → Puntos defensa " + format(result.defensePoints()));
                sender.sendMessage(ChatColor.YELLOW + "  Daño final mitigado: " + ChatColor.GREEN
                        + format(result.mitigatedDamage()));
            }

            private String format(double value) {
                return String.format("%.2f", value);
            }
        });

        registerSubCommand(new SubCommand() {
            @Override
            public String getName() {
                return "debug";
            }

            @Override
            public String getPermission() {
                return "infydamage.admin";
            }

            @Override
            public String getDescription() {
                return "Activa/desactiva el log de consola con el desglose real de cada golpe (PROBLEMS.md).";
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                boolean enabled = !plugin.getDebugState().isEnabled();
                plugin.getDebugState().setEnabled(enabled);
                sender.sendMessage((enabled ? ChatColor.GREEN : ChatColor.RED)
                        + "[InfyDamage] Debug de combate " + (enabled ? "activado" : "desactivado") + ".");
            }
        });
    }
}
