package ro.gs777.trading;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Locale;

final class AdvancedMarket {
 static final class Depth {double buySlip=Double.NaN,sellSlip=Double.NaN;String text="Lichiditate indisponibila";}
 static Depth depth(String symbol,double notional)throws Exception {
  JSONObject book=Market.get("orderbook?category=linear&symbol="+symbol+"&limit=50");
  JSONArray bids=book.getJSONArray("b"),asks=book.getJSONArray("a");
  Depth d=new Depth();
  d.buySlip=slip(asks,notional);d.sellSlip=slip(bids,notional);
  d.text=String.format(Locale.US,"Impact estimat la %.0f USDT: LONG %s, SHORT %s (50 niveluri, snapshot)",
   notional,Double.isFinite(d.buySlip)?String.format(Locale.US,"%.3f%%",d.buySlip):"adancime insuficienta",
   Double.isFinite(d.sellSlip)?String.format(Locale.US,"%.3f%%",d.sellSlip):"adancime insuficienta");
  return d;
 }
 static double slip(JSONArray levels,double notional)throws Exception{
  if(levels.length()==0||notional<=0)return Double.NaN;
  double top=Double.parseDouble(levels.getJSONArray(0).getString(0)),needed=notional/top,filled=0,spent=0;
  for(int i=0;i<levels.length()&&filled<needed;i++){
   JSONArray level=levels.getJSONArray(i);
   double price=Double.parseDouble(level.getString(0)),qty=Double.parseDouble(level.getString(1));
   if(price<=0||qty<=0)continue;double take=Math.min(qty,needed-filled);filled+=take;spent+=take*price;
  }
  if(filled<needed*.9999)return Double.NaN;
  return 100*Math.abs(spent/filled/top-1);
 }
 static String barriers(MarketExtras.Plan p,Market.Analysis a,Depth depth) {
  ArrayList<String> reasons=new ArrayList<>();
  if(p.spreadPct>.20)reasons.add("spread peste 0,20%");
  if(a.coin.turnover<1000000)reasons.add("rulaj 24h sub 1 milion USDT");
  if(Math.abs(a.coin.change)>15)reasons.add("miscare 24h peste 15%: risc de a urmari pretul");
  if(p.ratio<1.3)reasons.add("risc/castig sub 1,3 dupa costuri");
  double impact=p.verdict.equals("LONG")?depth.buySlip:depth.sellSlip;
  if(!p.verdict.equals("ASTEAPTA")&&(!Double.isFinite(impact)||impact>.25))reasons.add("lichiditate insuficienta la suma selectata");
  if(!p.verdict.equals("ASTEAPTA")&&((p.verdict.equals("LONG")&&p.fundingPct>.10)||(p.verdict.equals("SHORT")&&p.fundingPct<-.10)))
   reasons.add("funding nefavorabil directionei");
  if(!reasons.isEmpty())p.verdict="ASTEAPTA";
  return reasons.isEmpty()?"Filtre de risc: fara blocaje detectate.":"NU INTRA: "+android.text.TextUtils.join("; ",reasons)+".";
 }
 static final class Event {String title,url,when;long published;}
 static ArrayList<Event> announcements(String base)throws Exception {
  HttpURLConnection conn=(HttpURLConnection)new URL("https://api.bybit.com/v5/announcements/index?locale=en-US&limit=50").openConnection();
  conn.setConnectTimeout(7000);conn.setReadTimeout(8000);
  try {
   if(conn.getResponseCode()!=200)throw new Exception("Bybit HTTP "+conn.getResponseCode());
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=conn.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>1000000)throw new Exception("Raspuns mare");}}
   JSONObject root=new JSONObject(out.toString("UTF-8"));if(root.optInt("retCode",-1)!=0)throw new Exception(root.optString("retMsg"));
   ArrayList<Event> events=new ArrayList<>();JSONArray list=root.getJSONObject("result").getJSONArray("list");
   for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);String title=item.optString("title"),description=item.optString("description");
    if(!(title+" "+description).toUpperCase(Locale.ROOT).matches("(?s).*(?<![A-Z0-9])"+java.util.regex.Pattern.quote(base.toUpperCase(Locale.ROOT))+"(?![A-Z0-9]).*"))continue;
    String link=item.optString("url");if(!link.startsWith("https://"))continue;
    Event e=new Event();e.title=title;e.url=link;
    long stamp=item.optLong("publishTime",0);e.published=stamp;e.when=stamp>0?new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new java.util.Date(stamp)):"ora indisponibila";
    events.add(e);if(events.size()>=4)break;
   }
   return events;
  }finally{conn.disconnect();}
 }
}
