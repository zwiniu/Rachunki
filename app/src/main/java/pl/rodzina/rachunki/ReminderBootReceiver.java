package pl.rodzina.rachunki;
import android.content.*;
public class ReminderBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent!=null&&Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))ReminderScheduler.restore(context);
 }
}
