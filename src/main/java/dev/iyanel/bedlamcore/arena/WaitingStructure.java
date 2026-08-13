package dev.iyanel.bedlamcore.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

final class WaitingStructure {
    private final WaitingTemplateService template;
    private final Location center;
    private final List<WaitingTemplateService.BlockSnap> replaced = new ArrayList<WaitingTemplateService.BlockSnap>();

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
        if (replaced.isEmpty()) {
            template.clear(center);
            return;
        }
        for (WaitingTemplateService.BlockSnap snap : replaced) snap.restore();
        replaced.clear();
    }

    private void place(int x, int y, int z, Material material) {
        Block block = center.clone().add(x, y, z).getBlock();
        replaced.add(WaitingTemplateService.BlockSnap.original(block, material, (byte) 0));
        block.setType(material);
    }
}
