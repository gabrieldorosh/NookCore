package net.nikosnook.core;

import java.util.List;

/** Forty-five designs leave one inventory row for preview navigation. */
record CataloguePage<T>(List<T> entries,int index,int pages) {
    static final int SIZE=45;
    CataloguePage {entries=List.copyOf(entries);}
    static <T> CataloguePage<T> of(List<T> all,int requested){
        int pages=all.isEmpty()?1:1+(all.size()-1)/SIZE;
        int index=Math.clamp(requested,0,pages-1),start=index*SIZE;
        return new CataloguePage<>(all.subList(start,start+Math.min(SIZE,all.size()-start)),index,pages);
    }
    boolean previous(){return index>0;}
    boolean next(){return index+1<pages;}
}
