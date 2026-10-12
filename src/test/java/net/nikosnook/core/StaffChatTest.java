package net.nikosnook.core;

import org.junit.jupiter.api.Test;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StaffChatTest {
    static class User {
        boolean allowed;
        final List<Component> messages = new ArrayList<>();
        final CommandSender sender;
        User(String name, boolean allowed, boolean player) {
            this.allowed = allowed;
            sender = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{player ? Player.class : CommandSender.class}, (o,m,a) -> switch(m.getName()) {
                    case "hasPermission" -> this.allowed && a[0].equals(StaffChat.PERMISSION);
                    case "getName" -> name;
                    case "sendMessage" -> { messages.add((Component)a[0]); yield null; }
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        }
        Player player() { return (Player)sender; }
        String text() { return messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("",String::concat); }
    }
    @Test void sendsOnlyToCurrentlyPermittedStaffAndEchoesOnce() {
        var helper=new User("Helper",true,true);var admin=new User("Admin",true,true);var regular=new User("Regular",false,true);
        var chat=new StaffChat(()->List.of(helper.player(),admin.player(),regular.player()));
        chat.onCommand(helper.sender,null,"sc",new String[]{"Check","spawn"});
        assertEquals("Staff » Helper: Check spawn",helper.text());assertEquals(helper.text(),admin.text());
        assertEquals(1,helper.messages.size());assertTrue(regular.messages.isEmpty());
        admin.allowed=false;chat.onCommand(helper.sender,null,"staff",new String[]{"Again"});
        assertEquals(1,admin.messages.size());
    }
    @Test void ordinarySenderCannotDeliverAnything() {
        var regular=new User("Regular",false,true);var helper=new User("Helper",true,true);
        new StaffChat(()->List.of(helper.player())).onCommand(regular.sender,null,"sc",new String[]{"secret"});
        assertTrue(helper.messages.isEmpty());assertTrue(regular.text().contains("permission"));
    }
    @Test void emptyMessageShowsAliasUsageWithoutBroadcast() {
        var helper=new User("Helper",true,true);var admin=new User("Admin",true,true);
        new StaffChat(()->List.of(helper.player(),admin.player())).onCommand(helper.sender,null,"sc",new String[0]);
        assertEquals("/sc <message>",helper.text());assertTrue(admin.messages.isEmpty());
    }
    @Test void consoleCanSendAndMessageMarkupRemainsLiteral() {
        var console=new User("CONSOLE",true,false);var helper=new User("Helper",true,true);
        var chat=new StaffChat(()->List.of(helper.player()));
        chat.onCommand(console.sender,null,"staff",new String[]{"<red>hello</red>"});
        assertEquals("Staff » CONSOLE: <red>hello</red>",helper.text());assertEquals(helper.text(),console.text());
        assertTrue(chat.onTabComplete(console.sender,null,"sc",new String[]{""}).isEmpty());
    }
}
