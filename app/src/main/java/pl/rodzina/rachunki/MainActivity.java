package pl.rodzina.rachunki;

import android.app.*;
import android.os.*;
import android.content.*;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import org.json.*;
import java.util.*;
import android.provider.Settings;
import android.net.Uri;
import android.content.pm.PackageManager;

public class MainActivity extends Activity {
 private WebView web;
 private FirebaseAuth auth;
 private FirebaseFirestore db;
 private ListenerRegistration dataListener, requestListener, memberListener;
 private String familyId="", currentData="[]";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private static final int NOTIFICATION_REQUEST=4101;

 @Override public void onCreate(Bundle b){super.onCreate(b);
  getWindow().setStatusBarColor(android.graphics.Color.rgb(245,247,243));
  getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  auth=FirebaseAuth.getInstance();db=FirebaseFirestore.getInstance();
  web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);
  web.getSettings().setAllowFileAccess(true);web.getSettings().setDomStorageEnabled(true);
  web.addJavascriptInterface(new Bridge(),"Android");
  web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){refreshState();}});
  setContentView(web);web.loadUrl("file:///android_asset/index.html");
  if(auth.getCurrentUser()!=null)initializeFamily();
 }
 private void js(String fn,String...args){StringBuilder sb=new StringBuilder(fn+"(");for(int i=0;i<args.length;i++){if(i>0)sb.append(',');sb.append(JSONObject.quote(args[i]));}sb.append(");");handler.post(()->web.evaluateJavascript(sb.toString(),null));}
 private String uid(){FirebaseUser u=auth.getCurrentUser();return u==null?"":u.getUid();}
 private String prefKey(){return "family_"+uid();}
 private void toast(String message){handler.post(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
 private void refreshState(){FirebaseUser u=auth.getCurrentUser();String name=u==null?"":(u.getDisplayName()==null||u.getDisplayName().isEmpty()?u.getEmail():u.getDisplayName());js("cloudAccount",u==null?"":name,familyId);}
 private void clearListeners(){if(dataListener!=null){dataListener.remove();dataListener=null;}if(requestListener!=null){requestListener.remove();requestListener=null;}if(memberListener!=null){memberListener.remove();memberListener=null;}}
 private void initializeFamily(){clearListeners();familyId=getPreferences(0).getString(prefKey(),"");if(!familyId.isEmpty())watchFamily();refreshState();}
 private void watchFamily(){if(uid().isEmpty()||familyId.isEmpty())return;clearListeners();String fid=familyId;
  memberListener=db.collection("families").document(fid).collection("members").document(uid()).addSnapshotListener((snap,err)->{
   if(!fid.equals(familyId))return;
   if(err!=null){js("cloudNotice","Brak dostępu do rodziny: "+err.getMessage());return;}
   if(snap==null||!snap.exists()){js("cloudNotice","Oczekiwanie na akceptację zaproszenia.");refreshState();return;}
   if(dataListener==null)dataListener=db.collection("families").document(fid).collection("data").document("bills").addSnapshotListener((doc,error)->{
    if(error!=null){js("cloudNotice","Błąd synchronizacji: "+error.getMessage());return;}
    currentData=doc!=null&&doc.exists()&&doc.getString("items")!=null?doc.getString("items"):"[]";
     ReminderScheduler.sync(MainActivity.this,currentData,fid,uid());
    js("cloudUpdated",currentData);
   });
   refreshState();
  });
  requestListener=db.collection("families").document(fid).collection("requests").addSnapshotListener((snap,err)->{
   if(err!=null||snap==null)return;JSONArray arr=new JSONArray();for(DocumentSnapshot d:snap.getDocuments()){
    try{JSONObject o=new JSONObject();o.put("uid",d.getId());o.put("name",d.getString("displayName"));arr.put(o);}catch(Exception ignored){}
   }js("cloudRequests",arr.toString());
  });
 }
 private String authError(Exception e){
  if(e instanceof FirebaseAuthWeakPasswordException)return "Hasło jest zbyt słabe. Użyj co najmniej 6 znaków.";
  if(e instanceof FirebaseAuthUserCollisionException)return "Ten adres e-mail jest już zarejestrowany.";
  if(e instanceof FirebaseAuthInvalidCredentialsException)return "Nieprawidłowy adres e-mail lub dane logowania.";
  if(e instanceof FirebaseAuthInvalidUserException)return "Nie znaleziono takiego konta.";
  return e==null?"Nieznany błąd":e.getLocalizedMessage();
 }
 private boolean validCredentials(String email,String password){
  if(email==null||!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){toast("Podaj poprawny adres e-mail.");return false;}
  if(password==null||password.length()<6){toast("Hasło musi mieć co najmniej 6 znaków.");return false;}
  return true;
 }
 private void register(String email,String password){if(!validCredentials(email,password))return;
  auth.createUserWithEmailAndPassword(email.trim(),password).addOnCompleteListener(this,t->{
   if(t.isSuccessful()){initializeFamily();toast("Konto utworzone. Możesz utworzyć rodzinę lub do niej dołączyć.");}
   else toast("Rejestracja: "+authError(t.getException()));
  });
 }
 private void login(String email,String password){if(!validCredentials(email,password))return;
  auth.signInWithEmailAndPassword(email.trim(),password).addOnCompleteListener(this,t->{
   if(t.isSuccessful()){initializeFamily();toast("Zalogowano pomyślnie");}
   else toast("Logowanie: "+authError(t.getException()));
  });
 }
 private void resetPassword(String email){
  if(email==null||!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){toast("Podaj poprawny adres e-mail.");return;}
  auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener(this,t->{
   if(t.isSuccessful())toast("Jeśli konto istnieje, sprawdź wiadomość e-mail z linkiem do zmiany hasła.");
   else toast("Nie udało się wysłać wiadomości: "+authError(t.getException()));
  });
 }
 private void createFamily(){if(uid().isEmpty()){toast("Najpierw zaloguj się");return;}String fid=UUID.randomUUID().toString().replace("-","");
  Map<String,Object> family=new HashMap<>();family.put("ownerUid",uid());family.put("createdAt",FieldValue.serverTimestamp());
  WriteBatch batch=db.batch();DocumentReference root=db.collection("families").document(fid);
  batch.set(root,family);Map<String,Object> member=new HashMap<>();member.put("name",auth.getCurrentUser().getEmail());
  batch.set(root.collection("members").document(uid()),member);
  batch.commit().addOnSuccessListener(x->{setFamily(fid);toast("Utworzono rodzinę. Udostępnij kod żonie.");}).addOnFailureListener(e->toast("Błąd: "+e.getMessage()));
 }
 private void setFamily(String fid){ReminderScheduler.clear(this);familyId=fid;getPreferences(0).edit().putString(prefKey(),fid).apply();currentData="[]";js("cloudUpdated","[]");watchFamily();refreshState();}
 private void joinFamily(String code){if(uid().isEmpty()){toast("Najpierw zaloguj się");return;}String fid=code.trim();if(!fid.matches("[a-f0-9]{32}")){toast("Nieprawidłowy kod rodziny");return;}
  Map<String,Object> m=new HashMap<>();m.put("displayName",auth.getCurrentUser().getEmail());
  db.collection("families").document(fid).collection("requests").document(uid()).set(m).addOnSuccessListener(x->{setFamily(fid);toast("Wysłano prośbę. Poczekaj na zatwierdzenie.");}).addOnFailureListener(e->toast("Nie udało się wysłać prośby: "+e.getMessage()));
 }
 private void approve(String userId){if(uid().isEmpty()||familyId.isEmpty()||userId.equals(uid()))return;DocumentReference root=db.collection("families").document(familyId);
  root.get().addOnSuccessListener(doc->{if(!uid().equals(doc.getString("ownerUid")))return;
   DocumentReference request=root.collection("requests").document(userId);
   request.get().addOnSuccessListener(r->{if(!r.exists())return;
    Map<String,Object> member=new HashMap<>();member.put("name",r.getString("displayName"));WriteBatch b=db.batch();b.set(root.collection("members").document(userId),member);b.delete(request);
    b.commit().addOnSuccessListener(x->toast("Zaproszenie zaakceptowane")).addOnFailureListener(e->toast("Błąd: "+e.getMessage()));
   });
  });
 }
 public class Bridge {
  @JavascriptInterface public String load(){return currentData;}
  @JavascriptInterface public String save(String json){if(uid().isEmpty()||familyId.isEmpty())return "Zaloguj się i dołącz do rodziny";
   try{new JSONArray(json);}catch(Exception e){return "Błędne dane";}
   Map<String,Object> m=new HashMap<>();m.put("items",json);
   db.collection("families").document(familyId).collection("data").document("bills").set(m).addOnFailureListener(e->js("cloudNotice","Nie zapisano: "+e.getMessage()));
   return "ok";
  }
  @JavascriptInterface public String permissions(){
   boolean notifications=android.os.Build.VERSION.SDK_INT<33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED;
   AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
   boolean exact=android.os.Build.VERSION.SDK_INT<31 || (am!=null&&am.canScheduleExactAlarms());
   return "{\"notifications\":"+notifications+",\"exact\":"+exact+"}";
  }
  @JavascriptInterface public void requestNotifications(){handler.post(()->{
   if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){
    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},NOTIFICATION_REQUEST);
   }else{
    Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
    i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);
   }
  });}
  @JavascriptInterface public void requestExact(){handler.post(()->{
   if(android.os.Build.VERSION.SDK_INT>=31){
    AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
    if(am!=null&&!am.canScheduleExactAlarms()){
     try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}
     catch(Exception e){toast("Nie można otworzyć ustawień dokładnych alarmów.");}
    }
   }
  });}
  @JavascriptInterface public void exportData(){toast("Kopia: dane synchronizują się z Firebase");}
  @JavascriptInterface public void importData(){toast("Import danych nie jest dostępny w wersji projektowej");}
  @JavascriptInterface public void finishApp(){handler.post(()->finish());}
  @JavascriptInterface public void signIn(String email,String password){handler.post(()->MainActivity.this.login(email,password));}
  @JavascriptInterface public void register(String email,String password){handler.post(()->MainActivity.this.register(email,password));}
  @JavascriptInterface public void resetPassword(String email){handler.post(()->MainActivity.this.resetPassword(email));}
  @JavascriptInterface public void signOut(){handler.post(()->{clearListeners();auth.signOut();familyId="";currentData="[]";js("cloudUpdated","[]");refreshState();});}
  @JavascriptInterface public void createFamily(){toast("Użyj istniejącego kodu rodziny z Rodzinnych wizyt.");}
  @JavascriptInterface public void joinFamily(String code){handler.post(()->MainActivity.this.joinFamily(code));}
  @JavascriptInterface public void approveMember(String id){handler.post(()->MainActivity.this.approve(id));}
  @JavascriptInterface public void pickDate(String date){handler.post(()->{
   try{String[] p=date.split("-");DatePickerDialog d=new DatePickerDialog(MainActivity.this,(v,y,m,day)->js("nativeDate",String.format(Locale.US,"%04d-%02d-%02d",y,m+1,day)),Integer.parseInt(p[0]),Integer.parseInt(p[1])-1,Integer.parseInt(p[2]));d.show();}catch(Exception ignored){}
  });}
  @JavascriptInterface public void pickTime(String time){handler.post(()->{
   try{String[] p=time.split(":");new TimePickerDialog(MainActivity.this,(v,h,m)->js("nativeTime",String.format(Locale.US,"%02d:%02d",h,m)),Integer.parseInt(p[0]),Integer.parseInt(p[1]),true).show();}catch(Exception ignored){}
  });}
 }
 @Override protected void onResume(){super.onResume();if(web!=null)handler.postDelayed(()->web.evaluateJavascript("nativeResume()",null),250);}
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults);
  if(requestCode==NOTIFICATION_REQUEST&&web!=null)handler.post(()->web.evaluateJavascript("nativeResume()",null));
 }
 @Override public void onBackPressed(){web.evaluateJavascript("goBack()",null);}
 @Override protected void onDestroy(){clearListeners();super.onDestroy();}
}