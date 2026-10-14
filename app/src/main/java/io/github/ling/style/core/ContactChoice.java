package io.github.ling.style.core;
import java.util.*;
/** Local selection model; IDs without a roster entry are preserved. */
public final class ContactChoice {
    public final String id,name;public final Set<String> aliases;
    public ContactChoice(String id,String name,String... aliases){this.id=id;this.name=name;Set<String> ids=new HashSet<>();ids.add(id);for(String alias:aliases)if(alias!=null&&!alias.isEmpty())ids.add(alias);this.aliases=Collections.unmodifiableSet(ids);}
    public boolean selected(Set<String> values){for(String value:aliases)if(values.contains(value))return true;return false;}
    public static Set<String> apply(Set<String> previous,List<ContactChoice> rows,Set<String> checked){
        Set<String> next=new TreeSet<>(previous);for(ContactChoice row:rows){next.removeAll(row.aliases);if(checked.contains(row.id))next.add(row.id);}return next;
    }
}
