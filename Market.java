package ro.gs777.trading;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class Market {
 static final class Coin {
  String symbol; double price, change, turnover;
  Coin(String s,double p,double c,double t){symbol=s;price=p;change=c;turnover=t;}
 }
 static final class Analysis {
  Coin coin; double rsi,sma20,sma50,dailyRsi,dailySma20; int score;
  String direction,reason;
 }
 static JSONObject get(String path) throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL("https://api.bybit.com/v5/market/"+path).openConnection();
  c.setConnectTimeout(9000);c.setReadTimeout(12000);
  c.setRequestProperty("Accept","application/json");
  c.setRequestProperty("User-Agent","GS777TradingAndroid/0.5");
  try {
   if(c.getResponseCode()!=200)throw new Exception("Bybit HTTP "+c.getResponseCode());
   ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[8192];
   try(InputStream in=c.getInputStream()){int n;while((n=in.read(chunk))!=-1){b.write(chunk,0,n);if(b.size()>2500000)throw new Exception("Răspuns prea mare");}}
   JSONObject json=new JSONObject(b.toString("UTF-8"));
   if(json.optInt("retCode",-1)!=0)throw new Exception(json.optString("retMsg","Eroare Bybit"));
   return json.getJSONObject("result");
  }finally{c.disconnect();}
 }
 static Coin fromJson(JSONObject j){return new Coin(j.optString("symbol"),j.optDouble("lastPrice"),j.optDouble("price24hPcnt")*100,j.optDouble("turnover24h"));}
 static Coin ticker(String symbol)throws Exception {
  JSONArray a=get("tickers?category=linear&symbol="+symbol).getJSONArray("list");
  if(a.length()==0)throw new Exception("Moneda nu este disponibilă pe Bybit Perpetual USDT");
  return fromJson(a.getJSONObject(0));
 }
 static List<Coin> allUsdtPerpetual()throws Exception {
  JSONArray array=get("tickers?category=linear").getJSONArray("list");ArrayList<Coin> out=new ArrayList<>();
  for(int i=0;i<array.length();i++){
   Coin coin=fromJson(array.getJSONObject(i));
   if(coin.symbol.endsWith("USDT")&&coin.price>0&&Double.isFinite(coin.change))out.add(coin);
  }
  out.sort(Comparator.comparing((Coin coin)->coin.symbol));return out;
 } static List<Coin> movers()throws Exception {
  JSONArray array=get("tickers?category=linear").getJSONArray("list");ArrayList<Coin> out=new ArrayList<>();
  for(int i=0;i<array.length();i++){
   Coin c=fromJson(array.getJSONObject(i));
   if(c.symbol.endsWith("USDT")&&c.price>0&&c.turnover>=1000000&&Double.isFinite(c.change))out.add(c);
  }
  out.sort(Comparator.comparingDouble((Coin c)->c.change).reversed());return out;
 }
 static double[][] candles(String symbol,String interval)throws Exception {
  JSONArray a=get("kline?category=linear&symbol="+symbol+"&interval="+interval+"&limit=240").getJSONArray("list");
  if(a.length()<201)throw new Exception("Istoric insuficient pentru MA200");
  double[][] out=new double[a.length()][4];
  for(int i=0;i<a.length();i++){
   JSONArray c=a.getJSONArray(a.length()-1-i);
   for(int j=0;j<4;j++)out[i][j]=Double.parseDouble(c.getString(j+1));
  }
  return out;
 }
 static double[] closes(String symbol,String interval)throws Exception {
  JSONArray a=get("kline?category=linear&symbol="+symbol+"&interval="+interval+"&limit=100").getJSONArray("list");
  if(a.length()<51)throw new Exception("Istoric insuficient pentru "+symbol);
  double[] d=new double[a.length()-1];
  for(int i=1;i<a.length();i++)d[d.length-i]=Double.parseDouble(a.getJSONArray(i).getString(4));
  return d;
 }
 static double sma(double[] d,int period){double total=0;for(int i=d.length-period;i<d.length;i++)total+=d[i];return total/period;}
 static double rsi(double[] d){
  int n=14;double gain=0,loss=0;
  for(int i=1;i<=n;i++){double diff=d[i]-d[i-1];gain+=Math.max(diff,0);loss+=Math.max(-diff,0);}
  gain/=n;loss/=n;
  for(int i=n+1;i<d.length;i++){double diff=d[i]-d[i-1];gain=(gain*13+Math.max(diff,0))/14;loss=(loss*13+Math.max(-diff,0))/14;}
  return loss==0?(gain==0?50:100):100-100/(1+gain/loss);
 }
 static Analysis analyze(String symbol)throws Exception {
  return analyze(symbol,ticker(symbol));
 }
 static Analysis analyze(String symbol,Coin quote)throws Exception {
  Analysis a=new Analysis();a.coin=quote;
  double[] h=closes(symbol,"60"),d=closes(symbol,"D");
  a.rsi=rsi(h);a.dailyRsi=rsi(d);a.sma20=sma(h,20);a.sma50=sma(h,50);a.dailySma20=sma(d,20);
  double last=h[h.length-1];int score=0;
  score+=last>a.sma20?1:-1;score+=a.sma20>a.sma50?1:-1;score+=a.coin.price>a.dailySma20?1:-1;
  score+=a.rsi>=52&&a.rsi<=68?1:a.rsi<=48&&a.rsi>=32?-1:0;
  score+=a.dailyRsi>=52&&a.dailyRsi<=68?1:a.dailyRsi<=48&&a.dailyRsi>=32?-1:0;
  a.score=score;
  a.direction=score>=3?"LONG (condiții favorabile)":score<=-3?"SHORT (condiții favorabile)":"AȘTEAPTĂ (semnale mixte)";
  a.reason=String.format(Locale.US,"Scor indicatori: %+d / 5 · RSI 1h: %.1f · RSI 1D: %.1f\nPreț vs. SMA20 1h: %s · SMA20 vs. SMA50: %s · Preț vs. SMA20 1D: %s",
   score,a.rsi,a.dailyRsi,last>a.sma20?"peste":"sub",a.sma20>a.sma50?"peste":"sub",a.coin.price>a.dailySma20?"peste":"sub");
  return a;
 }
 static String number(double value){return String.format(Locale.US,value>=100?"%,.2f":value>=1?"%.4f":"%.8f",value);}
}


