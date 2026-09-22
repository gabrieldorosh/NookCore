package net.nikosnook.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.OfflinePlayer;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class VaultBridgeTest{
 @TempDir Path dir;NookStore s;VaultBridge v;UUID id=UUID.randomUUID();
 @BeforeEach void setup()throws Exception{s=new NookStore(dir.resolve("test.db"));s.join(id,".BedrockPlayer",1,6000);v=new VaultBridge(s,()->true,e->{throw new AssertionError(e);});}
 @AfterEach void close()throws Exception{s.close();}
 @Test void rejectsInvalidFractionalAndNonfiniteAmounts(){for(double d:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,.001}){assertFalse(v.depositPlayer(".BedrockPlayer",d).transactionSuccess());assertFalse(v.withdrawPlayer(".BedrockPlayer",d).transactionSuccess());}assertEquals(60,v.getBalance(".BedrockPlayer"));}
 @Test void offlineUuidWorksWithoutName(){OfflinePlayer p=(OfflinePlayer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{OfflinePlayer.class},(o,m,a)->m.getName().equals("getUniqueId")?id:null);assertTrue(v.withdrawPlayer(p,1.23).transactionSuccess());assertEquals(58.77,v.getBalance(p));}
 @Test void noBonusFromAccountCreation(){assertFalse(v.createPlayerAccount("NeverJoined"));assertTrue(v.createPlayerAccount(".BedrockPlayer"));assertEquals(60,v.getBalance(".BedrockPlayer"));}
 @Test void depositAndWithdrawalPersist(){assertTrue(v.withdrawPlayer(".BedrockPlayer",12.50).transactionSuccess());assertTrue(v.depositPlayer(".BedrockPlayer",1).transactionSuccess());assertEquals(48.50,v.getBalance(".BedrockPlayer"));}
 @Test void noBanksOrNegativeBalances(){assertFalse(v.hasBankSupport());assertFalse(v.bankDeposit("shop",1).transactionSuccess());assertFalse(v.withdrawPlayer(".BedrockPlayer",61).transactionSuccess());assertEquals(60,v.getBalance(".BedrockPlayer"));}
 @Test void closedEconomyDeniesMutations(){VaultBridge paused=new VaultBridge(s,()->false,e->{});assertFalse(paused.depositPlayer(".BedrockPlayer",1).transactionSuccess());assertEquals(60,v.getBalance(".BedrockPlayer"));}
}
