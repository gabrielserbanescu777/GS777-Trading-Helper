package ro.gs777.trading;
import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
 final int background=Color.rgb(9,9,8), panel=Color.rgb(25,22,17),gold=Color.rgb(241,193,80),muted=Color.rgb(196,187,169),green=Color.rgb(111,228,163),red=Color.rgb(249,121,134);
 SharedPreferences prefs;AutoCompleteTextView symbolInput,planSymbolInput;CoinDropdownAdapter marketAdapter,planAdapter;TextView coinCount,selectedDelta;ScrollView screenScroll;LinearLayout root,output,moverUp,moverDown,suggestions,tabPanel,contentHost,currentSection;HorizontalScrollView tabScroll;TextView pageTitle,planTitle,planDetails,topSummary,savedPlans,alertHealth;TextView[] bottomItems,topItems;java.util.ArrayList<LinearLayout> sectionBodies=new java.util.ArrayList<>();java.util.ArrayList<TextView> sectionTabs=new java.util.ArrayList<>();android.widget.HorizontalScrollView tickerUp,tickerDown;android.animation.ObjectAnimator upAnimation,downAnimation;int suggestionGeneration=0;volatile int planGeneration=0;long analysisAt=0;TextView status,heading,overlayMessage,chartStatus,chartAnswer;Button overlayToggle;Spinner aiProvider;Uri chosenChart;
 EditText tpInput,slInput,leverageInput,amountInput,capitalInput,riskInput,thresholdInput,watchInput,priceAlertInput,aiQuestion,chartSymbolInput,channelIdsInput;
 CheckBox alertPlan,alertRise,alertFall,alertTips,alertEvents,alertCreator,alertPrice,alertSetup,alertFunding,alertInterest,alertPositions,alertJournal;CheckBox[] creatorChoices;
 volatile boolean loading=false;
 int dashboardIndex=-1; TextView dashMarket,dashRisk,dashLeaders,dashJournal,dashFreshness,dashScores; DashboardChart dashChart; TextView dashPlanInfo,dashChartTitle,dashSetupInfo,dashConnection; volatile long lastLiveTickAt=0; volatile int dashboardRequest=0; BybitTickerStream liveStream; final android.os.Handler dashboardTimer=new android.os.Handler(android.os.Looper.getMainLooper()); boolean dashboardTimerActive=false; volatile boolean dashboardRefreshing=false; final Runnable dashboardPulse=new Runnable(){public void run(){if(!dashboardTimerActive)return;refreshDashboard();dashboardTimer.postDelayed(this,90000);}};
 Market.Analysis analysis;
 boolean overlayStarting=false;
 final BroadcastReceiver overlayEvents=new BroadcastReceiver(){
  @Override public void onReceive(Context context,Intent intent){
   overlayStarting=false;updateOverlayToggle();
  }
 }; String symbol="BTCUSDT";
 int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
  prefs=getSharedPreferences("gs777",MODE_PRIVATE);
  IntentFilter overlayFilter=new IntentFilter(OverlayService.STATE_ACTION);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(overlayEvents,overlayFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(overlayEvents,overlayFilter);
  symbol=prefs.getString("symbol","BTCUSDT");
  if(Build.VERSION.SDK_INT>=26){NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("movers","Mișcări Bybit ±33%",NotificationManager.IMPORTANCE_DEFAULT));}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
  AlertsJob.schedule(this);
  render();
  startLiveTicker();
  dashboardTimerActive=true;dashboardTimer.postDelayed(dashboardPulse,90000);
 }
 @Override protected void onResume(){
  super.onResume();
  if(upAnimation!=null&&upAnimation.isPaused())upAnimation.resume();if(downAnimation!=null&&downAnimation.isPaused())downAnimation.resume();
  if(prefs==null)return;
  String shared=prefs.getString("symbol",symbol);if(symbolInput!=null&&!symbol.equals(shared))choose(shared);
  updateAlertHealth();
  if(prefs.getBoolean("overlayPending",false)&&Settings.canDrawOverlays(this)){
   prefs.edit().putBoolean("overlayPending",false).apply();
   new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->startOverlay(),300);
  }
  updateOverlayToggle();
  if(analysisAt>0&&System.currentTimeMillis()-analysisAt>5*60*1000&&topSummary!=null)topSummary.setText(symbol+" / analiza veche (>5 min), actualizeaza");
 }
 @Override protected void onPause(){if(upAnimation!=null&&upAnimation.isRunning())upAnimation.pause();if(downAnimation!=null&&downAnimation.isRunning())downAnimation.pause();super.onPause();}
 void updateOverlayToggle(){
  if(overlayToggle!=null)overlayToggle.setText(OverlayService.running||overlayStarting?"Dezactiveaza Panou":"Activeaza Panou Peste Bybit");
 }
 void toggleOverlay(){
  if(OverlayService.running||overlayStarting){
   prefs.edit().putBoolean("overlayPending",false).putString("overlayStatus","Panou oprit.").apply();
   overlayStarting=false;overlayToggle.setText("Activeaza Panou Peste Bybit");
   stopService(new Intent(this,OverlayService.class));
   overlayToggle.postDelayed(this::updateOverlayToggle,300);
  }else startOverlay();
 } void startOverlay(){
  if(!Settings.canDrawOverlays(this)){
   prefs.edit().putBoolean("overlayPending",true).putString("overlayStatus","Astept permisiunea de afisare peste aplicatii.").apply();
   if(overlayMessage!=null)overlayMessage.setText("Activeaza Afisare peste alte aplicatii, apoi revino in GS777.");
   Intent permission=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
   try{startActivity(permission);}catch(Exception ex){notice("Deschide Setari > Aplicatii > Acces special > Afisare peste alte aplicatii > GS777 Trading.");}
   return;
  }
  prefs.edit().putString("overlayStatus","Pornire in curs...").apply();
  if(overlayMessage!=null)overlayMessage.setText("Pornire panou...");
  Intent service=new Intent(this,OverlayService.class);
  try{
   if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
   overlayStarting=true;updateOverlayToggle();
   new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
    String state=prefs.getString("overlayStatus","Serviciul nu a raspuns.");
    updateOverlayToggle();
    if(state.equals("Pornire in curs...")||state.startsWith("Eroare"))notice(state+" Verifica si permisiunea de ferestre flotante din setarile telefonului.");
   },1800);
  }catch(Exception ex){
   String error="Eroare la pornire: "+ex.getClass().getSimpleName()+" "+ex.getMessage();
   overlayStarting=false;updateOverlayToggle();
   prefs.edit().putString("overlayStatus",error).apply();if(overlayMessage!=null)overlayMessage.setText(error);
  }
 }
 GradientDrawable shape(int color){GradientDrawable d=color==gold?new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(255,220,131),gold,Color.rgb(152,104,34)}):
 new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(34,29,20),Color.rgb(15,15,13),Color.rgb(36,28,16)});
 d.setCornerRadius(dp(18));d.setStroke(dp(1),color==gold?Color.rgb(255,225,152):Color.rgb(170,132,66));return d;}
 LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setLineSpacing(dp(2),1.05f);return t;}
 void add(LinearLayout parent,View child){parent.addView(child);}
 void section(String title){
  TextView item=text(title,15,gold);
  sectionTabs.add(item);
  LinearLayout body=vertical();body.setVisibility(View.GONE);
  contentHost.addView(body,new LinearLayout.LayoutParams(-1,-2));
  sectionBodies.add(body);currentSection=body;
 }
 void showSection(int index,boolean ignored){
  if(index<0||index>=sectionBodies.size())return;
  for(LinearLayout body:sectionBodies)body.setVisibility(View.GONE);
  sectionBodies.get(index).setVisibility(View.VISIBLE);
  pageTitle.setText(sectionTabs.get(index).getText());
  int[] bottomTargets={dashboardIndex,2,1,3};
  for(int i=0;i<bottomItems.length;i++){
   boolean selected=index==bottomTargets[i];
   bottomItems[i].setTextColor(selected?background:gold);
   bottomItems[i].setBackground(shape(selected?gold:Color.rgb(43,37,26)));
  }
  int[] topTargets={4,5,6};
  for(int i=0;i<topItems.length;i++){
   boolean selected=i<3?index==topTargets[i]:!isQuickSection(index);
   topItems[i].setTextColor(selected?background:gold);
   topItems[i].setBackground(shape(selected?gold:Color.rgb(43,37,26)));
  }
  if(index==5&&savedPlans!=null)savedPlans.setText(PlanStore.display(this));
  if(index==8)updateAlertHealth();
  screenScroll.post(()->screenScroll.smoothScrollTo(0,0));
 }
 boolean isQuickSection(int index){
  return index==dashboardIndex||index==0||index==1||index==2||index==3||index==4||index==5||index==6;
 }
 void openSections(){
  LinearLayout panel=vertical();
  panel.setPadding(dp(10),dp(11),dp(10),dp(11));
  panel.setBackground(shape(Color.rgb(26,23,18)));
  TextView heading=text("UNELTE GS777",18,gold);heading.setTypeface(null,Typeface.BOLD);
  heading.setPadding(dp(8),dp(3),dp(8),dp(9));panel.addView(heading);
  ScrollView scroller=new ScrollView(this);
  scroller.setVerticalScrollBarEnabled(true);
  LinearLayout list=vertical();scroller.addView(list);
  int count=0;for(int i=0;i<sectionTabs.size();i++)if(!isQuickSection(i))count++;
  int maxHeight=Math.min(dp(55*count),getResources().getDisplayMetrics().heightPixels*55/100);
  panel.addView(scroller,new LinearLayout.LayoutParams(-1,maxHeight));
  int width=getResources().getDisplayMetrics().widthPixels-dp(20);
  final android.widget.PopupWindow popup=new android.widget.PopupWindow(panel,width,-2,true);
  popup.setBackgroundDrawable(shape(Color.rgb(26,23,18)));
  popup.setOutsideTouchable(true);popup.setElevation(dp(12));
  for(int i=0;i<sectionTabs.size();i++){
   if(isQuickSection(i))continue;
   final int target=i;
   boolean selected=sectionBodies.get(i).getVisibility()==View.VISIBLE;
   TextView row=text((selected?"* ":"")+sectionTabs.get(i).getText().toString(),16,selected?Color.WHITE:gold);
   row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setMinHeight(dp(51));
   row.setPadding(dp(12),dp(8),dp(8),dp(8));
   row.setBackground(shape(selected?Color.rgb(89,68,32):Color.rgb(43,37,26)));
   LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(5);
   list.addView(row,rp);
   row.setOnClickListener(v->{popup.dismiss();showSection(target,false);});
  }
  popup.showAsDropDown(topItems[3],0,dp(3),android.view.Gravity.RIGHT);
 }
 void navButton(LinearLayout bar,String name,Runnable action,TextView[] slots,int index){
  TextView item=text(name,12,gold);item.setTypeface(null,Typeface.BOLD);
  item.setGravity(android.view.Gravity.CENTER);
  item.setMinHeight(dp(51));item.setBackground(shape(Color.rgb(43,37,26)));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(51),1);
  if(index<3)lp.rightMargin=dp(4);
  bar.addView(item,lp);slots[index]=item;item.setOnClickListener(v->action.run());
 } LinearLayout card(){
  LinearLayout l=vertical();l.setPadding(dp(17),dp(16),dp(17),dp(17));
  l.setBackground(shape(panel));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
  p.bottomMargin=dp(15);currentSection.addView(l,p);return l;
 } Button button(String label,Runnable task){Button b=new Button(this);b.setText(label);b.setTextSize(15);b.setTextColor(background);b.setMinHeight(dp(43));b.setAllCaps(false);b.setBackground(shape(gold));b.setPadding(dp(12),dp(9),dp(12),dp(9));b.setOnClickListener(v->task.run());return b;}
 EditText field(String hint,String value,boolean decimal){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(Color.WHITE);e.setTextSize(18);e.setSingleLine(true);if(decimal)e.setInputType(8194);e.setSelectAllOnFocus(true);return e;}
 void line(LinearLayout l,String value){TextView t=text(value,16,Color.WHITE);t.setPadding(0,dp(5),0,dp(5));add(l,t);}
 CheckBox alertCheck(LinearLayout box,String label,String key,boolean fallback){
  CheckBox check=new CheckBox(this);check.setText(label);check.setTextSize(16);check.setTextColor(Color.WHITE);
  check.setButtonTintList(android.content.res.ColorStateList.valueOf(gold));check.setChecked(prefs.getBoolean(key,fallback));
  check.setPadding(dp(2),dp(7),dp(2),dp(7));add(box,check);return check;
 } void notice(String message){new AlertDialog.Builder(this).setTitle("GS777 Trading").setMessage(message).setPositiveButton("Închide",null).show();}
 void async(Job job){if(loading)return;loading=true;status.setText("Se încarcă datele Bybit…");new Thread(()->{
  try{job.run();}catch(Exception ex){runOnUiThread(()->status.setText("Eroare: "+ex.getMessage()+" · Verifică conexiunea și datele pe Bybit."));}finally{runOnUiThread(()->loading=false);}
 }).start();}
 interface Job{void run()throws Exception;}
 double positive(EditText e,double fallback){try{double v=Double.parseDouble(e.getText().toString().replace(",","."));return Double.isFinite(v)&&v>0?v:fallback;}catch(Exception ignored){return fallback;}}
 void saveSettings(){
  double tp=positive(tpInput,33),sl=positive(slInput,7),lev=positive(leverageInput,2);
  if(tp>1000||sl>=100||lev>100){notice("TP, SL sau levier în afara intervalului permis.");return;}
  prefs.edit().putFloat("tp",(float)tp).putFloat("sl",(float)sl).putFloat("lev",(float)lev).apply();
  notice("Valorile au fost salvate: TP "+tp+"%, SL "+sl+"%, levier "+lev+"×.");
  if(analysis!=null)showAnalysis(analysis);
 }
 void updateSelectedDelta(String name){
  if(selectedDelta==null||marketAdapter==null)return;
  Market.Coin coin=marketAdapter.find(name);
  if(coin==null){selectedDelta.setText("24h: se actualizeaza pentru "+name);selectedDelta.setTextColor(muted);return;}
  selectedDelta.setText(String.format(Locale.US,"%s / 24h %+.2f%% / pret %s USDT",
   name,coin.change,Market.number(coin.price)));
  selectedDelta.setTextColor(coin.change>=0?green:red);
  if(topSummary!=null)topSummary.setText(String.format(Locale.US,"%s  |  %+.2f%%/24h  |  %s USDT",name,coin.change,Market.number(coin.price)));
 } void choose(String name){
  if(name==null||!name.matches("[A-Z0-9]{2,22}USDT"))return;
  symbol=name;dashboardRequest++;prefs.edit().putString("symbol",name).apply();
  if(symbolInput!=null&&!name.equals(symbolInput.getText().toString()))symbolInput.setText(name,false);
  if(planSymbolInput!=null&&!name.equals(planSymbolInput.getText().toString()))planSymbolInput.setText(name,false);
  if(chartSymbolInput!=null&&!name.equals(chartSymbolInput.getText().toString()))chartSymbolInput.setText(name);
  updateSelectedDelta(name);if(liveStream!=null)liveStream.changeSymbol(name);analysis=null;if(dashChart!=null)dashChart.clearTradeLevels();if(dashPlanInfo!=null)dashPlanInfo.setText("Selecteaza planul activ pentru "+name+".");refreshDashboard();
  if(output!=null){output.removeAllViews();line(output,"Apasa Analizeaza pentru datele curente "+name+".");}
  if(topSummary!=null)topSummary.setText(name+" / asteapta actualizarea Bybit");
 } void render(){
  ScrollView scroll=new ScrollView(this);screenScroll=scroll;scroll.setFillViewport(true);
  scroll.setBackgroundColor(background);root=vertical();
  root.setPadding(dp(18),dp(18),dp(18),dp(24));scroll.addView(root);
  LinearLayout shell=vertical();shell.setBackgroundColor(background);
  LinearLayout topBar=vertical();topBar.setPadding(dp(8),dp(7),dp(8),dp(5));
  LinearLayout quickTop=new LinearLayout(this);quickTop.setOrientation(LinearLayout.HORIZONTAL);
  topItems=new TextView[4];
  navButton(quickTop,"TP / SL",()->showSection(4,false),topItems,0);
  navButton(quickTop,"PLAN 3 ZILE",()->showSection(5,false),topItems,1);
  navButton(quickTop,"AI",()->showSection(6,false),topItems,2);
  navButton(quickTop,"UNELTE v",this::openSections,topItems,3);
  TextView brand=text("TRADING HELP  |  gabrielserbanescu777.com",13,gold);brand.setTypeface(null,Typeface.BOLD);topBar.addView(brand);
  topSummary=text(symbol+" / se incarca datele Bybit",13,muted);topBar.addView(topSummary);
  topBar.addView(quickTop);shell.addView(topBar);
  shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout bottom=vertical();bottom.setPadding(dp(8),dp(4),dp(8),dp(8));
  pageTitle=text("Moneda",14,gold);pageTitle.setGravity(android.view.Gravity.CENTER);
  bottom.addView(pageTitle);
  LinearLayout quickBottom=new LinearLayout(this);quickBottom.setOrientation(LinearLayout.HORIZONTAL);
  bottomItems=new TextView[4];
  navButton(quickBottom,"ACASA",()->showSection(dashboardIndex,false),bottomItems,0);
  navButton(quickBottom,"PONTURI",()->showSection(2,false),bottomItems,1);
  navButton(quickBottom,"MISCARI",()->showSection(1,false),bottomItems,2);
  navButton(quickBottom,"GRAFICE",()->showSection(3,false),bottomItems,3);
  bottom.addView(quickBottom);shell.addView(bottom);setContentView(shell);
  line(root,"Analiză Bybit · Demo local · Alerte ±33%");status=text("Apasă Actualizează pentru prețuri în timp real.",14,muted);add(root,status);
  overlayToggle=button("Activeaza Panou Peste Bybit",this::toggleOverlay);
  LinearLayout.LayoutParams overlayPlacement=new LinearLayout.LayoutParams(-1,dp(49));
  overlayPlacement.setMargins(0,dp(12),0,dp(6));root.addView(overlayToggle,overlayPlacement);
  updateOverlayToggle();
  sectionBodies.clear();sectionTabs.clear();contentHost=vertical();root.addView(contentHost);
  section("Moneda");
  LinearLayout c=card();line(c,"Bybit USDT Perpetual / variația in ultimele 24h.");
  symbolInput=new AutoCompleteTextView(this);symbolInput.setSingleLine(true);
  symbolInput.setTextColor(Color.WHITE);symbolInput.setHintTextColor(muted);symbolInput.setTextSize(19);
  symbolInput.setHint("Cauta BTC, SOL, XRP...");symbolInput.setText(symbol);
  symbolInput.setThreshold(1);symbolInput.setDropDownHeight(Math.min(dp(420),getResources().getDisplayMetrics().heightPixels*60/100));
  marketAdapter=new CoinDropdownAdapter(this);symbolInput.setAdapter(marketAdapter);
  symbolInput.setOnItemClickListener((parent,view,position,id)->{
   Market.Coin picked=(Market.Coin)parent.getItemAtPosition(position);
   choose(picked.symbol);
  });
  LinearLayout selectRow=new LinearLayout(this);selectRow.setOrientation(0);
  selectRow.addView(symbolInput,new LinearLayout.LayoutParams(0,-2,1));
  Button chooseCoin=button("LISTA",()->{
   if(marketAdapter.totalCount()==0){notice("Lista coinurilor se incarca. Verifica internetul si apasa Actualizeaza mai jos.");return;}
   marketAdapter.getFilter().filter("",count->{symbolInput.requestFocus();symbolInput.showDropDown();});
  });
  chooseCoin.setTextSize(22);LinearLayout.LayoutParams arrow=new LinearLayout.LayoutParams(dp(51),dp(52));
  arrow.leftMargin=dp(6);selectRow.addView(chooseCoin,arrow);add(c,selectRow);
  coinCount=text("Se incarca toate perechile USDT Perpetual...",14,muted);add(c,coinCount);
  selectedDelta=text("24h: se incarca...",16,gold);add(c,selectedDelta);
  add(c,button("Analizeaza moneda",()->{
   String input=symbolInput.getText().toString().trim().toUpperCase(Locale.ROOT).replace("/","");
   if(!input.matches("[A-Z0-9]{2,22}USDT")){notice("Scrie un simbol USDT valid, de exemplu SOLUSDT.");return;}
   openCoin(input);
  }));
  output=vertical();add(c,output);line(output,"Indicatori 1h si 1D calculati dupa apasarea butonului.");

  LinearLayout intel=card();line(intel,"GS777 INTELLIGENCE | Radar si risc. Datele provin din API public Bybit.");
  add(intel,button("RADAR | miscari +33% / -33%",()->async(()->{
   String report=OpportunityEngine.scan(Market.allUsdtPerpetual());runOnUiThread(()->notice(report));
  })));
  add(intel,button("ANALIZA AVANSATA | OI / FUNDING / ORDERBOOK",()->{
   String selected=symbol;async(()->{Market.Analysis a=Market.analyze(selected);OpportunityEngine.Score score=OpportunityEngine.inspect(a);
    runOnUiThread(()->notice(selected+"\n"+score.details));});
  }));
  add(intel,button("JURNAL | WIN RATE / PROFIT FACTOR / DRAWDOWN",()->{
   try{notice(TradeJournal.performance(this));}catch(Exception e){notice("Jurnal indisponibil: "+e.getMessage());}
  }));
  add(intel,button("RISC ZILNIC | DEMO",()->{
   try{double capital=prefs.getFloat("capital",100),loss=TradeJournal.todayRealizedLoss(this),limit=capital*.025;
    notice(String.format(Locale.US,"Pierdere DEMO realizata azi %.2f USDT / limita %.2f USDT (2.5%% capital).\nLimita se aplica numai simularilor locale. Nu controleaza contul Bybit real.",loss,limit));}
   catch(Exception e){notice("Nu se poate calcula riscul: "+e.getMessage());}
  }));
  section("Miscari rapide // 24h");
  LinearLayout m=card();line(m,"Active Bybit Perpetual USDT cu volum de minimum 1 milion USDT/24h.");
  add(m,button("Actualizează câștigători și pierzători",this::refreshMovers));
  line(m,"Au crescut");tickerUp=new android.widget.HorizontalScrollView(this);tickerUp.setHorizontalScrollBarEnabled(false);moverUp=new LinearLayout(this);moverUp.setOrientation(LinearLayout.HORIZONTAL);tickerUp.addView(moverUp);add(m,tickerUp);
  line(m,"Au scazut");tickerDown=new android.widget.HorizontalScrollView(this);tickerDown.setHorizontalScrollBarEnabled(false);moverDown=new LinearLayout(this);moverDown.setOrientation(LinearLayout.HORIZONTAL);tickerDown.addView(moverDown);add(m,tickerDown);
  section("Ce ponturi am acum");
  LinearLayout picks=card();
  line(picks,"5 monede urmarite din piata Bybit. LONG/SHORT indica directia de analizat; intra numai daca semnalul este confirmat.");
  suggestions=vertical();add(picks,suggestions);
  line(suggestions,"Se cauta coinuri potrivite...");
  section("Basic Grafice");
  LinearLayout photoBox=card();
  line(photoBox,"VERIFICARE VIZUALA ORIENTATIVA: poza arata doar culori si inclinare; introdu simbolul pentru comparatie cu datele Bybit.");
  chartSymbolInput=field("Coinul din poza: BTCUSDT",symbol,false);add(photoBox,chartSymbolInput);
  chartStatus=text("Nicio poza selectata.",14,muted);add(photoBox,chartStatus);
  add(photoBox,button("VERIFICA POZA CU GRAFIC",this::pickChart));
  chartAnswer=text("Selecteaza fotografia pentru evaluarea graficului.",16,gold);add(photoBox,chartAnswer);
  LinearLayout basics=card();
  line(basics,"12 lecții cu grafice și explicații. Scheme educative, fără prețuri live.");
  BasicCharts.addTo(basics,this);
  section("TP / SL si levier");
  LinearLayout risk=card();line(risk,"Procentele TP și SL sunt ROE estimat la levierul ales, înainte de comisioane.");
  tpInput=field("TP ROE %",Float.toString(prefs.getFloat("tp",33)),true);add(risk,tpInput);
  slInput=field("SL ROE %",Float.toString(prefs.getFloat("sl",7)),true);add(risk,slInput);
  leverageInput=field("Levier ×",Float.toString(prefs.getFloat("lev",2)),true);add(risk,leverageInput);
  add(risk,button("Salvează valorile",this::saveSettings));
  section("Plan pentru 3 zile");
  LinearLayout planBox=card();
  line(planBox,"Alege o pereche reala Bybit USDT Perpetual. Planul foloseste pretul curent, trendul 1h/1D si nivelurile din lumanari 4h inchise.");
  planSymbolInput=new AutoCompleteTextView(this);
  planSymbolInput.setTextColor(Color.WHITE);planSymbolInput.setHintTextColor(muted);
  planSymbolInput.setTextSize(18);planSymbolInput.setSingleLine(true);
  planSymbolInput.setHint("Alege coinul: BTCUSDT...");
  planSymbolInput.setText(prefs.getString("plan_symbol",symbol));
  planSymbolInput.setThreshold(1);
  planSymbolInput.setDropDownHeight(Math.min(dp(420),getResources().getDisplayMetrics().heightPixels*60/100));
  planAdapter=new CoinDropdownAdapter(this);planSymbolInput.setAdapter(planAdapter);
  planSymbolInput.setOnItemClickListener((parent,view,position,id)->{
   Market.Coin picked=(Market.Coin)parent.getItemAtPosition(position);
   planSymbolInput.setText(picked.symbol,false);
   prefs.edit().putString("plan_symbol",picked.symbol).apply();choose(picked.symbol);
  });
  LinearLayout planCoinRow=new LinearLayout(this);planCoinRow.setOrientation(LinearLayout.HORIZONTAL);
  planCoinRow.addView(planSymbolInput,new LinearLayout.LayoutParams(0,-2,1));
  Button listCoins=button("LISTA",()->{
   if(planAdapter.totalCount()==0){notice("Lista coinurilor Bybit inca se incarca.");return;}
   planAdapter.getFilter().filter("",count->{planSymbolInput.requestFocus();planSymbolInput.showDropDown();});
  });
  listCoins.setTextSize(12);
  LinearLayout.LayoutParams listPlacement=new LinearLayout.LayoutParams(dp(72),dp(50));
  listPlacement.leftMargin=dp(6);planCoinRow.addView(listCoins,listPlacement);add(planBox,planCoinRow);
  line(planBox,"Capitalul si riscul maxim limiteaza marimea pozitiei. Setarile nu plaseaza ordine.");  capitalInput=field("Capital USDT",Float.toString(prefs.getFloat("capital",100)),true);add(planBox,capitalInput);
  riskInput=field("Risc maxim %",Float.toString(prefs.getFloat("risk",1)),true);add(planBox,riskInput);
  add(planBox,button("Salveaza riscul",()->{
   double capital=positive(capitalInput,0),riskPct=positive(riskInput,0);
   if(capital<=0||capital>10000000||riskPct<=0||riskPct>2){notice("Capital >0 si risc intre 0 si 2%.");return;}
   prefs.edit().putFloat("capital",(float)capital).putFloat("risk",(float)riskPct).apply();notice("Setari de risc salvate.");
  }));
  add(planBox,button("TEST ISTORIC 1H",()->{
   String current=planSymbolInput.getText().toString().trim().toUpperCase(Locale.ROOT);if(planAdapter.find(current)==null){notice("Alege coinul planului din lista Bybit.");return;}double take=positive(tpInput,33),stop=positive(slInput,7),leverage=positive(leverageInput,2);
   new Thread(()->{try{double fee=.0012;try{if(ReadOnlyBybit.connected(this))fee=ReadOnlyBybit.roundTripTakerFee(this,current);}catch(Exception ignored){}String info=Backtest.run(current,take,stop,leverage,fee).summary();
    runOnUiThread(()->notice(info));}catch(Exception ex){runOnUiThread(()->notice("Test indisponibil: "+ex.getMessage()));}}).start();
  }));
  add(planBox,button("GENEREAZA PLANUL PE 3 ZILE",this::generateThreeDayPlan));
  planTitle=text("Alege un coin si genereaza planul.",18,gold);
  planTitle.setTypeface(null,Typeface.BOLD);planTitle.setPadding(0,dp(12),0,dp(7));add(planBox,planTitle);
  planDetails=text("",15,Color.WHITE);planDetails.setLineSpacing(dp(3),1.07f);add(planBox,planDetails);
  LinearLayout savedBox=card();line(savedBox,"PLANURI URMARITE / ultimele 8");
  add(savedBox,button("VERIFICA PLANURILE CU PRETUL BYBIT",this::refreshPlanHistory));
  savedPlans=text(PlanStore.display(this),14,Color.WHITE);add(savedBox,savedPlans);  section("Intreaba AI");
  LinearLayout aiBox=card();
  line(aiBox,"Asistent local pe baza datelor Bybit. Intreaba despre LONG, SHORT, intrare, TP, SL sau risc. Nu foloseste un model AI conectat.");
  aiQuestion=field("Ex.: Sa fac SHORT pe SOLUSDT?","",false);add(aiBox,aiQuestion);
  TextView aiAnswer=text("Alege coinul si scrie intrebarea.",15,gold);
  add(aiBox,button("INTREABA AI",()->{
   String current=symbolInput.getText().toString().trim().toUpperCase(Locale.ROOT).replace("/","");
   String question=aiQuestion.getText().toString().trim();
   if(!current.matches("[A-Z0-9]{2,22}USDT")){aiAnswer.setText("Introdu un coin USDT valid.");return;}
   if(question.isEmpty()){aiAnswer.setText("Scrie o intrebare.");return;}
   double take=positive(tpInput,33),stop=positive(slInput,7),leverage=positive(leverageInput,2);
   double capital=positive(capitalInput,100),riskPct=positive(riskInput,1);
   aiAnswer.setText("Se verifica datele Bybit pentru "+current+"...");
   new Thread(()->{String response=AskAi.answer(this,current,question,take,stop,leverage,capital,riskPct);
    runOnUiThread(()->{if(!isFinishing())aiAnswer.setText(response);});}).start();
  }));
  add(aiBox,aiAnswer);
  LinearLayout externalAi=card();
  TextView externalHeading=text("INTREABA UN MODEL AI EXTERN",17,gold);externalHeading.setTypeface(null,Typeface.BOLD);add(externalAi,externalHeading);
  line(externalAi,"Selecteaza serviciul. Intrebarea despre coin este copiata pentru lipire in browser. Verifica manual raspunsul si pretul curent.");
  aiProvider=new Spinner(this);
  String[] aiNames={"ChatGPT","Claude","Gemini","Perplexity","Grok"};
  ArrayAdapter<String> aiOptions=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,aiNames);
  aiOptions.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  aiProvider.setAdapter(aiOptions);add(externalAi,aiProvider);
  add(externalAi,button("Deschide AI cu intrebarea",()->openExternalAi()));
  section("Evenimente Bybit");
  LinearLayout eventsBox=card();
  line(eventsBox,"Anunturi oficiale legate de coin; apasa un titlu pentru sursa.");
  LinearLayout eventLinks=vertical();add(eventsBox,eventLinks);
  add(eventsBox,button("CAUTA EVENIMENTE",()->{
   String base=symbol.endsWith("USDT")?symbol.substring(0,symbol.length()-4):symbol;
   new Thread(()->{try{java.util.ArrayList<AdvancedMarket.Event> events=AdvancedMarket.announcements(base);
    runOnUiThread(()->{eventLinks.removeAllViews();if(events.isEmpty())line(eventLinks,"Niciun anunt relevant in ultimele 50.");
     for(AdvancedMarket.Event event:events){TextView link=text(event.when+" | "+event.title,15,gold);link.setPadding(0,dp(8),0,dp(8));link.setOnClickListener(v->{
      try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(event.url)));}catch(Exception ignored){notice("Link indisponibil.");}
     });add(eventLinks,link);}
    });}catch(Exception ex){runOnUiThread(()->notice("Anunturi indisponibile: "+ex.getMessage()));}}).start();
  }));
  section("Alerte GS777");
  LinearLayout alertBox=card();
  line(alertBox,"Alege alertele dorite. Android poate amana verificarea in fundal.");
  alertHealth=text("Se verifica starea alertelor...",14,muted);add(alertBox,alertHealth);
  updateAlertHealth();
  add(alertBox,button("TESTEAZA NOTIFICAREA",this::testNotification));
  alertRise=alertCheck(alertBox,"Cresteri rapide 24h pe Bybit","alertRise",true);
  alertFall=alertCheck(alertBox,"Scaderi rapide 24h pe Bybit","alertFall",true);
  thresholdInput=field("Prag miscare 24h %",Float.toString(prefs.getFloat("alertThreshold",33)),true);add(alertBox,thresholdInput);
  alertTips=alertCheck(alertBox,"Coin nou in Ce ponturi am acum + directie LONG/SHORT","alertTips",true);
  alertSetup=alertCheck(alertBox,"Semnal LONG/SHORT confirmat pe coinurile urmarite","alertSetup",true);
  alertEvents=alertCheck(alertBox,"Eveniment nou in anunturile oficiale Bybit","alertEvents",true);
  alertCreator=alertCheck(alertBox,"YouTuber monitorizat posteaza despre un coin urmarit","alertCreator",true);
  line(alertBox,"Canale YouTube incluse (bifeaza-le pe cele dorite):");
  creatorChoices=YoutubeChannels.addChoices(this,alertBox,prefs);
  channelIdsInput=field("Optional: alte ID-uri UC..., separate prin virgula",prefs.getString("youtubeChannelsCustom",prefs.getString("youtubeChannels","")),false);add(alertBox,channelIdsInput);
  line(alertBox,"Cele 30 de canale sunt deja adaugate. Optional, adauga pana la 4 ID-uri noi. Verificarile se fac pe rand, fara alerte pentru Discord sau postari private.");
  alertPrice=alertCheck(alertBox,"Pretul coinului ales trece prin tinta setata","alertPrice",true);
  watchInput=field("Coinuri urmarite: BTCUSDT,SOLUSDT",prefs.getString("watchlist","BTCUSDT,SOLUSDT"),false);add(alertBox,watchInput);
  priceAlertInput=field("Pret tinta pentru coinul ales (0 = dezactivat)",Float.toString(prefs.getFloat("priceAlert",0)),true);add(alertBox,priceAlertInput);
  alertFunding=alertCheck(alertBox,"Funding extrem pe coinurile urmarite","alertFunding",false);
  alertInterest=alertCheck(alertBox,"Open interest creste brusc","alertInterest",false);
  alertPositions=alertCheck(alertBox,"Risc in pozitiile mele Bybit Read Only","alertPositions",true);
  alertPlan=alertCheck(alertBox,"Planul salvat atinge pragul sau invalidarea","alertPlan",true);
  alertJournal=alertCheck(alertBox,"Raport despre jurnalul Demo","alertJournal",true);
  add(alertBox,button("Salveaza alertele",()->{
   double threshold=positive(thresholdInput,0);if(threshold<1||threshold>1000){notice("Prag intre 1 si 1000%.");return;}
   String watch=watchInput.getText().toString().toUpperCase(Locale.ROOT).replace(" ","");
   if(!watch.matches("[A-Z0-9,]{3,120}")){notice("Scrie simboluri USDT separate prin virgula.");return;}
   String channels=channelIdsInput.getText().toString().trim().replace(" ","");
   if(!channels.isEmpty()){
    String[] ids=channels.split(",");
    if(ids.length>4){notice("Maximum 4 canale YouTube.");return;}
    for(String id:ids)if(!id.matches("UC[A-Za-z0-9_-]{22}")){notice("Introdu ID-uri YouTube de forma UC... (24 caractere), nu @handle.");return;}
   }
   boolean anyCreator=!channels.isEmpty();for(CheckBox cb:creatorChoices)if(cb.isChecked())anyCreator=true;
   if(alertCreator.isChecked()&&!anyCreator){notice("Bifeaza cel putin un canal YouTube.");return;}
   double target=positive(priceAlertInput,0);
   prefs.edit().putFloat("alertThreshold",(float)threshold).putString("watchlist",watch).putFloat("priceAlert",(float)target)
    .putString("alertSymbol",symbolInput.getText().toString().trim().toUpperCase(Locale.ROOT).replace("/",""))
    .putString("youtubeChannelsCustom",channels)
    .putBoolean("alertRise",alertRise.isChecked()).putBoolean("alertFall",alertFall.isChecked())
    .putBoolean("alertTips",alertTips.isChecked()).putBoolean("alertSetup",alertSetup.isChecked())
    .putBoolean("alertEvents",alertEvents.isChecked()).putBoolean("alertCreator",alertCreator.isChecked())
    .putBoolean("alertPrice",alertPrice.isChecked()).putBoolean("alertFunding",alertFunding.isChecked())
    .putBoolean("alertInterest",alertInterest.isChecked()).putBoolean("alertPositions",alertPositions.isChecked())
    .putBoolean("alertJournal",alertJournal.isChecked()).putBoolean("alertPlan",alertPlan.isChecked()).apply();
   SharedPreferences.Editor creatorEditor=prefs.edit();
   for(int i=0;i<creatorChoices.length;i++)creatorEditor.putBoolean(YoutubeChannels.selectionKey(YoutubeChannels.IDS[i]),creatorChoices[i].isChecked());
   creatorEditor.apply();
   AlertsJob.schedule(this);notice("Preferintele au fost salvate. Android poate amana verificarile in fundal.");
  }));
  section("Tranzactie Demo");
  LinearLayout demo=card();line(demo,"Simulare pe telefon. Nu folosește bani și nu trimite ordine.");
  amountInput=field("Marja în USDT (de ex. 10)","10",true);add(demo,amountInput);
  LinearLayout pair=new LinearLayout(this);pair.setOrientation(0);
  Button buy=button("Simulează LONG",()->simulate(true));Button sell=button("Simulează SHORT",()->simulate(false));
  LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.setMargins(0,dp(3),dp(4),dp(3));pair.addView(buy,half);
  LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1);half2.setMargins(dp(4),dp(3),0,dp(3));pair.addView(sell,half2);add(demo,pair);
  add(demo,button("Istoric Demo",this::history));
  section("Pozitii reale - numai citire");
  LinearLayout accountBox=card();
  line(accountBox,"Optional. Introdu aici o cheie API Bybit cu Read Only; nu o trimite in chat. Salvare criptata pe telefon. Aplicatia nu trimite ordine.");
  EditText apiKey=field("API Key","",false),apiSecret=field("API Secret","",false);
  apiKey.setInputType(129);apiSecret.setInputType(129);
  add(accountBox,apiKey);add(accountBox,apiSecret);
  add(accountBox,button("Conecteaza Read Only",()->{
   String k=apiKey.getText().toString(),s=apiSecret.getText().toString();apiKey.setText("");apiSecret.setText("");
   new Thread(()->{try{ReadOnlyBybit.connect(this,k,s);runOnUiThread(()->notice("Conectat Read Only."));}catch(Exception ex){runOnUiThread(()->notice("Conectare respinsa: "+ex.getMessage()));}}).start();
  }));
  add(accountBox,button("Vezi pozitiile mele",()->{
   new Thread(()->{try{String positions=ReadOnlyBybit.positions(this);runOnUiThread(()->notice(positions));}catch(Exception ex){runOnUiThread(()->notice("Pozitii indisponibile: "+ex.getMessage()));}}).start();
  }));
  add(accountBox,button("Sterge cheia salvata",()->{ReadOnlyBybit.disconnect(this);notice("Cheia API salvata a fost stearsa.");}));
  createDashboard();
  showSection(dashboardIndex,false);
  refreshDashboard();
  refreshMovers();
  line(root,"Date: API public Bybit · Nu reprezintă o probabilitate verificată de profit. Alertele Android sunt aproximativ la 30-60 minute; sistemul poate întârzia rularea.");
 }

 void startLiveTicker(){
  if(liveStream!=null)liveStream.stop();
  liveStream=new BybitTickerStream(new BybitTickerStream.Listener(){
   public void status(String message){runOnUiThread(()->{if(dashConnection!=null)dashConnection.setText("WebSocket: "+message);});}
   public void ticker(String name,double price,double change){lastLiveTickAt=System.currentTimeMillis();runOnUiThread(()->{
    if(name.equals(symbol)&&dashChart!=null)dashChart.updateLive(price);
    if("BTCUSDT".equals(name)&&dashMarket!=null)dashMarket.setText(String.format(Locale.US,"BTCUSDT %s USDT | 24h %+.2f%% | LIVE",Market.number(price),change));
    if(name.equals(symbol)&&topSummary!=null)topSummary.setText(String.format(Locale.US,"%s | %s USDT | %+.2f%%/24h",name,Market.number(price),change));
   });}
  });liveStream.start(symbol);
 }
 void createDashboard(){
  dashboardIndex=sectionBodies.size();section("GS777 | Dashboard");
  LinearLayout hero=card();
  TextView title=text("GS777  /  TRADING INTELLIGENCE",21,gold);title.setTypeface(null,Typeface.BOLD);add(hero,title);
  line(hero,"Radar Bybit  •  Capital protejat  •  Decizii disciplinate");
  dashFreshness=text("REST: datele nu au fost actualizate.",12,muted);add(hero,dashFreshness);
  dashConnection=text("WebSocket: conectare in asteptare",12,muted);add(hero,dashConnection);
  add(hero,button("ACTUALIZEAZA DASHBOARD",this::refreshDashboard));
  LinearLayout regime=card();line(regime,"01  /  STAREA PIETEI");dashMarket=text("BTC: asteapta actualizarea",17,Color.WHITE);add(regime,dashMarket);
  LinearLayout chartBox=card();dashChartTitle=text("LUMANARI 1H  ·  MA50/100/200  ·  ZOOM",14,gold);add(chartBox,dashChartTitle);
  dashChart=new DashboardChart(this);chartBox.addView(dashChart,new LinearLayout.LayoutParams(-1,dp(270)));
  dashPlanInfo=text("Selecteaza un plan activ pentru a vedea Entry, TP1, TP2 si SL.",13,muted);add(chartBox,dashPlanInfo);
  dashSetupInfo=text("Breakout / retest 4H: in asteptarea analizei.",13,muted);add(chartBox,dashSetupInfo);
  add(chartBox,button("AFISEAZA ULTIMUL PLAN PE GRAFIC",this::loadSelectedPlanOnChart));
  add(chartBox,button("STERGE NIVELURILE PLANULUI",()->{if(dashChart!=null)dashChart.clearTradeLevels();if(dashPlanInfo!=null)dashPlanInfo.setText("Nivelurile planului au fost ascunse.");}));
  add(chartBox,button("DESCHIDE GRAFICELE",()->showSection(3,false)));
  LinearLayout scores=card();line(scores,"SCORURI LONG / SHORT");
  dashScores=text("Scoruri indisponibile pana la analiza. NO TRADE.",16,Color.WHITE);add(scores,dashScores);
  add(scores,button("ANALIZEAZA MONEDA SELECTATA",()->openCoin(symbol)));
  LinearLayout opportunities=card();line(opportunities,"02  /  RADAR OPORTUNITATI");
  dashLeaders=text("Nicio oportunitate confirmata. Datele ±33% sunt doar pentru investigatie.",15,Color.WHITE);add(opportunities,dashLeaders);
  add(opportunities,button("DESCHIDE ANALIZA",()->showSection(0,false)));
  add(opportunities,button("MISCARI 24H",()->showSection(1,false)));
  LinearLayout risk=card();line(risk,"03  /  PROTECTIA CAPITALULUI");
  dashRisk=text("Se calculeaza riscul Demo...",16,Color.WHITE);add(risk,dashRisk);
  add(risk,button("RISC SI SIMULARE DEMO",()->showSection(0,false)));
  LinearLayout journal=card();line(journal,"04  /  REZULTATE DEMO");
  dashJournal=text("Jurnalul este disponibil dupa actualizare.",14,Color.WHITE);add(journal,dashJournal);
  add(journal,button("JURNAL COMPLET",()->notice(TradeJournal.performance(this))));
  LinearLayout actions=card();line(actions,"ACCES RAPID");
  add(actions,button("GRAFICE",()->showSection(3,false)));
  add(actions,button("PLAN 3 ZILE",()->showSection(5,false)));
  add(actions,button("UNELTE GS777",this::openSections));
  line(currentSection,"Scorurile sunt euristice. NO TRADE este o decizie valida. Nu se plaseaza ordine reale.");
 }
 void refreshDashboard(){
  if(dashFreshness==null||dashboardRefreshing)return;
  dashboardRefreshing=true;
  final String requestedSymbol=symbol; final int requestId=++dashboardRequest;
  dashFreshness.setText("Actualizare in curs... (date publice Bybit)");
  new Thread(()->{
   String marketText,leaderText,riskText,journalText,scoreText="NO TRADE: analiza indisponibila.",setupText="Setup 4H neverificat."; double[][] chartCandles=null; SetupConfirmation.Result setupResult=null;
   try{
    java.util.List<Market.Coin> coins=Market.allUsdtPerpetual();
    Market.Coin btc=null;java.util.ArrayList<Market.Coin> movers=new java.util.ArrayList<>();
    for(Market.Coin coin:coins){if("BTCUSDT".equals(coin.symbol))btc=coin;
     if(coin.turnover>=1000000&&Math.abs(coin.change)>=33)movers.add(coin);}
    movers.sort((a,b)->Double.compare(Math.abs(b.change),Math.abs(a.change)));
    try{Market.Analysis signal=Market.analyze(requestedSymbol);
     int longScore=Math.max(0,Math.min(100,50+signal.score*9));int shortScore=Math.max(0,Math.min(100,50-signal.score*9));
     scoreText=String.format(Locale.US,"%s   LONG %d/100   SHORT %d/100\n%s",requestedSymbol,longScore,shortScore,Math.max(longScore,shortScore)>=85?"SETUP DE VERIFICAT":"NO TRADE / WATCH");
    }catch(Exception ignored){scoreText="NO TRADE: analiza nu a fost validata.";}
    try{chartCandles=Market.candles(requestedSymbol,"60");}catch(Exception ignored){}
    try{
     org.json.JSONArray plans=PlanStore.snapshot(this);String side="";
     for(int pi=0;pi<plans.length();pi++){
      org.json.JSONObject item=plans.getJSONObject(pi);
      if(requestedSymbol.equals(item.optString("symbol"))&&item.optLong("expires")>System.currentTimeMillis()&&("LONG".equals(item.optString("side"))||"SHORT".equals(item.optString("side")))){side=item.optString("side");break;}
     }
     if(!side.isEmpty()){
      setupResult=SetupConfirmation.inspect(Market.candles(requestedSymbol,"240"),side);
      setupText="4H "+side+" | "+setupResult.state+"\n"+setupResult.description;
      try{MarketConfirmation.Result confirm=MarketConfirmation.inspect(requestedSymbol,side,setupResult);
       setupText+="\nConfirmare flux: "+confirm.status+"\n"+confirm.details;
      }catch(Exception ce){setupText+="\nConfirmare flux: NO TRADE (date indisponibile)";}
     }else setupText="4H: alege un plan LONG/SHORT activ pentru verificare.";
    }catch(Exception ex){setupText="4H neverificat: date indisponibile. NO TRADE.";}
    marketText=btc==null?"BTC indisponibil":String.format(Locale.US,"BTCUSDT  %s USDT  |  24h %+.2f%%",Market.number(btc.price),btc.change);
    StringBuilder b=new StringBuilder();b.append(coins.size()).append(" perechi monitorizate | ").append(movers.size()).append(" miscari ±33%\n");
    if(movers.isEmpty())b.append("Nicio miscare extrema la acest moment. NO TRADE.");
    for(int i=0;i<Math.min(4,movers.size());i++){Market.Coin c=movers.get(i);b.append(String.format(Locale.US,"\n%s  %+.1f%%  |  %.1fM USDT volum",c.symbol,c.change,c.turnover/1000000));}
    b.append("\n\nMiscarile extreme NU sunt semnale LONG/SHORT.");leaderText=b.toString();
   }catch(Exception e){marketText="Bybit indisponibil: "+e.getMessage();leaderText="Nu exista date verificate. NO TRADE.";}
   try{double capital=prefs.getFloat("capital",100);double loss=TradeJournal.todayRealizedLoss(this);double limit=capital*.025;
    riskText=String.format(Locale.US,"Capital Demo: %.2f USDT\nRisc recomandat 1%%: %.2f USDT\nPierdere zilnica: %.2f / %.2f USDT\n%s",capital,capital*.01,loss,limit,loss>=limit?"BLOCAT: limita zilnica atinsa":"Limita Demo 2.5% activa");
   }catch(Exception e){riskText="Risc indisponibil; verifica jurnalul inainte de simulare.";}
   try{journalText=TradeJournal.performance(this);}catch(Exception e){journalText="Jurnal indisponibil: "+e.getMessage();}
   final String m=marketText,l=leaderText,r=riskText,j=journalText,sc=scoreText,st=setupText,chartSymbol=requestedSymbol;final double[][] cc=chartCandles;final SetupConfirmation.Result sr=setupResult;
   runOnUiThread(()->{dashboardRefreshing=false;if(requestId!=dashboardRequest||!requestedSymbol.equals(symbol))return;if(dashMarket!=null){dashMarket.setText(m);dashLeaders.setText(l);dashRisk.setText(r);dashJournal.setText(j);if(dashScores!=null)dashScores.setText(sc);if(dashSetupInfo!=null)dashSetupInfo.setText(st);if(dashChart!=null){if(sr!=null)dashChart.setSetupLevels(sr.breakout,sr.retest);else dashChart.clearSetupLevels();}if(dashChart!=null&&cc!=null&&chartSymbol.equals(symbol))dashChart.setCandles(cc);if(dashChartTitle!=null)dashChartTitle.setText(symbol+"  /  LUMANARI 1H  ·  MA50/100/200  ·  ZOOM");
    dashFreshness.setText("REST actualizat: "+new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new Date())+" | verifica eventualele erori de conexiune");}});
  }).start();
 }

 void loadSelectedPlanOnChart(){
  if(dashChart==null||dashPlanInfo==null)return;
  try{
   org.json.JSONArray all=PlanStore.snapshot(this);
   for(int i=0;i<all.length();i++){
    org.json.JSONObject item=all.getJSONObject(i);
    if(!symbol.equals(item.optString("symbol")))continue;
    if(!"LONG".equals(item.optString("side"))&&!"SHORT".equals(item.optString("side")))continue;
    if(item.optLong("expires")<=System.currentTimeMillis())continue;
    double entry=item.optDouble("entry"),sl=item.optDouble("stop"),tp1=item.optDouble("tp1"),tp2=item.optDouble("tp2");
    if(!(entry>0&&sl>0&&tp1>0&&tp2>0))continue;
    boolean longSide="LONG".equals(item.optString("side"));
    if(longSide?!(sl<entry&&entry<tp1&&tp1<tp2):!(tp2<tp1&&tp1<entry&&entry<sl))continue;
    dashChart.setTradeLevels(entry,tp1,tp2,sl);
    double rr1=Math.abs(tp1-entry)/Math.abs(entry-sl),rr2=Math.abs(tp2-entry)/Math.abs(entry-sl);
    dashPlanInfo.setText(String.format(Locale.US,"%s %s  |  Entry %s  |  SL %s\nTP1 %s (%.2fR)  |  TP2 %s (%.2fR)\nPlan conditionat, nu ordin executat. Confirmarea 4H si retestul sunt obligatorii.",symbol,item.optString("side"),ThreeDayPlan.price(entry),ThreeDayPlan.price(sl),ThreeDayPlan.price(tp1),rr1,ThreeDayPlan.price(tp2),rr2));
    return;
   }
   dashChart.clearTradeLevels();dashPlanInfo.setText("Niciun plan LONG/SHORT activ pentru "+symbol+". Genereaza unul in Plan.");
  }catch(Exception e){dashChart.clearTradeLevels();dashPlanInfo.setText("Planul nu a putut fi citit: "+e.getMessage());}
 }
 void refreshPlanHistory(){
  if(savedPlans==null)return;
  savedPlans.setText("Se verifica preturile pentru cel mult cinci planuri...");
  new Thread(()->{
   try{
    org.json.JSONArray list=PlanStore.snapshot(this);
    int checked=0;
    java.util.HashMap<String,Double> quotes=new java.util.HashMap<>();
    for(int i=0;i<list.length()&&checked<5;i++){
     org.json.JSONObject entry=list.getJSONObject(i);
     if(System.currentTimeMillis()>=entry.optLong("expires"))continue;
     if(entry.optString("side").equals("ASTEAPTA"))continue;
     String name=entry.optString("symbol");
     try{
      Double current=quotes.get(name);
      if(current==null){current=Market.ticker(name).price;quotes.put(name,current);}
      PlanStore.update(this,entry.optLong("created"),current);checked++;
     }catch(Exception ignored){}
    }
    String display=PlanStore.display(this);
    runOnUiThread(()->{if(!isFinishing()&&savedPlans!=null)savedPlans.setText(display);});
   }catch(Exception ex){
    runOnUiThread(()->{if(savedPlans!=null)savedPlans.setText(PlanStore.display(this)+"\nActualizare indisponibila: "+ex.getMessage());});
   }
  }).start();
 }
 void updateAlertHealth(){
  if(alertHealth==null)return;
  long at=getSharedPreferences("gs777_alerts",MODE_PRIVATE).getLong("lastCheck",0);
  String label=at==0?"Nu exista verificari de fundal inregistrate.":
   "Ultima verificare: "+new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(at));
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   label+="\nNotificarile nu au permisiune.";
  long delay=at==0?0:System.currentTimeMillis()-at;
  if(delay>2L*60*60*1000)label+="\nDatele alertelor sunt vechi; Android poate amana verificarile.";
  String failure=getSharedPreferences("gs777_alerts",MODE_PRIVATE).getString("lastMarketError","");
  if(!failure.isEmpty())label+="\nUltima eroare de piata: "+failure;
  alertHealth.setText(label+"\nVerificare programata aproximativ, nu la minut fix.");
 }
 void testNotification(){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
   notice("Accepta permisiunea si apasa din nou Testeaza notificarea.");return;
  }
  NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(manager==null){notice("Notificarile nu sunt disponibile pe acest telefon.");return;}
  android.app.Notification.Builder test=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(this,"movers"):new android.app.Notification.Builder(this);
  test.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("GS777 - notificare test")
   .setContentText("Alertele pot fi afisate. Frecventa depinde de Android.");
  manager.notify(777123,test.build());updateAlertHealth();
 } void generateThreeDayPlan(){
  final String coin=planSymbolInput.getText().toString().trim().toUpperCase(Locale.ROOT).replace("/","");
  if(!coin.matches("[A-Z0-9]{2,22}USDT")||planAdapter==null||planAdapter.find(coin)==null){
   planTitle.setText("Alege o pereche reala din lista Bybit.");
   planDetails.setText("Apasa LISTA sau cauta simbolul si selecteaza rezultatul.");
   return;
  }
  double capital,riskPct,lev;
  try{
   capital=Double.parseDouble(capitalInput.getText().toString().replace(",",".").trim());
   riskPct=Double.parseDouble(riskInput.getText().toString().replace(",",".").trim());
   lev=Double.parseDouble(leverageInput.getText().toString().replace(",",".").trim());
  }catch(Exception ex){planTitle.setText("Verifica cifrele pentru capital, risc si levier.");return;}
  prefs.edit().putString("plan_symbol",coin).apply();
  final int generation=++planGeneration;
  planTitle.setText("Se calculeaza planul pentru "+coin+"...");
  planDetails.setText("Se citesc date Bybit actuale si lumanari 4h inchise.");
  new Thread(()->{
   try{
    ThreeDayPlan.Result result=ThreeDayPlan.build(coin,capital,riskPct,lev);
    runOnUiThread(()->{
     if(isFinishing()||generation!=planGeneration)return;
     try{PlanStore.save(this,result);}catch(Exception saveError){planDetails.setText("Planul a fost calculat, dar nu a putut fi salvat: "+saveError.getMessage());return;}
     planTitle.setText(result.title);
     planTitle.setTextColor(result.tone>0?green:result.tone<0?red:gold);
     planDetails.setText(result.details);
     if(savedPlans!=null)savedPlans.setText(PlanStore.display(this));if(coin.equals(symbol))loadSelectedPlanOnChart();
    });
   }catch(Exception ex){
    runOnUiThread(()->{
     if(isFinishing()||generation!=planGeneration)return;
     planTitle.setText("Plan indisponibil pentru "+coin);
     planTitle.setTextColor(gold);
     planDetails.setText(ex.getMessage()+" Verifica conexiunea si incearca din nou.");
    });
   }
  }).start();
 } void showAnalysis(Market.Analysis a){
  output.removeAllViews();
  analysisAt=System.currentTimeMillis();
  String updated=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(analysisAt));
  if(topSummary!=null)topSummary.setText(a.coin.symbol+" / Bybit "+updated+" / "+Market.number(a.coin.price)+" USDT");
  line(output,a.coin.symbol+" · "+Market.number(a.coin.price)+" USDT");
  TextView delta=text(String.format(Locale.US,"24h: %+.2f%%",a.coin.change),22,a.coin.change>=0?green:red);add(output,delta);
  TextView verdict=text("Scor tehnic: "+a.direction,20,a.score>=3?green:a.score<=-3?red:gold);verdict.setTypeface(null,Typeface.BOLD);verdict.setPadding(0,dp(10),0,dp(8));add(output,verdict);
  int longScore=Math.max(0,Math.min(100,50+a.score*9));
  int shortScore=Math.max(0,Math.min(100,50-a.score*9));
  String decision=Math.max(longScore,shortScore)>=85?(longScore>shortScore?"LONG - verificare necesara":"SHORT - verificare necesara"):"NO TRADE / WATCH";
  line(output,String.format(Locale.US,"GS777 Score | LONG %d/100 | SHORT %d/100 | %s",longScore,shortScore,decision));
  line(output,"Scor euristic, NU probabilitate de castig. OI, funding si lichiditatea necesita verificare.");
  TextView reason=text(a.reason,14,muted);reason.setVisibility(View.GONE);
  add(output,button("DE CE ACEASTA CONCLUZIE?",()->reason.setVisibility(reason.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));
  add(output,reason);
  asyncPlan(a,positive(capitalInput,100),positive(riskInput,1),positive(tpInput,33),positive(slInput,7),positive(leverageInput,2));
  double tp=positive(tpInput,33),sl=positive(slInput,7),lev=positive(leverageInput,2),entry=a.coin.price;
  if(lev>100||sl>=100){line(output,"Verifică valorile TP / SL și levier.");return;}
  double targetMove=tp/100/lev,stopMove=sl/100/lev;
  line(output,String.format(Locale.US,"La levier %.2f× · TP ROE %.1f%% / SL ROE %.1f%%",lev,tp,sl));
  line(output,"LONG · TP "+Market.number(entry*(1+targetMove))+" · SL "+Market.number(entry*(1-stopMove)));
  line(output,"SHORT · TP "+Market.number(entry*(1-targetMove))+" · SL "+Market.number(entry*(1+stopMove)));
  line(output,"Niveluri orientative calculate din ultimul preț. Verifică tick size, mark price, spread, comisioane și lichidarea în Bybit.");
  status.setText("Date Bybit: "+new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date())+" · Actualizează înainte de decizie.");
 }
 void asyncPlan(Market.Analysis a,double capital,double risk,double take,double stop,double leverage){
  new Thread(()->{try{
   MarketExtras.Plan p=MarketExtras.inspect(this,a.coin.symbol,a,capital,risk,take,stop,leverage);
   runOnUiThread(()->{if(analysis==a){TextView t=text("PLAN: "+p.verdict+"\n"+MarketExtras.summary(p),14,gold);add(output,t);}});
  }catch(Exception ex){runOnUiThread(()->{if(analysis==a)line(output,"Planul complet nu s-a incarcat: "+ex.getMessage());});}}).start();
 }
 void openBybitCoin(String name){
  if(name==null||!name.matches("[A-Z0-9]{2,22}USDT"))return;
  Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.bybit.com/trade/usdt/"+name));
  try{
   Intent app=new Intent(Intent.ACTION_VIEW,intent.getData());app.setPackage("com.bybit.app");
   startActivity(app);return;
  }catch(Exception ignored){}
  try{
   Intent eu=new Intent(Intent.ACTION_VIEW,intent.getData());eu.setPackage("com.bybit.eu");
   startActivity(eu);return;
  }catch(Exception ignored){}
  try{startActivity(intent);}catch(Exception ex){notice("Instaleaza Bybit sau verifica daca aplicatia poate deschide linkul "+name+".");}
 } String selectedAiCoin(){
  String current=symbolInput.getText().toString().trim().toUpperCase(Locale.ROOT).replace("/","");
  return current.matches("[A-Z0-9]{2,22}USDT")?current:"";
 }
 String externalPrompt(boolean image){
  String current=selectedAiCoin(),custom=aiQuestion.getText().toString().trim();
  return "Analizeaza "+current+" pe Bybit USDT Perpetual: "+(image?"fotografia atasata a graficului, ":"")+"trend 1h/4h/1D, suport, rezistenta, volum, retest, invalidare, spread si risc. "
   +(custom.isEmpty()?"Este mai justificat LONG, SHORT sau ASTEAPTA? Explica de ce.":custom+" ")
   +"Da directia numai daca ai date verificabile actuale; daca imaginea nu arata perioada sau pretul, spune ce lipseste. Nu inventa procent de castig.";
 }
 void copyPrompt(String prompt){
  android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
  clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Intrebare GS777",prompt));
 }
 void openExternalAi(){
  if(selectedAiCoin().isEmpty()){notice("Alege un coin USDT valid in sectiunea Moneda.");return;}
  String[] urls={"https://chatgpt.com/","https://claude.ai/new","https://gemini.google.com/app","https://www.perplexity.ai/","https://grok.com/"};
  int selected=aiProvider.getSelectedItemPosition();
  if(selected<0||selected>=urls.length)return;
  copyPrompt(externalPrompt(false));
  try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(urls[selected])).addCategory(Intent.CATEGORY_BROWSABLE));
   notice("Intrebarea este copiata. Lipeste-o in "+aiProvider.getSelectedItem()+" si trimite-o acolo.");
  }catch(Exception ex){notice("Browser indisponibil. Intrebarea ramane copiata.");}
 }
 void pickChart(){
  Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("image/*");
  try{startActivityForResult(picker,741);}catch(Exception ex){notice("Nu am putut deschide fotografiile.");}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==741&&result==RESULT_OK&&data!=null&&data.getData()!=null){
   chosenChart=data.getData();
   try{getContentResolver().takePersistableUriPermission(chosenChart,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
   if(chartStatus!=null)chartStatus.setText("Poza aleasa. Se analizeaza graficul...");
   analyzeChart(chosenChart);
  }
 }
 void analyzeChart(Uri image){
  String current=chartSymbolInput.getText().toString().trim().toUpperCase(Locale.ROOT).replace("/","");
  if(!current.matches("[A-Z0-9]{2,22}USDT")){chartAnswer.setText("CONCLUZIE: ASTEAPTA. Scrie coinul USDT din poza.");return;}
  if(!current.equals(symbol))choose(current);
  final String market=current;
  chartAnswer.setText("Se verifica pixelii graficului si datele Bybit...");
  new Thread(()->{
   String answer=PhotoChartAnalyzer.analyze(this,image,market);
   runOnUiThread(()->{if(isFinishing()||!image.equals(chosenChart))return;
    android.text.SpannableString styled=new android.text.SpannableString(answer);
    int start="CONCLUZIE: ".length(),end=answer.indexOf(' ',start);
    if(end<0)end=answer.length();
    String verdict=answer.substring(start,end);
    styled.setSpan(new android.text.style.ForegroundColorSpan(verdict.equals("LONG")?green:verdict.equals("SHORT")?red:gold),
     start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    chartAnswer.setText(styled);chartStatus.setText("Evaluare pentru "+market+" / poza din telefon.");
   });
  }).start();
 }
 void openCoin(String name){choose(name);showSection(0,false);screenScroll.post(()->screenScroll.smoothScrollTo(0,0));new Thread(()->{try{Market.Analysis a=Market.analyze(name);runOnUiThread(()->{if(name.equals(symbol)){analysis=a;showAnalysis(a);}});}catch(Exception e){runOnUiThread(()->status.setText("Analiza "+name+" nu este disponibila: "+e.getMessage()));}}).start();}
 void refreshMovers(){
  async(()->{
   List<Market.Coin> all=Market.allUsdtPerpetual();
   java.util.ArrayList<Market.Coin> liquid=new java.util.ArrayList<>();
   for(Market.Coin coin:all)if(coin.turnover>=1000000)liquid.add(coin);
   liquid.sort(java.util.Comparator.comparingDouble((Market.Coin coin)->coin.change).reversed());
   runOnUiThread(()->{
    if(isFinishing())return;
    marketAdapter.replace(all,symbolInput.getText());
    planAdapter.replace(all,planSymbolInput.getText());
    updateSelectedDelta(symbolInput.getText().toString().trim().toUpperCase(Locale.ROOT));
    coinCount.setText(all.size()+" perechi USDT Perpetual / variatia 24h Bybit. Scrie un nume sau apasa ▼.");
    moverUp.removeAllViews();moverDown.removeAllViews();
    if(upAnimation!=null)upAnimation.cancel();if(downAnimation!=null)downAnimation.cancel();
    int n=Math.min(8,liquid.size());
    for(int i=0;i<n;i++)mover(moverUp,liquid.get(i));
    for(int i=liquid.size()-1;i>=Math.max(0,liquid.size()-n);i--)mover(moverDown,liquid.get(i));
    startRail(tickerUp,moverUp,true);startRail(tickerDown,moverDown,false);
    status.setText("Bybit / "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date())+
      " / "+all.size()+" perechi USDT / "+liquid.size()+" cu rulaj de minimum 1M.");
    int generation=++suggestionGeneration;suggestions.removeAllViews();
    line(suggestions,"Se verifica lichiditatea si semnalele...");
    new Thread(()->recommend(liquid,generation)).start();
   });
  });
 }
 void startRail(android.widget.HorizontalScrollView rail,LinearLayout row,boolean rising){
  rail.postDelayed(()->{
   int max=row.getWidth()-rail.getWidth();if(max<=0)return;
   android.animation.ObjectAnimator animator=android.animation.ObjectAnimator.ofInt(rail,"scrollX",0,max);
   animator.setDuration(Math.max(5000,Math.min(24000,max*14L)));
   animator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
   animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
   if(rising)upAnimation=animator;else downAnimation=animator;
   rail.setOnTouchListener((v,e)->{if(e.getAction()==android.view.MotionEvent.ACTION_DOWN)animator.pause();
    else if(e.getAction()==android.view.MotionEvent.ACTION_UP||e.getAction()==android.view.MotionEvent.ACTION_CANCEL)animator.resume();
    return false;
   });animator.start();
  },250);
 }
 void mover(LinearLayout list,Market.Coin coin){
  String label=String.format(Locale.US,"%s  %+.1f%%",coin.symbol,coin.change);
  Button b=button(label,()->openBybitCoin(coin.symbol));
  b.setTextSize(13);b.setTextColor(gold);b.setBackground(shape(panel));b.setMinHeight(dp(42));
  android.text.SpannableString colored=new android.text.SpannableString(label);
  colored.setSpan(new android.text.style.ForegroundColorSpan(coin.change>=0?green:red),coin.symbol.length()+2,label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  b.setText(colored);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));p.setMargins(0,dp(4),dp(8),dp(6));list.addView(b,p);
 }
 double opportunityRank(Market.Coin c){return Math.abs(c.change)+Math.log10(1+c.turnover/1000000.0)*1.5;}
 void recommend(List<Market.Coin> all,int generation){
  java.util.ArrayList<Market.Coin> candidates=new java.util.ArrayList<>();
  for(Market.Coin c:all)if(c.turnover>=5000000&&Math.abs(c.change)>=.5&&Math.abs(c.change)<=15)candidates.add(c);
  candidates.sort((a,b)->Double.compare(opportunityRank(b),opportunityRank(a)));
  java.util.ArrayList<Market.Coin> chosen=new java.util.ArrayList<>();int rising=0,falling=0;
  for(Market.Coin c:candidates){if(chosen.size()==5)break;if(c.change>0&&rising>=3||c.change<0&&falling>=3)continue;
   chosen.add(c);if(c.change>0)rising++;else falling++;}
  for(Market.Coin c:candidates){if(chosen.size()==5)break;if(!chosen.contains(c))chosen.add(c);}
  runOnUiThread(()->{if(generation!=suggestionGeneration)return;suggestions.removeAllViews();
   if(chosen.isEmpty())line(suggestions,"ASTEAPTA. Nu exista suficiente piete cu miscare si rulaj verificate.");
   else line(suggestions,"Top dupa miscare si rulaj. Directia si confirmarea sunt lucruri diferite; verifica datele inainte de intrare.");});
  for(int i=0;i<chosen.size();i++){if(generation!=suggestionGeneration)return;recommendOne(chosen.get(i),i+1,generation);}
 }
 void recommendOne(Market.Coin coin,int position,int generation){
  String detail,side="";boolean confirmed=false;
  try{
   Market.Analysis a=Market.analyze(coin.symbol,coin);
   side=a.score>0?"LONG":a.score<0?"SHORT":a.sma20>a.sma50?"LONG":"SHORT";
   String common=String.format(Locale.US,"24h %+.1f%% | Rulaj %.1fM USDT | Scor %+d/5 | RSI 1h %.0f, 1D %.0f.",
    coin.change,coin.turnover/1000000,a.score,a.rsi,a.dailyRsi);
   if(Math.abs(a.score)>=3){
    try{
     MarketExtras.Plan plan=MarketExtras.inspect(this,coin.symbol,a,prefs.getFloat("capital",100),prefs.getFloat("risk",1),
      prefs.getFloat("tp",33),prefs.getFloat("sl",7),prefs.getFloat("lev",2));
     confirmed=plan.verdict.equals(side);
     detail=common+"\n"+(confirmed?"Conditii confirmate: "+plan.reason:
      "De urmarit, fara intrare confirmata: "+plan.reason+" "+plan.guard);
    }catch(Exception ex){detail=common+"\nFara confirmare: filtrul de risc nu s-a incarcat. "+a.reason;}
   }else detail=common+"\nDirectie preliminara: indicatorii 1h si 1D sunt amestecati; asteapta confirmarea. "+a.reason;
  }catch(Exception ex){detail="Date Bybit insuficiente: "+ex.getMessage()+". Fara directie verificata.";}
  final String body=detail,signal=side;final boolean ready=confirmed;
  runOnUiThread(()->{
   if(generation!=suggestionGeneration||isFinishing())return;
   LinearLayout item=vertical();item.setPadding(dp(12),dp(10),dp(12),dp(10));item.setBackground(shape(panel));
   LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(-1,-2);box.bottomMargin=dp(9);suggestions.addView(item,box);
   TextView title=text("#"+position+"  "+coin.symbol,19,Color.WHITE);title.setTypeface(null,android.graphics.Typeface.BOLD);add(item,title);
   TextView direction=text(signal.isEmpty()?"DATE INDISPONIBILE":signal+(ready?"  /  CONFIRMAT":"  /  DE URMARIT"),17,
    signal.equals("LONG")?green:signal.equals("SHORT")?red:muted);
   direction.setTypeface(null,android.graphics.Typeface.BOLD);add(item,direction);
   TextView explanation=text(body,14,muted);explanation.setPadding(0,dp(5),0,dp(8));add(item,explanation);
   Button go=button("Mergi la Coin  >  Bybit",()->openBybitCoin(coin.symbol));go.setTextSize(14);add(item,go);
  });
 }
 @Override protected void onDestroy(){
  dashboardTimerActive=false;dashboardTimer.removeCallbacks(dashboardPulse);if(liveStream!=null)liveStream.stop();unregisterReceiver(overlayEvents);if(upAnimation!=null)upAnimation.cancel();if(downAnimation!=null)downAnimation.cancel();super.onDestroy();}
 void simulate(boolean longSide){
  if(analysis==null||!symbol.equals(analysis.coin.symbol)){notice("Analizează moneda și actualizează prețul înainte de simulare.");return;}
  double amount=positive(amountInput,0),lev=positive(leverageInput,2),tp=positive(tpInput,33),sl=positive(slInput,7);
  if(amount<=0||amount>1000000||lev>100||sl>=100||tp>1000){notice("Introdu o marjă pozitivă și valori TP/SL/levier valide.");return;}
  double price=analysis.coin.price,sign=longSide?1:-1;
  String record=String.format(Locale.US,"%s  %s · intrare %s · marjă %.2f USDT · levier %.1f×\nTP %s · SL %s · %s",
   new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date()),longSide?"LONG":"SHORT",Market.number(price),amount,lev,
   Market.number(price*(1+sign*tp/100/lev)),Market.number(price*(1-sign*sl/100/lev)),symbol);
  double riskLimit=positive(capitalInput,100)*positive(riskInput,1)/100;
  RiskEngine.Result safety=RiskEngine.check(positive(capitalInput,100),positive(riskInput,1),amount,lev,sl,tp);
  try{double daily=TradeJournal.todayRealizedLoss(this),cap=positive(capitalInput,100);
   if(daily+safety.loss>cap*.025){notice("NO TRADE: limita zilnica Demo 2.5% depasita. Pierdere curenta "+daily+" USDT.");return;}
  }catch(Exception e){notice("Risc zilnic indisponibil: "+e.getMessage());return;}
  if(!safety.allowed){notice("NO TRADE - "+safety.message);return;}
  String riskWarning="\n"+safety.message;
  new AlertDialog.Builder(this).setTitle("Confirmare Demo").setMessage(record+riskWarning+"\n\nOrdinul este doar o simulare locală.").setNegativeButton("Anulează",null)
   .setPositiveButton("Salvează simularea",(d,w)->{
    String prior=prefs.getString("demoHistory","");
    try{TradeJournal.add(this,symbol,longSide,price,price*(1+sign*tp/100/lev),price*(1-sign*sl/100/lev),amount,lev);}catch(Exception ex){notice("Jurnalul nu s-a salvat: "+ex.getMessage());return;}
    prefs.edit().putString("demoHistory",record+"\n\n"+prior.substring(0,Math.min(prior.length(),10000))).apply();notice("Simularea Demo a fost salvată.");
   }).show();
 }
 void history(){
  new Thread(()->{try{String report=TradeJournal.report(this,true);runOnUiThread(()->notice(report));}catch(Exception ex){runOnUiThread(()->notice("Istoricul nu este disponibil: "+ex.getMessage()));}}).start();
 }
 void oldHistory(){String h=prefs.getString("demoHistory","");notice(h.isEmpty()?"Nu există tranzacții Demo.":h);}
}




