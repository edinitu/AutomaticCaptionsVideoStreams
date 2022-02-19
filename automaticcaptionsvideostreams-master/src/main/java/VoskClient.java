import com.neovisionaries.ws.client.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.AppSink;
import javax.sound.sampled.*;

public class VoskClient extends Application {

    public static int count;
    public static ArrayList<Byte> waveBytes = new ArrayList<>();
    private static Pipeline pipeline;
    private static Pipeline pipelineClip;
    private ArrayList<String> results = new ArrayList<String>();
    private CountDownLatch recieveLatch;
    private CountDownLatch receiveLatch2;
    private static long audioDuration;
    private byte[] buffer;

    //launching the Text Field to show the subtitles
    @Override
    public void start(Stage s) throws Exception{
        s.setTitle("Subtitrari");

        // create a textfield
        StackPane r = new StackPane();

        TextField b = new TextField();
        r.getChildren().add(b);
        b.setEditable(false);
        b.setMinHeight(100);
        // create a scene
        Scene sc = new Scene(r, 1500, 100);
        // set the scene
        s.setScene(sc);

        s.show();

        //new thread to receive the transcribe and play the live stream
        TextThread t1 = new TextThread(b);
        Thread t = new Thread(t1);
        t.start();
    }

    public ArrayList<String> transcribe(TextField t) throws Exception {
        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = factory.createSocket("ws://live-transcriber.zevo-tech.com:12320");

        //method that receives the transcrptions from the vosk server and adds it to an array
        ws.addListener(new WebSocketAdapter() {
            @Override
            public void onTextMessage(WebSocket websocket, String message) {
                if(message.charAt(5) == 'p') {
                    results.add(message);
                    try {
                        t.setText(message.substring(17, message.length()-2));
                    } catch(Exception n) {
                        System.out.println(message.substring(17, message.length()-2));
                    }
                }
                recieveLatch.countDown();
            }
        });
        ws.connect();
        String key = "e4b70d22ca4b47369fbbc46b2afa3c33 ";
        ws.sendText("{\"config\": {\"key\": \"" + key + "\"}}");

        writeToByteArray(ws);

        recieveLatch = new CountDownLatch(1);
        ws.sendText("{\"eof\" : 1}");
        recieveLatch.await();
        ws.disconnect();

        return results;
    }

    // Using GStreamer classes, create a pipeline that separates audio and video from an online live stream, and writes the audio part in a byte[] buffer
    public void writeToByteArray(WebSocket ws) {
        ArrayList<Byte> byteList = new ArrayList<>();
        Utils.configurePaths();
        Gst.init(Version.BASELINE, "BasicPipeline");

        //pipeline used to play the live stream
        pipelineClip = (Pipeline) Gst.parseLaunch("gst-launch-1.0 souphttpsrc location=https://mn-nl.mncdn.com/tvr1_hd_live/smil:tvr1_hd_live.smil/chunklist_b8288000.m3u8 ! decodebin name = decoder decoder. ! queue ! videoconvert ! autovideosink decoder. ! queue ! audioconvert ! autoaudiosink");
        pipelineClip.play();

        pipeline = new Pipeline("my_pipeline");

        //initialise the elements requiered for the custom pipeline
        Element src = ElementFactory.make("souphttpsrc", "src");
        src.set("location", "https://mn-nl.mncdn.com/tvr1_hd_live/smil:tvr1_hd_live.smil/chunklist_b8288000.m3u8");
        Element hlsdemux = ElementFactory.make("hlsdemux", "hlsdemux");
        Element decodebin = ElementFactory.make("decodebin", "decodebin");
        Element audioconvert = ElementFactory.make("audioconvert", "audioconvert");
        audioconvert.setCaps(new Caps("audio/x-wav, rate=16000, channels=1"));
        Element wavenc = ElementFactory.make("wavenc", "wavenc");
        Element audioresample = ElementFactory.make("audioresample", "audioresample");
        audioresample.setCaps(new Caps("audio/x-wav, rate=16000, channels=1"));
        AppSink sink = (AppSink) ElementFactory.make("appsink", "sink");

        //customize the appsink so that it accepts wav format audio, formats it and sends it to vosk server
        sink.connect(new AppSink.NEW_SAMPLE() {
            @Override
            public FlowReturn newSample(AppSink elem) {
                if (elem.isEOS()) {
                    return FlowReturn.EOS;
                }
                Sample s = elem.pullSample();
                Buffer b = s.getBuffer();
                ByteBuffer bb = b.map(false);
                int k = 0;

                //wait for at least 50 samples to load before transmitting to server
                while(count < 50) {
                    while (k < bb.capacity()) {
                        byteList.add(bb.get());
                        k++;
                    }
                    count++;

                    //save the wav format header in an array
                    if(bb.capacity() < 100) waveBytes.addAll(byteList);

                    if (count == 50) {
                        count = 0;
                        convertByteList(byteList);
                        byteList.clear();

                        for(Byte octet:waveBytes) {
                            byteList.add(octet);
                        }

                        //create the audiostream from a byte[] buffer
                        InputStream in = new ByteArrayInputStream(buffer);
                        try {
                            AudioInputStream before = AudioSystem.getAudioInputStream(in);

                            //format the stream to the vosk server's requirements
                            AudioFormat a = new AudioFormat(16000, 16, 1, true, false);
                            AudioInputStream after = AudioSystem.getAudioInputStream(a, before);

                            //transmission to vosk server
                            byte[] buf = new byte[8000];
                            while (true) {
                                int nbytes = after.read(buf);
                                if (nbytes < 0) break;
                                recieveLatch = new CountDownLatch(1);
                                ws.sendBinary(buf);
                                recieveLatch.await();
                            }
                        } catch (UnsupportedAudioFileException | IOException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else break;
                }
                return FlowReturn.OK;
            }
            });

        sink.set("emit-signals", true);
        sink.set("sync", true);

        //build the custom pipeline
        pipeline.addMany(src, hlsdemux, decodebin, audioconvert, audioresample, wavenc, sink);
        Element.linkMany(src, hlsdemux);
        Element.linkMany(audioconvert, audioresample, wavenc, sink);
        hlsdemux.connect(new Element.PAD_ADDED() {
            @Override
            public void padAdded(Element element, Pad pad) {
                boolean b = Element.linkPads(element, pad.getName(), decodebin, "sink");
            }
        });
        decodebin.connect(new Element.PAD_ADDED() {
            @Override
            public void padAdded(Element element, Pad pad) {
              boolean b = Element.linkPads(element, pad.getName(), audioconvert, "sink");
            }
        });
        pipeline.play();

        //set the desired duration that the live stream plays for
        Gst.getExecutor().schedule(Gst::quit, 120, TimeUnit.SECONDS);
        Gst.main();
        convertByteList(byteList);
    }

    //convert ArratList<Byte> to byte[]
    public void convertByteList(ArrayList<Byte> byteList) {
       // pipeline.stop();
        buffer =  new byte[byteList.size()];
        for(int i = 0; i<byteList.size()-1; i++) {
            buffer[i] = byteList.get(i);
        }
    }

    //start the application
    public static void main(String[] args) throws Exception {
        launch(args);
    }
}
