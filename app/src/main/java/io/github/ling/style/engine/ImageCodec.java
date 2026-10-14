package io.github.ling.style.engine;
import android.graphics.*;
import android.media.ExifInterface;
import android.util.Base64;
import java.io.*;
/** Re-encoding strips file metadata; never uploads original files or QQ image URLs. */
public final class ImageCodec {
    public static final long MAX_SOURCE=64*1024*1024;
    private ImageCodec(){}
    public static String encode(File file)throws IOException{
        if(!file.isFile())throw new IOException("QQ 返回的图片文件不存在");
        if(file.length()==0)throw new IOException("QQ 返回的图片文件为空");
        if(file.length()>MAX_SOURCE)throw new IOException("图片解码文件超过 64 MiB（"+(file.length()/1048576)+" MiB）");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),options);
        if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>100000000)throw new IOException("图片格式或尺寸不支持");
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2560)options.inSampleSize*=2;
        Bitmap original=BitmapFactory.decodeFile(file.getPath(),options);if(original==null)throw new IOException("图片解码失败");
        Bitmap result=null;
        try{
            int orientation=1;try{orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
            Matrix matrix=new Matrix();float scale=Math.min(1f,1280f/Math.max(original.getWidth(),original.getHeight()));matrix.postScale(scale,scale);
            if(orientation==3)matrix.postRotate(180);else if(orientation==6)matrix.postRotate(90);else if(orientation==8)matrix.postRotate(270);
            else if(orientation==2)matrix.postScale(-1,1);else if(orientation==4)matrix.postScale(1,-1);
            else if(orientation==5){matrix.postRotate(90);matrix.postScale(-1,1);}else if(orientation==7){matrix.postRotate(270);matrix.postScale(-1,1);}
            result=Bitmap.createBitmap(original,0,0,original.getWidth(),original.getHeight(),matrix,true);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();result.compress(Bitmap.CompressFormat.JPEG,82,bytes);
            if(bytes.size()>768*1024){bytes.reset();result.compress(Bitmap.CompressFormat.JPEG,60,bytes);}
            if(bytes.size()>768*1024)throw new IOException("压缩后的图片仍超过限制");
            return "data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);
        }finally{if(result!=null&&result!=original)result.recycle();original.recycle();}
    }
}
