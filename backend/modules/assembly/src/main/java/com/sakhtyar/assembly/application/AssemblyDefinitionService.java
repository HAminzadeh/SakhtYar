package com.sakhtyar.assembly.application;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Uses invoker SQL and the same scoped connection as the rest of the assembly module. */
@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class AssemblyDefinitionService {
    private final JdbcTemplate jdbc;
    public AssemblyDefinitionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // JSON is returned as text intentionally: no lossy float conversion in the HTTP layer.
    public record Definition(String revision, String definitionJson, boolean historical, boolean available) {}

    public Definition current(UUID id) {
        return one(jdbc.query("""
            select revision::text, public.sakhtyar_assembly_definition(id)::text
            from public.cost_assembly where id=?
            """, (r,n)->new Definition(r.getString(1),r.getString(2),false,true),id));
    }

    public List<Definition> revisions(UUID id,int limit,Long beforeRevision) {
        current(id); // indistinguishable absent and out-of-scope IDs
        if (limit<1 || limit>100 || (beforeRevision!=null && beforeRevision<1))
            throw new IllegalArgumentException("Invalid revision page.");
        return jdbc.query("""
            select revision::text, definition::text from public.cost_assembly_revision
            where assembly_id=? and revision < ? order by revision desc limit ?
            """, (r,n)->new Definition(r.getString(1),r.getString(2),true,true),
                id,beforeRevision==null?Long.MAX_VALUE:beforeRevision,limit);
    }

    public Definition revision(UUID id,long revision) {
        if(revision<1) throw new IllegalArgumentException("Invalid revision.");
        Definition current=current(id);
        if(Long.toString(revision).equals(current.revision())) return current;
        return one(jdbc.query("""
            select revision::text, definition::text from public.cost_assembly_revision
            where assembly_id=? and revision=?
            """, (r,n)->new Definition(r.getString(1),r.getString(2),true,true),id,revision));
    }

    public Definition estimateDefinition(UUID id) {
        return one(jdbc.query("""
            select definition_revision::text, assembly_definition::text from public.cost_estimate where id=?
            """, (r,n)->new Definition(r.getString(1),r.getString(2),true,r.getString(1)!=null),id));
    }

    private static Definition one(List<Definition> rows) {
        if(rows.size()!=1) throw new IllegalArgumentException("Assembly definition not found.");
        return rows.getFirst();
    }
}
