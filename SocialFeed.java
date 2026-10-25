package ro.gs777.trading;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.regex.Pattern;

final class SocialFeed {
 static final class Item {
  final String source,title,date,url;
  Item(String source,String title,String date,String url){this.source=source;this.title=title;this.date=date;this.url=url;}
 }
 static final class Opinion {
  String direction="ASTEAPTA";int positive,negative,relevant,forums,news,forumPositive,forumNegative;
 }
 static final Pattern UP=Pattern.compile("\\b(bullish|breakout|rally|rallies|surge|surges|uptrend|gains|rises)\\b");
 static final Pattern DOWN=Pattern.compile("\\b(bearish|breakdown|crash|crashes|plunge|plunges|downtrend|falls|drops)\\b");
 static boolean fresh(String date){
  String[] patterns={"EEE, dd MMM yyyy HH:mm:ss Z","EEE, dd MMM yyyy HH:mm:ss zzz","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"};
  for(String pattern:patterns)try{
   java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat(pattern,Locale.US);fmt.setLenient(false);
   if(pattern.endsWith("'Z'"))fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
   java.util.Date parsed=fmt.parse(date);long age=System.currentTimeMillis()-parsed.getTime();
   return age>=-10*60*1000&&age<=7L*24*60*60*1000;
  }catch(Exception ignored){}
  return false;
 }
 static List<Item> relevantItems(List<Item> items,String symbol){
  ArrayList<Item> out=new ArrayList<>();
  String coin=symbol.toUpperCase(Locale.ROOT);
  String alias=coin.equals("BTC")?"BITCOIN":coin.equals("ETH")?"ETHEREUM":coin.equals("SOL")?"SOLANA":coin.equals("XRP")?"RIPPLE":coin;
  Pattern relevant=Pattern.compile("(?<![A-Z0-9])(?:"+Pattern.quote(coin)+"|"+Pattern.quote(alias)+")(?![A-Z0-9])");
  HashSet<String> seen=new HashSet<>();
  for(Item item:items){
   if(!fresh(item.date)||!relevant.matcher(item.title.toUpperCase(Locale.ROOT)).find())continue;
   if(!seen.add(item.title.toUpperCase(Locale.ROOT)))continue;
   out.add(item);if(out.size()>=8)break;
  }
  return out;
 } static Opinion opinion(List<Item> items,String symbol){
  Opinion result=new Opinion();
  String coin=symbol.toUpperCase(Locale.ROOT);
  String alias=coin.equals("BTC")?"BITCOIN":coin.equals("ETH")?"ETHEREUM":coin.equals("SOL")?"SOLANA":coin.equals("XRP")?"RIPPLE":coin;
  Pattern relevant=Pattern.compile("(?<![A-Z0-9])(?:"+Pattern.quote(coin)+"|"+Pattern.quote(alias)+")(?![A-Z0-9])");
  HashSet<String> unique=new HashSet<>();
  for(Item item:items){
   if(!fresh(item.date))continue;
   String title=item.title.toUpperCase(Locale.ROOT);
   if(!relevant.matcher(title).find()||!unique.add(title))continue;
   result.relevant++;
   if(item.source.startsWith("Reddit"))result.forums++;else result.news++;
   String normalized=title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ");
   boolean up=UP.matcher(normalized).find(),down=DOWN.matcher(normalized).find();
   if(up&&!down){result.positive++;if(item.source.startsWith("Reddit"))result.forumPositive++;}
   if(down&&!up){result.negative++;if(item.source.startsWith("Reddit"))result.forumNegative++;}
  }
  int directional=result.positive+result.negative;
  if(directional>=3&&result.positive-result.negative>=2)result.direction="LONG";
  else if(directional>=3&&result.negative-result.positive>=2)result.direction="SHORT";
  return result;
 } static List<Item> rss(String address,String source,int limit)throws Exception {
  HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
  connection.setConnectTimeout(7000);connection.setReadTimeout(8000);
  connection.setRequestProperty("User-Agent","Mozilla/5.0 (compatible; GS777Trading/0.12)");
  try {
   if(connection.getResponseCode()!=200)throw new Exception("HTTP "+connection.getResponseCode());
   List<Item> items=new ArrayList<>();
   try(InputStream stream=connection.getInputStream()) {
    XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
    parser.setInput(stream,"UTF-8");boolean inside=false;String title="",date="",link="";
    int event=parser.getEventType();
    while(event!=XmlPullParser.END_DOCUMENT&&items.size()<limit){
     if(event==XmlPullParser.START_TAG){
      String name=parser.getName();
      if(name.equals("item")||name.equals("entry")){inside=true;title="";date="";link="";}
      else if(inside&&name.equals("link")&&parser.getAttributeValue(null,"href")!=null){link=parser.getAttributeValue(null,"href");}
      else if(inside&&(name.equals("title")||name.equals("pubDate")||name.equals("updated")||name.equals("link"))){
       String value=parser.nextText();
       if(name.equals("title"))title=value;
       else if(name.equals("pubDate")||name.equals("updated"))date=value;
       else link=value;
      }
     }else if(event==XmlPullParser.END_TAG&&(parser.getName().equals("item")||parser.getName().equals("entry"))){
      inside=false;
      if(!title.isEmpty()&&!link.isEmpty()&&link.startsWith("https://"))items.add(new Item(source,title,date,link));
     }
     event=parser.next();
    }
   }
   return items;
  }finally{connection.disconnect();}
 }
 static List<Item> news(String coin)throws Exception {
  String query=URLEncoder.encode("\""+coin+"\" crypto when:7d","UTF-8");
  return rss("https://news.google.com/rss/search?q="+query+"&hl=en-US&gl=US&ceid=US:en","Stiri",8);
 }
 static List<Item> forumGeneral(String coin)throws Exception {
  String query=URLEncoder.encode(coin+" crypto","UTF-8");
  return rss("https://www.reddit.com/search.rss?q="+query+"&sort=new&t=week","Reddit global",8);
 }
 static List<Item> forum(String coin)throws Exception {
  String query=URLEncoder.encode(coin+" crypto","UTF-8");
  return rss("https://www.reddit.com/r/CryptoCurrency/search.rss?q="+query+"&restrict_sr=1&sort=new&t=week","Reddit",8);
 }
}
