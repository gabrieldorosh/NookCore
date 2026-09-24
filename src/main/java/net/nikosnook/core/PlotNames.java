package net.nikosnook.core;

/** Plain display text only; never parsed as formatting or used as a region/command ID. */
final class PlotNames {
    static String clean(String input){
        String name=input.strip();
        if(name.codePointCount(0,name.length())<1 || name.codePointCount(0,name.length())>32 || !name.matches("[\\p{L}\\p{N} '’.,!()\\-]+"))
            throw new IllegalArgumentException("Use 1–32 letters, numbers, spaces or simple punctuation for your shop name. Formatting codes are not supported.");
        return name.replaceAll(" +"," ");
    }
}
