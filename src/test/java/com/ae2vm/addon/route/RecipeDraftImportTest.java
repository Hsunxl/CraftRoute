package com.ae2vm.addon.route;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ae2vm.addon.route.RecipeDraftImport.*;

class RecipeDraftImportTest {
    RouteView.Stack item(String key,long amount){return new RouteView.Stack(key,key,amount);}
    Slot slot(Role role,RouteView.Stack... stacks){return new Slot(role,List.of(stacks),null);}
    Result read(Slot... slots){return RecipeDraftImport.read(List.of(slots),o->(RouteView.Stack)o);}
    @Test void keepsItemAndFluidQuantitiesAndMultipleOutputs(){
        var r=read(slot(Role.INPUT,item("dust",32)),slot(Role.INPUT,item("water",1000)),slot(Role.OUTPUT,item("crystal",64),item("other_variant",64)),slot(Role.OUTPUT,item("byproduct",1)));
        assertEquals(32,r.inputs().get(0).amount());assertEquals(1000,r.inputs().get(1).amount());assertEquals(2,r.outputs().size());assertEquals(64,r.outputs().get(0).amount());
    }
    @Test void takesDisplayedTagAlternativeNotSumOfVariants(){
        var a=item("copper_a",2);var b=item("copper_b",2);
        var r=read(new Slot(Role.INPUT,List.of(a,b),b),slot(Role.OUTPUT,item("wire",4)));
        assertEquals(List.of(b),r.inputs());
    }
    @Test void invisibleDisplayFallsBackToFirstIngredient(){var a=item("iron",1);assertEquals(List.of(a),read(slot(Role.INPUT,a),slot(Role.OUTPUT,item("out",1))).inputs());}
    @Test void duplicateInputSlotsArePreserved(){var a=item("iron",1);assertEquals(2,read(slot(Role.INPUT,a),slot(Role.INPUT,a),slot(Role.OUTPUT,item("out",1))).inputs().size());}
    @Test void emptySlotsAndCatalystDisplayAreNotConsumed(){assertTrue(read(new Slot(Role.INPUT,List.of(),null),slot(Role.OTHER,item("machine",1)),slot(Role.OUTPUT,item("out",1))).inputs().isEmpty());}
    @Test void missingOutputAndZeroCountAreRejected(){assertThrows(IllegalArgumentException.class,()->read(slot(Role.INPUT,item("iron",1))));assertThrows(IllegalArgumentException.class,()->read(slot(Role.OUTPUT,item("out",0))));}
    @Test void unknownMaterialFailsWholeImportRatherThanDroppingAnInput(){assertThrows(IllegalArgumentException.class,()->RecipeDraftImport.read(List.of(new Slot(Role.INPUT,List.of("gas"),null),slot(Role.OUTPUT,item("out",1))),o->{if(o instanceof RouteView.Stack s)return s;throw new IllegalArgumentException("unsupported");}));}
    @Test void boundsSlots(){var slots=new ArrayList<Slot>();for(int i=0;i<129;i++)slots.add(slot(Role.INPUT,item("iron",1)));slots.add(slot(Role.OUTPUT,item("out",1)));assertThrows(IllegalArgumentException.class,()->RecipeDraftImport.read(slots,o->(RouteView.Stack)o));}
    @Test void draftsRoundTripWithoutBecomingPatterns(){var d=new RouteView.Draft("test:recipe","machine",List.of(item("water",1000)),List.of(item("out",2)),"display only");assertEquals(d,RouteNetwork.GSON.fromJson(RouteNetwork.GSON.toJson(d),RouteView.Draft.class));}
}
