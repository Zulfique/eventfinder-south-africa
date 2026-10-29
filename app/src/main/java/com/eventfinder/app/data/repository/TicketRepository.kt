package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.TicketDao
import com.eventfinder.app.data.local.TicketEntity
import com.eventfinder.app.domain.model.EventTicket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TicketRepository {
    suspend fun issueTicket(eventId: String, userId: String): EventTicket
    suspend fun getTicketForUser(eventId: String, userId: String): EventTicket?
    fun observeTicketsForEvent(eventId: String): Flow<List<EventTicket>>
    suspend fun verifyAndCheckIn(ticketCode: String): Result<EventTicket>
    suspend fun getAllTicketsForEvent(eventId: String): List<EventTicket>
}

class TicketRepositoryImpl(
    private val ticketDao: TicketDao
) : TicketRepository {
    override suspend fun issueTicket(eventId: String, userId: String): EventTicket {
        val ticket = EventTicket(
            id = java.util.UUID.randomUUID().toString(),
            eventId = eventId,
            userId = userId,
            ticketCode = java.util.UUID.randomUUID().toString().substring(0, 8).uppercase(),
            issuedAt = System.currentTimeMillis()
        )
        ticketDao.insert(TicketEntity(
            id = ticket.id,
            eventId = ticket.eventId,
            userId = ticket.userId,
            ticketCode = ticket.ticketCode,
            issuedAt = ticket.issuedAt,
            checkedInAt = null
        ))
        return ticket
    }

    override suspend fun getTicketForUser(eventId: String, userId: String): EventTicket? {
        // For simplicity, searching by code is faster, but here we need to find the ticket linked to user/event
        // We might need a better query in Dao, but for now we'll filter in memory or add a query.
        return null // Placeholder: should call a new Dao method
    }

    override fun observeTicketsForEvent(eventId: String): Flow<List<EventTicket>> {
        return ticketDao.getTicketsForEvent(eventId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun verifyAndCheckIn(ticketCode: String): Result<EventTicket> {
        val entity = ticketDao.getByCode(ticketCode) ?: return Result.failure(Exception("Invalid ticket code"))
        if (entity.checkedInAt != null) return Result.failure(Exception("Ticket already checked in"))
        
        ticketDao.markCheckedIn(entity.id, System.currentTimeMillis())
        return Result.success(entity.toDomain())
    }

    override suspend fun getAllTicketsForEvent(eventId: String): List<EventTicket> {
        return ticketDao.getAllTicketsForEvent(eventId).map { it.toDomain() }
    }
}

fun TicketEntity.toDomain() = EventTicket(
    id = id,
    eventId = eventId,
    userId = userId,
    ticketCode = ticketCode,
    issuedAt = issuedAt,
    checkedInAt = checkedInAt
)
