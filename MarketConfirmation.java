package ro.gs777.trading;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
/** Read-only confirmation gate. Missing data always means NO TRADE. */
final class MarketConfirmation {
 static final class Result {
  final boolean valid; final String status,details;
  Result(boolean v,String s,String d){valid=v;status=s;details=d;}
 }
 static double number(JSONObject o,String key){try{return Double.parseDouble(o.optString(key,"NaN"));}catch(Exception e){return Double.NaN;}}
 static Result inspect(String symbol,String side,SetupConfirmation.Result setup)throws Exception{
  if(setup==null||!"RETEST CONFIRMAT".equals(setup.state))return new Result(false,"NO TRADE","Retest 4H neconfirmat.");
  if(!"LONG".equals(side)&&!"SHORT".equals(side))return new Result(false,"NO TRADE","Directie lipsa.");
  // Market.candles returns chronological OHLC; volume is not part of that shape.
  // Obtain 4H volume directly and discard the still-forming latest candle.
  JSONObject response=Market.get("kline?category=linear&symbol="+symbol+"&interval=240&limit=30");
  JSONArray rows=response.getJSONArray("list");
  if(rows.length()<23)return new Result(false,"NO TRADE","Istoric de volum insuficient.");
  double latest=Double.NaN,total=0;int count=0;
  for(int i=1;i<rows.length()&&i<=21;i++){
   JSONArray row=rows.getJSONArray(i);double volume=Double.parseDouble(row.getString(5));
   if(i==1)latest=volume;
   else if(volume>=0&&Double.isFinite(volume)){total+=volume;count++;}
  }
  double ratio=count==20&&total>0?latest/(total/count):Double.NaN;
  JSONObject ticker=Market.get("tickers?category=linear&symbol="+symbol).getJSONArray("list").getJSONObject(0);
  double funding=number(ticker,"fundingRate");
  double bid=number(ticker,"bid1Price"),ask=number(ticker,"ask1Price");
  double spread=bid>0&&ask>=bid?10000*(ask-bid)/((ask+bid)/2):Double.NaN;
  JSONArray oi=Market.get("open-interest?category=linear&symbol="+symbol+"&intervalTime=1h&limit=2").getJSONArray("list");
  double oiChange=Double.NaN;
  if(oi.length()>=2){double current=number(oi.getJSONObject(0),"openInterest"),previous=number(oi.getJSONObject(1),"openInterest");if(previous>0)oiChange=100*(current/previous-1);}
  boolean complete=Double.isFinite(ratio)&&Double.isFinite(funding)&&Double.isFinite(oiChange)&&Double.isFinite(spread);
  String metrics=String.format(Locale.US,"Volum 4H %.2fx media 20 | OI 1H %+.2f%% | funding %+.4f%% | spread %.1f bps",ratio,oiChange,funding*100,spread);
  if(!complete)return new Result(false,"NO TRADE","Date incomplete. "+metrics);
  if(spread>20)return new Result(false,"NO TRADE","Spread prea mare. "+metrics);
  if(ratio<1.2)return new Result(false,"WATCH","Volum insuficient pe ultima lumanare inchisa. "+metrics);
  if(oiChange<=0)return new Result(false,"WATCH","OI nu confirma participare in crestere. "+metrics);
  if(("LONG".equals(side)&&funding>0.001)||( "SHORT".equals(side)&&funding< -0.001))return new Result(false,"WATCH","Funding extrem in directia tranzactiei. "+metrics);
  return new Result(true,"CONFIRMARE PARTIALA","Retest + volum + OI sustin setup-ul; funding si spread acceptabile. Nu este semnal de executie. "+metrics);
 }
}
