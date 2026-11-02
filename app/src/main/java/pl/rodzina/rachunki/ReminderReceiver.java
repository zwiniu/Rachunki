package pl.rodzina.rachunki;

import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.PackageManager;

public class ReminderReceiver extends android.content.BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent==null||!"pl.rodzina.rachunki.REMINDER".equals(intent.getAction()))return;
  if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
  String person=intent.getStringExtra("name"), specialty=intent.getStringExtra("amount"),date=intent.getStringExtra("date"),time=intent.getStringExtra("time");
  if(person==null||specialty==null)return;
  NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
  String channel="bills";
  if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(channel,"Przypomnienia o rachunkach",NotificationManager.IMPORTANCE_HIGH));
  Intent open=new Intent(context,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent click=PendingIntent.getActivity(context,intent.getIntExtra("id",0),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,channel):new Notification.Builder(context);
  b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Rachunek: "+person).setContentText(specialty+" zł · termin "+date).setStyle(new Notification.BigTextStyle().bigText("Rachunek: "+person+" · "+specialty+" zł · termin "+date)).setAutoCancel(true).setContentIntent(click).setDefaults(Notification.DEFAULT_ALL);
  manager.notify(intent.getIntExtra("id",0),b.build());
 }
}
