package com.minemenu.items;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Item Creator, Potion Creator and Mob Creator: turn the forms' text into real items and mobs. Lenient: unknown
 * ids are reported in chat and skipped, numbers out of range are clamped. Server thread only.
 */
public final class Creators {
    private Creators() {
    }

    static Identifier id(String text) {
        String t = text.trim().toLowerCase().replace(' ', '_');
        return Identifier.tryParse(t.contains(":") ? t : "minecraft:" + t);
    }

    static double number(Map<String, String> values, String key, double fallback) {
        try {
            String text = values.getOrDefault(key, "").trim();
            return text.isEmpty() ? fallback : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** "&c" colour codes, like signs in many servers. */
    static Text colored(String text) {
        return Text.literal(text.replace('&', '§'));
    }

    private static void warn(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Text.translatable(key, args).formatted(Formatting.YELLOW), false);
    }

    private static void give(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    /** Item Creator: item, name, lore ("line 1 | line 2"), enchantments ("sharpness 255, looting 10"), attributes, flags. */
    public static void createItem(ServerPlayerEntity player, Map<String, String> v) {
        Identifier itemId = id(v.getOrDefault("item", "diamond_sword"));
        Item item = itemId == null ? Items.AIR : Registries.ITEM.get(itemId);
        if (item == Items.AIR) {
            warn(player, "minemenu.creator.unknown_item", v.get("item"));
            return;
        }
        ItemStack stack = new ItemStack(item, (int) Math.max(1, Math.min(item.getMaxCount(), number(v, "count", 1))));
        String name = v.getOrDefault("name", "").trim();
        if (!name.isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, colored(name));
        }
        String lore = v.getOrDefault("lore", "").trim();
        if (!lore.isEmpty()) {
            List<Text> lines = new ArrayList<>();
            for (String line : lore.split("\\|")) {
                lines.add(colored(line.trim()));
            }
            stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }
        boolean hide = "true".equals(v.get("Hide tooltips"));
        String enchants = v.getOrDefault("enchantments", "").trim();
        if (!enchants.isEmpty()) {
            var registry = player.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            for (String part : enchants.split(",")) {
                String[] words = part.trim().split("\\s+");
                if (words.length == 0 || words[0].isEmpty()) {
                    continue;
                }
                int level = 1;
                try {
                    level = words.length > 1 ? Integer.parseInt(words[words.length - 1]) : 1;
                } catch (NumberFormatException ignored) {
                }
                String enchantName = words.length > 1 ? String.join("_", java.util.Arrays.copyOf(words, words.length - 1)) : words[0];
                Identifier enchantId = id(enchantName);
                Optional<? extends RegistryEntry<net.minecraft.enchantment.Enchantment>> entry = enchantId == null ? Optional.empty() : registry.getEntry(enchantId);
                if (entry.isEmpty()) {
                    warn(player, "minemenu.creator.unknown_enchantment", enchantName);
                    continue;
                }
                builder.set(entry.get(), Math.max(1, Math.min(255, level)));
            }
            stack.set(DataComponentTypes.ENCHANTMENTS, builder.build().withShowInTooltip(!hide));
        }
        AttributeModifiersComponent.Builder attributes = AttributeModifiersComponent.builder();
        boolean anyAttribute = false;
        anyAttribute |= attribute(attributes, EntityAttributes.GENERIC_ATTACK_DAMAGE, "damage", number(v, "damage", 0), AttributeModifierSlot.MAINHAND);
        anyAttribute |= attribute(attributes, EntityAttributes.GENERIC_ATTACK_SPEED, "speed", number(v, "attack speed", 0), AttributeModifierSlot.MAINHAND);
        anyAttribute |= attribute(attributes, EntityAttributes.GENERIC_MAX_HEALTH, "health", number(v, "health", 0), AttributeModifierSlot.ANY);
        anyAttribute |= attribute(attributes, EntityAttributes.GENERIC_ARMOR, "armor", number(v, "armor", 0), AttributeModifierSlot.ANY);
        if (anyAttribute) {
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributes.build().withShowInTooltip(!hide));
        }
        if ("true".equals(v.get("Unbreakable"))) {
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(!hide));
        }
        if (hide) {
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        }
        give(player, stack);
        player.sendMessage(Text.translatable("minemenu.text.created_s", stack.getName().getString()).formatted(Formatting.GREEN), true);
    }

    private static boolean attribute(AttributeModifiersComponent.Builder builder, RegistryEntry<EntityAttribute> attribute, String name,
                                     double value, AttributeModifierSlot slot) {
        if (value == 0 || !Double.isFinite(value)) {
            return false;
        }
        builder.add(attribute, new EntityAttributeModifier(Identifier.of("minemenu", "creator_" + name), Math.max(-1024, Math.min(1024, value)),
                EntityAttributeModifier.Operation.ADD_VALUE), slot);
        return true;
    }

    /** "speed 5 60, strength 10 120": effect, level, seconds. */
    static List<StatusEffectInstance> effects(ServerPlayerEntity player, String text) {
        List<StatusEffectInstance> list = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] words = part.trim().split("\\s+");
            if (words.length == 0 || words[0].isEmpty()) {
                continue;
            }
            Identifier effectId = id(words[0]);
            var entry = effectId == null ? Optional.<RegistryEntry.Reference<net.minecraft.entity.effect.StatusEffect>>empty() : Registries.STATUS_EFFECT.getEntry(effectId);
            if (entry.isEmpty()) {
                warn(player, "minemenu.creator.unknown_effect", words[0]);
                continue;
            }
            int level = 1;
            int seconds = 60;
            try {
                if (words.length > 1) level = Integer.parseInt(words[1]);
                if (words.length > 2) seconds = Integer.parseInt(words[2]);
            } catch (NumberFormatException ignored) {
            }
            list.add(new StatusEffectInstance(entry.get(), Math.max(1, Math.min(1_000_000, seconds)) * 20, Math.max(0, Math.min(254, level - 1))));
        }
        return list;
    }

    /** Potion Creator: type (potion, splash, lingering, arrow), effects, colour. */
    public static void createPotion(ServerPlayerEntity player, Map<String, String> v) {
        String type = v.getOrDefault("type", "potion").trim().toLowerCase();
        Item item = switch (type) {
            case "splash" -> Items.SPLASH_POTION;
            case "lingering" -> Items.LINGERING_POTION;
            case "arrow", "arrows" -> Items.TIPPED_ARROW;
            default -> Items.POTION;
        };
        List<StatusEffectInstance> list = effects(player, v.getOrDefault("effects", "speed 2 60"));
        if (list.isEmpty()) {
            return;
        }
        int color = 0xFF55FF;
        try {
            String hex = v.getOrDefault("color", "").replace("#", "").trim();
            if (!hex.isEmpty()) color = Integer.parseInt(hex, 16);
        } catch (NumberFormatException ignored) {
        }
        ItemStack stack = new ItemStack(item, item == Items.TIPPED_ARROW ? 64 : 1);
        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(color), list));
        String name = v.getOrDefault("name", "").trim();
        if (!name.isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, colored(name));
        }
        give(player, stack);
        player.sendMessage(Text.translatable("minemenu.text.created_s", stack.getName().getString()).formatted(Formatting.GREEN), true);
    }

    /** Mob Creator: spawns the mob where you look, with name, health, baby, no AI, equipment, effects and a rider. */
    public static void createMob(ServerPlayerEntity player, Map<String, String> v) {
        HitResult hit = player.raycast(64, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            warn(player, "minemenu.creator.look_at_block");
            return;
        }
        BlockPos at = blockHit.getBlockPos().offset(blockHit.getSide());
        ServerWorld world = player.getServerWorld();
        Entity mob = spawnOne(player, world, v.getOrDefault("mob", "zombie"), at, v);
        if (mob == null) {
            return;
        }
        String rider = v.getOrDefault("rider", "").trim();
        if (!rider.isEmpty()) {
            Entity riding = spawnOne(player, world, rider, at, Map.of());
            if (riding != null) {
                riding.startRiding(mob, true);
            }
        }
        player.sendMessage(Text.translatable("minemenu.text.spawned_s", mob.getName().getString()).formatted(Formatting.GREEN), true);
    }

    private static Entity spawnOne(ServerPlayerEntity player, ServerWorld world, String idText, BlockPos at, Map<String, String> v) {
        Identifier typeId = id(idText);
        EntityType<?> type = typeId == null ? null : Registries.ENTITY_TYPE.getOrEmpty(typeId).orElse(null);
        if (type == null) {
            warn(player, "minemenu.creator.unknown_mob", idText);
            return null;
        }
        Entity entity = type.spawn(world, at, SpawnReason.COMMAND);
        if (entity == null) {
            warn(player, "minemenu.creator.cant_spawn", idText);
            return null;
        }
        String name = v.getOrDefault("name", "").trim();
        if (!name.isEmpty()) {
            entity.setCustomName(colored(name));
            entity.setCustomNameVisible(true);
        }
        if (entity instanceof MobEntity mob) {
            mob.setPersistent();
            if ("true".equals(v.get("Baby"))) mob.setBaby(true);
            if ("true".equals(v.get("No AI"))) mob.setAiDisabled(true);
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            String[] parts = v.getOrDefault("equipment", "").split(",");
            for (int i = 0; i < Math.min(slots.length, parts.length); i++) {
                Identifier itemId = parts[i].isBlank() ? null : id(parts[i]);
                Item item = itemId == null ? Items.AIR : Registries.ITEM.get(itemId);
                if (item != Items.AIR) {
                    mob.equipStack(slots[i], new ItemStack(item));
                    mob.setEquipmentDropChance(slots[i], 0);
                }
            }
        }
        if (entity instanceof LivingEntity living) {
            double health = number(v, "health", 0);
            var max = living.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (health > 0 && max != null) {
                max.setBaseValue(Math.min(1024, health));
                living.setHealth(living.getMaxHealth());
            }
            for (StatusEffectInstance effect : effects(player, v.getOrDefault("effects", ""))) {
                living.addStatusEffect(effect);
            }
        }
        return entity;
    }
}
