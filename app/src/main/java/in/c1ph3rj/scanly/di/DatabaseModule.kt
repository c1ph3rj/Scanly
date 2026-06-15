package `in`.c1ph3rj.scanly.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideScanlyDatabase(
        @ApplicationContext context: Context,
    ): ScanlyDatabase = Room.databaseBuilder(
        context,
        ScanlyDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(
        ScanlyDatabase.MIGRATION_1_2,
        ScanlyDatabase.MIGRATION_2_3,
    ).addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }).build()

    @Provides
    fun provideDocumentDao(database: ScanlyDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideScanPageDao(database: ScanlyDatabase): ScanPageDao = database.scanPageDao()

    @Provides
    fun provideDocumentGroupDao(database: ScanlyDatabase): DocumentGroupDao =
        database.documentGroupDao()

    private const val DATABASE_NAME = "scanly.db"
}
