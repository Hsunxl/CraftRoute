package com.ae2vm.addon.route.client;

import appeng.api.stacks.*;
import com.ae2vm.addon.route.RouteView;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Embed JEI's public recipe-layout API, not a copy of its internal RecipesGui screen. */
final class RouteJeiRecipes implements RouteRecipeBrowser {
    private final IJeiRuntime runtime;
    private final List<Entry<?>> recipes=new ArrayList<>();
    private AEKey key;
    private int index,x,y,w,h,bodyHeight,ticks,lastMouseX,lastMouseY;
    private float scale=1;
    private IRecipeLayoutDrawable<?> layout;
    private List<ITypedIngredient<?>> machines=List.of();
    private ITypedIngredient<?> shownMachine;
    private String error="",focusedId;
    private boolean picking,moving;
    private int pickerPage;
    private ITypedIngredient<?> held;
    private RouteView.Draft heldDraft;
    private final Map<Object,List<ITypedIngredient<?>>> catalystCache=new HashMap<>();
    private final Map<Integer,ITypedIngredient<?>> optionMachine=new HashMap<>();
    private List<ITypedIngredient<?>> catalysts(Entry<?> entry) {
        var type=entry.category().getRecipeType();
        return catalystCache.computeIfAbsent(type,t->{
            try {return runtime.getRecipeManager().createRecipeCatalystLookup(type).get().toList();}
            catch(RuntimeException ignored) {return List.of();}
        });
    }
    public void renderOption(GuiGraphics g,int index,int x,int y,int width,int mx,int my) {
        if(index<0 || index>=recipes.size())return;
        var entry=recipes.get(index);var candidates=catalysts(entry);
        boolean hover=mx>=x && mx<x+width && my>=y && my<y+18;
        var machine=optionMachine.get(index);
        if(!hover || machine==null)machine=candidates.isEmpty()?null:candidates.get(Math.floorMod(ticks/60,candidates.size()));
        if(machine!=null) {optionMachine.put(index,machine);drawTyped(g,machine,x+1,y+1);}
        else {var icon=entry.category().getIcon();if(icon!=null)icon.draw(g,x+1,y+1);}
        String text=machine==null?entry.category().getTitle().getString():name(machine);
        g.drawString(Minecraft.getInstance().font,Minecraft.getInstance().font.plainSubstrByWidth(text,Math.max(1,width-23)),x+22,y+5,0x303030,false);
        if(hover)g.renderComponentTooltip(Minecraft.getInstance().font,List.of(Component.literal(text),Component.literal("点击行选择配方；点击设备图标查询设备自身配方")),mx,my);
    }
    public void showOptionMachine(int index) {
        var machine=optionMachine.get(index);if(machine!=null)showIngredient(machine,RecipeIngredientRole.OUTPUT);
    }
    private record MachineInfo(List<ITypedIngredient<?>> candidates,mezz.jei.api.gui.drawable.IDrawable icon,String title) {}
    private final Map<String,MachineInfo> treeMachines=new HashMap<>();
    private final Map<String,ITypedIngredient<?>> treeShown=new HashMap<>();
    public void renderMachine(String id,GuiGraphics g,int x,int y,int mx,int my) {
        var info=treeMachines.get(id);if(info==null) {g.drawString(Minecraft.getInstance().font,"?",x+4,y+4,0x555555,false);return;}
        boolean hover=mx>=x && mx<x+18 && my>=y && my<y+18;
        var machine=treeShown.get(id);
        if(!hover || machine==null)machine=info.candidates.isEmpty()?null:info.candidates.get(Math.floorMod(ticks/60,info.candidates.size()));
        if(machine!=null) {treeShown.put(id,machine);drawTyped(g,machine,x+1,y+1);}
        else if(info.icon!=null)info.icon.draw(g,x+1,y+1);
        if(hover)g.renderComponentTooltip(Minecraft.getInstance().font,List.of(Component.literal(machine==null?info.title:name(machine)),Component.literal("点击查询此设备自身配方")),mx,my);
    }
    public void showMachine(String id) {var machine=treeShown.get(id);if(machine!=null)showIngredient(machine,RecipeIngredientRole.OUTPUT);}
    private <T> void remember(Entry<T> entry) {
        var id=entry.category.getRegistryName(entry.recipe);
        if(id!=null)treeMachines.put(id.toString(),new MachineInfo(catalysts(entry),entry.category.getIcon(),entry.category.getTitle().getString()));
    }
    private double pressX,pressY,dragX,dragY;
    public List<Option> options() {
        var result=new ArrayList<Option>();
        for(int i=0;i<recipes.size();i++)result.add(new Option(i,label(recipes.get(i))));
        return List.copyOf(result);
    }
    private <T> String label(Entry<T> entry) {return entry.category().getTitle().getString();}
    public void selectIndex(int selected) {if(selected>=0 && selected<recipes.size()) {index=selected;load();}}
    public RouteView.Draft currentDraft() {
        if(layout==null)return null;
        try {return RouteJeiPlugin.readDraft(layout);}catch(RuntimeException e) {error=e.getMessage();return null;}
    }
    private AEKey ingredientKey(ITypedIngredient<?> value) {
        try {return RouteJeiConverters.from(value).what();}catch(RuntimeException e) {return null;}
    }
    public boolean release(double mx,double my,int button,IngredientDrop drop) {
        if(held==null)return false;
        var value=held;var draft=heldDraft;held=null;heldDraft=null;
        if(moving || Math.hypot(mx-pressX,my-pressY)>4) {
            var ingredient=ingredientKey(value);
            if(draft!=null && ingredient!=null)drop.accept(draft,ingredient,mx,my);
        } else showIngredient(value,RecipeIngredientRole.OUTPUT);
        moving=false;return true;
    }
    public boolean draggingIngredient() {return held!=null && moving;}
    public void renderDrag(GuiGraphics g) {
        if(held==null || !moving)return;
        g.pose().pushPose();g.pose().translate(0,0,1300);
        try {drawTyped(g,held,(int)dragX-8,(int)dragY-8);}finally {g.pose().popPose();}
    }
    public void focusRecipe(String id) {
        if(Objects.equals(id,focusedId))return;
        focusedId=id;
        for(int i=0;i<recipes.size();i++)if(matchesId(recipes.get(i),id)) {index=i;load();break;}
    }
    private <T> boolean matchesId(Entry<T> entry,String id) {
        var actual=entry.category().getRegistryName(entry.recipe());return actual!=null && actual.toString().equals(id);
    }
    private record Entry<T>(IRecipeCategory<T> category,T recipe,IFocusGroup focus) {}
    RouteJeiRecipes(IJeiRuntime runtime) {this.runtime=runtime;}
    public void select(AEKey key) {
        if(Objects.equals(key,this.key))return;
        this.key=key;optionMachine.clear();focusedId=null;index=0;picking=false;held=null;recipes.clear();layout=null;error="";
        if(key==null)return;
        try {
            Object value=RouteJeiConverters.ingredient(key,1);
            if(value==null)return;
            var typed=runtime.getIngredientManager().createTypedIngredient(value,false).orElse(null);
            if(typed==null)return;
            var factory=runtime.getJeiHelpers().getFocusFactory();
            var focus=factory.createFocus(RecipeIngredientRole.OUTPUT,typed);
            var group=factory.createFocusGroup(List.of(focus));
            runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().forEach(category->{
                try {collect(category,group);}catch(RuntimeException e) {error="部分 JEI 分类读取失败";}
            });
            load();
        }catch(RuntimeException e) {error="JEI 配方读取失败："+e.getClass().getSimpleName();}
    }
    private <T> void collect(IRecipeCategory<T> category,IFocusGroup focus) {
        runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).limitFocus(focus.getAllFocuses()).get()
                .forEach(recipe->{var entry=new Entry<>(category,recipe,focus);recipes.add(entry);remember(entry);});
    }
    private <T> IRecipeLayoutDrawable<T> create(Entry<T> entry) {
        return runtime.getRecipeManager().createRecipeLayoutDrawable(entry.category(),entry.recipe(),entry.focus()).orElse(null);
    }
    private void load() {
        layout=null;shownMachine=null;machines=List.of();bodyHeight=0;error="";
        if(recipes.isEmpty())return;
        try {layout=create(recipes.get(index));}
        catch(RuntimeException e) {error="此插件的原生配方布局无法创建";}
        if(layout!=null)try {machines=runtime.getRecipeManager().createRecipeCatalystLookup(layout.getRecipeCategory().getRecipeType()).get().toList();}
        catch(RuntimeException ignored) {machines=List.of();}
    }
    public void page(int delta) {if(!recipes.isEmpty()) {index=Math.floorMod(index+delta,recipes.size());load();}}
    public void tick() {ticks++;if(layout!=null)try {layout.tick();}catch(RuntimeException e) {error="配方动画插件更新失败";}}
    private boolean bodyContains(double mx,double my) {return layout!=null && bodyHeight>0 && mx>=x && mx<x+w && my>=y+22 && my<y+22+bodyHeight;}
    private double nativeX(double mx) {return (mx-x)/scale;}
    private double nativeY(double my) {return (my-y-22)/scale;}
    public void render(GuiGraphics g,int x,int y,int w,int h,int mx,int my) {
        this.x=x;this.y=y;this.w=w;this.h=h;lastMouseX=mx;lastMouseY=my;
        var font=Minecraft.getInstance().font;
        bodyHeight=Math.max(0,h-44);
        if(layout==null)g.drawString(font,font.plainSubstrByWidth(error.isEmpty()?"无可展示的 JEI 配方":error,w),x,y+22,0x666666,false);
        else {
            String title=layout.getRecipeCategory().getTitle().getString();
            boolean machineHover=mx>=x+w-20 && mx<x+w-2 && my>=y && my<y+18;
            if(!machineHover || shownMachine==null)shownMachine=machines.isEmpty()?null:machines.get(Math.floorMod(ticks/60,machines.size()));
            String machineName=shownMachine==null?title:name(shownMachine);
            g.drawString(font,font.plainSubstrByWidth("配方 "+(index+1)+"/"+recipes.size()+" ▾ "+machineName,Math.max(1,w-26)),x,y+5,0x555555,false);
            try {drawMachine(g,mx,my,title);}catch(RuntimeException e) {error="JEI 设备图标无法绘制";}
            if(bodyHeight>=24) {
                var border=layout.getRectWithBorder();var recipeRect=layout.getRect();
                scale=Math.min(1F,Math.min((w-8F)/Math.max(1,border.getWidth()),(float)bodyHeight/Math.max(1,border.getHeight())));
                scale=Math.max(0.05F,scale);
                layout.setPosition(4+recipeRect.getX()-border.getX(),recipeRect.getY()-border.getY());
                int rx=(int)nativeX(mx),ry=(int)nativeY(my);
                g.enableScissor(x,y+22,x+w,y+22+bodyHeight);
                g.pose().pushPose();g.pose().translate(x,y+22,0);g.pose().scale(scale,scale,1);
                try {layout.drawRecipe(g,rx,ry);}
                catch(RuntimeException e) {error="此插件原生布局绘制失败，可切换其他配方";}
                finally {g.pose().popPose();g.disableScissor();}
                if(bodyContains(mx,my)) {
                    g.pose().pushPose();g.pose().translate(x,y+22,300);g.pose().scale(scale,scale,1);
                    try {layout.getInputHandler().handleMouseMoved(rx,ry);layout.drawOverlays(g,rx,ry);}
                    catch(RuntimeException e) {error="此插件原生提示绘制失败";}
                    finally {g.pose().popPose();}
                }
            }
        }
        if(layout!=null && !error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,w),x,y+h-32,0xAA3333,false);
        if(!recipes.isEmpty()) {
            button(g,x,y+h-20,28,recipes.size()>1?"‹":"—");button(g,x+32,y+h-20,28,recipes.size()>1?"›":"—");
            if(layout!=null)button(g,x+64,y+h-20,w-64,"展开全部材料");
        }
        if(picking) {
            int rows=pickerRows(),start=pickerPage*rows;
            g.pose().pushPose();g.pose().translate(0,0,350);
            g.fill(x,y+20,x+w,y+20+(rows+1)*18,0xFF555555);
            for(int row=0;row<rows && start+row<recipes.size();row++) {
                button(g,x,y+20+row*18,w,"");renderOption(g,start+row,x+1,y+20+row*18,w-2,mx,my);
            }
            button(g,x,y+20+rows*18,w,"‹ 上页    "+(pickerPage+1)+"/"+Math.max(1,(recipes.size()+rows-1)/rows)+"    下页 ›");
            g.pose().popPose();
        }
    }
    private int pickerRows() {return Math.max(1,Math.min(6,(h-40)/18));}
    private <T> void drawTyped(GuiGraphics g,ITypedIngredient<T> ingredient,int x,int y) {
        var renderer=runtime.getIngredientManager().getIngredientRenderer(ingredient.getType());
        float factor=Math.min(16F/Math.max(1,renderer.getWidth()),16F/Math.max(1,renderer.getHeight()));
        g.pose().pushPose();g.pose().translate(x,y,20);g.pose().scale(factor,factor,1);
        try {renderer.render(g,ingredient.getIngredient());}finally {g.pose().popPose();}
    }
    private <T> String name(ITypedIngredient<T> ingredient) {
        return runtime.getIngredientManager().getIngredientHelper(ingredient.getType()).getDisplayName(ingredient.getIngredient());
    }
    private void drawMachine(GuiGraphics g,int mx,int my,String title) {
        int sx=x+w-20,sy=y;
        appeng.client.gui.Icon.SLOT_BACKGROUND.getBlitter().dest(sx,sy).blit(g);
        if(shownMachine!=null)drawTyped(g,shownMachine,sx+1,sy+1);
        else {
            var icon=layout.getRecipeCategory().getIcon();
            if(icon!=null) {
                float factor=Math.min(16F/Math.max(1,icon.getWidth()),16F/Math.max(1,icon.getHeight()));
                g.pose().pushPose();g.pose().translate(sx+1,sy+1,20);g.pose().scale(factor,factor,1);
                try {icon.draw(g,0,0);}finally {g.pose().popPose();}
            }
        }
        if(mx>=sx && mx<sx+18 && my>=sy && my<sy+18) {
            var lines=List.<Component>of(Component.literal(shownMachine==null?title:name(shownMachine)),Component.literal(
                    shownMachine==null?"分类未提供可查询的设备物品":"点击查看此机器自身的合成配方"));
            g.renderComponentTooltip(Minecraft.getInstance().font,lines,mx,my);
        }
    }
    private <T> void showIngredient(ITypedIngredient<T> ingredient,RecipeIngredientRole role) {
        runtime.getRecipesGui().show(List.of(runtime.getJeiHelpers().getFocusFactory().createFocus(role,ingredient)));
    }
    private IJeiUserInput input(InputConstants.Key key) {
        return new IJeiUserInput() {
            public InputConstants.Key getKey() {return key;}
            public int getModifiers() {return (Screen.hasShiftDown()?1:0)|(Screen.hasControlDown()?2:0)|(Screen.hasAltDown()?4:0);}
            public boolean isSimulate() {return false;}
            public boolean is(KeyMapping mapping) {return mapping.isActiveAndMatches(key);}
            public boolean is(mezz.jei.api.runtime.IJeiKeyMapping mapping) {return mapping.isActiveAndMatches(key);}
        };
    }
    private void button(GuiGraphics g,int x,int y,int w,String text) {
        g.fill(x,y,x+w,y+18,0xFF373737);g.fill(x+1,y+1,x+w-1,y+17,0xFFADB0C4);
        g.fill(x+1,y+1,x+w-1,y+2,0xFFFFFFFF);
        g.drawString(Minecraft.getInstance().font,text,x+4,y+5,0x303030,false);
    }
    public boolean click(double mx,double my,int button,Consumer<RouteView.Draft> choose) {
        if(picking && mx>=x && mx<x+w && my>=y+20 && my<y+20+(pickerRows()+1)*18) {
            int row=(int)(my-y-20)/18,rows=pickerRows();
            if(row==rows)pickerPage=Math.floorMod(pickerPage+(mx<x+w/2?-1:1),Math.max(1,(recipes.size()+rows-1)/rows));
            else {int selected=pickerPage*rows+row;if(mx<x+20)showOptionMachine(selected);else {selectIndex(selected);picking=false;}}
            return true;
        }
        if(button==0 && mx>=x && mx<x+w-24 && my>=y && my<y+18) {picking=!picking;pickerPage=index/pickerRows();return true;}
        picking=false;
        if(layout!=null && button==0 && mx>=x+w-20 && mx<x+w-2 && my>=y && my<y+18) {
            if(shownMachine!=null)showIngredient(shownMachine,RecipeIngredientRole.OUTPUT);return true;
        }
        if(bodyContains(mx,my)) {
            double rx=nativeX(mx),ry=nativeY(my);
            var clickedSlot=layout.getRecipeSlotUnderMouse(rx,ry);
            if(button==0 && clickedSlot.isPresent() && clickedSlot.get().getRole()==RecipeIngredientRole.INPUT) {
                var displayed=clickedSlot.get().getDisplayedIngredient();
                if(displayed.isPresent() && ingredientKey(displayed.get())!=null) {
                    held=displayed.get();heldDraft=currentDraft();moving=false;pressX=dragX=mx;pressY=dragY=my;return true;
                }
            }
            if(layout.getInputHandler().handleInput(rx,ry,input(InputConstants.Type.MOUSE.getOrCreate(button))))return true;
            var ingredient=layout.getRecipeSlotUnderMouse(rx,ry).flatMap(slot->slot.getDisplayedIngredient());
            if(ingredient.isPresent() && (button==0 || button==1)) {
                showIngredient(ingredient.get(),button==0?RecipeIngredientRole.OUTPUT:RecipeIngredientRole.INPUT);return true;
            }
        }
        if(button!=0 || mx<x || mx>=x+w || my<y+h-20 || my>=y+h-2)return false;
        if(recipes.size()==1 && mx<x+60) {error="JEI 当前仅返回 1 张产出配方；材料替代项不等于另一张配方";return true;}
        if(mx<x+28)page(-1);
        else if(mx<x+60)page(1);
        else if(mx>=x+64 && layout!=null) {
            try {choose.accept(RouteJeiPlugin.readDraft(layout));}
            catch(RuntimeException e) {error=e.getMessage();layout=null;}
        }
        return true;
    }
    public boolean scroll(double mx,double my,double sx,double sy) {
        return bodyContains(mx,my) && layout.getInputHandler().handleMouseScrolled(nativeX(mx),nativeY(my),sx,sy);
    }
    public boolean drag(double mx,double my,int button,double dx,double dy) {
        if(held!=null) {dragX=mx;dragY=my;if(Math.hypot(mx-pressX,my-pressY)>4)moving=true;return true;}
        return bodyContains(mx,my) && layout.getInputHandler().handleMouseDragged(nativeX(mx),nativeY(my),InputConstants.Type.MOUSE.getOrCreate(button),dx/scale,dy/scale);
    }
    public boolean key(int keyCode,int scanCode) {
        if(!bodyContains(lastMouseX,lastMouseY))return false;
        double rx=nativeX(lastMouseX),ry=nativeY(lastMouseY);
        if(layout.getInputHandler().handleInput(rx,ry,input(InputConstants.getKey(keyCode,scanCode))))return true;
        if(keyCode!=org.lwjgl.glfw.GLFW.GLFW_KEY_R && keyCode!=org.lwjgl.glfw.GLFW.GLFW_KEY_U)return false;
        var ingredient=layout.getRecipeSlotUnderMouse(rx,ry).flatMap(slot->slot.getDisplayedIngredient());
        if(ingredient.isEmpty())return false;
        showIngredient(ingredient.get(),keyCode==org.lwjgl.glfw.GLFW.GLFW_KEY_R?RecipeIngredientRole.OUTPUT:RecipeIngredientRole.INPUT);return true;
    }
}
