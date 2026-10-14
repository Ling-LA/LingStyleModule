package io.github.ling.style.core;
import java.util.Locale;
/** Only explicit modality errors establish lack of vision support. */
public final class VisionPolicy {
    private VisionPolicy(){}
    public static boolean unsupported(int code,String detail){
        if(code!=400&&code!=422)return false;
        String s=detail==null?"":detail.toLowerCase(Locale.ROOT);
        boolean image=s.contains("image_url")||s.contains("image input")||s.contains("image content")||s.contains("vision")||s.contains("图片")||s.contains("图像");
        boolean no=s.contains("not support")||s.contains("unsupported")||s.contains("does not accept")||s.contains("text-only")||s.contains("不支持");
        return image&&no;
    }
    public static final int IMAGE_TOKENS=4096,MAX_IMAGES=2;
}
