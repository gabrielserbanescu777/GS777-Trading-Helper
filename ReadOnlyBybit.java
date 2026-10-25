package ro.gs777.trading;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReadOnlyBybit {
 static final String ALIAS="gs777_readonly_v1";
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("gs777_readonly",Context.MODE_PRIVATE);}
 static SecretKey key()throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(store.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
  return generator.generateKey();
 }
 static String encrypt(String value)throws Exception {
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
 }
 static String decrypt(String value)throws Exception {
  String[] parts=value.split(":");if(parts.length!=2)throw new Exception("Cheia salvata nu poate fi citita");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
  return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
 }
 static JSONObject get(String apiKey,String secret,String path,String query)throws Exception {
  String timestamp=Long.toString(System.currentTimeMillis()),window="5000";
  String payload=timestamp+apiKey+window+query;
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  byte[] bytes=mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
  StringBuilder signature=new StringBuilder();for(byte b:bytes)signature.append(String.format(java.util.Locale.US,"%02x",b&255));
  HttpURLConnection connection=(HttpURLConnection)new URL("https://api.bybit.com/v5/"+path+(query.isEmpty()?"":"?"+query)).openConnection();
  connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
  connection.setRequestProperty("X-BAPI-API-KEY",apiKey);connection.setRequestProperty("X-BAPI-TIMESTAMP",timestamp);
  connection.setRequestProperty("X-BAPI-RECV-WINDOW",window);connection.setRequestProperty("X-BAPI-SIGN",signature.toString());
  try {
   if(connection.getResponseCode()!=200)throw new Exception("Bybit HTTP "+connection.getResponseCode());
   ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){
    byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1){buffer.write(chunk,0,n);if(buffer.size()>1000000)throw new Exception("Raspuns mare");}
   }
   JSONObject reply=new JSONObject(buffer.toString("UTF-8"));
   if(reply.optInt("retCode",-1)!=0)throw new Exception(reply.optString("retMsg","Eroare Bybit"));
   return reply.getJSONObject("result");
  }finally{connection.disconnect();}
 }
 static void connect(Context context,String api,String secret)throws Exception {
  if(api.trim().isEmpty()||secret.trim().isEmpty())throw new Exception("Introdu ambele campuri");
  JSONObject permission=get(api.trim(),secret.trim(),"user/query-api","");
  if(permission.optInt("readOnly",-1)!=1)throw new Exception("Accept numai chei API cu Read Only activat.");
  prefs(context).edit().clear().putString("key",encrypt(api.trim())).putString("secret",encrypt(secret.trim())).apply();
 }
 static boolean connected(Context c){return prefs(c).contains("key")&&prefs(c).contains("secret");}
 static void disconnect(Context c){prefs(c).edit().clear().apply();}
 static double roundTripTakerFee(Context c,String symbol)throws Exception {
  if(!connected(c))throw new Exception("Fara cheie Read Only");
  String keyName="fee_"+symbol;
  long at=prefs(c).getLong(keyName+"_at",0);
  if(System.currentTimeMillis()-at<24L*60*60*1000&&at>0)return Double.longBitsToDouble(prefs(c).getLong(keyName,0));
  String api=decrypt(prefs(c).getString("key","")),secret=decrypt(prefs(c).getString("secret",""));
  JSONArray rates=get(api,secret,"account/fee-rate","category=linear&symbol="+symbol).getJSONArray("list");
  if(rates.length()==0)throw new Exception("Fara taxa pentru simbol");
  double perSide=MarketExtras.parse(rates.getJSONObject(0),"takerFeeRate");
  if(perSide<0||perSide>.02)throw new Exception("Taxa nevalida");
  double roundtrip=2*perSide;
  prefs(c).edit().putLong(keyName,Double.doubleToRawLongBits(roundtrip)).putLong(keyName+"_at",System.currentTimeMillis()).apply();
  return roundtrip;
 }
 static String positionWarnings(Context c)throws Exception{
  if(!connected(c))return "";
  String api=decrypt(prefs(c).getString("key","")),secret=decrypt(prefs(c).getString("secret",""));
  JSONArray list=get(api,secret,"position/list","category=linear&settleCoin=USDT").getJSONArray("list");
  StringBuilder alerts=new StringBuilder();
  int shown=0;
  for(int i=0;i<list.length()&&shown<4;i++){
   JSONObject j=list.getJSONObject(i);if(MarketExtras.parse(j,"size")<=0)continue;
   String symbol=j.optString("symbol");double mark=MarketExtras.parse(j,"markPrice"),liq=MarketExtras.parse(j,"liqPrice"),sl=MarketExtras.parse(j,"stopLoss");
   String problem="";
   if(sl<=0)problem="fara Stop Loss";
   else if(mark>0&&liq>0&&100*Math.abs(mark-liq)/mark<5)problem="lichidare la sub 5% de mark";
   else{
    double entry=MarketExtras.parse(j,"avgPrice"),leverage=MarketExtras.parse(j,"leverage");
    double configured=c.getSharedPreferences("gs777",Context.MODE_PRIVATE).getFloat("sl",7);
    if(entry>0&&leverage>0&&configured>0){
     double lossRoe=100*leverage*(j.optString("side").equals("Buy")?(entry-sl)/entry:(sl-entry)/entry);
     if(lossRoe>configured*1.2)problem=String.format(java.util.Locale.US,"SL permite ROE -%.1f%%, peste pragul tau %.1f%%",lossRoe,configured);
    }
   }
   if(!problem.isEmpty()){if(alerts.length()>0)alerts.append("; ");alerts.append(symbol).append(": ").append(problem);shown++;}
  }
  return alerts.toString();
 }
 static String positions(Context c)throws Exception{
  if(!connected(c))return "Conecteaza o cheie API Read Only din ecranul principal. Nu trimite cheia in chat.";
  String api=decrypt(prefs(c).getString("key","")),secret=decrypt(prefs(c).getString("secret",""));
  JSONArray list=get(api,secret,"position/list","category=linear&settleCoin=USDT").getJSONArray("list");
  StringBuilder out=new StringBuilder("Pozitii Bybit / doar citire");
  int count=0;
  for(int i=0;i<list.length();i++){
   JSONObject j=list.getJSONObject(i);double size=MarketExtras.parse(j,"size");if(size<=0)continue;
   if(count++>=8)break;
   out.append("\n").append(j.optString("symbol")).append(" ").append(j.optString("side"))
    .append(" | intrare ").append(j.optString("avgPrice")).append(" | mark ").append(j.optString("markPrice"))
    .append(" | liq ").append(j.optString("liqPrice","n/a"))
    .append("\nTP ").append(j.optString("takeProfit","-")).append(" SL ").append(j.optString("stopLoss","-"));
   double mark=MarketExtras.parse(j,"markPrice"),liquidation=MarketExtras.parse(j,"liqPrice");
   if(MarketExtras.parse(j,"stopLoss")<=0)out.append(" | ATENTIE: fara SL");
   if(mark>0&&liquidation>0){
    double distance=100*Math.abs(mark-liquidation)/mark;
    out.append(String.format(java.util.Locale.US," | lichidare la %.1f%% de mark",distance));
    if(distance<5)out.append(" | RISC RIDICAT");
   }
  }
  if(count==0)out.append("\nFara pozitii deschise in USDT perpetual.");
  return out.toString();
 }
}
