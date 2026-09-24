package net.nikosnook.core;

/** Inclusive horizontal footprints shared by setup validation and price previews. */
record PlotLayout(int minX,int minZ,int maxX,int maxZ) {
    PlotLayout { if(minX>maxX || minZ>maxZ)throw new IllegalArgumentException("Invalid selection."); }
    long area(){return Math.multiplyExact((long)maxX-minX+1,(long)maxZ-minZ+1);}
    boolean contains(PlotLayout b){return b.minX>=minX && b.maxX<=maxX && b.minZ>=minZ && b.maxZ<=maxZ;}
    boolean overlaps(PlotLayout b){return minX<=b.maxX && maxX>=b.minX && minZ<=b.maxZ && maxZ>=b.minZ;}
    static String id(String text){if(!text.matches("[a-z0-9_-]{1,32}") || text.startsWith("__"))throw new IllegalArgumentException("Use 1–32 lowercase letters, numbers, underscores or hyphens for the ID.");return text;}
}
