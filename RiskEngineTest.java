package ro.gs777.trading;
public class RiskEngineTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  check(!RiskEngine.check(100,1,100,10,7,14).allowed,"oversized trade");
  check(!RiskEngine.check(100,1,1,2,7,3).allowed,"poor R:R");
  check(!RiskEngine.check(100,3,1,2,7,14).allowed,"risk >2%");
  check(!RiskEngine.check(100,1,1,21,7,14).allowed,"leverage >20");
  check(!RiskEngine.check(100,1,1,2,Double.NaN,14).allowed,"NaN stop");
  check(RiskEngine.check(1000,1,20,2,7,21).allowed,"reasonable demo sizing");
  System.out.println("6 risk checks passed");
 }
}
