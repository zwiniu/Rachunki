package pl.rodzina.rachunki;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ReminderScheduler {
 private static final String PREF="bill_reminders", DATA="data", FAMILY="family", USER="user";
 private static final String ACTION="pl.rodzina.rachunki.REMINDER";
 private ReminderScheduler(){}
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
 private static String key(String family,String user,String id){return family+":"+user+":"+id;}
 private static PendingIntent alarmIntent(Context c,String key,JSONObject v){
  Intent i=new Intent(c,ReminderReceiver.class).setAction(ACTION).setData(Uri.parse("reminder://"+Uri.encode(key)));
  i.putExtra("id",key.hashCode()&0x7fffffff).putExtra("name",v.optString("name")).putExtra("amount",v.optString("amount")).putExtra("date",v.optString("date")).putExtra("time",v.optString("time"));
  return PendingIntent.getBroadcast(c,key.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 private static void cancelAll(Context c){
  try{
   JSONArray old=new JSONArray(prefs(c).getString(DATA,"[]"));String f=prefs(c).getString(FAMILY,"");String u=prefs(c).getString(USER,"");
   AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
   for(int j=0;j<old.length();j++){JSONObject v=old.optJSONObject(j);if(v!=null)am.cancel(alarmIntent(c,key(f,u,v.optString("id")),v));}
  }catch(Exception ignored){}
 }
 public static synchronized void clear(Context c){cancelAll(c);prefs(c).edit().clear().apply();}
 public static synchronized void sync(Context c,String json,String family,String user){
  if(family==null||family.isEmpty()||user==null||user.isEmpty())return;
  try{new JSONArray(json);}catch(Exception e){return;}
  cancelAll(c);
  prefs(c).edit().putString(DATA,json).putString(FAMILY,family).putString(USER,user).apply();
  schedule(c,json,family,user);
 }
 public static synchronized void restore(Context c){
  String data=prefs(c).getString(DATA,null);if(data!=null)schedule(c,data,prefs(c).getString(FAMILY,""),prefs(c).getString(USER,""));
 }
 private static void schedule(Context c,String json,String family,String user){
  try{
   AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
   JSONArray items=new JSONArray(json);long now=System.currentTimeMillis();
   for(int j=0;j<items.length();j++){
    JSONObject v=items.optJSONObject(j);if(v==null||v.optBoolean("done",false))continue;
    int minutes=v.optInt("reminder",-1);if(minutes<0)continue;
    String date=v.optString("date"),time=v.optString("time"),id=v.optString("id");if(id.isEmpty())continue;
    SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);format.setLenient(false);
    Date parsed=format.parse(date+" 09:00");if(parsed==null)continue;
    long when=parsed.getTime()-minutes*60000L;if(when<=now)continue;
    PendingIntent pi=alarmIntent(c,key(family,user,id),v);
    if(Build.VERSION.SDK_INT>=31&&am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    else if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    else am.set(AlarmManager.RTC_WAKEUP,when,pi);
   }
  }catch(Exception ignored){}
 }
}
