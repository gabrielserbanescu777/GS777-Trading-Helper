package ro.gs777.trading;
import android.content.SharedPreferences;
import android.content.Context;
import android.graphics.Color;
import android.widget.CheckBox;
import android.widget.LinearLayout;

/** Public YouTube channel IDs checked against their channel pages. */
final class YoutubeChannels {
 static final String[] NAMES={
  "Coin Bureau","Altcoin Daily","Brian Jung","Crypto Banter","Benjamin Cowen",
  "CryptosRUs","Crypto Casey","The Moon","Lark Davis","99Bitcoins",
  "Finematics","aantonop / Andreas Antonopoulos","Michael Wrubel","Bankless","Crypto Jebb",
  "Ivan on Tech","Crypto Kirby","DataDash","Andrei Jikh","Jacob Crypto Bury",
  "Whiteboard Crypto","The Defiant","CoinDesk","Unchained","Anthony Pompliano",
  "Natalie Brunell","CryptoWendyO","Bitcoin Magazine","Blockchain Romania","CryptoAce"
 };
 static final String[] IDS={
  "UCqK_GSMbpiV8spgD3ZGloSw","UCbLhGKVY-bJPcawebgtNfbw","UCQglaVhGOBI0BR5S6IJnQPg","UCN9Nj4tjXbVTLYWN0EKly_Q","UCRvqjQPSeaWn-uEx-w0XOIg",
  "UCpwd5EGZWvgVoPwMhIsve5g","UCi7RBPfTtRkVchV6qO8PUzg","UCc4Rz_T9Sb1w5rqqo9pL1Og","UCl2oCaw8hdR_kbqyqd2klIA","UC0Sk-PL9_KQFaBu7TY_h57Q",
  "UCh1ob28ceGdqohUnR7vBACA","UCnJ_qo2QTvRYWGS2e4j1oAQ","UClWUQqWTL6xSK2Bx1bRlKPw","UCAl9Ld79qaZxp9JzEOwd3aA","UCviqt5aaucA1jP3qFmorZLQ",
  "UCrYmtJBtLdtm2ov84ulV-yg","UCOaew10hdmtfa0MinTjOBqg","UCCatR7nWbYrkVXdxXb4cGXw","UCGy7SkBjcIAgTiwkXEtPnYg","UCw2OA30Mobztf52r-dpYYTA",
  "UCsYYksPHiGqXHPoHI-fm5sg","UCL0J4MLEdLP0-UyLu0hCktg","UC7TghOL755nBk7HelHoi9LQ","UCWiiMnsnw5Isc2PP1to9nNw","UCevXpeL8cNyAnww-NqJ4m2w",
  "UCru3nlhzHrbgK21x0MdB_eg","UCla2jS8BrfLJj7kbKyy5_ew","UCtOV5M-T3GcsJAq8QKaf0lg","UCF7UtZO9UVq6YpOcfm4lqOg","UCEfEvGzPMiuyLcdO4SM9aYw"
 };
 static String selectionKey(String id){return "yt_channel_"+id;}
 static CheckBox[] addChoices(Context context,LinearLayout box,SharedPreferences prefs){
  CheckBox[] controls=new CheckBox[IDS.length];
  for(int i=0;i<IDS.length;i++){
   CheckBox cb=new CheckBox(context);
   cb.setText(NAMES[i]);cb.setTextColor(Color.WHITE);cb.setTextSize(15);
   cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(241,193,80)));
   cb.setChecked(prefs.getBoolean(selectionKey(IDS[i]),true));
   box.addView(cb);controls[i]=cb;
  }
  return controls;
 }
 static String selected(SharedPreferences prefs){
  StringBuilder csv=new StringBuilder();
  for(String id:IDS)if(prefs.getBoolean(selectionKey(id),true)){if(csv.length()>0)csv.append(',');csv.append(id);}
  String extra=prefs.getString("youtubeChannelsCustom","");
  for(String id:extra.split(",")){
   if(id.matches("UC[A-Za-z0-9_-]{22}")&&!csv.toString().contains(id)){if(csv.length()>0)csv.append(',');csv.append(id);}
  }
  return csv.toString();
 }
}
