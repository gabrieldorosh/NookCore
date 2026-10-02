import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

final class ReleasePresentationChecks {
    private static int checks;
    private static void check(boolean ok,String message,JavaPlugin log){if(!ok)throw new AssertionError(message);checks++;log.getLogger().info("PASS "+message);}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args)throws Exception{var m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    static void run(JavaPlugin log)throws Exception{
        JavaPlugin core=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NookCore");Object chat=field(core,"chat"),store=field(core,"store");
        var cfg=core.getConfig().getConfigurationSection("chat.display-titles");
        for(String key:cfg.getKeys(false)){
            UUID id=UUID.fromString(key);String title=cfg.getString(key);
            String name=switch(title){case "mr-duck"->"qahar0";case "mrs-bee"->"MoonCheese213";default->"paigebun";};
            Player player=(Player)Proxy.newProxyInstance(log.getClass().getClassLoader(),new Class[]{Player.class},(o,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "getName"->name;case "hasPermission"->false;case "getStatistic"->0;default->null;
            });
            invoke(store,"join",new Class[]{UUID.class,String.class,long.class,long.class},id,name,System.currentTimeMillis(),12345L);
            invoke(chat,"refresh",new Class[]{Player.class},player);
            String rank=(String)invoke(chat,"placeholder",new Class[]{UUID.class,String.class},id,"rank");
            String label=switch(title){case "mr-duck"->"mr. duck";case "mrs-bee"->"miss bee";default->"mrs. niko";};
            String colour=switch(title){case "mr-duck"->"#E8A0A0";case "mrs-bee"->"#F1DEA0";default->"#EAB8D2";};
            check(rank.toLowerCase(Locale.ROOT).contains(colour.toLowerCase(Locale.ROOT)) && rank.contains("["+label+"]"),"account-specific title and colour "+name,log);
            check(!player.hasPermission("nookcore.admin"),"title does not require or grant admin "+name,log);
            Object account=((Optional<?>)invoke(store,"account",new Class[]{UUID.class},id)).orElseThrow();
            long cents=(long)invoke(account,"cents",new Class[]{});
            for(int i=0;i<100;i++)invoke(chat,"placeholder",new Class[]{UUID.class,String.class},id,"balance");
            Object after=((Optional<?>)invoke(store,"account",new Class[]{UUID.class},id)).orElseThrow();
            check(cents==(long)invoke(after,"cents",new Class[]{}),"TAB reads do not change balance "+name,log);
            invoke(store,"adjust",new Class[]{UUID.class,long.class,String.class,String.class,long.class},id,100000000000L-cents,"probe","maximum balance display",System.currentTimeMillis());
            invoke(chat,"refresh",new Class[]{Player.class},player);
            check("1000000000.00".equals(invoke(chat,"placeholder",new Class[]{UUID.class,String.class},id,"balance")),"full-precision numeric balance at supported TAB maximum "+name,log);
            check("1000000000".equals(invoke(chat,"placeholder",new Class[]{UUID.class,String.class},id,"balance_whole")),"legacy objective stays within integer range "+name,log);
            invoke(store,"adjust",new Class[]{UUID.class,long.class,String.class,String.class,long.class},id,12345L-100000000000L,"probe","restore display test balance",System.currentTimeMillis());
            invoke(chat,"refresh",new Class[]{Player.class},player);
            check("123.45".equals(invoke(chat,"placeholder",new Class[]{UUID.class,String.class},id,"balance")),"balance refresh reflects debit and cents "+name,log);
        }
        check("0".equals(invoke(chat,"placeholder",new Class[]{UUID.class,String.class},UUID.randomUUID(),"balance")),"unknown account yields numeric placeholder",log);
        var tab=Bukkit.getPluginManager().getPlugin("TAB");check(tab!=null && tab.isEnabled(),"TAB enabled with candidate configuration",log);
        var tc=YamlConfiguration.loadConfiguration(new java.io.File(tab.getDataFolder(),"config.yml"));
        check(tc.getStringList("scoreboard-teams.sorting-types").equals(List.of("PLACEHOLDER_HIGH_TO_LOW:%nookcore_balance%","PLACEHOLDER_A_TO_Z:%player%")),"TAB numeric descending balance then name",log);
        check(tc.getStringList("header-footer.designs.default.footer").stream().anyMatch(s->s.contains("%tps% TPS")),"TAB footer uses native TPS",log);
        log.getLogger().info("RELEASE_PRESENTATION_PASS checks="+checks);
        ReleaseQuestChecks.run(log);
    }
}
