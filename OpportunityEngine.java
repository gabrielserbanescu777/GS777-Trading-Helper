package ro.gs777.trading;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
/** Read-only, transparent heuristic; score is not a calibrated probability. */
final class OpportunityEngine {
 static final class Score {int longScore,shortScore;String verdict,details;double funding,oiChange,imbalance,spreadBps;boolean sufficient;}
 static double val(JSONObject j,String k){try{return Double.parseDouble(j.optString(k,"NaN"));}catch(Exception e){return Double.NaN;}}
 static int clamp(double x){return (int)Math.max(0,Math.min(100,Math.round(x)));}
 static Score inspect(Market.Analysis a)throws Exception {
  String sym=a.coin.symbol;
  JSONObject ticker=Market.get("tickers?category=linear&symbol="+sym).getJSONArray("list").getJSONObject(0);
  double funding=val(ticker,"fundingRate"),spread=Double.NaN,imb=Double.NaN,oi=Double.NaN;
  double bid=val(ticker,"bid1Price"),ask=val(ticker,"ask1Price");
  if(bid>0&&ask>bid)spread=10000*(ask-bid)/((bid+ask)/2);
  try {JSONArray o=Market.get("open-interest?category=linear&symbol="+sym+"&intervalTime=1h&limit=2").getJSONArray("list");
   if(o.length()>=2){double now=val(o.getJSONObject(0),"openInterest"),prev=val(o.getJSONObject(1),"openInterest");if(prev>0)oi=100*(now/prev-1);}
  }catch(Exception ignored){}
  try {JSONObject b=Market.get("orderbook?category=linear&symbol="+sym+"&limit=50");double buys=0,sells=0;
   for(int i=0;i<b.getJSONArray("b").length();i++){JSONArray x=b.getJSONArray("b").getJSONArray(i);buys+=x.getDouble(0)*x.getDouble(1);}
   for(int i=0;i<b.getJSONArray("a").length();i++){JSONArray x=b.getJSONArray("a").getJSONArray(i);sells+=x.getDouble(0)*x.getDouble(1);}
   if(buys+sells>0)imb=(buys-sells)/(buys+sells);
  }catch(Exception ignored){}
  Score s=new Score();s.funding=funding;s.oiChange=oi;s.imbalance=imb;s.spreadBps=spread;
  double trend=a.score*5;double momentum=Math.max(-12,Math.min(12,a.coin.change/3));
  double flow=Double.isFinite(imb)?imb*10:0;
  double interest=Double.isFinite(oi)?Math.max(-8,Math.min(8,oi)):0;
  double crowd=Double.isFinite(funding)?Math.max(-5,Math.min(5,funding*10000)):0;
  double penalty=(a.coin.turnover<1000000?20:0)+(Double.isFinite(spread)&&spread>20?20:0);
  s.longScore=clamp(50+trend+momentum+flow+interest-crowd-penalty);
  s.shortScore=clamp(50-trend-momentum-flow+interest+crowd-penalty);
  s.sufficient=a.coin.turnover>=1000000&&Double.isFinite(spread)&&spread<=20&&Double.isFinite(oi)&&Double.isFinite(funding)&&Double.isFinite(imb);
  int best=Math.max(s.longScore,s.shortScore);
  s.verdict=!s.sufficient?"NO TRADE (date/lichiditate insuficiente)":best>=85?(s.longScore>s.shortScore?"LONG - de verificat":"SHORT - de verificat"):best>=75?"WATCH - asteapta confirmare":"NO TRADE";
  s.details=String.format(Locale.US,"LONG %d/100 | SHORT %d/100 | %s\n24h %+.2f%% | turnover %.0f USDT | OI 1h %s | funding %s | orderbook %s | spread %s bps\nScor euristic; NU este probabilitate de castig. Datele lipsa blocheaza confirmarea.",s.longScore,s.shortScore,s.verdict,a.coin.change,a.coin.turnover,fmt(oi,"%%"),fmt(funding*100,"%%"),fmt(imb*100,"%%"),fmt(spread,""));
  return s;
 }
 static String fmt(double d,String suffix){return Double.isFinite(d)?String.format(Locale.US,"%+.2f%s",d,suffix):"indisponibil";}
 static String scan(List<Market.Coin> coins){
  ArrayList<Market.Coin> liquid=new ArrayList<>();for(Market.Coin c:coins)if(c.turnover>=1000000&&Double.isFinite(c.change))liquid.add(c);
  liquid.sort((a,b)->Double.compare(Math.abs(b.change),Math.abs(a.change)));
  StringBuilder sb=new StringBuilder("RADAR DE MISCARE (nu semnale de tranzactionare)\nFiltru: turnover >= 1M USDT / 24h. +33% si -33% declanseaza analiza, NU ordine.\n");
  int shown=0;for(Market.Coin c:liquid){if(shown>=12)break;if(Math.abs(c.change)<15)continue;
   sb.append(String.format(Locale.US,"\n%s %+.2f%% | turnover %.1fM | %s",c.symbol,c.change,c.turnover/1000000,Math.abs(c.change)>=33?"ANALIZEAZA":"WATCH"));shown++;}
  if(shown==0)sb.append("\nNu exista miscari peste 15% in perechile lichide.");return sb.toString();
 }
}
