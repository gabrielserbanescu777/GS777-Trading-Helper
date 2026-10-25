package ro.gs777.trading;
import org.json.JSONArray;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Conditional 72-hour scenario based on completed Bybit 4h candles and current public ticker. */
final class ThreeDayPlan {
 static final class Result {
  String title,details,symbol,side;
  int tone;double entry,stop,tp1,tp2;long created,expires;
 }
 static String price(double v){
  if(!Double.isFinite(v)||v<=0)return "n/a";
  return new BigDecimal(Double.toString(v)).round(new MathContext(8))
   .stripTrailingZeros().toPlainString();
 }
 static Result build(String symbol,double capital,double riskPercent,double leverage)throws Exception {
  if(symbol==null||!symbol.matches("[A-Z0-9]{2,22}USDT"))throw new Exception("Simbol USDT invalid.");
  if(!Double.isFinite(capital)||capital<=0||capital>10000000
   ||!Double.isFinite(riskPercent)||riskPercent<=0||riskPercent>5
   ||!Double.isFinite(leverage)||leverage<1||leverage>20)
   throw new Exception("Capital > 0, risc maxim 5%, levier intre 1 si 20.");
  Market.Coin quote=Market.ticker(symbol);
  if(!symbol.equals(quote.symbol)||quote.price<=0||!Double.isFinite(quote.turnover))
   throw new Exception("Coinul nu este disponibil in datele Bybit.");
  Market.Analysis analysis=Market.analyze(symbol,quote);
  JSONArray bars=Market.get("kline?category=linear&symbol="+symbol+"&interval=240&limit=50").getJSONArray("list");
  if(bars.length()<22)throw new Exception("Istoricul 4h este prea scurt pentru plan.");
  // Bybit returns newest candle first. Index zero is still forming, so ignore it.
  double high=0,low=Double.MAX_VALUE,atr=0;
  for(int i=1;i<=18;i++){
   JSONArray bar=bars.getJSONArray(i);
   high=Math.max(high,bar.getDouble(2));low=Math.min(low,bar.getDouble(3));
  }
  for(int i=1;i<=14;i++){
   JSONArray bar=bars.getJSONArray(i),previous=bars.getJSONArray(i+1);
   double barHigh=bar.getDouble(2),barLow=bar.getDouble(3),priorClose=previous.getDouble(4);
   atr+=Math.max(barHigh-barLow,Math.max(Math.abs(barHigh-priorClose),Math.abs(barLow-priorClose)))/14;
  }
  if(!(high>low&&low>0&&atr>0&&Double.isFinite(atr)))throw new Exception("Niveluri 4h nevalide.");
  double longTrigger=high+atr*.10,shortTrigger=Math.max(0,low-atr*.10);
  boolean illiquid=quote.turnover<1000000;
  boolean extremeVolatility=atr/quote.price>.18 || shortTrigger<=0;
  boolean longBias=analysis.score>=3 && !illiquid && !extremeVolatility;
  boolean shortBias=analysis.score<=-3 && !illiquid && !extremeVolatility;
  String direction=longBias?"LONG":shortBias?"SHORT":"ASTEAPTA";
  boolean trade=longBias||shortBias;
  double entry=longBias?longTrigger:shortTrigger;
  double stopDistance=Math.max(atr*1.2,entry*.006);
  double sl=longBias?entry-stopDistance:entry+stopDistance;
  double tp1=longBias?entry+1.5*stopDistance:entry-1.5*stopDistance;
  double tp2=longBias?entry+2*stopDistance:entry-2*stopDistance;
  double riskMoney=capital*riskPercent/100;
  // Reserve a buffer for round-trip fees and slippage; actual costs may be higher.
  double estimatedFraction=trade?stopDistance/entry+.002:0;
  double maxNotional=trade?Math.min(riskMoney/estimatedFraction,capital*leverage):0;
  double margin=trade?maxNotional/leverage:0;
  SimpleDateFormat date=new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault());
  Calendar expiry=Calendar.getInstance();expiry.add(Calendar.HOUR_OF_DAY,72);
  Result result=new Result();result.tone=longBias?1:shortBias?-1:0;
  result.symbol=symbol;result.side=direction;result.entry=trade?entry:0;result.stop=trade?sl:0;
  result.tp1=trade?tp1:0;result.tp2=trade?tp2:0;result.created=System.currentTimeMillis();result.expires=expiry.getTimeInMillis();
  result.title=symbol+" / "+direction+" / orizont 3 zile";
  StringBuilder text=new StringBuilder();
  text.append("Date Bybit ").append(date.format(new Date())).append(" | expirare ")
   .append(date.format(expiry.getTime())).append("\n");
  text.append("Pret actual ").append(price(quote.price)).append(" USDT | 24h ")
   .append(String.format(Locale.US,"%+.2f%%",quote.change)).append("\n");
  text.append("Structura: scor 1h + 1D ").append(String.format(Locale.US,"%+d/5",analysis.score))
   .append("; RSI 1h ").append(String.format(Locale.US,"%.1f",analysis.rsi))
   .append("; volum 24h ").append(String.format(Locale.US,"%.0f",quote.turnover)).append(" USDT.\n");
  text.append("Suport 4h (18 lumanari inchise): ").append(price(low))
   .append(" | rezistenta: ").append(price(high))
   .append(" | ATR 4h: ").append(price(atr)).append(".\n\n");
  if(illiquid)text.append("Rulaj sub 1 milion USDT/24h: risc de executie. Nu genera intrare.\n");
  else if(extremeVolatility)text.append("Volatilitate 4h extrema fata de pret: asteapta stabilizarea si recalculeaza planul.\n");
  else if(!trade)text.append("Semnalele 1h si 1D nu sustin aceeasi directie. Urmareste o confirmare noua; nu intra acum.\n");
  if(trade){
   text.append("Scenariu ").append(direction).append(" NUMAI daca o lumanare 4h inchide ")
    .append(longBias?"peste ":"sub ").append(price(entry))
    .append(" si retestul mentine nivelul. Fara confirmare = fara intrare.\n");
   text.append("Intrare orientativa ").append(price(entry)).append(" | SL ")
    .append(price(sl)).append(" | TP1 ").append(price(tp1))
    .append(" (1.5R) | TP2 ").append(price(tp2)).append(" (2R).\n");
   if((longBias&&quote.price>=entry)||(shortBias&&quote.price<=entry))
    text.append("Pretul a trecut deja de prag; asteapta retestul, nu urmari miscarea.\n");
   text.append(String.format(Locale.US,
    "Capital %.2f USDT | risc %.2f%% = %.2f USDT | levier %.1fx | marja maxima estimata %.2f USDT | pozitie maxima estimata %.2f USDT.\n",
    capital,riskPercent,riskMoney,leverage,margin,maxNotional));
  }else{
   text.append("Praguri de urmarit, fara ordin: inchidere 4h peste ")
    .append(price(longTrigger)).append(" pentru LONG sau sub ").append(price(shortTrigger))
    .append(" pentru SHORT, apoi recalcularea planului.\n");
  }
  text.append("\nZIUA 1: urmareste inchiderea 4h la nivelul cheie si volumul; asteapta retestul.\n");
  text.append("ZIUA 2: recalculeaza planul din date noi; daca nu s-a confirmat, ramai pe margine.\n");
  text.append("ZIUA 3: reevalueaza structura; daca nu exista confirmare pana la expirare, abandoneaza acest plan.\n");
  text.append("Invalidare: revenire sub nivelul rupt pentru LONG / peste pentru SHORT; ")
   .append("daca atinge SL, respecta limita de risc.\n");
  text.append("Estimare, nu prognoza sau probabilitate de castig. Costurile reale, funding si lichidarea ")
   .append("se verifica in Bybit. Nu plaseaza ordine.");
  result.details=text.toString();return result;
 }
}
