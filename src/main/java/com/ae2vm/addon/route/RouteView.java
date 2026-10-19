package com.ae2vm.addon.route;

import appeng.api.stacks.AEKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import java.util.List;

/** Data-only DTOs shared by the UI, JEI drafts and network. Drafts are previews until explicitly encoded by the server. */
public final class RouteView {
    public record Stack(String key, String name, long amount) {
        public static Stack of(AEKey key, long amount, HolderLookup.Provider registries) {
            return new Stack(key.toTagGeneric(registries).toString(), key.getDisplayName().getString(), amount);
        }
        public AEKey decode(HolderLookup.Provider registries) {
            try { return AEKey.fromTagGeneric(registries, TagParser.parseTag(key)); }
            catch (Exception e) { throw new IllegalArgumentException("材料数据无法读取", e); }
        }
    }
    public record Choice(String id, List<Stack> inputs, List<Stack> outputs, boolean alternatives) {}
    public record Reply(String message, List<Choice> choices, String locked, List<String> banned,
                        boolean stockOnly, int ruleCount, boolean truncated) {}
    public record Draft(String recipeId, String category, List<Stack> inputs, List<Stack> outputs, String note) {}
}
