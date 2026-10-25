package ro.gs777.trading;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.security.SecureRandom;
import java.util.Locale;
/** Public market ticker WebSocket. No API keys and no trading permissions. */
final class BybitTickerStream {
 interface Listener {void status(String status);void ticker(String symbol,double price,double change);}
 private final Listener listener;
 private volatile boolean running=false;
 private volatile String selected="BTCUSDT";
 private volatile SSLSocket socket;
 private Thread worker;
 private final SecureRandom random=new SecureRandom();
 BybitTickerStream(Listener l){listener=l;}
 synchronized void start(String symbol){selected=valid(symbol);if(running)return;running=true;worker=new Thread(this::loop,"GS777-Bybit-WS");worker.setDaemon(true);worker.start();}
 void changeSymbol(String symbol){String next=valid(symbol);if(!next.equals(selected)){selected=next;close();}}
 synchronized void stop(){running=false;close();if(worker!=null)worker.interrupt();}
 private String valid(String symbol){return symbol!=null&&symbol.matches("[A-Z0-9]{2,22}USDT")?symbol:"BTCUSDT";}
 private void close(){try{SSLSocket s=socket;if(s!=null)s.close();}catch(Exception ignored){}}
 private void loop(){int failures=0;while(running){
  try{connect();failures=0;}catch(Exception ex){if(running)listener.status("WebSocket offline; reconectare automata. "+ex.getClass().getSimpleName());}
  if(!running)break;
  long wait=Math.min(30000,1000L<<Math.min(5,failures++));
  try{Thread.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
 }}
 private void connect()throws Exception{
  listener.status("WebSocket Bybit: conectare...");
  SSLSocket s=(SSLSocket)SSLSocketFactory.getDefault().createSocket("stream.bybit.com",443);socket=s;s.setSoTimeout(35000);s.startHandshake();
  InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();byte[] key=new byte[16];random.nextBytes(key);
  String base64=android.util.Base64.encodeToString(key,android.util.Base64.NO_WRAP);
  String request="GET /v5/public/linear HTTP/1.1\r\nHost: stream.bybit.com\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: "+base64+"\r\nSec-WebSocket-Version: 13\r\n\r\n";
  out.write(request.getBytes("US-ASCII"));out.flush();
  ByteArrayOutputStream header=new ByteArrayOutputStream();int last=0,b;
  while((b=in.read())!=-1){header.write(b);last=((last<<8)|b)&0xffffffff;if(last==0x0d0a0d0a)break;if(header.size()>8192)throw new IOException("Handshake too large");}
  String response=header.toString("US-ASCII");if(!response.startsWith("HTTP/1.1 101")&&!response.startsWith("HTTP/1.0 101"))throw new IOException("WebSocket handshake rejected");
  String expected=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-1").digest((base64+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("US-ASCII")),android.util.Base64.NO_WRAP);
  if(!response.toLowerCase(Locale.US).contains("sec-websocket-accept: "+expected.toLowerCase(Locale.US)))throw new IOException("Handshake verification failed");
  String subscribed=selected;send(out,1,"{\"op\":\"subscribe\",\"args\":[\"tickers.BTCUSDT\""+(subscribed.equals("BTCUSDT")?"":",\"tickers."+subscribed+"\"")+"]}");
  listener.status("LIVE: Bybit WebSocket conectat");long lastPing=System.currentTimeMillis();
  while(running&&!s.isClosed()){
   if(!selected.equals(subscribed))break;
   try{
    int opcode=in.read();if(opcode<0)break;int lengthByte=in.read();if(lengthByte<0)break;
    int type=opcode&15;long len=lengthByte&127;
    if(len==126)len=((long)readByte(in)<<8)|readByte(in);
    else if(len==127){len=0;for(int i=0;i<8;i++)len=(len<<8)|readByte(in);}
    if(len>1048576)throw new IOException("Oversized WS frame");
    byte[] mask=null;if((lengthByte&128)!=0){mask=new byte[4];readFully(in,mask);}
    byte[] data=new byte[(int)len];readFully(in,data);if(mask!=null)for(int i=0;i<data.length;i++)data[i]^=mask[i%4];
    if(type==8)break;if(type==9){send(out,10,data);continue;}if(type==1){JSONObject msg=new JSONObject(new String(data,"UTF-8"));
     String topic=msg.optString("topic");if(topic.startsWith("tickers.")){
      JSONObject ticker=msg.optJSONObject("data");if(ticker!=null){String name=topic.substring(8);
       double price=number(ticker.opt("lastPrice")),change=number(ticker.opt("price24hPcnt"))*100;
       if(price>0&&Double.isFinite(change))listener.ticker(name,price,change);
      }
     }
    }
   }catch(java.net.SocketTimeoutException ignored){}
   if(System.currentTimeMillis()-lastPing>20000){send(out,1,"{\"op\":\"ping\"}");lastPing=System.currentTimeMillis();}
  }
  s.close();socket=null;
 }
 private static double number(Object o){try{return Double.parseDouble(String.valueOf(o));}catch(Exception e){return Double.NaN;}}
 private static int readByte(InputStream in)throws IOException{int b=in.read();if(b<0)throw new EOFException();return b;}
 private static void readFully(InputStream in,byte[] b)throws IOException{int off=0;while(off<b.length){int n=in.read(b,off,b.length-off);if(n<0)throw new EOFException();off+=n;}}
 private void send(OutputStream out,int opcode,String message)throws IOException{send(out,opcode,message.getBytes("UTF-8"));}
 private synchronized void send(OutputStream out,int opcode,byte[] payload)throws IOException{
  if(payload.length>65535)throw new IOException("Frame too large");out.write(0x80|opcode);
  if(payload.length<126)out.write(0x80|payload.length);else{out.write(0xfe);out.write(payload.length>>>8);out.write(payload.length&255);}
  byte[] mask=new byte[4];random.nextBytes(mask);out.write(mask);for(int i=0;i<payload.length;i++)out.write(payload[i]^mask[i%4]);out.flush();
 }
}
