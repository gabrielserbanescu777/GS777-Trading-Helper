package ro.gs777.trading;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BybitSymbolService extends AccessibilityService {
 static volatile BybitSymbolService instance;
 private static final Pattern PAIR=Pattern.compile("(?i)(?<![A-Z0-9])([A-Z0-9]{2,15})\\s*[/_-]?\\s*USDT(?![A-Z0-9])");
 private static String last="";
 private static long lastAt=0;
 private static boolean isBybit(String name){return "com.bybit.app".equals(name)||"com.bybit.eu".equals(name);}
 @Override protected void onServiceConnected(){
  instance=this;
  AccessibilityServiceInfo info=getServiceInfo();
  if(info!=null){info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;setServiceInfo(info);}
 }
 @Override public void onAccessibilityEvent(AccessibilityEvent e){
  if(e==null||e.getPackageName()==null||!isBybit(e.getPackageName().toString()))return;
  String current=readWindow();if(current!=null){last=current;lastAt=System.currentTimeMillis();}
 }
 static String fetch(){
  BybitSymbolService service=instance;if(service==null)return null;
  String current=service.readWindow();
  if(current!=null){last=current;lastAt=System.currentTimeMillis();return current;}
  return System.currentTimeMillis()-lastAt<10000&&!last.isEmpty()?last:null;
 }
 private String readWindow(){
  try{
   for(AccessibilityWindowInfo window:getWindows()){
    AccessibilityNodeInfo root=window.getRoot();
    if(root==null||root.getPackageName()==null||!isBybit(root.getPackageName().toString()))continue;
    ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();nodes.add(root);
    for(int count=0;!nodes.isEmpty()&&count<100;count++){
     AccessibilityNodeInfo node=nodes.remove();String found=match(node.getText());
     if(found==null)found=match(node.getContentDescription());
     if(found!=null)return found;
     for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)nodes.add(child);}
    }
   }
  }catch(Exception ignored){}
  return null;
 }
 private static String match(CharSequence text){
  if(text==null)return null;
  Matcher matcher=PAIR.matcher(text);if(!matcher.find())return null;
  String base=matcher.group(1).toUpperCase(Locale.ROOT);
  return base.equals("USDT")?null:base+"USDT";
 }
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){instance=null;last="";lastAt=0;super.onDestroy();}
}
