package ir.sakhtyar.launcher;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** ChatGPT-like JavaFX chat surface; no terminal BiDi manipulation and no external frontend. */
public final class ChatWorkspace {
    private final LauncherCore core;
    private final ComboBox<String> profile;
    private final VBox messages=new VBox(12);
    private final TextArea prompt=new TextArea();
    private final Label attachments=new Label();
    private final Label status=new Label();
    private final CheckBox projectMode=new CheckBox();
    private final List<Path> selected=new ArrayList<>();
    private final Button send=new Button("Send / ارسال");
    private final Button cancel=new Button("Stop / توقف");
    private String sessionId;
    private final ClaudePrintService service;
    private boolean busy;
    public ChatWorkspace(LauncherCore core,ComboBox<String> profile) {this.core=core;this.profile=profile;this.service=new ClaudePrintService(core.root());}
    public Node view(boolean persian) {
        VBox shell=new VBox(12);shell.setPadding(new Insets(16));shell.setNodeOrientation(persian?NodeOrientation.RIGHT_TO_LEFT:NodeOrientation.LEFT_TO_RIGHT);
        projectMode.setText(persian?"دسترسی خواندنی به سورس و CodeGraph (Context بیشتر)":"Read-only project tools & CodeGraph (more context)");
        projectMode.setSelected(false);
        Label hint=new Label(persian?"گفتگوی فارسی با Claude-Free؛ پیوست‌ها به درخواست معرفی می‌شوند. در مدل متنی، تحلیل تصویر تضمین‌شده نیست.":"Chat with Claude-Free. Images require a vision-capable provider.");
        hint.setWrapText(true);
        Button add=new Button(persian?"افزودن فایل / عکس":"Attach files / images");
        add.setOnAction(e->{FileChooser c=new FileChooser();c.setTitle("Attach local files");var files=c.showOpenMultipleDialog(shell.getScene()==null?null:shell.getScene().getWindow());if(files!=null){for(var f:files){if(selected.size()>=8)break;selected.add(f.toPath());}refreshAttachments();}});
        Button clear=new Button(persian?"گفتگوی جدید":"New conversation");clear.setOnAction(e->{if(busy)return;sessionId=null;messages.getChildren().clear();status.setText("New session / نشست جدید");});
        HBox toolbar=new HBox(9,add,clear,projectMode,status);
        ScrollPane scroll=new ScrollPane(messages);scroll.setFitToWidth(true);messages.setPadding(new Insets(12));messages.setFillWidth(true);
        messages.heightProperty().addListener((obs,oldValue,newValue)->scroll.setVvalue(1));
        prompt.setPromptText(persian?"پیامت را به فارسی یا انگلیسی بنویس...":"Type your message here...");prompt.setWrapText(true);prompt.setPrefRowCount(4);prompt.setNodeOrientation(persian?NodeOrientation.RIGHT_TO_LEFT:NodeOrientation.LEFT_TO_RIGHT);
        attachments.setWrapText(true);cancel.setDisable(true);
        send.setOnAction(e->submit());cancel.setOnAction(e->{service.cancel();status.setText("Stopping...");});
        HBox actions=new HBox(10,send,cancel);
        shell.getChildren().addAll(hint,toolbar,scroll,attachments,prompt,actions);
        VBox.setVgrow(scroll,Priority.ALWAYS);
        return shell;
    }
    private void refreshAttachments(){attachments.setText(selected.isEmpty()?"":selected.stream().map(p->p.getFileName().toString()).reduce((a,b)->a+" · "+b).orElse(""));}
    private void bubble(String author,String text,boolean me) {
        Label title=new Label(author);title.setStyle("-fx-font-weight:bold;-fx-text-fill:"+(me?"#22518c":"#24573d")+";");
        TextArea body=new TextArea(text);body.setEditable(false);body.setWrapText(true);body.setPrefRowCount(Math.max(2,Math.min(14,text.length()/90+2)));body.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        body.setStyle("-fx-control-inner-background:"+(me?"#eaf2ff":"#f5f8f5")+";");
        VBox box=new VBox(5,title,body);box.setPadding(new Insets(8));messages.getChildren().add(box);
    }
    private void submit() {
        if(busy)return;
        String text=prompt.getText().trim();if(text.isBlank()&&selected.isEmpty())return;
        final List<Path> files=List.copyOf(selected); selected.clear();refreshAttachments();prompt.clear();
        StringBuilder query=new StringBuilder(text);
        if(!files.isEmpty()) {
            query.append("\n\nAttachments from trusted local project session:\n");
            for(Path p:files){
                try{
                    if(!Files.isRegularFile(p)||Files.size(p)>20_000_000)throw new IllegalArgumentException("Not a regular file or exceeds 20MB");
                    query.append("\nFile: ").append(p.toAbsolutePath()).append("\n");
                    String ext=p.getFileName().toString().toLowerCase(Locale.ROOT);
                    boolean isText=List.of(".java",".txt",".md",".json",".xml",".yaml",".yml",".py",".js",".ts",".tsx",".sql",".html",".css",".csv",".log").stream().anyMatch(ext::endsWith);
                    if(isText){String c=Files.readString(p,StandardCharsets.UTF_8);query.append("```\n").append(c.substring(0,Math.min(12000,c.length()))).append("\n```\n");}
                    else query.append("Use the Read tool if available to inspect this file. Images require a vision model; do not pretend to see unsupported images.\n");
                }catch(Exception ex){query.append("Attachment unavailable: ").append(ex.getMessage()).append("\n");}
            }
        }
        // Files intended for the Read tool require project mode; text snippets are attached directly.
        if (!files.isEmpty() && !projectMode.isSelected() && files.stream().anyMatch(file -> {
            String lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
            return List.of(".png", ".jpg", ".jpeg", ".webp", ".pdf", ".docx").stream().anyMatch(lower::endsWith);
        })) {
            status.setText("Attachments requiring Read/Vision need Project Mode and a compatible model.");
        }
        String profileName=profile.getValue();if(profileName==null)profileName="Claude-Free";
        bubble("You / شما",text+(files.isEmpty()?"":"\n📎 "+files.size()+" files"),true);
        busy=true;send.setDisable(true);cancel.setDisable(false);status.setText("Waiting for Claude...");
        final String targetProfile=profileName, priorSession=sessionId, payload=query.toString();
        final boolean readOnlyProjectTools=projectMode.isSelected();
        Thread t=new Thread(()->{
            try{
                ClaudePrintService.Reply reply=service.ask(targetProfile,payload,priorSession,readOnlyProjectTools);
                Platform.runLater(()->{sessionId=reply.sessionId();bubble("Claude",reply.content(),false);status.setText("Completed (exit "+reply.exitCode()+")");});
            }catch(Exception ex){Platform.runLater(()->{bubble("Error / خطا",ex.getMessage(),false);status.setText("Request failed");});}
            finally{Platform.runLater(()->{busy=false;send.setDisable(false);cancel.setDisable(true);});}
        },"sakhtyar-graphical-chat");t.setDaemon(true);t.start();
    }
}
