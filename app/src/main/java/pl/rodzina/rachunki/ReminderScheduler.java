package pl.rodzina.rachunki;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ReminderScheduler {
 private static final String PREF="bill_reminders", DATA="data", FAMILY="family", USER="user";
 private static final String ACTION="pl.rodzina.rachunki.REMINDER";
 private static boolean synced;
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
  JSONArray items;
  try{items=new JSONArray(json);}catch(JSONException e){return;}
  String oldFamily=prefs(c).getString(FAMILY,"");
  String oldUser=prefs(c).getString(USER,"");
  Map<String,JSONObject> previous=Collections.emptyMap();
  if(family.equals(oldFamily)&&user.equals(oldUser)){
   try{previous=activeBills(new JSONArray(prefs(c).getString(DATA,"[]")));}catch(JSONException ignored){}
  }else{
   cancelAll(c);
  }
  Map<String,JSONObject> updated=activeBills(items);
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  for(Map.Entry<String,JSONObject> bill:previous.entrySet()){
   String id=bill.getKey();
   if(!sameReminder(bill.getValue(),updated.get(id))){
    am.cancel(alarmIntent(c,key(family,user,id),bill.getValue()));
   }
  }
  prefs(c).edit().putString(DATA,json).putString(FAMILY,family).putString(USER,user).apply();
  for(Map.Entry<String,JSONObject> bill:updated.entrySet()){
   String id=bill.getKey();
   // The first snapshot can arrive after a force-stop cleared the system alarms.
   if(!synced||!sameReminder(bill.getValue(),previous.get(id))){
    scheduleBill(c,am,key(family,user,id),bill.getValue());
   }
  }
  synced=true;
 }
 public static synchronized void restore(Context c){
  String data=prefs(c).getString(DATA,null);
  if(data==null)return;
  String family=prefs(c).getString(FAMILY,"");
  String user=prefs(c).getString(USER,"");
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  try{
   for(Map.Entry<String,JSONObject> bill:activeBills(new JSONArray(data)).entrySet()){
    scheduleBill(c,am,key(family,user,bill.getKey()),bill.getValue());
   }
  }catch(JSONException ignored){}
 }
 private static Map<String,JSONObject> activeBills(JSONArray bills){
  Map<String,JSONObject> result=new LinkedHashMap<>();
  for(int i=0;i<bills.length();i++){
   JSONObject bill=bills.optJSONObject(i);
   if(bill==null||bill.optBoolean("done",false)||bill.optInt("reminder",-1)<0)continue;
   String id=bill.optString("id");
   if(!id.isEmpty())result.put(id,bill);
  }
  return result;
 }
 private static boolean sameReminder(JSONObject bill,JSONObject other){
  return other!=null
   && bill.optInt("reminder",-1)==other.optInt("reminder",-1)
   && bill.optString("date").equals(other.optString("date"))
   && bill.optString("time").equals(other.optString("time"))
   && bill.optString("name").equals(other.optString("name"))
   && bill.optString("amount").equals(other.optString("amount"));
 }
 private static void scheduleBill(Context c,AlarmManager am,String key,JSONObject bill){
  try{
   SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);format.setLenient(false);
   Date due=format.parse(bill.optString("date")+" 09:00");
   long when=due.getTime()-bill.optInt("reminder",-1)*60000L;
   if(when<=System.currentTimeMillis())return;
   PendingIntent pi=alarmIntent(c,key,bill);
   if(Build.VERSION.SDK_INT>=31&&am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
   else if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
   else am.set(AlarmManager.RTC_WAKEUP,when,pi);
  }catch(ParseException|RuntimeException ignored){}
 }
}
