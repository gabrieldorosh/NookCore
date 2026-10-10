package net.nikosnook.core;

import java.util.*;

/** Stock journal evidence and an optional explicit staff decision; never replays inventories. */
record NativeStockReview(UUID receipt,UUID offer,UUID actor,String actorName,int quantity,long time,
        String state,String plot,byte[] item,byte[] beforeHash,byte[] afterHash,
        Integer creditedQuantity,int currentStock,Resolution resolution) {
    enum Recovery { CREDIT, NO_CREDIT }
    record Resolution(Recovery recovery,String staff,String reason,long time) {}
    NativeStockReview {item=item.clone();beforeHash=beforeHash.clone();afterHash=afterHash.clone();}
    @Override public byte[] item(){return item.clone();}
    @Override public byte[] beforeHash(){return beforeHash.clone();}
    @Override public byte[] afterHash(){return afterHash.clone();}
    boolean sameIntent(NativeStockReview other){
        return other!=null && receipt.equals(other.receipt) && offer.equals(other.offer) && actor.equals(other.actor)
            && quantity==other.quantity && time==other.time && plot.equals(other.plot)
            && Arrays.equals(item,other.item) && Arrays.equals(beforeHash,other.beforeHash) && Arrays.equals(afterHash,other.afterHash);
    }
    boolean sameSnapshot(NativeStockReview other){
        return sameIntent(other) && actorName.equals(other.actorName) && state.equals(other.state)
            && Objects.equals(creditedQuantity,other.creditedQuantity) && currentStock==other.currentStock
            && Objects.equals(resolution,other.resolution);
    }
    List<String> lines(String itemDescription){
        var lines=new ArrayList<>(List.of(
            "Stock receipt: "+receipt+" · "+state,
            "Player: "+actorName+" · "+actor,
            "Shop: "+offer+" · plot "+plot,
            "Attempted deposit: "+quantity+" × "+itemDescription+" · "+NookUi.date(time),
            "Credit recorded for this receipt: "+(creditedQuantity==null?"none":creditedQuantity+" items")+" · current shop stock: "+currentStock,
            "Before inventory hash: "+HexFormat.of().formatHex(beforeHash),
            "Expected after inventory hash: "+HexFormat.of().formatHex(afterHash)));
        if(resolution!=null){
            lines.add("Staff resolution: "+resolution.recovery()+" · "+resolution.staff()+" · "+NookUi.date(resolution.time()));
            lines.add("Resolution reason: "+resolution.reason());
        }
        lines.add("Read-only: no items or stock changed. This record does not prove whether the player's items were removed.");
        return List.copyOf(lines);
    }
}
