package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.GroupPlanDao
import com.eventfinder.app.data.local.GroupPlanEntity
import com.eventfinder.app.domain.model.GroupPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface GroupPlanningRepository {
    fun observePlansForEvent(eventId: String): Flow<List<GroupPlan>>
    suspend fun createPlan(plan: GroupPlan): Result<Unit>
    suspend fun joinPlan(planId: String, userId: String): Result<Unit>
    suspend fun updateMeetingPoint(planId: String, label: String?, lat: Double?, lng: Double?): Result<Unit>
    suspend fun deletePlan(planId: String): Result<Unit>
}

class GroupPlanningRepositoryImpl(
    private val groupPlanDao: GroupPlanDao
) : GroupPlanningRepository {

    override fun observePlansForEvent(eventId: String): Flow<List<GroupPlan>> =
        groupPlanDao.observePlansForEvent(eventId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createPlan(plan: GroupPlan): Result<Unit> = runCatching {
        groupPlanDao.upsert(plan.toEntity())
    }

    override suspend fun joinPlan(planId: String, userId: String): Result<Unit> = runCatching {
        val plan = groupPlanDao.findById(planId) ?: throw Exception("Plan not found")
        val members = plan.members.split(",").toMutableSet()
        members.add(userId)
        groupPlanDao.upsert(plan.copy(members = members.joinToString(",")))
    }

    override suspend fun updateMeetingPoint(planId: String, label: String?, lat: Double?, lng: Double?): Result<Unit> = runCatching {
        val plan = groupPlanDao.findById(planId) ?: throw Exception("Plan not found")
        groupPlanDao.upsert(plan.copy(meetingPointLabel = label, meetingLat = lat, meetingLng = lng))
    }

    override suspend fun deletePlan(planId: String): Result<Unit> = runCatching {
        groupPlanDao.deleteById(planId)
    }

    private fun GroupPlan.toEntity() = GroupPlanEntity(
        id = id,
        eventId = eventId,
        creatorId = creatorId,
        members = members.joinToString(","),
        meetingPointLabel = meetingPointLabel,
        meetingLat = meetingLat,
        meetingLng = meetingLng,
        proposedTime = proposedTime,
        createdAt = createdAt
    )

    private fun GroupPlanEntity.toDomain() = GroupPlan(
        id = id,
        eventId = eventId,
        creatorId = creatorId,
        members = members.split(",").filter { it.isNotBlank() }.toSet(),
        meetingPointLabel = meetingPointLabel,
        meetingLat = meetingLat,
        meetingLng = meetingLng,
        proposedTime = proposedTime,
        createdAt = createdAt
    )
}
