package com.example.aegis.data.repository

import com.example.aegis.data.db.dao.VisitLogDao
import com.example.aegis.data.db.entity.VisitLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitRepository @Inject constructor(
    private val visitLogDao: VisitLogDao,
) {
    val allVisits: Flow<List<VisitLogEntity>> = visitLogDao.getAllVisits()

    suspend fun addVisit(visit: VisitLogEntity): Long = visitLogDao.insert(visit)
    suspend fun updateVisit(visit: VisitLogEntity) = visitLogDao.update(visit)
    suspend fun deleteVisit(visit: VisitLogEntity) = visitLogDao.delete(visit)
    suspend fun getVisitById(id: Long): VisitLogEntity? = visitLogDao.getById(id)
}
