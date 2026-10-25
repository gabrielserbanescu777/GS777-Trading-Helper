package ro.gs777.trading;
import android.content.Context;
import java.util.Locale;

/** Read-only, explainable question assistant using fresh Bybit data. */
final class AskAi {
 static String answer(Context context,String symbol,String question,double tp,double sl,double lev,double capital,double risk) {
  if(!symbol.matches("[A-Z0-9]{2,22}USDT"))return "Scrie un simbol valid, de exemplu SOLUSDT.";
  if(question.trim().isEmpty())return "Scrie intrebarea ta despre LONG, SHORT, intrare sau risc.";
  if(tp<=0||tp>1000||sl<=0||sl>=100||lev<=0||lev>100||capital<=0||risk<=0||risk>10)
   return "Verifica TP, SL, levierul, capitalul si riscul din setari.";
  try {
   Market.Analysis a=Market.analyze(symbol);
   MarketExtras.Plan p=MarketExtras.inspect(context,symbol,a,capital,risk,tp,sl,lev);
   String q=question.toLowerCase(Locale.ROOT);
   String direction=p.verdict;
   String answer="INTREABA AI / asistent local bazat pe reguli\n"+symbol+" / CONCLUZIE: "+direction+
    "\nPret "+Market.number(a.coin.price)+" USDT | 24h "+String.format(Locale.US,"%+.2f%%",a.coin.change)+
    " | scor "+String.format(Locale.US,"%+d/5",a.score)+" | RSI 1h "+String.format(Locale.US,"%.0f",a.rsi)+
    " | RSI 1D "+String.format(Locale.US,"%.0f",a.dailyRsi)+
    "\nDe ce: "+p.reason+" "+p.guard;
   if(q.contains("short")&&direction.equals("LONG")||q.contains("long")&&direction.equals("SHORT"))
    answer+="\nDirectia ceruta in intrebare este opusa semnalului confirmat.";
   if(q.contains("short")&&direction.equals("ASTEAPTA")&&a.score>0||q.contains("long")&&direction.equals("ASTEAPTA")&&a.score<0)
    answer+="\nIndicatorii actuali nu sustin directia ceruta.";
   if(q.contains("tp")||q.contains("sl")||q.contains("intr")||q.contains("risc")||direction.equals("LONG")||direction.equals("SHORT"))
    answer+="\nLONG TP "+Market.number(p.tpLong)+" / SL "+Market.number(p.slLong)+
     " | SHORT TP "+Market.number(p.tpShort)+" / SL "+Market.number(p.slShort)+
     String.format(Locale.US," | risc %.2f USDT | R/R %.2f",p.estimatedLoss,p.ratio);
   return answer+"\nDate Bybit "+p.timestamp+". Evaluare automata locala, fara model AI conectat; nu este probabilitate de castig.";
  }catch(Exception ex){return "ASTEAPTA / date Bybit sau filtre indisponibile: "+ex.getMessage()+". Nu emit semnal fara verificare.";}
 }
}
