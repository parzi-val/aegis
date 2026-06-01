package com.example.aegis.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aegis.data.crypto.CryptoManager
import com.example.aegis.data.db.AegisDatabase
import com.example.aegis.data.db.dao.AllergyDao
import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.ConditionSuggestionDao
import com.example.aegis.data.db.dao.DocumentDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.dao.MedicationSuggestionDao
import com.example.aegis.data.db.dao.PatientDao
import com.example.aegis.data.db.dao.ShareAuditDao
import com.example.aegis.data.db.dao.VisitLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `share_audit` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`sessionId` TEXT NOT NULL, " +
            "`documentId` INTEGER NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, " +
            "`action` TEXT NOT NULL, " +
            "`deviceHint` TEXT NOT NULL, " +
            "`userNote` TEXT NOT NULL)"
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `condition_suggestion` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, " +
            "`sourceDocumentId` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`dismissed` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `medication_suggestion` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, " +
            "`dosage` TEXT NOT NULL, " +
            "`frequency` TEXT NOT NULL, " +
            "`sourceDocumentId` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`dismissed` INTEGER NOT NULL)"
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAegisDatabase(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager,
    ): AegisDatabase {
        val factory = SupportFactory(cryptoManager.getPassphrase())
        return Room.databaseBuilder(context, AegisDatabase::class.java, "aegis.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // readTimeout intentionally not set here — AstpService creates a no-timeout
            // client for SSE via newBuilder() when needed
            .build()

    @Provides @Singleton
    fun providePatientDao(db: AegisDatabase): PatientDao = db.patientDao()

    @Provides @Singleton
    fun provideConditionDao(db: AegisDatabase): ConditionDao = db.conditionDao()

    @Provides @Singleton
    fun provideMedicationDao(db: AegisDatabase): MedicationDao = db.medicationDao()

    @Provides @Singleton
    fun provideAllergyDao(db: AegisDatabase): AllergyDao = db.allergyDao()

    @Provides @Singleton
    fun provideDocumentDao(db: AegisDatabase): DocumentDao = db.documentDao()

    @Provides @Singleton
    fun provideVisitLogDao(db: AegisDatabase): VisitLogDao = db.visitLogDao()

    @Provides @Singleton
    fun provideConditionSuggestionDao(db: AegisDatabase): ConditionSuggestionDao = db.conditionSuggestionDao()

    @Provides @Singleton
    fun provideMedicationSuggestionDao(db: AegisDatabase): MedicationSuggestionDao = db.medicationSuggestionDao()

    @Provides @Singleton
    fun provideShareAuditDao(db: AegisDatabase): ShareAuditDao = db.shareAuditDao()
}
