package com.example.aegis.di

import android.content.Context
import androidx.room.Room
import com.example.aegis.data.crypto.CryptoManager
import com.example.aegis.data.db.AegisDatabase
import com.example.aegis.data.db.dao.AllergyDao
import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.DocumentDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.dao.PatientDao
import com.example.aegis.data.db.dao.VisitLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAegisDatabase(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager,
    ): AegisDatabase {
        // Passphrase is in memory only — set by CryptoManager after BiometricPrompt succeeds.
        // This provider is called lazily by Hilt the first time any DAO is requested,
        // which only happens after successful auth (Lock screen navigates away first).
        val factory = SupportFactory(cryptoManager.getPassphrase())
        return Room.databaseBuilder(context, AegisDatabase::class.java, "aegis.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

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
}
