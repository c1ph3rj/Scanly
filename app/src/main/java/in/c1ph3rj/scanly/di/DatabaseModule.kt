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
import `in`.c1ph3rj.scanly.data.local.db.dao.PageMarkupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.PageTextDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.dao.SignatureDao
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
        ScanlyDatabase.MIGRATION_3_4,
        ScanlyDatabase.MIGRATION_4_5,
        ScanlyDatabase.MIGRATION_5_6,
        ScanlyDatabase.MIGRATION_6_7,
        ScanlyDatabase.MIGRATION_7_8,
        ScanlyDatabase.MIGRATION_8_9,
        ScanlyDatabase.MIGRATION_9_10,
        ScanlyDatabase.MIGRATION_10_11,
    ).addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(ScanlyDatabase.CREATE_PAGE_TEXT_TERMS)
        }
    }).build()

    @Provides
    fun provideDocumentDao(database: ScanlyDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideScanPageDao(database: ScanlyDatabase): ScanPageDao = database.scanPageDao()

    @Provides
    fun provideDocumentGroupDao(database: ScanlyDatabase): DocumentGroupDao =
        database.documentGroupDao()

    @Provides
    fun providePageTextDao(database: ScanlyDatabase): PageTextDao = database.pageTextDao()

    @Provides
    fun provideSignatureDao(database: ScanlyDatabase): SignatureDao = database.signatureDao()

    @Provides
    fun providePageMarkupDao(database: ScanlyDatabase): PageMarkupDao = database.pageMarkupDao()

    private const val DATABASE_NAME = "scanly.db"
}
