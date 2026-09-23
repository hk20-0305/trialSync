package com.trialsync.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.User;

/**
 * Accounts.
 *
 * <p>Email is stored already lower-cased by the registration endpoint and every lookup passes a
 * lower-cased address, so the unique index {@code ix_users_email} is a plain equality index and
 * callers must lower-case before calling {@link #findByEmail(String)} - exactly as
 * {@code api/auth.py} does.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Login and demo-seed lookup: {@code select(User).where(User.email == email.lower())}. */
    Optional<User> findByEmail(String email);

    /**
     * Demo reset: {@code delete(User).where(User.email != DEMO_EMAIL)}.
     *
     * <p>Deliberately a bulk statement rather than a derived {@code deleteBy...} so it issues one
     * {@code DELETE} and lets the database-level {@code ON DELETE CASCADE} rules clean up the owned
     * rows, matching the Python implementation.
     */
    @Modifying
    @Query("delete from User u where u.email <> :email")
    int deleteAllByEmailNot(@Param("email") String email);
}
