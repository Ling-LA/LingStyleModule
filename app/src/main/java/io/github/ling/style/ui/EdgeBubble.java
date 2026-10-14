package io.github.ling.style.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** A view-local edge bubble. No overlay permission or background timer. */
@android.annotation.SuppressLint("ViewConstructor") // Created only by code with its host Activity.
public final class EdgeBubble extends TextView {
    private final SharedPreferences position;
    private final int slop;
    private boolean right, tucked, pressed, dragging, cancelled, menuOpen;
    private float fraction, downX, downY, originX, originY, latestX, latestY;
    private int pointer=-1;
    private ViewGroup container;
    private final Runnable hide=()->{if(!pressed&&!menuOpen&&isAttachedToWindow())dock(true,true);};
    private final Runnable hold=()->{
        if(!pressed||cancelled)return;
        dragging=true; tucked=false; describe();
        animate().cancel(); setX(clamp(getX(),left(),rightX()));
        originX=getX();originY=getY();downX=latestX;downY=latestY;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
    };
    private final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->{
        if(!pressed&&getWidth()>0)dock(tucked,false);
    };

    public EdgeBubble(Activity activity){
        super(activity);
        position=activity.getSharedPreferences("ling_style_floating_ui",Context.MODE_PRIVATE);
        right=position.getBoolean("right",true);
        fraction=clamp(position.getFloat("vertical",.5f),0,1);
        slop=ViewConfiguration.get(activity).getScaledTouchSlop();
        setText("玲");setTextColor(PinkTheme.DEEP);setTextSize(19);
        setTypeface(Typeface.DEFAULT,Typeface.BOLD);setGravity(Gravity.CENTER);
        setElevation(dp(5));setBackground(PinkTheme.shape(activity,PinkTheme.SOFT,18,PinkTheme.PINK));
        setFocusable(true);setClickable(true);describe();
    }
    public void mount(ViewGroup parent){
        container=parent;
        parent.addView(this,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT));
        parent.addOnLayoutChangeListener(layout);
        post(()->{if(isAttachedToWindow()){dock(false,false);arm();}});
    }
    public void setMenuOpen(boolean open){menuOpen=open;removeCallbacks(hide);if(!open)arm();}
    public void dispose(){
        removeCallbacks(hide);removeCallbacks(hold);animate().cancel();
        if(container!=null){container.removeOnLayoutChangeListener(layout);container.removeView(this);container=null;}
    }
    @Override protected void onDetachedFromWindow(){
        removeCallbacks(hide);removeCallbacks(hold);animate().cancel();
        if(container!=null)container.removeOnLayoutChangeListener(layout);
        super.onDetachedFromWindow();
    }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        post(()->{if(isAttachedToWindow()&&!pressed)dock(tucked,false);});
        return super.onApplyWindowInsets(insets);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        // Reserve only the bubble's small footprint, leaving the rest of back gestures intact.
        if(Build.VERSION.SDK_INT>=29)setSystemGestureExclusionRects(java.util.Collections.singletonList(new android.graphics.Rect(0,0,w,h)));
    }
    @Override public boolean performClick(){
        if(tucked){dock(false,true);arm();return true;}
        boolean handled=super.performClick();arm();return handled;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(hide);animate().cancel();pressed=true;dragging=false;cancelled=false;
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                pointer=e.getPointerId(0);downX=latestX=e.getRawX();downY=latestY=e.getRawY();
                originX=getX();originY=getY();postDelayed(hold,ViewConfiguration.getLongPressTimeout());return true;
            case MotionEvent.ACTION_MOVE:
                if(!pressed||e.findPointerIndex(pointer)<0)return true;
                latestX=e.getRawX();latestY=e.getRawY();
                float dx=latestX-downX,dy=latestY-downY;
                if(!dragging&&dx*dx+dy*dy>slop*slop){cancelled=true;removeCallbacks(hold);}
                if(dragging){setX(clamp(originX+dx,left(),rightX()));setY(clamp(originY+dy,top(),bottomY()));}
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                finishGesture(false);return true;
            case MotionEvent.ACTION_UP:
                finishGesture(true);return true;
            default:return true;
        }
    }
    private void finishGesture(boolean click){
        if(!pressed)return;
        removeCallbacks(hold);pressed=false;pointer=-1;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        if(dragging){
            right=getX()+getWidth()/2f>=(left()+rightX()+getWidth())/2f;
            // Cancellation restores the saved position rather than saving an interrupted drag.
            if(click){fraction=bottomY()>top()?(getY()-top())/(bottomY()-top()):.5f;
                position.edit().putBoolean("right",right).putFloat("vertical",fraction).apply();}
            else right=position.getBoolean("right",true);
            dock(false,true);
        }else if(click&&!cancelled)performClick();
        else dock(tucked,true);
        dragging=false;arm();
    }
    private void arm(){removeCallbacks(hide);if(!menuOpen&&!pressed&&isAttachedToWindow())postDelayed(hide,3000);}
    private void dock(boolean collapse,boolean smooth){
        if(container==null||getWidth()==0)return;
        tucked=collapse;describe();animate().cancel();
        float x=right?rightX():left();
        if(collapse)x+=(right?1:-1)*(getWidth()-dp(12));
        float y=top()+fraction*(bottomY()-top());
        if(smooth)animate().x(x).y(y).setDuration(220).start();else{setX(x);setY(y);}
    }
    private void describe(){setContentDescription(tucked?"展开玲风格悬浮球":"打开玲风格控制菜单");}
    private int inset(int side){
        WindowInsets w=getRootWindowInsets();if(w==null)return 0;
        if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=w.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            return side==0?i.left:side==1?i.top:side==2?i.right:i.bottom;}
        return side==0?w.getSystemWindowInsetLeft():side==1?w.getSystemWindowInsetTop():side==2?w.getSystemWindowInsetRight():w.getSystemWindowInsetBottom();
    }
    private float left(){return inset(0);}
    private float rightX(){return Math.max(left(),container.getWidth()-inset(2)-getWidth());}
    private float top(){return inset(1)+dp(8);}
    private float bottomY(){return Math.max(top(),container.getHeight()-inset(3)-getHeight()-dp(8));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static float clamp(float n,float min,float max){return Math.max(min,Math.min(max,n));}
}
