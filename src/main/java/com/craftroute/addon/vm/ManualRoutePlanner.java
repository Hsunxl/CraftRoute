package com.craftroute.addon.vm;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.crafting.inv.CraftingSimulationState;
import com.craftroute.addon.api.CraftRouteCrafting;
import com.craftroute.addon.compiler.PatternCompiler;
import com.craftroute.addon.route.*;
import java.util.*;
import java.util.function.Function;

/** Isolated planning path: no shared warm plan, fuzzy fallback or native fallback. */
public final class ManualRoutePlanner {
    public static Map<AEKey, List<IPatternDetails>> capture(AEKey root, RoutePolicy<AEKey> policy,
            Function<AEKey, ? extends Collection<IPatternDetails>> source, Function<IPatternDetails, String> identity) {
        var result = new HashMap<AEKey, List<IPatternDetails>>();
        var queue = new ArrayDeque<AEKey>(); queue.add(root);
        int edges = 0;
        while (!queue.isEmpty()) {
            AEKey key = queue.removeFirst();
            if (result.containsKey(key)) continue;
            if (result.size() >= 256) throw new RoutePlanningException("手动路线超过 256 个材料，请拆分任务");
            var candidates = source.apply(key);
            if (candidates.size() > 128) throw new RoutePlanningException("单个材料样板过多，请先整理网络");
            var allowed = policy.filter(key, candidates, identity).stream()
                    .filter(p -> p.getPrimaryOutput() != null && key.equals(p.getPrimaryOutput().what())).toList();
            if (policy.rule(key).locked() != null && allowed.isEmpty())
                throw new RoutePlanningException("锁定样板已移除或更改：" + key.getDisplayName().getString());
            result.put(key, allowed);
            for (var p : allowed) for (var input : p.getInputs()) for (var stack : input.getPossibleInputs()) {
                if (++edges > 8192) throw new RoutePlanningException("手动路线输入组合过多，请拆分任务");
                if (stack != null && !result.containsKey(stack.what())) queue.addLast(stack.what());
            }
        }
        return Map.copyOf(result);
    }
    public static ICraftingPlan plan(AEKey root, long amount, Map<AEKey, List<IPatternDetails>> candidates,
            Function<AEKey, Long> stock, CraftingSimulationState state) {
        if (amount <= 0) throw new RoutePlanningException("合成数量必须大于零");
        Function<AEKey, IPatternDetails> choose = k -> CraftRouteCrafting.pickBestPattern(candidates.getOrDefault(k, List.of()), k);
        var top = choose.apply(root);
        if (top == null) throw new RoutePlanningException("目标没有允许使用的已编码样板；请编码样板或恢复自动路线");
        var request = PatternCompiler.compileRequest(top, amount);
        var plan = TwoNodeGrowthPlanner.tryPlan(request, choose, stock, state, false);
        if (plan == null || plan.simulation()) {
            var alternative = AlternativePatternSearch.search(request, choose, k -> candidates.getOrDefault(k, List.of()), stock);
            if (alternative.plan() != null) plan = alternative.plan();
        }
        if (plan == null) throw new RoutePlanningException("指定路线仍有不支持的循环、催化剂或多输出样板；未退回自动路线");
        if (!plan.simulation() && !NetProduction.sufficient(plan))
            throw new RoutePlanningException("指定路线没有足够净产量；未退回自动路线");
        // A second independent fence catches accidental planner escape from the frozen allowed set.
        for (var p : plan.patternTimes().keySet()) {
            if (candidates.values().stream().noneMatch(list -> list.stream().anyMatch(allowed -> allowed == p)))
                throw new RoutePlanningException("计算结果包含未允许的样板，已取消");
        }
        return plan;
    }
}
