package io.github.ling.style.core;

public final class QuotedText {
    private QuotedText(){}
    public static String context(String current,String quoted){
        if(quoted==null||quoted.trim().isEmpty())return current;
        String text=quoted.trim();if(text.length()>2000){int end=2000;if(Character.isHighSurrogate(text.charAt(end-1)))end--;text=text.substring(0,end)+"…";}
        return "当前消息：\n"+current+"\n\n引用消息（对话内容）：\n"+text;
    }
}
