package com.craftroute.addon.route;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteLayoutTest {
    @Test void jeiOpeningBeforeScreenInitUsesCurrentLogicalWindow() {
        assertEquals(RouteLayout.of(700,310),RouteLayout.forScreen(0,0,700,310));
    }
    @Test void partiallyInitializedScreenUsesOneConsistentWindowSnapshot() {
        assertEquals(RouteLayout.of(700,310),RouteLayout.forScreen(700,0,700,310));
        assertEquals(RouteLayout.of(700,310),RouteLayout.forScreen(0,310,700,310));
    }
    @Test void initializedAndResizedScreenKeepsItsOwnDimensions() {
        assertEquals(RouteLayout.of(854,480),RouteLayout.forScreen(854,480,700,310));
    }
    @Test void screenshotAtGuiScaleFourIsNotRejected() {
        var l=RouteLayout.of(700,310);
        assertTrue(l.usable());assertTrue(l.compact());
        assertTrue(l.recipeBodyHeight()>=70);
    }
    @Test void standardMinecraftLogicalWindowsStayUsable() {
        for(int[] size:new int[][]{{320,240},{427,240},{480,270},{512,288},{640,360},{700,306},{854,480},{1280,720}}) {
            var l=RouteLayout.of(size[0],size[1]);
            assertTrue(l.usable(),size[0]+"x"+size[1]);
            assertTrue(l.canvasRight()-l.canvasLeft()>=96);
            assertTrue(l.panelWidth()-14>=120);
            assertTrue(l.canvasBottom()>l.canvasTop()+100);
            assertTrue(l.recipeBodyHeight()>=40,"JEI body at "+size[0]+"x"+size[1]);
            assertTrue(l.panelRight()<l.contentRight());
        }
    }
    @Test void cachePagesReplaceVerticalOverflow() {
        var small=RouteLayout.of(480,270);
        assertTrue(small.collapseCache());assertEquals(20,small.cacheReservedHeight());
        var medium=RouteLayout.of(700,310);
        assertFalse(medium.collapseCache());assertEquals(1,medium.cacheRows());
        assertEquals(58,medium.cacheReservedHeight());
    }
    @Test void allStandardGuiScalesOfUsersWindowFit() {
        for(int scale=1;scale<=5;scale++) {
            var l=RouteLayout.of((2799+scale-1)/scale,(1230+scale-1)/scale);
            assertTrue(l.usable());assertTrue(l.recipeBodyHeight()>=40);
        }
    }
    @Test void genuineTinyWindowsHaveAnExplicitFallback() {
        assertFalse(RouteLayout.of(280,180).usable());
        assertThrows(IllegalArgumentException.class,()->RouteLayout.of(0,300));
    }
}
