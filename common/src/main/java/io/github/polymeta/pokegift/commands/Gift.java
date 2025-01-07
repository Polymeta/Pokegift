package io.github.polymeta.pokegift.commands;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.permission.CobblemonPermission;
import com.cobblemon.mod.common.api.permission.PermissionLevel;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.command.argument.PartySlotArgumentType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.polymeta.pokegift.Pokegift;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

public class Gift {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var giftCommand = dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("pokegift")
                        .requires(req -> Cobblemon.INSTANCE.getPermissionValidator().hasPermission(req,
                                new CobblemonPermission("pokegift.command.gift.base", PermissionLevel.NONE)))
                        .then(Commands.argument("slot", PartySlotArgumentType.Companion.partySlot())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("confirmation", StringArgumentType.greedyString()).executes(ExecuteWithConfirm))
                                        .executes(Execute)))
        );
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("pgift").redirect(giftCommand));
    }

    private static final ConcurrentSkipListSet<UUID> playersOnCooldown = new ConcurrentSkipListSet<>();

    private static final Command<CommandSourceStack> Execute = context -> {
        var slot = PartySlotArgumentType.Companion.getPokemon(context, "slot");
        var player = context.getSource().getPlayerOrException();
        var targetPlayer = EntityArgument.getPlayer(context, "player");
        if(player.getUUID().equals(targetPlayer.getUUID())) {
            player.sendSystemMessage(Pokegift.config.messages.errorCantGiftYourself(player.registryAccess()));
            return Command.SINGLE_SUCCESS;
        }
        var slotNo = ((PartyPosition)slot.getStoreCoordinates().get().getPosition()).getSlot();
        var canBypass = Cobblemon.INSTANCE.getPermissionValidator().hasPermission(player,
                new CobblemonPermission("pokegift.command.gift.bypass",
                        PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS));
        if(playersOnCooldown.contains(player.getUUID()) && !canBypass && Pokegift.config.cooldownEnabled) {
            player.sendSystemMessage(Pokegift.config.messages.cooldownFeedback(player.registryAccess()));
            return Command.SINGLE_SUCCESS;
        }
        if(isPokemonForbidden(slot) && !canBypass) {
            player.sendSystemMessage(Pokegift.config.messages.pokemonNotAllowed(player.registryAccess()));
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Pokegift.config.messages.pokegiftFeedback(slot, slotNo, targetPlayer));

        return Command.SINGLE_SUCCESS;
    };

    private static final Command<CommandSourceStack> ExecuteWithConfirm = context -> {
        var slot = PartySlotArgumentType.Companion.getPokemon(context, "slot");
        var confirmation = StringArgumentType.getString(context, "confirmation");
        if(!confirmation.trim().equals("--confirm")){
            return Execute.run(context);
        }
        var player = context.getSource().getPlayerOrException();
        var targetPlayer = EntityArgument.getPlayer(context, "player");
        if(player.getUUID().equals(targetPlayer.getUUID())) {
            player.sendSystemMessage(Pokegift.config.messages.errorCantGiftYourself(player.registryAccess()));
            return Command.SINGLE_SUCCESS;
        }
        var canBypass = Cobblemon.INSTANCE.getPermissionValidator().hasPermission(player,
                new CobblemonPermission("pokegift.command.gift.bypass",
                        PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS));
        if(playersOnCooldown.contains(player.getUUID()) && !canBypass && Pokegift.config.cooldownEnabled) {
            player.sendSystemMessage(Pokegift.config.messages.cooldownFeedback(player.registryAccess()));
            return Command.SINGLE_SUCCESS;
        }
        if(isPokemonForbidden(slot) && !canBypass) {
            player.sendSystemMessage(Pokegift.config.messages.pokemonNotAllowed(player.registryAccess()));
            return Command.SINGLE_SUCCESS;
        }
        var playerParty = Cobblemon.INSTANCE.getStorage().getParty(player);
        var targetParty = Cobblemon.INSTANCE.getStorage().getParty(targetPlayer);
        if(playerParty.remove(slot)) {
            if(!targetParty.add(slot)) {
                player.sendSystemMessage(Pokegift.config.messages.errorCouldntGivePokemon(targetPlayer));
                playerParty.add(slot); //give pokemon back if something went wrong
                return Command.SINGLE_SUCCESS;
            }
        }
        else {
            player.sendSystemMessage(Pokegift.config.messages.errorCouldntTakePokemon(player.registryAccess()));
            return Command.SINGLE_SUCCESS;
        }

        if(Pokegift.config.cooldownEnabled && !canBypass) {
            playersOnCooldown.add(player.getUUID());
            Pokegift.scheduler.schedule(() -> {playersOnCooldown.remove(player.getUUID());}, Pokegift.config.cooldown, TimeUnit.MINUTES);
        }
        player.sendSystemMessage(Pokegift.config.messages.successFeedback(player.registryAccess()));
        targetPlayer.sendSystemMessage(Pokegift.config.messages.receivedPokemonFeedback(player, slot));
        return Command.SINGLE_SUCCESS;
    };

    private static boolean isPokemonForbidden(Pokemon pokemon) {
        for (String property : Pokegift.config.blacklist) {
            if(PokemonProperties.Companion.parse(property, " ", "=").matches(pokemon)) {
                return true;
            }
        }
        return false;
    }

}
