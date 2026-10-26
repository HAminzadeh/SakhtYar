package ir.sakhtyar.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Read-only provider inventory with explicit profile-based live connection probe. Never displays credentials. */
public final class RouterProvidersPanel {
    private final LauncherCore core;
    private final ComboBox<String> profile;
    private final ComboBox<String> testProfile=new ComboBox<>();
    private final TextArea output=new TextArea();
    private final ObjectMapper mapper=new ObjectMapper();
    public RouterProvidersPanel(LauncherCore core,ComboBox<String> profile){this.core=core;this.profile=profile;}
    public Node view(boolean fa){
        VBox box=new VBox(10);box.setPadding(new Insets(18));
        testProfile.getItems().setAll(core.profiles());testProfile.setValue(profile.getValue());
        Label note=new Label(fa?"Providerها از فایل‌های تنظیمات CCR به‌صورت فقط‌خواندنی نمایش داده می‌شوند؛ تست واقعی روی پروفایل انتخابی انجام می‌شود و ممکن است توکن مصرف کند.":"Read-only provider inventory. The live test uses the selected CCR agent profile and may consume tokens.");note.setWrapText(true);
        Button refresh=new Button(fa?"به‌روزرسانی لیست":"Refresh providers");
        Button gateway=new Button(fa?"بررسی Gateway":"Check gateway");
        Button test=new Button(fa?"تست واقعی پروفایل":"Live profile test");
        Button manage=new Button(fa?"مدیریت در CCR":"CCR administration");
        output.setEditable(false);output.setWrapText(true);
        refresh.setOnAction(e->async(()->load()));gateway.setOnAction(e->async(()->{
            boolean online=core.routerListening();Platform.runLater(()->output.appendText("\nCCR gateway localhost:"+LauncherCore.port("SAKHTYAR_CCR_PORT",3456)+(online?" reachable":" OFFLINE")+"\n"));
        }));
        test.setOnAction(e->{Alert confirmation=new Alert(Alert.AlertType.CONFIRMATION,fa?"یک درخواست واقعی کم‌حجم از پروفایل انتخابی ارسال شود؟":"Send one short model request on the selected CCR profile?",ButtonType.YES,ButtonType.NO);
            if(confirmation.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
            String value=testProfile.getValue();async(()->{
                ClaudePrintService.Reply r=new ClaudePrintService(core.root()).ask(value,"Reply with exactly OK.",null,false);
                Platform.runLater(()->output.appendText("\nProfile "+value+": exit "+r.exitCode()+"; response: "+r.content()+"\n"));
            });
        });
        manage.setOnAction(e->async(()->{
            new ProcessBuilder("cmd.exe","/d","/c","ccr.cmd","ui").directory(core.root().toFile()).start();
        }));
        box.getChildren().addAll(note,new HBox(8,refresh,gateway,test,manage),new Label(fa?"پروفایل تست (در تب Home قابل انتخاب است):":"Test profile (selected on Home):"),testProfile,output);
        VBox.setVgrow(output,Priority.ALWAYS);
        async(()->load());return box;
    }
    private void async(Job j){Thread t=new Thread(()->{try{j.run();}catch(Exception ex){Platform.runLater(()->output.appendText("\nError: "+ex.getMessage()+"\n"));}},"ccr-providers");t.setDaemon(true);t.start();}
    private interface Job {void run()throws Exception;}
    private void load()throws Exception{
        List<String> lines=new ArrayList<>();
        Path dir=Path.of(System.getenv().getOrDefault("APPDATA",System.getProperty("user.home")),"claude-code-router");
        Path db=dir.resolve("config.sqlite");
        if(Files.isRegularFile(db)){
            // Read-only URI: SQLite handles live writer locks; no copying or modifying the runtime DB.
            String uri="jdbc:sqlite:file:"+db.toUri().getRawPath()+"?mode=ro";
            try(Connection c=DriverManager.getConnection(uri);Statement st=c.createStatement();ResultSet tables=st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")){
                List<String> names=new ArrayList<>();while(tables.next())names.add(tables.getString(1));
                for(String tbl:names){if(!tbl.toLowerCase(Locale.ROOT).contains("provider"))continue;
                    if(!tbl.matches("[A-Za-z0-9_]+"))continue;
                    try(Statement scan=c.createStatement();ResultSet rows=scan.executeQuery("SELECT * FROM \""+tbl+"\" LIMIT 100")){
                        var metadata=rows.getMetaData();int cols=metadata.getColumnCount();
                        while(rows.next()){
                            String name="",model="";
                            for(int i=1;i<=cols;i++){
                                String field=metadata.getColumnName(i).toLowerCase(Locale.ROOT);
                                String value=rows.getString(i);if(value==null)continue;
                                if(Set.of("name","provider_name","display_name","displayname").contains(field))name=value;
                                if(Set.of("models","model","model_id").contains(field))model=value;
                                if(field.equals("data")||field.equals("config")||field.equals("settings")){
                                    try{JsonNode j=mapper.readTree(value);if(name.isBlank())name=j.path("name").asText(j.path("displayName").asText(""));
                                        if(model.isBlank())model=j.path("models").toString();}catch(Exception ignored){}
                                }
                            }
                            if(!name.isBlank())lines.add(name+" | "+truncate(model,110)+" | CCR SQLite");
                        }
                    }catch(Exception ignored){}
                }
            }catch(Exception ex){lines.add("SQLite metadata unavailable: "+ex.getClass().getSimpleName());}
        }
        for(String file:List.of("gateway.config.json","config.json")){
            Path path=dir.resolve(file);if(!Files.isRegularFile(path))continue;
            try{
                JsonNode config=mapper.readTree(path.toFile());
                JsonNode providers=config.path("Providers");if(!providers.isArray())providers=config.path("providers");
                if(providers.isArray())for(JsonNode node:providers){String name=node.path("name").asText(node.path("id").asText(""));
                    String model=node.path("models").toString();if(!name.isBlank())lines.add(name+" | "+truncate(model,110)+" | "+file);
                }
            }catch(Exception ex){lines.add(file+": format unsupported");}
        }
        StringBuilder display=new StringBuilder("CCR Gateway: "+(core.routerListening()?"ONLINE":"OFFLINE")+"\n");
        display.append("Profiles configured in launcher: ").append(String.join(", ",core.profiles())).append("\n\n");
        if(lines.isEmpty())display.append("Provider metadata not exposed by this CCR installation. Use 'CCR administration' to view the authoritative list.\n");
        else {lines.stream().distinct().sorted().forEach(x->display.append("• ").append(x).append("\n"));}
        Platform.runLater(()->output.setText(display.toString()));
    }
    private static String truncate(String s,int max){return s.length()>max?s.substring(0,max)+"…":s;}
}
