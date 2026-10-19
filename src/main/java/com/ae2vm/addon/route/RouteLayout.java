package com.ae2vm.addon.route;

/** GUI-scaled logical coordinates, not framebuffer pixels. No changes to Minecraft's global GUI scale. */
public record RouteLayout(int width,int height,int contentRight,int panelLeft,int panelRight,int panelWidth,
                          int canvasLeft,int canvasRight,int canvasTop,int canvasBottom,int toolbarY,
                          boolean compact,boolean collapseCache) {
    /** JEI can query a newly opening Screen before Screen.init assigns its dimensions. */
    public static RouteLayout forScreen(int width,int height,int windowWidth,int windowHeight) {
        return width>0 && height>0 ? of(width,height) : of(windowWidth,windowHeight);
    }
    public static RouteLayout of(int width,int height) {
        if(width<=0 || height<=0)throw new IllegalArgumentException("窗口尺寸无效");
        boolean compact=height<380;
        int sidebar=Math.max(64,Math.min(112,width*16/100));
        int right=width-sidebar;
        int preferred=Math.max(right>=380?190:142,Math.min(250,right*32/100));
        int panel=Math.max(1,Math.min(preferred,right-120));
        int panelRight=right-8,panelLeft=panelRight-panel;
        return new RouteLayout(width,height,right,panelLeft,panelRight,panel,8,panelLeft-8,
                compact?50:78,height-(compact?22:26),compact?26:52,compact,height<300);
    }
    public boolean usable() {return width>=320 && height>=200;}
    public int cacheColumns() {return Math.max(1,(panelWidth-14)/18);}
    public int cacheRows() {return compact?1:(18+cacheColumns()-1)/cacheColumns();}
    public int cacheReservedHeight() {return collapseCache?20:18+cacheRows()*18+22;}
    public int inspectorBottom() {return canvasBottom-cacheReservedHeight()-5;}
    public int recipeTop() {return canvasTop+(compact?30:44);}
    public int recipeHeight() {return inspectorBottom()-recipeTop()-22;}
    public int recipeBodyHeight() {return Math.max(0,recipeHeight()-44);}
}
