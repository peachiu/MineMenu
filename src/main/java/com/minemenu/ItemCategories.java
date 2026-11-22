package com.minemenu;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.AnimalArmorItem;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Category words for the Give Item search, in Portuguese and English: type "comida" or "food" and
 * you get every food, "armadura" or "armor" for armor, and so on. Accents don't matter.
 */
public final class ItemCategories {
    /** A category: a name to show, the words that find it, and which items belong to it. */
    public record ItemCategory(String name, List<String> words, Predicate<Item> test) {
    }

    private static final Set<Item> WEAPONS_EXTRA = Set.of(Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE,
            Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW);
    private static final Set<Item> DRINKS = Set.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION,
            Items.MILK_BUCKET, Items.HONEY_BOTTLE, Items.OMINOUS_BOTTLE);

    private static final List<ItemCategory> CATEGORIES = List.of(
            new ItemCategory("Food", words("comida", "comidas", "alimento", "alimentos", "comer", "food", "eat"),
                    item -> has(item, DataComponentTypes.FOOD) || item == Items.CAKE),
            new ItemCategory("Drinks & Potions", words("bebida", "bebidas", "pocao", "pocoes", "drink", "drinks", "potion", "potions"),
                    DRINKS::contains),
            new ItemCategory("Armor", words("armadura", "armaduras", "roupa", "roupas", "protecao", "armor", "armour", "clothes"),
                    item -> isPlayerArmor(item) || item == Items.ELYTRA),
            new ItemCategory("Helmets", words("capacete", "capacetes", "chapeu", "helmet", "helmets", "hat"),
                    item -> armorSlot(item) == EquipmentSlot.HEAD),
            new ItemCategory("Chestplates", words("peitoral", "peitorais", "colete", "chestplate", "chestplates"),
                    item -> armorSlot(item) == EquipmentSlot.CHEST || item == Items.ELYTRA),
            new ItemCategory("Leggings", words("calcas", "calca", "perneira", "perneiras", "leggings", "pants"),
                    item -> armorSlot(item) == EquipmentSlot.LEGS),
            new ItemCategory("Boots", words("botas", "bota", "sapatos", "boots", "shoes"),
                    item -> armorSlot(item) == EquipmentSlot.FEET),
            new ItemCategory("Swords", words("espada", "espadas", "sword", "swords"), tag(ItemTags.SWORDS)),
            new ItemCategory("Pickaxes", words("picareta", "picaretas", "pickaxe", "pickaxes"), tag(ItemTags.PICKAXES)),
            new ItemCategory("Axes", words("machado", "machados", "axe", "axes"), tag(ItemTags.AXES)),
            new ItemCategory("Shovels", words("pa", "pas", "shovel", "shovels"), tag(ItemTags.SHOVELS)),
            new ItemCategory("Hoes", words("enxada", "enxadas", "hoe", "hoes"), tag(ItemTags.HOES)),
            new ItemCategory("Weapons", words("arma", "armas", "weapon", "weapons"),
                    item -> isIn(item, ItemTags.SWORDS) || isIn(item, ItemTags.AXES) || WEAPONS_EXTRA.contains(item)),
            new ItemCategory("Combat", words("combate", "luta", "combat", "pvp"), group(ItemGroups.COMBAT)),
            new ItemCategory("Tools", words("ferramenta", "ferramentas", "utilidades", "tool", "tools", "utilities"),
                    group(ItemGroups.TOOLS)),
            new ItemCategory("Building Blocks", words("bloco", "blocos", "construcao", "construir", "block", "blocks", "building"),
                    group(ItemGroups.BUILDING_BLOCKS)),
            new ItemCategory("Colored Blocks", words("cor", "cores", "colorido", "coloridos", "color", "colors", "colored"),
                    group(ItemGroups.COLORED_BLOCKS)),
            new ItemCategory("Nature", words("natureza", "natural", "plantas", "nature", "plants"), group(ItemGroups.NATURAL)),
            new ItemCategory("Functional", words("funcional", "uteis", "util", "functional", "useful"), group(ItemGroups.FUNCTIONAL)),
            new ItemCategory("Redstone", words("redstone", "mecanismo", "mecanismos", "circuito", "mechanism"), group(ItemGroups.REDSTONE)),
            new ItemCategory("Ingredients", words("ingrediente", "ingredientes", "material", "materiais", "ingredient", "ingredients", "materials"),
                    group(ItemGroups.INGREDIENTS)),
            new ItemCategory("Ores", words("minerio", "minerios", "ore", "ores"), ItemCategories::isOre),
            new ItemCategory("Spawn Eggs", words("ovo", "ovos", "spawn", "egg", "eggs", "mob", "mobs"),
                    item -> item instanceof SpawnEggItem),
            new ItemCategory("Wood", words("madeira", "madeiras", "tronco", "troncos", "tabua", "tabuas", "wood", "log", "logs", "planks"),
                    item -> isIn(item, ItemTags.LOGS) || isIn(item, ItemTags.PLANKS)),
            new ItemCategory("Wool", words("la", "las", "wool"), tag(ItemTags.WOOL)),
            new ItemCategory("Beds", words("cama", "camas", "bed", "beds"), tag(ItemTags.BEDS)),
            new ItemCategory("Flowers", words("flor", "flores", "flower", "flowers"), tag(ItemTags.FLOWERS)),
            new ItemCategory("Music Discs", words("disco", "discos", "musica", "musicas", "music", "disc", "discs"),
                    item -> has(item, DataComponentTypes.JUKEBOX_PLAYABLE)),
            new ItemCategory("Boats", words("barco", "barcos", "boat", "boats"), tag(ItemTags.BOATS)),
            new ItemCategory("Minecarts & Rails", words("carrinho", "carrinhos", "vagoneta", "trilho", "trilhos", "carril", "carris",
                    "minecart", "minecarts", "rail", "rails"),
                    item -> isIn(item, ItemTags.RAILS) || id(item).contains("minecart")),
            new ItemCategory("Dyes", words("corante", "corantes", "tinta", "tintas", "dye", "dyes"), item -> item instanceof DyeItem),
            new ItemCategory("Heads", words("cabeca", "cabecas", "cranio", "head", "heads", "skull", "skulls"), tag(ItemTags.SKULLS)),
            new ItemCategory("Buckets", words("balde", "baldes", "bucket", "buckets"), item -> id(item).endsWith("bucket")),
            new ItemCategory("Books", words("livro", "livros", "book", "books"), item -> id(item).contains("book")),
            new ItemCategory("Glass", words("vidro", "vidros", "glass"), item -> id(item).contains("glass")),
            new ItemCategory("Operator", words("operador", "admin", "comando", "comandos", "operator", "command"),
                    group(ItemGroups.OPERATOR)));

    /** Items in each creative tab, filled in by {@link #refreshGroups}. */
    private static final Map<RegistryKey<ItemGroup>, Set<Item>> groupItems = new HashMap<>();

    private ItemCategories() {
    }

    private static List<String> words(String... words) {
        return List.of(words);
    }

    /**
     * The category a single search word means: an exact category word, or the start of one
     * (4+ letters, so "armad" already means armor). Null if it isn't a category word.
     */
    public static ItemCategory match(String word) {
        String w = normalize(word);
        for (ItemCategory category : CATEGORIES) {
            if (category.words().contains(w)) {
                return category;
            }
        }
        if (w.length() >= 4) {
            for (ItemCategory category : CATEGORIES) {
                for (String candidate : category.words()) {
                    if (candidate.startsWith(w)) {
                        return category;
                    }
                }
            }
        }
        return null;
    }

    /** Lower case without accents, so "Poção" and "pocao" are the same. */
    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Works out which items are in which creative tab (like the creative inventory does). */
    public static void refreshGroups(MinecraftClient client) {
        if (client.world == null) {
            return;
        }
        ItemGroups.updateDisplayContext(client.world.getEnabledFeatures(), true, client.world.getRegistryManager());
        groupItems.clear();
        for (RegistryKey<ItemGroup> key : List.of(ItemGroups.BUILDING_BLOCKS, ItemGroups.COLORED_BLOCKS, ItemGroups.NATURAL,
                ItemGroups.FUNCTIONAL, ItemGroups.REDSTONE, ItemGroups.TOOLS, ItemGroups.COMBAT, ItemGroups.FOOD_AND_DRINK,
                ItemGroups.INGREDIENTS, ItemGroups.SPAWN_EGGS, ItemGroups.OPERATOR)) {
            Set<Item> items = new HashSet<>();
            for (ItemStack stack : Registries.ITEM_GROUP.getOrThrow(key).getDisplayStacks()) {
                items.add(stack.getItem());
            }
            groupItems.put(key, items);
        }
    }

    private static Predicate<Item> group(RegistryKey<ItemGroup> key) {
        return item -> groupItems.getOrDefault(key, Set.of()).contains(item);
    }

    private static Predicate<Item> tag(TagKey<Item> tag) {
        return item -> isIn(item, tag);
    }

    private static boolean isIn(Item item, TagKey<Item> tag) {
        return Registries.ITEM.getEntry(item).isIn(tag);
    }

    private static boolean has(Item item, net.minecraft.component.ComponentType<?> type) {
        return item.getComponents().contains(type);
    }

    /** Armor you wear (not horse or wolf armor: search "cavalo" or "lobo" for those). */
    private static boolean isPlayerArmor(Item item) {
        return item instanceof ArmorItem && !(item instanceof AnimalArmorItem);
    }

    private static EquipmentSlot armorSlot(Item item) {
        return isPlayerArmor(item) ? ((ArmorItem) item).getSlotType() : null;
    }

    private static String id(Item item) {
        return Registries.ITEM.getId(item).getPath();
    }

    private static boolean isOre(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        var state = block.getDefaultState();
        return id(item).endsWith("_ore") || state.isOf(net.minecraft.block.Blocks.ANCIENT_DEBRIS)
                || state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.IRON_ORES) || state.isIn(BlockTags.GOLD_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.EMERALD_ORES);
    }
}
