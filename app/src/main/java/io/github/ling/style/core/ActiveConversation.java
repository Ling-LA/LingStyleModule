package io.github.ling.style.core;

/** Immutable, account-bound visible conversation; never retained in preferences. */
public final class ActiveConversation {
    public final String account,uid;
    public final int type;
    public ActiveConversation(String account,int type,String uid){this.account=account;this.type=type;this.uid=uid;}
    public boolean matches(boolean enabled,boolean visible,String account,int type,String uid){
        return enabled&&visible&&this.type==type&&(type==1||type==2||type==100)
            &&this.account!=null&&!this.account.isEmpty()&&this.account.equals(account)
            &&this.uid!=null&&!this.uid.isEmpty()&&this.uid.equals(uid);
    }
}
