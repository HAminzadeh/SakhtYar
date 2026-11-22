package ir.sakhtyar.context;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

public class ContextEngineTest {
    @Test void indexAndSearchAndPersistence()throws Exception{
        Path dir=Files.createTempDirectory("context-test-");
        Path file=dir.resolve("Example.java");Files.writeString(file,"public class Example { void calculateInvoice() {} }");
        try(ContextStore store=new ContextStore(dir)){
            assertTrue(store.update(file,Files.readString(file)));
            assertFalse(store.update(file,Files.readString(file)));
            assertFalse(store.search("calculateInvoice",3,200).isEmpty());
            store.note("decision:architecture","Use modular monolith");
            assertEquals("Use modular monolith",store.getNote("decision:architecture"));
            store.archive("session-001",1,"user","Investigate controller");
            assertFalse(store.searchSessions("controller",3).isEmpty());
        }
    }
    @Test void skillCatalogRejectsNonreviewedDraft()throws Exception{
        Path dir=Files.createTempDirectory("skills-test-");
        Path file=dir.resolve("engineering/ai-harness/skills/java-coding-standards/SKILL.md");
        Files.createDirectories(file.getParent());Files.writeString(file,"---\nname: java-coding-standards\nmaturity: catalog\norigin: sakhtyar-chatgpt-adaptation\n---\n# Draft");
        SkillCatalog skill=new SkillCatalog(dir);
        assertThrows(java.io.IOException.class,()->skill.install("java-coding-standards",false));
    }
    @Test void indexExcludesSecretsAndBuildOutput()throws Exception{
        Path dir=Files.createTempDirectory("index-test-");
        Files.writeString(dir.resolve("Main.java"),"public class Main { }");
        Files.writeString(dir.resolve(".env"),"API_KEY=private");
        Path t=dir.resolve("target");Files.createDirectories(t);Files.writeString(t.resolve("DoNotIndex.java"),"secret");
        try(ContextStore store=new ContextStore(dir)){
            ProjectIndexer.Result result=new ProjectIndexer(dir).index(store,100);
            assertEquals(1,result.scanned());
            assertEquals(1,store.status().sources());
        }
    }

    @Test void catalogDraftCanBeInstalledOnlyWithApprovalAndSurvivesRestart()throws Exception {
        Path dir=Files.createTempDirectory("skills-draft-");
        Path src=dir.resolve("engineering/ai-harness/skills/java-coding-standards/SKILL.md");
        Files.createDirectories(src.getParent());
        Files.writeString(src,"---\nname: java-coding-standards\ndescription: Draft\nmaturity: catalog\norigin: sakhtyar-chatgpt-adaptation\n---\nManual draft");
        SkillCatalog catalog=new SkillCatalog(dir);
        assertThrows(java.io.IOException.class,()->catalog.install("java-coding-standards",false));
        catalog.install("java-coding-standards",true);
        SkillCatalog reopened=new SkillCatalog(dir);
        assertTrue(reopened.list().stream().anyMatch(x->x.name().equals("java-coding-standards")&&x.installed()));
        Path installed=dir.resolve(".claude/skills/sakhtyar-java-coding-standards/SKILL.md");
        assertTrue(Files.readString(installed).contains("disable-model-invocation: true"));
        assertTrue(reopened.removeIfManaged("java-coding-standards"));
        assertFalse(new SkillCatalog(dir).list().stream().anyMatch(x->x.name().equals("java-coding-standards")&&x.installed()));
    }
}
