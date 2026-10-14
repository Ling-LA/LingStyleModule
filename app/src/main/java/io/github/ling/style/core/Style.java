package io.github.ling.style.core;

public enum Style {
    CAT("猫娘语", "https://yinglish.club/translators/%E8%B2%93%E5%A8%98%E8%AA%9E"),
    TEASE("雌小鬼", "https://yinglish.club/translators/%E9%9B%8C%E5%B0%8F%E9%AC%BC"),
    YANDERE("病娇", "https://yinglish.club/translators/%E7%97%85%E5%A8%87"),
    LEWD("淫语", "https://yinglish.club/translators/%E6%B7%AB%E8%AF%AD%E7%BF%BB%E8%AF%91%E5%99%A8");
    public final String title, url;
    Style(String title,String url){this.title=title;this.url=url;}
    public boolean available(){return !url.isEmpty();}
    public static Style from(String id){try{Style s=valueOf(id);return s.available()?s:CAT;}catch(Exception e){return CAT;}}
}
