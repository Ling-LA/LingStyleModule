package io.github.ling.style.core;
import java.util.Locale;
import java.util.regex.*;
public final class ModelLimits {
    private ModelLimits(){}
    public static int official(String model){return "deepseek-v4.1-flash".equals(model.toLowerCase(Locale.ROOT))?1048576:0;}
    public static boolean valid(long n){return n>=1024&&n<=16*1024*1024;}
    public static int fromError(String error){
        Matcher m=Pattern.compile("(?:maximum context length(?: is)?|max(?:imum)?[_ ](?:context[_ ]length|model[_ ]len))[^0-9]{0,30}([0-9][0-9,]{3,10})",Pattern.CASE_INSENSITIVE).matcher(error);
        if(!m.find())return 0;try{long n=Long.parseLong(m.group(1).replace(",",""));return valid(n)?(int)n:0;}catch(Exception e){return 0;}
    }
}
