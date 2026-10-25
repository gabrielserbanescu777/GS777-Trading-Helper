package ro.gs777.trading;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

final class MarketMood {
 static final class Snapshot {
  String trend="ASTEAPTA",text="";
  double change24,change1,funding,buyRatio=-1,oiDelta=Double.NaN;
 }
 static Snapshot get(String symbol)throws Exception {
  Snapshot m=new Snapshot();
  JSONObject ticker=Market.get("tickers?category=linear&symbol="+symbol).getJSONArray("list").getJSONObject(0);
  double last=MarketExtras.parse(ticker,"lastPrice"),prev1=MarketExtras.parse(ticker,"prevPrice1h");
  if(last<=0)throw new Exception("Pret Bybit indisponibil");
  m.change24=100*MarketExtras.parse(ticker,"price24hPcnt");
  m.change1=prev1>0?100*(last/prev1-1):Double.NaN;
  m.funding=100*MarketExtras.parse(ticker,"fundingRate");
  // Market movement describes what happened; it is not a prediction or a vote from people.
  if(Double.isFinite(m.change1)&&m.change24>=1&&m.change1>=.25)m.trend="LONG";
  else if(Double.isFinite(m.change1)&&m.change24<=-1&&m.change1<=-.25)m.trend="SHORT";
  try {
   JSONArray ratios=Market.get("account-ratio?category=linear&symbol="+symbol+"&period=1h&limit=1").getJSONArray("list");
   if(ratios.length()>0)m.buyRatio=100*MarketExtras.parse(ratios.getJSONObject(0),"buyRatio");
  }catch(Exception ignored){}
  try {
   JSONArray oi=Market.get("open-interest?category=linear&symbol="+symbol+"&intervalTime=1h&limit=2").getJSONArray("list");
   if(oi.length()>=2){double a=MarketExtras.parse(oi.getJSONObject(0),"openInterest"),b=MarketExtras.parse(oi.getJSONObject(1),"openInterest");
    if(b>0)m.oiDelta=100*(a/b-1);}
  }catch(Exception ignored){}
  m.text=String.format(Locale.US,"TREND BYBIT: %s | 24h %+.2f%% | 1h %s | funding %+.4f%%",
   m.trend,m.change24,Double.isFinite(m.change1)?String.format(Locale.US,"%+.2f%%",m.change1):"n/a",m.funding);
  if(m.buyRatio>=0)m.text+=String.format(Locale.US," | conturi LONG %.0f%%",m.buyRatio);
  if(Double.isFinite(m.oiDelta))m.text+=String.format(Locale.US," | OI 1h %+.1f%%",m.oiDelta);
  m.text+=". Miscarea trecuta si numarul conturilor nu garanteaza directia.";
  return m;
 }
 static String combine(SocialFeed.Opinion people,Snapshot market){
  if(market==null||people.relevant<3||people.forums<2||people.positive+people.negative<3)return "ASTEAPTA";
  if(people.direction.equals(market.trend)&&((people.direction.equals("LONG")&&people.forumPositive>people.forumNegative)||(people.direction.equals("SHORT")&&people.forumNegative>people.forumPositive)))return people.direction;
  return "ASTEAPTA";
 }
}
