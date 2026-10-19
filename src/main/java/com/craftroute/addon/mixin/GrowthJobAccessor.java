package com.craftroute.addon.mixin;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=ExecutingCraftingJob.class,remap=false)
public interface GrowthJobAccessor {
 @Accessor("tasks") Map<IPatternDetails,?> craftroute$getTasks();
 @Mutable @Accessor("tasks") void craftroute$setTasks(Map<IPatternDetails,?> tasks);
 @Accessor("waitingFor") ListCraftingInventory craftroute$getWaitingFor();
 @Accessor("timeTracker") ElapsedTimeTracker craftroute$getTimeTracker();
 @Accessor("finalOutput") GenericStack craftroute$getFinalOutput();
}
