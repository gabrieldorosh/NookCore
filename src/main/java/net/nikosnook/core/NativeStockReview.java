package net.nikosnook.core;

import java.util.*;

/** Read-only stock journal detail. Hashes are evidence, never an instruction to replay items. */
record NativeStockReview(UUID receipt,UUID offer,UUID actor,String actorName,int quantity,long time,
        String state,String plot,byte[] item,byte[] beforeHash,byte[] afterHash,
        Integer creditedQuantity,int currentStock) {
    NativeStockReview {item=item.clone();beforeHash=beforeHash.clone();afterHash=afterHash.clone();}
    @Override public byte[] item(){return item.clone();}
    @Override public byte[] beforeHash(){return beforeHash.clone();}
    @Override public byte[] afterHash(){return afterHash.clone();}
    List<String> lines(String itemDescription){
        return List.of(
            "Stock receipt: "+receipt+" · "+state,
            "Player: "+actorName+" · "+actor,
            "Shop: "+offer+" · plot "+plot,
            "Attempted deposit: "+quantity+" × "+itemDescription+" · "+NookUi.date(time),
            "Credit recorded for this receipt: "+(creditedQuantity==null?"none":creditedQuantity+" items")+" · current shop stock: "+currentStock,
            "Before inventory hash: "+HexFormat.of().formatHex(beforeHash),
            "Expected after inventory hash: "+HexFormat.of().formatHex(afterHash),
            "Read-only: no items or stock changed. This record does not prove whether the player's items were removed.");
    }
}
