package net.nikosnook.core;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Pure storage-slot planning. Keys must encode full item metadata, not just a material name. */
final class NativeInventoryPlan {
    record Stack(String key,int count,int limit){
        Stack { if(key==null || key.isBlank() || count<1 || limit<1 || count>limit)throw new IllegalArgumentException("Invalid inventory stack."); }
    }
    record Plan(List<Stack> before,List<Stack> after){
        Plan { before=freeze(before);after=freeze(after); }
        byte[] beforeHash(){return hash(before);}
        byte[] afterHash(){return hash(after);}
    }
    enum Observation { NOT_APPLIED, APPLIED, AMBIGUOUS }
    private static List<Stack> freeze(List<Stack> slots){return Collections.unmodifiableList(new ArrayList<>(slots));}
    private static void quantity(int count){if(count<1 || count>64)throw new IllegalArgumentException("Move between 1 and 64 items per operation.");}
    static Plan deposit(List<Stack> inventory,String key,int quantity){
        quantity(quantity);if(key==null || key.isBlank())throw new IllegalArgumentException("An exact item key is required.");
        List<Stack> result=new ArrayList<>(inventory);int left=quantity;
        for(int i=0;i<result.size() && left>0;i++){
            Stack stack=result.get(i);if(stack==null || !stack.key().equals(key))continue;
            int take=Math.min(left,stack.count());left-=take;
            result.set(i,take==stack.count()?null:new Stack(key,stack.count()-take,stack.limit()));
        }
        if(left!=0)throw new IllegalArgumentException("Not enough exactly matching items in storage slots.");
        return new Plan(inventory,result);
    }
    static Plan deliver(List<Stack> inventory,Stack item,int quantity){
        quantity(quantity);Objects.requireNonNull(item);
        List<Stack> result=new ArrayList<>(inventory);int left=quantity;
        for(int i=0;i<result.size() && left>0;i++){
            Stack stack=result.get(i);if(stack==null || !stack.key().equals(item.key()))continue;
            if(stack.limit()!=item.limit())throw new IllegalArgumentException("Matching item keys have different stack limits.");
            int add=Math.min(left,stack.limit()-stack.count());left-=add;result.set(i,new Stack(stack.key(),stack.count()+add,stack.limit()));
        }
        for(int i=0;i<result.size() && left>0;i++)if(result.get(i)==null){int add=Math.min(left,item.limit());left-=add;result.set(i,new Stack(item.key(),add,item.limit()));}
        if(left!=0)throw new IllegalArgumentException("Not enough inventory space for the complete bundle.");
        return new Plan(inventory,result);
    }
    static byte[] hash(List<Stack> slots){
        try{
            var buffer=new ByteArrayOutputStream();var out=new DataOutputStream(buffer);out.writeInt(1);out.writeInt(slots.size());
            for(var stack:slots){out.writeBoolean(stack!=null);if(stack!=null){byte[] key=stack.key().getBytes(StandardCharsets.UTF_8);out.writeInt(key.length);out.write(key);out.writeInt(stack.count());out.writeInt(stack.limit());}}
            out.flush();return MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray());
        }catch(IOException | NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    // Observations support staff recovery; never automatically overwrite a differing inventory.
    static Observation observe(Plan plan,List<Stack> actual){byte[] hash=hash(actual);return Arrays.equals(hash,plan.afterHash())?Observation.APPLIED:Arrays.equals(hash,plan.beforeHash())?Observation.NOT_APPLIED:Observation.AMBIGUOUS;}
}
