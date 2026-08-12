package dev.iyanel.bedlamcore.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

final class WaitingStructure {
    private final WaitingTemplateService template;
    private final Location center;
    private final List<BlockState> replaced = new ArrayList<BlockState>();

    WaitingStructure(WaitingTemplateService template, Location center) {
        this.template = template;
        this.center = center == null ? null : center.getBlock().getLocation();
    }

    void build() {
        if (center == null || !replaced.isEmpty()) return;
        if (template.place(center, replaced)) return;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) place(x, -1, z, Material.GLASS);
        for (int y = 0; y <= 3; y++) {
            place(-3, y, -3, Material.GLASS); place(-3, y, 3, Material.GLASS);
            place(3, y, -3, Material.GLASS); place(3, y, 3, Material.GLASS);
        }
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) == 3 || Math.abs(z) == 3) place(x, 4, z, Material.GLASS);
        }
    }

    void remove() {
        for (BlockState state : replaced) state.update(true, false);
        replaced.clear();
    }

    private void place(int x, int y, int z, Material material) {
        Block block = center.clone().add(x, y, z).getBlock();
        replaced.add(block.getState());
        block.setType(material);
    }
}
