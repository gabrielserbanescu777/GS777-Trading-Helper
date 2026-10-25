package ro.gs777.trading;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.List;
import java.util.Locale;

public class AlertsJob extends JobService {
 static final int JOB_ID=777204;
 static void schedule(Context context){
  try {
   JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
   if(scheduler==null)return;
   JobInfo task=new JobInfo.Builder(JOB_ID,new ComponentName(context,AlertsJob.class))
    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
    .setPersisted(true)
    .setMinimumLatency(30L*60*1000)
    .setOverrideDeadline(60L*60*1000).build();
   scheduler.schedule(task);
  }catch(Exception ignored){}
 }
 void notifyOnce(String key,String title,String message,long now,SharedPreferences state,int id) {
  if(now-state.getLong(key,0)<(key.startsWith("move_")?24L:key.startsWith("event_")?168L:6L)*60*60*1000)return;
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  Intent intent=new Intent(this,MainActivity.class);
  PendingIntent open=PendingIntent.getActivity(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"movers"):new Notification.Builder(this);
  b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message)
   .setStyle(new Notification.BigTextStyle().bigText(message)).setAutoCancel(true).setContentIntent(open);
  NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(manager!=null){manager.notify(id,b.build());state.edit().putLong(key,now).apply();}
 }
 static int noticeId(String key){return 1000+(key.hashCode()&0x7fffffff)%100000;}
 static double rank(Market.Coin c){return Math.abs(c.change)+Math.log10(1+c.turnover/1000000.0)*1.5;}
 static java.util.ArrayList<Market.Coin> topFive(List<Market.Coin> all){
  java.util.ArrayList<Market.Coin> eligible=new java.util.ArrayList<>(),chosen=new java.util.ArrayList<>();
  for(Market.Coin c:all)if(c.turnover>=5000000&&Math.abs(c.change)>=.5&&Math.abs(c.change)<=15)eligible.add(c);
  eligible.sort((a,b)->Double.compare(rank(b),rank(a)));
  int up=0,down=0;
  for(Market.Coin c:eligible){if(chosen.size()==5)break;if(c.change>0&&up>=3||c.change<0&&down>=3)continue;
   chosen.add(c);if(c.change>0)up++;else down++;}
  for(Market.Coin c:eligible){if(chosen.size()==5)break;if(!chosen.contains(c))chosen.add(c);}
  return chosen;
 }
 void checkTips(List<Market.Coin> all,SharedPreferences state,long now){
  java.util.ArrayList<Market.Coin> top=topFive(all);StringBuilder list=new StringBuilder(",");
  for(Market.Coin coin:top)list.append(coin.symbol).append(",");
  String previous=state.getString("lastTips","");
  if(!previous.isEmpty()){
   int sent=0;
   for(Market.Coin coin:top){
    if(sent>=2)break;
    if(previous.contains(","+coin.symbol+","))continue;
    try{
     Market.Analysis a=Market.analyze(coin.symbol,coin);
     String direction=a.score>0?"LONG":a.score<0?"SHORT":a.sma20>a.sma50?"LONG":"SHORT";
     String status=Math.abs(a.score)>=3?"indicatori favorabili, confirma retestul":"directie preliminara; asteapta confirmarea";
     String msg=String.format(Locale.US,"%s: %s / %s. 24h %+.1f%%, scor %+d/5. Nu este ordin automat.",
      coin.symbol,direction,status,coin.change,a.score);
     notifyOnce("tip_"+coin.symbol,"Pont nou GS777: "+coin.symbol,msg,now,state,noticeId("tip_"+coin.symbol));sent++;
    }catch(Exception ignored){}
   }
  }
  state.edit().putString("lastTips",list.toString()).apply();
 }
 void checkChannels(String channels,String watch,SharedPreferences state,long now){
  for(String channel:channels.split(",")){
   if(!channel.matches("UC[A-Za-z0-9_-]{22}"))continue;
   java.net.HttpURLConnection connection=null;
   try{
    connection=(java.net.HttpURLConnection)new java.net.URL("https://www.youtube.com/feeds/videos.xml?channel_id="+channel).openConnection();
    connection.setConnectTimeout(6000);connection.setReadTimeout(7000);
    if(connection.getResponseCode()!=200)continue;
    org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();parser.setInput(connection.getInputStream(),"UTF-8");
    java.util.ArrayList<String[]> videos=new java.util.ArrayList<>();
    String id="",title="",published="",author="",tag="";boolean insideEntry=false;
    for(int type=parser.getEventType();type!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT&&videos.size()<12;type=parser.next()){
     if(type==org.xmlpull.v1.XmlPullParser.START_TAG){tag=parser.getName();
      if(tag.equals("entry")){insideEntry=true;id="";title="";published="";author="";}
     }else if(type==org.xmlpull.v1.XmlPullParser.TEXT){
      String value=parser.getText().trim();
      if(tag.equals("videoId"))id=value;
      else if(tag.equals("title")&&insideEntry)title=value;
      else if(tag.equals("published"))published=value;
      else if(tag.equals("name"))author=value;
     }else if(type==org.xmlpull.v1.XmlPullParser.END_TAG){
      if(parser.getName().equals("entry")){insideEntry=false;if(!id.isEmpty())videos.add(new String[]{id,title,published,author});}
      tag="";
     }
    }
    if(videos.isEmpty())continue;
    String key="lastVideo_"+channel,prior=state.getString(key,"");
    if(!prior.isEmpty()){
     int sent=0;
     for(String[] video:videos){
      if(video[0].equals(prior)||sent>=2)break;
      long publishedAt=0;try{publishedAt=java.time.Instant.parse(video[2]).toEpochMilli();}catch(Exception ignored){}
      if(publishedAt<=0||now-publishedAt>48L*60*60*1000)continue;
      String match=matchedCoin(video[1],watch);
      if(match.isEmpty())continue;
      String label=video[3].isEmpty()?"Canal YouTube":video[3];
      notifyOnce("creator_"+video[0],"YouTube: "+match+" / "+label,
       video[1]+" | Titlu nou, opinie neverificata. Verifica sursa in YouTube.",now,state,noticeId("creator_"+video[0]));sent++;
     }
    }
    state.edit().putString(key,videos.get(0)[0]).apply();
   }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
  }
 }
 static String matchedCoin(String title,String watch){
  String words=title.toUpperCase(Locale.ROOT);
  for(String symbol:watch.split(",")){
   if(!symbol.matches("[A-Z0-9]{2,22}USDT"))continue;
   String base=symbol.substring(0,symbol.length()-4);
   String alias=base.equals("BTC")?"BITCOIN":base.equals("ETH")?"ETHEREUM":base.equals("SOL")?"SOLANA":base.equals("XRP")?"RIPPLE":"";
   if(java.util.regex.Pattern.compile("(?<![A-Z0-9])"+java.util.regex.Pattern.quote(base)+"(?![A-Z0-9])").matcher(words).find()||
      !alias.isEmpty()&&words.contains(alias))return symbol;
  }
  return "";
 }
 void checkSavedPlans(SharedPreferences state,long now){
  try{
   org.json.JSONArray plans=PlanStore.snapshot(this);
   int checked=0;java.util.HashMap<String,Double> prices=new java.util.HashMap<>();
   for(int i=0;i<plans.length()&&checked<5;i++){
    org.json.JSONObject item=plans.getJSONObject(i);
    long created=item.optLong("created");String coin=item.optString("symbol");
    if(created<=0||coin.isEmpty()||"ASTEAPTA".equals(item.optString("side")))continue;
    if(item.optLong("expires")<=now){PlanStore.update(this,created,0);continue;}
    try{
     Double price=prices.get(coin);
     if(price==null){price=Market.ticker(coin).price;prices.put(coin,price);}
     String transition=PlanStore.update(this,created,price);checked++;
     if(transition.equals("PRAG ATINS")||transition.equals("INVALIDAT")){
      String message=transition.equals("PRAG ATINS")
       ?"Pragul a fost atins; verifica inchiderea 4h si retestul. Nu este intrare confirmata."
       :"Pretul a traversat nivelul de invalidare. Recalculeaza planul.";
      notifyOnce("plan_"+created+"_"+transition,"Plan "+coin+": "+transition,message,now,state,noticeId("plan_"+created+"_"+transition));
     }
    }catch(Exception ignored){}
   }
  }catch(Exception ex){state.edit().putString("lastPlanError",ex.getClass().getSimpleName()).apply();}
 } @Override public boolean onStartJob(JobParameters params){
  new Thread(()->{
   try {
    SharedPreferences config=getSharedPreferences("gs777",MODE_PRIVATE),state=getSharedPreferences("gs777_alerts",MODE_PRIVATE);
    long now=System.currentTimeMillis();
    boolean rise=config.getBoolean("alertRise",true),fall=config.getBoolean("alertFall",true),tips=config.getBoolean("alertTips",true);
    if(config.getBoolean("alertPlan",true))checkSavedPlans(state,now);
    if(rise||fall||tips){
     try{
      List<Market.Coin> coins=Market.movers();
      state.edit().remove("lastMarketError").putLong("lastMarketSuccess",now).apply();
      if(rise||fall){int sentRise=0,sentFall=0;double threshold=config.getFloat("alertThreshold",33);
       for(Market.Coin coin:coins){
        if(sentRise>=3&&sentFall>=3)break;
        if(Math.abs(coin.change)<threshold||coin.change>=0&&(!rise||sentRise>=3)||coin.change<0&&(!fall||sentFall>=3))continue;
        String text=String.format(Locale.US,"%s %+.1f%%/24h | pret %s USDT. Miscare trecuta: verifica trendul si riscul.",
         coin.symbol,coin.change,Market.number(coin.price));
        notifyOnce("move_"+coin.symbol,"Bybit: "+(coin.change>=0?"crestere":"scadere"),text,now,state,noticeId("move_"+coin.symbol));if(coin.change>=0)sentRise++;else sentFall++;
       }
      }
      if(tips)checkTips(coins,state,now);
     }catch(Exception error){state.edit().putString("lastMarketError",error.getClass().getSimpleName()+": "+error.getMessage()).apply();}
    }
    String watch=config.getString("watchlist","BTCUSDT,SOLUSDT");
    int n=0;for(String raw:watch.split(",")){
     String symbol=raw.trim();if(!symbol.matches("[A-Z0-9]{2,22}USDT")||n++>=4)continue;
     boolean price=config.getBoolean("alertPrice",true),setup=config.getBoolean("alertSetup",true),
      funding=config.getBoolean("alertFunding",false),interest=config.getBoolean("alertInterest",false);
     if(!price&&!setup&&!funding&&!interest)continue;
     try{
      Market.Coin coin=Market.ticker(symbol);
      if(price){
       double target=config.getFloat("priceAlert",0);
       double previous=Double.longBitsToDouble(state.getLong("previousPrice_"+symbol,Double.doubleToRawLongBits(0)));
       if(symbol.equals(config.getString("alertSymbol",""))&&target>0&&previous>0&&((previous<target&&coin.price>=target)||(previous>target&&coin.price<=target)))
        notifyOnce("target_"+symbol,"Prag atins: "+symbol,String.format(Locale.US,"Pretul %s USDT a trecut prin %.6f USDT",Market.number(coin.price),target),now,state,noticeId("target_"+symbol));
       state.edit().putLong("previousPrice_"+symbol,Double.doubleToRawLongBits(coin.price)).apply();
      }
      if(setup){
       Market.Analysis a=Market.analyze(symbol,coin);
       String current="ASTEAPTA",prior=state.getString("verdict_"+symbol,"");
       if(Math.abs(a.score)>=3)try{
        MarketExtras.Plan plan=MarketExtras.inspect(this,symbol,a,config.getFloat("capital",100),config.getFloat("risk",1),
         config.getFloat("tp",33),config.getFloat("sl",7),config.getFloat("lev",2));
        current=plan.verdict;
        if(!current.equals("ASTEAPTA")&&!current.equals(prior))
         notifyOnce("confirmed_"+symbol+current,"Semnal confirmat: "+symbol,
          current+" | "+plan.reason+" | Risc "+String.format(Locale.US,"%.2f USDT",plan.estimatedLoss),now,state,noticeId("confirmed_"+symbol));
       }catch(Exception ignored){}
       state.edit().putString("verdict_"+symbol,current).apply();
      }
      if(funding||interest){
       org.json.JSONObject quote=Market.get("tickers?category=linear&symbol="+symbol).getJSONArray("list").getJSONObject(0);
       if(funding){
        double rate=Math.abs(Double.parseDouble(quote.optString("fundingRate","0")))*100;
        if(rate>=.1)notifyOnce("funding_"+symbol,"Funding ridicat: "+symbol,
         String.format(Locale.US,"Funding %.3f%%. Costurile si directia se verifica pe Bybit.",100*Double.parseDouble(quote.optString("fundingRate","0"))),now,state,noticeId("funding_"+symbol));
       }
       if(interest){
        double oi=Double.parseDouble(quote.optString("openInterestValue","0"));
        double prev=Double.longBitsToDouble(state.getLong("previousOI_"+symbol,Double.doubleToRawLongBits(0)));
        if(prev>0&&oi>prev*1.15)notifyOnce("interest_"+symbol,"Open interest +15%: "+symbol,
         String.format(Locale.US,"OI %.0f fata de %.0f USDT la verificarea trecuta; directia nu este dedusa automat.",oi,prev),now,state,noticeId("interest_"+symbol));
        if(oi>0)state.edit().putLong("previousOI_"+symbol,Double.doubleToRawLongBits(oi)).apply();
       }
      }
     }catch(Exception ignored){}
    }
    if(config.getBoolean("alertPositions",true)&&ReadOnlyBybit.connected(this))try{
     String warnings=ReadOnlyBybit.positionWarnings(this);
     if(!warnings.isEmpty())notifyOnce("position_warning","GS777: verifica pozitiile",warnings,now,state,830);
    }catch(Exception ignored){}
    if(config.getBoolean("alertEvents",true)&&now-state.getLong("lastAnnouncementCheck",0)>6L*60*60*1000){
     int checkedEvents=0;for(String raw:watch.split(",")){
      if(checkedEvents++>=4)break;
      String sym=raw.trim();if(!sym.matches("[A-Z0-9]{2,22}USDT"))continue;
      try{String base=sym.substring(0,sym.length()-4);
       for(AdvancedMarket.Event event:AdvancedMarket.announcements(base)){
        if(event.published<=0||now-event.published>24L*60*60*1000)continue;
        notifyOnce("event_"+event.title.hashCode(),"Bybit: eveniment "+base,event.title,now,state,noticeId("event_"+event.title));
       }
      }catch(Exception ignored){}
     }
     state.edit().putLong("lastAnnouncementCheck",now).apply();
    }
    if(config.getBoolean("alertCreator",true)){
     String channels=YoutubeChannels.selected(config);
     if(!channels.isEmpty()){
      String[] ids=channels.split(",");
      int cursor=Math.floorMod(state.getInt("creatorCursor",0),ids.length);
      StringBuilder batch=new StringBuilder();
      for(int i=0;i<Math.min(8,ids.length);i++){if(batch.length()>0)batch.append(',');batch.append(ids[(cursor+i)%ids.length]);}
      state.edit().putInt("creatorCursor",(cursor+Math.min(8,ids.length))%ids.length).apply();
      checkChannels(batch.toString(),watch,state,now);
     }
    }
    if(config.getBoolean("alertJournal",true)&&now-state.getLong("lastJournalCheck",0)>2L*60*60*1000&&!getSharedPreferences("gs777_journal",MODE_PRIVATE).getString("entries","[]").equals("[]"))try{
     TradeJournal.report(this,true);state.edit().putLong("lastJournalCheck",now).apply();
    }catch(Exception ignored){}
    state.edit().putLong("lastCheck",now).apply();
   }catch(Exception ignored){}finally{jobFinished(params,false);schedule(this);}
  }).start();
  return true;
 }
 @Override public boolean onStopJob(JobParameters params){return true;}
}
