package io.github.ling.style.core;
/** Native reply references are local IDs and/or server sequence numbers, never text searches. */
public final class QuoteRef {
    public final long storedId,replayId,sequence;
    public final boolean expired,imageHint;
    public QuoteRef(long stored,long replay,long seq,boolean gone,boolean image){storedId=stored;replayId=replay;sequence=seq;expired=gone;imageHint=image;}
    public boolean valid(){return !expired&&(storedId!=0||replayId!=0||sequence>0);}
    public boolean matches(long id,long seq){return valid()&&((id!=0&&(id==storedId||id==replayId))||(sequence>0&&seq==sequence));}
}
