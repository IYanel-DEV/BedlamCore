package dev.iyanel.bedlamcore.compat;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class Skins {
    private Skins() { }

    @SuppressWarnings("deprecation")
    public static ItemStack head(String source) {
        ItemStack head = Items.stack("PLAYER_HEAD", "SKULL_ITEM", 1, (short) 3);
        if (source == null || source.isEmpty() || !(head.getItemMeta() instanceof SkullMeta)) return head;
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (!source.startsWith("https://textures.minecraft.net/texture/")) {
            if (source.matches("[A-Za-z0-9_]{1,16}")) meta.setOwner(source);
            head.setItemMeta(meta);
            return head;
        }
        if (!applyModern(meta, source)) applyLegacy(meta, source);
        head.setItemMeta(meta);
        return head;
    }

    private static boolean applyModern(ItemMeta meta, String url) {
        try {
            Object profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class).invoke(null, UUID.randomUUID());
            Object textures = profile.getClass().getMethod("getTextures").invoke(profile);
            textures.getClass().getMethod("setSkin", URL.class).invoke(textures, new URL(url));
            for (Method method : profile.getClass().getMethods()) {
                if (method.getName().equals("setTextures") && method.getParameterTypes().length == 1) method.invoke(profile, textures);
            }
            for (Method method : meta.getClass().getMethods()) {
                if (method.getName().equals("setOwnerProfile") && method.getParameterTypes().length == 1) {
                    method.invoke(meta, profile);
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static void applyLegacy(ItemMeta meta, String url) {
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), null);
            Method propsMethod;
            try {
                propsMethod = profileClass.getMethod("getProperties");
            } catch (NoSuchMethodException e) {
                propsMethod = profileClass.getMethod("properties"); // record accessor, authlib 9.x
            }
            Object properties = propsMethod.invoke(profile);
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
            String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", value);
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);
            Field field = findField(meta.getClass(), "profile");
            field.setAccessible(true);
            field.set(meta, profile);
        } catch (Exception ignored) { }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
