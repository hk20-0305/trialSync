package com.trialsync.backend.entity.type;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.function.Function;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * Binds a Java enum to a native PostgreSQL {@code ENUM} column using the enum's wire label
 * rather than its Java constant name.
 *
 * <p>The shared {@code com.trialsync.backend.domain.model} enums are the single source of truth
 * for the API contract and use upper-case Java constants with a lower-case wire value. Hibernate's
 * built-in {@code SqlTypes.NAMED_ENUM} handling binds {@code Enum#name()} and reads back through
 * {@code Enum#valueOf}, so it cannot be used for those enums without duplicating them with
 * lower-case constants. This type keeps the column a real PostgreSQL enum (no silent conversion to
 * {@code varchar}) while translating through the same {@code value()} / {@code fromValue(String)}
 * pair the API layer uses.
 *
 * <p>The value is bound with {@link Types#OTHER}, which makes the PostgreSQL JDBC driver send the
 * parameter with an unspecified type OID. The server then resolves it against the target enum type,
 * which is exactly how a literal would be resolved.
 */
public abstract class PostgresEnumUserType<E extends Enum<E>> implements UserType<E> {

    private final Class<E> enumClass;
    private final String postgresTypeName;
    private final Function<E, String> toLabel;
    private final Function<String, E> fromLabel;

    protected PostgresEnumUserType(
            Class<E> enumClass,
            String postgresTypeName,
            Function<E, String> toLabel,
            Function<String, E> fromLabel) {
        this.enumClass = enumClass;
        this.postgresTypeName = postgresTypeName;
        this.toLabel = toLabel;
        this.fromLabel = fromLabel;
    }

    /** The PostgreSQL enum type this mapping targets, used only for diagnostics. */
    public String postgresTypeName() {
        return postgresTypeName;
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<E> returnedClass() {
        return enumClass;
    }

    @Override
    public boolean equals(E x, E y) {
        return x == y;
    }

    @Override
    public int hashCode(E x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public E nullSafeGet(
            ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String label = rs.getString(position);
        if (label == null) {
            return null;
        }
        E resolved = fromLabel.apply(label);
        if (resolved == null) {
            throw new IllegalStateException(
                    "Unknown "
                            + postgresTypeName
                            + " value '"
                            + label
                            + "' for "
                            + enumClass.getName());
        }
        return resolved;
    }

    @Override
    public void nullSafeSet(
            PreparedStatement st, E value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, toLabel.apply(value), Types.OTHER);
        }
    }

    @Override
    public E deepCopy(E value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(E value) {
        return value;
    }

    @Override
    public E assemble(Serializable cached, Object owner) {
        return enumClass.cast(cached);
    }

    @Override
    public E replace(E detached, E managed, Object owner) {
        return detached;
    }
}
