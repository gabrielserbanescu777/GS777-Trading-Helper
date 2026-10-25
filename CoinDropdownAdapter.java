package ro.gs777.trading;
import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CoinDropdownAdapter extends BaseAdapter implements Filterable {
 private final Context context;
 private volatile List<Market.Coin> all=new ArrayList<>(),shown=new ArrayList<>();
 private final Filter filter=new Filter(){
  @Override protected FilterResults performFiltering(CharSequence constraint){
   String query=constraint==null?"":constraint.toString().trim().toUpperCase(Locale.ROOT);
   ArrayList<Market.Coin> found=new ArrayList<>();
   for(Market.Coin coin:all){
    if(query.isEmpty()||coin.symbol.contains(query))found.add(coin);
   }
   FilterResults results=new FilterResults();results.values=found;results.count=found.size();return results;
  }
  @Override protected void publishResults(CharSequence constraint,FilterResults results){
   @SuppressWarnings("unchecked") List<Market.Coin> list=(List<Market.Coin>)results.values;
   shown=list==null?new ArrayList<>():list;notifyDataSetChanged();
  }
 };
 CoinDropdownAdapter(Context app){context=app;}
 void replace(List<Market.Coin> coins,CharSequence current){
  all=new ArrayList<>(coins);filter.filter(current);
 }
 int totalCount(){return all.size();}
 Market.Coin find(String symbol){for(Market.Coin coin:all)if(coin.symbol.equals(symbol))return coin;return null;}
 @Override public int getCount(){return shown.size();}
 @Override public Market.Coin getItem(int index){return shown.get(index);}
 @Override public long getItemId(int index){return index;}
 @Override public View getView(int index,View recycled,ViewGroup parent){
  TextView row=recycled instanceof TextView?(TextView)recycled:new TextView(context);
  Market.Coin coin=getItem(index);
  String label=String.format(Locale.US,"%s   %+.2f%% / 24h",coin.symbol,coin.change);
  SpannableString styled=new SpannableString(label);
  styled.setSpan(new ForegroundColorSpan(coin.change>=0?Color.rgb(111,228,163):Color.rgb(249,121,134)),
   coin.symbol.length()+3,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  row.setText(styled);row.setTextColor(Color.rgb(241,193,80));row.setTextSize(16);
  int space=(int)(12*context.getResources().getDisplayMetrics().density);
  row.setPadding(space,space,space,space);row.setBackgroundColor(Color.rgb(29,25,19));
  return row;
 }
 @Override public Filter getFilter(){return filter;}
}
