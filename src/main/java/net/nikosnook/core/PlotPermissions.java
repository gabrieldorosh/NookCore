package net.nikosnook.core;

import java.sql.SQLException;
import java.util.UUID;

/** Role restrictions supplement WorldGuard: being a WG member alone is not sufficient. */
public final class PlotPermissions {
    public enum Action { BUILD, STOCK, ALTER_CONTAINER }
    private final NookStore store;
    public PlotPermissions(NookStore store){this.store=store;}
    public boolean allowed(String location,UUID player,Action action)throws SQLException {
        if(location.equals(ShopTradePolicy.TOWN))return true;
        if(location.equals(ShopTradePolicy.ROAD))return false;
        var plot=store.plot(location);
        if(plot.owner()==null || plot.state().equals("AVAILABLE"))return false;
        String role=store.role(location,player);
        boolean build=role.equals("OWNER") || role.equals("BUILD") || role.equals("BUILD_STOCK");
        boolean stock=role.equals("OWNER") || role.equals("STOCK") || role.equals("BUILD_STOCK");
        // Grace/reclamation keeps collection possible; sale permission is checked separately.
        return switch(action){case BUILD->build;case STOCK->stock;case ALTER_CONTAINER->build && stock;};
    }
}
