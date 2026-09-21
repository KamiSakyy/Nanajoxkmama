package com.kamisakyy.nanajoxkmama.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [PlaylistEntity::class, PlaylistTrackEntity::class, HistoryEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AniBeatDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AniBeatDatabase =
        Room.databaseBuilder(context, AniBeatDatabase::class.java, "anibeat.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun dao(db: AniBeatDatabase): LibraryDao = db.libraryDao()
}
