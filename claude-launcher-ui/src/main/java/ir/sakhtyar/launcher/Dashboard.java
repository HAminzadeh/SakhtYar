package ir.sakhtyar.launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.nio.file.Path;
import ir.sakhtyar.context.ContextStore;
import ir.sakhtyar.context.ProjectIndexer;
import ir.sakhtyar.context.SkillCatalog;
import ir.sakhtyar.context.CodeGraphBridge;
import ir.sakhtyar.context.ClaudeSessionImporter;
import ir.sakhtyar.context.CcrUsageImporter;
import javafx.stage.FileChooser;
import java.util.*;

/** Only presentation and interaction; real checks and settings live in LauncherCore. */
public final class Dashboard extends Application {
    private LauncherCore core;
    private final Map<String,Boolean> preferences=new LinkedHashMap<>();
    private final ComboBox<String> providers=new ComboBox<>();
    private final Label connection=new Label();
    private final Label status=new Label();
    private final TextArea activity=new TextArea();
    private final TabPane tabs=new TabPane();
    private Scene scene;
    private final TextArea memoryResults=new TextArea();
    private final Label graphHealth=new Label();
    private final Label memoryHealth=new Label();
    private Stage stage;
    private boolean persian=true;
    private ComboBox<String> language;
    private static final String[] KEYS={"checkV2ray","checkProxy","checkGermany","checkRouter","rememberSessions","logDiagnostics","monitorConnection","usageStatus"};
    private static final String[] FA={"بررسی v2rayN","بررسی پراکسی","بررسی IP آلمان","بررسی روتر CCR","حفظ گفتگوها","ثبت گزارش","پایش اتصال","نمایش مصرف"};
    private static final String[] EN={"Check v2rayN","Check proxy","Check German IP","Check CCR router","Preserve conversations","Diagnostic logging","Monitor connection","Usage summary"};
    private String tr(String fa,String en){return persian?fa:en;}
    @Override public void start(Stage s){
        stage=s;
        String env=System.getenv("SAKHTYAR_PROJECT_ROOT");
        core=new LauncherCore(Path.of(env==null||env.isBlank()?"..":env));
        var settings=core.readSettings();persian=!"en".equals(settings.get("language"));
        for(String key:KEYS)preferences.put(key,!(settings.get(key) instanceof Boolean b)||b);
        providers.getItems().addAll(core.profiles()); providers.setValue("Claude-Free");
        status.setWrapText(true);connection.setWrapText(true);
        s.setOnHidden(e->TerminalCoordinator.stop());
        activity.setEditable(false);
        build();s.setTitle("SakhtYar Claude Control Center");s.show();
        connection.setText(tr("روتر CCR آماده است. برای تست اتصال کلیک کنید.","CCR router ready. Click Check Connection."));
    }
    private void build(){
        BorderPane root=new BorderPane();root.setStyle("-fx-background-color:#f7f9fc;");
        Label title=new Label(tr("مدیریت کلود ساخت‌یار","SakhtYar Claude Control Center"));title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;");
        language=new ComboBox<>(); language.getItems().addAll("فارسی","English");language.setValue(persian?"فارسی":"English");language.setOnAction(e->{boolean p="فارسی".equals(language.getValue());if(p!=persian){persian=p;save();build();}});
        HBox header=new HBox(14,title,new Region(),language);HBox.setHgrow(header.getChildren().get(1),Priority.ALWAYS);header.setPadding(new Insets(18));root.setTop(header);
        tabs.getTabs().clear();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(tab(tr("خانه","Home"),home()),tab(tr("توانایی‌ها","Skills"),skillsTab()),tab(tr("حافظه","Memory"),memoryTab()),tab(tr("مصرف","Usage"),usage()),tab(tr("گفتگوها","Sessions"),sessions()),tab(tr("تنظیمات","Settings"),settings()),tab(tr("گزارش","Activity"),logs()));
        root.setCenter(tabs);scene=new Scene(root,850,610);scene.setNodeOrientation(persian?NodeOrientation.RIGHT_TO_LEFT:NodeOrientation.LEFT_TO_RIGHT);stage.setScene(scene);
    }
    private Tab tab(String name,javafx.scene.Node node){return new Tab(name,node);}
    private VBox home(){
        Button cont=button(tr("ادامه آخرین گفتگو","Continue project"),LauncherCore.Action.CONTINUE);
        Button fresh=button(tr("گفتگوی جدید","New conversation"),LauncherCore.Action.NEW);
        Button pick=button(tr("انتخاب گفتگوی قبلی","Choose past conversation"),LauncherCore.Action.CHOOSE);
        Button check=new Button(tr("بررسی اتصال","Check connection"));check.setOnAction(e->check());
        VBox p=new VBox(17,new Label(tr("آماده شروع کار","Ready to work")),card(tr("اتصال","Connection"),connection,check),card(tr("پروفایل فعال","Provider profile"),providers),new HBox(10,cont,fresh,pick),status);
        p.setPadding(new Insets(22));return p;
    }
    private VBox skillsTab(){
        VBox body=new VBox(12);body.setPadding(new Insets(20));
        Label info=new Label(tr("فایل‌های adapted را پس از تأیید نصب کن؛ draftهای catalog هشدار دارند.","Install reviewed adapted skills only; catalog drafts require further work."));info.setWrapText(true);
        body.getChildren().add(info);
        var manager=new SkillCatalog(core.root());
        try{
            Map<String,VBox> containers=new LinkedHashMap<>();
            Map<String,List<CheckBox>> selections=new LinkedHashMap<>();
            for(var item:manager.list()){
                VBox group=containers.computeIfAbsent(item.group(),k->new VBox(7));
                CheckBox cb=new CheckBox(item.name()+"  ["+item.maturity()+"] "+(item.installed()?"✓":""));
                cb.setUserData(item);cb.setSelected("adapted".equals(item.maturity()));
                group.getChildren().add(cb);selections.computeIfAbsent(item.group(),k->new ArrayList<>()).add(cb);
            }
            for(var e:containers.entrySet()){
                Button install=new Button(tr("نصب موارد انتخاب‌شده","Install selected"));
                install.setOnAction(ev->{
                    Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,tr("Skillهای انتخاب‌شده از سورس داخلی پروژه کپی شوند؟","Install selected local reviewed skills?"),ButtonType.YES,ButtonType.NO);
                    if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
                    StringBuilder logText=new StringBuilder();
                    for(CheckBox cb:selections.get(e.getKey()))if(cb.isSelected()){
                        var skill=(SkillCatalog.Skill)cb.getUserData();
                        try{logText.append(manager.install(skill.name(),false)).append("\n");}
                        catch(Exception x){logText.append(skill.name()).append(": ").append(x.getMessage()).append("\n");}
                    }
                    activity.appendText(logText.toString());
                });
                e.getValue().getChildren().add(install);
                TitledPane pane=new TitledPane(e.getKey(),e.getValue());pane.setExpanded(false);body.getChildren().add(pane);
            }
        }catch(Exception e){body.getChildren().add(new Label(e.getMessage()));}
        ScrollPane panel=new ScrollPane(body);panel.setFitToWidth(true);VBox shell=new VBox(panel);VBox.setVgrow(panel,Priority.ALWAYS);return shell;
    }
    private VBox memoryTab(){
        TextField query=new TextField();query.setPromptText(tr("کلمه، کلاس یا موضوع موردنظر","Search phrase, class, or topic"));
        Button search=new Button(tr("جست‌وجو","Search"));
        Button index=new Button(tr("ساخت/به‌روزرسانی ایندکس","Index changed files"));
        Button graph=new Button(tr("بررسی Knowledge Graph","Check CodeGraph"));
        Button stats=new Button(tr("وضعیت حافظه","Memory status"));
        Button importSession=new Button(tr("ورود اختیاری تاریخچه Claude","Import session JSONL"));
        memoryResults.setEditable(false);memoryResults.setWrapText(true);
        search.setOnAction(e->background(()->{
            try(ContextStore store=new ContextStore(core.root())){
                StringBuilder out=new StringBuilder();for(var hit:store.search(query.getText(),7,900))out.append(hit.source()).append("\n").append(hit.excerpt()).append("\n----\n");
                for(var hit:store.searchSessions(query.getText(),4))out.append(hit.source()).append("\n").append(hit.excerpt()).append("\n----\n");
                Platform.runLater(()->memoryResults.setText(out.length()==0?"No matching local records":out.toString()));
            }
        }));
        index.setOnAction(e->background(()->{
            try(ContextStore store=new ContextStore(core.root())){
                var report=new ProjectIndexer(core.root()).index(store,15000);
                Platform.runLater(()->memoryHealth.setText(report.toString()));
            }
        }));
        importSession.setOnAction(e->{
            FileChooser chooser=new FileChooser();chooser.setTitle(tr("انتخاب فایل تاریخچه محلی Claude","Select Claude local session JSONL"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSONL", "*.jsonl"));
            var chosen=chooser.showOpenDialog(stage);if(chosen==null)return;
            Alert approval=new Alert(Alert.AlertType.CONFIRMATION,tr("محتوای متنی گفتگو در SQLite محلی ذخیره می‌شود؛ ادامه دهیم؟","Store session text in private local SQLite memory?"),ButtonType.YES,ButtonType.NO);
            if(approval.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return;
            background(()->{try(ContextStore store=new ContextStore(core.root())){
                var report=new ClaudeSessionImporter().importFile(chosen.toPath(),store);
                Platform.runLater(()->memoryHealth.setText(report.toString()));
            }});
        });
        graph.setOnAction(e->background(()->{
            var health=new CodeGraphBridge(core.root()).inspect();Platform.runLater(()->graphHealth.setText(health.toString()));
        }));
        stats.setOnAction(e->background(()->{
            try(ContextStore store=new ContextStore(core.root())){var st=store.status();Platform.runLater(()->memoryHealth.setText(st+" — "+store.location()));}
        }));
        VBox p=new VBox(10,new Label(tr("حافظه محلی و اتصال به CodeGraph","Local memory and CodeGraph")),new HBox(8,index,graph,stats,importSession),memoryHealth,graphHealth,new HBox(8,query,search),memoryResults);
        p.setPadding(new Insets(20));HBox.setHgrow(query,Priority.ALWAYS);VBox.setVgrow(memoryResults,Priority.ALWAYS);return p;
    }
    private void background(ThrowingJob job){Thread t=new Thread(()->{
        try{job.run();}catch(Exception e){Platform.runLater(()->activity.appendText("ERROR: "+e.getMessage()+"\n"));}
    },"context-worker");t.setDaemon(true);t.start();}
    private interface ThrowingJob{void run()throws Exception;}
    private VBox usage(){
        Label result=new Label(tr("برای نمایش آمار واقعی، خروجی JSON/JSONL لاگ CCR را انتخاب کن.","Select a CCR JSON/JSONL log export for measured usage."));result.setWrapText(true);
        Button importLogs=new Button(tr("خواندن گزارش CCR","Import CCR usage export"));
        importLogs.setOnAction(e->{
            FileChooser f=new FileChooser();f.setTitle("CCR JSON / JSONL export");
            var chosen=f.showOpenDialog(stage);if(chosen==null)return;
            background(()->{var report=new CcrUsageImporter().inspect(chosen.toPath());Platform.runLater(()->result.setText(report.toString()));});
        });
        VBox b=new VBox(18,new Label(tr("مصرف و وضعیت","Usage and status")),new Label(tr("هزینه ساختگی نمایش داده نمی‌شود و این اعداد از گزارش انتخاب‌شده خوانده می‌شوند.","Only counts present in the selected export are reported, never invented cost.")),importLogs,result,new Label(core.usageNote()));
        b.setPadding(new Insets(22));return b;
    }
    private VBox sessions(){VBox b=new VBox(16,new Label(tr("تاریخچه گفتگوهای پروژه حفظ می‌شود.","Local project conversation history is preserved.")),button(tr("ادامه آخرین گفتگو","Continue latest"),LauncherCore.Action.CONTINUE),button(tr("انتخاب گفتگو","Choose conversation"),LauncherCore.Action.CHOOSE),button(tr("گفتگوی جدید","New conversation"),LauncherCore.Action.NEW));b.setPadding(new Insets(22));return b;}
    private ScrollPane settings(){VBox body=new VBox(12);body.setPadding(new Insets(22));body.getChildren().add(new Label(tr("امکانات به‌صورت پیش‌فرض فعال هستند.","Features are enabled by default.")));
        String[] cats={tr("شبکه","Network"),tr("جلسات و روتر","Sessions and router"),tr("گزارش‌ها","Reporting")};
        for(int cat=0;cat<3;cat++){VBox group=new VBox(10);int begin=cat==0?0:cat==1?3:6;int end=cat==0?3:cat==1?6:KEYS.length;
            for(int i=begin;i<end;i++){final String key=KEYS[i];CheckBox c=new CheckBox(persian?FA[i]:EN[i]);c.setSelected(preferences.getOrDefault(key,true));c.selectedProperty().addListener((o,a,b)->{preferences.put(key,b);save();});group.getChildren().add(c);}
            TitledPane pane=new TitledPane(cats[cat],group);pane.setExpanded(false);body.getChildren().add(pane);
        }
        ComboBox<String> codeGraphProfile=new ComboBox<>();
        codeGraphProfile.getItems().addAll("core","graph","memory","all");
        Object configuredProfile=core.readSettings().get("codeGraphProfile");
        codeGraphProfile.setValue(Set.of("core","graph","memory","all").contains(String.valueOf(configuredProfile))?String.valueOf(configuredProfile):"core");
        codeGraphProfile.setOnAction(e->{try{
            Map<String,Object> conf=new LinkedHashMap<>(core.readSettings());conf.put("codeGraphProfile",codeGraphProfile.getValue());core.writeSettings(conf);
        }catch(Exception ex){activity.appendText(ex.getMessage()+"\n");}});
        CheckBox toolSearch=new CheckBox(tr("Tool Search آزمایشی؛ فقط با مدل سازگار","Experimental tool search (compatible models only)"));
        toolSearch.setSelected(Boolean.TRUE.equals(core.readSettings().get("toolSearchEnabled")));
        toolSearch.selectedProperty().addListener((o,a,b)->{try{
            Map<String,Object> conf=new LinkedHashMap<>(core.readSettings());conf.put("toolSearchEnabled",b);core.writeSettings(conf);
        }catch(Exception ex){activity.appendText(ex.getMessage()+"\n");}});
        body.getChildren().add(new TitledPane(tr("بهینه‌سازی ابزارها","Tool optimization"),new VBox(9,new Label(tr("پروفایل CodeGraph","CodeGraph profile")),codeGraphProfile,toolSearch,new Label(tr("مقدار پیش‌فرض core است؛ Tool Search تنها پس از تست فعال شود.","Core is default; enable Tool Search only after compatibility testing.")))));
        Button reset=new Button(tr("بازگشت به پیش‌فرض","Restore defaults"));reset.setOnAction(e->{for(String key:KEYS)preferences.put(key,true);save();build();});body.getChildren().add(reset);ScrollPane s=new ScrollPane(body);s.setFitToWidth(true);return s;
    }
    private VBox logs(){VBox b=new VBox(12,activity);VBox.setVgrow(activity,Priority.ALWAYS);b.setPadding(new Insets(20));return b;}
    private VBox card(String title,javafx.scene.Node... nodes){VBox c=new VBox(10);Label l=new Label(title);l.setStyle("-fx-font-weight:bold;");c.getChildren().add(l);c.getChildren().addAll(nodes);c.setPadding(new Insets(15));c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#dce3ef;-fx-border-radius:10;");return c;}
    private Button button(String title,LauncherCore.Action action){Button b=new Button(title);b.setMinHeight(42);b.setOnAction(e->choose(action));return b;}
    private boolean prepareInstalledSkills(){
        try{
            SkillCatalog manager=new SkillCatalog(core.root());
            List<SkillCatalog.Skill> missing=manager.list().stream()
                .filter(s->s.present()&&!s.installed()&&"adapted".equals(s.maturity())).toList();
            if(missing.isEmpty())return true;
            Alert prompt=new Alert(Alert.AlertType.CONFIRMATION,
                tr("پیش از اجرا "+missing.size()+" Skill آماده در پروژه پیدا شد. نصب شود؟","Found "+missing.size()+" reviewed project skills. Install before launch?"),
                ButtonType.YES,ButtonType.NO,ButtonType.CANCEL);
            ButtonType choice=prompt.showAndWait().orElse(ButtonType.CANCEL);
            if(choice==ButtonType.CANCEL)return false;
            if(choice==ButtonType.YES){
                for(var skill:missing)try{activity.appendText(manager.install(skill.name(),false)+"\n");}
                catch(Exception x){activity.appendText("Skill "+skill.name()+": "+x.getMessage()+"\n");}
            }
            return true;
        }catch(Exception ex){activity.appendText("Skill inventory: "+ex.getMessage()+"\n");return true;}
    }
    private void choose(LauncherCore.Action action){status.setText(tr("بررسی پیش‌نیازها...","Checking prerequisites..."));String profile=providers.getValue();new Thread(()->{
        try{core.preflight();Platform.runLater(()->{
            if(!prepareInstalledSkills()) {status.setText(tr("اجرای Claude لغو شد.","Launch cancelled."));return;}
            boolean queued=TerminalCoordinator.enqueue(new LauncherCore.Request(action,profile),status::setText);
            status.setText(queued?tr("کلود در همان ترمینال IntelliJ در حال اجراست؛ فرم باز می‌ماند.","Claude is launching in this IntelliJ terminal; dashboard stays open."):tr("نشست دیگری در حال اجراست.","Another session is active."));
        });}
        catch(Exception e){Platform.runLater(()->{status.setText(tr("اجرا متوقف شد: ","Launch blocked: ")+e.getMessage());activity.appendText(e+"\n");});}
    },"preflight").start();}
    private void check(){
        connection.setText(tr("در حال بررسی اتصال واقعی از پراکسی...","Testing HTTPS connectivity through the HTTP proxy..."));
        Thread t=new Thread(()->{
            try{var results=core.preflight();Platform.runLater(()->{
                connection.setText(tr("اتصال آلمان تأیید شد.\n","German proxy verified.\n")+String.join("\n",results));
                status.setText(tr("اتصال آماده است.","Connection ready."));
            });}catch(Exception ex){Platform.runLater(()->{
                connection.setText(tr("بررسی اتصال ناموفق بود: ","Connection check failed: ")+ex.getMessage());
                status.setText(tr("اتصال نیاز به بررسی دارد.","Connection needs attention."));
            });}
        },"network-check");t.setDaemon(true);t.start();
    }
    private void save(){try{Map<String,Object> map=new LinkedHashMap<>(core.readSettings());map.putAll(preferences);map.put("language",persian?"fa":"en");core.writeSettings(map);}catch(Exception e){activity.appendText(e+"\n");}}
    public static void main(String[] args){
        String path=System.getenv("SAKHTYAR_PROJECT_ROOT");
        try {new LauncherCore(Path.of(path==null||path.isBlank()?"..":path)).ensureRouter();}
        catch(Exception ex){System.err.println("[ERROR] CCR router: "+ex.getMessage());System.exit(2);}
        Thread ui = new Thread(() -> launch(args),"sakhtyar-javafx");
        ui.start();
        TerminalCoordinator.eventLoop(Path.of(path==null||path.isBlank()?"..":path).toAbsolutePath().normalize());
    }
}
