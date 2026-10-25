package ro.gs777.trading;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small local store of conditional scenarios. A touched price is never called a confirmed trade. */
final class PlanStore {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("gs777_plans",Context.MODE_PRIVATE);}
 static synchronized void save(Context context,ThreeDayPlan.Result plan)throws Exception{
  JSONArray old=new JSONArray(prefs(context).getString("items","[]")),out=new JSONArray();
  JSONObject p=new JSONObject();
  p.put("symbol",plan.symbol);p.put("side",plan.side);
  p.put("entry",plan.entry);p.put("stop",plan.stop);p.put("tp1",plan.tp1);p.put("tp2",plan.tp2);
  p.put("created",plan.created);p.put("expires",plan.expires);
  p.put("status",plan.side.equals("ASTEAPTA")?"ASTEAPTA":"IN ASTEPTARE");
  p.put("checked",0L);
  out.put(p);
  for(int i=0;i<Math.min(29,old.length());i++)out.put(old.getJSONObject(i));
  prefs(context).edit().putString("items",out.toString()).apply();
 }
 static synchronized JSONArray snapshot(Context c)throws Exception{return new JSONArray(prefs(c).getString("items","[]"));}
 static String state(JSONObject p,long now,double latest){
  if(now>=p.optLong("expires"))return "EXPIRAT";
  String side=p.optString("side"),previous=p.optString("status","IN ASTEPTARE");
  if(side.equals("ASTEAPTA"))return "ASTEAPTA";
  if(previous.equals("INVALIDAT"))return previous;
  double entry=p.optDouble("entry"),stop=p.optDouble("stop");
  if(latest<=0||entry<=0||stop<=0)return "NEVERIFICAT";
  if(side.equals("LONG")&&latest<=stop||side.equals("SHORT")&&latest>=stop)return "INVALIDAT";
  if(previous.equals("PRAG ATINS"))return previous;
  return side.equals("LONG")&&latest>=entry||side.equals("SHORT")&&latest<=entry
   ?"PRAG ATINS":"IN ASTEPTARE";
 }
 static synchronized String update(Context c,long created,double latest)throws Exception{
  JSONArray list=snapshot(c);
  for(int index=0;index<list.length();index++){
   JSONObject p=list.getJSONObject(index);
   if(p.optLong("created")!=created)continue;
   String prior=p.optString("status","IN ASTEPTARE"),next=state(p,System.currentTimeMillis(),latest);
   p.put("status",next);p.put("checked",System.currentTimeMillis());p.put("lastPrice",latest);
   prefs(c).edit().putString("items",list.toString()).apply();
   return prior.equals(next)?"":next;
  }
  return "";
 } static synchronized String latestId(Context c,String symbol,String side){
  try{
   JSONArray list=snapshot(c);long now=System.currentTimeMillis();
   for(int i=0;i<list.length();i++){
    JSONObject p=list.getJSONObject(i);
    if(symbol.equals(p.optString("symbol"))&&side.equals(p.optString("side"))
      &&p.optLong("created")<=now&&p.optLong("expires")>now)
     return Long.toString(p.optLong("created"));
   }
  }catch(Exception ignored){}
  return "";
 }
 static String display(Context c){
  try {
   JSONArray all=snapshot(c);if(all.length()==0)return "Nu exista planuri salvate.";
   StringBuilder text=new StringBuilder();
   SimpleDateFormat date=new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault());
   long now=System.currentTimeMillis();
   for(int i=0;i<Math.min(8,all.length());i++){
    JSONObject p=all.getJSONObject(i);
    String state=now>=p.optLong("expires")?"EXPIRAT":p.optString("status","IN ASTEPTARE");
    text.append(p.optString("symbol")).append(" / ").append(p.optString("side"))
     .append(" / ").append(state).append("\n");
    text.append("Prag ").append(ThreeDayPlan.price(p.optDouble("entry")))
     .append(" | SL ").append(ThreeDayPlan.price(p.optDouble("stop")))
     .append(" | TP1 ").append(ThreeDayPlan.price(p.optDouble("tp1")))
     .append(" | TP2 ").append(ThreeDayPlan.price(p.optDouble("tp2"))).append("\n");
    text.append("Expira ").append(date.format(new Date(p.optLong("expires"))));
    if(p.optLong("checked")>0)text.append(" | verificat ").append(date.format(new Date(p.optLong("checked"))));
    text.append("\n\n");
   }
   text.append("PRAG ATINS cere inchidere 4h si retest manual; nu confirma automat o intrare.");
   return text.toString();
  }catch(Exception ex){return "Planurile nu au putut fi citite: "+ex.getMessage();}
 }
}
