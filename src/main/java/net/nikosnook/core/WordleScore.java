package net.nikosnook.core;

import java.util.*;

/** Exact matches are consumed before misplaced matches, including repeated letters. */
final class WordleScore {
    enum Mark { ABSENT, PRESENT, EXACT }
    static List<Mark> score(String answer,String guess){
        check(answer);check(guess);var marks=new ArrayList<Mark>(Collections.nCopies(5,Mark.ABSENT));
        int[] remaining=new int[26];
        for(int i=0;i<5;i++)if(answer.charAt(i)==guess.charAt(i))marks.set(i,Mark.EXACT);else remaining[answer.charAt(i)-'a']++;
        for(int i=0;i<5;i++)if(marks.get(i)!=Mark.EXACT && remaining[guess.charAt(i)-'a']>0){marks.set(i,Mark.PRESENT);remaining[guess.charAt(i)-'a']--;}
        return List.copyOf(marks);
    }
    static String encode(List<Mark> marks){if(marks.size()!=5)throw new IllegalArgumentException("Invalid word feedback.");var out=new StringBuilder();marks.forEach(mark->out.append((char)('0'+mark.ordinal())));return out.toString();}
    static List<Mark> decode(String text){if(text==null || !text.matches("[012]{5}"))throw new IllegalArgumentException("Invalid saved word feedback.");return text.chars().mapToObj(c->Mark.values()[c-'0']).toList();}
    static String normalize(String input){
        String value=input==null?"":input.trim().toLowerCase(Locale.ROOT);
        check(value);return value;
    }
    private static void check(String value){if(value==null || !value.matches("[a-z]{5}"))throw new IllegalArgumentException("pick a five-letter word, using A to Z!");}
    static boolean solved(List<Mark> marks){return marks.size()==5 && marks.stream().allMatch(mark->mark==Mark.EXACT);}
}
