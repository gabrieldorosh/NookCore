package net.nikosnook.core;

import java.util.List;
import java.util.Set;

/** Exact vanilla variant policy; design tags are copyable, not edition authentication. */
final class PipBannerIdentity {
    private static final Set<String> BASES=Set.of("WHITE","ORANGE","MAGENTA","LIGHT_BLUE","YELLOW","LIME","PINK","GRAY","LIGHT_GRAY","CYAN","PURPLE","BLUE","BROWN","GREEN","RED","BLACK");
    static boolean matches(String product,String design,String savedType,String currentType,List<?> savedPatterns,List<?> currentPatterns){
        if(!"BANNER".equals(product)||design==null||design.isBlank()||savedType==null||!savedType.endsWith("_BANNER"))return false;
        String base=savedType.substring(0,savedType.length()-7);
        return BASES.contains(base)&&(savedType.equals(currentType)||(base+"_WALL_BANNER").equals(currentType))
            && savedPatterns!=null&&savedPatterns.equals(currentPatterns);
    }
}
