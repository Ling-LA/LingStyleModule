package io.github.ling.style.core;

import java.io.IOException;

/** A fixed diagnostic code, never a raw response, prompt, account or credential. */
public class ReplyFault extends IOException {
    public final FaultKind kind;
    public final String code;
    public ReplyFault(FaultKind kind,String code,String message){super(message);this.kind=kind;this.code=safeCode(code);}
    public ReplyFault(FaultKind kind,String code,String message,Throwable cause){this(kind,code,message);initCause(cause);}
    public static String safeCode(String code){return code!=null&&code.matches("[A-Za-z0-9_.-]{1,80}")?code:"unspecified";}
}
