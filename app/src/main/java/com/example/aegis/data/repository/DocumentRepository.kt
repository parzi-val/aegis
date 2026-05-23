package com.example.aegis.data.repository

import com.example.aegis.data.db.dao.DocumentDao
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.model.DocumentType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val allDocumentsByDate: Flow<List<DocumentEntity>> = documentDao.getAllByDate()

    suspend fun addDocument(document: DocumentEntity): Long = documentDao.insert(document)
    suspend fun updateDocument(document: DocumentEntity) = documentDao.update(document)
    suspend fun deleteDocument(document: DocumentEntity) = documentDao.delete(document)
    suspend fun getDocumentById(id: Long): DocumentEntity? = documentDao.getById(id)
    fun getDocumentByIdAsFlow(id: Long): Flow<DocumentEntity?> = documentDao.getByIdAsFlow(id)

    // Multi-dimensional vault views
    fun getByType(type: DocumentType): Flow<List<DocumentEntity>> =
        documentDao.getByType(type.name)

    fun getByCondition(conditionId: Long): Flow<List<DocumentEntity>> =
        documentDao.getByCondition(conditionId)

    fun getByProvider(providerName: String): Flow<List<DocumentEntity>> =
        documentDao.getByProvider(providerName)
}
