package io.github.ling.style.hook;

import android.content.Context;
import android.os.Bundle;
import io.github.ling.style.core.*;
import io.github.ling.style.engine.ReplyProtocol;
import io.github.ling.style.store.FaultStore;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Synthetic native-source fixtures, actual resolver and Android persistence/JSON; no QQ sends or API calls. */
public final class RuntimeRegressionChecks {
    private final Bundle report=new Bundle();private int checks;
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;report.putBoolean(name,true);}
    public static Bundle run(Context context){return new RuntimeRegressionChecks().execute(context);}
    private Bundle execute(Context context){
        ExecutorService writer=Executors.newSingleThreadExecutor();File file=new File(context.getFilesDir(),"ling-style-faults-v1.tsv");
        try{
            QuoteRef textRef=new QuoteRef(11,12,13,false,false),imageRef=new QuoteRef(11,12,13,false,true);
            QuotedImages.Source text=new QuotedImages.Source(20,Collections.emptyList(),"被引用的普通文字");
            IncomingImages.Picture pic=new IncomingImages.Picture(30,40,"",Collections.emptyList());
            QuotedImages.Source image=new QuotedImages.Source(21,Collections.singletonList(pic),"图片说明");
            int[] calls={0};QuotedImages.Source got=QuotedImages.resolve(textRef,fetch->{calls[0]++;return text;});
            check(got.pictures.isEmpty()&&got.text.equals(text.text)&&calls[0]==1,"text_quote_succeeds_without_image_fallback");
            check(QuotedText.context("当前问题",got.text).contains(text.text),"text_quote_retained_as_context");
            got=QuotedImages.resolve(textRef,fetch->image);
            check(got.pictures.size()==1&&got.message==21,"actual_image_detected_even_without_hint");
            got=QuotedImages.resolve(imageRef,fetch->fetch?text:image);
            check(got.pictures.size()==1&&got.message==21,"image_hint_uses_source_fallback");
            got=QuotedImages.resolve(imageRef,fetch->fetch?null:image);
            check(got==image,"missing_fetch_uses_record_fallback");
            boolean rejected=false;try{QuotedImages.resolve(imageRef,fetch->text);}catch(IOException e){rejected=true;}
            check(rejected,"missing_expected_image_still_rejected");
            rejected=false;try{QuotedImages.resolve(textRef,fetch->null);}catch(IOException e){rejected=true;}
            check(rejected,"missing_reference_is_not_invented");
            calls[0]=0;rejected=false;try{QuotedImages.resolve(new QuoteRef(11,12,13,true,false),fetch->{calls[0]++;return text;});}catch(IOException e){rejected=true;}
            check(rejected&&calls[0]==0,"expired_reference_not_read");
            check(ReplyProtocol.decision("```JSON\n{\"action\":\"answer\",\"text\":\"普通文字\"}\n```").getString("text").equals("普通文字"),"android_json_fence_supported");
            check(ReplyProtocol.decision("{\"action\":\"silent\"}").getString("text").isEmpty(),"android_silent_without_text");
            rejected=false;try{ReplyProtocol.decision("{\"action\":\"answer\",\"text\":\"x\"} extra");}catch(ReplyFault e){rejected=e.kind==FaultKind.DECISION_JSON;}
            check(rejected,"android_trailing_decision_rejected");
            rejected=false;try{ReplyProtocol.response("<html>not json</html>",false);}catch(ReplyFault e){rejected=e.kind==FaultKind.RESPONSE_JSON&&e.code.equals("response.syntax.html");}
            check(rejected,"android_html_response_classified");
            FaultStore store=new FaultStore(context,writer);
            long before=store.history.errors();for(int i=0;i<230;i++)store.add(FaultKind.DECISION_SCHEMA,Style.CAT,"decision.action");
            // The store may coalesce changes into another queued flush; wait until its file catches up.
            long until=android.os.SystemClock.elapsedRealtime()+5000;
            FaultStore restored;
            do{writer.submit(()->{}).get(2,TimeUnit.SECONDS);restored=new FaultStore(context,writer);if(restored.history.errors()==before+230)break;Thread.sleep(20);}while(android.os.SystemClock.elapsedRealtime()<until);
            report.putLong("history_memory_total",store.history.errors());report.putLong("history_disk_total",restored.history.errors());report.putInt("history_disk_entries",restored.history.entries().size());report.putLong("history_file_bytes",file.length());
            if(restored.history.errors()!=before+230)report.putString("history_read_status",restored.report().split("\n")[0]);
            check(restored.history.errors()==before+230&&restored.history.entries().size()==200,"atomic_history_restores_totals_and_limit");
            check(restored.report().contains("decision.action")&&!restored.report().contains("被引用的普通文字"),"history_contains_no_quote_text");
            try(FileOutputStream out=new FileOutputStream(file)){out.write("damaged".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            FaultStore corrupt=new FaultStore(context,writer);
            check(corrupt.history.errors()==0&&corrupt.report().contains("无法读取"),"corrupt_file_recovers_with_visible_notice");
            report.putBoolean("success",true);
        }catch(Throwable e){report.putString("failure",e.toString());}
        finally{writer.shutdown();try{writer.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}file.delete();new File(file.getPath()+".bak").delete();new File(file.getPath()+".new").delete();}
        report.putInt("checks",checks);return report;
    }
}
