package ir.sakhtyar.launcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class Dashboard extends Application {
  private final ObjectMapper mapper=new ObjectMapper();
  private final Map<String,List<String>> groups=new LinkedHashMap<>();
  private final Map<String,CheckBox> checks=new LinkedHashMap<>();
  private final TextArea log=new TextArea();
  private final Label activity=new Label("Ready"), network=new Label("Not checked"), usage=new Label("Not available yet");
  private final ComboBox<String> profile=new ComboBox<>();
  private Path root, settings, backend, profilesFile;
  private volatile Process workerProcess;
  private Button startBtn, resumeBtn, newBtn;
  private final Map<String,Boolean> saved=new LinkedHashMap<>();
  private final List<String> profiles=new ArrayList<>();

  public Dashboard(){
    groups.put("Connection", List.of("Check v2rayN process","Detect HTTP proxy port","Test proxy HTTPS","Check German exit IP","Monitor connection","Retry connection","Set Claude proxy"));
    groups.put("Claude / CCR",List.of("Check Claude CLI","Check authentication","Check CCR provider","Select provider profile","Track usage","Check versions"));
    groups.put("SakhtYar",List.of("Verify project path","Build handoff","Check CLAUDE.md","Inspect Git changes","Protect sensitive files","Run hooks","Run Java tests","Check Java Node and npm"));
    groups.put("Tools & reports",List.of("Protect API secrets","Write logs","Create diagnostics","Show Windows notifications","Avoid duplicate launcher","Run preflight","Show dashboard status"));
  }
  @Override public void start(Stage stage){
    String env=System.getenv("SAKHTYAR_PROJECT_ROOT");
    root=Path.of(env==null||env.isBlank()?".":env).toAbsolutePath().normalize();
    settings=root.resolve("_claude_launcher/launcher-settings.json");
    profilesFile=root.resolve("_claude_launcher/provider-profiles.json");
    backend=root.resolve("claude-launcher-ui/scripts/Invoke-Claude.ps1");
    load();

    BorderPane shell=new BorderPane();shell.setStyle("-fx-background-color: #f7f9fc;");
    Label title=new Label("SakhtYar  /  Claude Control Center");title.setStyle("-fx-font-size:20px;-fx-font-weight:bold;");
    Label subtitle=new Label("Your project, sessions, and provider profiles in one place");subtitle.setStyle("-fx-text-fill:#637083;");
    VBox header=new VBox(4,title,subtitle);header.setPadding(new Insets(20,24,15,24));shell.setTop(header);
    TabPane tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().addAll(new Tab("Home",home()),new Tab("Usage",usageTab()),new Tab("Sessions",sessionTab()),new Tab("Settings",settingsTab()),new Tab("Activity",activityTab()));
    shell.setCenter(tabs);
    stage.setTitle("SakhtYar Claude Control Center");stage.setScene(new Scene(shell,1050,740));stage.show();
    append("Project: "+root);append("CCR profile defaults to Claude-Free. No TUN or leak-prevention rules are enabled.");
    stage.setOnCloseRequest(e->{ /* Child terminal is intentionally independent and keeps Claude interactive. */ });
  }
  private VBox home(){
    Label head=new Label("Ready to work");head.setStyle("-fx-font-size:19px;-fx-font-weight:bold;");
    network.setStyle("-fx-font-size:13px;");
    VBox conn=section("Connection",network,new Label("v2rayN + HTTP proxy + German exit IP are checked before launch."));
    profile.getItems().addAll(profiles);profile.setValue(profiles.contains("Claude-Free")?"Claude-Free":profiles.get(0));
    profile.setMaxWidth(Double.MAX_VALUE);profile.valueProperty().addListener((o,a,b)->saveProfiles());
    VBox provider=section("Provider profile",profile,new Label("Choose Claude-Free now; add paid providers later under Settings."));
    startBtn=new Button("Continue project");startBtn.setDefaultButton(true);startBtn.setOnAction(e->run("Resume"));
    newBtn=new Button("New conversation");newBtn.setOnAction(e->run("Launch"));
    resumeBtn=new Button("Choose a past conversation");resumeBtn.setOnAction(e->run("Choose"));
    for(Button b:List.of(startBtn,newBtn,resumeBtn)){b.setMinHeight(40);}
    startBtn.setStyle("-fx-background-color:#176b63;-fx-text-fill:white;-fx-font-weight:bold;");
    HBox buttons=new HBox(10,startBtn,newBtn,resumeBtn);
    Button check=new Button("Check connection");check.setOnAction(e->run("Preflight"));
    HBox footer=new HBox(12,check,activity);footer.setPadding(new Insets(9,0,0,0));
    VBox page=new VBox(17,head,conn,provider,buttons,footer);page.setPadding(new Insets(24));return page;
  }
  private VBox usageTab(){
    Label note=new Label("Usage data is shown only when supported by your CCR / Claude installation. No prices are invented.");note.setWrapText(true);
    Button refresh=new Button("Refresh usage");refresh.setOnAction(e->run("Usage"));
    usage.setWrapText(true);usage.setStyle("-fx-font-size:14px;");
    VBox page=new VBox(15,new Label("Usage & status"),note,refresh,section("Reported usage",usage),new Label("Tip: CCR may provide additional request and token information in its management UI."));page.setPadding(new Insets(24));return page;
  }
  private VBox sessionTab(){
    Label note=new Label("Claude stores conversations locally by project. Continue keeps the history; New conversation does not delete old sessions.");note.setWrapText(true);
    Button c=new Button("Continue latest");c.setOnAction(e->run("Resume"));
    Button p=new Button("Pick from history");p.setOnAction(e->run("Choose"));
    Button n=new Button("Start new");n.setOnAction(e->run("Launch"));
    VBox page=new VBox(16,new Label("Project conversations"),note,new HBox(10,c,p,n));page.setPadding(new Insets(24));return page;
  }
  private ScrollPane settingsTab(){
    VBox page=new VBox(15);page.setPadding(new Insets(24));
    Label hint=new Label("Advanced options are optional. Features are enabled by default, and stored automatically.");hint.setWrapText(true);
    Button add=new Button("Add provider profile");
    TextField name=new TextField();name.setPromptText("Provider profile name, e.g. Claude-Paid");name.setPrefWidth(280);
    add.setOnAction(e->{String p=name.getText().trim();if(p.isBlank()||p.contains("\n")||p.contains("\r")){append("Enter a valid profile name");return;} if(!profiles.contains(p)){profiles.add(p);profile.getItems().add(p);saveProfiles();}name.clear();});
    page.getChildren().addAll(hint,section("Provider profiles",new HBox(10,name,add),new Label("Profiles must already be configured and enabled in CCR before they can launch.")));
    for(var g:groups.entrySet()){
      VBox content=new VBox(8);content.setPadding(new Insets(9));
      CheckBox all=new CheckBox("Enable entire category");content.getChildren().add(all);
      List<CheckBox> members=new ArrayList<>();
      for(String key:g.getValue()){
        CheckBox cb=new CheckBox(key);cb.setSelected(saved.getOrDefault(key,true));cb.setWrapText(true);
        members.add(cb);checks.put(key,cb);content.getChildren().add(cb);
        cb.selectedProperty().addListener((o,a,b)->{all.setSelected(members.stream().allMatch(CheckBox::isSelected));save();});
      }
      all.setSelected(members.stream().allMatch(CheckBox::isSelected));all.setOnAction(e->members.forEach(cb->cb.setSelected(all.isSelected())));
      TitledPane pane=new TitledPane(g.getKey(),content);pane.setExpanded(false);page.getChildren().add(pane);
    }
    Button reset=new Button("Restore enabled defaults");reset.setOnAction(e->checks.values().forEach(c->c.setSelected(true)));
    page.getChildren().addAll(reset,new Label("TUN and guaranteed direct-connection leak prevention are intentionally excluded."));
    ScrollPane sp=new ScrollPane(page);sp.setFitToWidth(true);return sp;
  }
  private VBox activityTab(){
    log.setEditable(false);log.setWrapText(true);VBox.setVgrow(log,Priority.ALWAYS);
    Button diagnostics=new Button("Run diagnostics");diagnostics.setOnAction(e->run("Diagnostics"));
    Button preflight=new Button("Run preflight");preflight.setOnAction(e->run("Preflight"));
    Button clear=new Button("Clear view");clear.setOnAction(e->log.clear());
    VBox box=new VBox(10,new HBox(10,diagnostics,preflight,clear),log);box.setPadding(new Insets(16));return box;
  }
  private VBox section(String heading,javafx.scene.Node... nodes){
    Label h=new Label(heading);h.setStyle("-fx-font-size:15px;-fx-font-weight:bold;");
    VBox box=new VBox(10);box.getChildren().add(h);box.getChildren().addAll(nodes);box.setPadding(new Insets(16));
    box.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e4e9f0;-fx-border-radius:10;");return box;
  }
  private void load(){
    try{if(Files.isRegularFile(settings)){Map<String,Boolean> x=mapper.readValue(settings.toFile(),new TypeReference<Map<String,Boolean>>(){});saved.putAll(x);}}catch(Exception e){append("Cannot read previous settings: "+e.getMessage());}
    try{if(Files.isRegularFile(profilesFile)){List<String> x=mapper.readValue(profilesFile.toFile(),new TypeReference<List<String>>(){});for(String p:x)if(p!=null&&!p.isBlank()&&!profiles.contains(p))profiles.add(p);}}catch(Exception e){append("Cannot read profiles: "+e.getMessage());}
    if(!profiles.contains("Claude-Free"))profiles.add(0,"Claude-Free");
  }
  private synchronized void save(){try{Files.createDirectories(settings.getParent());Map<String,Boolean> data=new LinkedHashMap<>();checks.forEach((k,v)->data.put(k,v.isSelected()));writeJson(settings,data);}catch(Exception e){append("Settings save error: "+e.getMessage());}}
  private synchronized void saveProfiles(){try{Files.createDirectories(profilesFile.getParent());writeJson(profilesFile,profiles);}catch(Exception e){append("Profile save error: "+e.getMessage());}}
  private void writeJson(Path p,Object o)throws IOException{Path tmp=Files.createTempFile(p.getParent(),"config-",".tmp");mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),o);Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}
  private void run(String action){
    if(workerProcess!=null && workerProcess.isAlive()){append("Another launcher task is still running.");return;}
    if(!Files.isRegularFile(backend)){append("Backend missing: "+backend);return;}
    save();saveProfiles();String selected=profile.getValue()==null?"Claude-Free":profile.getValue();
    activity.setText("Checking "+action+"...");
    Thread t=new Thread(()->{
      try{
        List<String> cmd=new ArrayList<>(List.of("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",backend.toString(),"-Action",action,"-Root",root.toString(),"-Settings",settings.toString(),"-ProfileName",selected));
        Process p=new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true).start();workerProcess=p;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){append(line);if(line.contains("Proxy IP:"))Platform.runLater(()->network.setText("Proxy checked — see Activity for IP"));if(line.startsWith("[USAGE]")){String s=line.substring(7).trim();Platform.runLater(()->usage.setText(s));}}}
        int code=p.waitFor();Platform.runLater(()->activity.setText(action+" completed (exit="+code+")"));
      }catch(Exception e){append("ERROR: "+e.getMessage());Platform.runLater(()->activity.setText("Error"));}finally{workerProcess=null;}
    },"sakhtyar-task");t.setDaemon(true);t.start();
  }
  private void append(String text){Platform.runLater(()->log.appendText("["+LocalDateTime.now().toLocalTime().withNano(0)+"] "+text+"\n"));}
  public static void main(String[] args){launch(args);}
}
