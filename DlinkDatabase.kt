package com.dlink.app.data
import android.content.Context
import androidx.room.*
@Database(entities=[ProfileEntity::class,ConnectionEntity::class],version=1,exportSchema=false)
abstract class DlinkDatabase:RoomDatabase(){ abstract fun profileDao():ProfileDao; abstract fun connectionDao():ConnectionDao
 companion object { @Volatile private var INSTANCE:DlinkDatabase?=null; fun get(c:Context)=INSTANCE?:synchronized(this){INSTANCE?:Room.databaseBuilder(c.applicationContext,DlinkDatabase::class.java,"dlink.db").fallbackToDestructiveMigration().build().also{INSTANCE=it}} } }
