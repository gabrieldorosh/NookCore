package net.nikosnook.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Private answer pool and a broader permitted-guess catalogue; no dated solution schedule. */
final class WordleWords {
    private static final int MAX_BYTES=1_000_000,MAX_ENTRIES=50_000;
    private final List<String> answers;
    private final Set<String> guesses;

    WordleWords(Collection<String> answers,Collection<String> guesses){
        this.answers=List.copyOf(catalogue(answers,"answers"));
        this.guesses=Set.copyOf(catalogue(guesses,"guesses"));
        if(this.answers.isEmpty())throw new IllegalArgumentException("The answer list is empty.");
        for(String answer:this.answers)if(!this.guesses.contains(answer))
            throw new IllegalArgumentException("Answer '"+answer+"' is missing from the permitted guesses.");
    }
    static WordleWords bundled(ClassLoader loader)throws IOException {
        Objects.requireNonNull(loader,"loader");
        return load(loader.getResourceAsStream("wordle/answers.txt"),loader.getResourceAsStream("wordle/guesses.txt"));
    }
    /** Reads and closes both UTF-8 inputs. Blank lines and whole-line # comments are allowed. */
    static WordleWords load(InputStream answers,InputStream guesses)throws IOException {
        try(InputStream a=answers;InputStream g=guesses){
            if(a==null)throw new IOException("Missing wordle/answers.txt.");
            if(g==null)throw new IOException("Missing wordle/guesses.txt.");
            try{return new WordleWords(read(a,"wordle/answers.txt"),read(g,"wordle/guesses.txt"));}
            catch(IllegalArgumentException e){throw new IOException("Invalid word catalogue: "+e.getMessage(),e);}
        }
    }
    static String normalize(String input){
        if(input==null)throw new IllegalArgumentException("try a five-letter word!");
        String word=input.strip();
        if(word.length()!=5)throw new IllegalArgumentException("try a five-letter word!");
        word=word.toLowerCase(Locale.ROOT);
        if(!word.matches("[a-z]{5}"))throw new IllegalArgumentException("use five letters, a to z!");
        return word;
    }
    boolean accepts(String word){
        try{return guesses.contains(normalize(word));}
        catch(IllegalArgumentException e){return false;}
    }
    List<String> answers(){return answers;}
    int answerCount(){return answers.size();}
    int guessCount(){return guesses.size();}
    private static LinkedHashSet<String> catalogue(Collection<String> input,String label){
        if(input==null)throw new IllegalArgumentException("Missing "+label+" list.");
        if(input.size()>MAX_ENTRIES)throw new IllegalArgumentException("The "+label+" list is too large.");
        var result=new LinkedHashSet<String>();int entry=0;
        for(String raw:input){
            entry++;String word;
            try{word=normalize(raw);}catch(IllegalArgumentException e){throw new IllegalArgumentException(label+" entry "+entry+": "+e.getMessage());}
            if(!result.add(word))throw new IllegalArgumentException("Duplicate "+label+" word '"+word+"'.");
        }
        return result;
    }
    private static List<String> read(InputStream input,String path)throws IOException {
        byte[] bytes=input.readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES)throw new IOException(path+" is larger than one megabyte.");
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw new IOException(path+" must use UTF-8 text.",e);}
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        var words=new ArrayList<String>();int line=0;
        for(String raw:text.split("\\R",-1)){
            line++;String word=raw.strip();if(word.isEmpty()||word.startsWith("#"))continue;
            try{words.add(normalize(word));}catch(IllegalArgumentException e){throw new IOException(path+" line "+line+": "+e.getMessage(),e);}
            if(words.size()>MAX_ENTRIES)throw new IOException(path+" has too many entries.");
        }
        return words;
    }
}
