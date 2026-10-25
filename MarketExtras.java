package ro.gs777.trading;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

final class MarketExtras {
 static final class Plan {
  String symbol, verdict, reason, context, costs, timestamp, guard="",liquidity="";
  double capital, riskPercent, entry, tpLong, slLong, tpShort, slShort, support, resistance, spreadPct, fundingPct, riskUsdt, margin, notional, estimatedLoss, expectedProfit, ratio;
 }
 static double parse(JSONObject j,String field){try{return Double.parseDouble(j.optString(field,"0"));}catch(Exception ignored){return 0;}}
 static Plan inspect(android.content.Context app,String symbol,Market.Analysis analysis,double capital,double riskPercent,double tpRoe,double slRoe,double lev)throws Exception {
  Plan p=new Plan();p.symbol=symbol;p.entry=analysis.coin.price;p.capital=capital;p.riskPercent=riskPercent;
  if(p.entry<=0||capital<=0||capital>10000000||riskPercent<=0||riskPercent>10||lev<=0||lev>100||slRoe<=0||slRoe>=100||tpRoe<=0)throw new Exception("Parametri de risc invalizi");
  JSONObject ticker=Market.get("tickers?category=linear&symbol="+symbol).getJSONArray("list").getJSONObject(0);
  double bid=parse(ticker,"bid1Price"),ask=parse(ticker,"ask1Price"),mark=parse(ticker,"markPrice");
  if(bid<=0||ask<bid)throw new Exception("Spread indisponibil; risc imposibil de estimat corect");
  p.spreadPct=100*(ask-bid)/((ask+bid)/2);
  p.fundingPct=100*parse(ticker,"fundingRate");
  JSONArray bars=Market.get("kline?category=linear&symbol="+symbol+"&interval=60&limit=30").getJSONArray("list");
  if(bars.length()<26)throw new Exception("Prea putine lumanari de 1h");
  double high=0,low=Double.MAX_VALUE,vol=0,prev=0;
  for(int i=2;i<=25;i++){JSONArray b=bars.getJSONArray(i);high=Math.max(high,b.getDouble(2));low=Math.min(low,b.getDouble(3));vol+=b.getDouble(5);}
  JSONArray lastClosed=bars.getJSONArray(1);prev=lastClosed.getDouble(5);
  double candleClose=lastClosed.getDouble(4),candleLow=lastClosed.getDouble(3),candleHigh=lastClosed.getDouble(2);
  p.support=low;p.resistance=high;
  double buffer=.0015;
  p.verdict=analysis.score>=3&&candleClose>high*(1+buffer)&&candleLow<=high*(1+buffer)&&p.entry>high*(1+buffer)?"LONG":
   analysis.score<=-3&&candleClose<low*(1-buffer)&&candleHigh>=low*(1-buffer)&&p.entry<low*(1-buffer)?"SHORT":"ASTEAPTA";
  // High/low refer to closed candles; a breakout of recent closed resistance/support needs price confirmation.
  p.reason=String.format(Locale.US,"Scor %+d/5. LONG peste %s cu inchidere si retestare 1h; SHORT sub %s cu inchidere si retestare 1h. Volum ultima 1h / medie 24h: %.2fx.",
   analysis.score,Market.number(high*(1+buffer)),Market.number(low*(1-buffer)),vol>0?prev/(vol/24):0);
  double oi=parse(ticker,"openInterestValue");
  String extra="";
  try {
   JSONArray list=Market.get("open-interest?category=linear&symbol="+symbol+"&intervalTime=1h&limit=2").getJSONArray("list");
   if(list.length()>=2){double a=Double.parseDouble(list.getJSONObject(0).getString("openInterest")),b=Double.parseDouble(list.getJSONObject(1).getString("openInterest"));if(b>0)extra=String.format(Locale.US," | OI 1h %+.1f%%",100*(a/b-1));}
  }catch(Exception ignored){}
  try {
   JSONArray list=Market.get("account-ratio?category=linear&symbol="+symbol+"&period=1h&limit=1").getJSONArray("list");
   if(list.length()>0)extra+=String.format(Locale.US," | Conturi LONG %.0f%%",100*parse(list.getJSONObject(0),"buyRatio"));
  }catch(Exception ignored){}
  try{JSONObject instrument=Market.get("instruments-info?category=linear&symbol="+symbol).getJSONArray("list").getJSONObject(0);
   extra+=" | Pas pret "+instrument.getJSONObject("priceFilter").optString("tickSize");
   extra+=" | Levier max "+instrument.getJSONObject("leverageFilter").optString("maxLeverage");
  }catch(Exception ignored){}
  p.context=String.format(Locale.US,"Spread %.3f%% | Funding %+,.4f%% | Mark %s | OI %.0f USDT%s",
   p.spreadPct,p.fundingPct,mark>0?Market.number(mark):"n/a",oi,extra);
  double tr=tpRoe/100/lev,sr=slRoe/100/lev;
  p.tpLong=p.entry*(1+tr);p.slLong=p.entry*(1-sr);p.tpShort=p.entry*(1-tr);p.slShort=p.entry*(1+sr);
  double fees=0.0012;boolean actualFee=false;
  if(app!=null&&ReadOnlyBybit.connected(app)){try{fees=ReadOnlyBybit.roundTripTakerFee(app,symbol);actualFee=true;}catch(Exception ignored){}}
  double stopFraction=slRoe/100+fees*lev+p.spreadPct/100*lev;
  p.riskUsdt=capital*riskPercent/100;
  p.margin=Math.min(capital,p.riskUsdt/stopFraction);
  p.notional=p.margin*lev;
  p.estimatedLoss=p.margin*slRoe/100+p.notional*(fees+p.spreadPct/100);
  p.expectedProfit=p.margin*tpRoe/100-p.notional*(fees+p.spreadPct/100);
  p.ratio=p.estimatedLoss>0?p.expectedProfit/p.estimatedLoss:0;
  if(p.ratio<1){p.verdict="ASTEAPTA";p.reason+=" Raport risc/castig sub 1 dupa costurile estimate.";}
  p.costs=String.format(Locale.US,"Taxe %s %.3f%% dus-intors + spread; funding viitor necalculat. Lichidarea reala se verifica in Bybit.",actualFee?"Bybit cont":"estimate",fees*100);
  try{AdvancedMarket.Depth depth=AdvancedMarket.depth(symbol,p.notional);p.liquidity=depth.text;p.guard=AdvancedMarket.barriers(p,analysis,depth);}
  catch(Exception ex){p.liquidity="Adancime order book indisponibila";p.guard="Lichiditatea nu a putut fi verificata.";if(!p.verdict.equals("ASTEAPTA"))p.verdict="ASTEAPTA";}
  p.timestamp=new java.text.SimpleDateFormat("dd.MM HH:mm:ss",Locale.getDefault()).format(new java.util.Date());
  return p;
 }
 static String compact(Plan p){
  String why=p.guard.startsWith("NU INTRA:")?p.guard.replace("NU INTRA: ",""):p.verdict.equals("ASTEAPTA")?"Asteapta inchiderea/retestarea pe 1h":"Conditii de intrare confirmate pe 1h";
  if(why.length()>58)why=why.substring(0,58)+"...";
  return String.format(Locale.US,"%s | 1h\nDe ce: %s\nLONG > %s | SHORT < %s\nRisc %.2f USDT | R/R %.2f\nTP/SL L %s/%s | S %s/%s\nDate %s | atinge detalii",
   p.symbol,why,Market.number(p.resistance*1.0015),Market.number(p.support*.9985),p.estimatedLoss,p.ratio,
   Market.number(p.tpLong),Market.number(p.slLong),Market.number(p.tpShort),Market.number(p.slShort),p.timestamp);
 }
 static String summary(Plan p){
  return String.format(Locale.US,"%s | %s\n%s\n%s\n%s\nCapital %.2f USDT | risc max %.1f%% (%.2f USDT)\nMarja %.2f | pozitie %.2f USDT | pierdere estimata %.2f | profit estimat %.2f | R/R %.2f\nLONG TP %s SL %s | SHORT TP %s SL %s\n%s\n%s\nBybit: %s",
   p.symbol,p.verdict,p.reason,p.guard,p.liquidity,p.capital,p.riskPercent,p.riskUsdt,p.margin,p.notional,p.estimatedLoss,p.expectedProfit,p.ratio,
   Market.number(p.tpLong),Market.number(p.slLong),Market.number(p.tpShort),Market.number(p.slShort),p.context,p.costs,p.timestamp);
 }
}
