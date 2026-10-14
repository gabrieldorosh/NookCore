package net.nikosnook.core;

import java.util.*;

/** Bounded constraint search; invalid pools fail closed instead of publishing an invalid visit. */
final class VendorCatalogue {
    enum Rarity { COMMON, UNCOMMON, RARE }
    record Design(String id,String name,String texture,Rarity rarity,String category,Set<String> seasons,
                  boolean yearRound,boolean modern,long price,int allocation,int cap,String source) {
        Design {
            seasons=Set.copyOf(seasons);
            if(!id.matches("[a-z0-9_-]{1,48}") || name.isBlank() || !texture.matches("[a-f0-9]{32,64}")
                || category.isBlank() || price<1 || price>Money.MAX || allocation<1 || allocation>1000 || cap<0 || cap>2000)
                throw new IllegalArgumentException("Invalid Pip design: "+id);
        }
        boolean seasonal(String season){return seasons.contains(season);}
        boolean eligible(String season){return yearRound || seasonal(season);}
    }
    static List<Design> select(List<Design> pool,String season,Set<String> pinned,Set<String> previous,long seed){
        if(pool.stream().map(Design::id).distinct().count()!=pool.size())throw new IllegalArgumentException("Duplicate Pip design IDs.");
        var choices=new ArrayList<>(pool.stream().filter(d->d.eligible(season)).toList());
        var random=new Random(seed);Collections.shuffle(choices,random);
        // Stable sort retains the seeded random order inside each priority group.
        choices.sort(Comparator.comparingInt(d->pinned.contains(d.id())?0:previous.contains(d.id())?2:1));
        if(!choices.stream().map(Design::id).toList().containsAll(pinned) || pinned.size()>7)
            throw new IllegalArgumentException("Pinned designs are missing or ineligible.");
        var result=new ArrayList<Design>();
        if(!search(choices,0,result,season,pinned,new int[]{0}))throw new IllegalArgumentException("Pip needs seven valid designs: 4 common, 2 uncommon, 1 rare, at least 2 seasonal and 2 year-round, at most 1 modern and 2 per category.");
        return List.copyOf(result);
    }
    private static boolean search(List<Design> all,int from,List<Design> out,String season,Set<String> pinned,int[] visited){
        if(++visited[0]>250000)return false;
        if(out.size()==7)return valid(out,season) && out.stream().map(Design::id).toList().containsAll(pinned);
        if(all.size()-from<7-out.size())return false;
        for(int i=from;i<all.size();i++){
            var d=all.get(i);
            long same=out.stream().filter(x->x.rarity()==d.rarity()).count();
            int max=switch(d.rarity()){case COMMON->4;case UNCOMMON->2;case RARE->1;};
            if(same>=max || d.modern() && out.stream().anyMatch(Design::modern)
                || out.stream().filter(x->x.category().equals(d.category())).count()>=2)continue;
            out.add(d);if(search(all,i+1,out,season,pinned,visited))return true;out.removeLast();
        }
        return false;
    }
    static boolean valid(List<Design> d,String season){
        return d.size()==7 && d.stream().map(Design::id).distinct().count()==7
            && d.stream().allMatch(x->x.eligible(season)) && d.stream().filter(x->x.seasonal(season)).count()>=2
            && d.stream().filter(Design::yearRound).count()>=2 && d.stream().filter(Design::modern).count()<=1
            && d.stream().filter(x->x.rarity()==Rarity.COMMON).count()==4
            && d.stream().filter(x->x.rarity()==Rarity.UNCOMMON).count()==2
            && d.stream().filter(x->x.rarity()==Rarity.RARE).count()==1
            && d.stream().allMatch(x->d.stream().filter(y->y.category().equals(x.category())).count()<=2);
    }
}
