package net.nikosnook.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static net.nikosnook.core.PlotPermissions.Action.*;

class PlotPermissionsTest {
    @TempDir Path dir;NookStore store;PlotPermissions permissions;
    UUID owner=UUID.randomUUID(),member=UUID.randomUUID(),outsider=UUID.randomUUID();long now=1_800_000_000_000L;
    @BeforeEach void setup()throws Exception {
        store=new NookStore(dir.resolve("nooks.db"));store.join(owner,"Owner",now,6000);store.join(member,"Member",now,6000);
        store.definePlot("one",3000);store.rent("one",owner,now);permissions=new PlotPermissions(store);
    }
    @AfterEach void close()throws Exception {store.close();}
    void add(String role)throws Exception {store.acceptInvitation(store.invite("one",owner,member,role,now).token(),member,now);}
    @Test void buildOnlyCannotOpenOrBreakStockContainers()throws Exception {
        add("BUILD");assertTrue(permissions.allowed("one",member,BUILD));assertFalse(permissions.allowed("one",member,STOCK));assertFalse(permissions.allowed("one",member,ALTER_CONTAINER));
    }
    @Test void stockOnlyCannotAlterBuildingsOrContainers()throws Exception {
        add("STOCK");assertTrue(permissions.allowed("one",member,STOCK));assertFalse(permissions.allowed("one",member,BUILD));assertFalse(permissions.allowed("one",member,ALTER_CONTAINER));
    }
    @Test void combinedRoleAndOwnerHaveBothCapabilities()throws Exception {
        add("BUILD_STOCK");for(var action:PlotPermissions.Action.values()){assertTrue(permissions.allowed("one",owner,action));assertTrue(permissions.allowed("one",member,action));}
    }
    @Test void removalAndRoleDowngradeImmediatelyRevokeAccess()throws Exception {
        add("BUILD_STOCK");store.changeRole("one",owner,member,"BUILD",now);assertFalse(permissions.allowed("one",member,STOCK));
        store.removeMember("one",owner,member,now);assertFalse(permissions.allowed("one",member,BUILD));assertFalse(store.members("one").containsKey(member));
    }
    @Test void graceAndReclamationPermitCollectionButNotSales()throws Exception {
        add("STOCK");long late=now+NookStore.WEEK+1;store.tick(late);
        assertTrue(permissions.allowed("one",member,STOCK));assertFalse(store.canTrade("one",late));
        store.tick(now+2*NookStore.WEEK);assertTrue(permissions.allowed("one",member,STOCK));
        store.confirmCleared("one","staff","archive/one",now+2*NookStore.WEEK);assertFalse(permissions.allowed("one",member,STOCK));
    }
    @Test void outsidersAndRoadsDeniedButWildernessUnchanged()throws Exception {
        for(var action:PlotPermissions.Action.values()){
            assertFalse(permissions.allowed("one",outsider,action));assertFalse(permissions.allowed(ShopTradePolicy.ROAD,owner,action));assertTrue(permissions.allowed(ShopTradePolicy.TOWN,outsider,action));
        }
    }
}
