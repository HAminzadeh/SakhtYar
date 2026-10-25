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
        activity.setEditable(false);
        build();s.setTitle("SakhtYar Claude Control Center");s.show();
        connection.setText(tr("اتصال هنوز بررسی نشده است؛ روی «بررسی اتصال» بزنید.","Connection has not been tested yet. Click Check connection."));
    }
    private void build(){
        BorderPane root=new BorderPane();root.setStyle("-fx-background-color:#f7f9fc;");
        Label title=new Label(tr("مدیریت کلود ساخت‌یار","SakhtYar Claude Control Center"));title.setStyle("-fx-font-size:21px;-fx-font-weight:bold;");
        language=new ComboBox<>(); language.getItems().addAll("فارسی","English");language.setValue(persian?"فارسی":"English");language.setOnAction(e->{boolean p="فارسی".equals(language.getValue());if(p!=persian){persian=p;save();build();}});
        HBox header=new HBox(14,title,new Region(),language);HBox.setHgrow(header.getChildren().get(1),Priority.ALWAYS);header.setPadding(new Insets(18));root.setTop(header);
        tabs.getTabs().clear();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(tab(tr("خانه","Home"),home()),tab(tr("مصرف","Usage"),usage()),tab(tr("گفتگوها","Sessions"),sessions()),tab(tr("تنظیمات","Settings"),settings()),tab(tr("گزارش","Activity"),logs()));
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
    private VBox usage(){VBox b=new VBox(18,new Label(tr("مصرف و وضعیت","Usage and status")),new Label(tr("در صورت نبود داده معتبر، مصرف یا هزینه تخمینی نمایش داده نمی‌شود.","No invented token or cost totals.")),new Label(core.usageNote()));b.setPadding(new Insets(22));return b;}
    private VBox sessions(){VBox b=new VBox(16,new Label(tr("تاریخچه گفتگوهای پروژه حفظ می‌شود.","Local project conversation history is preserved.")),button(tr("ادامه آخرین گفتگو","Continue latest"),LauncherCore.Action.CONTINUE),button(tr("انتخاب گفتگو","Choose conversation"),LauncherCore.Action.CHOOSE),button(tr("گفتگوی جدید","New conversation"),LauncherCore.Action.NEW));b.setPadding(new Insets(22));return b;}
    private ScrollPane settings(){VBox body=new VBox(12);body.setPadding(new Insets(22));body.getChildren().add(new Label(tr("امکانات به‌صورت پیش‌فرض فعال هستند.","Features are enabled by default.")));
        String[] cats={tr("شبکه","Network"),tr("جلسات و روتر","Sessions and router"),tr("گزارش‌ها","Reporting")};
        for(int cat=0;cat<3;cat++){VBox group=new VBox(10);int begin=cat==0?0:cat==1?3:6;int end=cat==0?3:cat==1?6:KEYS.length;
            for(int i=begin;i<end;i++){final String key=KEYS[i];CheckBox c=new CheckBox(persian?FA[i]:EN[i]);c.setSelected(preferences.getOrDefault(key,true));c.selectedProperty().addListener((o,a,b)->{preferences.put(key,b);save();});group.getChildren().add(c);}
            TitledPane pane=new TitledPane(cats[cat],group);pane.setExpanded(false);body.getChildren().add(pane);
        }
        Button reset=new Button(tr("بازگشت به پیش‌فرض","Restore defaults"));reset.setOnAction(e->{for(String key:KEYS)preferences.put(key,true);save();build();});body.getChildren().add(reset);ScrollPane s=new ScrollPane(body);s.setFitToWidth(true);return s;
    }
    private VBox logs(){VBox b=new VBox(12,activity);VBox.setVgrow(activity,Priority.ALWAYS);b.setPadding(new Insets(20));return b;}
    private VBox card(String title,javafx.scene.Node... nodes){VBox c=new VBox(10);Label l=new Label(title);l.setStyle("-fx-font-weight:bold;");c.getChildren().add(l);c.getChildren().addAll(nodes);c.setPadding(new Insets(15));c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#dce3ef;-fx-border-radius:10;");return c;}
    private Button button(String title,LauncherCore.Action action){Button b=new Button(title);b.setMinHeight(42);b.setOnAction(e->choose(action));return b;}
    private void choose(LauncherCore.Action action){status.setText(tr("بررسی پیش‌نیازها...","Checking prerequisites..."));String profile=providers.getValue();new Thread(()->{
        try{core.preflight();core.choose(new LauncherCore.Request(action,profile));Platform.runLater(()->stage.close());}
        catch(Exception e){Platform.runLater(()->{status.setText(tr("اجرا متوقف شد: ","Launch blocked: ")+e.getMessage());activity.appendText(e+"\n");});}
    },"preflight").start();}
    private void check(){
        connection.setText(tr("در حال بررسی اتصال واقعی از پراکسی...","Testing HTTPS connectivity through the HTTP proxy..."));
        Thread t = new Thread(() -> {
            try {
                var results = core.preflight();
                Platform.runLater(() -> {
                    connection.setText(tr("اتصال آلمان تأیید شد.\n", "German proxy verified.\n") + String.join("\n", results));
                    status.setText(tr("اتصال آماده است.", "Connection ready."));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    connection.setText(tr("بررسی اتصال ناموفق بود: ", "Connection check failed: ") + ex.getMessage());
                    status.setText(tr("اتصال نیاز به بررسی دارد.", "Connection needs attention."));
                });
            }
        }, "network-check");
        t.setDaemon(true);
        t.start();
    }
    private void save(){try{Map<String,Object> map=new LinkedHashMap<>(preferences);map.put("language",persian?"fa":"en");core.writeSettings(map);}catch(Exception e){activity.appendText(e+"\n");}}
    public static void main(String[] args){
        String path=System.getenv("SAKHTYAR_PROJECT_ROOT");
        try {new LauncherCore(Path.of(path==null||path.isBlank()?"..":path)).ensureRouter();}
        catch(Exception ex){System.err.println("[ERROR] CCR router: "+ex.getMessage());System.exit(2);}
        launch(args);
    }
}
