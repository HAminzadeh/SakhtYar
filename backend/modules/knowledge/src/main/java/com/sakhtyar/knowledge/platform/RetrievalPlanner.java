package com.sakhtyar.knowledge.platform;
import org.springframework.stereotype.Component;
@Component public class RetrievalPlanner {
 public enum Mode { STRUCTURED, FULL_TEXT, GRAPH, GRAPHRAG, HYBRID }
 public Mode plan(String question){ String q=question==null?"":question; if(q.contains("\u0647\u0645\u0647")||q.contains("\u0639\u0648\u0627\u0645\u0644")||q.contains("\u0645\u0631\u062a\u0628\u0637")||q.contains("\u0686\u0647 \u0636\u0648\u0627\u0628\u0637")) return Mode.GRAPHRAG; if(q.matches(".*(\u062d\u062f\u0627\u0642\u0644|\u062d\u062f\u0627\u06a9\u062b\u0631|\u0686\u0646\u062f|\u0645\u0642\u062f\u0627\u0631|\u062f\u0631\u0635\u062f).*")) return Mode.STRUCTURED; return Mode.HYBRID; }
}