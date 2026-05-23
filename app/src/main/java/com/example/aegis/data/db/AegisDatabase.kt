package com.example.aegis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.aegis.data.db.dao.AllergyDao
import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.DocumentDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.dao.PatientDao
import com.example.aegis.data.db.dao.VisitLogDao
import com.example.aegis.data.db.entity.AllergyEntity
import com.example.aegis.data.db.entity.ConditionEntity
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.db.entity.MedicationEntity
import com.example.aegis.data.db.entity.PatientEntity
import com.example.aegis.data.db.entity.VisitLogEntity

@Database(
    entities = [
        PatientEntity::class,
        ConditionEntity::class,
        MedicationEntity::class,
        AllergyEntity::class,
        DocumentEntity::class,
        VisitLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AegisDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun conditionDao(): ConditionDao
    abstract fun medicationDao(): MedicationDao
    abstract fun allergyDao(): AllergyDao
    abstract fun documentDao(): DocumentDao
    abstract fun visitLogDao(): VisitLogDao
}
