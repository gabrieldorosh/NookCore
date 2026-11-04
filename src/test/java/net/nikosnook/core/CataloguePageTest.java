package net.nikosnook.core;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CataloguePageTest {
    @Test void emptyCatalogueStillHasOneHarmlessPage(){var p=CataloguePage.of(List.of(),99);assertEquals(0,p.index());assertEquals(1,p.pages());assertTrue(p.entries().isEmpty());assertFalse(p.previous());assertFalse(p.next());}
    @Test void oldFiftyFourDesignsAllRemainReachableInOrder(){var all=IntStream.range(0,54).boxed().toList();var seen=new ArrayList<Integer>();for(int i=0;i<2;i++){var p=CataloguePage.of(all,i);seen.addAll(p.entries());assertEquals(i>0,p.previous());assertEquals(i==0,p.next());}assertEquals(all,seen);}
    @Test void exactBoundaryDoesNotInventAnEmptyNextPage(){var p=CataloguePage.of(IntStream.range(0,90).boxed().toList(),1);assertEquals(45,p.entries().size());assertEquals(2,p.pages());assertFalse(p.next());}
    @Test void largeCataloguesExposeEveryEntryExactlyOnce(){var all=IntStream.range(0,1001).boxed().toList();var seen=new ArrayList<Integer>();for(int i=0;i<CataloguePage.of(all,0).pages();i++)seen.addAll(CataloguePage.of(all,i).entries());assertEquals(all,seen);assertEquals(all.size(),new HashSet<>(seen).size());}
    @Test void staleOrExtremeRequestsClampToExistingPages(){var all=IntStream.range(0,46).boxed().toList();assertEquals(0,CataloguePage.of(all,Integer.MIN_VALUE).index());assertEquals(List.of(45),CataloguePage.of(all,Integer.MAX_VALUE).entries());}
    @Test void previewPageIsAnImmutableSnapshot(){var all=new ArrayList<>(List.of("one","two"));var p=CataloguePage.of(all,0);all.set(0,"changed");assertEquals(List.of("one","two"),p.entries());assertThrows(UnsupportedOperationException.class,()->p.entries().add("three"));}
}
