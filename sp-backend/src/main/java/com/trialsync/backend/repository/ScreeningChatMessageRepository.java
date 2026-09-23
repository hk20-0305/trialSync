package com.trialsync.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ScreeningChatMessage;

/**
 * Turns of the bounded conversation attached to a saved screening.
 *
 * <p>The conversation is capped: after each exchange the newest N ids are collected and everything
 * else for that screening is deleted in one statement. Ordering is
 * {@code (created_at desc, id desc)} - the tie-break on id matters because a user turn and the
 * assistant turn answering it are written one microsecond apart, and the id keeps the pair stable if
 * the clock ever repeats.
 *
 * <p>The {@code limit} is a runtime setting rather than a constant, so it is passed as a
 * {@link Pageable} ({@code PageRequest.of(0, limit)}) instead of being baked into a {@code findTopN}
 * method name.
 */
@Repository
public interface ScreeningChatMessageRepository extends JpaRepository<ScreeningChatMessage, UUID> {

    /**
     * The most recent turns, newest first. Callers reverse the list to render it chronologically,
     * exactly as {@code _recent_chat()} does.
     */
    List<ScreeningChatMessage> findByScreeningIdOrderByCreatedAtDescIdDesc(
            UUID screeningId, Pageable pageable);

    /** Ids of the turns to keep when trimming the conversation to its configured maximum. */
    @Query("""
            select m.id
              from ScreeningChatMessage m
             where m.screeningId = :screeningId
             order by m.createdAt desc, m.id desc
            """)
    List<UUID> findRecentIds(@Param("screeningId") UUID screeningId, Pageable pageable);

    /**
     * Trim to the retained ids. {@code keepIds} is never empty in practice - the two turns that
     * triggered the trim were flushed first - and an empty collection would produce invalid SQL, so
     * callers must not pass one.
     */
    @Modifying
    @Query("""
            delete from ScreeningChatMessage m
             where m.screeningId = :screeningId
               and m.id not in :keepIds
            """)
    int deleteByScreeningIdAndIdNotIn(
            @Param("screeningId") UUID screeningId, @Param("keepIds") Collection<UUID> keepIds);

    /**
     * Clear the whole conversation. A bulk statement, matching
     * {@code delete(ScreeningChatMessage).where(screening_id == ...)}, rather than loading each row
     * to remove it.
     */
    @Modifying
    @Query("delete from ScreeningChatMessage m where m.screeningId = :screeningId")
    int deleteByScreeningId(@Param("screeningId") UUID screeningId);
}
