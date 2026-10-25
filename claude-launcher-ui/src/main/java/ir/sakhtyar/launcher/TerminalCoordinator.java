package ir.sakhtyar.launcher;

import javafx.application.Platform;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Interactive CCR runs in the SAME inherited IntelliJ terminal while JavaFX stays open. */
public final class TerminalCoordinator {
    private record Launch(LauncherCore.Request request,Consumer<String> status,boolean close) { }
    private static final BlockingQueue<Launch> QUEUE=new LinkedBlockingQueue<>();
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();
    private TerminalCoordinator(){}
    public static boolean enqueue(LauncherCore.Request request,Consumer<String> status){
        if(!BUSY.compareAndSet(false,true))return false;
        QUEUE.offer(new Launch(request,status,false));return true;
    }
    public static void stop(){QUEUE.offer(new Launch(null,null,true));}
    private static void send(Consumer<String> status,String message){if(status!=null)Platform.runLater(()->status.accept(message));}
    public static void eventLoop(Path root){
        for(;;){
            Launch work;
            try{work=QUEUE.take();}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            if(work.close)break;
            try{
                LauncherCore.Request req=work.request;
                if(!LauncherCore.validProfile(req.profile()))throw new IllegalArgumentException("Invalid provider profile");
                List<String> cmd=new ArrayList<>(List.of("cmd.exe","/d","/c","ccr.cmd",req.profile(),"cli"));
                if(req.action()!=LauncherCore.Action.NEW){
                    cmd.add("--");cmd.add(req.action()==LauncherCore.Action.CONTINUE?"--continue":"--resume");
                }
                ProcessBuilder pb=new ProcessBuilder(cmd).directory(root.toFile()).inheritIO();
                int proxy=LauncherCore.port("SAKHTYAR_PROXY_PORT",10808);
                pb.environment().put("HTTP_PROXY","http://127.0.0.1:"+proxy);
                pb.environment().put("HTTPS_PROXY","http://127.0.0.1:"+proxy);
                // Local CodeGraph supports narrowed MCP tool surface when this profile flag is understood.
                var saved=new LauncherCore(root).readSettings();
                String codeGraphProfile=String.valueOf(saved.getOrDefault("codeGraphProfile","core"));
                if(Set.of("core","graph","memory","all").contains(codeGraphProfile))pb.environment().put("CODEGRAPH_TOOL_PROFILE",codeGraphProfile);
                // Opt-in only: some free gateway models do not implement native tool search.
                if(Boolean.TRUE.equals(saved.get("toolSearchEnabled")))pb.environment().put("ENABLE_TOOL_SEARCH","auto:5");
                send(work.status,"Claude running in IntelliJ terminal; dashboard remains open.");
                Process child=pb.start();int code=child.waitFor();send(work.status,"Claude exited with code "+code);
            }catch(Exception e){send(work.status,"Launcher failed: "+e.getMessage());}
            finally{BUSY.set(false);}
        }
    }
}
