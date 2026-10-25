package ro.gs777.trading;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.view.inputmethod.InputMethodManager;
import java.net.URLEncoder;
import java.util.List;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.RelativeSizeSpan;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public class OverlayService extends Service {
 static volatile boolean running=false;
 static final String STATE_ACTION="ro.gs777.trading.OVERLAY_STATE";
 void notifyState(){sendBroadcast(new Intent(STATE_ACTION).setPackage(getPackageName()));}
 WindowManager window; WindowManager.LayoutParams params;
 LinearLayout shell,body,opinionBox,outputCard;ScrollView panelContent,resultScroll;TextView result,subtitle,headerTitle;Button expandButton,closeButton,longButton,shortButton;EditText coin,tp,sl,lev,aiQuestion;
 volatile boolean analysisBusy=false;int analysisGeneration=0;boolean collapsed=true,active=false,planDetails=false;String detailedPlan="",compactPlan="";int opinionRequest=0;CharSequence savedResult="";Handler handler=new Handler(Looper.getMainLooper());
 SharedPreferences prefs;
 final int bg=Color.argb(172,12,11,9),gold=Color.rgb(241,193,80),muted=Color.rgb(232,220,192),card=Color.argb(135,32,26,17);
 int px(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
 GradientDrawable surface(int color){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
  color==gold?new int[]{Color.rgb(255,223,140),gold,Color.rgb(171,113,37)}:
  color==bg?new int[]{Color.argb(192,18,16,12),Color.argb(179,31,23,13)}:
  new int[]{Color.argb(180,38,31,20),Color.argb(171,19,17,13)});
  d.setCornerRadius(px(14));d.setStroke(px(1),color==gold?Color.rgb(255,225,152):Color.rgb(187,143,62));return d;}
 TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setPadding(px(6),px(5),px(6),px(5));return t;}
 EditText edit(String value,String hint,boolean number){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setSingleLine(true);e.setTextColor(Color.WHITE);e.setHintTextColor(muted);e.setTextSize(16);e.setSelectAllOnFocus(true);e.setPadding(px(5),px(2),px(5),px(2));e.setBackground(surface(card));e.setOnTouchListener((view,event)->{if(collapsed&&event.getAction()==MotionEvent.ACTION_UP){collapse();enableInputMode();e.postDelayed(()->e.requestFocus(),120);return true;}if(!collapsed&&event.getAction()==MotionEvent.ACTION_DOWN)enableInputMode();return false;});e.setOnFocusChangeListener((view,focused)->{if(focused&&active&&!collapsed){enableInputMode();InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);e.postDelayed(()->im.showSoftInput(e,InputMethodManager.SHOW_IMPLICIT),150);}else if(!focused&&active)handler.postDelayed(()->{if(!anyFieldFocused())releaseInputMode();},180);});if(number)e.setInputType(8194);return e;}
 Button button(String label,Runnable callback){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(px(2),px(1),px(2),px(1));b.setTextColor(bg);b.setBackground(surface(gold));b.setOnClickListener(v->{releaseInputMode();callback.run();});return b;}
 Button quietButton(String label,Runnable callback){Button b=button(label,callback);b.setTextColor(gold);b.setBackground(surface(card));return b;}
 boolean anyFieldFocused(){return coin!=null&&coin.hasFocus()||tp!=null&&tp.hasFocus()||sl!=null&&sl.hasFocus()||lev!=null&&lev.hasFocus()||aiQuestion!=null&&aiQuestion.hasFocus();}
 void enableInputMode(){if(!active||params==null)return;if((params.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0){params.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;window.updateViewLayout(shell,params);}}
 void releaseInputMode(){if(!active||params==null||collapsed)return;if((params.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)==0){InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);im.hideSoftInputFromWindow(shell.getWindowToken(),0);shell.requestFocus();params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;window.updateViewLayout(shell,params);}} void showOpinion(String verdict,String detail){
  String content="CE ZICE PIATA: "+verdict+"\n"+detail;
  SpannableString styled=new SpannableString(content);
  int start="CE ZICE PIATA: ".length(),end=start+verdict.length();
  int color=verdict.equals("LONG")?Color.rgb(97,238,160):verdict.equals("SHORT")?Color.rgb(255,108,120):Color.rgb(255,213,91);
  styled.setSpan(new ForegroundColorSpan(color),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  styled.setSpan(new StyleSpan(Typeface.BOLD),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  styled.setSpan(new RelativeSizeSpan(1.1f),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  result.setText(styled);
 } void coloredConclusion(String shown,String verdict){
  SpannableString styled=new SpannableString(shown);
  String marker="CONCLUZIE: "+verdict;
  int index=shown.indexOf(marker);
  if(index>=0){
   int start=index+"CONCLUZIE: ".length(),end=start+verdict.length();
   int color=verdict.equals("LONG")?Color.rgb(97,238,160):verdict.equals("SHORT")?Color.rgb(255,108,120):Color.rgb(255,213,91);
   styled.setSpan(new ForegroundColorSpan(color),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   styled.setSpan(new StyleSpan(Typeface.BOLD),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   styled.setSpan(new RelativeSizeSpan(1.1f),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  }
  result.setText(styled);
 } double value(EditText e,double fallback){try{double v=Double.parseDouble(e.getText().toString().trim().replace(",","."));return Double.isFinite(v)&&v>0?v:fallback;}catch(Exception ex){return fallback;}}
 String selected(){String s=coin.getText().toString().trim().toUpperCase(Locale.ROOT).replace("/","");return s.matches("[A-Z0-9]{2,22}USDT")?s:"";}
 @Override public void onCreate(){
  super.onCreate();prefs=getSharedPreferences("gs777",MODE_PRIVATE);window=(WindowManager)getSystemService(WINDOW_SERVICE);
  NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26&&n!=null)n.createNotificationChannel(new NotificationChannel("overlay","Panou GS777 peste Bybit",NotificationManager.IMPORTANCE_LOW));
  Intent open=new Intent(this,MainActivity.class);
  PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder nb=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"overlay"):new Notification.Builder(this);
  nb.setContentTitle("GS777: panou mobil pornit").setContentText("Deschide Bybit sau opreste panoul din GS777")
    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pending).setOngoing(true);
  try{
   startForeground(777,nb.build());
   if(!android.provider.Settings.canDrawOverlays(this))throw new SecurityException("Permisiunea ferestrelor flotante lipseste");
   show();
   prefs.edit().putString("overlayStatus","Panoul este vizibil. Deschide Bybit.").apply();
  }catch(Throwable ex){
   prefs.edit().putString("overlayStatus","Eroare: "+ex.getClass().getSimpleName()+" "+ex.getMessage()).apply();
   stopSelf();
  }
 }
 void show(){
  int width=Math.min(px(225),getResources().getDisplayMetrics().widthPixels-px(24));
  params=new WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,
   Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
  params.gravity=Gravity.TOP|Gravity.LEFT;params.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-width,prefs.getInt("overlayXcompact",getResources().getDisplayMetrics().widthPixels-width-px(12))));params.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-px(70),prefs.getInt("overlayYcompact",px(110))));
  shell=new LinearLayout(this){@Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_OUTSIDE)releaseInputMode();return super.dispatchTouchEvent(event);}};shell.setOrientation(1);shell.setFocusableInTouchMode(true);shell.setPadding(px(10),px(3),px(10),px(8));shell.setBackground(surface(bg));
  LinearLayout header=new LinearLayout(this);header.setOrientation(0);header.setGravity(Gravity.CENTER_VERTICAL);
  headerTitle=text("Trading Help\ngabrielserbanescu777.com",10,gold);headerTitle.setTypeface(null,Typeface.BOLD);headerTitle.setPadding(px(5),px(1),px(1),px(1));
  header.addView(headerTitle,new LinearLayout.LayoutParams(0,px(44),1));
  expandButton=quietButton("+",()->collapse());header.addView(expandButton,new LinearLayout.LayoutParams(px(44),px(44)));
  closeButton=quietButton("X",()->stopSelf());header.addView(closeButton,new LinearLayout.LayoutParams(px(44),px(44)));
  closeButton.setVisibility(View.GONE);shell.addView(header);
  headerTitle.setOnTouchListener(new View.OnTouchListener(){
   float downX,downY;int originX,originY;
   public boolean onTouch(View view,MotionEvent ev){
    if(ev.getAction()==MotionEvent.ACTION_DOWN){downX=ev.getRawX();downY=ev.getRawY();originX=params.x;originY=params.y;return true;}
    if(ev.getAction()==MotionEvent.ACTION_MOVE){
     params.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-params.width,originX+Math.round(ev.getRawX()-downX)));
     params.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-(collapsed?px(120):px(410)),originY+Math.round(ev.getRawY()-downY)));
     window.updateViewLayout(shell,params);return true;
    }
    if(ev.getAction()==MotionEvent.ACTION_UP){
     prefs.edit().putInt("overlayXcompact",params.x).putInt("overlayYcompact",params.y).apply();
     if(Math.abs(ev.getRawX()-downX)<px(8)&&Math.abs(ev.getRawY()-downY)<px(8))collapse();
     return true;
    }
    return false;
   }
  });
  panelContent=new ScrollView(this);body=new LinearLayout(this);body.setPadding(px(3),px(4),px(3),px(6));body.setOrientation(1);panelContent.addView(body);
  shell.addView(panelContent,new LinearLayout.LayoutParams(-1,-2));
  panelContent.setFillViewport(false);
  LinearLayout pinnedFields=new LinearLayout(this);pinnedFields.setOrientation(1);
  TextView labels=text("COIN              TP %        SL %       X",11,muted);labels.setPadding(px(5),0,px(5),0);pinnedFields.addView(labels);
  LinearLayout values=new LinearLayout(this);values.setOrientation(0);
  coin=edit(prefs.getString("symbol","BTCUSDT"),"XRPUSDT",false);coin.setTextSize(14);
  tp=edit(Float.toString(prefs.getFloat("tp",33)),"TP",true);tp.setTextSize(14);
  sl=edit(Float.toString(prefs.getFloat("sl",7)),"SL",true);sl.setTextSize(14);
  lev=edit(Float.toString(prefs.getFloat("lev",2)),"x",true);lev.setTextSize(14);
  LinearLayout.LayoutParams coinWidth=new LinearLayout.LayoutParams(0,px(38),2.1f);coinWidth.rightMargin=px(3);values.addView(coin,coinWidth);
  LinearLayout.LayoutParams tpWidth=new LinearLayout.LayoutParams(0,px(29),1);tpWidth.rightMargin=px(3);values.addView(tp,tpWidth);
  LinearLayout.LayoutParams slWidth=new LinearLayout.LayoutParams(0,px(29),1);slWidth.rightMargin=px(3);values.addView(sl,slWidth);
  values.addView(lev,new LinearLayout.LayoutParams(0,px(38),.85f));pinnedFields.addView(values);shell.addView(pinnedFields,1);
  subtitle=text("BYBIT • ANALIZA 1H / 1D",9,muted);subtitle.setPadding(px(5),0,px(5),0);body.addView(subtitle);
  LinearLayout controls=new LinearLayout(this);controls.setOrientation(1);controls.setPadding(px(4),px(5),px(4),px(12));controls.setBackground(surface(card));
  LinearLayout topRow=new LinearLayout(this);topRow.setOrientation(0);
  Button btc=quietButton("BTC",()->{coin.setText("BTCUSDT");prefs.edit().putString("symbol","BTCUSDT").apply();analyze("");});btc.setTextSize(11);
  Button fetch=quietButton("FETCH COIN",()->fetchCoin());fetch.setTextSize(11);
  Button analyzeButton=button("ANALIZEAZA",()->analyze(""));analyzeButton.setTextSize(11);
  LinearLayout.LayoutParams btcWidth=new LinearLayout.LayoutParams(px(48),px(48));btcWidth.rightMargin=px(3);topRow.addView(btc,btcWidth);
  LinearLayout.LayoutParams fetchWidth=new LinearLayout.LayoutParams(0,px(48),1.2f);fetchWidth.rightMargin=px(3);topRow.addView(fetch,fetchWidth);
  topRow.addView(analyzeButton,new LinearLayout.LayoutParams(0,px(48),1.5f));controls.addView(topRow);
  LinearLayout bottomRow=new LinearLayout(this);bottomRow.setOrientation(0);
  longButton=quietButton("LONG\n--%",()->analyze("LONG"));longButton.setTextSize(12);longButton.setTextColor(Color.rgb(97,238,160));longButton.setTypeface(null,Typeface.BOLD);longButton.setSingleLine(false);longButton.setMaxLines(2);
  shortButton=quietButton("SHORT\n--%",()->analyze("SHORT"));shortButton.setTextSize(12);shortButton.setTextColor(Color.rgb(255,108,120));shortButton.setTypeface(null,Typeface.BOLD);shortButton.setSingleLine(false);shortButton.setMaxLines(2);
  Button news=quietButton("CE ZICE PIATA",()->opinions());news.setTextSize(9);news.setSingleLine(false);news.setMaxLines(2);
  Button save=quietButton("SALVEAZA",()->save());save.setTextSize(10);
  LinearLayout.LayoutParams longWidth=new LinearLayout.LayoutParams(0,px(55),1.12f);longWidth.rightMargin=px(3);bottomRow.addView(longButton,longWidth);
  LinearLayout.LayoutParams shortWidth=new LinearLayout.LayoutParams(0,px(55),1.12f);shortWidth.rightMargin=px(3);bottomRow.addView(shortButton,shortWidth);
  LinearLayout.LayoutParams newsWidth=new LinearLayout.LayoutParams(0,px(55),1.32f);newsWidth.rightMargin=px(3);bottomRow.addView(news,newsWidth);
  bottomRow.addView(save,new LinearLayout.LayoutParams(0,px(55),1.12f));controls.addView(bottomRow);  LinearLayout.LayoutParams controlParams=new LinearLayout.LayoutParams(-1,-2);controlParams.topMargin=px(6);body.addView(controls,controlParams);
  LinearLayout askRow=new LinearLayout(this);askRow.setOrientation(0);
  aiQuestion=edit("","Intreaba AI: LONG sau SHORT?",false);aiQuestion.setTextSize(12);
  LinearLayout.LayoutParams askWidth=new LinearLayout.LayoutParams(0,px(38),1);askWidth.rightMargin=px(3);
  askRow.addView(aiQuestion,askWidth);
  Button ask=quietButton("INTREABA AI",()->askAi());ask.setTextSize(10);
  askRow.addView(ask,new LinearLayout.LayoutParams(px(90),px(38)));
  LinearLayout.LayoutParams askParams=new LinearLayout.LayoutParams(-1,-2);askParams.topMargin=px(5);
  body.addView(askRow,askParams);  coin.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){longButton.setText("LONG\n--%");shortButton.setText("SHORT\n--%");}
   public void afterTextChanged(Editable e){}
  });
  outputCard=new LinearLayout(this);outputCard.setOrientation(1);outputCard.setPadding(px(5),px(5),px(5),px(5));
  outputCard.setBackground(surface(card));
  result=text("REZULTAT / Apasa ANALIZEAZA.",15,Color.WHITE);result.setMinHeight(px(55));outputCard.addView(result);
  result.setOnClickListener(v->{if(compactPlan.isEmpty()||opinionBox.getVisibility()==View.VISIBLE)return;planDetails=!planDetails;result.setTextSize(planDetails?14:13);coloredConclusion(planDetails?detailedPlan:compactPlan,planDetails?detailedPlan.split("\\n")[0].replace("CONCLUZIE: ",""):compactPlan.split("\\n")[0].replace("CONCLUZIE: ",""));resultScroll.post(()->resultScroll.scrollTo(0,0));});
  opinionBox=new LinearLayout(this);opinionBox.setOrientation(1);opinionBox.setVisibility(View.GONE);outputCard.addView(opinionBox);
  resultScroll=new ScrollView(this);resultScroll.setFillViewport(false);resultScroll.addView(outputCard);
  LinearLayout.LayoutParams outputParams=new LinearLayout.LayoutParams(-1,Math.min(px(143),getResources().getDisplayMetrics().heightPixels*21/100));outputParams.topMargin=px(7);shell.addView(resultScroll,outputParams);
  panelContent.setVisibility(View.GONE);resultScroll.setVisibility(View.GONE);
  params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
  try{window.addView(shell,params);active=true;running=true;notifyState();}catch(Exception ex){throw new IllegalStateException("Fereastra a fost blocata: "+ex.getMessage(),ex);}
 }
 void collapse(){
  collapsed=!collapsed;panelContent.setVisibility(collapsed?View.GONE:View.VISIBLE);resultScroll.setVisibility(collapsed?View.GONE:View.VISIBLE);
  params.width=collapsed?px(225):Math.min(px(275),getResources().getDisplayMetrics().widthPixels-px(24));
  headerTitle.setText("Trading Help\ngabrielserbanescu777.com");closeButton.setVisibility(collapsed?View.GONE:View.VISIBLE);expandButton.setText(collapsed?"+":"-");
  params.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-params.width,params.x));
  params.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-(collapsed?px(120):px(410)),params.y));
  params.flags=collapsed?(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN):
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
  if(collapsed){InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);im.hideSoftInputFromWindow(shell.getWindowToken(),0);shell.requestFocus();}
  if(active)window.updateViewLayout(shell,params);
 }
 void save(){
  double t=value(tp,33),s=value(sl,7),l=value(lev,2);
  if(t>1000||s>=100||l>100){result.setText("Valorile TP / SL / levier nu sunt valide.");return;}
  prefs.edit().putFloat("tp",(float)t).putFloat("sl",(float)s).putFloat("lev",(float)l).apply();
  result.setText(String.format(Locale.US,"Salvat: TP %.1f%% · SL %.1f%% · %.1fx",t,s,l));
 }
 void analyze(String requested){
  String symbol=selected();
  if(symbol.isEmpty()){result.setText("Scrie un simbol valid, de ex. SOLUSDT.");return;}
  if(analysisBusy){result.setText("Analiza precedenta este in curs...");return;}
  double take=value(tp,33),stop=value(sl,7),leverage=value(lev,2);
  if(stop>=100||leverage>100||take>1000){result.setText("Verifica TP, SL si levierul.");return;}
  prefs.edit().putString("symbol",symbol).apply();
  analysisBusy=true;int generation=++analysisGeneration;compactPlan="";detailedPlan="";planDetails=false;result.setTextSize(15);
  longButton.setText("LONG\n--%");shortButton.setText("SHORT\n--%");
  result.setText("Se citeste pretul Bybit pentru "+symbol+"...");
  new Thread(()->{
   Market.Coin quote=null;
   try{
    quote=Market.ticker(symbol);
    double p=quote.price;
    String levels=requested.equals("LONG")?
      "LONG TP "+Market.number(p*(1+take/100/leverage))+" / SL "+Market.number(p*(1-stop/100/leverage)):
      requested.equals("SHORT")?
      "SHORT TP "+Market.number(p*(1-take/100/leverage))+" / SL "+Market.number(p*(1+stop/100/leverage)):
      "TP/SL calculat dupa alegerea LONG sau SHORT.";
    String first=String.format(Locale.US,"%s: %s USDT (%+.2f%%/24h)\n%s",symbol,Market.number(p),quote.change,levels);
    handler.post(()->{if(active){result.setText(first+"\nSe verifica RSI si media pretului...");resultScroll.post(()->resultScroll.scrollTo(0,0));}});
    try{
     Market.Analysis a=Market.analyze(symbol,quote);
     String verdict=requested.isEmpty()?a.direction:
      requested+" - "+(requested.equals("LONG")?(a.score>=3?"indicatori favorabili":a.score<=-3?"indicatori contrarii":"indicatori neclari"):
      (a.score<=-3?"indicatori favorabili":a.score>=3?"indicatori contrarii":"indicatori neclari"));
     String conclusion=a.score>=3?"LONG":a.score<=-3?"SHORT":"ASTEAPTA";
     TradeJournal.Rate longHistory=TradeJournal.rate(this,symbol,"LONG"),shortHistory=TradeJournal.rate(this,symbol,"SHORT");
     String estimates="Scor tehnic 1h + 1D: "+String.format(Locale.US,"%+d/5",a.score)
       +" (LONG) / "+String.format(Locale.US,"%+d/5",-a.score)+" (SHORT). Procentele din butoane vin din rezultate istorice TP/SL, nu garanteaza tranzactia urmatoare.";
     if(longHistory!=null)estimates+="\nDemo LONG: "+longHistory.percent+"% din "+longHistory.samples+" cazuri rezolvate.";
     if(shortHistory!=null)estimates+="\nDemo SHORT: "+shortHistory.percent+"% din "+shortHistory.samples+" cazuri rezolvate.";
     String seenAt=new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new java.util.Date());
     String shown="CONCLUZIE: "+conclusion+"\n"+estimates+"\nDate Bybit "+seenAt+"\n"+first+"\n"+verdict+"\n"+a.reason;
     handler.post(()->{if(active&&generation==analysisGeneration&&symbol.equals(selected())){
      subtitle.setText(symbol+" / Bybit "+seenAt);
      longButton.setText("LONG\n--%");
      shortButton.setText("SHORT\n--%");
      coloredConclusion(shown,conclusion);resultScroll.post(()->resultScroll.scrollTo(0,0));
     }});     try{
      double capital=prefs.getFloat("capital",100),risk=prefs.getFloat("risk",1);
      MarketExtras.Plan plan=MarketExtras.inspect(this,symbol,a,capital,risk,take,stop,leverage);
      String full="CONCLUZIE: "+plan.verdict+"\n"+estimates+"\nDate Bybit "+seenAt+"\n"+MarketExtras.summary(plan)+"\n"+a.reason;
      String brief="CONCLUZIE: "+plan.verdict+"\nLONG --% | SHORT --% (istoric in calcul)\n"+MarketExtras.compact(plan);
      handler.post(()->{if(active&&symbol.equals(selected())){detailedPlan=full;compactPlan=brief;planDetails=false;result.setTextSize(13);coloredConclusion(brief,plan.verdict);resultScroll.post(()->resultScroll.scrollTo(0,0));}});
      new Thread(()->{try{
       double fee=.0012;try{if(ReadOnlyBybit.connected(this))fee=ReadOnlyBybit.roundTripTakerFee(this,symbol);}catch(Exception ignored){}
       Backtest.Result historical=Backtest.run(symbol,take,stop,leverage,fee);
       String evidence=historical.oddsSummary()+"\n"+historical.summary();
       handler.post(()->{if(active&&generation==analysisGeneration&&symbol.equals(selected())&&!detailedPlan.isEmpty()){
        longButton.setText("LONG\n"+historical.percent(true));shortButton.setText("SHORT\n"+historical.percent(false));
        compactPlan=compactPlan.replace("LONG --% | SHORT --% (istoric in calcul)","LONG "+historical.percent(true)+" ("+historical.count(true)+") | SHORT "+historical.percent(false)+" ("+historical.count(false)+")");
        detailedPlan+="\n\n"+evidence;
        if(!planDetails&&opinionBox.getVisibility()!=View.VISIBLE)coloredConclusion(compactPlan,plan.verdict);
        if(planDetails&&opinionBox.getVisibility()!=View.VISIBLE)coloredConclusion(detailedPlan,plan.verdict);
       }});
      }catch(Exception ex){handler.post(()->{if(active&&generation==analysisGeneration&&symbol.equals(selected())){compactPlan=compactPlan.replace(" (istoric in calcul)"," (istoric indisponibil)");if(!planDetails&&opinionBox.getVisibility()!=View.VISIBLE)coloredConclusion(compactPlan,plan.verdict);}});} }).start();
     }catch(Exception ex){handler.post(()->{if(active&&symbol.equals(selected()))result.append("\nContext suplimentar indisponibil: "+ex.getMessage());});}
    }catch(Exception problem){
     String message=first+"\nIndicatorii nu s-au incarcat: "+problem.getMessage()+". Verifica simbolul si conexiunea.";
     handler.post(()->{if(active)result.setText(message);});
    }
   }catch(Exception ex){
    String message="Date Bybit indisponibile pentru "+symbol+": "+ex.getMessage()+". Nu calculez niveluri fara pret verificat.";
    handler.post(()->{if(active)result.setText(message);});
   }finally{analysisBusy=false;}
  }).start();
 }
 void askAi(){
  String symbol=selected(),question=aiQuestion.getText().toString().trim();
  if(symbol.isEmpty()){result.setText("Introdu un coin USDT valid.");return;}
  if(question.isEmpty()){result.setText("Scrie intrebarea ta despre LONG sau SHORT.");return;}
  double take=value(tp,33),stop=value(sl,7),leverage=value(lev,2);
  int request=++analysisGeneration;
  prefs.edit().putString("symbol",symbol).apply();
  compactPlan="";detailedPlan="";opinionBox.setVisibility(View.GONE);
  result.setText("INTREABA AI / Se analizeaza "+symbol+"...");
  new Thread(()->{
   String response=AskAi.answer(this,symbol,question,take,stop,leverage,prefs.getFloat("capital",100),prefs.getFloat("risk",1));
   handler.post(()->{if(active&&request==analysisGeneration){
    SpannableString colored=new SpannableString(response);
    String marker="CONCLUZIE: ";int at=response.indexOf(marker);
    if(at>=0){int from=at+marker.length();int to=response.indexOf('\n',from);if(to<0)to=response.length();
     String side=response.substring(from,to);
     colored.setSpan(new ForegroundColorSpan(side.equals("LONG")?Color.rgb(97,238,160):side.equals("SHORT")?Color.rgb(255,108,120):Color.rgb(255,213,91)),from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    result.setText(colored);resultScroll.post(()->resultScroll.scrollTo(0,0));
   }});
  }).start();
 } void fetchCoin(){
  if(BybitSymbolService.instance==null){
   result.setText("Activeaza GS777 Trading in Setari > Accesibilitate. Revino in Bybit si apasa FETCH COIN.");
   try{Intent settings=new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(settings);}catch(Exception ignored){}
   return;
  }
  String found=BybitSymbolService.fetch();
  if(found==null){result.setText("Nu am gasit perechea USDT pe ecranul Bybit. Deschide graficul monedei si incearca din nou.");return;}
  coin.setText(found);prefs.edit().putString("symbol",found).apply();analyze("");
 } void opinions(){
  if(opinionBox.getVisibility()==View.VISIBLE){opinionRequest++;opinionBox.setVisibility(View.GONE);result.setTextSize(compactPlan.isEmpty()?15:planDetails?14:13);result.setText(savedResult);return;}
  String symbol=selected();
  if(symbol.isEmpty()){result.setText("Scrie un simbol valid, de ex. SOLUSDT.");return;}
  int requestId=++opinionRequest;
  savedResult=result.getText();result.setTextSize(13);
  String remembered=prefs.getString("mood_"+symbol,"");
  showOpinion("ASTEAPTA",symbol+" / "+(remembered.isEmpty()?"Se citesc titlurile recente...":"Ultima analiza salvata (se revalideaza):\n"+remembered));
  opinionBox.removeAllViews();opinionBox.setVisibility(View.VISIBLE);
  TextView status=text("Se cauta stiri si discutii recente...",13,muted);opinionBox.addView(status);
  String coinName=symbol.substring(0,symbol.length()-4);
  String q;try{q=URLEncoder.encode(coinName+" crypto","UTF-8");}catch(Exception e){q=coinName;}
  final String encoded=q;
  opinionBox.addView(quietButton("YouTube / opinii",()->openLink("https://www.youtube.com/results?search_query="+encoded)));
  opinionBox.addView(quietButton("Reddit / discutii",()->openLink("https://www.reddit.com/search/?q="+encoded)));
  opinionBox.addView(quietButton("Discord / cautare manuala",()->openLink("https://discord.com/discovery?query="+encoded)));
  resultScroll.post(()->resultScroll.scrollTo(0,0));
  new Thread(()->{
   java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(4);
   java.util.concurrent.Future<List<SocialFeed.Item>> newsTask=pool.submit(()->SocialFeed.news(coinName));
   java.util.concurrent.Future<List<SocialFeed.Item>> forumTask=pool.submit(()->SocialFeed.forum(coinName));
   java.util.concurrent.Future<List<SocialFeed.Item>> globalTask=pool.submit(()->SocialFeed.forumGeneral(coinName));
   java.util.concurrent.Future<MarketMood.Snapshot> marketTask=pool.submit(()->MarketMood.get(symbol));
   java.util.ArrayList<SocialFeed.Item> items=new java.util.ArrayList<>();
   boolean newsOk=false,forumOk=false,globalOk=false;MarketMood.Snapshot market=null;
   try{items.addAll(newsTask.get(13,java.util.concurrent.TimeUnit.SECONDS));newsOk=true;}catch(Exception ignored){}
   try{items.addAll(forumTask.get(2,java.util.concurrent.TimeUnit.SECONDS));forumOk=true;}catch(Exception ignored){}
   try{items.addAll(globalTask.get(2,java.util.concurrent.TimeUnit.SECONDS));globalOk=true;}catch(Exception ignored){}
   try{market=marketTask.get(2,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ignored){}
   pool.shutdownNow();
   final boolean newsAvailable=newsOk,forumAvailable=forumOk,globalAvailable=globalOk;
   final MarketMood.Snapshot snapshot=market;
   handler.post(()->{
    if(!active||requestId!=opinionRequest||opinionBox.getVisibility()!=View.VISIBLE||!symbol.equals(selected()))return;
    SocialFeed.Opinion opinion=SocialFeed.opinion(items,coinName);
    String verdict=MarketMood.combine(opinion,snapshot);
    String counts=String.format(Locale.US,"TITLURI INTERNET: %s | %d relevante (%d Reddit, %d stiri); %d optimiste / %d pesimiste.",
     opinion.direction,opinion.relevant,opinion.forums,opinion.news,opinion.positive,opinion.negative);
    String trend=snapshot==null?"TREND BYBIT: indisponibil; verdictul ramane ASTEAPTA.":snapshot.text;
    String sourceStatus="Surse: stiri "+(newsAvailable?"OK":"indisponibile")+", Reddit crypto "+(forumAvailable?"OK":"indisponibil")+", Reddit general "+(globalAvailable?"OK":"indisponibil")+".";
    String mood=counts+"\n"+trend+"\n"+sourceStatus+"\nTitlurile nu reprezinta voturi verificate si nu prezic pretul.";
    showOpinion(verdict,mood);prefs.edit().putString("mood_"+symbol,mood).putLong("moodAt_"+symbol,System.currentTimeMillis()).apply();
    List<SocialFeed.Item> visible=SocialFeed.relevantItems(items,coinName);
    status.setText(visible.isEmpty()?"Niciun titlu recent relevant disponibil.":"Titluri relevante - apasa pentru sursa:");
    int index=1;
    for(SocialFeed.Item item:visible){
     TextView link=text(item.source+" | "+item.date+"\n"+item.title+"  >",14,gold);
     link.setMinHeight(px(52));link.setContentDescription("Deschide sursa: "+item.title);
     link.setOnClickListener(v->openLink(item.url));opinionBox.addView(link,index++);
    }
    resultScroll.post(()->resultScroll.scrollTo(0,0));
   });
  }).start();
 } void openLink(String url){
  try{Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);}
  catch(Exception e){if(result!=null)result.setText("Nu am putut deschide sursa.");}
 } @Override public void onDestroy(){
  active=false;running=false;notifyState();prefs.edit().putString("overlayStatus","Panou oprit.").apply();if(window!=null&&shell!=null)try{window.removeView(shell);}catch(Exception ignored){}
  super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}









