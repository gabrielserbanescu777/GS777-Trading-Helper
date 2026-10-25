package ro.gs777.trading;

/** Conservative, closed-candle breakout/retest assessment. Not an order signal. */
final class SetupConfirmation {
 static final class Result {
  final String state,description; final double breakout,retest;
  Result(String s,String d,double b,double r){state=s;description=d;breakout=b;retest=r;}
 }
 static Result inspect(double[][] bars,String side){
  if(bars==null||bars.length<26||(!"LONG".equals(side)&&!"SHORT".equals(side)))
   return new Result("NEVERIFICAT","Istoric 4H insuficient sau directie necunoscuta.",Double.NaN,Double.NaN);
  // Last returned candle can still be forming. Exclude it from all confirmations.
  int last=bars.length-2;
  for(int i=last;i>=Math.max(20,last-5);i--){
   double threshold="LONG".equals(side)?Double.NEGATIVE_INFINITY:Double.POSITIVE_INFINITY;
   for(int j=i-20;j<i;j++){
    if("LONG".equals(side))threshold=Math.max(threshold,bars[j][1]);
    else threshold=Math.min(threshold,bars[j][2]);
   }
   double close=bars[i][3];
   boolean breakout="LONG".equals(side)?close>threshold:close<threshold;
   if(!breakout)continue;
   // A breakout is not a retest. Require a LATER closed candle that touches the
   // broken level, closes back on the correct side, and does not violate by >0.3%.
   for(int k=i+1;k<=last;k++){
    double[] b=bars[k];double tolerance=threshold*.003;
    boolean touched="LONG".equals(side)?b[2]<=threshold:b[1]>=threshold;
    boolean held="LONG".equals(side)?b[2]>=threshold-tolerance&&b[3]>threshold:b[1]<=threshold+tolerance&&b[3]<threshold;
    if(touched&&held)return new Result("RETEST CONFIRMAT","Breakout 4H urmat de retest pe lumanari inchise; necesita verificare volum, lichiditate si risc.",threshold,threshold);
    if("LONG".equals(side)?b[3]<threshold-tolerance:b[3]>threshold+tolerance)
     return new Result("INVALIDAT","Pretul a inchis inapoi dincolo de zona breakout.",threshold,Double.NaN);
   }
   return new Result("BREAKOUT IN ASTEPTARE","Inchidere 4H peste/sub nivel; retestul nu este confirmat.",threshold,Double.NaN);
  }
  return new Result("FARA CONFIRMARE","Niciun breakout valid pe ultimele 5 lumanari 4H inchise.",Double.NaN,Double.NaN);
 }
}
