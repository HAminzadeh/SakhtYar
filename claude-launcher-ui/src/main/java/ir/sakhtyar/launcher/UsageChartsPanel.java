package ir.sakhtyar.launcher;

import ir.sakhtyar.context.CcrUsageImporter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;

/** Charts show only counts obtained from a selected CCR JSON/JSONL export. */
public final class UsageChartsPanel {
    public Node view(boolean fa) {
        VBox shell=new VBox(12);shell.setPadding(new Insets(16));
        Label heading=new Label(fa?"مصرف ثبت‌شده CCR — نمودار بر اساس خروجی واقعی":"CCR usage charts — measured export only");
        Label summary=new Label(fa?"هنوز فایلی انتخاب نشده؛ صفر یا هزینه فرضی نمایش داده نمی‌شود.":"No log export selected; no invented usage.");summary.setWrapText(true);
        Button load=new Button(fa?"انتخاب خروجی CCR (JSON/JSONL)":"Load CCR log export");
        Button live=new Button(fa?"خواندن آمار محلی CCR":"Read local CCR usage");
        CategoryAxis x=new CategoryAxis();NumberAxis y=new NumberAxis();y.setLabel("Tokens");
        BarChart<String,Number> tokens=new BarChart<>(x,y);tokens.setAnimated(false);tokens.setLegendVisible(false);tokens.setTitle(fa?"مصرف توکن بر اساس نوع":"Token usage breakdown");
        PieChart states=new PieChart();states.setTitle(fa?"وضعیت درخواست‌ها":"Request status");states.setLabelsVisible(true);
        HBox charts=new HBox(10,tokens,states);HBox.setHgrow(tokens,Priority.ALWAYS);HBox.setHgrow(states,Priority.ALWAYS);
        load.setOnAction(e->{FileChooser picker=new FileChooser();picker.setTitle("CCR logs JSON or JSONL");
            picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON or JSONL","*.json","*.jsonl"));
            File file=picker.showOpenDialog(shell.getScene()==null?null:shell.getScene().getWindow());if(file==null)return;
            load.setDisable(true);summary.setText("Reading file...");
            Thread t=new Thread(()->{
                try{CcrUsageImporter.Report r=new CcrUsageImporter().inspect(file.toPath());Platform.runLater(()->{
                    show(r,"Export: "+file.getName(),summary,tokens,states);
                });}catch(Exception ex){Platform.runLater(()->summary.setText("Import failed: "+ex.getMessage()));}
                finally{Platform.runLater(()->load.setDisable(false));}
            },"ccr-chart-import");t.setDaemon(true);t.start();
        });
        live.setOnAction(e->{live.setDisable(true);summary.setText("Reading CCR local usage...");
            Thread t=new Thread(()->{
                try{LocalCcrUsageReader.Snapshot snapshot=new LocalCcrUsageReader().read();Platform.runLater(()->show(snapshot.report(),snapshot.source(),summary,tokens,states));}
                catch(Exception ex){Platform.runLater(()->summary.setText(ex.getMessage()));}
                finally{Platform.runLater(()->live.setDisable(false));}
            },"ccr-live-usage");t.setDaemon(true);t.start();
        });
        Label limits=new Label(fa?"System tools در /context جزء سربار داخلی Claude هستند و با نمودار CCR یکسان نیستند. مصرف واقعی پس از انتخاب فایل ثبت‌شده نمایش داده می‌شود.":"Claude's built-in system-tools context is not the same as metered CCR token usage.");limits.setWrapText(true);
        shell.getChildren().addAll(heading,new HBox(8,live,load),summary,charts,limits);VBox.setVgrow(charts,Priority.ALWAYS);return shell;
    }
    private static void show(CcrUsageImporter.Report r,String origin,Label summary,BarChart<String,Number> tokens,PieChart states){
        tokens.getData().clear();XYChart.Series<String,Number> series=new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Input",r.inputTokens()));
        series.getData().add(new XYChart.Data<>("Output",r.outputTokens()));
        series.getData().add(new XYChart.Data<>("Cache read",r.cacheReadTokens()));
        series.getData().add(new XYChart.Data<>("Thinking",r.thinkingTokens()));tokens.getData().add(series);
        states.getData().setAll(new PieChart.Data("200: "+r.successful(),r.successful()),
            new PieChart.Data("Errors: "+r.failed(),r.failed()),
            new PieChart.Data("Other: "+Math.max(0,r.entries()-r.successful()-r.failed()),Math.max(0,r.entries()-r.successful()-r.failed())));
        summary.setText(origin+" | Requests: "+r.entries()+" | Measured: "+r.measured()+" | Input: "+r.inputTokens()+" | Output: "+r.outputTokens()+" | Cache: "+r.cacheReadTokens());
    }

}
