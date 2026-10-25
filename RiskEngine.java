package ro.gs777.trading;
import java.util.Locale;
/** Conservative risk limits for demo positions; all monetary inputs in USDT. */
final class RiskEngine {
 static final class Result {boolean allowed;double loss,maxMargin,rr;String message;}
 static Result check(double capital,double riskPct,double margin,double leverage,double slRoe,double tpRoe){
  Result r=new Result();
  if(!Double.isFinite(capital)||capital<=0||!Double.isFinite(riskPct)||riskPct<=0||riskPct>2||!Double.isFinite(margin)||margin<=0||!Double.isFinite(leverage)||leverage<1||leverage>20||!Double.isFinite(slRoe)||slRoe<=0||slRoe>=100||!Double.isFinite(tpRoe)||tpRoe<=0){r.message="Valori invalide: risc maxim 2%, levier maxim 20x.";return r;}
  double fraction=slRoe/100+leverage*(.0012+.002); // fees plus conservative slippage buffer
  r.loss=margin*fraction;r.maxMargin=Math.min(capital*riskPct/100/fraction,capital*2/leverage);
  r.rr=(tpRoe/100-leverage*.0012)/fraction;
  r.allowed=margin<=r.maxMargin&&r.rr>=1.0&&margin<=capital;
  r.message=String.format(Locale.US,"Pierdere estimata %.2f USDT; marja maxima %.2f USDT; R:R net estimat %.2f (taxe + buffer slippage).",r.loss,r.maxMargin,r.rr);
  if(r.rr<1.0)r.message+=" R:R sub 1:1: NO TRADE.";
  if(margin>capital)r.message+=" Marja depaseste capitalul: NO TRADE.";
  return r;
 }
}
