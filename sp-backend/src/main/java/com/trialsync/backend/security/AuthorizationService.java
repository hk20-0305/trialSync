package com.trialsync.backend.security;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.User;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ownership and role checks.
 *
 * <p>The Python API deliberately answered {@code 404} rather than {@code 403} when a record
 * belonged to somebody else, so one tenant cannot probe another's identifiers. That choice is
 * preserved: callers pass the not-found code for the resource they were loading.
 */
@Service
public class AuthorizationService {

    /** Fails with the caller's not-found code unless the record belongs to the current user. */
    public void requireOwnership(UUID ownerId, User user, String notFoundCode, String message) {
        if (ownerId == null || !ownerId.equals(user.getId())) {
            throw ApplicationError.notFound(notFoundCode, message);
        }
    }

    /** Port of {@code require_catalog_admin}. */
    public void requireCatalogAdmin(User user) {
        if (!user.isCatalogAdmin()) {
            throw new ApplicationError(
                    "CATALOG_ADMIN_REQUIRED",
                    "Catalog administration is required for this action.",
                    403);
        }
    }
}
