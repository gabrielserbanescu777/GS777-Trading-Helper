package ro.gs777.trading;
import org.json.JSONArray;
import java.util.Locale;
import java.util.HashMap;

final class Backtest {
 static final class Result {
  int longsWin,longsLoss,shortsWin,shortsLoss,ambiguous,expired; double realizedGrossRoe=0,positiveRoe=0,negativeRoe=0;
  int signals(){return longsWin+longsLoss+shortsWin+shortsLoss+ambiguous+expired;}
  double profitFactor(){return negativeRoe>0?positiveRoe/negativeRoe:Double.NaN;}
  void record(double roe){realizedGrossRoe+=roe;if(roe>0)positiveRoe+=roe;else negativeRoe-=roe;}
  int historicalLongWins,historicalLongLosses,historicalShortWins,historicalShortLosses,historicalAmbiguous,historicalExpired;
  long first,last,at;double tpRoe,slRoe,lev,fee;
  int count(boolean buy){return buy?historicalLongWins+historicalLongLosses:historicalShortWins+historicalShortLosses;}
  String percent(boolean buy){int n=count(buy),wins=buy?historicalLongWins:historicalShortWins;
   return n<15?"--%":String.format(Locale.US,"%.0f%%",100.0*wins/n);
  }
  String oddsSummary(){return "FRECVENTA ISTORICA 1h/24h, intrari la 24h: LONG "+percent(true)+" ("+count(true)+" rezolvate), SHORT "+percent(false)+" ("+count(false)+" rezolvate), "+historicalAmbiguous+" ambigue, "+historicalExpired+" neatinse. TP/SL ROE "+tpRoe+"%/"+slRoe+"%, levier "+lev+"x. Procentele descriu trecutul; nu estimeaza sigur rezultatul urmatoarei tranzactii.";}
  String summary(){
   int l=longsWin+longsLoss,s=shortsWin+shortsLoss;
   String base=String.format(Locale.US,"TEST ISTORIC 1H, %s - %s | LONG %s (%d), SHORT %s (%d) | %d ambigue, %d expirate la 24h. Speranta ROE LONG %s / SHORT %s (TP/SL minus taxa %.3f%% notional). Model 1h: retest + SMA20/50 + RSI; fara funding/slippage. Nu valideaza procentul viitor.",
    date(first),date(last),l>=20?String.format(Locale.US,"%.0f%%",100.0*longsWin/l):"esantion mic",l,
    s>=20?String.format(Locale.US,"%.0f%%",100.0*shortsWin/s):"esantion mic",s,ambiguous,expired,
    l>=20?String.format(Locale.US,"%+.1f%%",((longsWin*tpRoe-longsLoss*slRoe)/l)-fee*100*lev):"n/a",
    s>=20?String.format(Locale.US,"%+.1f%%",((shortsWin*tpRoe-shortsLoss*slRoe)/s)-fee*100*lev):"n/a",fee*100);
   return base+String.format(Locale.US,"\nStrategie: %d semnale, %d ambigue, %d expirate. Profit factor brut: %s. Rezultat brut cumulat ROE: %+.2f%%. Include marcarea la inchidere pentru expirari; exclude slippage si funding.",signals(),ambiguous,expired,Double.isFinite(profitFactor())?String.format(Locale.US,"%.2f",profitFactor()):"n/a",realizedGrossRoe);
  }
 }
 static String date(long time){return new java.text.SimpleDateFormat("dd.MM",Locale.getDefault()).format(new java.util.Date(time));}
 static final HashMap<String,Result> CACHE=new HashMap<>();
 static synchronized Result run(String symbol,double tpRoe,double slRoe,double lev,double fee)throws Exception {
  if(lev<=0||tpRoe<=0||slRoe<=0)throw new Exception("TP/SL invalide");
  String key=symbol+"_"+tpRoe+"_"+slRoe+"_"+lev+"_"+fee;
  Result previous=CACHE.get(key);if(previous!=null&&System.currentTimeMillis()-previous.at<10*60*1000)return previous;
  JSONArray bars=Market.get("kline?category=linear&symbol="+symbol+"&interval=60&limit=1000").getJSONArray("list");
  int n=bars.length();if(n<150)throw new Exception("Istoric 1h insuficient");
  double[] hi=new double[n],lo=new double[n],open=new double[n],close=new double[n];long[] time=new long[n];
  for(int k=0;k<n;k++){JSONArray b=bars.getJSONArray(n-1-k);time[k]=Long.parseLong(b.getString(0));open[k]=b.getDouble(1);hi[k]=b.getDouble(2);lo[k]=b.getDouble(3);close[k]=b.getDouble(4);}
  Result r=new Result();r.first=time[60];r.last=time[n-26];r.tpRoe=tpRoe;r.slRoe=slRoe;r.lev=lev;r.fee=fee;int until=0;
  // Non-overlapping 24-hour windows; skip the current unfinished Bybit candle.
  for(int i=60;i+24<n-1;i+=24){
   double entry=open[i+1];if(entry<=0)continue;
   for(int side=0;side<2;side++){
    boolean buy=side==0;double tp=entry*(1+(buy?1:-1)*tpRoe/100/lev);
    double sl=entry*(1+(buy?-1:1)*slRoe/100/lev);boolean resolved=false;
    for(int future=i+1;future<=i+24;future++){
     boolean take=buy?hi[future]>=tp:lo[future]<=tp;
     boolean stop=buy?lo[future]<=sl:hi[future]>=sl;
     if(take&&stop){r.historicalAmbiguous++;resolved=true;break;}
     if(take){if(buy)r.historicalLongWins++;else r.historicalShortWins++;resolved=true;break;}
     if(stop){if(buy)r.historicalLongLosses++;else r.historicalShortLosses++;resolved=true;break;}
    }
    if(!resolved)r.historicalExpired++;
   }
  }
  for(int i=60;i<n-25;i++){
   if(i<until)continue;double high=0,low=Double.MAX_VALUE,sm20=0,sm50=0,up=0,down=0;
   for(int k=i-24;k<i;k++){high=Math.max(high,hi[k]);low=Math.min(low,lo[k]);}
   for(int k=i-19;k<=i;k++)sm20+=close[k];sm20/=20;
   for(int k=i-49;k<=i;k++)sm50+=close[k];sm50/=50;
   for(int k=i-13;k<=i;k++){double move=close[k]-close[k-1];up+=Math.max(0,move);down+=Math.max(0,-move);}
   double rsi=down==0?100:100-100/(1+up/down);
   double above=high*1.0015,below=low*.9985;
   boolean buy=close[i]>above&&lo[i]<=above&&sm20>sm50&&rsi>=52&&rsi<=68;
   boolean sell=close[i]<below&&hi[i]>=below&&sm20<sm50&&rsi>=32&&rsi<=48;
   if(!buy&&!sell)continue;
   double entry=open[i+1];if(entry<=0)continue;
   double tp=buy?entry*(1+tpRoe/100/lev):entry*(1-tpRoe/100/lev);
   double sl=buy?entry*(1-slRoe/100/lev):entry*(1+slRoe/100/lev);
   for(int future=i+1;future<=i+24;future++){
    boolean take=buy?hi[future]>=tp:lo[future]<=tp;
    boolean stop=buy?lo[future]<=sl:hi[future]>=sl;
    if(take&&stop){r.ambiguous++;r.record(-slRoe);until=future+1;break;} // conservative: stop first
    if(take){if(buy)r.longsWin++;else r.shortsWin++;r.record(tpRoe);until=future+1;break;}
    if(stop){if(buy)r.longsLoss++;else r.shortsLoss++;r.record(-slRoe);until=future+1;break;}
    if(future==i+24){r.expired++;r.record((buy?1:-1)*(close[future]/entry-1)*100*lev);until=future+1;}
   }
  }
  r.at=System.currentTimeMillis();CACHE.put(key,r);return r;
 }
}
