package net.nikosnook.core;

import java.sql.SQLException;
import java.util.*;

/** Checks every sign/container location, not just the sign, against the current lease. */
public final class ShopTradePolicy {
    public static final String TOWN = "", ROAD = "@district-road";
    private final NookStore store;
    public ShopTradePolicy(NookStore store) { this.store=store; }
    public void checkCustomer(List<String> locations,UUID beneficiary,UUID customer,long now)throws SQLException {
        check(locations,beneficiary,now);
        String plot=locations.getFirst();
        if(!plot.equals(TOWN) && Set.of("OWNER","STOCK","BUILD_STOCK").contains(store.role(plot,customer)))
            throw new IllegalArgumentException("You manage this shop's stock, so you cannot buy from or sell to it. Use the stock chest instead.");
    }
    public void check(List<String> locations, UUID beneficiary, long now) throws SQLException {
        if(locations.size()<2 || locations.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Cannot verify the shop's sign and stock container.");
        if(locations.stream().allMatch(TOWN::equals))return;
        String plot=locations.getFirst();
        if(plot.equals(TOWN) || plot.equals(ROAD) || locations.stream().anyMatch(p->!p.equals(plot)))
            throw new IllegalArgumentException("The sign and every stock container must be inside the same rented plot.");
        NookStore.Plot lease=store.plot(plot);
        if(!store.canTrade(plot,now))throw new IllegalArgumentException("This shop is closed until its rent is paid.");
        if(!Objects.equals(lease.owner(),beneficiary))
            throw new IllegalArgumentException("Shop payments must belong to the original renter.");
    }
}
