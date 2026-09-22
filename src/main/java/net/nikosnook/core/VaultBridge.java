package net.nikosnook.core;

import net.milkbowl.vault.economy.*;
import org.bukkit.OfflinePlayer;
import java.math.*;
import java.sql.SQLException;
import java.util.*;
import java.util.function.*;

/** Legacy Vault API supplied by VaultUnlocked. No shadow Essentials balance and no bank accounts. */
public final class VaultBridge implements Economy {
    private final NookStore store;private final BooleanSupplier enabled;private final Consumer<SQLException> failure;
    public VaultBridge(NookStore store,BooleanSupplier enabled,Consumer<SQLException> failure){this.store=store;this.enabled=enabled;this.failure=failure;}
    public boolean isEnabled(){return enabled.getAsBoolean();} public String getName(){return "NookCore";}
    public boolean hasBankSupport(){return false;}public int fractionalDigits(){return 2;}
    public String currencyNamePlural(){return "Nooks";}public String currencyNameSingular(){return "Nook";}
    private static long cents(double value){
        if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid amount.");
        try{long result=BigDecimal.valueOf(value).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();if(result>Money.MAX)throw new IllegalArgumentException("Amount too large.");return result;}
        catch(ArithmeticException e){throw new IllegalArgumentException("Only two decimal places are supported.");}
    }
    public String format(double value){try{return Money.format(cents(value));}catch(IllegalArgumentException e){return "Invalid amount";}}
    private UUID id(String name){try{return store.byName(name).map(NookStore.Account::id).orElse(null);}catch(SQLException e){failure.accept(e);return null;}catch(IllegalArgumentException e){return null;}}
    private boolean exists(UUID id){if(id==null||!isEnabled())return false;try{return store.account(id).isPresent();}catch(SQLException e){failure.accept(e);return false;}}
    private double balance(UUID id){if(id==null||!isEnabled())return 0;try{return store.account(id).map(a->a.cents()/100.0).orElse(0.0);}catch(SQLException e){failure.accept(e);return 0;}}
    private boolean enough(UUID id,double value){try{return exists(id)&&BigDecimal.valueOf(balance(id)).compareTo(BigDecimal.valueOf(cents(value),2))>=0;}catch(IllegalArgumentException e){return false;}}
    private EconomyResponse change(UUID id,double value,boolean deposit){
        if(!exists(id))return new EconomyResponse(0,0,EconomyResponse.ResponseType.FAILURE,"NookCore unavailable or account has not joined.");
        try{long amount=cents(value);store.integrationAdjustment(id,deposit?amount:-amount,"vault:"+(deposit?"deposit":"withdraw"),System.currentTimeMillis());return new EconomyResponse(value,balance(id),EconomyResponse.ResponseType.SUCCESS,null);}
        catch(IllegalArgumentException e){return new EconomyResponse(0,balance(id),EconomyResponse.ResponseType.FAILURE,e.getMessage());}
        catch(SQLException e){failure.accept(e);return new EconomyResponse(0,0,EconomyResponse.ResponseType.FAILURE,"Storage error; consult audit before retrying.");}
    }
    public boolean hasAccount(String p){return exists(id(p));}public boolean hasAccount(OfflinePlayer p){return exists(p.getUniqueId());}
    public boolean hasAccount(String p,String w){return hasAccount(p);}public boolean hasAccount(OfflinePlayer p,String w){return hasAccount(p);}
    public double getBalance(String p){return balance(id(p));}public double getBalance(OfflinePlayer p){return balance(p.getUniqueId());}
    public double getBalance(String p,String w){return getBalance(p);}public double getBalance(OfflinePlayer p,String w){return getBalance(p);}
    public boolean has(String p,double v){return enough(id(p),v);}public boolean has(OfflinePlayer p,double v){return enough(p.getUniqueId(),v);}
    public boolean has(String p,String w,double v){return has(p,v);}public boolean has(OfflinePlayer p,String w,double v){return has(p,v);}
    public EconomyResponse withdrawPlayer(String p,double v){return change(id(p),v,false);}public EconomyResponse withdrawPlayer(OfflinePlayer p,double v){return change(p.getUniqueId(),v,false);}
    public EconomyResponse withdrawPlayer(String p,String w,double v){return withdrawPlayer(p,v);}public EconomyResponse withdrawPlayer(OfflinePlayer p,String w,double v){return withdrawPlayer(p,v);}
    public EconomyResponse depositPlayer(String p,double v){return change(id(p),v,true);}public EconomyResponse depositPlayer(OfflinePlayer p,double v){return change(p.getUniqueId(),v,true);}
    public EconomyResponse depositPlayer(String p,String w,double v){return depositPlayer(p,v);}public EconomyResponse depositPlayer(OfflinePlayer p,String w,double v){return depositPlayer(p,v);}
    public boolean createPlayerAccount(String p){return hasAccount(p);}public boolean createPlayerAccount(OfflinePlayer p){return hasAccount(p);}
    public boolean createPlayerAccount(String p,String w){return hasAccount(p);}public boolean createPlayerAccount(OfflinePlayer p,String w){return hasAccount(p);}
    private EconomyResponse unsupported(){return new EconomyResponse(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Shared bank accounts are not enabled.");}
    public List<String> getBanks(){return List.of();}public EconomyResponse createBank(String n,String p){return unsupported();}public EconomyResponse createBank(String n,OfflinePlayer p){return unsupported();}
    public EconomyResponse deleteBank(String n){return unsupported();}public EconomyResponse bankBalance(String n){return unsupported();}public EconomyResponse bankHas(String n,double v){return unsupported();}
    public EconomyResponse bankWithdraw(String n,double v){return unsupported();}public EconomyResponse bankDeposit(String n,double v){return unsupported();}
    public EconomyResponse isBankOwner(String n,String p){return unsupported();}public EconomyResponse isBankOwner(String n,OfflinePlayer p){return unsupported();}
    public EconomyResponse isBankMember(String n,String p){return unsupported();}public EconomyResponse isBankMember(String n,OfflinePlayer p){return unsupported();}
}
