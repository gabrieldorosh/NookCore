package net.nikosnook.core;
import java.util.UUID;

/** Internal settlement records. No command or listener can trade through this unfinished backend. */
final class NativeShop {
    static final int MAX_STOCK=1_000_000;
    record Offer(UUID id,String plot,UUID lease,UUID owner,byte[] item,int bundle,long cents,int stock,long revision,boolean closed){
        Offer { item=item.clone(); }
        @Override public byte[] item(){return item.clone();}
    }
    record Receipt(UUID id,UUID offer,UUID buyer,UUID seller,int quantity,long cents,long revision,byte[] item){
        Receipt { item=item.clone(); }
        @Override public byte[] item(){return item.clone();}
    }
    static void terms(byte[] item,int bundle,long cents){
        if(item==null || item.length==0 || item.length>1_048_576)throw new IllegalArgumentException("Invalid sale item snapshot.");
        if(bundle<1 || bundle>64 || cents<1 || cents>Money.MAX)throw new IllegalArgumentException("Invalid shop terms.");
    }
}
