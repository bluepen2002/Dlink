package com.dlink.app.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Entity(tableName="profiles") data class ProfileEntity(@PrimaryKey val id:String,val name:String,val title:String="",val company:String="",val bio:String="",val profileType:String="professional",val isDefault:Boolean=true,val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="connections") data class ConnectionEntity(@PrimaryKey val id:String,val name:String,val title:String="",val company:String="",val method:String,val createdAt:Long=System.currentTimeMillis(),val note:String="")
@Dao interface ProfileDao { @Query("SELECT * FROM profiles WHERE isDefault=1 LIMIT 1") fun observeDefault():Flow<ProfileEntity?>; @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(p:ProfileEntity) }
@Dao interface ConnectionDao { @Query("SELECT * FROM connections ORDER BY createdAt DESC") fun observeAll():Flow<List<ConnectionEntity>>; @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insert(c:ConnectionEntity) }
