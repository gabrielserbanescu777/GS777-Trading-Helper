package ro.gs777.trading;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import java.io.InputStream;
import java.util.Locale;

/** Conservative screenshot heuristics; no OCR, neural model, or inferred price scale. */
final class PhotoChartAnalyzer {
 static final class Signal{
  String side,why;int columns;
  Signal(String s,String reason,int n){side=s;why=reason;columns=n;}
 }
 static Signal inspect(Context c,Uri uri)throws Exception{
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
  try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
  if(bounds.outWidth<120||bounds.outHeight<100)throw new Exception("Imaginea este prea mica.");
  BitmapFactory.Options options=new BitmapFactory.Options();
  options.inSampleSize=Math.max(1,Math.max(bounds.outWidth/800,bounds.outHeight/1000));
  Bitmap img;
  try(InputStream in=c.getContentResolver().openInputStream(uri)){img=BitmapFactory.decodeStream(in,null,options);}
  if(img==null)throw new Exception("Imaginea nu poate fi citita.");
  try {
   int width=img.getWidth(),height=img.getHeight(),bins=48;
   int[] highs=new int[bins],lows=new int[bins],greens=new int[bins],reds=new int[bins];
   java.util.Arrays.fill(highs,height);
   int left=width/12,right=width-left,top=height/7,bottom=height*5/6;
   float[] hsv=new float[3];
   for(int y=top;y<bottom;y+=2)for(int x=left;x<right;x+=2){
    int color=img.getPixel(x,y);Color.colorToHSV(color,hsv);
    boolean green=hsv[0]>=65&&hsv[0]<=175&&hsv[1]>.43&&hsv[2]>.27;
    boolean red=(hsv[0]<22||hsv[0]>340)&&hsv[1]>.48&&hsv[2]>.28;
    if(!green&&!red)continue;
    int bin=Math.min(bins-1,(x-left)*bins/(right-left));
    highs[bin]=Math.min(highs[bin],y);lows[bin]=Math.max(lows[bin],y);
    if(green)greens[bin]++;else reds[bin]++;
   }
   int found=0,up=0,down=0,firstN=0,lastN=0;
   double first=0,last=0;
   for(int i=0;i<bins;i++){
    int pixels=greens[i]+reds[i];
    if(pixels<Math.max(4,(bottom-top)/130)||lows[i]-highs[i]<4)continue;
    found++;
    if(greens[i]>reds[i]*1.35)up++;else if(reds[i]>greens[i]*1.35)down++;
    double mid=(highs[i]+lows[i])/2.0;
    if(i<bins/3){first+=mid;firstN++;}
    if(i>=bins*2/3){last+=mid;lastN++;}
   }
   if(found<15||firstN<4||lastN<4)
    return new Signal("ASTEAPTA","Nu se disting suficiente lumanari rosii/verzi in zona graficului. Decupeaza poza astfel incat graficul sa umple ecranul.",found);
   double travel=(first/firstN-last/lastN)/(bottom-top);
   int bias=up-down;int side=travel>.055&&bias>=2?1:travel<-.055&&bias<=-2?-1:0;
   String why=String.format(Locale.US,"Din %d coloane colorate, %d predomina verde si %d rosu. Structura vizuala %s (deplasare %.1f%% din inaltimea zonei).",
    found,up,down,travel>0?"urca":travel<0?"coboara":"este laterala",100*Math.abs(travel));
   if(side==0)return new Signal("ASTEAPTA",why+" Tiparul nu este clar; culoarea si inclinarea nu sunt suficiente.",found);
   return new Signal(side>0?"LONG":"SHORT",why+" Cauta suport/rezistenta, inchidere si retest; o fotografie nu contine preturi verificabile.",found);
  }finally{img.recycle();}
 }
 static String analyze(Context context,Uri uri,String symbol){
  String now=new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new java.util.Date());
  try{
   Signal image=inspect(context,uri);
   String visual="DIN POZA (euristica de culoare): "+image.side+". "+image.why;
   if(!symbol.matches("[A-Z0-9]{2,22}USDT"))
    return "CONCLUZIE: ASTEAPTA\n"+visual+"\nCoin USDT invalid. Pretul din fotografie nu a fost citit.";
   try{
    Market.Analysis a=Market.analyze(symbol);
    String market=a.score>=3?"LONG":a.score<=-3?"SHORT":"ASTEAPTA";
    String verified=String.format(Locale.US,"DIN BYBIT %s la %s: %s, scor %+d/5, RSI 1h %.0f, RSI 1D %.0f, 24h %+.1f%%.",
     symbol,now,market,a.score,a.rsi,a.dailyRsi,a.coin.change);
    String conclusion=market.equals(image.side)&&!market.equals("ASTEAPTA")?market:"ASTEAPTA";
    return "CONCLUZIE: "+conclusion+"\n"+visual+"\n"+verified
     +"\nFotografia nu confirma niveluri sau preturi. Sunt detectati doar pixeli rosii/verzi si inclinarea lor; verifica manual simbolul, intervalul si retestul.";
   }catch(Exception ex){
    return "CONCLUZIE: ASTEAPTA\n"+visual+"\nDIN BYBIT: date indisponibile ("+ex.getMessage()+").";
   }
  }catch(Exception ex){return "CONCLUZIE: ASTEAPTA\nPoza nu a putut fi citita: "+ex.getMessage();}
 }
}