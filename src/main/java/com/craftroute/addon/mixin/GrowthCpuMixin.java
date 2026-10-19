package com.craftroute.addon.mixin;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.craftroute.addon.vm.TwoNodeGrowthPlanner;
import com.craftroute.addon.vm.FeedbackReservation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Keep feedback inputs inside the CPU instead of delivering them as final output.
 * Uses the REAL remaining task counts, waiting-for gate and CPU inventory; pending
 * operations already dispatched have consumed their inputs and need no reservation.
 * Only activated on jobs whose actual pattern set contains the supported growth pair.
 */
@Mixin(value=CraftingCpuLogic.class,remap=false)
public abstract class GrowthCpuMixin {
 @Shadow private ExecutingCraftingJob job;
 @Shadow @Final private ListCraftingInventory inventory;
 @Shadow @Final private CraftingCPUCluster cluster;
 @Unique private boolean craftroute$growthFeedback;
 @Unique private long craftroute$restoreTargetSeed;
 @Inject(method="trySubmitJob",at=@At("RETURN"))
 private void craftroute$mark(IGrid grid,ICraftingPlan plan,IActionSource src,ICraftingRequester requester,
        CallbackInfoReturnable<ICraftingSubmitResult> cir) {
     if(job!=null && cir.getReturnValue().successful())
     {
         craftroute$growthFeedback=TwoNodeGrowthPlanner.containsRing(plan.patternTimes().keySet());
         craftroute$restoreTargetSeed=craftroute$growthFeedback && com.craftroute.addon.vm.NetProduction.sufficient(plan)
                 ?plan.usedItems().get(plan.finalOutput().what()):0;
         craftroute$orderFeedback();
     }
 }
 @Unique private void craftroute$orderFeedback() {
     if(craftroute$growthFeedback && job!=null) {
         GrowthJobAccessor a=(GrowthJobAccessor)(Object)job;
         a.craftroute$setTasks(TwoNodeGrowthPlanner.prioritizeFeedback(a.craftroute$getTasks()));
     }
 }
 @WrapMethod(method="insert")
 private long craftroute$reserveFeedback(AEKey what,long amount,Actionable mode,Operation<Long> original) {
     if(!craftroute$growthFeedback || job==null || what==null || amount<=0)
         return original.call(what,amount,mode);
     GrowthJobAccessor access=(GrowthJobAccessor)(Object)job;
     if(!what.matches(access.craftroute$getFinalOutput()))return original.call(what,amount,mode);
     long pending=0;
     try {
         for(var task:access.craftroute$getTasks().entrySet()) {
             long times=((GrowthTaskAccessor)task.getValue()).craftroute$getValue();
             if(times<=0)continue;
             for(var in:task.getKey().getInputs()) {
                 var choices=in.getPossibleInputs();
                 if(choices==null || choices.length==0 || choices[0]==null || !what.equals(choices[0].what()))continue;
                 if(what.equals(in.getRemainingKey(what)))continue;
                 pending=Math.addExact(pending,Math.multiplyExact(times,
                         Math.multiplyExact(in.getMultiplier(),choices[0].amount())));
             }
         }
     }catch(ArithmeticException ex){pending=Long.MAX_VALUE;}
     var waiting=access.craftroute$getWaitingFor();
     long reserve=FeedbackReservation.reserve(amount,waiting.extract(what,amount,Actionable.SIMULATE),
             pending,inventory.extract(what,Long.MAX_VALUE,Actionable.SIMULATE));
     // Seed replacement is not order delivery. Keep it as CPU surplus, which
     // native storeItems returns to ME after completion. This guarantees a NET
     // increase even for one-item output packets (e.g. requesting dust itself).
     long expected=waiting.extract(what,amount,Actionable.SIMULATE);
     long restore=Math.min(craftroute$restoreTargetSeed,Math.min(amount-reserve,Math.max(0,expected-reserve)));
     long retained=reserve+restore;
     if(retained==0)return original.call(what,amount,mode);
     if(mode==Actionable.MODULATE) {
         waiting.extract(what,retained,Actionable.MODULATE);
         inventory.insert(what,retained,Actionable.MODULATE);
         craftroute$restoreTargetSeed-=restore;
         ((GrowthTimeAccessor)(Object)access.craftroute$getTimeTracker()).craftroute$decrementItems(retained,what.getType());
         cluster.markDirty();
     }
     return retained+(amount>retained?original.call(what,amount-retained,mode):0L);
 }
 @Inject(method="writeToNBT",at=@At("TAIL"))
 private void craftroute$save(CompoundTag tag,HolderLookup.Provider registries,CallbackInfo ci) {
     tag.putBoolean("craftrouteGrowthFeedback",craftroute$growthFeedback && job!=null);
     tag.putLong("craftrouteRestoreTargetSeed",craftroute$restoreTargetSeed);
 }
 @Inject(method="readFromNBT",at=@At("TAIL"))
 private void craftroute$load(CompoundTag tag,HolderLookup.Provider registries,CallbackInfo ci) {
     craftroute$growthFeedback=job!=null && tag.getBoolean("craftrouteGrowthFeedback");
     craftroute$restoreTargetSeed=craftroute$growthFeedback?Math.max(0,tag.getLong("craftrouteRestoreTargetSeed")):0;
     craftroute$orderFeedback();
 }
}
