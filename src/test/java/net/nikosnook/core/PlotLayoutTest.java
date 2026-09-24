package net.nikosnook.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlotLayoutTest {
    @Test void inclusiveAreaWorksForRectanglesAndNegativeCoordinates(){assertEquals(77,new PlotLayout(-10,-20,-4,-10).area());assertEquals(1,new PlotLayout(0,0,0,0).area());}
    @Test void sharedBoundaryIsOverlapButAdjacentBlocksAreNot(){var a=new PlotLayout(0,0,8,8);assertTrue(a.overlaps(new PlotLayout(8,8,16,16)));assertFalse(a.overlaps(new PlotLayout(9,0,17,8)));}
    @Test void containmentRejectsOneBlockOutsideAnyEdge(){var a=new PlotLayout(-10,-10,10,10);assertTrue(a.contains(a));for(var b:new PlotLayout[]{new PlotLayout(-11,0,0,0),new PlotLayout(0,-11,0,0),new PlotLayout(0,0,11,0),new PlotLayout(0,0,0,11)})assertFalse(a.contains(b));}
    @Test void invalidIdsCannotBecomeYamlPathsOrSpecialRegions(){for(String id:new String[]{"../config","plot.one","UPPER","__global__","","a b"})assertThrows(IllegalArgumentException.class,()->PlotLayout.id(id));assertEquals("shop_1",PlotLayout.id("shop_1"));}
    @Test void invertedCoordinatesAreRejected(){assertThrows(IllegalArgumentException.class,()->new PlotLayout(1,0,0,1));}
}
