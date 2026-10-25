package ro.gs777.trading;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

final class TradeJournal {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("gs777_journal",Context.MODE_PRIVATE);}
 static synchronized void add(Context c,String symbol,boolean buy,double entry,double tp,double sl,double margin,double leverage)throws Exception {
  SharedPreferences store=prefs(c);JSONArray old=new JSONArray(store.getString("entries","[]"));JSONArray out=new JSONArray();
  JSONObject j=new JSONObject();j.put("symbol",symbol);j.put("side",buy?"LONG":"SHORT");j.put("entry",entry);j.put("tp",tp);j.put("sl",sl);j.put("margin",margin);j.put("leverage",leverage);j.put("at",System.currentTimeMillis());j.put("status","DESCHIS");String related=PlanStore.latestId(c,symbol,buy?"LONG":"SHORT");if(!related.isEmpty())j.put("relatedPlan",related);
  out.put(j);for(int i=0;i<Math.min(99,old.length());i++)out.put(old.getJSONObject(i));store.edit().putString("entries",out.toString()).apply();
 }
 static synchronized String report(Context c,boolean update)throws Exception {
  SharedPreferences store=prefs(c);JSONArray entries=new JSONArray(store.getString("entries","[]"));
  int wins=0,losses=0,open=0,unknown=0;
  StringBuilder details=new StringBuilder();
  java.util.HashMap<String,int[]> perPair=new java.util.HashMap<>();
  int checked=0;
  for(int i=0;i<entries.length();i++){
   JSONObject j=entries.getJSONObject(i);String status=j.optString("status","DESCHIS");
   if(update&&status.equals("DESCHIS")&&checked<5){checked++;status=resolve(j);j.put("status",status);if("TP".equals(status)||"SL".equals(status))j.put("resolvedAt",System.currentTimeMillis());}
   if(status.equals("TP"))wins++;else if(status.equals("SL"))losses++;
   if(status.equals("TP")||status.equals("SL")){String group=j.optString("symbol")+" "+j.optString("side");int[] counts=perPair.get(group);if(counts==null){counts=new int[2];perPair.put(group,counts);}counts[status.equals("TP")?0:1]++;}else if(status.equals("DESCHIS"))open++;else unknown++;
   if(i<16)details.append(String.format(Locale.US,"\n%s %s @%s -> %s",j.getString("symbol"),j.getString("side"),Market.number(j.getDouble("entry")),status+(j.has("relatedPlan")?" / plan "+new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new java.util.Date(j.optLong("relatedPlan"))):"")));
  }
  if(update)store.edit().putString("entries",entries.toString()).apply();
  String rate=(wins+losses)>=20?String.format(Locale.US,"Rata Demo: %.1f%% din %d rezultate; fara garantie viitoare.",100.0*wins/(wins+losses),wins+losses):"Rata Demo se afiseaza dupa 20 rezultate clare.";
  StringBuilder breakdown=new StringBuilder();
  for(java.util.Map.Entry<String,int[]> pair:perPair.entrySet()){int[] count=pair.getValue();int total=count[0]+count[1];
   breakdown.append("\n").append(pair.getKey()).append(": ").append(total>=20?String.format(Locale.US,"%.1f%% Demo (%d)",100.0*count[0]/total,total):total+" rezultate (esantion mic)");}
  return String.format(Locale.US,"%s\nTP: %d | SL: %d | deschise: %d | nedeterminate: %d.%s%s",rate,wins,losses,open,unknown,breakdown,details);
 }
 static synchronized String performance(Context c)throws Exception {
  JSONArray entries=new JSONArray(prefs(c).getString("entries","[]"));
  int wins=0,losses=0,unresolved=0;double grossWin=0,grossLoss=0,net=0,peak=0,drawdown=0;
  for(int i=entries.length()-1;i>=0;i--){JSONObject e=entries.getJSONObject(i);String status=e.optString("status");
   double margin=e.optDouble("margin",0),lev=e.optDouble("leverage",1),entry=e.optDouble("entry",0),tp=e.optDouble("tp",0),sl=e.optDouble("sl",0);
   if(margin<=0||lev<=0||entry<=0){unresolved++;continue;}
   boolean buy="LONG".equals(e.optString("side"));double target="TP".equals(status)?tp:"SL".equals(status)?sl:0;
   if(target<=0){unresolved++;continue;}
   double pnl=margin*lev*(buy?(target/entry-1):(1-target/entry)) - margin*lev*.0012;
   if(pnl>=0){wins++;grossWin+=pnl;}else{losses++;grossLoss-=pnl;}
   net+=pnl;peak=Math.max(peak,net);drawdown=Math.max(drawdown,peak-net);
  }
  int n=wins+losses;
  return String.format(Locale.US,"JURNAL DEMO (numai rezultate TP/SL)\nRezolvate %d | castig %d | pierderi %d | nerezolvate %d\nWin rate %s | Profit factor %s | P&L net estimat %+.2f USDT | Max drawdown realizat %.2f USDT\nComisioane estimate 0.12%% notional round-trip; funding si slippage real nu sunt incluse. Trade-uri ambigue/expirate nu sunt considerate castiguri.",n,wins,losses,unresolved,n>=20?String.format(Locale.US,"%.1f%%",100.0*wins/n):"esantion mic",grossLoss>0?String.format(Locale.US,"%.2f",grossWin/grossLoss):"n/a",net,drawdown);
 }
 static synchronized double todayRealizedLoss(Context c)throws Exception {
  JSONArray entries=new JSONArray(prefs(c).getString("entries","[]"));
  java.util.Calendar day=java.util.Calendar.getInstance();day.set(java.util.Calendar.HOUR_OF_DAY,0);day.set(java.util.Calendar.MINUTE,0);day.set(java.util.Calendar.SECOND,0);day.set(java.util.Calendar.MILLISECOND,0);
  double loss=0;for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);
   // Use resolution time if recorded; legacy entries have no resolution timestamp and must not be treated as today's realized loss.
   if(!"SL".equals(e.optString("status"))||e.optLong("resolvedAt",0)<day.getTimeInMillis())continue;
   double margin=e.optDouble("margin",0),lev=e.optDouble("leverage",1),entry=e.optDouble("entry",0),sl=e.optDouble("sl",0);
   if(entry>0&&margin>0)loss+=Math.max(0,margin*lev*Math.abs(sl/entry-1)+margin*lev*.0012);
  }return loss;
 }
 static final class Rate {int percent,samples;Rate(int p,int n){percent=p;samples=n;}}
 static synchronized Rate rate(Context context,String symbol,String side){
  try{
   JSONArray entries=new JSONArray(prefs(context).getString("entries","[]"));
   int wins=0,losses=0;
   for(int i=0;i<entries.length();i++){
    JSONObject j=entries.getJSONObject(i);
    if(!symbol.equals(j.optString("symbol"))||!side.equals(j.optString("side")))continue;
    if("TP".equals(j.optString("status")))wins++;
    if("SL".equals(j.optString("status")))losses++;
   }
   int n=wins+losses;
   return n>=20?new Rate((int)Math.round(100.0*wins/n),n):null;
  }catch(Exception ignored){return null;}
 }
 static String resolve(JSONObject j){
  try {
   long at=j.getLong("at"),now=System.currentTimeMillis();
   if(now-at<5*60*1000)return "DESCHIS";
   long candle=5L*60*1000,first=((at/candle)+1)*candle,deadline=at+24L*60*60*1000;
   long until=Math.min(now,deadline);
   JSONArray bars=Market.get("kline?category=linear&symbol="+j.getString("symbol")+"&interval=5&start="+first+"&end="+until+"&limit=300").getJSONArray("list");
   if(bars.length()<1)return now>=deadline?"EXPIRAT":"DESCHIS";
   long oldest=Long.parseLong(bars.getJSONArray(bars.length()-1).getString(0));
   if(oldest>first)return "NEDTERMINAT";
   long newest=Long.parseLong(bars.getJSONArray(0).getString(0));
   if(now>=deadline&&newest<deadline-2*candle)return "NEDTERMINAT";
   boolean buy=j.getString("side").equals("LONG");double tp=j.getDouble("tp"),sl=j.getDouble("sl");
   for(int k=bars.length()-1;k>=0;k--){
    JSONArray b=bars.getJSONArray(k);long t=Long.parseLong(b.getString(0));if(t<first||t>=deadline||t+candle>now)continue;
    double hi=b.getDouble(2),lo=b.getDouble(3);
    boolean take=buy?hi>=tp:lo<=tp,stop=buy?lo<=sl:hi>=sl;
    if(take&&stop)return "AMBIGUU";
    if(take)return "TP";if(stop)return "SL";
   }
   return "DESCHIS";
  }catch(Exception ignored){return "DESCHIS";}
 }
}
