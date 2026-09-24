package net.nikosnook.core;

/** Customer-facing offer terms. A draft has no stock, funds or trade side effects. */
record ShopDraft(int quantity,long nooks,String paymentItem,int paymentQuantity){
    ShopDraft {
        if(quantity<1 || quantity>64)throw new IllegalArgumentException("Choose a sale bundle between 1 and 64 items.");
        if(nooks<1 || nooks>Money.MAX)throw new IllegalArgumentException("Choose a positive Nooks price.");
        if(paymentQuantity<1 || paymentQuantity>64)throw new IllegalArgumentException("Choose between 1 and 64 payment items.");
        if(paymentItem!=null && !paymentItem.matches("[A-Z][A-Z0-9_]*"))throw new IllegalArgumentException("Choose a valid payment item.");
    }
    static ShopDraft initial(){return new ShopDraft(1,100,null,1);}
    ShopDraft quantity(int value){return new ShopDraft(value,nooks,paymentItem,paymentQuantity);}
    ShopDraft nooks(long value){return new ShopDraft(quantity,value,null,paymentQuantity);}
    ShopDraft item(String material,int amount){return new ShopDraft(quantity,nooks,material,amount);}
    ShopDraft adjustPrice(boolean increase,boolean bulk){
        int sign=increase?1:-1;
        return paymentItem==null?nooks(Math.clamp(nooks+sign*(bulk?100:25),1,Money.MAX))
            :item(paymentItem,Math.clamp(paymentQuantity+sign*(bulk?8:1),1,64));
    }
    String price(){return paymentItem==null?Money.format(nooks):paymentQuantity+" × "+paymentItem.toLowerCase(java.util.Locale.ROOT).replace('_',' ');}
}
