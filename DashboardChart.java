package ro.gs777.trading;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.Arrays;
/** OHLC candle chart. Touch to pan; pinch to zoom. MA overlays require real historical candles. */
final class DashboardChart extends View {
 private double[][] bars=new double[0][4];private final Paint paint=new Paint(3);
 private final ScaleGestureDetector zoom;private int visible=65,offset=0;private float lastX;
 private double entry=Double.NaN,tp=Double.NaN,tp2=Double.NaN,sl=Double.NaN,breakout=Double.NaN,retest=Double.NaN;
 DashboardChart(Context c){super(c);zoom=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
  @Override public boolean onScale(ScaleGestureDetector d){int old=visible;visible=Math.max(12,Math.min(180,Math.round(visible/d.getScaleFactor())));offset=Math.max(0,Math.min(offset+Math.max(0,(old-visible)/2),Math.max(0,bars.length-visible)));invalidate();return true;}
 });}
 void setCandles(double[][] input){if(input==null)return;bars=new double[input.length][4];for(int i=0;i<input.length;i++)bars[i]=Arrays.copyOf(input[i],4);offset=0;invalidate();}
 void updateLive(double price){if(!Double.isFinite(price)||price<=0||bars.length==0)return;double[] c=bars[bars.length-1];c[3]=price;c[1]=Math.max(c[1],price);c[2]=Math.min(c[2],price);invalidate();}
 void setTradeLevels(double entryPrice,double takeProfit1,double takeProfit2,double stopLoss){entry=entryPrice;tp=takeProfit1;tp2=takeProfit2;sl=stopLoss;invalidate();}
 void setSetupLevels(double b,double r){breakout=b;retest=r;invalidate();}
 void clearSetupLevels(){breakout=retest=Double.NaN;invalidate();}
 void clearTradeLevels(){entry=tp=tp2=sl=Double.NaN;invalidate();}
 @Override public boolean onTouchEvent(MotionEvent event){zoom.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=event.getX();return true;}if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!zoom.isInProgress()&&event.getPointerCount()==1){float delta=event.getX()-lastX;int shift=Math.round(delta/Math.max(3f,(float)getWidth()/visible));if(shift!=0){offset=Math.max(0,Math.min(Math.max(0,bars.length-visible),offset+shift));lastX=event.getX();invalidate();}return true;}return true;}
 private double ma(int index,int period){if(index+1<period)return Double.NaN;double sum=0;for(int i=index-period+1;i<=index;i++)sum+=bars[i][3];return sum/period;}
 private void overlay(Canvas c,int first,int last,int period,int color,double min,double range,int w,int h){paint.setColor(color);paint.setStrokeWidth(2);paint.setStyle(Paint.Style.STROKE);Path path=new Path();boolean started=false;float spacing=(float)w/(last-first+1);for(int i=first;i<=last;i++){double v=ma(i,period);if(!Double.isFinite(v))continue;float x=(i-first+.5f)*spacing,y=(float)(h-20-(v-min)/range*(h-40));if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);}c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
 private void level(Canvas c,double level,int color,double min,double range,int w,int h){if(!Double.isFinite(level)||level<=0)return;float y=(float)(h-20-(level-min)/range*(h-40));paint.setColor(color);paint.setStrokeWidth(2);c.drawLine(0,y,w,y,paint);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();c.drawColor(0xff191611);if(bars.length==0)return;int last=bars.length-1-offset,first=Math.max(0,last-visible+1);if(last<first)return;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(int i=first;i<=last;i++){min=Math.min(min,bars[i][2]);max=Math.max(max,bars[i][1]);for(int n:new int[]{50,100,200}){double v=ma(i,n);if(Double.isFinite(v)){min=Math.min(min,v);max=Math.max(max,v);}}}if(!Double.isFinite(min)||!Double.isFinite(max))return;
 for(double v:new double[]{entry,tp,tp2,sl,breakout,retest})if(Double.isFinite(v)&&v>0){min=Math.min(min,v);max=Math.max(max,v);}
 double range=Math.max(max-min,Math.abs(max)*.0001);float space=(float)w/(last-first+1);paint.setStrokeWidth(1);paint.setColor(0xff403a2d);for(int i=1;i<4;i++)c.drawLine(0,h*i/4f,w,h*i/4f,paint);for(int i=first;i<=last;i++){double[] b=bars[i];boolean up=b[3]>=b[0];paint.setColor(up?0xff42c99b:0xffed6870);float x=(i-first+.5f)*space;float top=(float)(h-20-(b[1]-min)/range*(h-40)),bottom=(float)(h-20-(b[2]-min)/range*(h-40));c.drawLine(x,top,x,bottom,paint);float y1=(float)(h-20-(Math.max(b[0],b[3])-min)/range*(h-40)),y2=(float)(h-20-(Math.min(b[0],b[3])-min)/range*(h-40));c.drawRect(x-Math.max(1,space*.3f),y1,x+Math.max(1,space*.3f),Math.max(y1+1,y2),paint);}overlay(c,first,last,50,0xffe4c05d,min,range,w,h);overlay(c,first,last,100,0xff65c7ec,min,range,w,h);overlay(c,first,last,200,0xffd995e9,min,range,w,h);level(c,entry,0xffffffff,min,range,w,h);level(c,tp,0xff42c99b,min,range,w,h);level(c,tp2,0xff23a8a3,min,range,w,h);level(c,sl,0xffed6870,min,range,w,h);level(c,breakout,0xffffae42,min,range,w,h);if(Double.isFinite(retest))level(c,retest,0xff59dbcf,min,range,w,h);}
}
