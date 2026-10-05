package com.dlink.app
import android.app.*
import android.content.Intent
import android.os.IBinder
class ShareForegroundService:Service(){override fun onStartCommand(i:Intent?,f:Int,s:Int):Int{val c=NotificationChannel("dlink_share","Dlink Share",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(c);val n=Notification.Builder(this,"dlink_share").setContentTitle("Dlink Share Mode").setContentText("Temporary nearby sharing is active.").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build();startForeground(1001,n);return START_NOT_STICKY};override fun onBind(i:Intent?):IBinder?=null}
