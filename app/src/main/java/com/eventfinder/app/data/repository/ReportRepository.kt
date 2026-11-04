package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.ReportDao
import com.eventfinder.app.data.local.ReportEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/** A safety report as shown on the moderation dashboard. */
data class EventReport(
    val id: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val createdAt: Long,
    val resolved: Boolean
)

interface ReportRepository {
    suspend fun report(targetType: String, targetId: String, reason: String, reporterId: String): Result<Unit>

    /** Every report, newest first — powers the moderation dashboard. */
    fun observeReports(): Flow<List<EventReport>>

    suspend fun setReportResolved(id: String, resolved: Boolean): Result<Unit>
    suspend fun isBlocked(userId: String, otherId: String): Boolean
    suspend fun block(userId: String, otherId: String): Result<Unit>
    /** Every user id [userId] has blocked; used to hide their reviews and events. */
    suspend fun blockedIds(userId: String): Set<String>
    suspend fun unblock(userId: String, otherId: String): Result<Unit>
}

class ReportRepositoryImpl(private val reportDao: ReportDao) : ReportRepository {

    override fun observeReports(): Flow<List<EventReport>> =
        reportDao.observeReports().map { reports ->
            reports.map { entity ->
                EventReport(
                    id = entity.id,
                    targetType = entity.targetType,
                    targetId = entity.targetId,
                    reason = entity.reason,
                    createdAt = entity.createdAt,
                    resolved = entity.resolved
                )
            }
        }

    override suspend fun setReportResolved(id: String, resolved: Boolean): Result<Unit> =
        runCatching { reportDao.setResolved(id, resolved) }

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

    override suspend fun blockedIds(userId: String): Set<String> =
        reportDao.blockedIds(userId).toSet()

    override suspend fun unblock(userId: String, otherId: String): Result<Unit> =
        runCatching { reportDao.unblock(userId, otherId) }

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