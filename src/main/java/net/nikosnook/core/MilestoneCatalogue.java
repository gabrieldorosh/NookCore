package net.nikosnook.core;

import java.util.*;
import java.util.function.Predicate;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Cosmetic definitions only. Stable keys and criteria preserve Minecraft progress. */
record MilestoneCatalogue(boolean enabled,Definition root,List<Definition> milestones) {
    static final String ROOT="nikosnook:small_comforts",CHOICE="nikosnook:quests/a_little_choice";
    enum Event { QUESTS_CHOSEN, PIP_VISITED, COMMUNITY_CONTRIBUTION, WEEK_COMPLETE }
    record Definition(String id,String title,String description,String icon,Event event,boolean enabled,boolean showToast) {
        String criterion(){return id.equals(ROOT)?"visit":id.equals(CHOICE)?"chosen":"complete";}
        String json(){
            String display="\"icon\":{\"id\":"+quote("minecraft:"+icon.toLowerCase(Locale.ROOT))+"},\"title\":{\"text\":"+quote(title)+"},\"description\":{\"text\":"+quote(description)+"},\"frame\":\"task\",\"announce_to_chat\":false,\"show_toast\":"+showToast;
            return "{"+(id.equals(ROOT)?"":"\"parent\":"+quote(ROOT)+",")+"\"criteria\":{"+quote(criterion())+":{\"trigger\":\"minecraft:impossible\"}},\"requirements\":[["+quote(criterion())+"]],\"display\":{"+display+(id.equals(ROOT)?",\"background\":\"minecraft:gui/advancements/backgrounds/stone\"":"")+"}}";
        }
    }
    static MilestoneCatalogue parse(String yaml,Predicate<String> validMaterial){
        var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setCodePointLimit(262144);
        var document=map(new Yaml(new SafeConstructor(options)).load(yaml),"advancements.yml");
        keys(document,Set.of("schema-version","enabled","root","milestones"),"advancements.yml");
        Object version=document.getOrDefault("schema-version",1);
        if(!(version instanceof Integer) || (int)version!=1)throw bad("schema-version","expected 1");
        var root=map(document.get("root"),"root");keys(root,Set.of("title","description","icon"),"root");
        var rootDefinition=definition(ROOT,root,null,true,false,validMaterial);
        var entries=map(document.get("milestones"),"milestones");if(entries.size()>128)throw bad("milestones","at most 128 definitions are supported");
        List<Definition> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(var entry:entries.entrySet()){
            String id=id(entry.getKey());if(id.equals(ROOT)||!ids.add(id))throw bad(entry.getKey(),"duplicate or reserved advancement ID");
            var config=map(entry.getValue(),entry.getKey());keys(config,Set.of("title","description","icon","event","enabled","show-toast"),entry.getKey());
            Event event;try{event=Event.valueOf(text(config,"event",48).toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){throw bad(entry.getKey()+".event","use QUESTS_CHOSEN, PIP_VISITED, COMMUNITY_CONTRIBUTION or WEEK_COMPLETE");}
            result.add(definition(id,config,event,bool(config,"enabled",true),bool(config,"show-toast",true),validMaterial));
        }
        return new MilestoneCatalogue(bool(document,"enabled",true),rootDefinition,List.copyOf(result));
    }
    private static Definition definition(String id,Map<String,Object> data,Event event,boolean enabled,boolean show,Predicate<String> validMaterial){
        String icon=text(data,"icon",80).toUpperCase(Locale.ROOT);if(icon.startsWith("MINECRAFT:"))icon=icon.substring(10);
        if(!icon.matches("[A-Z0-9_]+")||!validMaterial.test(icon))throw bad(id+".icon","choose an existing Minecraft item material");
        return new Definition(id,text(data,"title",80),text(data,"description",256),icon,event,enabled,show);
    }
    private static String id(String raw){
        String id=raw.contains(":")?raw:"nikosnook:"+raw;String[] pieces=id.split(":",-1);
        if(pieces.length!=2||!pieces[0].equals("nikosnook")||pieces[1].length()>96||!pieces[1].matches("[a-z0-9_./-]+")||pieces[1].startsWith("/")||pieces[1].endsWith("/")||Arrays.stream(pieces[1].split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw bad(raw,"use a stable lowercase nikosnook ID/path");
        return id;
    }
    private static Map<String,Object> map(Object value,String path){
        if(!(value instanceof Map<?,?> raw))throw bad(path,"expected a section");Map<String,Object> result=new LinkedHashMap<>();
        for(var e:raw.entrySet()){if(!(e.getKey() instanceof String key))throw bad(path,"keys must be text");result.put(key,e.getValue());}return result;
    }
    private static void keys(Map<String,Object> map,Set<String> accepted,String path){for(String key:map.keySet())if(!accepted.contains(key))throw bad(path+"."+key,"unsupported field; rewards and commands are not part of cosmetic milestones");}
    private static boolean bool(Map<String,Object> map,String key,boolean fallback){Object value=map.getOrDefault(key,fallback);if(!(value instanceof Boolean b))throw bad(key,"expected true or false");return b;}
    private static String text(Map<String,Object> map,String key,int maximum){Object value=map.get(key);if(!(value instanceof String s)||s.isBlank()||s.length()>maximum)throw bad(key,"expected nonempty text, up to "+maximum+" characters");return s;}
    private static IllegalArgumentException bad(String path,String message){return new IllegalArgumentException(path+": "+message);}
    private static String quote(String value){
        var out=new StringBuilder("\"");for(char c:value.toCharArray()){switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}}return out.append('"').toString();
    }
}
