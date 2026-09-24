package net.nikosnook.core;
import java.util.*;
import java.util.function.Predicate;
final class PlotAddresses {
    record Address(String id,String label){}
    static Address choose(Predicate<String> used,Random random){
        String[] first={"Mossbank","Lantern","Granite","Birch","Copper","Willow","Clover","Cobble","Meadow","Lapis","Honey","Fern"};
        String[] last={"Lane","Walk","Way","Row","Terrace","Market","Quay","Street","Place","Mews","Corner","Gardens"};
        List<Address> options=new ArrayList<>();
        for(String a:first)for(String b:last)options.add(new Address((a+"-"+b).toLowerCase(Locale.ROOT),a+" "+b));
        Collections.shuffle(options,random);
        for(int number=1;number<=10_000;number++)for(var option:options){
            String id=option.id()+(number==1?"":"-"+number);
            if(!used.test(id))return new Address(id,option.label()+(number==1?"":" "+number));
        }
        throw new IllegalArgumentException("No unused address is available.");
    }
}
