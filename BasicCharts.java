package ro.gs777.trading;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.*;

final class BasicCharts {
 static final int GOLD=0xfff1c150,GREEN=0xff6fe4a3,RED=0xfff97986,INK=0xff111212;
 static final String[] HEAD={"01 / Tipare BEARISH","02 / Tipare BULLISH","03 / Tipare de lumânări","04 / Respingeri la suport / rezistență","05 / Lumânări importante","06 / Strategie de trading","07 / Analiză tehnică","08 / Citirea lumânărilor","09 / Tipare de inversare","10 / Semnale din lumânări","11 / Ghid rapid de tipare","12 / Cinci tipare bullish"};
 static final String[][][] CARDS={
  {{"Cap și umeri","p0","Trei vârfuri; cel din mijloc mai sus. Confirmare sub linia gâtului."},{"Dublu vârf","p1","Două teste la rezistență, apoi ruperea minimului dintre ele."},{"Triplu vârf","p2","Trei teste la rezistență; urmărește suportul."},{"Triunghi descendent","p3","Maxime descendente și suport orizontal."},{"Pană ascendentă","p4","Urcare într-un interval tot mai strâns; așteaptă ruperea."}},
  {{"Cap și umeri inversat","p5","Trei minime; cel din mijloc mai jos. Confirmare peste linia gâtului."},{"Dublu minim","p6","Două teste la suport, apoi ruperea maximului dintre ele."},{"Triplu minim","p7","Trei teste la suport; urmărește rezistența."},{"Triunghi ascendent","p8","Minime crescătoare și rezistență orizontală."},{"Pană descendentă","p9","Scădere într-un interval tot mai strâns; așteaptă ieșirea."}},
  {{"Doji","c0","Deschiderea și închiderea sunt apropiate: indecizie."},{"Hammer","c1","Corp mic sus, umbră lungă jos."},{"Shooting star","c2","Corp mic jos, umbră lungă sus."},{"Bullish engulfing","c3","Corpul verde îl acoperă pe cel roșu."},{"Bearish engulfing","c4","Corpul roșu îl acoperă pe cel verde."}},
  {{"Respingere la suport","r0","Umbra trece sub nivel, închiderea revine deasupra."},{"Respingere la rezistență","r1","Umbra trece peste nivel, închiderea revine dedesubt."},{"Spargere falsă","r2","Prețul trece temporar peste nivel și revine."},{"Spargere și retest","r3","După ieșire, prețul testează din nou nivelul."}},
  {{"Hammer / hanging man","c1","Forma poate căpăta alt sens în funcție de trend."},{"Inverted hammer","c5","Corp mic jos, umbră lungă sus după scădere."},{"Spinning top","c6","Corp mic și două umbre: echilibru temporar."},{"Marubozu bullish","c7","Corp verde mare cu umbre foarte mici."},{"Marubozu bearish","c8","Corp roșu mare cu umbre foarte mici."}},
  {{"Context 1D → 4H → 1H","t0","Trend pe 1D, zone pe 4H și confirmarea intrării pe 1H."},{"Intrare după confirmare","t1","Așteaptă închiderea și, la nevoie, retestul."},{"Risc și invalidare","t2","Notează intrarea, stopul, ținta, costurile și mărimea poziției."}},
  {{"Trend și structură","t0","Maxime și minime crescătoare sau descrescătoare."},{"Suport / rezistență","r3","Zone testate repetat, nu puncte fixe."},{"Medii mobile","t1","Mediile arată contextul, fără a garanta direcția."},{"RSI și volum","t2","Caută confirmare din mai multe surse."}},
  {{"Anatomia lumânării","c9","Corpul: deschidere/închidere. Umbrele: maxim/minim."},{"Verde și roșu","c10","Verde închide peste deschidere; roșu dedesubt."},{"Două lumânări","c3","Compară corpurile, umbrele și poziția față de nivel."}},
  {{"Dublu vârf / minim","p10","Forme similare, sens opus; confirmare la nivelul cheie."},{"Rotunjire de vârf / minim","p11","Schimbare treptată a structurii."},{"Cap și umeri","p0","Linia gâtului stabilește confirmarea."}},
  {{"Morning star","c11","Roșie, corp mic, apoi verde după o scădere."},{"Evening star","c12","Verde, corp mic, apoi roșie după o urcare."},{"Piercing line","c13","Verde închide peste jumătatea corpului roșu."},{"Dark cloud cover","c14","Roșie închide sub jumătatea corpului verde."}},
  {{"Steag / pennant","p12","Consolidare după impuls: cere ieșire confirmată."},{"Triunghiuri","p13","Direcția se confirmă după spargere."},{"Canal și pană","p14","Urmărește limita și reacția la rupere."},{"Breakout fals","r2","Umbra peste nivel nu echivalează cu închiderea peste el."}},
  {{"Hammer","c1","Umbra inferioară lungă după scădere."},{"Piercing line","c13","A doua lumânare verde revine peste jumătatea primeia."},{"Bullish engulfing","c3","Verde înghite corpul roșu."},{"Morning star","c11","Trei lumânări pot semnala revenire."},{"Three white soldiers","c15","Trei corpuri verzi cu închideri crescătoare."}}
 };
 static void addTo(LinearLayout parent,Context ctx){
  TextView notice=new TextView(ctx);notice.setText("Exemple educative · schemele nu sunt prețuri live și nu reprezintă semnale. Confirmă pe graficul real.");notice.setTextColor(0xffc6c1b4);notice.setTextSize(13);notice.setPadding(9,8,9,12);parent.addView(notice);
  for(int s=0;s<HEAD.length;s++){
   TextView title=new TextView(ctx);title.setText(HEAD[s]);title.setTextColor(GOLD);title.setTextSize(17);title.setTypeface(null,1);title.setPadding(12,17,8,9);parent.addView(title);
   for(String[] row:CARDS[s]){
    LinearLayout box=new LinearLayout(ctx);box.setOrientation(1);box.setPadding(11,10,11,12);box.setBackgroundColor(0xff201f1b);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=8;parent.addView(box,lp);
    TextView label=new TextView(ctx);label.setText(row[0]);label.setTextColor(GOLD);label.setTextSize(15);label.setTypeface(null,1);box.addView(label);
    Diagram d=new Diagram(ctx,row[1],row[0]);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,(int)(106*ctx.getResources().getDisplayMetrics().density));dl.topMargin=7;dl.bottomMargin=7;box.addView(d,dl);
    TextView desc=new TextView(ctx);desc.setText(row[2]);desc.setTextColor(-1);desc.setTextSize(14);box.addView(desc);
   }
  }
 }
 static final class Diagram extends View {
  Canvas c;Paint p=new Paint(3);float w,h;final char kind;final int v;
  Diagram(Context context,String code,String label){super(context);kind=code.charAt(0);v=Integer.parseInt(code.substring(1));setContentDescription("Schemă: "+label);}
  void line(float x,float y,float xx,float yy,int color,float width){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(width);c.drawLine(x*w,y*h,xx*w,yy*h,p);}
  void path(int color,float... a){for(int i=2;i<a.length;i+=2)line(a[i-2],a[i-1],a[i],a[i+1],color,3);}
  void level(float y){p.setPathEffect(new DashPathEffect(new float[]{8,6},0));line(.06f,y,.94f,y,GOLD,2);p.setPathEffect(null);}
  void candle(float x,float open,float close,float hi,float lo){int color=close<open?GREEN:RED;line(x,hi,x,lo,color,2);p.setColor(color);p.setStyle(Paint.Style.FILL);float y=Math.min(open,close)*h,b=Math.max(open,close)*h;c.drawRect((x-.024f)*w,y,(x+.024f)*w,Math.max(y+2,b),p);}
  @Override protected void onDraw(Canvas canvas){c=canvas;w=getWidth();h=getHeight();c.drawColor(INK);for(int i=1;i<4;i++)line(.05f,i*.22f,.95f,i*.22f,0xff34312a,1);if(kind=='p')price();else if(kind=='c')candles();else if(kind=='r')rejection();else strategy();}
  void price(){int color=v<=4||v==11?RED:GREEN;
   switch(v){
    case 0:level(.62f);path(color,.06f,.76f,.18f,.35f,.28f,.61f,.41f,.15f,.52f,.61f,.65f,.34f,.77f,.64f,.93f,.84f);break;
    case 1:level(.62f);path(color,.06f,.76f,.22f,.26f,.36f,.61f,.53f,.27f,.68f,.60f,.93f,.83f);break;
    case 2:level(.67f);path(color,.06f,.76f,.19f,.29f,.3f,.66f,.44f,.3f,.54f,.65f,.67f,.3f,.77f,.65f,.93f,.83f);break;
    case 3:level(.69f);path(color,.06f,.25f,.2f,.68f,.32f,.33f,.44f,.69f,.56f,.43f,.7f,.68f,.82f,.54f,.94f,.83f);break;
    case 4:line(.09f,.49f,.83f,.16f,GOLD,2);line(.09f,.78f,.83f,.54f,GOLD,2);path(color,.09f,.77f,.2f,.46f,.31f,.69f,.43f,.38f,.55f,.62f,.68f,.34f,.82f,.55f,.94f,.83f);break;
    case 5:level(.39f);path(color,.06f,.21f,.19f,.72f,.29f,.39f,.42f,.85f,.53f,.39f,.65f,.69f,.76f,.39f,.93f,.16f);break;
    case 6:level(.38f);path(color,.06f,.2f,.22f,.75f,.37f,.38f,.54f,.74f,.68f,.39f,.93f,.15f);break;
    case 7:level(.35f);path(color,.06f,.2f,.18f,.73f,.31f,.36f,.44f,.73f,.56f,.34f,.69f,.72f,.79f,.35f,.94f,.15f);break;
    case 8:level(.3f);path(color,.06f,.77f,.19f,.3f,.31f,.68f,.44f,.3f,.56f,.57f,.69f,.3f,.81f,.46f,.94f,.14f);break;
    case 9:line(.09f,.18f,.84f,.52f,GOLD,2);line(.09f,.44f,.84f,.72f,GOLD,2);path(color,.09f,.18f,.2f,.45f,.31f,.26f,.43f,.55f,.55f,.39f,.68f,.63f,.8f,.52f,.94f,.15f);break;
    case 10:level(.5f);path(RED,.08f,.76f,.21f,.25f,.34f,.51f,.48f,.27f,.62f,.76f);path(GREEN,.62f,.23f,.75f,.75f,.86f,.5f,.95f,.18f);break;
    case 11:level(.46f);path(RED,.07f,.76f,.19f,.27f,.31f,.37f,.43f,.32f,.55f,.54f,.68f,.7f,.8f,.65f,.94f,.81f);break;
    case 12:path(GREEN,.07f,.79f,.2f,.24f,.32f,.37f,.44f,.27f,.55f,.5f,.67f,.39f,.8f,.56f,.94f,.16f);break;
    case 13:level(.37f);path(GREEN,.07f,.78f,.2f,.37f,.32f,.66f,.44f,.37f,.56f,.6f,.7f,.37f,.82f,.54f,.94f,.16f);break;
    default:line(.09f,.24f,.86f,.43f,GOLD,2);line(.09f,.76f,.86f,.55f,GOLD,2);path(GREEN,.09f,.76f,.21f,.25f,.33f,.67f,.45f,.33f,.57f,.61f,.69f,.42f,.82f,.55f,.94f,.17f);
   }
  }
  void candles(){switch(v){
   case 0:candle(.35f,.49f,.50f,.17f,.81f);candle(.66f,.47f,.48f,.22f,.73f);break;
   case 1:candle(.34f,.68f,.58f,.53f,.92f);candle(.66f,.65f,.54f,.49f,.92f);break;
   case 2:candle(.34f,.47f,.55f,.09f,.63f);candle(.66f,.46f,.56f,.15f,.64f);break;
   case 3:candle(.34f,.43f,.68f,.36f,.75f);candle(.66f,.78f,.27f,.21f,.85f);break;
   case 4:candle(.34f,.67f,.4f,.32f,.74f);candle(.66f,.29f,.8f,.23f,.86f);break;
   case 5:candle(.34f,.68f,.74f,.17f,.78f);candle(.66f,.7f,.66f,.22f,.77f);break;
   case 6:candle(.34f,.5f,.55f,.2f,.81f);candle(.66f,.56f,.52f,.23f,.82f);break;
   case 7:candle(.34f,.83f,.2f,.18f,.85f);candle(.66f,.74f,.28f,.26f,.76f);break;
   case 8:candle(.34f,.2f,.83f,.18f,.85f);candle(.66f,.27f,.77f,.25f,.79f);break;
   case 9:level(.5f);candle(.3f,.69f,.34f,.17f,.85f);candle(.69f,.32f,.65f,.13f,.84f);break;
   case 10:candle(.26f,.72f,.31f,.2f,.79f);candle(.5f,.3f,.73f,.23f,.81f);candle(.74f,.67f,.21f,.17f,.76f);break;
   case 11:candle(.25f,.32f,.71f,.27f,.77f);candle(.5f,.67f,.69f,.59f,.82f);candle(.75f,.71f,.25f,.2f,.77f);break;
   case 12:candle(.25f,.74f,.28f,.2f,.79f);candle(.5f,.31f,.33f,.21f,.43f);candle(.75f,.29f,.79f,.23f,.86f);break;
   case 13:candle(.33f,.32f,.78f,.26f,.85f);candle(.67f,.83f,.46f,.4f,.89f);break;
   case 14:candle(.33f,.79f,.28f,.22f,.86f);candle(.67f,.23f,.58f,.19f,.65f);break;
   default:candle(.2f,.83f,.6f,.55f,.89f);candle(.5f,.69f,.4f,.36f,.75f);candle(.8f,.51f,.18f,.14f,.56f);
  }}
  void rejection(){level(v==1?.4f:.61f);if(v==1){candle(.2f,.72f,.47f,.37f,.78f);candle(.47f,.45f,.55f,.18f,.63f);candle(.74f,.48f,.78f,.35f,.86f);}else{candle(.2f,.3f,.56f,.22f,.63f);candle(.47f,.59f,.48f,.4f,.86f);candle(.74f,.54f,.29f,.24f,.65f);}if(v==3)path(GREEN,.12f,.78f,.32f,.6f,.47f,.31f,.62f,.57f,.77f,.35f,.94f,.16f);}
  void strategy(){level(.55f);path(GREEN,.08f,.79f,.24f,.58f,.37f,.68f,.52f,.46f,.65f,.55f,.85f,.2f);if(v==2){line(.55f,.7f,.92f,.7f,RED,2);line(.55f,.29f,.92f,.29f,GREEN,2);}}
 }
}
