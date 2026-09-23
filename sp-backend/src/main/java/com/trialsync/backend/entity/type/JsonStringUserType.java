package com.trialsync.backend.entity.type;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * Stores a raw JSON document in a PostgreSQL {@code json} column without reformatting it.
 *
 * <p>The Python schema uses SQLAlchemy's {@code sa.JSON()}, which compiles to {@code json} (not
 * {@code jsonb}) on PostgreSQL. Several of these payloads are echoed straight back to API clients,
 * so the exact serialized text matters: {@code jsonb} would reorder object keys, collapse
 * whitespace and drop duplicate keys, all of which would be observable in responses. Keeping the
 * value as a {@code String} preserves the bytes produced by Jackson exactly the way PostgreSQL
 * preserved the bytes produced by Python's {@code json.dumps}.
 *
 * <p>Hibernate's built-in {@code SqlTypes.JSON} binder wraps the value in a {@code PGobject} typed
 * as {@code jsonb} on PostgreSQL, which does not assign to a {@code json} column. Binding with
 * {@link Types#OTHER} instead sends the text with an unspecified type OID so the server coerces it
 * to the column's declared {@code json} type.
 */
public class JsonStringUserType implements UserType<String> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String x, String y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(String x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public String nullSafeGet(
            ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        return rs.getString(position);
    }

    @Override
    public void nullSafeSet(
            PreparedStatement st, String value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value, Types.OTHER);
        }
    }

    @Override
    public String deepCopy(String value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }

    @Override
    public String replace(String detached, String managed, Object owner) {
        return detached;
    }
}
