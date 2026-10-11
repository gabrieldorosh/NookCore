package net.nikosnook.core;
import java.util.*;
import java.io.*;

/** Explicitly identifies unrecorded builds instead of guessing their source. */
record BuildIdentity(String commit,String state,String inputs,String built) {
    static BuildIdentity read(InputStream stream) {
        Properties p=new Properties();
        if(stream!=null)try(stream){p.load(stream);}catch(IOException | IllegalArgumentException ignored){}
        return new BuildIdentity(value(p,"commit"),value(p,"state"),value(p,"inputs"),value(p,"built"));
    }
    private static String value(Properties p,String key){String s=p.getProperty(key,"unrecorded");return s.isBlank()||s.contains("${")?"unrecorded":s;}
    List<String> lines(){return List.of("Source: "+commit+" ("+state+")","Build time UTC: "+built,"Runtime-source SHA256: "+inputs);}
}
