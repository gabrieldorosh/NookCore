package net.nikosnook.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipBannerIdentityTest {
    final List<String> layers=List.of("YELLOW:border","ORANGE:flower");
    boolean matches(String product,String design,String saved,String current,List<?> patterns){return PipBannerIdentity.matches(product,design,saved,current,layers,patterns);}
    @Test void standingAndWallOrientationsKeepTheSameSixteenBaseColours(){
        for(String colour:List.of("WHITE","ORANGE","MAGENTA","LIGHT_BLUE","YELLOW","LIME","PINK","GRAY","LIGHT_GRAY","CYAN","PURPLE","BLUE","BROWN","GREEN","RED","BLACK")){
            assertTrue(matches("BANNER","sunny",colour+"_BANNER",colour+"_BANNER",layers));
            assertTrue(matches("BANNER","sunny",colour+"_BANNER",colour+"_WALL_BANNER",layers));
        }
    }
    @Test void ordinaryItemsWrongTypesAndMissingIdentityNeverAcquirePipMetadata(){
        assertFalse(matches(null,"sunny","WHITE_BANNER","WHITE_BANNER",layers));
        assertFalse(matches("HEAD","sunny","WHITE_BANNER","WHITE_BANNER",layers));
        assertFalse(matches("BANNER"," ","WHITE_BANNER","WHITE_BANNER",layers));
        for(String material:List.of("DIAMOND","PLAYER_HEAD","WHITE_WALL_BANNER","UNKNOWN_BANNER"))assertFalse(matches("BANNER","sunny",material,material,layers));
    }
    @Test void changedBaseLayersColoursOrderAndMissingLayersCannotRestoreAnotherVariant(){
        assertFalse(matches("BANNER","sunny","WHITE_BANNER","RED_BANNER",layers));
        assertFalse(matches("BANNER","sunny","WHITE_BANNER","WHITE_BANNER",List.of("YELLOW:border")));
        assertFalse(matches("BANNER","sunny","WHITE_BANNER","WHITE_BANNER",List.of("RED:border","ORANGE:flower")));
        assertFalse(matches("BANNER","sunny","WHITE_BANNER","WHITE_BANNER",List.of("ORANGE:flower","YELLOW:border")));
        assertFalse(matches("BANNER","sunny","WHITE_BANNER","WHITE_BANNER",null));
    }
    @Test void blankBannersStillRequireMatchingColourAndTypedIdentity(){
        assertTrue(PipBannerIdentity.matches("BANNER","plain","BLACK_BANNER","BLACK_WALL_BANNER",List.of(),List.of()));
        assertFalse(PipBannerIdentity.matches(null,"plain","BLACK_BANNER","BLACK_WALL_BANNER",List.of(),List.of()));
    }
}
