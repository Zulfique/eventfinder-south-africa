package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.ReportDao
import com.eventfinder.app.data.local.ReportEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

interface ReportRepository {
    suspend fun report(targetType: String, targetId: String, reason: String, reporterId: String): Result<Unit>
    suspend fun isBlocked(userId: String, otherId: String): Boolean
    suspend fun block(userId: String, otherId: String): Result<Unit>
}

class ReportRepositoryImpl(private val reportDao: ReportDao) : ReportRepository {
    override suspend fun report(targetType: String, targetId: String, reason: String, reporterId: String): Result<Unit> =
        runCatching {
            reportDao.upsert(
                ReportEntity(
                    id = UUID.randomUUID().toString(),
                    reporterId = reporterId,
                    targetType = targetType,
                    targetId = targetId,
                    reason = reason,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

    override suspend fun isBlocked(userId: String, otherId: String): Boolean =
        reportDao.blockedIds(userId).contains(otherId)

    override suspend fun block(userId: String, otherId: String): Result<Unit> =
        runCatching {
            reportDao.block(
                com.eventfinder.app.data.local.BlockedUserEntity(
                    userId = userId,
                    blockedUserId = otherId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
}