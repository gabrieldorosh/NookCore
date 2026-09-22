package net.nikosnook.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.format.*;

/** UUID-keyed preferences; publish changes only after a successful atomic file replacement. */
final class ChatPreferences {
    record Preference(String colour, String pronouns) {
        Preference {
            colour=colour.toLowerCase(Locale.ROOT);
            if (!(colour.matches("#[0-9a-f]{6}") || NamedTextColor.NAMES.value(colour)!=null))
                throw new IllegalArgumentException("Use a colour name such as aqua, or #RRGGBB.");
            pronouns=pronouns.trim();
            if(pronouns.length()>32 || !pronouns.matches("[\\p{L} /-]*"))
                throw new IllegalArgumentException("Pronouns may contain up to 32 letters, spaces, / or -.");
        }
        TextColor textColour(){return colour.startsWith("#")?TextColor.fromHexString(colour):NamedTextColor.NAMES.value(colour);}
    }
    static final Preference DEFAULT=new Preference("white", "");
    private final Path path;
    private Properties data=new Properties();
    ChatPreferences(Path path)throws IOException {
        this.path=path;
        if(Files.exists(path))try(var input=Files.newInputStream(path)){data.load(input);}
        try {
            for(String key:data.stringPropertyNames()) {
                int split=key.lastIndexOf('.');
                if(split<0 || !Set.of("colour","pronouns").contains(key.substring(split+1)))throw new IllegalArgumentException("Unknown preference key");
                get(UUID.fromString(key.substring(0,split)));
            }
        }catch(IllegalArgumentException e){throw new IOException("Invalid chat preference file; preserved for repair.",e);}
    }
    synchronized Preference get(UUID id){return new Preference(data.getProperty(id+".colour","white"),data.getProperty(id+".pronouns",""));}
    synchronized void set(UUID id, Preference preference)throws IOException {
        Properties next=new Properties();next.putAll(data);
        next.setProperty(id+".colour",preference.colour());next.setProperty(id+".pronouns",preference.pronouns());
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary=Files.createTempFile(path.toAbsolutePath().getParent(),"chat-", ".tmp");
        try {
            try(var out=Files.newOutputStream(temporary)){next.store(out,"NookCore chat preferences, keyed by account UUID");}
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            data=next;
        }finally{Files.deleteIfExists(temporary);}
    }
}
