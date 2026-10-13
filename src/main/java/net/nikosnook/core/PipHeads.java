package net.nikosnook.core;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stable same-design metadata. Explosions leave collectible skulls intact. */
final class PipHeads implements Listener {
    static final NamespacedKey DESIGN=new NamespacedKey("nookcore","pip-design");
    static final NamespacedKey SAVED=new NamespacedKey("nookcore","pip-placed-item");
    static ItemStack make(VendorCatalogue.Design d){
        var item=new ItemStack(Material.PLAYER_HEAD);var meta=(SkullMeta)item.getItemMeta();
        var profile=Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(("nookcore:pip:"+d.id()).getBytes(StandardCharsets.UTF_8)));
        var textures=profile.getTextures();
        try{textures.setSkin(URI.create("https://textures.minecraft.net/texture/"+d.texture()).toURL());}catch(Exception ex){throw new IllegalArgumentException(ex);}
        profile.setTextures(textures);meta.setOwnerProfile(profile);
        var colour=switch(d.rarity()){case COMMON->TextColor.color(0xC5DAB1);case UNCOMMON->TextColor.color(0xB9CFF0);case RARE->TextColor.color(0xE5C0E9);};
        meta.displayName(Component.text(d.name(),colour).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(Component.text(switch(d.rarity()){case COMMON->"Common";case UNCOMMON->"Uncommon";case RARE->"Rare";},colour).decoration(TextDecoration.ITALIC,false),
            Component.text("Pip's collection · decoration",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
        var pdc=meta.getPersistentDataContainer();pdc.set(DESIGN,PersistentDataType.STRING,d.id());
        pdc.set(new NamespacedKey("nookcore","pip-rarity"),PersistentDataType.STRING,d.rarity().name());
        pdc.set(new NamespacedKey("nookcore","pip-seasons"),PersistentDataType.STRING,String.join(",",new TreeSet<>(d.seasons())));
        pdc.set(new NamespacedKey("nookcore","pip-schema"),PersistentDataType.INTEGER,1);
        item.setItemMeta(meta);return item;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){
        var item=e.getItemInHand();if(!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(DESIGN,PersistentDataType.STRING))return;
        if(e.getBlockPlaced().getState() instanceof Skull skull){
            var one=item.clone();one.setAmount(1);
            skull.getPersistentDataContainer().set(SAVED,PersistentDataType.BYTE_ARRAY,one.serializeAsBytes());skull.update(false,false);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drops(BlockDropItemEvent e){
        if(!(e.getBlockState() instanceof Skull skull))return;
        byte[] bytes=skull.getPersistentDataContainer().get(SAVED,PersistentDataType.BYTE_ARRAY);if(bytes==null)return;
        var head=ItemStack.deserializeBytes(bytes);boolean replaced=false;
        for(var entity:e.getItems())if(entity.getItemStack().getType()==Material.PLAYER_HEAD){
            if(!replaced){entity.setItemStack(head.clone());replaced=true;}
            // Vanilla produces one head. Never add a second entity or invent a cancelled drop.
        }
    }
    private static boolean collectible(Block b){return b.getState() instanceof Skull s && s.getPersistentDataContainer().has(SAVED,PersistentDataType.BYTE_ARRAY);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(PipHeads::collectible);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(BlockExplodeEvent e){e.blockList().removeIf(PipHeads::collectible);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(PipHeads::collectible))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(PipHeads::collectible))e.setCancelled(true);}
}
